package ch.epfl.dlab.quotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import scala.Tuple2;

public class HashTriePatternMatcher {

	private final HashTrie trie;
	private final List<String> currentMatch;
	
	public HashTriePatternMatcher(HashTrie trie) {
		this.trie = trie;
		currentMatch = new ArrayList<>();
	}
	
	public boolean match(List<Token> tokens) {
		List<Tuple2<Integer, List<String>>> longestMatches = new ArrayList<>();
		boolean result = false;
		for (int i = 0; i < tokens.size(); i++) {
			currentMatch.clear();
			if (matchImpl(tokens, trie.getRootNode(), i)) {
				result = true;
				longestMatches.add(new Tuple2<>(i, new ArrayList<>(currentMatch)));
			}
		}
		
		if (result) {
			// Get longest match out of multiple matches
			int maxLen = Collections.max(longestMatches, (x, y) -> Integer.compare(x._2.size(), y._2.size()))._2.size();
			longestMatches.removeIf(x -> x._2.size() != maxLen);
			
			currentMatch.clear();
			currentMatch.addAll(longestMatches.get(0)._2);
		}
		
		return result;
	}
	
	public List<List<String>> multiMatch(List<Token> tokens) {
		List<List<String>> matches = new ArrayList<>();
		for (int i = 0; i < tokens.size(); i++) {
			currentMatch.clear();
			if (matchImpl(tokens, trie.getRootNode(), i)) {
				matches.add(new ArrayList<>(currentMatch));
				i += currentMatch.size() - 1; // Avoid matching subsequences
			}
		}
		
		return matches;
	}
	
	public boolean match(Sentence s) {
		List<Token> tokens = s.getTokens();
		List<Tuple2<Integer, List<String>>> longestMatches = new ArrayList<>();
		boolean result = false;
		for (int i = 0; i < tokens.size(); i++) {
			currentMatch.clear();
			if (matchImpl(tokens, trie.getRootNode(), i)) {
				result = true;
				longestMatches.add(new Tuple2<>(i, new ArrayList<>(currentMatch)));
			}
		}
		
		if (result) {
			int maxLen = Collections.max(longestMatches, (x, y) -> Integer.compare(x._2.size(), y._2.size()))._2.size();
			longestMatches.removeIf(x -> x._2.size() != maxLen);
			
			// If there are multiple speakers with max length, select the one that is nearest to the quotation
			currentMatch.clear();
			if (longestMatches.size() > 1) {
				int quotationIdx = -1;
				for (int i = 0; i < tokens.size(); i++) {
					if (tokens.get(i).getType() == Token.Type.QUOTATION) {
						quotationIdx = i;
						break;
					}
				}
				final int qi = quotationIdx;
				Tuple2<Integer, List<String>> nearest = Collections.min(longestMatches, (x, y) -> {
						int delta1 = Math.abs(x._1 - qi);
						int delta2 = Math.abs(y._1 - qi);
						return Integer.compare(delta1, delta2);
					});
				currentMatch.addAll(nearest._2);
			} else {
				currentMatch.addAll(longestMatches.get(0)._2);
			}
		}
		
		return result;
	}
	
	public List<String> getLongestMatch() {
		return new ArrayList<>(currentMatch);
	}
	
	private boolean matchImpl(List<Token> tokens, HashTrie.Node current, int i) {

		if (i == tokens.size()) { // TODO investigate why it was tokens.size() - 1
			return false;
		}
		
		if (tokens.get(i).getType() != Token.Type.GENERIC) {
			return false;
		}
		
		String tokenStr = tokens.get(i).toString();
		HashTrie.Node next = current.findChild(tokenStr);
		if (next != null) {
			currentMatch.add(tokenStr);
			boolean result = false;
			if (next.isTerminal()) {
				// Match found
				result = true;
			}
			
			// Even if a match is found, try to match a longer sequence
			if (matchImpl(tokens, next, i + 1) || result) {
				return true;
			}
			
			currentMatch.remove(currentMatch.size() - 1);
		}
		
		return false;
	}
}
