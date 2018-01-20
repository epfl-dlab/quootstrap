package ch.epfl.dlab.quotation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.spark.Accumulator;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;

import com.google.common.base.Optional;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import scala.Tuple2;
import scala.Tuple3;

public class GroundTruthEvaluator {

	private final JavaRDD<Record> rdd;
	private final JavaRDD<Sentence> sentences;
	private final JavaSparkContext sc;
	
	private JavaPairRDD<String, Tuple2<List<Token>, Integer>> transformedRDD;
	private List<List<Token>> speakers;
	
	public GroundTruthEvaluator(JavaSparkContext sc, JavaRDD<Sentence> sentences) {
		this.rdd = sc.textFile(ConfigManager.getInstance().getGroundTruthPath())
			.map(x -> new Record(x));
		this.sc = sc;
		this.sentences = sentences;
	}
	
	public JavaRDD<Record> getRDD() {
		return rdd;
	}
	
	public JavaPairRDD<Tuple2<Long, Integer>, List<Token>> getPairRDD() {
		return rdd.mapToPair(x -> new Tuple2<>(new Tuple2<>(x.uid, x.idx), x.speaker));
	}
	
	public void evaluate(JavaPairRDD<String, Tuple2<List<Token>, LineageInfo>> testPairs, int iteration) {
		
		final List<List<Token>> speakers = getSpeakers();
		JavaPairRDD<String, Tuple2<List<Token>, LineageInfo>> matched = testPairs
				.mapValues(x -> new Tuple2<>(StaticRules.matchSpeakerApprox(x._1, speakers), x._2))
				.filter(x -> x._2._1.isPresent())
				.mapValues(x -> new Tuple2<>(x._1.get(), x._2));
		
		// This is a necessary evil
		JavaPairRDD<String, Tuple2<Optional<Tuple2<List<Token>, Integer>>, Optional<Tuple2<List<Token>, LineageInfo>>>> joinedRDD = getTransformedRDD()
			.fullOuterJoin(matched);
		
		JavaRDD<String> errors = joinedRDD.map(x -> {
			Optional<List<Token>> speakerReal = x._2._1.isPresent() ? Optional.of(x._2._1.get()._1) : Optional.absent();
				Optional<List<Token>> speakerPredicted = x._2._2.isPresent() ? Optional.of(x._2._2.get()._1) : Optional.absent();
				if (!speakerReal.equals(speakerPredicted)) {
					String strRet = "Expected " + speakerReal + "; Got " + speakerPredicted + "; " + x._1;
					if (speakerReal.isPresent()) {
						strRet = strRet + "; Redundancy: " + x._2._1.get()._2;
					}
					if (speakerPredicted.isPresent()) {
						strRet = strRet + "; Lineage: " + x._2._2.get()._2();
					}
					return strRet;
				}
				return null;
			})
			.filter(x -> x != null);
		
		Utils.dumpRDDLocal(errors, "errors" + iteration + ".txt");
		
		final Accumulator<Integer> labeledIncorrectly = sc.intAccumulator(0);
		final Accumulator<Integer> labeledCorrectly = sc.intAccumulator(0);
		final Accumulator<Integer> unlabeled = sc.intAccumulator(0);
		final Accumulator<Integer> labeledWrong = sc.intAccumulator(0);
		
		JavaPairRDD<List<Token>, Tuple3<Integer, Integer, Integer>> partialResult = joinedRDD.mapToPair(x -> {
				Optional<List<Token>> speakerReal = x._2._1.isPresent() ? Optional.of(x._2._1.get()._1) : Optional.absent();
				Optional<List<Token>> speakerPredicted = x._2._2.isPresent() ? Optional.of(x._2._2.get()._1) : Optional.absent();
				// Speaker, relevant documents, retrieved documents, relevant ^ retrieved documents
				if (!speakerReal.isPresent()) {
					// False positive (quotation incorrectly attributed to the speaker)
					labeledIncorrectly.add(1);
					return new Tuple2<>(speakerPredicted.get(), new Tuple3<>(0, 1, 0));
				}
				if (!speakerPredicted.isPresent()) {
					// False negative (quotation not attributed)
					unlabeled.add(1);
					return new Tuple2<>(speakerReal.get(), new Tuple3<>(1, 0, 0));
				}
				if (speakerReal.get().equals(speakerPredicted.get())) {
					// True positive (quotation attributed to the right speaker)
					labeledCorrectly.add(1);
					return new Tuple2<>(speakerReal.get(), new Tuple3<>(1, 1, 1));
				} else {
					// False positive (quotation attributed to the wrong speaker)
					labeledWrong.add(1);
					return new Tuple2<>(speakerReal.get(), new Tuple3<>(0, 1, 0));
				}
			})
			.reduceByKey((x, y) -> new Tuple3<>(x._1() + y._1(), x._2() + y._2(), x._3() + y._3()));
			
		Map<List<Token>, Tuple3<Double, Double, Integer>> result = partialResult
			.mapValues(x -> new Tuple3<>(checkNan((double)x._3()/x._2()), (double)x._3()/x._1(), x._2())) // Precision, recall, # relevant
			.collectAsMap();
		
		Tuple3<Integer, Integer, Integer> aggregateResults = partialResult.map(x -> x._2)
			.reduce((x, y) -> new Tuple3<>(x._1() + y._1(), x._2() + y._2(), x._3() + y._3()));
		
		JavaPairRDD<Integer, Tuple2<Integer, Integer>> recallByFrequency = joinedRDD.mapToPair(x -> {
				Optional<List<Token>> speakerReal = x._2._1.isPresent() ? Optional.of(x._2._1.get()._1) : Optional.absent();
				Optional<List<Token>> speakerPredicted = x._2._2.isPresent() ? Optional.of(x._2._2.get()._1) : Optional.absent();
				// Speaker, relevant documents, relevant ^ retrieved documents
				if (!speakerReal.isPresent()) {
					// False positive (quotation incorrectly attributed to the speaker)
					return null;
				}
				if (!speakerPredicted.isPresent()) {
					// False negative (quotation not attributed)
					return new Tuple2<>(x._2._1.get()._2, new Tuple2<>(1, 0));
				}
				if (speakerReal.get().equals(speakerPredicted.get())) {
					// True positive (quotation attributed to the right speaker)
					return new Tuple2<>(x._2._1.get()._2, new Tuple2<>(1, 1));
				} else {
					// False positive (quotation attributed to the wrong speaker)
					return null;
				}
			})
			.filter(x -> x != null)
			.reduceByKey((x, y) -> new Tuple2<>(x._1() + y._1(), x._2() + y._2()))
			.sortByKey();
		
		try {
			String fileName = ConfigManager.getInstance().getOutputPath() + "evaluation" + iteration + ".txt";
			FileUtils.deleteQuietly(new File(fileName));
			OutputStreamWriter outFile = new OutputStreamWriter(new FileOutputStream(fileName));
			outFile.write("speaker\tprecision\trecall\tnum_extracted\n");
			result.forEach((k, v) -> {
				String speaker = k.stream().map(x -> x.toString()).collect(Collectors.joining(" "));
				try {
					outFile.write(speaker + "\t" + v._1() + "\t" + v._2() + "\t"+v._3()+"\n");
				} catch (IOException e) {
				}
			});
			outFile.write("-----------------\n");
			double finalPrecision = (double)aggregateResults._3() / aggregateResults._2();
			double finalRecall = (double)aggregateResults._3() / aggregateResults._1();
			double f = fScore(finalPrecision, finalRecall, 0.5);
			double f1 = fScore(finalPrecision, finalRecall, 1.0);
			outFile.write("Precision: " + finalPrecision + "\n");
			outFile.write("Recall: " + finalRecall + "\n");
			outFile.write("F0.5 score: " + f + "\n");
			outFile.write("F1 score: " + f1 + "\n");
			outFile.write("-----------------\n");
			outFile.write("Unique valid quotations in ground truth: " + getTransformedRDD().count() + "\n");
			outFile.write("Attributed and labeled correctly: " + labeledCorrectly.value() + "\n");
			outFile.write("Not attributed: " + unlabeled.value() + "\n");
			outFile.write("Attributed to the wrong person: " + labeledWrong.value() + "\n");
			outFile.write("Attributed incorrectly: " + labeledIncorrectly.value() + "\n");
			outFile.write("-----------------\n");
			outFile.write("frequency\tnum_quotations\trecall\n");
			recallByFrequency.collect().forEach(x -> {
				try {
					outFile.write(x._1() + "\t" + x._2()._1() + "\t" + checkNan((double)x._2()._2()/x._2()._1()) + "\n");
				} catch (IOException e) {
				}
			});
			outFile.close();
		} catch (IOException e) {
		}

	}
	
