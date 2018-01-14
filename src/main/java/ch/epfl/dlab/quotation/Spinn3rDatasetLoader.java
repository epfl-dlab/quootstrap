package ch.epfl.dlab.quotation;

import java.util.List;
import java.util.Set;

import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;

import com.google.gson.Gson;

import ch.epfl.dlab.spinn3r.EntryWrapper;

public class Spinn3rDatasetLoader implements DatasetLoader {

	@Override
	public JavaRDD<DatasetLoader.Article> loadArticles(JavaSparkContext sc, String datasetPath, Set<String> languageFilter) {
		return sc.textFile(datasetPath)
			.map(x -> new Gson().fromJson(x, EntryWrapper.class).getPermalinkEntry())
			.filter(x -> languageFilter.contains(x.getLanguage()))
			.map(x -> new Article(Long.parseLong(x.getIdentifier()), x.getContent()));
	}
	
	public static class Article implements DatasetLoader.Article {

		private final long articleUID;
		private final List<String> articleContent;
		
		public Article(long articleUID, List<String> articleContent) {
			this.articleUID = articleUID;
			this.articleContent = articleContent;
		}
		
		@Override
		public long getArticleUID() {
			return articleUID;
		}

		@Override
		public List<String> getArticleContent() {
			return articleContent;
		}
		
	}

}
