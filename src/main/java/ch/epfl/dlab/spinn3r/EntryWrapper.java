package ch.epfl.dlab.spinn3r;
import java.io.Serializable;
import java.util.List;

/**
 * This apparently purpose-less class wraps Spinn3r entries
 * and is used as input for a JSON serializer such as Gson.
 */
public class EntryWrapper implements Serializable {

	private static final long serialVersionUID = 3319808914288405234L;
	
	private final SourceWrapper source;
	private final FeedWrapper feed;
	private final FeedEntryWrapper feedEntry;
	private final PermalinkEntryWrapper permalinkEntry;
	
	public EntryWrapper(SourceWrapper source, FeedWrapper feed, FeedEntryWrapper feedEntry,
			PermalinkEntryWrapper permalinkEntry) {
		this.source = source;
		this.feed = feed;
		this.feedEntry = feedEntry;
		this.permalinkEntry = permalinkEntry;
	}
	
	public SourceWrapper getSource() {
		return source;
	}

	public FeedWrapper getFeed() {
		return feed;
	}

	public FeedEntryWrapper getFeedEntry() {
		return feedEntry;
	}

	public PermalinkEntryWrapper getPermalinkEntry() {
		return permalinkEntry;
	}

	public static class SourceWrapper implements Serializable {
		
		private static final long serialVersionUID = 884787890709132785L;
		
		private final String url;
		private final String title;
		private final String language;
		private final String description;
		private final String lastPosted;
		private final String lastPublished;
		private final String dateFound;
		private final String publisherType;
		
		public SourceWrapper(String url, String title, String language, String description, String lastPosted,
				String lastPublished, String dateFound, String publisherType) {
			this.url = url;
			this.title = title;
			this.language = language;
			this.description = description;
			this.lastPosted = lastPosted;
			this.lastPublished = lastPublished;
			this.dateFound = dateFound;
			this.publisherType = publisherType;
		}

		public String getUrl() {
			return url;
		}

		public String getTitle() {
			return title;
		}

		public String getLanguage() {
			return language;
		}

		public String getDescription() {
			return description;
		}

		public String getLastPosted() {
			return lastPosted;
		}

		public String getLastPublished() {
			return lastPublished;
		}

		public String getDateFound() {
			return dateFound;
		}

		public String getPublisherType() {
			return publisherType;
		}
		
	}
	
	public static class FeedWrapper implements Serializable {
		
		private static final long serialVersionUID = 928980040260245901L;
		
		private final String url;
		private final String title;
		private final String language;
		private final String lastPosted;
		private final String lastPublished;
		private final String dateFound;
		private final String channelUrl;
		
		public FeedWrapper(String url, String title, String language, String lastPosted, String lastPublished,
				String dateFound, String channelUrl) {
			this.url = url;
			this.title = title;
			this.language = language;
			this.lastPosted = lastPosted;
			this.lastPublished = lastPublished;
			this.dateFound = dateFound;
			this.channelUrl = channelUrl;
		}

		public String getUrl() {
			return url;
		}

		public String getTitle() {
			return title;
		}

		public String getLanguage() {
			return language;
		}

		public String getLastPosted() {
			return lastPosted;
		}

		public String getLastPublished() {
			return lastPublished;
		}

		public String getDateFound() {
			return dateFound;
		}

		public String getChannelUrl() {
			return channelUrl;
		}
	}
	
	public static class FeedEntryWrapper implements Serializable {
		
		private static final long serialVersionUID = 5137190259805666093L;
		
		private final String identifier;
		private final String url;
		private final String title;
		private final String language;
		private final String authorName;
		private final String authorEmail;
		private final String lastPublished;
		private final String dateFound;
		private final List<String> tokenizedContent;
		
		public FeedEntryWrapper(String identifier, String url, String title, String language, String authorName,
				String authorEmail, String lastPublished, String dateFound, List<String> tokenizedContent) {
			super();
			this.identifier = identifier;
			this.url = url;
			this.title = title;
			this.language = language;
			this.authorName = authorName;
			this.authorEmail = authorEmail;
			this.lastPublished = lastPublished;
			this.dateFound = dateFound;
			this.tokenizedContent = tokenizedContent;
		}

		public String getIdentifier() {
			return identifier;
		}

		public String getUrl() {
			return url;
		}

		public String getTitle() {
			return title;
		}

		public String getLanguage() {
			return language;
		}

		public String getAuthorName() {
			return authorName;
		}

		public String getAuthorEmail() {
			return authorEmail;
		}

		public String getLastPublished() {
			return lastPublished;
		}

		public String getDateFound() {
			return dateFound;
		}

		public List<String> getContent() {
			return tokenizedContent;
		}
	}
	
	public static class PermalinkEntryWrapper implements Serializable {
		
		private static final long serialVersionUID = -1886188543863283140L;
		
		private final String identifier;
		private final String url;
		private final String title;
		private final String language;
		private final String authorName;
		private final String authorEmail;
		private final String lastPublished;
		private final String dateFound;
		private final List<String> tokenizedContent;
		
		public PermalinkEntryWrapper(String identifier, String url, String title, String language, String authorName,
				String authorEmail, String lastPublished, String dateFound, List<String> tokenizedContent) {
			this.identifier = identifier;
			this.url = url;
			this.title = title;
			this.language = language;
			this.authorName = authorName;
			this.authorEmail = authorEmail;
			this.lastPublished = lastPublished;
			this.dateFound = dateFound;
			this.tokenizedContent = tokenizedContent;
		}

		public String getIdentifier() {
			return identifier;
		}

		public String getUrl() {
			return url;
		}

		public String getTitle() {
			return title;
		}

		public String getLanguage() {
			return language;
		}

		public String getAuthorName() {
			return authorName;
		}

		public String getAuthorEmail() {
			return authorEmail;
		}

		public String getLastPublished() {
			return lastPublished;
		}

		public String getDateFound() {
			return dateFound;
		}

		public List<String> getContent() {
			return tokenizedContent;
		}
	}
}
