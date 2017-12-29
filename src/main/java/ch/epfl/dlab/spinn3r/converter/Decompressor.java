package ch.epfl.dlab.spinn3r.converter;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

/**
 * This class implements an on-the-fly decoder for .tar.gz archives.
 * The stream is decompressed in memory, and the result is returned as an iterator,
 * without materializing the entire result.
 */
public class Decompressor extends AbstractDecoder<byte[]> {

	private final TarArchiveInputStream tar;
	
	/**
	 * Initializes this iterator using the given stream.
	 * @param inStream the stream to decode
	 */
	public Decompressor(InputStream inStream) {
		try {
			tar = new TarArchiveInputStream(new GzipCompressorInputStream(inStream));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to open stream", e);
		}
	}

	@Override
	protected byte[] getNextImpl() {
		try {
			TarArchiveEntry entry;
			while ((entry = tar.getNextTarEntry()) != null) {
				if (entry.isDirectory()) {
					continue;
				}
	
				byte[] data = new byte[tar.available()];
				tar.read(data);
				return data;
			}
			
			// Entry is null
			tar.close();
			return null;
		} catch (IOException e) {
			throw new IllegalStateException("Unable to read stream", e);
		}
	}
}
