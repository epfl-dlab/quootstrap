package ch.epfl.dlab.quootstrap;

import java.util.ArrayList;
import java.util.List;

public class SimplePatternMatcher extends PatternMatcher {
	
	private final List<Pattern> patterns;
	private List<Token> patternTokens;
	private Pattern currentPattern;
	
	public SimplePatternMatcher(Pattern pattern,
			int speakerLengthMin, int speakerLengthMax, boolean caseSensitive) {
		super(speakerLengthMin, speakerLengthMax, caseSensitive);
		this.patterns = new ArrayList<>();
		patterns.add(pattern);
	}
	
	public SimplePatternMatcher(List<Pattern> patterns,
			int speakerLengthMin, int speakerLengthMax, boolean caseSensitive) {
		super(speakerLengthMin, speakerLengthMax, caseSensitive);
		this.patterns = new ArrayList<>(patterns);
	}
	
	@Override
	public boolean match(Sentence s) {
		sentenceTokens = s.getTokens();

		matches.clear();
		boolean result = false;
		for (int i = 0; i < sentenceTokens.size(); i++) {
			for (Pattern currentPattern : patterns) {
				this.currentPattern = currentPattern;
				patternTokens = currentPattern.getTokens();
				
				if (matchImpl(0, i, maxSpeakerLength)) {
					result = true;
				}
				
				assert matchedQuotation.isEmpty();
				assert matchedSpeaker.isEmpty();
				assert !speakerTokenFoundFlag;
				assert !quotationTokenFoundFlag;
			}
		}
		
		return result;
	}
	
	private boolean matchImpl(int i, int j, int speakerTokensLeft) {
		if (i == patternTokens.size()) {
			// End of pattern reached
			matches.add(new Match(matchedQuotation, matchedSpeaker, currentPattern));
			return true;
		}
		
		if (j == sentenceTokens.size()) {
			// End of sentence reached: no match possible
			return false;
		}
		
		if (matchTokens(patternTokens.get(i), sentenceTokens.get(j), speakerTokensLeft)) {
			if (speakerTokenFoundFlag) {
				speakerTokenFoundFlag = false;
				boolean m1 = false;
				if (matchedSpeaker.size() >= minSpeakerLength) {
					m1 = matchImpl(i + 1, j + 1, 0);
				}
				boolean m2 = matchImpl(i, j + 1, speakerTokensLeft - 1);
				matchedSpeaker.remove(matchedSpeaker.size() - 1);
				return m1 || m2;
				
			} else if (quotationTokenFoundFlag) {
				quotationTokenFoundFlag = false;
				boolean m = matchImpl(i + 1, j + 1, speakerTokensLeft);
				matchedQuotation.remove(matchedQuotation.size() - 1);
				return m;
				
			} else {
				return matchImpl(i + 1, j + 1, speakerTokensLeft);
			}
		}
		
		return false;
	}
}
