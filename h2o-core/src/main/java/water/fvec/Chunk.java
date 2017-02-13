package water.fvec;

import water.parser.BufferedString;

import java.util.NoSuchElementException;

/**
 * Created by tomas on 2/4/17.
 */
public abstract class Chunk extends DBlock {
  @Override public Chunk[] chunks(){return new Chunk[]{this};}

  /**
   * Sparse bulk interface, stream through the compressed values and extract them into dense double array.
   * @param vals holds extracted values, length must be >= this.sparseLen()
   * @param vals holds extracted chunk-relative row ids, length must be >= this.sparseLen()
   * @return number of extracted (non-zero) elements, equal to sparseLen()
   */
  public int asSparseDoubles(double[] vals, int[] ids){return asSparseDoubles(vals,ids,Double.NaN);}

  public int asSparseDoubles(double [] vals, int [] ids, double NA) {
    getDoubles(vals,0,vals.length);
    for(int i = 0; i < vals.length; ++i) ids[i] = i;
    return vals.length;
  }

  /**
   * Dense bulk interface, fetch values from the given range
   * @param vals
   * @param from
   * @param to
   */
  public double [] getDoubles(double[] vals, int from, int to){ return getDoubles(vals,from,to, Double.NaN);}

  public double [] getDoubles(double [] vals, int from, int to, double NA){
    for(int i = from; i < to; ++i) {
      vals[i - from] = atd(i);
      if(Double.isNaN(vals[i-from]))
        vals[i - from] = NA;
    }
    return vals;
  }

  public int [] getIntegers(int [] vals, int from, int to, int NA){
    for(int i = from; i < to; ++i) {
      double d = atd(i);
      if(Double.isNaN(d))
        vals[i] = NA;
      else {
        vals[i] = (int)d;
        if(vals[i] != d) throw new IllegalArgumentException("Calling getIntegers on non-integer column");
      }
    }
    return vals;
  }

  /**
   * Dense bulk interface, fetch values from the given ids
   * @param vals
   * @param ids
   */
  public double[] getDoubles(double [] vals, int [] ids){
    int j = 0;
    for(int i:ids) vals[j++] = atd(i);
    return vals;
  }

  Chunk compress(){return this;}

  public boolean hasFloat(){return true;}

  public boolean hasNA(){return true;}

  public abstract Chunk deepCopy();

  /** ByteArraySupportedChunk-specific readers.  Not a public API */
  public abstract double atd(int idx);

  public abstract long at8(int idx);

  public int at4(int i) {
    long l = at8(i);
    int x = (int)l;
    if(x != l) throw new IllegalArgumentException("Value does not fit into int.");
    return x;
  }

  public abstract boolean isNA(int idx);

  public long at16l(int idx) { throw new IllegalArgumentException("Not a UUID"); }

  public long at16h(int idx) { throw new IllegalArgumentException("Not a UUID"); }

  public BufferedString atStr(BufferedString bStr, int idx) { throw new IllegalArgumentException("Not a String"); }

  /** Sparse Chunks have a significant number of zeros, and support for
   *  skipping over large runs of zeros in a row.
   *  @return true if this ByteArraySupportedChunk is sparse.  */
  public boolean isSparseZero() {return false;}

  /** Sparse Chunks have a significant number of NAs, and support for
   *  skipping over large runs of NAs in a row.
   *  @return true if this ByteArraySupportedChunk is sparseNA.  */
  public boolean isSparseNA() {return false;}



  // Next non-NA. Analogous to nextNZ()
  public int nextNNA(int rid){ return rid + 1;}

  /** Report the ByteArraySupportedChunk min-value (excluding NAs), or NaN if unknown.  Actual
   *  min can be higher than reported.  Used to short-cut RollupStats for
   *  constant and boolean chunks. */
  double min() { return Double.NaN; }

  /** Report the ByteArraySupportedChunk max-value (excluding NAs), or NaN if unknown.  Actual
   *  max can be lower than reported.  Used to short-cut RollupStats for
   *  constant and boolean chunks. */
  double max() { return Double.NaN; }

