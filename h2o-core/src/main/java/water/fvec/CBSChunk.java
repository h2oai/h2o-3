package water.fvec;

import water.MemoryManager;

/** A simple chunk for boolean values. In fact simple bit vector.
 *  Each boolean is represented by 2bits since we need to represent NA.
 */
public class CBSChunk extends Chunk {
  static protected final byte _NA  = 0x02; // Internal representation of NA
  static protected final int _OFF = 2;
  private transient byte _bpv;
  public byte bpv() { return _bpv; } //bits per value
  private transient byte _gap;// number of trailing unused bits in the end (== _len % 8, we allocate bytes, but our length i generally not multiple of 8)
  public byte gap() { return _gap; } //number of trailing unused bits in the end


  public CBSChunk(boolean [] vals) {
    _len = vals.length;
    _bpv = 1;
    int memlen = _len >> 3 + (_len & 7) == 0?0:1;
    _mem = MemoryManager.malloc1(memlen);
    _mem[0] = _gap = (byte)(vals.length & 7);
    _mem[1] = _bpv;

    for(int i = 0; i < vals.length; ++i)
      if(vals[i])write(i,(byte)1);
  }
  public CBSChunk(byte[] bs) { _mem = bs; initFromBytes(); }
  public CBSChunk(int len, int bpv) {
    _gap = (byte) ((8 - (len*bpv & 7)) & 7);
    int clen = CBSChunk._OFF + (len >> (3 - bpv + 1)) + (_gap == 0?0:1);
    byte [] bs = MemoryManager.malloc1(clen);
    bs[0] = _gap;
    bs[1] = _bpv = (byte)bpv;
    assert ((clen - _OFF) - (_gap == 0?0:1) == (len >> (3-bpv+1)));
    _mem = bs; _start = -1;
    _len = len;
  }

  @Override protected long at8_impl(int idx) {
    byte b = read(idx);
    if( b == _NA ) throw new IllegalArgumentException("at8_abs but value is missing");
    return b;
  }
  @Override protected double atd_impl(int idx) {
    byte b = read(idx);
    return b == _NA ? Double.NaN : b;
  }
  @Override protected final boolean isNA_impl( int i ) { return read(i)==_NA; }



  private void set_byte(int idx, byte val){
    int bix = _OFF + ((idx*_bpv)>>3); // byte index
    int off = _bpv*idx & 7; // index within the byte
    int mask = ~((1 | _bpv) << off);
    _mem[bix] = (byte)((_mem[bix] & mask) | (val << off)); // 1 or 3 for 1bit per value or 2 bits per value
  }
  void write(int idx, byte val){
    int bix = _OFF + ((idx*_bpv)>>3); // byte index
    int off = _bpv*idx & 7; // index within the byte
    write(bix, off,val);
  }

  protected byte read(int idx) {
    int bix = _OFF + ((idx*_bpv)>>3); // byte index
    int off = _bpv*idx & 7; // index within the byte
    int mask = (1 | _bpv); // 1 or 3 for 1bit per value or 2 bits per value
    return read(_mem[bix], off,mask);
  }

  @Override boolean set_impl(int idx, long l) {
    if (l == 1 || l == 0) {
      set_byte(idx, (byte)l);
      return true;
    }
    return false;
  }

  @Override boolean set_impl(int idx, double d) {
    if(Double.isNaN(d)) return setNA_impl(idx);
    if(d == 0 || d == 1) {
      set_byte(idx,(byte)d);
      return true;
    }
    return false;
  }
  @Override boolean set_impl(int idx, float f ) {
    if(Float.isNaN(f))
      return setNA_impl(idx);
    if(f == 0 || f == 1) {
      set_byte(idx,(byte)f);
      return true;
    }
    return false;
  }
  @Override boolean setNA_impl(int idx) {
    if(_bpv == 2) {
      set_byte(idx, _NA);
      return true;
    }
    return false;
  }

  private void processRow(int r, ChunkVisitor v){
    int i = read(r);
    if(i == _NA) v.addNAs(1);
    else v.addValue(i);
  }

  @Override public ChunkVisitor processRows(ChunkVisitor v, int from, int to){
    for(int i = from; i < to; ++i)
      processRow(i,v);
    return v;
  }

  @Override public ChunkVisitor processRows(ChunkVisitor v, int... rows){
    for(int i:rows)
      processRow(i,v);
    return v;
  }

//  /** Writes 1bit from value into b at given offset and return b */
//  public static byte write1b(byte b, byte val, int off) {
//    val = (byte) ((val & 0x1) << (7-off));
//    return (byte) (b | val);
//  }
//  /** Writes 2bits from value into b at given offset and return b */
//  public static byte write2b(byte b, byte val, int off) {
//    val = (byte) ((val & 0x3) << (6-off)); // 0000 00xx << (6-off)
//    return (byte) (b | val);
//  }

  private byte read(int b, int off, int mask){
    return (byte)((b >> off) & mask);
  }

  private byte write(int bix, int off, int val){
    return _mem[bix] |= (val << off);
  }

//  /** Reads 1bit from given b in given offset. */
//  public static byte read1b(byte b, int off) { return (byte) ((b >> (7-off)) & 0x1); }
//  /** Reads 2bit from given b in given offset. */
//  public static byte read2b(byte b, int off) { return (byte) ((b >> (6-off)) & 0x3); }

  /** Returns compressed len of the given array length if the value if represented by bpv-bits. */
  public static int clen(int values, int bpv) {
    int len = (values*bpv) >> 3;
    return values*bpv % 8 == 0 ? len : len + 1;
  }
  @Override double min() { return 0; }
  @Override double max() { return 1; }

  @Override protected final void initFromBytes () {
    _start = -1;  _cidx = -1;
    _gap   = _mem[0];
    _bpv   = _mem[1];
    set_len(((_mem.length - _OFF)*8 - _gap) / _bpv);
  }

  @Override
  public boolean hasFloat() {return false;}

}
