package ch.epfl.dlab.quotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import ch.epfl.dlab.spinn3r.Tokenizer;
import ch.epfl.dlab.spinn3r.TokenizerImpl;
import scala.Tuple2;

public class ContextExtractor {

	public static List<Sentence> extractQuotations(List<String> tokenList, long articleUid) {
		String[] openTokens = {"``", "<q", "<blockquote"};
		String[] closeTokens = {"''", "</q>", "</blockquote>"};
		
		final int minQuotationSize = 8;
		final int maxQuotationSize = 100;
		final int minContextSize = 10; // At least N tokens in surrounding context
		final int maxContextSize = 40; // At most N tokens in surrounding context
		
		// Extract candidate quotations (without surrounding context)
		List<Tuple2<Integer, Integer>> candidateQuotations = new ArrayList<>(); // First token, last token
		int expectedToken = -1;
		int startIndex = -1;
		for (int t = 0; t < tokenList.size(); t++) {
			for (int i = 0; i < openTokens.length; i++) {
				if (tokenList.get(t).startsWith(openTokens[i])) {
					expectedToken = i;
					startIndex = t;
					break;
				}
			}
			if (expectedToken != -1) {
				for (int i = 0; i < closeTokens.length; i++) {
					if (tokenList.get(t).equals(closeTokens[i])) {
						if (i == expectedToken && t - startIndex >= minQuotationSize && t - startIndex <= maxQuotationSize) {
							// Consider only well-formed quotations (e.g. <q> must be followed by </q>, not </blockquote>)
							// In addition, when nested quotations are present, only the innermost one is extracted.
							candidateQuotations.add(new Tuple2<>(startIndex, t));
						}
						expectedToken = -1;
						startIndex = -1;
						break;
					}
				}
			}
		}
		
		Tokenizer tokenizer = new TokenizerImpl();
		
		// Extract surrounding context from quotation
		List<Sentence> quotations = new ArrayList<>();
		int articleIdx = 0;
		for (Tuple2<Integer, Integer> t : candidateQuotations) {
			
			List<Token> currentSentence = new ArrayList<>();
			
			List<String> prevContext = new ArrayList<>();
			scanBackward(tokenList, prevContext, t._1 - 1, minContextSize, maxContextSize);
			Collections.reverse(prevContext);
			prevContext.forEach(token -> {
				currentSentence.add(new Token(token, Token.Type.GENERIC));
			});
			
			List<String> quotation = new ArrayList<>();
			for (int i = t._1 + 1; i < t._2; i++) {
				quotation.add(tokenList.get(i));
			}
			
			String quotationStr = tokenizer.untokenize(quotation.stream()
				.filter(x -> !StaticRules.isHtmlTag(x))
				.collect(Collectors.toList()));

			currentSentence.add(new Token(quotationStr, Token.Type.QUOTATION));
			
			List<String> nextContext = new ArrayList<>();
			scanForward(tokenList, nextContext, t._2 + 1, minContextSize, maxContextSize);
			nextContext.forEach(token -> {
				currentSentence.add(new Token(token, Token.Type.GENERIC));
			});
			
			// Remove HTML tags
			// currentSentence.removeIf(x -> StaticRules.isHtmlTag(x.toString()));

			quotations.add(new Sentence(currentSentence, articleUid, articleIdx));
			articleIdx++;
		}
		
		return quotations;
	}
	
	private static int scanForward(List<String> tokens, List<String> context, int initialIndex, int minSize, int maxSize) {
		int finalIndexLower = Math.min(initialIndex + minSize, tokens.size() - 1);
		
		boolean anotherQuotationFound = false;
		for (int i = initialIndex; i < finalIndexLower; i++) {
			String token = tokens.get(i);
			if (token.equals("``")) {
				anotherQuotationFound = true;
			} else if (token.equals("''")) {
				anotherQuotationFound = false;
			}
			context.add(token);
		}
		
		int finalIndexUpper = Math.min(initialIndex + maxSize, tokens.size() - 1);
		for (int i = finalIndexLower; i < finalIndexUpper; i++) {
			String token = tokens.get(i);
			context.add(token);
			
			if (anotherQuotationFound) {
				if (token.equals("''")) {
					return i;
				}
			} else {
				if (token.equals(".") || token.equals("<br>") || token.equals("</p>")) {
					return i; 
				}
			}
		}
		// No stopper found -> revert to short context
		while (context.size() > minSize) {
			context.remove(context.size() - 1);
			finalIndexUpper--;
		}
		
		return finalIndexUpper;
	}
	
	private static int scanBackward(List<String> tokens, List<String> context, int initialIndex, int minSize, int maxSize) {
		int finalIndexLower = Math.max(initialIndex - minSize, 0);
		
		boolean anotherQuotationFound = false;
		for (int i = initialIndex; i > finalIndexLower; i--) {
			String token = tokens.get(i);
			if (token.equals("''")) {
				anotherQuotationFound = true;
			} else if (token.equals("``")) {
				anotherQuotationFound = false;
			}
			context.add(token);
		}
		
		int finalIndexUpper = Math.max(initialIndex - maxSize, 0);
		for (int i = finalIndexLower; i >= finalIndexUpper; i--) {
			String token = tokens.get(i);
			context.add(token);
			
			if (anotherQuotationFound) {
				if (token.equals("``")) {
					return i;
				}
			} else {
				if (token.equals(".") || token.equals("<br>") || token.startsWith("<p")) {
					return i;
				}
			}
		}
		// No stopper found -> revert to short context
		while (context.size() > minSize) {
			context.remove(context.size() - 1);
			finalIndexUpper++;
		}
		
		return finalIndexUpper;
	}
	
	public static Sentence postProcess(Sentence s) {
	
		List<Token> tokens = new ArrayList<>(s.getTokens());
		
		// Remove HTML tags
		tokens.removeIf(x -> StaticRules.isHtmlTag(x.toString()));
		
		// Canonicalize quotation, and push out final punctuation marks, if present inside quotation
		for (int i = 0; i < tokens.size(); i++) {
			if (tokens.get(i).getType() == Token.Type.QUOTATION) {
				String tokenStr = tokens.get(i).toString();
				final String[] patterns = {",", "."};
				for (String p : patterns) {
					if (tokenStr.endsWith(p)) {
						if (i == tokens.size() - 1 || !StaticRules.isPunctuation(tokens.get(i + 1).toString())) {
							// Push out
							tokens.add(i + 1, new Token(p, Token.Type.GENERIC));
						}
					}
				}
				tokens.set(i, new Token(StaticRules.canonicalizeQuotation(tokenStr), Token.Type.QUOTATION));
			}
		}
		
		// Find other quotations other than the main quotation
		int startQuot = 0;
		int i = 0;
		while (i < tokens.size()) {
			String t = tokens.get(i).toString();
			if (tokens.get(i).getType() == Token.Type.QUOTATION) {
				startQuot = -1;
			}
			if (t.equals("''") && startQuot != -1) {
				List<Token> sub = tokens.subList(startQuot, i + 1);
				sub.clear();
				sub.add(new Token("*QUOT*", Token.Type.GENERIC));
				startQuot = -1;
				i = startQuot;
			} else if ((t.equals("``"))) {
				startQuot = i;
			}
			i++;
		}
		if (startQuot != -1) {
			List<Token> sub = tokens.subList(startQuot, tokens.size());
			sub.clear();
			sub.add(new Token("*QUOT*", Token.Type.GENERIC));
		}
		
		return new Sentence(tokens, s.getArticleUid(), s.getIndex());
	}
	
}
