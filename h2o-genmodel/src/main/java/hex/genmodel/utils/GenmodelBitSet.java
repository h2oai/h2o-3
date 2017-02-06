package hex.genmodel.utils;

/**
 * GenmodelBitSet - bitset that "lives" on top of an external byte array. It does not necessarily span the entire
 * byte array, and thus essentially provides a "bitset-view" on the underlying data stream.
 *
 * This is a bastardized copy of water.utils.IcedBitSet
 */
public class GenmodelBitSet {
    private byte[] _val;  // Holder of the bits, perhaps also holding other unrelated data
    private int _byteoff; // Number of bytes skipped before starting to count bits
    private int _nbits;   // Number of bits in this bitset
    private int _bitoff;  // Number of bits discarded from beginning (inclusive min)

    public GenmodelBitSet(int nbits) {
        this(nbits, 0);
    }

    public GenmodelBitSet(int nbits, int bitoff) {
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
        assert v == null || byteoff + bytes(nbits) <= v.length;
        _val = v;
        _nbits = nbits;
        _bitoff = bitoff;
        _byteoff = byteoff;
    }

    public boolean isInRange(int b) {
        b -= _bitoff;
        return b >= 0 && b < _nbits;
    }

    public boolean contains(int idx) {
        idx -= _bitoff;
        assert (idx >= 0 && idx < _nbits): "Must have "+_bitoff+" <= idx <= " + (_bitoff+_nbits-1) + ": " + idx;
        return (_val[_byteoff + (idx >> 3)] & ((byte)1 << (idx & 7))) != 0;
    }

    public void fill2(byte[] bits, ByteBufferWrapper ab) {
        fill(bits, ab.position(), 32, 0);
        ab.skip(4);  // Skip inline bitset
    }

    // Reload IcedBitSet from AutoBuffer
    public void fill3(byte[] bits, ByteBufferWrapper ab) {
        int bitoff = ab.get2();
        int nbits = ab.get4();
        fill(bits, ab.position(), nbits, bitoff);
        ab.skip(bytes(nbits));  // Skip inline bitset
    }

    private static int bytes(int nbits) {
        return ((nbits-1) >> 3) + 1;
    }

    /* SET IN STONE FOR MOJO VERSION "1.00" - DO NOT CHANGE */
    public boolean contains0(int idx) {
      if (idx < 0) throw new IndexOutOfBoundsException("idx < 0: " + idx);
      idx -= _bitoff;
      return (idx >= 0) && (idx < _nbits) &&
              (_val[_byteoff + (idx >> 3)] & ((byte)1 << (idx & 7))) != 0;
    }

    /* SET IN STONE FOR MOJO VERSION "1.10" AND OLDER - DO NOT CHANGE */
    public void fill3_1(byte[] bits, ByteBufferWrapper ab) {
      int bitoff = ab.get2();
      int nbytes = ab.get2();
      fill_1(bits, ab.position(), nbytes<<3, bitoff);
      ab.skip(nbytes);  // Skip inline bitset
    }

    /* SET IN STONE FOR MOJO VERSION "1.10" AND OLDER - DO NOT CHANGE */
    public void fill_1(byte[] v, int byteoff, int nbits, int bitoff) {
      if (nbits < 0) throw new NegativeArraySizeException("nbits < 0: " + nbits);
      if (byteoff < 0) throw new IndexOutOfBoundsException("byteoff < 0: "+ byteoff);
      if (bitoff < 0) throw new IndexOutOfBoundsException("bitoff < 0: " + bitoff);
      assert v == null || byteoff + ((nbits-1) >> 3) + 1 <= v.length;
      _val = v;
      _nbits = nbits;
      _bitoff = bitoff;
      _byteoff = byteoff;
    }
}
