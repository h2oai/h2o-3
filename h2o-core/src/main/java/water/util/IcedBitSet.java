package water.util;

import hex.genmodel.GenModel;
import water.AutoBuffer;
import water.Iced;

/** BitSet - Iced, meaning cheaply serialized over the wire.
 *
 *  <p>The bits are numbered starting at _offset - so there is an implicit
 *  offset built-in as a service; the offset can be zero.  This allows for an
 *  efficient representation if there is a known zero prefix of bits.
 *
 *  <p>The number of bits (after the zero offset) is also required - meaning
 *  this is a fixed-size (not-auto-sizing) bitset, and this bit offset is
 *  removed from all bit-indices.
 *
 *  <p>A number of bytes in the byte[] can be skipped also; this is value skips
 *  <em>bytes</em>, not bit indices, and is intended to allow an IcedBitSet to
 *  be embedded inside a large byte array containing unrelated data.
 */
public class IcedBitSet extends Iced {
  private byte[] _val;  // Holder of the bits, perhaps also holding other unrelated data
  private int _byteoff; // Number of bytes skipped before starting to count bits
  private int _nbits;   // Number of bits-in-a-row
  private int _bitoff;  // Number of bits discarded from beginning (inclusive min)

  public IcedBitSet(int nbits) { this(nbits, 0); }
  public IcedBitSet(int nbits, int bitoff) {
    // For small bitsets, just use a no-offset fixed-length format
    if( bitoff+nbits <= 32 ) {  bitoff = 0;  nbits = 32;  }
    int nbytes = bytes(nbits);
    fill(nbits <= 0 ? null : new byte[nbytes], 0, nbits, bitoff);
  }

  // Fill in fields, with the bytes coming from some other large backing byte
  // array, which also contains other unrelated bits.
  public void fill(byte[] v, int byteoff, int nbits, int bitoff) {
    if( nbits   < 0 ) throw new NegativeArraySizeException("nbits < 0: " + nbits  );
    if( byteoff < 0 ) throw new IndexOutOfBoundsException("byteoff < 0: "+ byteoff);
    if( bitoff  < 0 ) throw new IndexOutOfBoundsException("bitoff < 0: " + bitoff );
    assert(v.length >= bytes(nbits));
    assert v==null || byteoff+bytes(nbits) <= v.length;
    _val = v;
    _nbits = nbits;
    _bitoff = bitoff;
    _byteoff = byteoff;
  }

  public boolean isInRange(int idx) {
    idx -= _bitoff;
    return idx >= 0 && idx < _nbits;
  }

  public boolean contains(int idx) {
    idx -= _bitoff;
    assert (idx >= 0 && idx < _nbits): "Must have "+_bitoff+" <= idx <= " + (_bitoff+_nbits-1) + ": " + idx;
    return (_val[_byteoff+(idx >> 3)] & ((byte)1 << (idx & 7))) != 0;
  }

  /**
   * Activate the bit specified by the integer (must be from 0 ... _nbits-1)
   * @param idx
   */
  public void set(int idx) {
    idx -= _bitoff;
    assert (idx >= 0 && idx < _nbits): "Must have "+_bitoff+" <= idx <= " + (_bitoff+_nbits-1) + ": " + idx;
    _val[_byteoff+(idx >> 3)] |= ((byte)1 << (idx & 7));
  }
  public void clear(int idx) {
    idx -= _bitoff;
    assert (idx >= 0 && idx < _nbits) : "Must have 0 <= idx <= " + Integer.toString(_nbits-1) + ": " + idx;
    _val[_byteoff + (idx >> 3)] &= ~((byte)1 << (idx & 7));
  }
  public int cardinality() {
    int nbits = 0;
    int bytes = numBytes();
    for(int i = 0; i < bytes; i++)
      nbits += Integer.bitCount(0xFF&_val[_byteoff + i]);
    return nbits;
  }

  public int size() { return _nbits; }
  private static int bytes(int nbits) { return ((nbits-1) >> 3) + 1; }
  public int numBytes() { return bytes(_nbits); }
  public int max() { return _bitoff+_nbits; } // 1 larger than the largest bit allowed

  // Smaller compression format: just exactly 4 bytes
  public void compress2( AutoBuffer ab ) {
    assert max() <= 32;          // Expect a larger format
    assert _byteoff == 0;       // This is only set on loading a pre-existing IcedBitSet
    assert _val.length==4;
    ab.putA1(_val,4);
  }
  public void fill2( byte[] bits, AutoBuffer ab ) {
    fill(bits,ab.position(),32,0);
    ab.skip(4);            // Skip inline bitset
  }

  // Larger compression format: dump down bytes into the AutoBuffer.
  public void compress3( AutoBuffer ab ) {
    assert max() > 32;         // Expect a larger format
    assert _byteoff == 0;       // This is only set on loading a pre-existing IcedBitSet
    assert _val.length==numBytes();
    ab.put2((char)_bitoff);
    ab.put4(_nbits);
    ab.putA1(_val,_val.length);
  }

  // Reload IcedBitSet from AutoBuffer
  public void fill3( byte[] bits, AutoBuffer ab ) {
    int bitoff = ab.get2();
    int nbits =  ab.get4();
    fill(bits,ab.position(),nbits,bitoff);
    ab.skip(bytes(nbits));            // Skip inline bitset
  }

  @Override public String toString() { return toString(new SB()).toString(); }
  public SB toString(SB sb) {
    sb.p("{");
    if( _bitoff>0 ) sb.p("...").p(_bitoff).p(" 0-bits... ");

    int bytes = bytes(_nbits);
    for(int i = 0; i < bytes; i++) {
      if( i>0 && _bitoff + 8*i < size()) sb.p(' ');
      for( int j=0; j<8; j++ ) {
        if (_bitoff + 8*i + j >= size()) break;
        sb.p((_val[_byteoff + i] >> j) & 1);
      }
    }
    return sb.p("}");
  }
  public String toStrArray() {
    StringBuilder sb = new StringBuilder();
    sb.append("{").append(_val[_byteoff]);
    int bytes = bytes(_nbits);
    for(int i = 1; i < bytes; i++)
      sb.append(", ").append(_val[_byteoff+i]);
    sb.append("}");
    return sb.toString();
  }

  public SB toJava( SB sb, String varname, int col) {
    return sb.p("!GenModel.bitSetContains(").p(varname).p(", ").p(_bitoff).p(", data[").p(col).p("])");
  }

  public SB toJavaRangeCheck(SB sb, String varname, int col) {
    return sb.p("GenModel.bitSetIsInRange(").p(varname).p(", ").p(_bitoff).p(", data[").p(col).p("])");
  }
}
