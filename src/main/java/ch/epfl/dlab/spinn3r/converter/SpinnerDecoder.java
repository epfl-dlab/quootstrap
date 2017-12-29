package ch.epfl.dlab.spinn3r.converter;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import com.spinn3r.api.EntryDecoderFactory;
import com.spinn3r.api.protobuf.ContentApi;
import com.spinn3r.api.protobuf.ContentApi.Entry;
import com.spinn3r.api.util.Decoder;

public class SpinnerDecoder extends AbstractDecoder<Entry> {

	private final Decoder<ContentApi.Entry> decoder;
	
	public SpinnerDecoder(byte[] data) {
		EntryDecoderFactory factory = EntryDecoderFactory.newFactory();
		decoder = factory.get(new ByteArrayInputStream(data));
	}

	@Override
	protected Entry getNextImpl() {
		try {
			Entry decoded = decoder.read();
			if (decoded == null) {
				decoder.close();
			}
			return decoded;
		} catch (IOException e) {
			throw new IllegalStateException("Unable to read stream", e);
		}
	}

}
