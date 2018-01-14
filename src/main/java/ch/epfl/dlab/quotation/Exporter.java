package ch.epfl.dlab.quotation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import scala.Tuple2;
import scala.Tuple4;

public class Exporter {

	private final JavaPairRDD<String, String> quotationMap;
	private final JavaPairRDD<List<Token>, String> freebaseMapping;
	
	public Exporter(JavaSparkContext sc, JavaRDD<Sentence> sentences, NameDatabase people)  {
		
		this.quotationMap = computeQuotationMap(sc, sentences);
		
		// Map speakers to their unique Freebase ID
		this.freebaseMapping = people.getPeopleRDD()
				.mapToPair(x -> new Tuple2<>(Token.getTokens(x._1), x._2));
	}
	
	private JavaPairRDD<String, String> computeQuotationMap(JavaSparkContext sc, JavaRDD<Sentence> sentences) {
		// Reconstruct quotations (from the lower-case canonical form to the full form)
		Set<String> langSet = new HashSet<>(ConfigManager.getInstance().getLangFilter());
		
		JavaPairRDD<Tuple2<Long, Integer>, String> canonical = sentences.mapToPair(x -> new Tuple2<>(x.getKey(), x.getQuotation()));
		
		JavaPairRDD<Tuple2<Long, Integer>, String> full = QuotationExtraction.getConcreteDatasetLoader().loadArticles(sc,
				ConfigManager.getInstance().getDatasetPath(), langSet)
			.flatMap(x -> ContextExtractor.extractQuotations(x.getArticleContent(), x.getArticleUID()))
			.mapToPair(x -> new Tuple2<>(x.getKey(), x.getQuotation()));
		
		return canonical.join(full)
			.mapToPair(x -> x._2)
			.reduceByKey((x, y) -> {
				// Out of multiple possibilities, get the longest quotation
				if (x.length() > y.length()) {
					return x;
				} else if (x.length() < y.length()) {
					return y;
				} else {
					// Lexicographical comparison to ensure determinism
					return x.compareTo(y) == -1 ? x : y;
				}
			});
		
	}
	
	public void exportResults(JavaPairRDD<String, Tuple2<List<Token>, LineageInfo>> pairs) {
		String exportPath = ConfigManager.getInstance().getExportPath();
		
		pairs.mapValues(x -> new Tuple2<>(x._1, x._2)) // (canonical quotation, (speaker, lineage info))
			.join(quotationMap)
			.mapToPair(x -> new Tuple2<>(x._2._1._1, new Tuple2<>(x._2._2, x._2._1._2))) // (full quotation, (speaker, lineage info))
			.join(freebaseMapping)
			.map(x -> new Tuple4<>(x._2._1._1, x._1, x._2._2, x._2._1._2)) // (quotation, speaker, Freebase ID of the speaker, lineage info)
			.map(x -> {
				JsonObject o = new JsonObject();
				o.addProperty("quotation", x._1());
				o.addProperty("speaker", String.join(" ", Token.getStrings(x._2())));
				o.addProperty("speakerID", x._3().substring(1, x._3().length() - 1)); // Remove < and > from the Freebase ID
				o.addProperty("confidence", x._4().getConfidence()); // Tuple confidence
				
				JsonArray occurrences = new JsonArray();
				for (int i = 0; i < x._4().getPatterns().size(); i++) {
					JsonObject occ = new JsonObject();
					occ.addProperty("articleUID", Long.toString(x._4().getSentences().get(i).getArticleUid()));
					occ.addProperty("articleOffset", x._4().getSentences().get(i).getIndex());
					occ.addProperty("extractedBy", x._4().getPatterns().get(i).toString(false));
					occ.addProperty("patternConfidence", x._4().getPatterns().get(i).getConfidenceMetric());
					occurrences.add(occ);
				}
				o.add("occurrences", occurrences);

				return new GsonBuilder().disableHtmlEscaping().create().toJson(o);
			})
			.saveAsTextFile(exportPath);
	}
	
}
