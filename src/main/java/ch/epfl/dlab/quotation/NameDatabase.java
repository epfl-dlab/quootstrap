package ch.epfl.dlab.quotation;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;

public class NameDatabase implements Serializable {

	private static final long serialVersionUID = 4372945856241816022L;
	
	private final HashTrie trie;
	
	public NameDatabase(JavaSparkContext sc, String knowledgeFile) {
		List<List<String>> names = getNamesRDD(sc, knowledgeFile).collect();
		trie = new HashTrie(names);
	}
	
	private JavaRDD<List<String>> getNamesRDD(JavaSparkContext sc, String fileName) {
		return sc.textFile(fileName)
				.map(x -> x.split("\t")[1])
				.flatMap(x -> Arrays.asList(x.split("\\|")))
				.filter(x -> x.length() > 0)
				.map(x -> Arrays.asList(x.split(" ")))
				.filter(x -> x.size() > 1)
				.distinct();
	}
	
	public HashTriePatternMatcher newMatcher() {
		return new HashTriePatternMatcher(trie);
	}
}
