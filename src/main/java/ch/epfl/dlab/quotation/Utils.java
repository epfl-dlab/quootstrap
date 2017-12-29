package ch.epfl.dlab.quotation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;

import ch.epfl.dlab.spinn3r.converter.ProtoToJson;

public class Utils {

	public static <T> JavaRDD<T> loadCache(JavaRDD<T> rdd, String fileName) {
		if (!ConfigManager.getInstance().isCacheEnabled()) {
			return rdd;
		}
		final String cacheDir = ConfigManager.getInstance().getCachePath();
		final String path = cacheDir + "/" + fileName;
		try {
			FileSystem hdfs = org.apache.hadoop.fs.FileSystem.get(rdd.context().hadoopConfiguration());
			Path hdfsPath = new Path(path);
			if (!hdfs.exists(hdfsPath)) {
				rdd.saveAsObjectFile(path);
			}
			return JavaSparkContext.fromSparkContext(rdd.context()).objectFile(path);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}

	}
	
	
	public static <T, U> JavaPairRDD<T, U> loadCache(JavaPairRDD<T, U> rdd, String fileName) {
		if (!ConfigManager.getInstance().isCacheEnabled()) {
			return rdd;
		}
		final String cacheDir = ConfigManager.getInstance().getCachePath();
		final String path = cacheDir + "/" + fileName;
		try {
			FileSystem hdfs = org.apache.hadoop.fs.FileSystem.get(rdd.context().hadoopConfiguration());
			Path hdfsPath = new Path(path);
			if (!hdfs.exists(hdfsPath)) {
				rdd.saveAsObjectFile(path);
			}
			return JavaPairRDD.fromJavaRDD(JavaSparkContext.fromSparkContext(rdd.context()).objectFile(path));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
	
	public static <T> void dumpRDD(JavaRDD<T> rdd, String fileName) {
		FileUtils.deleteQuietly(new File(fileName));
		rdd.saveAsTextFile(fileName);
		try {
			ProtoToJson.mergeHdfsFile(fileName);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
	
	public static <T> void dumpRDDLocal(JavaRDD<T> rdd, String fileName) {
		FileUtils.deleteQuietly(new File(fileName));
		List<String> lines = rdd.collect()
			.stream()
			.map(x -> x.toString())
			.collect(Collectors.toList());
		try {
			Files.write(Paths.get(fileName), lines);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
	
	public static <T> void dumpCollection(Collection<T> data, String fileName) {
		List<String> lines = data.stream()
			.map(x -> x.toString())
			.sorted()
			.collect(Collectors.toList());
		
		try {
			Files.write(Paths.get(fileName), lines);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
	
	public static <T, U> void dumpRDD(JavaPairRDD<T, U> rdd, String fileName) {
		FileUtils.deleteQuietly(new File(fileName));
		rdd.saveAsTextFile(fileName);
		try {
			ProtoToJson.mergeHdfsFile(fileName);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	
	public static <T> List<T> findLongestSuperstring(T needle, Iterable<List<T>> haystack) {
		List<T> bestMatch = null;
		for (List<T> candidate : haystack) {
			if (candidate.size() > 1 && candidate.contains(needle)
					&& (bestMatch == null || candidate.size() > bestMatch.size())) {
				bestMatch = candidate;
			}
		}
		return bestMatch;
	}
	
	public static <T> List<T> findLongestSuperstring(List<T> needle, Iterable<List<T>> haystack) {
		List<T> bestMatch = null;
		boolean dirty = false;
		for (List<T> candidate : haystack) {
			if (bestMatch == null || candidate.size() >= bestMatch.size()) {
				for (int i = 0; i < candidate.size() - needle.size() + 1; i++) {
					List<T> subCandidate = candidate.subList(i, i + needle.size());
					if (subCandidate.equals(needle)) {
						if (bestMatch == null || candidate.size() > bestMatch.size()) {
							bestMatch = candidate;
							dirty = false;
						} else {
							dirty = true;
						}
					}
				}
			}
		}
		
		// If we have multiple superstrings of the same length, return no match to avoid conflicts
		// e.g. "John Doe" could be extended to either "John Doe Jr" or "John Doe Sr"
		if (dirty) {
			return null;
		}
		return bestMatch;
	}
	
	public static <T> List<T> findUniqueSuperstring(List<T> needle, Iterable<List<T>> haystack) {
		List<T> bestMatch = null;
		for (List<T> candidate : haystack) {
			for (int i = 0; i < candidate.size() - needle.size() + 1; i++) {
				List<T> subCandidate = candidate.subList(i, i + needle.size());
				if (subCandidate.equals(needle)) {
					if (bestMatch != null) {
						return null; // Conflict detected
					}
					bestMatch = candidate;
					break; // Break the outer loop
				}
			}
		}

		return bestMatch;
	}
	
}
