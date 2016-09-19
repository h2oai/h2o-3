package water.fvec;

import water.*;
import water.parser.BufferedString;

/**
 * Created by tomas on 9/8/16.
 */
public class Chunks<T extends Chunk> extends Iced<Chunks<T>> {
  transient long _start = -1;
  transient Vec _vec = null;
  transient int _cidx = -1;
  transient public int _numRows = -1;
  T[] _cs;
  Chunk [] _chk2;

  public Chunks() {}

  public Chunks(Vec av, T... cs) {
    this(cs);
    _vec = av;
  }

  public Chunks(T... cs) {_cs = cs;}

  public int numRows() {return _numRows;}
  public int numCols() {return _cs.length;}

  public int sparseLenZero() { return sparseLenZero(0);}
  public int sparseLenZero(int cidx) {
    return _cs[cidx].isSparseZero()?_cs[cidx].sparseLenZero(): _numRows;
  }
  public int sparseLenNA(int cidx) {
    return _cs[cidx].isSparseNA()?_cs[cidx].sparseLenNA(): _numRows;
  }
  public int cidx() {return _cidx;}
  public long start() {return _start;}

  public T getChunk(int i) {
    if(i < 0) i += _cs.length;
    return _cs[i];
  }

  public T[] getChunks() {
    return _cs;
  }

  public void removeLocalCache(){
    Key k = _vec.chunkKey(cidx());
    if(!k.home()) H2O.raw_remove(k);
  }

  public Futures close(Futures fs) {
    if(_chk2 != null) {
      for (int i = 0; i < _chk2.length; ++i)
        if (_chk2[i] != null) _cs[i] = (T) _chk2[i].compress();
      _chk2 = null;
      DKV.put(_vec.chunkKey(_cidx), this, fs);
    }
    return fs;
  }

  public double[] getDoubles(int c, double[] ws) {
    if(_chk2 != null && _chk2[c] != null)
      return _chk2[c].getDoubles(ws,0, _numRows);
    else
      return _cs[c].getDoubles(ws,0, _numRows);
  }

  public int asSparseDoubles(int c, double[] vals, int[] ids) {
    if(_chk2 != null && _chk2[c] != null)
      return _chk2[c].asSparseDoubles(vals,ids);
    return _cs[c].asSparseDoubles(vals,ids);
  }

  public boolean isSparseZero(int i) {
    if(_chk2 != null && _chk2[i] != null)
      return _chk2[i].isSparseZero();
    return _cs[i].isSparseZero();
  }

  public boolean isSparseNA(int i){
    if(_chk2 != null && _chk2[i] != null)
      return _chk2[i].isSparseNA();
    return _cs[i].isSparseNA();
  }

  public void addColToNewChunk(AppendableChunks nc, int srcRowFrom, int srcRowTo, int srcCol, int tgtCol){
    _cs[srcCol].add2NewChunk(nc.getChunk(tgtCol),srcRowFrom,srcRowTo);
  }

  public Vec vec(int i) {return _vec;}

  public boolean isSparseZero() {return isSparseZero(0);}

  public int nextNZ(int r) {return nextNZ(r,0);}
  public int nextNZ(int r, int c) {return _cs[c].nextNZ(r);}

  public static class AppendableChunks extends Chunks<NewChunk> {
    public AppendableChunks(NewChunk [] chks) {
      _chk2 = chks;
      _cs = chks;
    }
    @Override public Futures close(Futures fs){
      Chunk [] cs = new Chunk[_cs.length];
      for(int i = 0; i < _cs.length; ++i)
        cs[i] = _cs[i].compress();
      DKV.put(_vec.chunkKey(_cidx),new Chunks(cs));
      return fs;
    }


    public void addNA(){addNA(0);}
    public void addNA(int c){_cs[c].addNA();}

