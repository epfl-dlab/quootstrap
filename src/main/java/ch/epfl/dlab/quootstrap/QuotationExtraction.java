package ch.epfl.dlab.quootstrap;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.broadcast.Broadcast;

import ch.epfl.dlab.quootstrap.Dawg.Node;
import ch.epfl.dlab.spinn3r.converter.Stopwatch;
import scala.Tuple2;
import scala.Tuple3;
import scala.Tuple4;

public class QuotationExtraction {

	public static void main(final String[] args) throws IOException {
		
		if (args.length > 0) {
			// Output path for the evaluation logs
			new File(args[0]).mkdirs();
			ConfigManager.getInstance().setOutputPath(args[0] + "/");
		}
		
		final String namesPath = ConfigManager.getInstance().getNamesPath();
		final int numIterations = ConfigManager.getInstance().getNumIterations();
		
		final double confidenceThreshold = ConfigManager.getInstance().getConfidenceThreshold();
		
		final boolean finalEvaluation = ConfigManager.getInstance().isFinalEvaluationEnabled();
		final boolean intermediateEvaluation = ConfigManager.getInstance().isIntermediateEvaluationEnabled();
		
		final boolean caseSensitive = ConfigManager.getInstance().isCaseSensitive();
		
		// Use Kryo serializer and register most frequently used classes to improve performance
		final SparkConf conf = new SparkConf()
				.setAppName("QuotationExtraction")
				.set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
				.registerKryoClasses(new Class<?>[] { ArrayList.class, Token.class, Token.Type.class, Sentence.class, Pattern.class,
					Trie.class, Trie.Node.class, String[].class, Object[].class, HashMap.class, Hashed.class });
		
		if (ConfigManager.getInstance().isLocalModeEnabled()) {
			conf.setMaster("local[*]")
				.set("spark.executor.memory", "10g")
				.set("spark.driver.memory", "10g");
		}
		
		Stopwatch sw = new Stopwatch();
		
		try (JavaSparkContext sc = new JavaSparkContext(conf)) {
			
			// (sentences, deduplicated sentences)
			final JavaRDD<Sentence> allSentences = loadSentences(sc, true,
					ConfigManager.getInstance().isMergingEnabled());
			
			GroundTruthEvaluator ev = null;
			if (intermediateEvaluation || finalEvaluation) {
				ev = new GroundTruthEvaluator(sc, allSentences);
			}
			
			MultiCounter mc = new MultiCounter(sc, "exact", "extended", "not_extended");
			
			JavaRDD<Sentence> remainingSentences = allSentences;
			JavaPairRDD<String, Sentence> remainingQuotations = allSentences
					.mapToPair(x -> new Tuple2<>(x.getQuotation(), x));
			
			// Patterns that are used in the current iteration
			Set<Pattern> currentPatterns = new HashSet<>(loadPatterns("seedPatterns.txt"));
			
			// Patterns from previous iterations
			Set<Pattern> oldPatterns = new HashSet<>(currentPatterns);
			
			// Pairs found in all iterations. Pair (quotation, (speaker, lineageInfo))
			JavaPairRDD<String, Tuple2<List<Token>, LineageInfo>> allPairs = JavaPairRDD.fromJavaRDD(sc.emptyRDD());
			
			NameDatabase db = new NameDatabase(sc, namesPath);
			Broadcast<NameDatabase> broadcastNames = sc.broadcast(db);
			final JavaPairRDD<Long, Iterable<List<Token>>> allSpeakers = Utils.loadCache(
				allSentences.mapPartitionsToPair(it -> {
					List<Tuple2<Long, List<Token>>> results = new ArrayList<>();
					HashTriePatternMatcher pm = broadcastNames.value().newMatcher();
					while (it.hasNext()) {
						Sentence s = it.next();
						if (pm.match(s)) {
							results.add(new Tuple2<>(s.getArticleUid(), Token.getTokens(pm.getLongestMatch())));
						}
					}
					return results.iterator();
				})
				.distinct()
				.groupByKey(), "speakers-" + ConfigManager.getInstance().getLangSuffix());
			
			final int minSpeakerLength = 1;
			final int maxSpeakerLength = 5;
			
			for (int iter = 0; iter < numIterations; iter++) {
				Broadcast<Trie> broadcastTrie = sc.broadcast(
						new Trie(currentPatterns, caseSensitive));
				JavaRDD<Tuple4<String, List<Token>, Sentence, Pattern>> rawPairs = remainingQuotations
					.map(x -> x._2)
					.mapPartitions(it -> {
						PatternMatcher mt = new TriePatternMatcher(broadcastTrie.value(), minSpeakerLength, maxSpeakerLength);
						return new TupleExtractor(it, mt);
					});

				// (quotation, (speaker, tuple confidence))
				JavaRDD<Tuple4<String, List<Token>, Sentence, Pattern>> matchedPairs = rawPairs
					.groupBy(x -> x._3().getArticleUid())
					.leftOuterJoin(allSpeakers)
					.flatMap(x -> {
						List<List<Token>> speakersInArticle = new ArrayList<>();
						List<Tuple4<String, List<Token>, Sentence, Pattern>> sentences = new ArrayList<>();
						x._2._1.forEach(val -> {
							speakersInArticle.add(val._2());
							sentences.add(val);
						});
						
						// (quotation, speaker, sentence, pattern)
						List<Tuple4<String, List<Token>, Sentence, Pattern>> out = new ArrayList<>();

						Set<List<Token>> validNames = new HashSet<>();
						x._2._2().or(Collections.emptyList()).forEach(validNames::add);
						List<Integer> unmatchedIndices = new ArrayList<>();
						for (int i = 0; i < speakersInArticle.size(); i++) {
							List<Token> speaker = speakersInArticle.get(i);
							
							// Check if the speaker exists in the name database
							HashTriePatternMatcher pm = broadcastNames.value().newMatcher();
							if (pm.match(speaker)) {
								List<Token> match = Token.getTokens(pm.getLongestMatch());
								if (match.size() == speaker.size()) {
									// We have an exact match -> nothing to do
									// e.g. "John Doe" matched with "John Doe"
									Tuple4<String, List<Token>, Sentence, Pattern> s = sentences.get(i);
									out.add(new Tuple4<>(s._1(), match, s._3(), s._4()));
									mc.increment("exact");
								}
							} else {
								// Might still be a partial match
								unmatchedIndices.add(i);
							}
						}
						
						// Try to extend short, unmatched patterns
						for (int i : unmatchedIndices) {
							List<Token> match = Utils.findUniqueSuperstring(speakersInArticle.get(i), validNames, caseSensitive);
							if (match != null) {
								
								// The tokens that we used for the extension must not be present in the sentence,
								// e.g. ["Hi" said spokesperson John Doe .] is fine if extracted by [$Q said *$ $S .],
								// but this pattern could also extract "Doe" from ["Hi" said John Doe], and the
								// extension would yield ["Hi" said John John Doe], which is incorrect.
								// Note that this can happen only if the speaker token $S is surrounded by $* tokens.
								Tuple4<String, List<Token>, Sentence, Pattern> s = sentences.get(i);
								boolean matched = true;
								if (s._4().isSpeakerSurroundedByAny()) {
									// Diff contains the extension difference,
									// e.g. if Doe is extended to John Doe, we obtain John.
									Set<Token> diff = new HashSet<>(match);
									diff.removeAll(speakersInArticle.get(i));
									List<Token> tokens = s._3().getTokensByType(Token.Type.GENERIC);
									for (Token t : tokens) {
										if (diff.contains(t)) {
											matched = false;
											break;
										}
									}
								}
								
								if (matched) {
									out.add(new Tuple4<>(s._1(), match, s._3(), s._4()));
									mc.increment("extended");
								}
							} else {
								mc.increment("not_extended");
							}
						}
						// (quotation, speaker, sentence, pattern)
						return out.iterator();
					});
				
				JavaPairRDD<String, Tuple2<List<Token>, LineageInfo>> pairs = matchedPairs
					.mapToPair(x -> new Tuple2<>(x._1(), new Tuple3<>(x._2(), x._3(), x._4()))) // (quotation, (speaker, sentence, pattern))
					.groupByKey()
					.mapValues(x -> {
						// (speaker, (aggregated confidence, count))
						Map<List<Token>, Tuple2<Double, Integer>> s = new HashMap<>();
						x.forEach(y -> {
							if (!s.containsKey(y._1())) {
								s.put(y._1(), new Tuple2<>(1.0, 0));
							}
							s.put(y._1(), new Tuple2<>(s.get(y._1())._1 * (1 - y._3().getConfidenceMetric()), s.get(y._1())._2 + 1));
						});
						
						List<Token> bestSpeaker = null;
						double bestConfidence = 1; // Note: inverted (0 = best)
						int bestCount = 0;
						boolean dirty = true;
						for (Map.Entry<List<Token>, Tuple2<Double, Integer>> entry : s.entrySet()) {
							if (bestSpeaker == null || entry.getValue()._1 < bestConfidence
									|| (entry.getValue()._1 == bestConfidence && entry.getValue()._2 > bestCount)) {
								dirty = false;
								bestConfidence = entry.getValue()._1;
								bestSpeaker = entry.getKey();
								bestCount = entry.getValue()._2;
							} else if (entry.getValue()._1 == bestConfidence && entry.getValue()._2 == bestCount) {
								// Avoid guessing in case of ties
								dirty = true;
							}
						}
						if (dirty) {
							return null;
						}
						
						// Build lineage information for the evaluation
						final List<Pattern> patterns = new ArrayList<>();
						final List<Sentence> sentences = new ArrayList<>();
						final List<Token> chosenSpeaker = bestSpeaker;
						x.forEach(y -> {
							if (y._1().equals(chosenSpeaker)) {
								sentences.add(y._2());
								patterns.add(y._3());
							}
						});
						
						return new Tuple2<>(bestSpeaker, new LineageInfo(patterns, sentences, 1 - bestConfidence));
					})
					.filter(x -> x._2 != null);
				
				// Quotations found in the current iteration. Pair (quotation, speaker)
				allPairs = allPairs.union(pairs);
				
				if (intermediateEvaluation || (iter == numIterations - 1 && finalEvaluation)) {
					// Run evaluation on current iteration
					ev.evaluate(allPairs, iter);
					mc.dump();
				}
				if (iter == numIterations - 1) {
					// Last iteration reached -> stop
					
					// Save results if requested
					if (ConfigManager.getInstance().isExportEnabled()) {
						Exporter exporter = new Exporter(sc, allSentences, db);
						exporter.exportResults(allPairs);
					}
					break;
				}
				
				remainingSentences = remainingSentences.subtract(matchedPairs.map(x -> x._3()).filter(x -> x != null));
				remainingQuotations = remainingQuotations
						.subtractByKey(matchedPairs.mapToPair(x -> new Tuple2<>(x._1(), null)));

				List<Pattern> nextPatternsTmp = remainingSentences.mapToPair(x -> new Tuple2<>(x.getQuotation(), x))
						.join(pairs)
						// (quotation, (pattern, speaker))
						.map(x -> PatternExtractor.extractPattern(x._2._1, x._1, x._2._2._1, caseSensitive))
						.filter(x -> x != null)
						.collect();
				
				if (ConfigManager.getInstance().isDumpPatternsEnabled()) {
					Utils.dumpCollection(nextPatternsTmp, "nextPatternsPreClustering" + iter + ".txt");
				}
				
				nextPatternsTmp = inferPatterns(nextPatternsTmp);
				
				// Update confidence factor
				Broadcast<Trie> broadcastNextTrie = sc.broadcast(new Trie(nextPatternsTmp, caseSensitive));
				List<Tuple2<Pattern, Integer>> nextPatterns = remainingSentences
					.mapPartitions(it -> {
						PatternMatcher mt = new TriePatternMatcher(broadcastNextTrie.value(), minSpeakerLength, maxSpeakerLength);
						return new TupleExtractor(it, mt);
					})
					.mapToPair(x -> new Tuple2<>(x._1(), new Tuple2<>(x._4(), x._2())))
					.join(allPairs) //i.e. previous pairs -> result tuple (quotation, ((pattern, extractedSpeaker), actualSpeaker))
					.mapToPair(x -> {
						// Give low weight to short quotations (collisions likely) and high weight to long quotations
						double weight = Math.tanh(0.1 * x._1.length());
						return new Tuple2<>(x._2._1._1(), new Tuple3<>(StaticRules.matchSpeakerApprox(x._2._1._2(), x._2._2._1, caseSensitive) ? weight : 0, weight, 1));
					})
					.reduceByKey((x, y) -> new Tuple3<>(x._1() + y._1(), x._2() + y._2(), x._3() + y._3()))
					.filter(x -> x._2._3() >= 5) // At least N extracted pairs
					.map(x -> new Tuple2<>(new Pattern(x._1.getTokens(), (double)x._2._1()/x._2._2()), x._2._3()))
					.collect();
				
				if (ConfigManager.getInstance().isDumpPatternsEnabled()) {
					Utils.dumpCollection(nextPatterns, "nextPatternsPostClustering" + iter + ".txt");
				}

				currentPatterns = nextPatterns.stream()
						.filter(x -> x._1.getConfidenceMetric() > confidenceThreshold)
						.map(x -> x._1())
						.collect(Collectors.toCollection(HashSet::new));
				
				currentPatterns.removeAll(oldPatterns);
				oldPatterns.addAll(currentPatterns);

				if (ConfigManager.getInstance().isDumpPatternsEnabled()) {
					Utils.dumpCollection(currentPatterns, "nextPatterns" + iter + ".txt");
				}
				
				if (iter == numIterations - 2) {
					// Export patterns
					Utils.dumpCollection(oldPatterns, "discoveredPatterns.txt");
				}
				
			}

			sw.printTime();
		}
	}
	