	private static double checkNan(double val) {
		if (Double.isNaN(val)) {
			return 0;
		}
		return val;
	}
	
	private static double fScore(double precision, double recall, double beta) {
		return (1 + beta*beta) * precision * recall / (beta*beta*precision + recall);
	}
	
	private List<List<Token>> getSpeakers() {
		if (speakers != null) {
			return speakers;
		}
		return speakers = new ArrayList<>(rdd.map(x -> x.speaker).distinct().collect());
	}
	
	private JavaPairRDD<String, Tuple2<List<Token>, Integer>> getTransformedRDD() {
		if (transformedRDD != null) {
			return transformedRDD;
		}
		
		// Quotation, (speaker, number of occurrences)
		JavaPairRDD<String, Tuple2<List<Token>, Integer>> data = sentences
				.mapToPair(x -> new Tuple2<>(x.getKey(), x.getQuotation()))
				.join(getPairRDD()) // (uid, idx), (quotation, speaker)
				.mapToPair(x -> new Tuple2<>(x._2._1, x._2._2))
				.groupByKey()
				.mapToPair(x -> new Tuple2<>(x._1, Utils.maxFrequencyItem(x._2)))
				.filter(x -> x._2 != null)
				.coalesce(1);
		
		String suffix = ConfigManager.getInstance().getLangFilter()
				.stream()
				.sorted()
				.collect(Collectors.joining("-"));
		
		if (ConfigManager.getInstance().isMergingEnabled()) {
			suffix = "merged-" + suffix;
		}
		
		
		
		transformedRDD = Utils.loadCache(data, "groundtruth-" + suffix);
		Utils.dumpRDDLocal(transformedRDD.map(x -> x), "groundTruthDump.txt");
		return transformedRDD;
	}
	