    public void addNum(long m){addNum(0,m);}
    public void addNum(int c, long m){_cs[c].addNum(m,0);}
    public void addNum(int c, long m, int e){_cs[c].addNum(m,e);}
    public void addNum(double d){addNum(0,d);}
    public void addNum(int c, double d){_cs[c].addNum(d);}
    public void addUUID(long lo, long hi){addUUID(0,lo,hi);}
    public void addUUID(int c, long lo, long hi){_cs[c].addUUID(lo, hi);}
    public void addStr(BufferedString str){addStr(0,str);}
    public void addStr(int c, BufferedString str){_cs[c].addStr(str);}
    public void addStr(String str){addStr(0,str);}
    public void addStr(int c, String str){_cs[c].addStr(str);}
    public void addZeros(int n){addZeros(0,n);}
    public void addZeros(int c, int n){_cs[c].addZeros(c);}

    @Override
    public void setWrite(int i){}

  }

  public final int[] getIntegers(int c, int [] vals, int na){
    return getChunk(c).getIntegers(vals,0,vals.length,na);
  }
  /** Load a {@code double} value using chunk-relative row numbers.  Returns Double.NaN
   *  if value is missing.
   *  @return double value at the given row, or NaN if the value is missing */
  public final double atd(int i) { return atd(i,0);}
  public final double atd(int i, int j) {
    if(j < 0) j += _cs.length;
    return _chk2 == null || _chk2[j] == null ? _cs[j].atd_impl(i) : _chk2[j].atd_impl(i);
  }

  /** Load a {@code long} value using chunk-relative row numbers.  Floating
   *  point values are silently rounded to a long.  Throws if the value is
   *  missing.
   *  @return long value at the given row, or throw if the value is missing */
  public final long at8(int i) { return at8(i,0);}
  public final long at8(int i, int j) {
    return _chk2 == null || _chk2[j] == null ? _cs[j].at8_impl(i) : _chk2[j].at8_impl(i);
  }
  public final int at4(int i) {return at4(i,0);}
  public final int at4(int i, int j) {
    return _chk2 == null || _chk2[j] == null ? _cs[j].at4_impl(i) : _chk2[j].at4_impl(i);
  }

  /** Missing value status using chunk-relative row numbers.
   *
   *  @return true if the value is missing */
  public final boolean isNA(int i) { return isNA(i,0);}
  public final boolean isNA(int i, int j) {
    return _chk2 == null || _chk2[j] == null ? _cs[j].isNA_impl(i) : _chk2[j].isNA_impl(i);
  }

  /** Low half of a 128-bit UUID, or throws if the value is missing.
   *
   *  @return Low half of a 128-bit UUID, or throws if the value is missing.  */
  public final long at16l(int i) { return at16l(i,0);}
  public final long at16l(int i, int j) {
    return _chk2 == null || _chk2[j] == null ? _cs[j].at16l_impl(i) : _chk2[j].at16l_impl(i);
  }

  /** High half of a 128-bit UUID, or throws if the value is missing.
   *
   *  @return High half of a 128-bit UUID, or throws if the value is missing.  */
  public final long at16h(int i) { return at16h(i,0);}
  public final long at16h(int i, int j) {
    return _chk2 == null || _chk2[j] == null ? _cs[j].at16h_impl(i) : _chk2[j].at16h_impl(i);
  }

  /** String value using chunk-relative row numbers, or null if missing.
   *
   *  @return String value or null if missing. */
  public final BufferedString atStr(BufferedString bStr, int i) { return atStr(bStr,i,0);}
  public final BufferedString atStr(BufferedString bStr, int i, int j) {
    return _chk2 == null || _chk2[j] == null? _cs[j].atStr_impl(bStr, i) : _chk2[j].atStr_impl(bStr, i);
  }

  protected void setWrite(int i) {
    if(_chk2 == null) {
      _chk2 = new Chunk[_cs.length];
      _vec.preWriting();
    }
    if(_chk2[i] == null)
      _chk2[i] = _cs[i];
  }
  /** Write a {@code long} with check-relative indexing.  There is no way to
   *  write a missing value with this call.  Under rare circumstances this can
   *  throw: if the long does not fit in a double (value is larger magnitude
   *  than 2^52), AND float values are stored in Vector.  In this case, there
   *  is no common compatible data representation.
   *
   *  <p>As with all the {@code set} calls, if the value written does not fit
   *  in the current compression scheme, the Chunk will be inflated into a
   *  NewChunk and the value written there.  Later, the NewChunk will be
   *  compressed (after a {@link #close} call) and written back to the DKV.
   *  i.e., there is some interesting cost if Chunk compression-types need to
   *  change.
   *  @return the set value
   */
  public final long set(int i, long l) { return set(i,0,l);}
  public final long set(int i, int j, long l) {
    setWrite(j);
    if( _chk2[j].set_impl(i,l) ) return l;
    (_chk2[j] = _cs[j].inflate()).set_impl(i,l);
    return l;
  }

