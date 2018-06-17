package ch.epfl.dlab.quootstrap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public abstract class PatternMatcher {
	
	protected List<Token> sentenceTokens;
	protected boolean speakerTokenFoundFlag;
	protected boolean quotationTokenFoundFlag;
	
	protected final List<Token> matchedQuotation;
	protected final List<Token> matchedSpeaker;
	protected final List<Match> matches;
	
	protected final int minSpeakerLength;
	protected final int maxSpeakerLength;
	protected final boolean caseSensitive;
	
	protected PatternMatcher(int speakerLengthMin, int speakerLengthMax, boolean caseSensitive) {
		matchedQuotation = new ArrayList<>();
		matchedSpeaker = new ArrayList<>();
		minSpeakerLength = speakerLengthMin;
		maxSpeakerLength = speakerLengthMax;
		this.caseSensitive = caseSensitive;
		matches = new ArrayList<>();
	}
	
	public abstract boolean match(Sentence s);
	
	public List<Match> getMatches(boolean longest) {
		if (longest && !matches.isEmpty()) {
			// We want only the pattern with the highest cardinality (i.e. number of text tokens)
			final int longestLength = Collections.max(matches,
					(x, y) -> Integer.compare(x.getPattern().getCardinality(), y.getPattern().getCardinality()))
					.getPattern().getCardinality();
			
			return matches.stream()
				.filter(x -> x.getPattern().getCardinality() == longestLength)
				.collect(Collectors.toList());
		} else {
			return Collections.unmodifiableList(matches);
		}
	}
	
	protected final boolean matchTokens(Token patternToken, Token token, int speakerTokensLeft) {
		switch (patternToken.getType()) {
		case GENERIC:
			if (token.getType() == Token.Type.GENERIC) {
				String tokenStr = token.toString();
				String patternTokenStr = patternToken.toString();
				if (caseSensitive) {
					return tokenStr.equals(patternTokenStr);
				} else {
					return tokenStr.equalsIgnoreCase(patternTokenStr);
				}
			}
			return false;
		case SPEAKER:
			if (speakerTokensLeft > 0 && token.getType() == Token.Type.GENERIC) {
				speakerTokenFoundFlag = true;
				matchedSpeaker.add(token);
				return true;
			}
			return false;
		case QUOTATION:
			if (token.getType() == Token.Type.QUOTATION) {
				matchedQuotation.add(token);
				quotationTokenFoundFlag = true;
				return true;
			}
			return false;
		case ANY:
			return true;
		default:
			throw new IllegalStateException();
		}
	}
	
	public final static class Match {
		private final String matchedQuotation;
		private final List<Token> matchedSpeaker;
		private final Pattern matchedPattern;
		
		public Match(List<Token> quotation, List<Token> speaker, Pattern pattern) {
			if (quotation.size() != 1) {
				throw new IllegalArgumentException("Invalid quotation token");
			}
			matchedQuotation = quotation.get(0).toString();
			matchedSpeaker = new ArrayList<>(speaker);
			matchedPattern = pattern;
		}
		
		public final String getQuotation() {
			return matchedQuotation;
		}
		
		public final List<Token> getSpeaker() {
			return matchedSpeaker;
		}
		
		public final Pattern getPattern() {
			return matchedPattern;
		}
	}
	
}
