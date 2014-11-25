package water.util;

import water.Iced;
import water.H2O;

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
    fill( nbits <= 0 ? null : new byte[((nbits-1) >> 3) + 1], 0, nbits, bitoff);
  }

  public void fill(byte[] v, int byteoff, int nbits, int bitoff) {
    if( nbits   < 0 ) throw new NegativeArraySizeException("nbits < 0: " + nbits  );
    if( byteoff < 0 ) throw new IndexOutOfBoundsException("byteoff < 0: "+ byteoff);
    if( bitoff  < 0 ) throw new IndexOutOfBoundsException("bitoff < 0: " + bitoff );
    assert v==null || byteoff+((nbits-1) >> 3)+1 <= v.length;
    _val = v;  _nbits = nbits;  _bitoff = bitoff;  _byteoff = byteoff;
    if(  bitoff != 0 ) throw H2O.unimpl(); // TODO
    if( byteoff != 0 ) throw H2O.unimpl(); // TODO
  }

  public boolean get(int idx) {
    if(idx < 0 || idx >= _nbits)
      throw new IndexOutOfBoundsException("Must have 0 <= idx <= " + Integer.toString(_nbits-1) + ": " + idx);
    return (_val[idx >> 3] & ((byte)1 << (idx & 7))) != 0;
  }
  public boolean contains(int idx) {
    if(idx < 0) throw new IndexOutOfBoundsException("idx < 0: " + idx);
    if(idx >= _nbits) return false;
    return get(idx);
  }
  public void set(int idx) {
    if(idx < 0 || idx >= _nbits)
      throw new IndexOutOfBoundsException("Must have 0 <= idx <= " + Integer.toString(_nbits-1) + ": " + idx);
    _val[idx >> 3] |= ((byte)1 << (idx & 7));
  }
  public void clear(int idx) {
    if(idx < 0 || idx >= _nbits)
      throw new IndexOutOfBoundsException("Must have 0 <= idx <= " + Integer.toString(_nbits-1) + ": " + idx);
    _val[idx >> 3] &= ~((byte)1 << (idx & 7));
  }
  public int cardinality() {
    int nbits = 0;
    for(int i = 0; i < _val.length; i++)
      nbits += Integer.bitCount(_val[i]);
    return nbits;
  }

  public int nextSetBit(int idx) {
    if(idx < 0 || idx >= _nbits)
      throw new IndexOutOfBoundsException("Must have 0 <= idx <= " + Integer.toString(_nbits-1) + ": " + idx);
    int idx_next = idx >> 3;
    byte bt_next = (byte)(_val[idx_next] & ((byte)0xff << idx));

    while(bt_next == 0) {
      if(++idx_next >= _val.length) return -1;
      bt_next = _val[idx_next];
    }
    return (idx_next << 3) + Integer.numberOfTrailingZeros(bt_next);
  }

  public int nextClearBit(int idx) {
    if(idx < 0 || idx >= _nbits)
      throw new IndexOutOfBoundsException("Must have 0 <= idx <= " + Integer.toString(_nbits-1) + ": " + idx);
    int idx_next = idx >> 3;
    byte bt_next = (byte)(~_val[idx_next] & ((byte)0xff << idx));

    // Mask out leftmost bits not in use
    if(idx_next == _val.length-1 && (_nbits & 7) > 0)
      bt_next &= ~((byte)0xff << (_nbits & 7));

    while(bt_next == 0) {
      if(++idx_next >= _val.length) return -1;
      bt_next = (byte)(~_val[idx_next]);
      if(idx_next == _val.length-1 && (_nbits & 7) > 0)
        bt_next &= ~((byte)0xff << (_nbits & 7));
    }
    return (idx_next << 3) + Integer.numberOfTrailingZeros(bt_next);
  }

  public int size() { return _val.length << 3; }
  public int numBytes() { return _val.length; };

  @Override public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("{");
    if (_bitoff>0) sb.append("...").append(_bitoff).append(" 0-bits... ");

    for(int i = 0; i < _val.length; i++) {
      if (i>0) sb.append(' ');
      sb.append(String.format("%8s", Integer.toBinaryString(0xFF & _val[i])).replace(' ', '0'));
    }
    sb.append("}");
    return sb.toString();
  }
  public String toStrArray() {
    StringBuilder sb = new StringBuilder();
    sb.append("{").append(_val[0]);
    for(int i = 1; i < _val.length; i++)
      sb.append(", ").append(_val[i]);
    sb.append("}");
    return sb.toString();
  }
}