  public final double [] set(double [] d) {return set(0, d);}
  public final double [] set(int j, double [] d){
    assert d.length == _numRows && _chk2 == null;
    setWrite(j);
    _chk2[j] = new NewChunk(d).compress();
    return d;
  }
  /** Write a {@code double} with check-relative indexing.  NaN will be treated
   *  as a missing value.
   *
   *  <p>As with all the {@code set} calls, if the value written does not fit
   *  in the current compression scheme, the Chunk will be inflated into a
   *  NewChunk and the value written there.  Later, the NewChunk will be
   *  compressed (after a {@link #close} call) and written back to the DKV.
   *  i.e., there is some interesting cost if Chunk compression-types need to
   *  change.
   *  @return the set value
   */
  public final double set(int i, double d) { return set(i,0,d);}
  public final double set(int i, int j, double d) {
    setWrite(j);
    if( _chk2[j].set_impl(i,d) ) return d;
    (_chk2[j] = _chk2[j].inflate()).set_impl(i,d);
    return d;
  }

  /** Write a {@code float} with check-relative indexing.  NaN will be treated
   *  as a missing value.
   *
   *  <p>As with all the {@code set} calls, if the value written does not fit
   *  in the current compression scheme, the Chunk will be inflated into a
   *  NewChunk and the value written there.  Later, the NewChunk will be
   *  compressed (after a {@link #close} call) and written back to the DKV.
   *  i.e., there is some interesting cost if Chunk compression-types need to
   *  change.
   *  @return the set value
   */
  public final float set(int i, int j, float f) {
    setWrite(j);
    if( _chk2[j].set_impl(i,f) ) return f;
    (_chk2[j] = _chk2[j].inflate()).set_impl(i,f);
    return f;
  }

  /** Set a value as missing.
   *
   *  <p>As with all the {@code set} calls, if the value written does not fit
   *  in the current compression scheme, the Chunk will be inflated into a
   *  NewChunk and the value written there.  Later, the NewChunk will be
   *  compressed (after a {@link #close} call) and written back to the DKV.
   *  i.e., there is some interesting cost if Chunk compression-types need to
   *  change.
   *  @return the set value
   */
  public final boolean setNA(int i) { return setNA(i,0);}
  public final boolean setNA(int i, int j) {
    setWrite(j);
    if( _chk2[j].setNA_impl(i) ) return true;
    (_chk2[j] = _chk2[j].inflate()).setNA_impl(i);
    return true;
  }

  /** Write a {@code String} with check-relative indexing.  {@code null} will
   *  be treated as a missing value.
   *
   *  <p>As with all the {@code set} calls, if the value written does not fit
   *  in the current compression scheme, the Chunk will be inflated into a
   *  NewChunk and the value written there.  Later, the NewChunk will be
   *  compressed (after a {@link #close} call) and written back to the DKV.
   *  i.e., there is some interesting cost if Chunk compression-types need to
   *  change.
   *  @return the set value
   */
  public final String set(int i, String str) { return set(i,0,str);}
  public final String set(int i, int j, String str) {
    setWrite(j);
    if( _chk2[j].set_impl(i,str) ) return str;
    (_chk2[j] = _chk2[j].inflate()).set_impl(i,str);
    return str;
  }

  final long at8_abs(long i, int j) {
    long s = start();
    long x = i - (s > 0 ? s : 0);
    if( 0 <= x && x < _numRows) return at8((int) x, j);
    throw new ArrayIndexOutOfBoundsException(""+ s + " <= "+i+" < "+( s+ _numRows));
  }

