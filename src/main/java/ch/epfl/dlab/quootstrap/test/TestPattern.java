package ch.epfl.dlab.quootstrap.test;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import ch.epfl.dlab.quootstrap.HashTrie;
import ch.epfl.dlab.quootstrap.HashTriePatternMatcher;
import ch.epfl.dlab.quootstrap.Pattern;
import ch.epfl.dlab.quootstrap.PatternMatcher;
import ch.epfl.dlab.quootstrap.Sentence;
import ch.epfl.dlab.quootstrap.SimplePatternMatcher;
import ch.epfl.dlab.quootstrap.Token;
import ch.epfl.dlab.quootstrap.Trie;
import ch.epfl.dlab.quootstrap.TriePatternMatcher;
import ch.epfl.dlab.quootstrap.PatternMatcher.Match;

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
		test1Impl(new SimplePatternMatcher(p, 2, 5, true));
		
		Trie t = new Trie(Arrays.asList(p), true);
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
		test2Impl(new SimplePatternMatcher(p, 2, 5, true));
		
		Trie t = new Trie(Arrays.asList(p), true);
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
	
	@Test
	public void testCaseInsensitive() {
		Pattern p = new Pattern("$Q , said $S .");
		PatternMatcher spm = new SimplePatternMatcher(p, 2, 5, false);
		test1Impl(spm);
		test3Impl(spm);
		
		Trie t = new Trie(Arrays.asList(p), false);
		PatternMatcher pm = new TriePatternMatcher(t, 2, 5);
		test1Impl(pm);
		test3Impl(pm);
	}
	
	private void test3Impl(PatternMatcher m) {
		assertFalse(m.match(buildSentence("*** , said John . However...", "It works!")));
		assertFalse(m.match(buildSentence("*** , SAID John . However...", "It works!")));
		assertTrue(m.match(buildSentence("*** , SAID John Doe . However...", "It works!")));
		assertTrue(m.match(buildSentence("*** , saId John Doe . However...", "It works!")));
		assertFalse(m.match(buildSentence("*** , sad Mr John Doe II Jr III . However...", "It works!")));
	}
	
	private void hashTrieCase(boolean caseSensitive, String haystack, String expected,
			String... needles) {
		Sentence s = new Sentence(Arrays.asList(haystack.split(" ")).stream()
				.map(x -> new Token(x, Token.Type.GENERIC))
				.collect(Collectors.toList()), true);
		
		HashTrie t = new HashTrie(Arrays.asList(needles).stream()
			.map(x -> Arrays.asList(x.split(" ")))
			.collect(Collectors.toList()),
			caseSensitive);
		
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
		hashTrieCase(true, "Hello John Doe !", "John Doe", "John Doe");
		hashTrieCase(true, "Hello John Doe Jr !", "John Doe", "John Doe");
		hashTrieCase(true, "Hello John Doe Jr !", "John Doe Jr", "John Doe", "John Doe Jr");
		hashTrieCase(true, "Hello John Doe Jr !", "John Doe Jr", "John Doe Jr");
		hashTrieCase(true, "Hello John Doe Jr II !", "John Doe Jr", "John Doe", "John Doe Jr");
		hashTrieCase(true, "Hello John Doe Jr II !", "John Doe Jr", "John Doe", "John Doe Jr", "John", "John Bar");
		hashTrieCase(true, "", null, "John Doe", "John Doe Jr II");
		hashTrieCase(true, "Hello Mark Doe Jr II !", null, "John Doe", "John Doe Jr", "John Doe Jr II");
		hashTrieCase(true, "Hello John Doe Jr II !", "John Doe Jr II", "John Doe", "John Doe Jr", "John Doe Jr II");
		
		// Case sensitive/insensitive test
		hashTrieCase(true, "Hello John Doe !", null, "john doe");
		hashTrieCase(true, "Hello John Doe !", null, "John doe");
		hashTrieCase(true, "Hello John Doe !", null, "john Doe");
		hashTrieCase(false, "Hello John Doe !", "john doe", "john doe");
		hashTrieCase(false, "Hello John Doe !", "John doe", "John doe");
		hashTrieCase(false, "Hello John Doe !", "John Doe", "John Doe");
		hashTrieCase(false, "Hello John Doe !", null, "Jane Doe");
	}
	
	@Test
	public void testLongest() {
		Trie t = new Trie(Arrays.asList(new Pattern("$Q Mr $S said"), new Pattern("$Q $S said")), true);
		PatternMatcher pm = new TriePatternMatcher(t, 1, 5);
		assertTrue(pm.match(buildSentence("*** Mr John Doe said .", "Test")));
		List<Match> matches = pm.getMatches(true);
		assertEquals(1, matches.size());
		assertEquals(2, matches.get(0).getSpeaker().size());
		assertEquals("John", matches.get(0).getSpeaker().get(0).toString());
		assertEquals("Doe", matches.get(0).getSpeaker().get(1).toString());
	}

}