	public static List<Pattern> inferPatterns(Collection<Pattern> patterns) {
		List<List<String>> tmp = patterns.stream()
			.map(x -> x.getTokens())
			.map(x -> x.stream().map(y -> y.toString()).collect(Collectors.toList()))
			.collect(Collectors.toList());
		Dawg d = new Dawg();
		d.addAll(tmp);
		
		Set<Pattern> newPatterns = new HashSet<>();
		
		for (double clusteringThreshold : ConfigManager.getInstance().getClusteringThresholds()) {
			final int threshold = (int)(patterns.size() * clusteringThreshold);
			List<List<Dawg.Node>> allNodes = new ArrayList<>();
			List<Node> accumulator = new ArrayList<>();
			for (Node c : d.getRoot().getNodes()) {
				c.dump(allNodes, accumulator);
			}
			
			allNodes.stream()
				.map(prePattern -> prePattern.stream()
						.map(node -> node.getCount() < threshold
									&& !node.getWord().equals(Pattern.QUOTATION_PLACEHOLDER)
									&& !node.getWord().equals(Pattern.SPEAKER_PLACEHOLDER)
									&& node.getNodes().size() > 0
								? new Token(null, Token.Type.ANY)
								: new Token(node.getWord(), Token.Type.GENERIC))
						.collect(Collectors.toList())
				)
				.map(x -> Dawg.convert(x))
				.filter(x -> x.isPresent())
				.map(x -> x.get())
				.forEach(newPatterns::add);
		}
		
		return new ArrayList<>(newPatterns);
	}
	
