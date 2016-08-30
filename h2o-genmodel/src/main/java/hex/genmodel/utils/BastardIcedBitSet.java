package hex.genmodel.utils;

/**
 * BastardIcedBitSet - bastardized copy of water.utils.IcedBitSet
 */
public class BastardIcedBitSet {
    private byte[] _val;  // Holder of the bits, perhaps also holding other unrelated data
    private int _byteoff; // Number of bytes skipped before starting to count bits
    private int _nbits;   // Number of bits-in-a-row
    private int _bitoff;  // Number of bits discarded from beginning (inclusive min)

    public BastardIcedBitSet(int nbits) {
        this(nbits, 0);
    }

    public BastardIcedBitSet(int nbits, int bitoff) {
        // For small bitsets, just use a no-offset fixed-length format
        if (bitoff + nbits <= 32) {
            bitoff = 0;
            nbits = 32;
        }
        fill(nbits <= 0 ? null : new byte[bytes(nbits)], 0, nbits, bitoff);
    }

    // Fill in fields, with the bytes coming from some other large backing byte
    // array, which also contains other unrelated bits.
    public void fill(byte[] v, int byteoff, int nbits, int bitoff) {
        if (nbits < 0) throw new NegativeArraySizeException("nbits < 0: " + nbits);
        if (byteoff < 0) throw new IndexOutOfBoundsException("byteoff < 0: "+ byteoff);
        if (bitoff < 0) throw new IndexOutOfBoundsException("bitoff < 0: " + bitoff);
        assert v == null || byteoff + ((nbits-1) >> 3) + 1 <= v.length;
        _val = v;
        _nbits = nbits;
        _bitoff = bitoff;
        _byteoff = byteoff;
    }

    public boolean contains(int idx) {
        if (idx < 0) throw new IndexOutOfBoundsException("idx < 0: " + idx);
        idx -= _bitoff;
        return (idx >= 0) && (idx < _nbits) &&
               (_val[_byteoff + (idx >> 3)] & ((byte)1 << (idx & 7))) != 0;
    }

    public void fill2(byte[] bits, ByteBufferWrapper ab) {
        fill(bits, ab.position(), 32, 0);
        ab.skip(4);  // Skip inline bitset
    }

    // Reload IcedBitSet from AutoBuffer
    public void fill3(byte[] bits, ByteBufferWrapper ab) {
        int bitoff = ab.get2();
        int nbytes = ab.get2();
        fill(bits, ab.position(), nbytes<<3, bitoff);
        ab.skip(nbytes);  // Skip inline bitset
    }

    private static int bytes(int nbits) {
        return ((nbits-1) >> 3) + 1;
    }
}
