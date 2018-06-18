package ch.epfl.dlab.quootstrap;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;

/**
 * This class defines a pattern used for representing regular expressions.
 * In this context, a valid regular expression is defined by the following set of rules:
 * - The matching is done on a per-token basis (and not on a per-character basis).
 * - A token usually corresponds to a full word or to a punctuation mark.
 * 
 * The pattern should/could contain:
 * - Exactly one quotation token $Q
 * - Exactly one speaker token $S (which can match a variable number of tokens)
 * - Any number of text tokens (i.e. generic tokens), which are matched exactly
 * - Any number of "ANY" tokens $* (equivalent to "." in Perl), which are matched 1 time
 * 
 * Additionally, a pattern is subject to the following constraints:
 * - It must not start or end with an ANY token or a speaker token
 */
public final class Pattern implements Serializable, Iterable<Token>, Comparable<Pattern> {

	private static final long serialVersionUID = -3877164912177196115L;
	
	public static final String QUOTATION_PLACEHOLDER = "$Q";
	public static final String SPEAKER_PLACEHOLDER = "$S";
	public static final String ANY_PLACEHOLDER = "$*";
	
	private final List<Token> tokens;
	private final double confidenceMetric;
	
	public Pattern(String expression, double confidence) {
		tokens = new ArrayList<>();
		String[] strTokens = expression.split(" ");
		for (String token : strTokens) {
			Token next;
			switch (token) {
			case QUOTATION_PLACEHOLDER:
				next = new Token(null, Token.Type.QUOTATION);
				break;
			case SPEAKER_PLACEHOLDER:
				next = new Token(null, Token.Type.SPEAKER);
				break;
			case ANY_PLACEHOLDER:
				next = new Token(null, Token.Type.ANY);
				break;
			default:
				next = new Token(token, Token.Type.GENERIC);
				break;
			}
			this.tokens.add(next);
		}
		confidenceMetric = confidence;
		sanityCheck();
	}
	
	public Pattern(List<Token> tokens, double confidence) {
		this.tokens = new ArrayList<>(tokens); // Defensive copy
		this.confidenceMetric = confidence;
		sanityCheck();
	}
	
	public Pattern(List<Token> tokens) {
		this(tokens, Double.NaN);
	}
	
	public Pattern(String expression) {
		this(expression, Double.NaN);
	}
	
	public List<Token> getTokens() {
		return Collections.unmodifiableList(tokens);
	}
	
	public int getTokenCount() {
		return tokens.size();
	}
	
	/**
	 * @return the number of text tokens
	 */
	public int getCardinality() {
		int count = 0;
		for (Token t : tokens) {
			if (t.getType() == Token.Type.GENERIC) {
				count++;
			}
		}
		return count;
	}
	
	public double getConfidenceMetric() {
		return confidenceMetric;
	}
	
	public boolean isSpeakerSurroundedByAny() {
		for (int i = 0; i < tokens.size(); i++) {
			if (tokens.get(i).getType() == Token.Type.SPEAKER) {
				return tokens.get(i - 1).getType() == Token.Type.ANY
						|| tokens.get(i + 1).getType() == Token.Type.ANY;
			}
		}
		throw new IllegalStateException("Speaker not found in pattern");
	}
	
	private void sanityCheck() {
		if (tokens.isEmpty()) {
			throw new IllegalArgumentException("Invalid pattern: the pattern is empty");
		}
		boolean quotationFound = false;
		boolean speakerFound = false;
		for (Token t : tokens) {
			if (t.getType() == Token.Type.QUOTATION) {
				if (quotationFound) {
					throw new IllegalArgumentException("Invalid pattern: more than one quotation placeholder found");
				}
				quotationFound = true;
			} else if (t.getType() == Token.Type.SPEAKER) {
				if (speakerFound) {
					throw new IllegalArgumentException("Invalid pattern: more than one speaker placeholder found");
				}
				speakerFound = true;
			}
		}
		if (!quotationFound) {
			throw new IllegalArgumentException("Invalid pattern: no quotation placeholder found");
		}
		if (!speakerFound) {
			throw new IllegalArgumentException("Invalid pattern: no speaker placeholder found");
		}
		if (tokens.get(0).getType() == Token.Type.SPEAKER) {
			throw new IllegalArgumentException("Invalid pattern: the pattern must not start with a speaker placeholder");
		}
		if (tokens.get(tokens.size() - 1).getType() == Token.Type.SPEAKER) {
			throw new IllegalArgumentException("Invalid pattern: the pattern must not end with a speaker placeholder");
		}
		if (tokens.get(0).getType() == Token.Type.ANY) {
			throw new IllegalArgumentException("Invalid pattern: the pattern must not start with an 'any' placeholder");
		}
		if (tokens.get(tokens.size() - 1).getType() == Token.Type.ANY) {
			throw new IllegalArgumentException("Invalid pattern: the pattern must not end with an 'any' placeholder");
		}
	}
	
	@Override
	public String toString() {
		return toString(true);
	}
	
	public String toString(boolean addConfidence) {
		StringBuilder str = new StringBuilder();
		Iterator<Token> it = tokens.iterator();
		while (it.hasNext()) {
			Token t = it.next();
			switch (t.getType()) {
			case QUOTATION:
				str.append(QUOTATION_PLACEHOLDER);
				break;
			case SPEAKER:
				str.append(SPEAKER_PLACEHOLDER);
				break;
			case ANY:
				str.append(ANY_PLACEHOLDER);
				break;
			default:
				str.append(t.toString());
				break;
			}
			if (it.hasNext()) {
				str.append(' ');
			}
		}
		
		String output = str.toString();
		if (addConfidence && confidenceMetric >= 0) {
			output = output.replace("\"", "\\\""); // Escape character
			output = "[\"" + output + "\": " + confidenceMetric + "]";
		}
		return output;		
	}
	
	public static Pattern parse(String input) {
		java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^\\[\\\"(.*)\": ([0-9.eE]+)\\]$");
		Matcher m = pattern.matcher(input);
		if (m.matches() && m.groupCount() == 2) {
			String p = m.group(1).replace("\\\"", "\"");
			double confidence = Double.parseDouble(m.group(2));
			return new Pattern(p, confidence);
		}
		throw new IllegalArgumentException("Invalid pattern format: " + input);
	}

	@Override
	public int hashCode() {
		return tokens.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Pattern) {
			Pattern p = (Pattern) obj;
			// The comparison is done only on the content
			return p.tokens.equals(tokens);
		}
		return false;
	}

	@Override
	public Iterator<Token> iterator() {
		return Collections.unmodifiableList(tokens).iterator();
	}

	@Override
	public int compareTo(Pattern other) {
		// Define a lexicographical order for patterns
		int n = Math.min(tokens.size(), other.tokens.size());
		for (int i = 0; i < n; i++) {
			int comp = tokens.get(i).compareTo(other.tokens.get(i));
			if (comp != 0) {
				return comp;
			}
		}
		return Integer.compare(tokens.size(), other.tokens.size());
	}
}
