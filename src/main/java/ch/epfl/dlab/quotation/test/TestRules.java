package ch.epfl.dlab.quotation.test;

import static org.junit.Assert.*;

import org.junit.Test;

import ch.epfl.dlab.quotation.StaticRules;

public class TestRules {

	@Test
	public void testCanonical() {
		assertEquals("hi", StaticRules.canonicalizeQuotation("Hi!"));
		assertEquals("hi", StaticRules.canonicalizeQuotation("  Hi!  "));
		assertEquals("h i", StaticRules.canonicalizeQuotation("  H i!  "));
		assertEquals("h i", StaticRules.canonicalizeQuotation("  H   i!  "));
		assertEquals("h i", StaticRules.canonicalizeQuotation("  H   i!  . "));
		
		assertEquals("test", StaticRules.canonicalizeQuotation("tEsT"));
		assertEquals("tטst", StaticRules.canonicalizeQuotation("Tטst"));
		assertEquals("אטלעש", StaticRules.canonicalizeQuotation("אטלעש:"));
		assertEquals("טי", StaticRules.canonicalizeQuotation("ָֹ*"));
	}
	
	@Test
	public void testPostProcessor() {
		
	}

}
