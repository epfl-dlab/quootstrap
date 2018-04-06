package ch.epfl.dlab.quootstrap;

import java.io.Serializable;

/**
 * Implements a lightweight hashed object that contains
 * a 128-bit hash of a string (using MurmurHash3 128bit x64),
 * as well as its original length prior to hashing it.
 */
public final class Hashed implements Serializable, Comparable<Hashed> {

	private static final long serialVersionUID = -5391976195212991788L;
	
	private final long h1;
	private final long h2;
	private final int len;

	public Hashed(String s) {
		long h1 = 0;
		long h2 = 0;
		
		final byte[] key = s.getBytes();
		final int len = key.length;
		final int offset = 0;

		final long c1 = 0x87c37b91114253d5L;
		final long c2 = 0x4cf5ad432745937fL;

		int roundedEnd = offset + (len & 0xFFFFFFF0);
		for (int i = offset; i < roundedEnd; i += 16) {
			long k1 = arrayToLong(key, i);
			long k2 = arrayToLong(key, i+8);
			k1 *= c1;
			k1  = Long.rotateLeft(k1,31);
			k1 *= c2;
			h1 ^= k1;
			h1 = Long.rotateLeft(h1,27);
			h1 += h2;
			h1 = h1*5 + 0x52dce729;
			k2 *= c2;
			k2  = Long.rotateLeft(k2,33);
			k2 *= c1;
			h2 ^= k2;
			h2 = Long.rotateLeft(h2,31);
			h2 += h1;
			h2 = h2*5 + 0x38495ab5;
		}

		long k1 = 0;
		long k2 = 0;

		switch (len & 15) {
		case 15: k2  = (key[roundedEnd+14] & 0xffL) << 48;
		case 14: k2 |= (key[roundedEnd+13] & 0xffL) << 40;
		case 13: k2 |= (key[roundedEnd+12] & 0xffL) << 32;
		case 12: k2 |= (key[roundedEnd+11] & 0xffL) << 24;
		case 11: k2 |= (key[roundedEnd+10] & 0xffL) << 16;
		case 10: k2 |= (key[roundedEnd+ 9] & 0xffL) << 8;
		case  9: k2 |= (key[roundedEnd+ 8] & 0xffL);
			k2 *= c2;
			k2  = Long.rotateLeft(k2, 33);
			k2 *= c1;
			h2 ^= k2;
		case  8: k1  = ((long)key[roundedEnd+7]) << 56;
		case  7: k1 |= (key[roundedEnd+6] & 0xffL) << 48;
		case  6: k1 |= (key[roundedEnd+5] & 0xffL) << 40;
		case  5: k1 |= (key[roundedEnd+4] & 0xffL) << 32;
		case  4: k1 |= (key[roundedEnd+3] & 0xffL) << 24;
		case  3: k1 |= (key[roundedEnd+2] & 0xffL) << 16;
		case  2: k1 |= (key[roundedEnd+1] & 0xffL) << 8;
		case  1: k1 |= (key[roundedEnd  ] & 0xffL);
		k1 *= c1; k1  = Long.rotateLeft(k1,31); k1 *= c2; h1 ^= k1;
		}
		
		h1 ^= len; h2 ^= len;

		h1 += h2;
		h2 += h1;

		h1 = mix(h1);
		h2 = mix(h2);

		h1 += h2;
		h2 += h1;

		this.h1 = h1;
		this.h2 = h2;
		this.len = s.length();
	}

	private static long mix(long k) {
		k ^= k >>> 33;
		k *= 0xff51afd7ed558ccdL;
		k ^= k >>> 33;
		k *= 0xc4ceb9fe1a85ec53L;
		k ^= k >>> 33;
		return k;
	}

	private static long arrayToLong(byte[] buf, int offset) {
		return ((long)buf[offset+7] << 56)
				| ((buf[offset+6] & 0xffL) << 48)
				| ((buf[offset+5] & 0xffL) << 40)
				| ((buf[offset+4] & 0xffL) << 32)
				| ((buf[offset+3] & 0xffL) << 24)
				| ((buf[offset+2] & 0xffL) << 16)
				| ((buf[offset+1] & 0xffL) << 8)
				| ((buf[offset  ] & 0xffL));
	}
	
	@Override
	public String toString() {
		return String.format("%016X", h1) + String.format("%016X", h2) + ":" + len;
	}
	
	@Override
	public boolean equals(Object o) {
		if (o instanceof Hashed) {
			Hashed h = (Hashed) o;
			return h1 == h.h1 && h2 == h.h2 && len == h.len;
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		// Return the 32 least significant bits of the hash
		return (int) (h1 & 0x00000000ffffffff);
	}
	
	public int getLength() {
		return len;
	}

	@Override
	public int compareTo(Hashed o) {
		// First, compare by the original string length...
		if (len != o.len) {
			return Integer.compare(len, o.len);
		}
		
		// In case of a tie, define a lexicographical order based on the hash
		if (h1 != o.h1) {
			return Long.compare(h1, o.h1);
		}
		return Long.compare(h2, o.h2);
	}
	
}
