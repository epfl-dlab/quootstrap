package ch.epfl.dlab.quootstrap;

import java.util.Set;

import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import ch.epfl.dlab.spinn3r.converter.EntryWrapperBuilder;
import scala.Tuple4;

public class ParquetDatasetLoader implements DatasetLoader {

	private static SparkSession session;
	
	private static void initialize(JavaSparkContext sc) {
		if (session == null) {
			session = new SparkSession(sc.sc());
		}
	}
	
	@Override
	public JavaRDD<Article> loadArticles(JavaSparkContext sc, String datasetPath, Set<String> languageFilter) {
		ParquetDatasetLoader.initialize(sc);
		Dataset<Row> df = ParquetDatasetLoader.session.read().parquet(datasetPath);
		
		/* Expected schema:
		 * UID: long
		 * URL: String
		 * content: String
		 * html: String (empty string if absent)
		 * date: String
		 * lang: String
		 */
		
		return df.filter(df.col("lang").isin(languageFilter.toArray()))
			.select(df.col("UID"), df.col("URL"), df.col("content"), df.col("html"), df.col("date"))
			.map(x -> {
					long uid = x.getLong(0);
					String url = x.getString(1);
					String content = x.getString(2);
					String html = x.getString(3);
					String date = x.getString(4);
					boolean hasHtml = !html.isEmpty();
					
					String cleanedContent = hasHtml ? EntryWrapperBuilder.cleanContent(html, url) : content;
					return new Tuple4<>(uid, cleanedContent, url, date);
				}, Encoders.tuple(Encoders.LONG(), Encoders.STRING(), Encoders.STRING(), Encoders.STRING()))
			.javaRDD()
			.map(x -> new Spinn3rDatasetLoader.Article(x._1(), EntryWrapperBuilder.tokenize(x._2()), x._3(), x._4()));
	}
}
