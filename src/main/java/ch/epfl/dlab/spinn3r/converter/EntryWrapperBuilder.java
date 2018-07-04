package ch.epfl.dlab.spinn3r.converter;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.Inflater;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Whitelist;

import com.spinn3r.api.protobuf.ContentApi.Content;
import com.spinn3r.api.protobuf.ContentApi.Entry;
import com.spinn3r.api.protobuf.ContentApi.Feed;
import com.spinn3r.api.protobuf.ContentApi.FeedEntry;
import com.spinn3r.api.protobuf.ContentApi.PermalinkEntry;
import com.spinn3r.api.protobuf.ContentApi.Source;

import ch.epfl.dlab.spinn3r.EntryWrapper;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizer;

public abstract class EntryWrapperBuilder {

	/**
	 * HTML tags that are preserved.
	 * All tags that are not in this list are removed entirely (including their content).
	 */
	private static final String[] HTML_WHITELIST = { "a", "b", "blockquote", "br", "caption",
			"cite", "code", "col", "colgroup", "dd", "div", "dl", "dt", "em", "h1", "h2",
			"h3", "h4", "h5", "h6", "i", "center", "font", "abbr", "li", "ol", "p", "pre",
			"q", "small", "span", "strike", "strong", "sub", "sup", "table", "tbody", "td",
			"tfoot", "th", "thead", "tr", "u", "ul" };
	
	private static final Whitelist JSOUP_WHITELIST = new Whitelist()
			.addTags(HTML_WHITELIST)
			.addAttributes("a", "href", "title", "target", "rel")
            .addAttributes("blockquote", "cite")
            .addAttributes("col", "span", "width")
            .addAttributes("colgroup", "span", "width")
            .addAttributes("ol", "start", "type")
            .addAttributes("q", "cite")
            .addAttributes("table", "summary", "width")
            .addAttributes("td", "abbr", "axis", "colspan", "rowspan", "width")
            .addAttributes("th", "abbr", "axis", "colspan", "rowspan", "scope", "width")
            .addAttributes("ul", "type")
            .addProtocols("a", "href", "ftp", "http", "https", "mailto", "#")
            .addProtocols("blockquote", "cite", "http", "https")
            .addProtocols("cite", "cite", "http", "https")
            .addProtocols("q", "cite", "http", "https");
	
	/**
	 * HTML tags to retain in the final output (both tag, attribute, and contents).
	 * All tags that are not in this list are removed, but their content is preserved.
	 * This list is especially useful to remove structural tags such as <div>, without
	 * actually removing the content.
	 */
	private static final String[] HTML_RETAINED_TAGS = { "a", "blockquote", "br", "caption", "cite",
			"h1", "h2", "h3", "h4", "h5", "h6", "p", "q" };
	
	private static final String[] HTML_TAGS_TO_STRIP;
	
	/**
	 * Configuration for Stanford PTBTokenizer.
	 */
	private static final String TOKENIZER_SETTINGS = "tokenizeNLs=false, americanize=false, " +
			"normalizeCurrency=false, normalizeParentheses=false," + 
			"normalizeOtherBrackets=false, unicodeQuotes=false, ptb3Ellipsis=true," + 
			"escapeForwardSlashAsterisk=false, untokenizable=noneKeep, normalizeSpace=false";
	
	static {
		HTML_TAGS_TO_STRIP = Arrays.stream(HTML_WHITELIST)
				.filter(tag -> !Arrays.asList(HTML_RETAINED_TAGS).contains(tag))
				.toArray(String[]::new);
	}
	
	public static EntryWrapper buildFrom(Entry e, boolean clean, boolean tokenize) {
		EntryWrapper.SourceWrapper source = build(e.getSource());
		EntryWrapper.FeedWrapper feed = build(e.getFeed());
		EntryWrapper.FeedEntryWrapper feedEntry = build(e.getFeedEntry(), clean, tokenize);
		EntryWrapper.PermalinkEntryWrapper permalinkEntry = build(e.getPermalinkEntry(), clean, tokenize);
		
		return new EntryWrapper(source, feed, feedEntry, permalinkEntry);
	}
	
	private static EntryWrapper.SourceWrapper build(Source s) {
		String url = s.getCanonicalLink().getHref();
		String title = s.getTitle();
		String language = s.getLangCount() > 0 ? s.getLang(0).getCode() : "";
		String description = s.getDescription();
		String lastPosted = s.getLastPosted();
		String lastPublished = s.getLastPublished();
		String dateFound = s.getDateFound();
		String publisherType = s.getPublisherType();
		
		return new EntryWrapper.SourceWrapper(url, title, language, description, lastPosted,
				lastPublished, dateFound, publisherType);
	}

