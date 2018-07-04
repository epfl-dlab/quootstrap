package ch.epfl.dlab.spinn3r.converter;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.util.LongAccumulator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import ch.epfl.dlab.spinn3r.EntryWrapper;
import scala.Tuple2;

public class ProtoToJson {
	
	private static final Gson JSON_BUILDER = new GsonBuilder().disableHtmlEscaping().create();
	
	public static void main(final String[] args) throws IOException {
		
		if (args.length < 2) {
			System.err.println("Usage: ProtoToJson <inputPath(s)> <outputFile> [optional arguments]");
			System.err.println("[--master=<master>] specifies a master if spark-submit is not used.");
			System.err.println("[--sample=<sample>] specifies the fraction of data to sample.");
			System.err.println("[--merge] merges HDFS output into one single file (if the application is run locally).");
			System.err.println("[--partitions=<n>] specifies the number of partitions to save (useful if compression is used)");
			System.err.println("[--compress=<codec>] compresses the output using the given codec (GzipCodec, Lz4Codec, BZip2Codec, SnappyCodec)");
			System.err.println("[--source-type=<type(s)>] specifies the source type(s) to extract (e.g. MAINSTREAM_NEWS). Separate with ;");
			System.err.println("[--clean=<true/false>] clean useless HTML tags (default: true)");
			System.err.println("[--tokenize=<true/false>] tokenize the output using Stanford PTBTokenizer (default: true)");
			System.err.println("[--remove-duplicates] try to remove articles with duplicate contents (costly operation)");
			System.err.println("Examples:");
			System.err.println("ProtoToJson dir/*.tar.gz out.json --master=local[*] --sample=0.1 --merge");
			System.err.println("ProtoToJson dir/*.tar.gz out.json --master=local[*] --partitions=1000 --compress=GzipCodec --source-type=MAINSTREAM_NEWS;FORUM");
			return;
		}
		
		final SparkConf conf = new SparkConf()
				.setAppName(ProtoToJson.class.getName())
				.set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
				.registerKryoClasses(new Class<?>[] { EntryWrapper.class, EntryWrapper.PermalinkEntryWrapper.class,
					EntryWrapper.FeedEntryWrapper.class, EntryWrapper.FeedWrapper.class, EntryWrapper.SourceWrapper.class, ArrayList.class,
					List.class });

		
		double samplingRate = 1.0;
		boolean merge = false;
		Optional<String> compressionCodec = Optional.empty();
		int numPartitions = 0;
		final List<String> allowedSources = new ArrayList<>();
		boolean clean = true;
		boolean tokenize = true;
		boolean removeDuplicates = false;
		for (int i = 2; i < args.length; i++) {
			String[] arg = args[i].split("=");
			switch (arg[0]) {
			case "--master":
				conf.setMaster(arg[1]);
				break;
			case "--sample":
				samplingRate = Double.parseDouble(arg[1]);
				break;
			case "--merge":
				merge = true;
				break;
			case "--partitions":
				numPartitions = Integer.parseInt(arg[1]);
				break;
			case "--compress":
				compressionCodec = Optional.of(arg[1]);
				break;
			case "--source-type":
				allowedSources.addAll(Arrays.asList(arg[1].split(";")));
				break;
			case "--clean":
				clean = Boolean.parseBoolean(arg[1]);
				break;
			case "--tokenize":
				tokenize = Boolean.parseBoolean(arg[1]);
				break;
			case "--remove-duplicates":
				removeDuplicates = true;
				break;
			}
		}
		
		// Clean-up
		if (merge) {
			FileUtils.deleteQuietly(new File(args[1]));
		}
		
		Stopwatch sw = new Stopwatch();
		
		try (JavaSparkContext sc = new JavaSparkContext(conf)) {
			// The accumulator is used to count the number of documents efficiently
			final LongAccumulator counter = sc.sc().longAccumulator();
			
			final boolean cleanFlag = clean;
			final boolean tokenizeFlag = tokenize;
			
			JavaRDD<EntryWrapper> rdd = sc.binaryFiles(args[0]).mapPartitions(it -> {
				return new CombinedDecoder(it, cleanFlag, tokenizeFlag);
			});
			
			if (!allowedSources.isEmpty()) {
				rdd = rdd.filter(x -> allowedSources.contains(x.getSource().getPublisherType()));
			}
			
			if (samplingRate < 1.0) {
				rdd = rdd.sample(false, samplingRate);
			}
			
			if (numPartitions > 0) {
				rdd = rdd.repartition(numPartitions);
			}
			
			if (removeDuplicates) {
				rdd = rdd.mapToPair(x -> new Tuple2<>(x.getPermalinkEntry().getContent(), x))
						.reduceByKey((x, y) -> {
							// Deterministic "distinct": return the article with the lowest ID
							long id1 = Long.parseLong(x.getPermalinkEntry().getIdentifier());
							long id2 = Long.parseLong(y.getPermalinkEntry().getIdentifier());
							return id1 < id2 ? x : y;
						})
						.map(x -> x._2);
			}
			
			JavaRDD<String> out = rdd.map(x -> {
						counter.add(1);
						// Convert the document to a JSON object
						return JSON_BUILDER.toJson(x);
					});
			
			if (compressionCodec.isPresent()) {
				try {
					out.saveAsTextFile(args[1],
							Class.forName("org.apache.hadoop.io.compress." + compressionCodec.get())
							.asSubclass(CompressionCodec.class));
				} catch (ClassNotFoundException e) {
					throw new IllegalArgumentException("Invalid compression codec", e);
				}
			} else {
				out.saveAsTextFile(args[1]);
			}
			
			long count = counter.value();
			System.out.println("Processed " + count + " documents");
			double time = sw.printTime();
			System.out.println((time / count * 1000) + " ms per document");
			System.out.println((count / time) + " documents per second");
		}
		
		// If the destination is not a HDFS path (i.e. a local one), we want a single file
		if (merge) {
			mergeHdfsFile(args[1]);
		}
	}
	
	/**
	 * Merges multiple HDFS chunks into one single file.
	 * @param fileName the file (directory) to merge
	 * @throws IOException
	 */
	public static void mergeHdfsFile(String fileName) throws IOException {
		System.out.println("Merging files...");
		Stopwatch stopwatch = new Stopwatch();
		
		BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(fileName + "_tmp"));
		File folder = new File(fileName);
		File[] files = folder.listFiles((dir, name) -> name.startsWith("part"));
		
		// Sort files by chunk ID
		Arrays.sort(files, (a, b) -> a.getPath().compareTo(b.getPath()));
		
		for (File file : files) {
			InputStream in = new FileInputStream(file.getPath());
			IOUtils.copyLarge(in, out);
			in.close();
		}
		out.close();
		FileUtils.deleteQuietly(new File(fileName));
		Files.move(Paths.get(fileName + "_tmp"), Paths.get(fileName));
		System.out.println("Merged " + files.length + " files.");
		stopwatch.printTime();
	}
}
