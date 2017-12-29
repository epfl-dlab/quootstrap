package ch.epfl.dlab.spinn3r;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizer;

public class TokenizerImpl implements Tokenizer {

	/**
	 * Configuration for Stanford PTBTokenizer.
	 */
	private static final String TOKENIZER_SETTINGS = "tokenizeNLs=false, americanize=false, " +
			"normalizeCurrency=false, normalizeParentheses=false," + 
			"normalizeOtherBrackets=false, unicodeQuotes=false, ptb3Ellipsis=true," + 
			"escapeForwardSlashAsterisk=false, untokenizable=noneKeep, normalizeSpace=false";
	
	
	@Override
	public List<String> tokenize(String sentence) {
		PTBTokenizer<CoreLabel> ptbt = new PTBTokenizer<>(new StringReader(sentence),
				new CoreLabelTokenFactory(), TOKENIZER_SETTINGS);
		List<String> tokens = new ArrayList<>();
		while (ptbt.hasNext()) {
			CoreLabel label = ptbt.next();
			tokens.add(label.toString());
		}
		return tokens;
	}

	@Override
	public String untokenize(List<String> tokens) {
		return PTBTokenizer.ptb2Text(tokens);
	}

}