	public void dumpGroundTruth() {
		Utils.dumpRDD(sentences
				.mapToPair(x -> new Tuple2<>(x.getKey(), new Tuple2<>(x.getQuotation(), x)))
				.join(getPairRDD()) // (uid, idx), (quotation, speaker)
				.mapToPair(x -> new Tuple2<>(x._2._1._1, new Tuple3<>(x._2._2, x._2._1._2, x._2._1._2.getKey()))),
						"ground_truth_dump.txt");
	}
	
	public void checkCompatibility() {
		sentences
			.mapToPair(x -> new Tuple2<>(x.getKey(), x.getQuotation()))
			.rightOuterJoin(getPairRDD())
			.foreach(x -> {
				if (!x._2._1.isPresent()) {
					throw new IllegalArgumentException("Incompatible ground truth: mismatch at " + x._1);
				}
			});
	}
	
	public static class Record implements Serializable {
		private static final long serialVersionUID = -1661736647814546848L;
		
		private final long uid;
		private final int idx;
		private final List<Token> speaker;
		
		public Record(String jsonString) {
			JsonObject obj = new JsonParser().parse(jsonString).getAsJsonObject();
			uid = Long.parseLong(obj.get("uid").getAsString());
			idx = obj.get("idx").getAsInt();
			speaker = Token.getTokens(Arrays.asList(obj.get("sa").getAsString().split(" ")));
		}
		
		public long getUid() {
			return uid;
		}
		
		public int getIdx() {
			return idx;
		}
		
		public List<Token> getSpeaker() {
			return Collections.unmodifiableList(speaker);
		}
	}

}
