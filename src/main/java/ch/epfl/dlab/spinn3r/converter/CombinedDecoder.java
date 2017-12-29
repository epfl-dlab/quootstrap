package ch.epfl.dlab.spinn3r.converter;
import java.util.Iterator;

import org.apache.spark.input.PortableDataStream;

import ch.epfl.dlab.spinn3r.EntryWrapper;
import scala.Tuple2;

public class CombinedDecoder extends AbstractDecoder<EntryWrapper> {

	private final Iterator<Tuple2<String, PortableDataStream>> fileInput;
	private Decompressor decompressor;
	private SpinnerDecoder spinnerDecoder;
	private final boolean clean;
	private final boolean tokenize;
	
	/**
	 * Initialize this decoder.
	 * @param in The streams to decode and convert.
	 * @param clean Whether to clean or not useless HTML tags.
	 * @param tokenize Whether to tokenize the output.
	 */
	public CombinedDecoder(Iterator<Tuple2<String, PortableDataStream>> in, boolean clean, boolean tokenize) {
		fileInput = in;
		this.clean = clean;
		this.tokenize = tokenize;
	}
	
	@Override
	protected EntryWrapper getNextImpl() {
		while (true) {
			
			if (decompressor == null) {
				if (fileInput.hasNext()) {
					// Decompress next archive
					decompressor = new Decompressor(fileInput.next()._2.open());
				} else {
					return null;
				}
			}
			
			if (spinnerDecoder == null) {
				if (decompressor.hasNext()) {
					// Decode next file from archive
					spinnerDecoder = new SpinnerDecoder(decompressor.next());
				} else {
					decompressor = null;
				}
			}
			
			if (spinnerDecoder != null) {
				if (spinnerDecoder.hasNext()) {
					// Decode next entry from file
					return EntryWrapperBuilder.buildFrom(spinnerDecoder.next(), clean, tokenize);
				} else {
					spinnerDecoder = null;
				}
			}
		}
	}

}
