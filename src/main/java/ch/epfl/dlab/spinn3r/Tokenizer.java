package ch.epfl.dlab.spinn3r;

import java.util.List;

public interface Tokenizer {

	List<String> tokenize(String sentence);
	String untokenize(List<String> tokens);
	
}
