package ch.epfl.dlab.quootstrap;

import java.util.ArrayList;
import java.util.List;

public class PatternExtractor {

	public static Pattern extractPattern(Sentence s, String quotation,
			List<Token> speaker, boolean caseSensitive) {
		
		int quotationPtr = 0;
		
		int indexOfSpeaker = getIndexOfSpeaker(s, speaker, caseSensitive);
		if (indexOfSpeaker == -1 || indexOfSpeaker == 0) {
			// If match not found, or, if match found at position 0...
			return null;
		}
		
		boolean patternStarted = false;
		boolean hasQuotation = false;
		boolean hasSpeaker = false;
		List<Token> tokens = s.getTokens();
		List<Token> patternTokens = new ArrayList<>();
		for (int i = 0; i < tokens.size(); i++) {
			
			if (i == indexOfSpeaker) {
				if (patternTokens.isEmpty()) {
					// The pattern cannot start with the speaker
					patternTokens.add(tokens.get(i - 1));
					patternStarted = true;
				}
				patternTokens.add(new Token(null, Token.Type.SPEAKER));
				i += speaker.size() - 1;
				hasSpeaker = true;
			} else {
				switch (tokens.get(i).getType()) {
				case QUOTATION:
					if (quotationPtr < 1 && tokens.get(i).toString().equals(quotation)) {
						patternStarted = true;
						quotationPtr++;
						patternTokens.add(new Token(null, Token.Type.QUOTATION));
					} else {
						return null;
					}
					if (quotationPtr == 1) {
						hasQuotation = true;
					}
					break;
				default:
					if (hasQuotation && hasSpeaker && patternTokens.get(patternTokens.size() - 1).getType() != Token.Type.SPEAKER) {
						patternStarted = false;
					}
					
					if (patternStarted) {
						patternTokens.add(tokens.get(i));
					}
					if (hasQuotation && hasSpeaker) {
						patternStarted = false;
					}
					break;
				}
			}
		}
		
		if (quotationPtr < 1) {
			// The quotation has not been matched entirely
			return null;
		}
		
		// Ensure that the speaker token is not last token in the pattern
		if (patternTokens.get(patternTokens.size() - 1).getType() == Token.Type.SPEAKER) {
			return null;
		}
		
		return new Pattern(patternTokens);
	}
	
	private static int getIndexOfSpeaker(Sentence s, List<Token> speaker, boolean caseSensitive) {
		List<Token> tokens = s.getTokens();
		
		if (!caseSensitive) {
			tokens = Token.caseFold(tokens);
			speaker = Token.caseFold(speaker);
		}
		
		for (Token t : speaker) {
			// Each token must be perfectly matched without duplicates (even if they are partial)
			int firstIndex = tokens.indexOf(t);
			int lastIndex = tokens.lastIndexOf(t);
			if (firstIndex == -1 || lastIndex != firstIndex) {
				return -1;
			}
		}
		
		for (int i = 0; i < tokens.size() - speaker.size() + 1; i++) {
			for (int j = 0; j < speaker.size(); j++) {
				if (!tokens.get(i + j).equals(speaker.get(j))) {
					break;
				}
				if (j == speaker.size() - 1) {
					// Match found!
					return i;
				}
			}
		}
		
		return -1;
	}
	
}