	private static EntryWrapper.FeedWrapper build(Feed f) {
		String url = f.getCanonicalLink().getHref();
		String title = f.getTitle();
		String language = f.getLangCount() > 0 ? f.getLang(0).getCode() : "";
		String lastPosted = f.getLastPosted();
		String lastPublished = f.getLastPublished();
		String dateFound = f.getDateFound();
		String channelUrl = f.getChannelLink().getHref();
		
		return new EntryWrapper.FeedWrapper(url, title, language, lastPosted, lastPublished,
				dateFound, channelUrl);
	}

	
	private static EntryWrapper.FeedEntryWrapper build(FeedEntry e, boolean clean, boolean tokenize) {
		String identifier = Long.toString(e.getIdentifier());
		String url = e.getCanonicalLink().getHref();
		String title = e.getTitle();
		String language = e.getLangCount() > 0 ? e.getLang(0).getCode() : "";
		String authorName;
		String authorEmail;
		if (e.getAuthorCount() > 0) {
			authorName = e.getAuthor(0).getName();
			authorEmail = e.getAuthor(0).getEmail();
		} else {
			authorName = "";
			authorEmail = "";
		}
		String lastPublished = e.getLastPublished();
		String dateFound = e.getDateFound();
		
		List<String> content;
		if (e.hasContent()) {
			String data = decompress(e.getContent());
			if (clean) {
				data = cleanContent(data, url);
			}
			if (tokenize) {
				content = tokenize(data);
			} else {
				content = new ArrayList<>();
				content.add(data);
			}
			
		} else {
			content = Collections.emptyList();
		}
		
		return new EntryWrapper.FeedEntryWrapper(identifier, url, title, language, authorName,
				authorEmail, lastPublished, dateFound, content);
	}	
	
	
	private static EntryWrapper.PermalinkEntryWrapper build(PermalinkEntry e, boolean clean, boolean tokenize) {
		String identifier = Long.toString(e.getIdentifier());
		String url = e.getCanonicalLink().getHref();
		String title = e.getTitle();
		String language = e.getLangCount() > 0 ? e.getLang(0).getCode() : "";
		String authorName;
		String authorEmail;
		if (e.getAuthorCount() > 0) {
			authorName = e.getAuthor(0).getName();
			authorEmail = e.getAuthor(0).getEmail();
		} else {
			authorName = "";
			authorEmail = "";
		}
		String lastPublished = e.getLastPublished();
		String dateFound = e.getDateFound();
		
		List<String> content;
		if (e.hasContentExtract()) {
			String data = decompress(e.getContentExtract());
			if (clean) {
				data = cleanContent(data, url);
			}
			if (tokenize) {
				content = tokenize(data);
			} else {
				content = new ArrayList<>();
				content.add(data);
			}
		} else {
			content = Collections.emptyList();
		}
		
		return new EntryWrapper.PermalinkEntryWrapper(identifier, url, title, language,
				authorName, authorEmail, lastPublished, dateFound, content);
	}
	
	/**
	 * Decompresses an entry compressed with zlib.
	 * @param c the Spinn3r content
	 * @return a plain UTF-8 string.
	 */
	private static String decompress(Content c) {
		if (c.getEncoding().equals("zlib")) {
			try {
				Inflater decompresser = new Inflater();
				decompresser.setInput(c.getData().toByteArray());
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();  
				byte[] buffer = new byte[1024];  
				while (!decompresser.finished()) {  
					int count = decompresser.inflate(buffer);  
				    outputStream.write(buffer, 0, count);  
				}
				decompresser.end();
				outputStream.close();
				return new String(outputStream.toByteArray(), "UTF-8");
			} catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		} else {
			return "";
		}
	}
	
	public static String cleanContent(String contentHtml, String url) {
		Cleaner cleaner = new Cleaner(JSOUP_WHITELIST);
		Document doc = Jsoup.parse(contentHtml, url);
		doc = cleaner.clean(doc);
		doc.select(String.join(",", HTML_TAGS_TO_STRIP)).unwrap();
		doc.outputSettings().prettyPrint(false);
		return doc.body().html();
	}
	
	public static List<String> tokenize(String content) {
		PTBTokenizer<CoreLabel> ptbt = new PTBTokenizer<>(new StringReader(content),
				new CoreLabelTokenFactory(), TOKENIZER_SETTINGS);
		List<String> tokens = new ArrayList<>();
		while (ptbt.hasNext()) {
			CoreLabel label = ptbt.next();
			tokens.add(label.toString());
		}
		return tokens;
	}
}
