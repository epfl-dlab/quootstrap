package ch.epfl.dlab.quotation;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.broadcast.Broadcast;

import scala.Tuple2;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FreebaseExtractor {
	
	private static final Pattern PATTERN = Pattern.compile("^<([^>]+)>\\s+<([^>]+)>\\s+(<([^>]+)>|\"(.+)\"(@(([a-zA-Z\\-]+)))?)\\s+.$");
	
	
	public static void main(String[] args) {
		
		final SparkConf conf = new SparkConf()
				.setAppName(FreebaseExtractor.class.getName())
				.set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
				.set("spark.kryoserializer.buffer.max", "1g");
		
		try (JavaSparkContext sc = new JavaSparkContext(conf)) {
			JavaRDD<String[]> triples = sc.textFile(args[0])
				.map(x -> {
					Matcher m = PATTERN.matcher(x);
					if (m.matches()) {
						if (m.group(1) == null || m.group(2) == null) {
							System.out.println(x);
							throw new IllegalArgumentException("Invalid parsing");
						}
						
						if (m.group(4) != null && m.group(5) == null && m.group(6) == null && m.group(7) == null) {
							return new String[] { m.group(1), m.group(2), m.group(4) };
						} else if (m.group(4) == null && m.group(5) != null && m.group(7) == null) {
							return new String[] { m.group(1), m.group(2), m.group(5) };
						} else if (m.group(4) == null && m.group(5) != null && m.group(7) != null) {
							return new String[] { m.group(1), m.group(2), m.group(5), m.group(7) };
						} else {
							System.out.println(x);
							throw new IllegalArgumentException("Invalid value parsed");
						}
					}
					//System.out.println(x);
					//throw new IllegalArgumentException("No match");
					return null;
				})
				.filter(x -> x != null);
			
			// (ID, name)
			JavaPairRDD<String, String> names = triples.filter(x -> x[1].equals("http://rdf.freebase.com/ns/type.object.name"))
					.filter(x -> {
						if (x.length == 4) {
							return x[3].startsWith("en");
						}
						return true;
					})
					.mapToPair(x -> new Tuple2<>(x[0], x[2]))
					.cache();
			
			Set<String> relevantPeople = new HashSet<>(sc.textFile(args[1])
				.map(x -> x.split("\t")[0])
				.map(x -> x.substring(1, x.length() - 1))
				.collect());
			
			Broadcast<Set<String>> broadcast = sc.broadcast(relevantPeople);
			
			// (person ID, profession ID)
			triples.filter(x -> x[1].equals("http://rdf.freebase.com/ns/common.topic.notable_types"))
					.filter(x -> broadcast.value().contains(x[0])) // Select relevant people
					.mapToPair(x -> new Tuple2<>(x[0], x[2])) // (person ID, profession ID)
					.join(names) // (person ID, (profession ID, person name))
					.mapToPair(x -> new Tuple2<>(x._2._1, new Tuple2<>(x._1, x._2._2))) // (profession ID, (person ID, person name))
					.join(names) // (profession ID, ((person ID, person name), profession name)
					.map(x -> new String[] {"<" + x._2._1._1 + ">", x._2._1._2, x._2._2 }) // (person id, person name, profession name)
					.map(x -> String.join("\t", x))
					.coalesce(1)
					.saveAsTextFile(args[2]);
				
		}
	}
}
