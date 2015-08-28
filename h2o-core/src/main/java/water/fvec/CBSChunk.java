package water.fvec;

import water.AutoBuffer;
import water.H2O;
import water.MemoryManager;

import java.util.Arrays;

/** A simple chunk for boolean values. In fact simple bit vector.
 *  Each boolean is represented by 2bits since we need to represent NA.
 */
public class CBSChunk extends Chunk {
  static protected final byte _NA  = 0x02; // Internal representation of NA
  static protected final int _OFF = 2;
  private byte _bpv;
  public byte bpv() { return _bpv; } //bits per value
  private byte _gap;// number of trailing unused bits in the end (== _len % 8, we allocate bytes, but our length i generally not multiple of 8)
  public byte gap() { return _gap; } //number of trailing unused bits in the end

  public CBSChunk(boolean [] vals) {
    int gap = 8 - vals.length % 8;
    int n = (vals.length >> 3) + (gap == 0?0:1);
    byte [] bytes = MemoryManager.malloc1(_OFF + n);
    bytes[0] = _gap = (byte)gap;
    bytes[1] = _bpv = 1;
    for(int i = 0; i < vals.length; ++i){
      if(vals[i]) {
        int j = _OFF + i / 8;
        int k = 8 - i % 8 - 1;
        bytes[j] = (byte)(bytes[j] | (1 << k));
      }
    }
    _mem = bytes;
    _start = -1;
    set_len(((_mem.length - _OFF)*8 - _gap) / _bpv); // number of boolean items
  }
  public CBSChunk(byte[] bs, byte gap, byte bpv) {
    assert gap < 8; assert bpv == 1 || bpv == 2;
    _mem = bs; _start = -1; _gap = gap; _bpv = bpv;
    set_len(((_mem.length - _OFF)*8 - _gap) / _bpv); // number of boolean items
  }
  @Override protected long at8_impl(int idx) {
    byte b = atb(idx);
    if( b == _NA ) throw new IllegalArgumentException("at8_abs but value is missing");
    return b;
  }
  @Override protected double atd_impl(int idx) {
    byte b = atb(idx);
    return b == _NA ? Double.NaN : b;
  }
  @Override protected final boolean isNA_impl( int i ) { return atb(i)==_NA; }
  protected byte atb(int idx) {
    int vpb = 8 / _bpv;  // values per byte (= 8 / bits_per_value)
    int bix = _OFF + idx / vpb; // byte index
    int off = _bpv * (idx % vpb);
    byte b   = _mem[bix];
    switch( _bpv ) {
      case 1: return read1b(b, off);
      case 2: return read2b(b, off);
      default: H2O.fail();
    }
    return -1;
  }
  @Override boolean set_impl(int idx, long l)   { return false; }
  @Override boolean set_impl(int idx, double d) { return false; }
  @Override boolean set_impl(int idx, float f ) { return false; }
  @Override boolean setNA_impl(int idx) {  return false; }
  @Override public NewChunk inflate_impl(NewChunk nc) {
    nc.set_sparseLen(nc.set_len(0));
    for (int i=0; i< _len; i++) {
      int res = atb(i);
      if (res == _NA) nc.addNA();
      else            nc.addNum(res,0);
    }
    return nc;
  }

  /** Writes 1bit from value into b at given offset and return b */
  public static byte write1b(byte b, byte val, int off) {
    val = (byte) ((val & 0x1) << (7-off));
    return (byte) (b | val);
  }
  /** Writes 2bits from value into b at given offset and return b */
  public static byte write2b(byte b, byte val, int off) {
    val = (byte) ((val & 0x3) << (6-off)); // 0000 00xx << (6-off)
    return (byte) (b | val);
  }

  /** Reads 1bit from given b in given offset. */
  public static byte read1b(byte b, int off) { return (byte) ((b >> (7-off)) & 0x1); }
  /** Reads 2bit from given b in given offset. */
  public static byte read2b(byte b, int off) { return (byte) ((b >> (6-off)) & 0x3); }

  /** Returns compressed len of the given array length if the value if represented by bpv-bits. */
  public static int clen(int values, int bpv) {
    int len = (values*bpv) >> 3;
    return values*bpv % 8 == 0 ? len : len + 1;
  }
  @Override double min() { return 0; }
  @Override double max() { return 1; }
  @Override public CBSChunk read_impl(AutoBuffer bb) {
    _mem   = bb.bufClose();
    _start = -1;  _cidx = -1;
    _gap   = _mem[0];
    _bpv   = _mem[1];
    set_len(((_mem.length - _OFF)*8 - _gap) / _bpv);
    return this;
  }
  @Override
  public boolean hasFloat() {return false;}
}