	/**
	 * Returns the dataset loader specified in the configuration.
	 */
	public static DatasetLoader getConcreteDatasetLoader() {
		String className = ConfigManager.getInstance().getNewsDatasetLoader();
		
		try {
			return (DatasetLoader) Class.forName(className).newInstance();
		} catch (ClassNotFoundException e) {
			throw new IllegalArgumentException("Unable to find the dataset loader class " + className, e);
		} catch (InstantiationException | IllegalAccessException e) {
			throw new IllegalArgumentException("Unable to instantiate the dataset loader class " + className, e);
		}
	}
	
	/**
	 * Returns all sentences (i.e. contexts) in the dataset.
	 * @param sc the SparkContext
	 * @param postProcess enable post-processing
	 * @param merge merge quotations
	 * @return deduplicated sentences
	 */
	public static JavaRDD<Sentence> loadSentences(JavaSparkContext sc, boolean postProcess, boolean merge) {
		Set<String> langSet = new HashSet<>(ConfigManager.getInstance().getLangFilter());
		
		JavaRDD<Sentence> allSentences = getConcreteDatasetLoader().loadArticles(sc,
				ConfigManager.getInstance().getDatasetPath(), langSet)
			.flatMap(x -> ContextExtractor.extractQuotations(x.getArticleContent(), x.getArticleUID()).iterator());
			
		if (postProcess) {
			allSentences = allSentences.map(ContextExtractor::postProcess);
			
			if (merge) {
				allSentences = mergeQuotations(allSentences);
			}
		}

		JavaRDD<Sentence> deduplicatedSentences = allSentences
			.mapToPair(x -> new Tuple2<>(x.getTokens(), x.getKey()))
			.reduceByKey((x, y) -> {
				// Deterministic "distinct" by defining a lexicographical order
				if (x._1 < y._1) {
					return x;
				} else if (x._1 > y._1) {
					return y;
				} else {
					return x._2 < y._2 ? x : y;
				}
			})
			.map(x -> new Sentence(x._1, x._2._1, x._2._2));
		
		String suffix = ConfigManager.getInstance().getLangSuffix();
		
		if (postProcess) {
			if (merge) {
				deduplicatedSentences = Utils.loadCache(deduplicatedSentences, "sentences-deduplicated-post-merged-" + suffix);
			} else {
				deduplicatedSentences = Utils.loadCache(deduplicatedSentences, "sentences-deduplicated-post-" + suffix);
			}
		}
		return deduplicatedSentences;
	}
	
