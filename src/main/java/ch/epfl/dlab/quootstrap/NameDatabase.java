package ch.epfl.dlab.quootstrap;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;

import scala.Tuple2;

public class NameDatabase implements Serializable {

	private static final long serialVersionUID = 4372945856241816022L;
	
	private final HashTrie trie;
	private transient JavaPairRDD<List<String>, String> peopleRDD; // Maps full name to its Freebase ID
	
	public NameDatabase(JavaSparkContext sc, String knowledgeFile) {
		List<List<String>> names = getNamesRDD(sc, knowledgeFile).collect();
		trie = new HashTrie(names);
	}
	
	private JavaRDD<List<String>> getNamesRDD(JavaSparkContext sc, String fileName) {
		peopleRDD = sc.textFile(fileName)
				.mapToPair(x -> {
					String[] chunks = x.split("\t");
					return new Tuple2<>(chunks[0], chunks[1]);
				})
				.flatMapValues(x -> Arrays.asList(x.split("\\|")))
				.filter(x -> x._2.length() > 0) // We want to avoid empty names
				.mapValues(x -> Arrays.asList(x.split(" ")))
				.filter(x -> x._2.size() > 1) // Number of tokens
				.mapToPair(Tuple2::swap)
				.reduceByKey((x, y) -> x.compareTo(y) == -1 ? x : y); // Deterministic distinct
		
		peopleRDD = Utils.loadCache(peopleRDD, "people-database");
		
		return peopleRDD.map(x -> x._1);
	}
	
	public JavaPairRDD<List<String>, String> getPeopleRDD() {
		return peopleRDD;
	}
	
	public HashTriePatternMatcher newMatcher() {
		return new HashTriePatternMatcher(trie);
	}
}
