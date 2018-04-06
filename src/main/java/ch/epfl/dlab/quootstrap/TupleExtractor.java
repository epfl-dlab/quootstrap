package ch.epfl.dlab.quootstrap;

import java.util.Iterator;
import java.util.List;

import ch.epfl.dlab.spinn3r.converter.AbstractDecoder;
import scala.Tuple4;

public class TupleExtractor
	// Tuple: (quotation, speaker, full context, pattern that extracted the quotation)
	extends AbstractDecoder<Tuple4<String, List<Token>, Sentence, Pattern>> {

	private final Iterator<Sentence> it;
	private final PatternMatcher patternMatcher;
	
	private Iterator<PatternMatcher.Match> currentMatch;
	private Sentence currentSentence;
	
	public TupleExtractor(Iterator<Sentence> input, PatternMatcher pm) {
		it = input;
		patternMatcher = pm;
		currentMatch = null;
		currentSentence = null;
	}
	
	@Override
	protected Tuple4<String, List<Token>, Sentence, Pattern> getNextImpl() {
		while (true) {
			if (currentMatch == null) {
				if (it.hasNext()) {
					currentSentence = it.next();
					if (patternMatcher.match(currentSentence)) {
						currentMatch = patternMatcher.getMatches(false).iterator();
					}
				} else {
					// No more results
					return null;
				}
			}
			
			if (currentMatch != null) {
				if (currentMatch.hasNext()) {
					PatternMatcher.Match m = currentMatch.next();
					return new Tuple4<>(m.getQuotation(), m.getSpeaker(), currentSentence, m.getPattern());
				} else {
					currentMatch = null;
				}
			}
		}
	}
	
}