  /** @return String version of a ByteArraySupportedChunk, currently just the class name */
  @Override public String toString() { return getClass().getSimpleName(); }

  public long byteSize(){
    return 24; // object header + reference
  }

  /** Fixed-width format printing support.  Filled in by the subclasses. */
  public byte precision() { return -1; } // Digits after the decimal, or -1 for "all"

  NewChunk add2Chunk(NewChunk nc, int from, int to){
    DVal dv = new DVal();
    for(int i = from; i < to; ++i)
      nc.addInflated(getInflated(i,dv));
    return nc;
  }

  NewChunk add2Chunk(NewChunk nc, int[] rows){
    DVal dv = new DVal();
    for(int i:rows)
      nc.addInflated(getInflated(i,dv));
    return nc;
  }

  protected boolean set_impl(int row, double d) {
    if( Double.isNaN(d) ) return setNA_impl(row);
    long l = (long)d;
    return l == d && set_impl(row, l);
  }

  protected boolean set_impl(int row, long val) {return false;}

  protected boolean set_impl(int row, float val) {return set_impl(row,(double)val);}

  protected boolean set_impl(int row, String val) {return false;}

  protected boolean set_impl(int row, BufferedString val) {return set_impl(row,val.toString());}

  protected boolean setNA_impl(int row) {return false;}

  protected boolean set_impl(int row, long uuid_lo, long uuid_hi) {return false;}

  public abstract DVal getInflated(int i, DVal v);

  public boolean setInflated(int i, DVal v){ return false;}

  public void removeChunks(int [] ids){
    if(ids.length != 1 || ids[0] != 0)
      throw new IndexOutOfBoundsException();

  }

  @Override
  public Chunk getColChunk(int c){
    if(c != 0) throw new ArrayIndexOutOfBoundsException(c);
    return this;
  }

  @Override public ChunkAry chunkAry(VecAry v, int cidx){return new ChunkAry(v,cidx,this);}

  @Override public final DBlock setChunk(int c, Chunk chk){
    if(c != 0) throw new ArrayIndexOutOfBoundsException(c);
    return chk;
  }


  public static final class SparseString {
    private Chunk _c;
    int _id;
    final int _len;
    final BufferedString _val = new BufferedString();

    int _off;

    SparseString(int len){_len = len;}
    public int rowId() {return _id;}
    public BufferedString val() {
      if(_val.isMissing()) throw new RuntimeException("accessing missing long value");
      return _val;
    }
    public SparseString nextNZ() {return _c.nextNZ(this);}
    public SparseString setChunk(Chunk c) {
      _off = -1;
      _id = -1;
      _val.setMissing();
      return (_c = c).nextNZ(this);
    }
    public boolean isNA() {return _val == null;}
  }


  
  public static final class SparseNum {
    private Chunk _c;
    int _id;
    final int _len;
    double _val;
    boolean _isLong;
    long _lval;
    int _off;

    SparseNum(int len){_len = len;}
    public boolean isLong(){return _isLong;}
    public int rowId() {return _id;}
    public double dval() {return _val;}
    public long lval(){
      if(isNA()) throw  new RuntimeException("Accessing missing long value");
      return _isLong?_lval:(long)_val;
    }
    public SparseNum nextNZ() {return _c.nextNZ(this);}
    public SparseNum setChunk(Chunk c) {
      _off = -1;
      _id = -1;
      _val = Double.NaN;
      _isLong = false;
      return (_c = c).nextNZ(this);
    }
    public boolean isNA() {return Double.isNaN(_val);}
  }

  protected SparseNum nextNZ(SparseNum sv){
    if(sv._id == sv._len) throw new NoSuchElementException();
    if(++sv._id < sv._len)
      sv._val = atd(sv._id);
    else
      sv._val = Double.NaN;
    return sv;
  }

  protected SparseString nextNZ(SparseString sv){
    if(sv._id == sv._len) throw new NoSuchElementException();
    if(++sv._id < sv._len && !isNA(sv._id)) {
      atStr(sv._val,sv._id);
    } else
      sv._val.setMissing();
    return sv;
  }

  public abstract int len();

}
