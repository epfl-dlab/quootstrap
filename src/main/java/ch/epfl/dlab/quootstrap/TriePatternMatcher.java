package ch.epfl.dlab.quootstrap;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TriePatternMatcher extends PatternMatcher {
	
	private final Trie trie;
	private final List<Trie.Node> currentPattern;
	private double matchedConfidenceFactor;
	
	public TriePatternMatcher(Trie trie, int speakerLengthMin, int speakerLengthMax) {
		super(speakerLengthMin, speakerLengthMax, trie.isCaseSensitive());
		this.trie = trie;
		currentPattern = new ArrayList<>();
		matchedConfidenceFactor = Double.NaN;
	}
	
	@Override
	public boolean match(Sentence s) {
		sentenceTokens = s.getTokens();
		
		boolean result = false;
		matches.clear();
		for (int i = 0; i < sentenceTokens.size(); i++) {
		
			matchedConfidenceFactor = Double.NaN;
			if (sentenceTokens.get(i).getType() == Token.Type.GENERIC) {
				Trie.Node node = trie.getRootNode().getTextChild(sentenceTokens.get(i).toString());
				if (node != null) {
					currentPattern.add(node);
					if (matchImpl(node, i, maxSpeakerLength)) {
						result = true;
					}
					currentPattern.remove(currentPattern.size() - 1);
				}
			}
			for (Trie.Node node : trie.getRootNode()) {
				currentPattern.add(node);
				if (matchImpl(node, i, maxSpeakerLength)) {
					result = true;
				}
				currentPattern.remove(currentPattern.size() - 1);
			}
		}
		
		return result;
	}
	
	private Pattern getMatchedPattern() {
		return new Pattern(new ArrayList<>(currentPattern.stream()
			.map(x -> x.getToken())
			.collect(Collectors.toList())), matchedConfidenceFactor);
	}
	
	private boolean matchImplNext(Trie.Node node, int j, int speakerTokensLeft) {
		if (node.hasChildren()) {
			boolean result = false;
			
			if (j + 1 < sentenceTokens.size() && sentenceTokens.get(j + 1).getType() == Token.Type.GENERIC) {
				Trie.Node next = node.getTextChild(sentenceTokens.get(j + 1).toString());
				if (next != null) {
					currentPattern.add(next);
					if (matchImpl(next, j + 1, speakerTokensLeft)) {
						result = true;
					}
					currentPattern.remove(currentPattern.size() - 1);
				}
			}
			for (Trie.Node next : node) {
				currentPattern.add(next);
				
				if (matchImpl(next, j + 1, speakerTokensLeft)) {
					result = true;
				}
				currentPattern.remove(currentPattern.size() - 1);
			}
			return result;
		} else {
			// End of pattern reached (terminal node)
			matchedConfidenceFactor = node.getConfidenceFactor();
			matches.add(new Match(matchedQuotation, matchedSpeaker, getMatchedPattern()));
			return true;
		}
	}
	
	private boolean matchImpl(Trie.Node node, int j, int speakerTokensLeft) {
		if (j == sentenceTokens.size()) {
			// End of text reached. No matches are possible.
			return false;
		}
		
		if (matchTokens(node.getToken(), sentenceTokens.get(j), speakerTokensLeft)) {
			if (speakerTokenFoundFlag) {
				speakerTokenFoundFlag = false;
				boolean m1 = false;
				if (matchedSpeaker.size() >= minSpeakerLength) {
					m1 = matchImplNext(node, j, 0);
				}
				boolean m2 = matchImpl(node, j + 1, speakerTokensLeft - 1);
				matchedSpeaker.remove(matchedSpeaker.size() - 1);
				return m1 || m2;
				
			} else if (quotationTokenFoundFlag) {
				quotationTokenFoundFlag = false;
				boolean m = matchImplNext(node, j, speakerTokensLeft);
				matchedQuotation.remove(matchedQuotation.size() - 1);
				return m;
				
			} else {
				return matchImplNext(node, j, speakerTokensLeft);
			}
		}
		
		return false;
	}
}