	public static List<Pattern> loadPatterns(String fileName) {
		try {
			return Files.readAllLines(Paths.get(fileName)).stream()
				.map(x -> Pattern.parse(x))
				.collect(Collectors.toCollection(ArrayList::new));
		} catch (IOException e) {
			throw new IllegalArgumentException(e);
		}
	}
	
	public static JavaRDD<Sentence> mergeQuotations(JavaRDD<Sentence> sentences) {
		JavaPairRDD<Hashed, String> quotations = sentences.map(x -> x.getQuotation())
				.distinct()
				.mapToPair(x -> new Tuple2<>(new Hashed(x), x));
		
		final int shingleSize = ConfigManager.getInstance().getMergingShingleSize();
		JavaPairRDD<Hashed, Hashed> remap = quotations
			.flatMapToPair(x -> {
				List<String> tokens = Arrays.asList(x._2.split(" "));
				List<Tuple2<Hashed, Hashed>> output = new ArrayList<>();
				final int size = shingleSize;
				Hashed hx = x._1;
				if (tokens.size() <= size) {
					output.add(new Tuple2<>(hx, hx));
				} else {
					for (int i = 0; i < tokens.size() - size + 1; i++) {
						output.add(new Tuple2<>(new Hashed(String.join(" ", tokens.subList(i, i + size))), hx));
					}
				}
				return output.iterator();
			})
			.groupByKey()
			.flatMapToPair(x -> {
				List<Hashed> s = new ArrayList<>();
				x._2.forEach(s::add);
				if (s.size() == 1) {
					return Collections.emptyIterator();
				}
				
				List<Tuple2<Hashed, Hashed>> output = new ArrayList<>();
				Hashed longest = Collections.max(s);
				for (Hashed sent : s) {
					if (sent != longest) {
						output.add(new Tuple2<>(sent, longest));
					}
				}
				return output.iterator();
			})
			.reduceByKey((x, y) -> x.compareTo(y) == 1 ? x : y);
		
		return sentences.mapToPair(x -> new Tuple2<>(new Hashed(x.getQuotation()), x))
			.leftOuterJoin(remap)
			.mapToPair(x -> {
				if (x._2._2.isPresent()) {
					return new Tuple2<>(x._2._2.get(), x._2._1);
				} else {
					return new Tuple2<>(x._1, x._2._1);
				}
			})
			.leftOuterJoin(quotations)
			.map(x -> {
				if (x._2._2.isPresent()) {
					// Replace quotation with extended version
					List<Token> tokens = new ArrayList<>(x._2._1.getTokens());
					Token repToken = new Token(x._2._2.get(), Token.Type.QUOTATION);
					tokens.replaceAll(y -> y.getType() == Token.Type.QUOTATION ? repToken : y);
					return new Sentence(tokens, x._2._1.getArticleUid(), x._2._1.getIndex());
				} else {
					return x._2._1;
				}
			});
	}
}
