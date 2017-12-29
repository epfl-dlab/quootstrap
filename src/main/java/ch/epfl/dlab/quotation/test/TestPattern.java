package ch.epfl.dlab.quotation.test;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import ch.epfl.dlab.quotation.HashTrie;
import ch.epfl.dlab.quotation.HashTriePatternMatcher;
import ch.epfl.dlab.quotation.Pattern;
import ch.epfl.dlab.quotation.PatternMatcher;
import ch.epfl.dlab.quotation.PatternMatcher.Match;
import ch.epfl.dlab.quotation.SimplePatternMatcher;
import ch.epfl.dlab.quotation.Sentence;
import ch.epfl.dlab.quotation.Token;
import ch.epfl.dlab.quotation.Trie;
import ch.epfl.dlab.quotation.TriePatternMatcher;

public class TestPattern {

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
	
	@Test
	public void test1() {
		Pattern p = new Pattern("$Q , said $S .");
		test1Impl(new SimplePatternMatcher(p, 2, 5));
		
		Trie t = new Trie(Arrays.asList(p));
		PatternMatcher pm = new TriePatternMatcher(t, 2, 5);
		test1Impl(pm);
		List<PatternMatcher.Match> matches = pm.getMatches(true);
		assertEquals(1, matches.size());
		assertEquals(p, matches.get(0).getPattern());
	}
	
	private void test1Impl(PatternMatcher m) {
		assertFalse(m.match(buildSentence("*** , said John . However...", "It works!")));
		assertTrue(m.match(buildSentence("*** , said John Doe . However...", "It works!")));
		assertFalse(m.match(buildSentence("*** , said Mr John Doe II Jr III . However...", "It works!")));
		assertFalse(m.match(buildSentence("However, *** , said John .", "It works!")));
		assertTrue(m.match(buildSentence("However, *** , said John Doe .", "It works!")));
		assertFalse(m.match(buildSentence("*** , said John .", "It works!")));
		assertTrue(m.match(buildSentence("*** , said John Doe .", "It works!")));
		assertFalse(m.match(buildSentence("*** , he said .", "Does it work?")));
		assertTrue(m.match(buildSentence("*** , said John Doe .", "It works!")));
		assertTrue(m.match(buildSentence("*** , said John Doe Jr .", "It works!")));
		assertFalse(m.match(buildSentence("*** , said John Doe Jr II X X X X .", "It works!")));
		assertFalse(m.match(buildSentence("However , *** , said John Doe Jr II X X X X .", "It works!")));
		assertTrue(m.match(buildSentence("However , *** , said John Doe II .", "It works!")));
		assertFalse(m.match(buildSentence("*** However said John Doe II . Or not ?", "It works!")));
		assertFalse(m.match(buildSentence("*** , said .", "It works!")));
		assertTrue(m.match(buildSentence("However , *** , said John Doe II . Or not ?", "It works!")));
	}
	
	@Test
	public void test2() {
		Pattern p = new Pattern(", $S said : $Q");
		test2Impl(new SimplePatternMatcher(p, 2, 5));
		
		Trie t = new Trie(Arrays.asList(p));
		PatternMatcher pm = new TriePatternMatcher(t, 2, 5);
		test2Impl(pm);
		List<PatternMatcher.Match> matches = pm.getMatches(true);
		assertEquals(1, matches.size());
		assertEquals(p, matches.get(0).getPattern());
	}
	
	private void test2Impl(PatternMatcher m) {
		assertFalse(m.match(buildSentence(", John said : ***", "Hi!")));
		assertTrue(m.match(buildSentence(", John Doe said : ***", "Hi!")));
		assertTrue(m.match(buildSentence(", Mr John Doe said : ***", "Hi!")));
		assertTrue(m.match(buildSentence("Yesterday , Mr John Doe said : ***", "Hi!")));
		assertFalse(m.match(buildSentence("Yesterday , Mr John Doe Jr X X X said : ***", "Hi!")));
		assertTrue(m.match(buildSentence("Yesterday , Mr John Doe said : *** .", "Hi!")));
	}
	
	private void hashTrieCase(String haystack, String expected, String... needles) {
		Sentence s = new Sentence(Arrays.asList(haystack.split(" ")).stream()
				.map(x -> new Token(x, Token.Type.GENERIC))
				.collect(Collectors.toList()), true);
		
		HashTrie t = new HashTrie(Arrays.asList(needles).stream()
			.map(x -> Arrays.asList(x.split(" ")))
			.collect(Collectors.toList()));
		
		HashTriePatternMatcher pm = new HashTriePatternMatcher(t);
		boolean match = pm.match(s);
		if (match && expected == null) {
			fail();
		}
		
		if (!match && expected != null) {
			fail();
		}
		
		if (match) {
			List<String> matched = pm.getLongestMatch();
			List<String> expectedList = Arrays.asList(expected.split(" "));
			assertEquals(expectedList, matched);
		}
	}
	
	@Test
	public void testHashTrie() {
		hashTrieCase("Hello John Doe !", "John Doe", "John Doe");
		hashTrieCase("Hello John Doe Jr !", "John Doe", "John Doe");
		hashTrieCase("Hello John Doe Jr !", "John Doe Jr", "John Doe", "John Doe Jr");
		hashTrieCase("Hello John Doe Jr !", "John Doe Jr", "John Doe Jr");
		hashTrieCase("Hello John Doe Jr II !", "John Doe Jr", "John Doe", "John Doe Jr");
		hashTrieCase("Hello John Doe Jr II !", "John Doe Jr", "John Doe", "John Doe Jr", "John", "John Bar");
		hashTrieCase("", null, "John Doe", "John Doe Jr II");
		hashTrieCase("Hello Mark Doe Jr II !", null, "John Doe", "John Doe Jr", "John Doe Jr II");
		hashTrieCase("Hello John Doe Jr II !", "John Doe Jr II", "John Doe", "John Doe Jr", "John Doe Jr II");
	}
	
	@Test
	public void testLongest() {
		Trie t = new Trie(Arrays.asList(new Pattern("$Q Mr $S said"), new Pattern("$Q $S said")));
		PatternMatcher pm = new TriePatternMatcher(t, 1, 5);
		assertTrue(pm.match(buildSentence("*** Mr John Doe said .", "Test")));
		List<Match> matches = pm.getMatches(true);
		assertEquals(1, matches.size());
		assertEquals(2, matches.get(0).getSpeaker().size());
		assertEquals("John", matches.get(0).getSpeaker().get(0).toString());
		assertEquals("Doe", matches.get(0).getSpeaker().get(1).toString());
	}

}
