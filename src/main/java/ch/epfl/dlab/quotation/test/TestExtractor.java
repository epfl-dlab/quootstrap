package ch.epfl.dlab.quotation.test;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import ch.epfl.dlab.quotation.Pattern;
import ch.epfl.dlab.quotation.PatternExtractor;
import ch.epfl.dlab.quotation.Sentence;
import ch.epfl.dlab.quotation.Token;

public class TestExtractor {

	private Sentence buildSentence(String str, String quotation) {
		String[] tokens = str.split(" ");
		List<Token> sentence = new ArrayList<>();
		for (String token : tokens) {
			if (token.equals("***")) {
				sentence.add(new Token(quotation, Token.Type.QUOTATION));
			} else {
				sentence.add(new Token(token, Token.Type.GENERIC));
			}
		}
		return new Sentence(sentence);
	}
	
	private List<Token> toTokenList(String str, Token.Type type) {
		return Arrays.stream(str.split(" "))
			.map(x -> new Token(x, type))
			.collect(Collectors.toList());
	}
	
	private String extract(String sentence, String quotation, String speaker) {
		Sentence s = buildSentence(sentence, "test");
		Pattern p = PatternExtractor.extractPattern(s,
				quotation,
				toTokenList(speaker, Token.Type.GENERIC));
		
		if (p == null) {
			return null;
		}
		return p.toString();
	}
	
	@Test
	public void test() {
		assertEquals(extract("*** , said John .", "test", "John"), "$Q , said $S .");
		assertEquals(extract("OK , *** , said John .", "test", "John"), "$Q , said $S .");
		assertNull(extract("OK , *** , said John .", "test", "Mark"));
		assertNull(extract("OK , *** , said .", "test", "John"));
		assertNull(extract("OK , said John . ***", "test2", "John"));
		assertNull(extract("*** , said John", "test", "John"));
		assertNull(extract("John said : ***", "test", "John"));
		assertEquals(extract(", John said : ***", "test", "John"), ", $S said : $Q");
		assertEquals(extract(", John said : *** .", "test", "John"), ", $S said : $Q");
		assertEquals(extract("Yesterday , John said : *** .", "test", "John"), ", $S said : $Q");
	}

}
