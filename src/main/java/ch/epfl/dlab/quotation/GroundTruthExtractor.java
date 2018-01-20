package ch.epfl.dlab.quotation;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.broadcast.Broadcast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import ch.epfl.dlab.spinn3r.converter.ProtoToJson;
import scala.Tuple2;
import scala.Tuple3;
import scala.Tuple4;

import java.util.Random;

public class GroundTruthExtractor {

	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	
	public static void main(String[] args) throws IOException {
		final SparkConf conf = new SparkConf()
				.setAppName(ProtoToJson.class.getName())
				.setMaster("local[*]")
				.set("spark.executor.memory", "8g")
				.set("spark.driver.memory", "8g")
				.set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
				//.set("spark.kryo.registrationRequired", "true")
				.registerKryoClasses(new Class<?>[] {ArrayList.class, Token.class, Token.Type.class, Sentence.class, Pattern.class, HashTrie.class, HashTrie.Node.class,
					Trie.class, String[].class, Object[].class, HashMap.class });
		
		FileUtils.deleteQuietly(new File("peopleList.txt"));
		FileUtils.deleteQuietly(new File("groundTruth.txt"));
		FileUtils.deleteQuietly(new File("stats.txt"));
		FileUtils.deleteQuietly(new File("mturk.json"));
		
		try (JavaSparkContext sc = new JavaSparkContext(conf)) {
			
			JavaPairRDD<String, String[]> peopleRaw = sc.textFile("C:\\Users\\Dario\\Documents\\Università\\Semester project 2\\name_dataset\\names.ALL.WITH-DATES.tsv.gz")
				.map(x -> x.split("\t"))
				.mapToPair(x -> new Tuple2<>(x[0], x));
			
			JavaPairRDD<String, Void> unambiguousPeople = sc.textFile("C:\\Users\\Dario\\Documents\\Università\\Semester project 2\\name_dataset\\names.ALL.UNAMBIGUOUS.tsv.gz")
				.map(x -> x.split("\t"))
				.mapToPair(x -> new Tuple2<>(x[0], x[1]))
				.flatMapValues(x -> new TreeSet<>(Arrays.asList(x.split("\\|")))) // Remove duplicates within group
				.mapToPair(x -> new Tuple2<>(x._2, new Tuple2<>(x._1, 1))) // (name, (id, 1))
				.reduceByKey((x, y) -> new Tuple2<>(x._1, x._2 + y._2)) // Count duplicate names
				.filter(x -> x._2._2 == 1)
				.map(x -> x._2._1) // Get only ID
				//.intersection(unamb.filter(x -> x._2.size() == 1).map(x -> x._1))
				.distinct()
				.mapToPair(x -> new Tuple2<>(x, null));
			
			// (Person ID, (name, profession))
			JavaPairRDD<String, Tuple2<String, String>> peopleProfessions = sc.textFile("C:\\Users\\Dario\\Documents\\Università\\Semester project 2\\name_dataset\\names.ALL.WITH-PROFESSION.gz")
					.map(x -> x.split("\t"))
					.mapToPair(x -> new Tuple2<>(x[0], new Tuple2<>(x[1], x[2]))) //(person ID, (person name, profession))
					.reduceByKey((x, y) -> x) // Only one profession per person (person ID, (person name, profession))
					.mapToPair(x -> new Tuple2<>(x._2._1, new Tuple3<>(x._1, x._2._2, 1))) // (name, (ID, profession, count))
					.reduceByKey((x, y) -> new Tuple3<>(x._1(), x._2(), x._3() + y._3()))
					//.filter(x -> x._2._3() == 1) // Remove ambiguous names
					.mapToPair(x -> new Tuple2<>(x._2._1(), new Tuple2<>(x._1, x._2._3() == 1 ? x._2._2() : "Ambiguous")));
			
			// People that are still alive (as of 2014).
			JavaPairRDD<String, String> alivePeople = peopleRaw.filter(x -> x._2[5].equals("ALIVE"))
					.mapToPair(x -> new Tuple2<>(x._1, x._2[1])); // (id, name)
			
			// People that are dead as of 2014, but were alive in 2011.
			JavaPairRDD<String, String> deadButAlivePeople = peopleRaw.filter(x -> x._2[5].equals("DEAD"))
					.filter(x -> x._2.length >= 7)
					.filter(x -> {
						try {
							return Integer.parseInt(x._2[6].split("-")[0]) > 2011;
						} catch (NumberFormatException e) {
							return false;
						}
					})
					.mapToPair(x -> new Tuple2<>(x._1, x._2[1]));
			
			// (name, profession)
			JavaPairRDD<List<String>, String> professionPairs = alivePeople.union(deadButAlivePeople)
				.join(unambiguousPeople)
				.mapValues(x -> x._1)
				.leftOuterJoin(peopleProfessions)
				.mapValues(x -> x._2.isPresent()
						? new Tuple2<>(x._2.get()._1, x._2.get()._2)
						: new Tuple2<>(x._1, "Unknown")) // (personId, (name, profession))
				.map(x -> x._2)
				//.filter(x -> x._2._2.contains("Politician") || x._2._2.contains("President"))
				.filter(x -> x._1.split("\\|").length == 1) // No aliases allowed (only unique names)
				.mapToPair(x -> new Tuple2<>(Arrays.asList(x._1.split(" ")), x._2))
				.filter(x -> x._1.size() >= 2)
				.filter(x -> {
					for (String token : x._1) {
						// At least 4 characters per token
						if (token.length() < 4) {
							return false;
						}
					}
					return true;
				})
				.mapToPair(x -> new Tuple2<>(x._1, new Tuple2<>(x._2, 1)))
				//.reduceByKey((x, y) -> new Tuple2<>(x._1, x._2 + y._2))
				//.filter(x -> x._2._2 == 1) // Remove further ambiguous names
				.mapToPair(x -> new Tuple2<>(x._1, x._2._1))
				.distinct()
				;
			
			JavaRDD<List<String>> finalPeople = professionPairs.map(x -> x._1);
			
			JavaRDD<List<String>> tokens = finalPeople
					.flatMap(x -> {
						// Add first name and last name, in addition to the full name
						return Arrays.asList(x, Arrays.asList(x.get(0)), Arrays.asList(x.get(x.size() - 1)));
					});
			
			//professionPairs.saveAsTextFile("peopleList.txt"); // was finalPeople
			//ProtoToJson.mergeHdfsFile("peopleList.txt");
			//System.exit(0);
			finalPeople = Utils.loadCache(finalPeople, "finalPeople");
			
			Broadcast<HashTrie> broadcastTrie = sc.broadcast(new HashTrie(tokens.collect()));
			JavaRDD<Sentence> quotations = QuotationExtraction.loadSentences(sc, false, false);
			JavaRDD<Tuple3<Sentence, List<String>, List<String>>> pairs = quotations.mapPartitionsToPair(it -> {
					List<Tuple2<List<String>, Sentence>> results = new ArrayList<>();
					HashTriePatternMatcher pm = new HashTriePatternMatcher(broadcastTrie.value());
					while (it.hasNext()) {
						Sentence s = it.next();
						// The post-processing ensures that names inside quotations are removed
						if (pm.match(ContextExtractor.postProcess(s))) {
							results.add(new Tuple2<>(pm.getLongestMatch(), s));
						}
					}
					return results;
				})
				.groupBy(x -> x._2.getArticleUid())
				.flatMapValues(x -> {
					List<List<String>> speakersInArticle = new ArrayList<>();
					List<Sentence> sentences = new ArrayList<>();
					x.forEach(val -> {
						speakersInArticle.add(val._1);
						sentences.add(val._2);
					});
					
					// (Sentence, speaker, long speaker alias)
					List<Tuple3<Sentence, List<String>, List<String>>> out = new ArrayList<>();

					int i = 0;
					for (List<String> speaker : speakersInArticle) {
						if (speaker.size() == 1) {
							// Single token speaker -> try to match to longer speaker
							List<String> match = findLongestSuperstring(speaker.get(0), speakersInArticle);
							if (match != null) {
								out.add(new Tuple3<>(sentences.get(i), speaker, match));
							}
						} else if (speaker.size() > 1) {
							out.add(new Tuple3<>(sentences.get(i), speaker, speaker));
						}
						i++;
					}
					return out;
				})
				.coalesce(10)
				.mapToPair(x -> new Tuple2<>(x._2._1().toHumanReadableString(false), x._2))
				.reduceByKey((x, y) -> x) // Remove key duplicates
				.map(x -> x._2);
			
			pairs = Utils.loadCache(pairs, "ground_truth_pairs").cache();
			
			
			/*pairs.map(x -> new Tuple3<>(x._1().toHumanReadableString(false), x._2(), x._3()))
				.saveAsTextFile("groundTruth.txt");
			ProtoToJson.mergeHdfsFile("groundTruth.txt");*/
		
			JavaRDD<Tuple4<List<String>, String, Integer, Integer>> stats = pairs.groupBy(x -> x._3())
				.mapValues(x -> {
					Set<List<Token>> distinctQuotations = new HashSet<>();
					int count = 0;
					int distinct = 0;
					for (Tuple3<Sentence, List<String>, List<String>> s : x) {
						List<Token> quotation = s._1().getTokensByType(Token.Type.QUOTATION);
						quotation.replaceAll(q -> new Token(StaticRules.canonicalizeQuotation(q.toString()), Token.Type.QUOTATION));
						if (distinctQuotations.add(quotation)) {
							distinct++;
						}
						count++;
					}
					return new Tuple2<>(count, distinct);
				})
				.filter(x -> x._2._1() >= 50)
				.mapToPair(x -> new Tuple2<>(x._2._1(), new Tuple2<>(x._2._2(), x._1)))
				//.sortByKey(false)
				.mapToPair(x -> new Tuple2<>(x._2._2, new Tuple2<>(x._1, x._2._1))) // (name, (count, distinct))
				.join(professionPairs)
				.map(x -> new Tuple4<>(x._1, x._2._2, x._2._1._1, x._2._1._2))
				.cache()
				;
			
			stats.saveAsTextFile("stats.txt");
			ProtoToJson.mergeHdfsFile("stats.txt");
			
			/*List<Tuple2<Integer, List<String>>> speakers = stats.map(x -> new Tuple2<>(x._3(), x._1()))
				.collect();
			
			int quotationCount = 0;
			final int target = 10000;
			for (int i = 0; i < speakers.size(); i++) {
				quotationCount += speakers.get(i)._1;
				if (quotationCount >= target) {
					speakers = speakers.subList(0, i + 1);
					break;
				}
			}
			
			List<List<String>> finalSpeakers = speakers.stream()
				.map(x -> x._2)
				.collect(Collectors.toList());*/
			
			List<List<String>> finalSpeakers = Arrays.asList("Sarah Palin", "John Boehner", "Mohamed ElBaradei", "John McCain",
					"Angela Merkel", "Benjamin Netanyahu", "Vladimir Putin", "Guido Westerwelle",
					"Charlie Sheen", "Mark Zuckerberg", "Roger Federer", "Julian Assange", "Mahmoud Ahmadinejad")
					.stream()
					.map(x -> Arrays.asList(x.split(" ")))
					.collect(Collectors.toList());
			
			List<JsonObject> mTurkList = pairs.filter(x -> finalSpeakers.contains(x._3()))
				.map(x -> {
					JsonObject o = new JsonObject();
					o.addProperty("uid", Long.toString(x._1().getArticleUid()));
					o.addProperty("idx", x._1().getIndex());
					o.addProperty("q", x._1().toHumanReadableString(true));
					o.addProperty("s", String.join(" ", x._2()));
					o.addProperty("sa", String.join(" ", x._3()));
					return o;
				})
				.collect();
			
			// Lexicographical sort (deterministic)
			mTurkList = new ArrayList<>(mTurkList);
			mTurkList.sort((x, y) -> GSON.toJson(x).compareTo(GSON.toJson(y)));
			
			Random rnd = new Random(10); // Deterministic
			
			
			Collections.shuffle(mTurkList, rnd);
			final int chunkSize = 9;
			int ptr = 0;
			// Split JSON file into chunks
			int batchId = 0;
			SampleSentenceGenerator gen = new SampleSentenceGenerator(rnd);
			BufferedWriter csvfile = new BufferedWriter(new FileWriter("mturk/hits.csv"));
			csvfile.write("index");
			csvfile.newLine();
			int[] recordIdx = {0};
			while (ptr < mTurkList.size()) {
				List<JsonObject> batch = new ArrayList<>(mTurkList.subList(ptr, Math.min(ptr + chunkSize, mTurkList.size())));
				
				// Build bogus question
				Tuple3<String, String, Boolean> bogus = gen.generate();
				JsonObject o = new JsonObject();
				o.addProperty("uid", batchId);
				o.addProperty("idx", bogus._3() ? 1 : 0);
				o.addProperty("q", bogus._1());
				o.addProperty("s", bogus._2());
				o.addProperty("sa", bogus._2());
				batch.add(o);
				
				Collections.shuffle(batch, rnd);
				// Write lineage data
				try (BufferedWriter outfile = new BufferedWriter(new FileWriter("mturk_lineage/" + batchId + ".json")))
				{
					batch.stream()
						.map(x -> {
							x.addProperty("i", recordIdx[0]++);
							return x;
						})
						.map(x -> GSON.toJson(x))
						.forEach(x -> {
							try {
								outfile.write(x);
								outfile.newLine();
							} catch (IOException e) {
								throw new IllegalStateException(e);
							}
						});
				}
				
				
				// Write user data
				try (BufferedWriter outfile = new BufferedWriter(new FileWriter("mturk/" + batchId + ".json")))
				{
					JsonArray arr = new JsonArray();
					batch.stream().forEach(x -> {
							x.remove("uid");
							x.remove("idx");
							x.remove("sa");
							arr.add(x);
						});
					outfile.write(GSON.toJson(arr));
				}
				
				csvfile.write(Integer.toString(batchId));
				csvfile.newLine();
				
				ptr += chunkSize;
				batchId++;
			}
			csvfile.close();
		}
		
	}
	
	public static List<String> findLongestSuperstring(String needle, List<List<String>> haystack) {
		List<String> bestMatch = null;
		for (List<String> candidate : haystack) {
			// TODO: search only at beginning and end maybe?
			if (candidate.size() > 1 && candidate.contains(needle)
					&& (bestMatch == null || candidate.size() > bestMatch.size())) {
				bestMatch = candidate;
			}
		}
		return bestMatch;
	}
}
