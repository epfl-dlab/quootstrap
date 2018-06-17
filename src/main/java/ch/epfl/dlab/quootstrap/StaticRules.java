package ch.epfl.dlab.quootstrap;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class StaticRules {
	
	public static boolean isHtmlTag(String token) {
		return token.startsWith("<") && token.endsWith(">");
	}
	
	public static boolean isPunctuation(String token) {
		return token.equals(",") || token.equals(".");
	}
	
	public static String canonicalizeQuotation(String str) {
		StringBuilder sb = new StringBuilder();
		str.codePoints()
			.filter(c -> Character.isWhitespace(c) || Character.isLetterOrDigit(c))
			.map(c -> Character.isWhitespace(c) ? ' ' : c)
			.mapToObj(c -> Character.isAlphabetic(c) ? Character.toLowerCase(c) : c)
			.forEach(sb::appendCodePoint);
		
		return sb.toString()
			.trim()
			.replaceAll(" +", " "); // Remove double (or more) spaces
	}
	
	public static boolean matchSpeakerApprox(List<Token> first, List<Token> second,
			boolean caseSensitive) {
		if (second == null) {
			return false;
		}
		if (!caseSensitive) {
			first = Token.caseFold(first);
			second = Token.caseFold(second);
		}
		// Return true if they have at least one token in common
		return !Collections.disjoint(first, second);
	}
	
	public static Optional<List<Token>> matchSpeakerApprox(List<Token> first,
			Iterable<List<Token>> choices, boolean caseSensitive) {
		// Return the match with the highest number of tokens in common
		Optional<List<Token>> bestMatch = Optional.empty();
		int bestMatchLen = 0;
		boolean dirty = false; // Used to track conflicts
		for (List<Token> choice : choices) {
			int matches = 0;
			// O(n^2) loop, but it is fine since these lists are very small
			for (int i = 0; i < choice.size(); i++) {
				for (int j = 0; j < first.size(); j++) {
					boolean equals;
					if (caseSensitive) {
						equals = choice.get(i).equals(first.get(j));
					} else {
						equals = choice.get(i).equalsIgnoreCase(first.get(j));
					}
					if (equals) {
						matches++;
						break;
					}
				}
			}
			if (matches > bestMatchLen) {
				bestMatchLen = matches;
				bestMatch = Optional.of(choice);
				dirty = false;
			} else if (matches == bestMatchLen) {
				dirty = true;
			}
		}
		
		if (dirty && bestMatchLen > 1) {
			throw new IllegalStateException("Conflicting speakers during ground truth evaluation: "
					+ first + " " + bestMatch.get());
		}
		
		if (bestMatchLen >= 2) {
			return bestMatch;
		} else {
			return Optional.empty();
		}
	}
	
}