package ch.epfl.dlab.quootstrap;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import ch.epfl.dlab.spinn3r.Tokenizer;
import ch.epfl.dlab.spinn3r.TokenizerImpl;
import scala.Tuple2;

/**
 * A "Sentence" is an extracted context from an article.
 * It consists of:
 * - A *single* quotation between quotation marks (or equivalent HTML tags)
 * - The context surrounding the quotation (before and after)
 */
public final class Sentence implements Serializable, Iterable<Token>, Comparable<Sentence> {
	
	private static final long serialVersionUID = -4971549043386733522L;
	
	private final List<Token> tokens;
	private final long articleUid;
	private final int index;
	private transient int cachedHashCode;
	
	/**
	 * Constructs a new Sentence from the given list of tokens.
	 * @param tokens The list of tokens (must contain one quotation token)
	 * @param articleUid The UID of the article from which this sentence has been extracted
	 * @param index The index of this sentence within the article (starting from 0)
	 * @param skipChecks Skip safety checks (for debug or test purposes)
	 */
	public Sentence(List<Token> tokens, long articleUid, int index, boolean skipChecks) {
		this.tokens = tokens;
		this.articleUid = articleUid;
		this.index = index;
		if (!skipChecks) {
			sanityCheck();
		}
	}
	
	/**
	 * Constructs a new Sentence from the given list of tokens.
	 * @param tokens The list of tokens (must contain one quotation token)
	 * @param articleUid The UID of the article from which this sentence has been extracted
	 * @param index The index of this sentence within the article (starting from 0)
	 */
	public Sentence(List<Token> tokens, long articleUid, int index) {
		this(tokens, articleUid, index, false);
	}
	
	/**
	 * Constructs a new Sentence from the given list of tokens.
	 * @param tokens The list of tokens (must contain one quotation token)
	 */
	public Sentence(List<Token> tokens) {
		this(tokens, -1, -1, false);
	}
	
	/**
	 * Constructs a new Sentence from the given list of tokens.
	 * @param tokens The list of tokens (must contain one quotation token)
	 * @param skipChecks Skip safety checks (for debug or test purposes)
	 */
	public Sentence(List<Token> tokens, boolean skipChecks) {
		this(tokens, -1, -1, skipChecks);
	}
	
	public boolean matches(Predicate<Iterator<Token>> predicate) {
		return predicate.test(tokens.iterator());
	}
	
	public List<Token> getTokens() {
		return tokens;
	}
	
	public List<Token> getTokensByType(Token.Type type) {
		return tokens.stream()
				.filter(x -> x.getType() == type)
				.collect(Collectors.toCollection(ArrayList::new));
	}
	
	public String getQuotation() {
		for (Token t : tokens) {
			if (t.getType() == Token.Type.QUOTATION) {
				return t.toString();
			}
		}
		throw new IllegalStateException("No quotation found in this sentence");
	}
	
	public long getArticleUid() {
		return articleUid;
	}
	
	public int getIndex() {
		return index;
	}
	
	/**
	 * Gets the key that uniquely identifies this Sentence.
	 * @return An (Article UID, index within article) pair
	 */
	public Tuple2<Long, Integer> getKey() {
		return new Tuple2<>(articleUid, index);
	}
	
	public int getTokenCount() {
		return tokens.size();
	}
	
	@Override
	public String toString() {
		Iterator<Token> it = tokens.iterator();
		StringBuilder str = new StringBuilder();
		while (it.hasNext()) {
			Token next = it.next();
			if (next.getType() == Token.Type.QUOTATION) {
				str.append('[');
			}
			str.append(next.toString());
			if (next.getType() == Token.Type.QUOTATION) {
				str.append(']');
			}
			if (it.hasNext()) {
				str.append(' ');
			}
			
		}
		return str.toString();
	}
	
	public String toHumanReadableString(boolean htmlQuotation) {
		if (tokens.isEmpty()) {
			return "";
		}
		
		Tokenizer tokenizer = new TokenizerImpl();
		List<String> buffer = new ArrayList<>();
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < tokens.size(); i++) {
			Token t = tokens.get(i);
			if (t.getType() == Token.Type.GENERIC && StaticRules.isHtmlTag(t.toString())) {
				continue; // Discard HTML tags
			}
			
			if (i == 0 && t.getType() == Token.Type.GENERIC && (t.toString().equals(".") || t.toString().equals(","))) {
				continue; // Discard leading punctuation
			}
			
			if (t.getType() != Token.Type.QUOTATION) {
				buffer.add(t.toString());
			} else {
				if (!buffer.isEmpty()) {
					result.append(tokenizer.untokenize(buffer));
					result.append(" ");
				}
				
				if (htmlQuotation) {
					result.append("<q>" + t.toString() + "</q>");
				} else {
					result.append("\"" + t.toString() + "\"");
				}
				buffer.clear();
			}
		}
		if (!buffer.isEmpty()) {
			result.append(" ");
			result.append(tokenizer.untokenize(buffer));
		}
		
		
		return result.toString();
	}
	
	@Override
	public int hashCode() {
		// Racy single-check idiom
		int h = cachedHashCode;
		if (h == 0) {
			h = tokens.hashCode();
			cachedHashCode = h;
		}
		return h;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Sentence) {
			Sentence p = (Sentence) obj;
			return p.tokens.equals(tokens);
		}
		return false;
	}

	@Override
	public Iterator<Token> iterator() {
		return Collections.unmodifiableList(tokens).iterator();
	}

	@Override
	public int compareTo(Sentence other) {
		// Define a lexicographical order for sentences
		int n = Math.min(tokens.size(), other.tokens.size());
		for (int i = 0; i < n; i++) {
			int comp = tokens.get(i).compareTo(other.tokens.get(i));
			if (comp != 0) {
				return comp;
			}
		}
		return Integer.compare(tokens.size(), other.tokens.size());
	}
	
	private void sanityCheck() {
		if (tokens.isEmpty()) {
			throw new IllegalArgumentException("Invalid sentence: the sentence is empty");
		}
		
		boolean quotationFound = false;
		for (Token t : tokens) {
			if (t.getType() == Token.Type.QUOTATION) {
				if (quotationFound) {
					throw new IllegalArgumentException("Invalid sentence: more than one quotation placeholder found");
				}
				quotationFound = true;
			}
		}
		if (!quotationFound) {
			throw new IllegalArgumentException("Invalid sentence: no quotation placeholder found");
		}
	}
}
