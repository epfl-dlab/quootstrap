package ch.epfl.dlab.quotation.test;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import ch.epfl.dlab.quotation.Utils;

public class TestUtils {

	private List<String> tokenize(String str) {
		if (str == null) {
			return null;
		}
		return Arrays.asList(str.split(" "));
	}
	
	private void testSuper(String needle, String groundTruth, String... haystack) {
		List<List<String>> db = Arrays.asList(haystack).stream()
			.map(x -> tokenize(x))
			.collect(Collectors.toList());
		List<String> result = Utils.findLongestSuperstring(tokenize(needle), db);
		assertEquals(tokenize(groundTruth), result);
	}
	
	@Test
	public void testSuperstrings() {
		testSuper("John", "John Doe", "Mark", "Mark Doe Jr", "John Doe", "Mark Doe Sr.", "Bob", "Mr. Sir. Doe");
		testSuper("John Doe", "John Doe", "Mark", "Mark Doe Jr", "John Doe", "Mark Doe Sr.", "Bob", "Mr. Sir. Doe");
		testSuper("Mark", null, "Mark", "Mark Doe Jr", "John Doe", "Mark Doe Sr.", "Bob", "Mr. Sir. Doe");
		testSuper("Doe", "Mr Mark Doe Sr.", "Mr Mark Doe Sr.", "Mark Doe Jr", "John Doe", "Bob", "Mr. Sir. Doe");
		testSuper("Bob Doe", null, "Mr Mark Doe Sr.", "Mark Doe Jr", "John Doe", "Mr Mark Doe Sr.", "Bob", "Mr. Sir. Doe");
		testSuper("Alice Doe", null, "Mr Mark Doe Sr.", "Mark Doe Jr", "John Doe", "Mr Mark Doe Sr.", "Bob", "Mr. Sir. Doe");
		testSuper("Alice", null, "Mr Mark Doe Sr.", "Mark Doe Jr", "John Doe", "Mr Mark Doe Sr.", "Bob", "Mr. Sir. Doe");
	}
	
}