  /** Load a {@code double} value using absolute row numbers.  Returns
   *  Double.NaN if value is missing.
   *
   *  <p>This version uses absolute element numbers, but must convert them to
   *  chunk-relative indices - requiring a load from an aliasing local var,
   *  leading to lower quality JIT'd code (similar issue to using iterator
   *  objects).
   *
   *  <p>Slightly slower than {@link #atd} since it range-checks within a chunk.
   *  @return double value at the given row, or NaN if the value is missing */
  final double at_abs(long i, int j) {
    long s = start();
    long x = i - (s>0 ? s : 0);
    if( 0 <= x && x < _numRows) return atd((int) x, j);
    throw new ArrayIndexOutOfBoundsException(""+s+" <= "+i+" < "+(s+ _numRows));
  }

  final void set_abs(long i, int j, double d) {
    long s = start();
    long x = i - (s>0 ? s : 0);
    if( 0 <= x && x < _numRows)
      set((int) x, j, d);
    else
      throw new ArrayIndexOutOfBoundsException(""+s+" <= "+i+" < "+(s+ _numRows));
  }

  /** Missing value status.
   *
   *  <p>This version uses absolute element numbers, but must convert them to
   *  chunk-relative indices - requiring a load from an aliasing local var,
   *  leading to lower quality JIT'd code (similar issue to using iterator
   *  objects).
   *
   *  <p>Slightly slower than {@link #isNA} since it range-checks within a chunk.
   *  @return true if the value is missing */
  final boolean isNA_abs(long i, int j) {
    long s = start();
    long x = i - (s>0 ? s : 0);
    if( 0 <= x && x < _numRows) return isNA((int) x, j);
    throw new ArrayIndexOutOfBoundsException(""+ s+" <= "+i+" < "+(s+ _numRows));
  }

  /** Low half of a 128-bit UUID, or throws if the value is missing.
   *
   *  <p>This version uses absolute element numbers, but must convert them to
   *  chunk-relative indices - requiring a load from an aliasing local var,
   *  leading to lower quality JIT'd code (similar issue to using iterator
   *  objects).
   *
   *  <p>Slightly slower than {@link #at16l} since it range-checks within a chunk.
   *  @return Low half of a 128-bit UUID, or throws if the value is missing.  */
  final long at16l_abs(long i, int j) {
    long s = start();
    long x = i - (s>0 ? s : 0);
    if( 0 <= x && x < _numRows) return at16l((int) x, j);
    throw new ArrayIndexOutOfBoundsException(""+s+" <= "+i+" < "+(s+ _numRows));
  }

  /** High half of a 128-bit UUID, or throws if the value is missing.
   *
   *  <p>This version uses absolute element numbers, but must convert them to
   *  chunk-relative indices - requiring a load from an aliasing local var,
   *  leading to lower quality JIT'd code (similar issue to using iterator
   *  objects).
   *
   *  <p>Slightly slower than {@link #at16h} since it range-checks within a chunk.
   *  @return High half of a 128-bit UUID, or throws if the value is missing.  */
  final long at16h_abs(long i, int j) {
    long s = start();
    long x = i - (s>0 ? s : 0);
    if( 0 <= x && x < _numRows) return at16h((int) x, j);
    throw new ArrayIndexOutOfBoundsException("" + s + " <= " + i + " < " + ( s + _numRows));
  }

  /** String value using absolute row numbers, or null if missing.
   *
   *  <p>This version uses absolute element numbers, but must convert them to
   *  chunk-relative indices - requiring a load from an aliasing local var,
   *  leading to lower quality JIT'd code (similar issue to using iterator
   *  objects).
   *
   *  <p>Slightly slower than {@link #atStr} since it range-checks within a chunk.
   *  @return String value using absolute row numbers, or null if missing. */
  final BufferedString atStr_abs(BufferedString bStr, long i, int j) {
    long s = start();
    long x = i - (s>0 ? s : 0);
    if( 0 <= x && x < _numRows) return atStr(bStr, (int) x, j);
    throw new ArrayIndexOutOfBoundsException(""+ s+" <= "+i+" < "+(s+ _numRows));
  }

  public double sparseRatio() {
    Chunk [] chks = _cs;
    double cnt = 0;
    double reg = 1.0 / chks.length;
    for (Chunk c : chks)
      if (c.isSparseNA()) {
        cnt += c.sparseLenNA() / (double) c.len();
      } else if (c.isSparseZero()) {
        cnt += c.sparseLenZero() / (double) c.len();
      } else cnt += 1;
    return cnt * reg;
  }

}
