package water.fvec;

import com.google.common.base.Charsets;
import water.AutoBuffer;
import water.Futures;
import water.H2O;
import water.MemoryManager;
import water.parser.BufferedString;
import water.util.PrettyPrint;
import water.util.UnsafeUtils;

import java.util.*;

// An uncompressed chunk of data, supporting an append operation
public class NewChunk extends Chunk {

  public void alloc_mantissa(int sparseLen) {_ms = new Mantissas(sparseLen);}

  public void alloc_exponent(int sparseLen) {_xs = new Exponents(sparseLen);}

  public int is(int i) { return _is[i];}

  public void set_is(int i, int val) {_is[i] = val;}

  public void alloc_nums(int len) { _ms = new Mantissas(len); _xs = new Exponents(len);}


  /**
   * Wrapper around exponent, stores values (only if there are non-zero exponents) in bytes or ints.
   */
  public static class Exponents {
    int _len;
    public Exponents(int cap){_len = cap;}
    byte [] _vals1;
    int  [] _vals4;
    private void alloc_data(int val){
      byte b = (byte)val;
      if(b == val && b != CATEGORICAL_1)
        _vals1 = MemoryManager.malloc1(_len);
      else
        _vals4 = MemoryManager.malloc4(_len);
    }

    public void set(int idx, int x) {
      if(_vals1 == null && _vals4 == null) {
        if(x == 0) return;
        alloc_data(x);
      }
      if(_vals1 != null){
        byte b = (byte)x;
        if(x == b && b > Byte.MIN_VALUE-1) {
          _vals1[idx] = b;
        } else {
          // need to switch to 4 byte values
          int len = _vals1.length;
          _vals4 = MemoryManager.malloc4(len);
          for (int i = 0; i < _vals1.length; ++i)
            _vals4[i] = (_vals1[i] == CATEGORICAL_1)?CATEGORICAL_2:_vals1[i];
          _vals1 = null;
          _vals4[idx] = x;
        }
      } else
        _vals4[idx] = x;
    }
    public int get(int id){
      if(_vals1 == null && null == _vals4) return 0;
      if(_vals1 != null) {
        int x = _vals1[id];
        if(x == CATEGORICAL_1)
          x = CATEGORICAL_2;
        return x;
      }
      return _vals4[id];
    }
    public boolean isCategorical(int i) { return _vals1 !=  null && _vals1[i] == CATEGORICAL_1 || _vals4 != null && _vals4[i] == CATEGORICAL_2;}

    private static byte CATEGORICAL_1 = Byte.MIN_VALUE;
    private static int  CATEGORICAL_2 = Integer.MIN_VALUE;

    public void setCategorical(int idx) {
      if(_vals1 == null && _vals4 == null)
        alloc_data(0);
      if(_vals1 != null) _vals1[idx] = CATEGORICAL_1;
      else _vals4[idx] = CATEGORICAL_2;
    }

    public void move(int to, int from) {
      if(_vals1 == null && null == _vals4) return;
      if(_vals1 != null)
        _vals1[to] = _vals1[from];
      else
        _vals4[to] = _vals4[from];
    }

    public void resize(int len) {
      if (_vals1 != null) _vals1 = Arrays.copyOf(_vals1, len);
      else if (_vals4 != null) _vals4 = Arrays.copyOf(_vals4, len);
      _len = len;
    }
  }

  /**
   * Class wrapping around mantissa.
   * Stores values in bytes, ints or longs, if data fits.
   * Sets and gets done in longs.
   */
  public static class Mantissas {
    byte [] _vals1;
    int  [] _vals4;
    long [] _vals8;
    int _nzs;

    public Mantissas(int cap) {_vals1 = MemoryManager.malloc1(cap);}

    public void set(int idx, long l) {
      long old;
      if(_vals1 != null) { // check if we fit withing single byte
        byte b = (byte)l;
        if(b == l) {
          old = _vals1[idx];
          _vals1[idx] = b;
        } else {
          int i = (int)l;
          if(i == l) {
            switchToInts();
            old = _vals4[idx];
            _vals4[idx] = i;
          } else {
            switchToLongs();
            old = _vals8[idx];
            _vals8[idx] = l;
          }
        }
      } else  if(_vals4 != null) {
        int i = (int)l;
        if(i != l) {
          switchToLongs();
          old = _vals8[idx];
          _vals8[idx] = l;
        } else {
          old = _vals4[idx];
          _vals4[idx] = i;
        }
      } else {
        old = _vals8[idx];
        _vals8[idx] = l;
      }
      if (old != l) {
        if (old == 0) ++_nzs;
        else if(l == 0) --_nzs;
      }
    }
    public long get(int id) {
      if(_vals1 != null) return _vals1[id];
      if(_vals4 != null) return _vals4[id];
      return _vals8[id];
    }

    public void switchToInts() {
      int len = _vals1.length;
      _vals4 = MemoryManager.malloc4(len);
      for(int i = 0; i < _vals1.length; ++i)
        _vals4[i] = _vals1[i];
      _vals1 = null;
    }

    public void switchToLongs() {
      int len = Math.max(_vals1 == null?0:_vals1.length,_vals4 == null?0:_vals4.length);
      int newlen = len;
      _vals8 = MemoryManager.malloc8(newlen);
      if(_vals1 != null)
        for(int i = 0; i < _vals1.length; ++i)
          _vals8[i] = _vals1[i];
      else if(_vals4 != null) {
        for(int i = 0; i < _vals4.length; ++i)
          _vals8[i] = _vals4[i];
      }
      _vals1 = null;
      _vals4 = null;
    }

    public void move(int to, int from) {
      if(to != from) {
        if (_vals1 != null) {
          _vals1[to] = _vals1[from];
          _vals1[from] = 0;
        } else if (_vals4 != null) {
          _vals4[to] = _vals4[from];
          _vals4[from] = 0;
        } else {
          _vals8[to] = _vals8[from];
          _vals8[from] = 0;
        }
      }
    }

    public int len() {
      return _vals1 != null?_vals1.length:_vals4 != null?_vals4.length:_vals8.length;
    }

    public void resize(int len) {
      if(_vals1 != null) _vals1 = Arrays.copyOf(_vals1,len);
      else if(_vals4 != null) _vals4 = Arrays.copyOf(_vals4,len);
      else if(_vals8 != null) _vals8 = Arrays.copyOf(_vals8,len);
    }
  }

  public final int _cidx;
  // We can record the following (mixed) data types:
  // 1- doubles, in _ds including NaN for NA & 0; _ls==_xs==null
  // 2- scaled decimals from parsing, in _ls & _xs; _ds==null
  // 3- zero: requires _ls==0 && _xs==0
  // 4- NA: _ls==Long.MAX_VALUE && _xs==Integer.MIN_VALUE || _ds=NaN
  // 5- Categorical: _xs==(Integer.MIN_VALUE+1) && _ds==null
  // 6- Str: _ss holds appended string bytes (with trailing 0), _is[] holds offsets into _ss[]
  // Chunk._len is the count of elements appended
  // Sparse: if _sparseLen != _len, then _ls/_ds are compressed to non-zero's only,
  // and _xs is the row number.  Still _len is count of elements including
  // zeros, and _sparseLen is count of non-zeros.
  protected transient Mantissas _ms;   // Mantissa
  protected transient BitSet   _missing;
  protected transient Exponents _xs;   // Exponent, or if _ls==0, NA or Categorical or Rows
  private transient int    _id[];   // Indices (row numbers) of stored values, used for sparse
  private transient double _ds[];   // Doubles, for inflating via doubles
  public transient byte[]   _ss;   // Bytes of appended strings, including trailing 0
  private transient int    _is[];   // _is[] index of strings - holds offsets into _ss[]. _is[i] == -1 means NA/sparse

  int   [] alloc_indices(int l)  { return _id = MemoryManager.malloc4(l); }
  public double[] alloc_doubles(int l)  {
    _ms = null;
    _xs = null;
    _missing = null;
    return _ds = MemoryManager.malloc8d(l);
  }
  int   [] alloc_str_indices(int l) {
    _ms = null;
    _xs = null;
    _missing = null;
    _ds = null;
    return _is = MemoryManager.malloc4(l);
  }

  final protected int   []  indices() { return _id; }
  final protected double[]  doubles() { return _ds; }

  @Override public boolean isSparseZero() { return sparseZero(); }
  public boolean _sparseNA = false;
  @Override public boolean isSparseNA() {return sparseNA();}
  void setSparseNA() {_sparseNA = true;}

  public int _sslen;                   // Next offset into _ss for placing next String

  public int _sparseLen;
  int set_sparseLen(int l) {
    return this._sparseLen = l;
  }
  @Override public int sparseLenZero() { return _sparseNA ? _len : _sparseLen;}
  @Override public int sparseLenNA() { return _sparseNA ? _sparseLen : _len; }

  private int _naCnt=-1;                // Count of NA's   appended
  protected int naCnt() { return _naCnt; }               // Count of NA's   appended
  private int _catCnt;                  // Count of Categorical's appended
  private int _strCnt;                  // Count of string's appended
  private int _nzCnt;                   // Count of non-zero's appended
  private int _uuidCnt;                 // Count of UUIDs

  public int _timCnt = 0;
  protected static final int MIN_SPARSE_RATIO = 8;
  private int _sparseRatio = MIN_SPARSE_RATIO;
  public boolean _isAllASCII = true; //For cat/string col, are all characters in chunk ASCII?

  public NewChunk( Vec vec, int cidx ) {
    _vec = vec; _cidx = cidx;
    _ms = new Mantissas(4);
    _xs = new Exponents(4);
  }

  public NewChunk( Vec vec, int cidx, boolean sparse ) {
    _vec = vec; _cidx = cidx;
    _ms = new Mantissas(4);
    _xs = new Exponents(4);
    if(sparse) _id = new int[4];

  }

  public NewChunk(double [] ds) {
    _cidx = -1;
    _vec = null;
    setDoubles(ds);
  }
  public NewChunk( Vec vec, int cidx, long[] mantissa, int[] exponent, int[] indices, double[] doubles) {
    _vec = vec; _cidx = cidx;
    _ms = new Mantissas(mantissa.length);
    _xs = new Exponents(exponent.length);
    for(int i = 0; i < mantissa.length; ++i) {
      _ms.set(i,mantissa[i]);
      _xs.set(i,exponent[i]);
    }
    _id = indices;
    _ds = doubles;
    if (_ms != null && _sparseLen==0) set_sparseLen(set_len(mantissa.length));
    if (_ds != null && _sparseLen==0) set_sparseLen(set_len(_ds.length));
    if (_id != null && _sparseLen==0) set_sparseLen(_id.length);
  }

  // Constructor used when inflating a Chunk.
  public NewChunk( Chunk c ) {
    this(c._vec, c.cidx());
    _start = c._start;
  }

  // Constructor used when inflating a Chunk.
  public NewChunk( Chunk c, double [] vals) {
    _vec = c._vec; _cidx = c.cidx();
    _start = c._start;
    _ds = vals;
    _sparseLen = _len = _ds.length;
  }

  // Pre-sized newchunks.
  public NewChunk( Vec vec, int cidx, int len ) {
    this(vec,cidx);
    _ds = new double[len];
    Arrays.fill(_ds, Double.NaN);
    set_sparseLen(set_len(len));
  }

  public NewChunk setSparseRatio(int s) {
    _sparseRatio = s;
    return this;
  }

  public void setDoubles(double[] ds) {
    _ds = ds;
    _sparseLen = _len = ds.length;
    _ms = null;
    _xs = null;
  }

  public void set_vec(Vec vec) { _vec = vec; }


  public final class Value {
    int _gId; // row number in dense (ie counting zeros)
    int _lId; // local array index of this value, equal to _gId if dense

    public Value(int lid, int gid){_lId = lid; _gId = gid;}
    public final int rowId0(){return _gId;}
    public void add2Chunk(NewChunk c){add2Chunk_impl(c,_lId);}
  }

  private transient BufferedString _bfstr = new BufferedString();

  private void add2Chunk_impl(NewChunk c, int i) {
    if (isNA2(i)) {
      c.addNA();
    } else  if (isUUID()) {
      c.addUUID(_ms.get(i), Double.doubleToRawLongBits(_ds[i]));
    } else if(_ms != null) {
      c.addNum(_ms.get(i), _xs.get(i));
    } else if(_ds != null) {
      c.addNum(_ds[i]);
    } else if (_ss != null) {
      int sidx = _is[i];
      int nextNotNAIdx = i + 1;
      // Find next not-NA value (_is[idx] != -1)
      while (nextNotNAIdx < _is.length && _is[nextNotNAIdx] == -1) nextNotNAIdx++;
      int slen = nextNotNAIdx < _is.length ? _is[nextNotNAIdx] - sidx : _sslen - sidx;
      // null-BufferedString represents NA value
      BufferedString bStr = sidx == -1 ? null : _bfstr.set(_ss, sidx, slen);
      c.addStr(bStr);
    } else
      throw new IllegalStateException();
  }
  public void add2Chunk(NewChunk c, int i){
    if(!isSparseNA() && !isSparseZero())
      add2Chunk_impl(c,i);
    else {
      int j = Arrays.binarySearch(_id,0,_sparseLen,i);
      if(j >= 0)
        add2Chunk_impl(c,j);
      else if(isSparseNA())
        c.addNA();
      else
        c.addNum(0,0);
    }
  }

  public Iterator<Value> values(){ return values(0,_len);}
  public Iterator<Value> values(int fromIdx, int toIdx){
    final int lId, gId;
    final int to = Math.min(toIdx, _len);

    if(_id != null){
      int x = Arrays.binarySearch(_id,0, _sparseLen,fromIdx);
      if(x < 0) x = -x -1;
      lId = x;
      gId = x == _sparseLen ? _len :_id[x];
    } else
      lId = gId = fromIdx;
    final Value v = new Value(lId,gId);
    final Value next = new Value(lId,gId);
    return new Iterator<Value>(){
      @Override public final boolean hasNext(){return next._gId < to;}
      @Override public final Value next(){
        if(!hasNext())throw new NoSuchElementException();
        v._gId = next._gId; v._lId = next._lId;
        next._lId++;
        if(_id != null) next._gId = next._lId < _sparseLen ?_id[next._lId]: _len;
        else next._gId++;
        return v;
      }
      @Override
      public void remove() {throw new UnsupportedOperationException();}
    };
  }

  // Heuristic to decide the basic type of a column
  byte type() {
    if( _naCnt == -1 ) {        // No rollups yet?
      int nas=0, es=0, nzs=0, ss=0;
      if( _ds != null && _ms != null ) { // UUID?
        for(int i = 0; i< _sparseLen; i++ )
          if( _xs != null && _xs.get(i)==Integer.MIN_VALUE )  nas++;
          else if( _ds[i] !=0 || _ms.get(i) != 0 ) nzs++;
        _uuidCnt = _len -nas;
      } else if( _ds != null ) { // Doubles?
        assert _xs==null;
        for(int i = 0; i < _sparseLen; ++i) {
          if( Double.isNaN(_ds[i]) ) nas++; 
          else if( _ds[i]!=0 ) nzs++;
        }
      } else {
        if( _ms != null && _sparseLen > 0) // Longs and categoricals?
          for(int i = 0; i< _sparseLen; i++ )
            if( isNA2(i) ) nas++;
            else {
              if( isCategorical2(i)   ) es++;
              if( _ms.get(i) != 0 ) nzs++;
            }
        if( _is != null )  // Strings
          for(int i = 0; i< _sparseLen; i++ )
            if( isNA2(i) ) nas++;
            else ss++;
      }
      if (_sparseNA) nas += (_len - _sparseLen);
      _nzCnt=nzs;  _catCnt =es;  _naCnt=nas; _strCnt = ss;
    }
    // Now run heuristic for type
    if(_naCnt == _len)          // All NAs ==> NA Chunk
      return Vec.T_BAD;
    if(_strCnt > 0)
      return Vec.T_STR;
    if(_catCnt > 0 && _catCnt + _naCnt + (isSparseZero()? _len-_sparseLen : 0) == _len)
      return Vec.T_CAT; // All are Strings+NAs ==> Categorical Chunk
    // UUIDs?
    if( _uuidCnt > 0 ) return Vec.T_UUID;
    // Larger of time & numbers
    int nums = _len -_naCnt-_timCnt;
    return _timCnt >= nums ? Vec.T_TIME : Vec.T_NUM;
  }

  //what about sparse reps?
  protected final boolean isNA2(int idx) {
    if (isString()) return _is[idx] == -1;
    if(isUUID() || _ds == null) return _missing != null && _missing.get(idx);
    return Double.isNaN(_ds[idx]);
  }
  protected final boolean isCategorical2(int idx) {
    return _xs!=null && _xs.isCategorical(idx);
  }
  protected final boolean isCategorical(int idx) {
    if(_id == null)return isCategorical2(idx);
    int j = Arrays.binarySearch(_id,0, _sparseLen,idx);
    return j>=0 && isCategorical2(j);
  }

  public void addCategorical(int e) {
    if(_ms == null || _ms.len() == _sparseLen)
      append2slow();
    if( e != 0 || !isSparseZero() ) {
      _ms.set(_sparseLen,e);
      _xs.setCategorical(_sparseLen);
      if(_id != null) _id[_sparseLen] = _len;
      ++_sparseLen;
    }
    ++_len;
  }
  public void addNA() {
    if(!_sparseNA) {
      if (isString()) {
        addStr(null);
        return;
      } else if (isUUID()) {
        if( _ms==null || _ds== null || _sparseLen >= _ms.len() )
          append2slowUUID();
        if(_missing == null) _missing = new BitSet();
        _missing.set(_sparseLen);
        if (_id != null) _id[_sparseLen] = _len;
        _ds[_sparseLen] = Double.NaN;
        ++_sparseLen;
      } else if (_ds != null) {
        addNum(Double.NaN);
        return;
      } else {
        if (!_sparseNA && _sparseLen == _ms.len())
          append2slow();
        if(!_sparseNA) {
          if(_missing == null) _missing = new BitSet();
          _missing.set(_sparseLen);
          if (_id != null) _id[_sparseLen] = _len;
          ++_sparseLen;
        }
      }
    }
    ++_len;
  }

  public void addNum (long val, int exp) {
    if( isUUID() || isString() ) {
      addNA();
    } else if(_ds != null) {
      assert _ms == null;
      addNum(val*PrettyPrint.pow10(exp));
    } else {
      if( val == 0 ) exp = 0;// Canonicalize zero
      if(val != 0 || !isSparseZero()) {
        if (_ms == null || _ms.len() == _sparseLen) {
          append2slow();
          addNum(val, exp); // sparsity could've changed
          return;
        }
        int len = _ms.len();
        int slen = _sparseLen;
        long t;                // Remove extra scaling
        while (exp < 0 && exp > -9999999 && (t = val / 10) * 10 == val) {
          val = t;
          exp++;
        }
        _ms.set(_sparseLen, val);
        _xs.set(_sparseLen, exp);
        assert _id == null || _id.length == _ms.len() : "id.len = " + _id.length + ", ms.len = " + _ms.len() + ", old ms.len = " + len + ", sparseLen = " + slen;
        if (_id != null) _id[_sparseLen] = _len;
        _sparseLen++;
      }
      _len++;
    }
  }
  // Fast-path append double data
  public void addNum(double d) {
    if( isUUID() || isString() ) { addNA(); return; }
    boolean predicate = _sparseNA ? !Double.isNaN(d) : isSparseZero()?d != 0:true;
    if(predicate) {
      if(_ms != null) {
        if((long)d == d){
          addNum((long)d,0);
          return;
        }
        switch_to_doubles();
      }
      //if ds not big enough
      if(_sparseLen == _ds.length ) {
        append2slowd();
        // call addNum again since append2slowd might have flipped to sparse
        addNum(d);
        assert _sparseLen <= _len;
        return;
      }
      if(_id != null)_id[_sparseLen] = _len;
      _ds[_sparseLen] = d;
      _sparseLen++;
    }
    _len++;
    assert _sparseLen <= _len;
  }

  private void append_ss(String str) {
    byte[] bytes = str == null ? new byte[0] : str.getBytes(Charsets.UTF_8);

    // Allocate memory if necessary
    if (_ss == null)
      _ss = MemoryManager.malloc1((bytes.length+1) * 4);
    while (_ss.length < (_sslen + bytes.length+1))
      _ss = MemoryManager.arrayCopyOf(_ss,_ss.length << 1);

    // Copy bytes to _ss
    for (byte b : bytes) _ss[_sslen++] = b;
    _ss[_sslen++] = (byte)0; // for trailing 0;
  }

  private void append_ss(BufferedString str) {
    int strlen = str.length();
    int off = str.getOffset();
    byte b[] = str.getBuffer();

    if (_ss == null) {
      _ss = MemoryManager.malloc1((strlen + 1) * 4);
    }
    while (_ss.length < (_sslen + strlen + 1)) {
      _ss = MemoryManager.arrayCopyOf(_ss,_ss.length << 1);
    }
    for (int i = off; i < off+strlen; i++)
      _ss[_sslen++] = b[i];
    _ss[_sslen++] = (byte)0; // for trailing 0;
  }

  // Append a string, store in _ss & _is
  public void addStr(Object str) {
    if(_id == null || str != null) {
      if(_is == null || _sparseLen >= _is.length) {
        append2slowstr();
        addStr(str);
        assert _sparseLen <= _len;
        return;
      }
      if (str != null) {
        if(_id != null)_id[_sparseLen] = _len;
        _is[_sparseLen] = _sslen;
        _sparseLen++;
        if (str instanceof BufferedString)
          append_ss((BufferedString) str);
        else // this spares some callers from an unneeded conversion to BufferedString first
          append_ss((String) str);
      } else if (_id == null) {
        _is[_sparseLen] = CStrChunk.NA;
        set_sparseLen(_sparseLen + 1);
      }
    }
    set_len(_len + 1);
    assert _sparseLen <= _len;
  }

  // TODO: FIX isAllASCII test to actually inspect string contents
  public void addStr(Chunk c, long row) {
    if( c.isNA_abs(row) ) addNA();
    else { addStr(c.atStr_abs(new BufferedString(), row)); _isAllASCII &= ((CStrChunk)c)._isAllASCII; }
  }

  public void addStr(Chunk c, int row) {
    if( c.isNA(row) ) addNA();
    else { addStr(c.atStr(new BufferedString(), row)); _isAllASCII &= ((CStrChunk)c)._isAllASCII; }
  }

  public void addUUID(UUID uuid) {
    if (uuid == null) addNA();
    else addUUID(uuid.getLeastSignificantBits(), uuid.getMostSignificantBits());
  }

  // Append a UUID, stored in _ls & _ds
  public void addUUID( long lo, long hi ) {
    set_impl(_sparseLen, lo, hi);
    _sparseLen++;
    _len++;
    assert _sparseLen <= _len;
  }

  @Override public boolean set_impl(int idx, long lo, long hi) {
    if (C16Chunk.isNA(lo, hi)) throw new IllegalArgumentException("Cannot set illegal UUID value");
    if( _ms==null || _ds== null || idx >= _ms.len() )
      append2slowUUID();
    _ms.set(idx,lo);
    _ds[idx] = Double.longBitsToDouble(hi);
    return true;
  }

  public void addUUID( Chunk c, long row ) {
    if (c.isNA_abs(row)) addNA();
    else addUUID(c.at16l_abs(row),c.at16h_abs(row));
  }
  public void addUUID( Chunk c, int row ) {
    if( c.isNA(row) ) addNA();
    else addUUID(c.at16l(row),c.at16h(row));
  }

  public final boolean isUUID(){return _ms != null && _ds != null; }
  public final boolean isString(){return _is != null; }
  public final boolean sparseZero(){return _id != null && !_sparseNA;}
  public final boolean sparseNA() {return _id != null && _sparseNA;}

  public void addZeros(int n){
    if(!sparseZero()) for(int i = 0; i < n; ++i)addNum(0,0);
    else set_len(_len + n);
  }

  public void addNAs(int n) {
    if(!sparseNA())
      for (int i = 0; i <n; ++i) {
        addNA();
        if(sparseNA()) {
          set_len(_len + n - i -1);
          return;
        }
      }
    else set_len(_len + n);
  }

  // Append all of 'nc' onto the current NewChunk.  Kill nc.
  public void add( NewChunk nc ) {
    assert _cidx >= 0;
    assert _sparseLen <= _len;
    assert nc._sparseLen <= nc._len :"_sparseLen = " + nc._sparseLen + ", _len = " + nc._len;
    if( nc._len == 0 ) return;
    if(_len == 0){
      _ms = nc._ms; nc._ms = null;
      _xs = nc._xs; nc._xs = null;
      _id = nc._id; nc._id = null;
      _ds = nc._ds; nc._ds = null;
      _is = nc._is; nc._is = null;
      _ss = nc._ss; nc._ss = null;
      set_sparseLen(nc._sparseLen);
      set_len(nc._len);
      return;
    }
    if(nc.sparseZero() != sparseZero() || nc.sparseNA() != sparseNA()){ // for now, just make it dense
      cancel_sparse();
      nc.cancel_sparse();
    }
    if( _ds != null ) throw H2O.fail();
    for(int i = 0; i < nc._sparseLen; ++i) {
      _ms.set(_sparseLen+i,nc._ms.get(i));
      _xs.set(_sparseLen+i,nc._xs.get(i));
    }
    if(_id != null) {
      assert nc._id != null;
      _id = MemoryManager.arrayCopyOf(_id,_sparseLen + nc._sparseLen);
      System.arraycopy(nc._id,0,_id, _sparseLen, nc._sparseLen);
      for(int i = _sparseLen; i < _sparseLen + nc._sparseLen; ++i) _id[i] += _len;
    } else assert nc._id == null;

    set_sparseLen(_sparseLen + nc._sparseLen);
    set_len(_len + nc._len);
    nc._ms = null;  nc._xs = null; nc._id = null; nc.set_sparseLen(nc.set_len(0));
    assert _sparseLen <= _len;
  }

  // Fast-path append long data
//  void append2( long l, int x ) {
//    boolean predicate = _sparseNA ? (l != Long.MAX_VALUE || x != Integer.MIN_VALUE): l != 0;
//    if(_id == null || predicate){
//      if(_ms == null || _sparseLen == _ms._c) {
//        append2slow();
//        // again call append2 since calling append2slow might have changed things (eg might have switched to sparse and l could be 0)
//        append2(l,x);
//        return;
//      }
//      _ls[_sparseLen] = l;
//      _xs[_sparseLen] = x;
//      if(_id  != null)_id[_sparseLen] = _len;
//      set_sparseLen(_sparseLen + 1);
//    }
//    set_len(_len + 1);
//    assert _sparseLen <= _len;
//  }

  // Slow-path append data
  private void append2slowd() {
    assert _ms==null;
    if(_ds != null && _ds.length > 0){
      if(_id == null) { // check for sparseness
        int nzs = 0; // assume one non-zero for the element currently being stored
        int nonnas = 0;
        for(double d:_ds) {
          if(d != 0)++nzs;
          if(!Double.isNaN(d))++nonnas;
        }
        if((nzs+1)*_sparseRatio < _len) {
          set_sparse(nzs,Compress.ZERO);
          assert _sparseLen == 0 || _sparseLen <= _ds.length:"_sparseLen = " + _sparseLen + ", _ds.length = " + _ds.length + ", nzs = " + nzs +  ", len = " + _len;
          assert _id.length == _ds.length;
          assert _sparseLen <= _len;
          return;
        }
        else if((nonnas+1)*_sparseRatio < _len) {
          set_sparse(nonnas,Compress.NA);
          assert _sparseLen == 0 || _sparseLen <= _ds.length:"_sparseLen = " + _sparseLen + ", _ds.length = " + _ds.length + ", nonnas = " + nonnas +  ", len = " + _len;
          assert _id.length == _ds.length;
          assert _sparseLen <= _len;
          return;
        }
      }
      else {
        // verify we're still sufficiently sparse
        if((_sparseRatio*(_sparseLen) >> 2) > _len)  cancel_sparse();
        else _id = MemoryManager.arrayCopyOf(_id, _sparseLen << 1);
      }
      _ds = MemoryManager.arrayCopyOf(_ds, _sparseLen << 1);
    } else {
      alloc_doubles(4);
      if (_id != null) alloc_indices(4);
    }
    assert _sparseLen == 0 || _ds.length > _sparseLen :"_ds.length = " + _ds.length + ", _sparseLen = " + _sparseLen;
    assert _id == null || _id.length == _ds.length;
    assert _sparseLen <= _len;
  }
  // Slow-path append data
  private void append2slowUUID() {
    if(_id != null)
      cancel_sparse();
    if( _ds==null && _ms!=null ) { // This can happen for columns with all NAs and then a UUID
      _xs=null;
      _ms.switchToLongs();
      _ds = MemoryManager.malloc8d(_sparseLen);
      Arrays.fill(_ms._vals8,C16Chunk._LO_NA);
      Arrays.fill(_ds,Double.longBitsToDouble(C16Chunk._HI_NA));
    }
    if( _ms != null && _sparseLen > 0 ) {
      _ds = MemoryManager.arrayCopyOf(_ds, _sparseLen * 2);
      _ms.resize(_sparseLen*2);
      if(_id != null) _id = Arrays.copyOf(_id,_sparseLen*2);
    } else {
      _ms = new Mantissas(4);
      _xs = null;
      _ms.switchToLongs();
      _ds = new double[4];
    }
  }
  // Slow-path append string
  private void append2slowstr() {
    // In case of all NAs and then a string, convert NAs to string NAs
    if (_xs != null) {
      _xs = null; _ms = null;
      alloc_str_indices(_sparseLen);
      Arrays.fill(_is,-1);
    }

    if(_is != null && _is.length > 0){
      // Check for sparseness
      if(_id == null){
        int nzs = 0; // assume one non-null for the element currently being stored
        for( int i:_is) if( i != -1 ) ++nzs;
        if( (nzs+1)*_sparseRatio < _len)
          set_sparse(nzs, Compress.ZERO);
      } else {
        if((_sparseRatio*(_sparseLen) >> 2) > _len)  cancel_sparse();
        else _id = MemoryManager.arrayCopyOf(_id,_sparseLen<<1);
      }

      _is = MemoryManager.arrayCopyOf(_is, _sparseLen<<1);
      /* initialize the memory extension with -1s */
      for (int i = _sparseLen; i < _is.length; i++) _is[i] = -1;
    } else {
      _is = MemoryManager.malloc4 (4);
        /* initialize everything with -1s */
      for (int i = 0; i < _is.length; i++) _is[i] = -1;
      if (sparseZero()||sparseNA()) alloc_indices(4);
    }
    assert _sparseLen == 0 || _is.length > _sparseLen:"_ls.length = " + _is.length + ", _len = " + _sparseLen;

  }
  // Slow-path append data
  private void append2slow( ) {
// PUBDEV-2639 - don't die for many rows, few columns -> can be long chunks
//    if( _sparseLen > FileVec.DFLT_CHUNK_SIZE )
//      throw new ArrayIndexOutOfBoundsException(_sparseLen);
    assert _ds==null;
    if(_ms != null && _sparseLen > 0){
      if(_id == null) { // check for sparseness
        int nzs = _ms._nzs + (_missing != null?_missing.cardinality():0);
        int nonnas = _sparseLen - ((_missing != null)?_missing.cardinality():0);
        if((nonnas+1)*_sparseRatio < _len) {
          set_sparse(nonnas,Compress.NA);
          assert _id.length == _ms.len():"id.len = " + _id.length + ", ms.len = " + _ms.len();
          assert _sparseLen <= _len;
          return;
        } else if((nzs+1)*_sparseRatio < _len) { // note order important here
          set_sparse(nzs,Compress.ZERO);
          assert _sparseLen <= _len;
          assert _sparseLen == nzs;
          return;
        }
      } else {
        // verify we're still sufficiently sparse
        if(2*_sparseLen > _len)  cancel_sparse();
        else _id = MemoryManager.arrayCopyOf(_id, _id.length*2);
      }
      _ms.resize(_sparseLen*2);
      _xs.resize(_sparseLen*2);
    } else {
      _ms = new Mantissas(16);
      _xs = new Exponents(16);
      if (_id != null) _id = new int[16];
    }
    assert _sparseLen <= _len;
  }

  // Do any final actions on a completed NewVector.  Mostly: compress it, and
  // do a DKV put on an appropriate Key.  The original NewVector goes dead
  // (does not live on inside the K/V store).
  public Chunk new_close() {
    Chunk chk = compress();
    if(_vec instanceof AppendableVec)
      ((AppendableVec)_vec).closeChunk(_cidx,chk._len);
    return chk;
  }
  public void close(Futures fs) { close(_cidx,fs); }

  private void switch_to_doubles(){
    assert _ds == null;
    double [] ds = MemoryManager.malloc8d(_sparseLen);
    for(int i = 0; i < _sparseLen; ++i)
      if(isNA2(i) || isCategorical2(i)) ds[i] = Double.NaN;
      else  ds[i] = _ms.get(i)*PrettyPrint.pow10(_xs.get(i));
    _ms = null;
    _xs = null;
    _missing = null;
    _ds = ds;
  }

  public enum Compress {ZERO, NA}

  //Sparsify. Compressible element can be 0 or NA. Store noncompressible elements in _ds OR _ls and _xs OR _is and 
  // their row indices in _id.
  protected void set_sparse(int num_noncompressibles, Compress sparsity_type) {
    assert !isUUID():"sparse for uuids is not supported";
    if ((sparsity_type == Compress.ZERO && isSparseNA()) || (sparsity_type == Compress.NA && isSparseZero()))
      cancel_sparse();
    if (sparsity_type == Compress.NA) {
      _sparseNA = true;
    }
    if (_id != null && _sparseLen == num_noncompressibles && _len != 0) return;
    if (_id != null)
      cancel_sparse();
    assert _sparseLen == _len : "_sparseLen = " + _sparseLen + ", _len = " + _len + ", num_noncompressibles = " + num_noncompressibles;
    int cs = 0; //number of compressibles
    if (_is != null) {
      assert num_noncompressibles <= _is.length;
      _id = MemoryManager.malloc4(_is.length);
      for (int i = 0; i < _sparseLen; i++) {
        if (_is[i] == -1) cs++; //same condition for NA and 0
        else {
          _is[i - cs] = _is[i];
          _id[i - cs] = i;
        }
      }
    } else if (_ds == null) {
      if (_len == 0) {
        _ms = new Mantissas(0);
        _xs = new Exponents(0);
        _id = new int[0];
        set_sparseLen(0);
        return;
      } else {
        assert num_noncompressibles <= _sparseLen;
        _id = MemoryManager.malloc4(_ms.len());
        for (int i = 0; i < _sparseLen; ++i) {
          if (is_compressible(i)) {
            ++cs;
          } else  {
            _ms.move(i - cs, i);
            _xs.move(i - cs, i);
            _id[i - cs] = i;
            if(sparsity_type != Compress.NA && _missing != null){
              _missing.set(i-cs,_missing.get(i));
            }
          }
        }
        if(_missing != null && _missing.length() > num_noncompressibles)
            _missing.clear(num_noncompressibles, _missing.length());
      }
    } else {
      assert num_noncompressibles <= _ds.length;
      _id = alloc_indices(_ds.length);
      for (int i = 0; i < _sparseLen; ++i) {
        if (is_compressible(_ds[i])) ++cs;
        else {
          _ds[i - cs] = _ds[i];
          _id[i - cs] = i;
        }
      }
    }
    assert cs == (_sparseLen - num_noncompressibles) : "cs = " + cs + " != " + (_sparseLen - num_noncompressibles) + ", sparsity type = " + sparsity_type;
    assert (sparsity_type == Compress.NA) == _sparseNA;
    if(sparsity_type == Compress.NA && _missing != null)
      _missing.clear();
    set_sparseLen(num_noncompressibles);
  }

  private boolean is_compressible(double d) {
    return _sparseNA ? Double.isNaN(d) : d == 0;
  }

  private boolean is_compressible(int x) {
    return isNA2(x)?_sparseNA:!_sparseNA &&_ms.get(x) == 0;
  }

  public void cancel_sparse(){
    if(_sparseLen != _len){
      if(_is != null){
        int [] is = MemoryManager.malloc4(_len);
        Arrays.fill(is, -1);
        for (int i = 0; i < _sparseLen; i++) is[_id[i]] = _is[i];
        _is = is;
      } else if(_ds == null) {
        Exponents xs = new Exponents(_len);
        Mantissas ms = new Mantissas(_len);
        BitSet missing = new BitSet();
        if(_sparseNA)
          missing.set(0,_len);
        for (int i = 0; i < _sparseLen; ++i) {
          xs.set(_id[i], _xs.get(i));
          ms.set(_id[i], _ms.get(i));
          missing.set(_id[i], _sparseNA || (_missing != null && _missing.get(i)));
        }
        assert _sparseNA || (ms._nzs == _ms._nzs):_ms._nzs + " != " + ms._nzs;
        ms._nzs = _ms._nzs;
        _xs = xs;
        _missing = missing;
        _ms = ms;
      } else{
        double [] ds = MemoryManager.malloc8d(_len);
        _missing = new BitSet();
        if (_sparseNA) Arrays.fill(ds, Double.NaN);
        for(int i = 0; i < _sparseLen; ++i) {
          ds[_id[i]] = _ds[i];
          if(_sparseNA)_missing.set(_id[i]);
        }
        _ds = ds;
      }
      set_sparseLen(_len);
    }
    _id = null;
    _sparseNA = false;
  }

  // Study this NewVector and determine an appropriate compression scheme.
  // Return the data so compressed.
  public Chunk compress() {
    Chunk res = compress2();
    byte type = type();
    assert _vec == null ||  // Various testing scenarios do not set a Vec
      type == _vec._type || // Equal types
      // Allow all-bad Chunks in any type of Vec
      type == Vec.T_BAD ||
      // Specifically allow the NewChunk to be a numeric type (better be all
      // ints) and the selected Vec type an categorical - whose String mapping
      // may not be set yet.
      (type==Vec.T_NUM && _vec._type==Vec.T_CAT) ||
      // Another one: numeric Chunk and Time Vec (which will turn into all longs/zeros/nans Chunks)
      (type==Vec.T_NUM && _vec._type == Vec.T_TIME && !res.hasFloat())
      : "NewChunk has type "+Vec.TYPE_STR[type]+", but the Vec is of type "+_vec.get_type_str();
    assert _len == res._len : "NewChunk has length "+_len+", compressed Chunk has "+res._len;
    // Force everything to null after compress to free up the memory.  Seems
    // like a non-issue in the land of GC, but the NewChunk *should* be dead
    // after this, but might drag on.  The arrays are large, and during a big
    // Parse there's lots and lots of them... so free early just in case a GC
    // happens before the drag-time on the NewChunk finishes.
    _id = null;
    _xs = null;
    _ds = null;
    _ms = null;
    _is = null;
    _ss = null;
    return res;
  }

  private static long leRange(long lemin, long lemax){
    if(lemin < 0 && lemax >= (Long.MAX_VALUE + lemin))
      return Long.MAX_VALUE; // if overflow return 64 as the max possible value
    long res = lemax - lemin;
    return res < 0 ? 0 /*happens for rare FP roundoff computation of min & max */: res;
  }

  private Chunk compress2() {
    // Check for basic mode info: all missing or all strings or mixed stuff
    byte mode = type();
    if( mode==Vec.T_BAD ) // ALL NAs, nothing to do
      return new C0DChunk(Double.NaN, _len);
    if( mode==Vec.T_STR )
      return new CStrChunk(_sslen, _ss, _sparseLen, _len, _is, _isAllASCII);
    boolean rerun=false;
    if(mode == Vec.T_CAT) {
      for(int i = 0; i< _sparseLen; i++ )
        if(isCategorical2(i))
          _xs.set(i,0);
        else if(!isNA2(i)){
          setNA_impl2(i);
          ++_naCnt;
        }
        // Smack any mismatched string/numbers
    } else if( mode == Vec.T_NUM ) {
      for(int i = 0; i< _sparseLen; i++ )
        if(isCategorical2(i)) {
          setNA_impl2(i);
          rerun = true;
        }
    }
    if( rerun ) { _naCnt = -1;  type(); } // Re-run rollups after dropping all numbers/categoricals

    boolean sparse = false;
    boolean na_sparse = false;
    // sparse? treat as sparse iff fraction of noncompressed elements is less than 1/MIN_SPARSE_RATIO
    if(_sparseRatio*(_naCnt + _nzCnt) < _len) {
      set_sparse(_naCnt + _nzCnt, Compress.ZERO);
      sparse = true;
    } else if(_sparseRatio*(_len - _naCnt) < _len){
      set_sparse(_len - _naCnt, Compress.NA);
      na_sparse = true;
    } else if (_sparseLen != _len)
      cancel_sparse();

    // If the data is UUIDs there's not much compression going on
    if( _ds != null && _ms != null )
      return chunkUUID();
    // cut out the easy all NaNs case; takes care of constant na_sparse
    if(_naCnt == _len) return new C0DChunk(Double.NaN,_len);
    // If the data was set8 as doubles, we do a quick check to see if it's
    // plain longs.  If not, we give up and use doubles.
    if( _ds != null ) {
      int i; // check if we can flip to ints
      for (i=0; i < _sparseLen; ++i)
        if (!Double.isNaN(_ds[i]) && (double) (long) _ds[i] != _ds[i])
          break;
      boolean isInteger = i == _sparseLen;
      boolean isConstant = !(sparse || na_sparse) || _sparseLen == 0;
      double constVal = 0;
      if (!(sparse || na_sparse)) { // check the values, sparse with some nonzeros can not be constant - has 0s and (at least 1) nonzero
        constVal = _ds[0];
        for(int j = 1; j < _len; ++j)
          if(_ds[j] != constVal) {
            isConstant = false;
            break;
          }
      }
      if(isConstant)
        return isInteger? new C0LChunk((long)constVal, _len): new C0DChunk(constVal,_len);
      if(!isInteger) {
        if (sparse) return new CXDChunk(_len, 8, bufD(8));
        else if (na_sparse) return new CNAXDChunk(_len, 8, bufD(8));
        else return chunkD();
      }
      // Else flip to longs
      _ms = new Mantissas(_ds.length);
      _xs = new Exponents(_ds.length);
      _missing = new BitSet();
      double [] ds = _ds;
      _ds = null;
      final int naCnt = _naCnt;
      for(i=0; i< _sparseLen; i++ )   // Inject all doubles into longs
        if( Double.isNaN(ds[i]) ) {
          _missing.set(i);
        } else {
          _ms.set(i,(long)ds[i]);
          _xs.set(i,0);
        }
      // setNA_impl2 will set _naCnt to -1!
      // we already know what the naCnt is (it did not change!) so set it back to correct value
      _naCnt = naCnt;
    }

    // IF (_len > _sparseLen) THEN Sparse
    // Check for compressed *during appends*.  Here we know:
    // - No specials; _xs[]==0.
    // - No floats; _ds==null
    // - NZ length in _sparseLen, actual length in _len.
    // - Huge ratio between _len and _sparseLen, and we do NOT want to inflate to
    //   the larger size; we need to keep it all small all the time.
    // - Rows in _xs

    // Data in some fixed-point format, not doubles
    // See if we can sanely normalize all the data to the same fixed-point.
    int  xmin = Integer.MAX_VALUE;   // min exponent found
    boolean floatOverflow = false;
    double min = Double.POSITIVE_INFINITY;
    double max = Double.NEGATIVE_INFINITY;
    int p10iLength = PrettyPrint.powers10i.length;
    long llo=Long   .MAX_VALUE, lhi=Long   .MIN_VALUE;
    int  xlo=Integer.MAX_VALUE, xhi=Integer.MIN_VALUE;

    for(int i = 0; i< _sparseLen; i++ ) {
      if( isNA2(i) ) continue;
      long l = _ms.get(i);
      int  x = _xs.get(i);
      if( x==Integer.MIN_VALUE) x=0; // Replace categorical flag with no scaling
      assert l!=0 || x==0:"l == 0 while x = " + x + " ms = " + _ms.toString();      // Exponent of zero is always zero
      long t;                   // Remove extra scaling
      while( l!=0 && (t=l/10)*10==l ) { l=t; x++; }
      // Compute per-chunk min/max
      double d = l*PrettyPrint.pow10(x);
      if( d < min ) { min = d; llo=l; xlo=x; }
      if( d > max ) { max = d; lhi=l; xhi=x; }
      floatOverflow = l < Integer.MIN_VALUE+1 || l > Integer.MAX_VALUE;
      xmin = Math.min(xmin,x);
    }
    if(sparse){ // sparse?  then compare vs implied 0s
      if( min > 0 ) { min = 0; llo=0; xlo=0; }
      if( max < 0 ) { max = 0; lhi=0; xhi=0; }
      xmin = Math.min(xmin,0);
    }
    // Constant column?
    if( _naCnt==0 && (min==max)) {
      if (llo == lhi && xlo == 0 && xhi == 0)
        return new C0LChunk(llo, _len);
      else if ((long)min == min)
        return new C0LChunk((long)min, _len);
      else
        return new C0DChunk(min, _len);
    }

    // Compute min & max, as scaled integers in the xmin scale.
    // Check for overflow along the way
    boolean overflow = ((xhi-xmin) >= p10iLength) || ((xlo-xmin) >= p10iLength);
    long lemax=0, lemin=0;
    if( !overflow ) {           // Can at least get the power-of-10 without overflow
      long pow10 = PrettyPrint.pow10i(xhi-xmin);
      lemax = lhi*pow10;
      // Hacker's Delight, Section 2-13, checking overflow.
      // Note that the power-10 is always positive, so the test devolves this:
      if( (lemax/pow10) != lhi ) overflow = true;
      // Note that xlo might be > xmin; e.g. { 101e-49 , 1e-48}.
      long pow10lo = PrettyPrint.pow10i(xlo-xmin);
      lemin = llo*pow10lo;
      if( (lemin/pow10lo) != llo ) overflow = true;
    }

    // Boolean column?
    if (max == 1 && min == 0 && xmin == 0 && !overflow) {
      if(sparse) { // Very sparse?
        return  _naCnt==0
          ? new CX0Chunk(_len, bufS(0))// No NAs, can store as sparse bitvector
          : new CXIChunk(_len, 1,bufS(1)); // have NAs, store as sparse 1byte values
      }
      if(na_sparse) return new CNAXIChunk(_len, 1, bufS(1));
      int bpv = _catCnt +_naCnt > 0 ? 2 : 1;   // Bit-vector
      byte[] cbuf = bufB(bpv);
      return new CBSChunk(cbuf, cbuf[0], cbuf[1]);
    }

    final boolean fpoint = xmin < 0 || min < Long.MIN_VALUE || max > Long.MAX_VALUE;

    if( sparse ) {
      if(fpoint) return new CXDChunk(_len,8,bufD(8));
      int sz = 8;
      if( Short.MIN_VALUE <= min && max <= Short.MAX_VALUE ) sz = 2;
      else if( Integer.MIN_VALUE <= min && max <= Integer.MAX_VALUE ) sz = 4;
      return new CXIChunk(_len,sz,bufS(sz));
    }
    if( na_sparse ) {
      if(fpoint) return new CNAXDChunk(_len,8,bufD(8));
      int sz = 8;
      if( Short.MIN_VALUE <= min && max <= Short.MAX_VALUE ) sz = 2;
      else if( Integer.MIN_VALUE <= min && max <= Integer.MAX_VALUE ) sz = 4;
      return new CNAXIChunk(_len,sz,bufS(sz));      
    }
    // Exponent scaling: replacing numbers like 1.3 with 13e-1.  '13' fits in a
    // byte and we scale the column by 0.1.  A set of numbers like
    // {1.2,23,0.34} then is normalized to always be represented with 2 digits
    // to the right: {1.20,23.00,0.34} and we scale by 100: {120,2300,34}.
    // This set fits in a 2-byte short.

    // We use exponent-scaling for bytes & shorts only; it's uncommon (and not
    // worth it) for larger numbers.  We need to get the exponents to be
    // uniform, so we scale up the largest lmax by the largest scale we need
    // and if that fits in a byte/short - then it's worth compressing.  Other
    // wise we just flip to a float or double representation.
    if( overflow || (fpoint && floatOverflow) || -35 > xmin || xmin > 35 )
      return chunkD();
    final long leRange = leRange(lemin,lemax);
    if( fpoint ) {
      if( (int)lemin == lemin && (int)lemax == lemax ) {
        if(leRange < 255) // Fits in scaled biased byte?
          return new C1SChunk( bufX(lemin,xmin,C1SChunk._OFF,0),lemin,PrettyPrint.pow10(xmin));
        if(leRange < 65535) { // we use signed 2B short, add -32k to the bias!
          long bias = 32767 + lemin;
          return new C2SChunk( bufX(bias,xmin,C2SChunk._OFF,1),bias,PrettyPrint.pow10(xmin));
        }
      }
      if(leRange < 4294967295l) {
        long bias = 2147483647l + lemin;
        return new C4SChunk( bufX(bias,xmin,C4SChunk._OFF,2),bias,PrettyPrint.pow10(xmin));
      }
      return chunkD();
    } // else an integer column

    // Compress column into a byte
    if(xmin == 0 &&  0<=lemin && lemax <= 255 && ((_naCnt + _catCnt)==0) )
      return new C1NChunk( bufX(0,0,C1NChunk._OFF,0));
    if( lemin < Integer.MIN_VALUE ) return new C8Chunk( bufX(0,0,0,3));
    if( leRange < 255 ) {    // Span fits in a byte?
      if(0 <= min && max < 255 ) // Span fits in an unbiased byte?
        return new C1Chunk( bufX(0,0,C1Chunk._OFF,0));
      return new C1SChunk( bufX(lemin,xmin,C1SChunk._OFF,0),lemin,PrettyPrint.pow10i(xmin));
    }

    // Compress column into a short
    if( leRange < 65535 ) {               // Span fits in a biased short?
      if( xmin == 0 && Short.MIN_VALUE < lemin && lemax <= Short.MAX_VALUE ) // Span fits in an unbiased short?
        return new C2Chunk( bufX(0,0,C2Chunk._OFF,1));
      long bias = (lemin-(Short.MIN_VALUE+1));
      return new C2SChunk( bufX(bias,xmin,C2SChunk._OFF,1),bias,PrettyPrint.pow10i(xmin));
    }
    // Compress column into ints
    if( Integer.MIN_VALUE < min && max <= Integer.MAX_VALUE )
      return new C4Chunk( bufX(0,0,0,2));
    return new C8Chunk( bufX(0,0,0,3));
  }

  private static long [] NAS = {C1Chunk._NA,C2Chunk._NA,C4Chunk._NA,C8Chunk._NA};

  // Compute a sparse integer buffer
  private byte[] bufS(final int valsz){
    int log = 0;
    while((1 << log) < valsz)++log;
    assert valsz == 0 || (1 << log) == valsz;
    final int ridsz = _len >= 65535?4:2;
    final int elmsz = ridsz + valsz;
    int off = CXIChunk._OFF;
    byte [] buf = MemoryManager.malloc1(off + _sparseLen *elmsz,true);
    for(int i = 0; i< _sparseLen; i++, off += elmsz ) {
      if(ridsz == 2)
        UnsafeUtils.set2(buf,off,(short)_id[i]);
      else
        UnsafeUtils.set4(buf,off,_id[i]);
      if(valsz == 0){
        assert _xs.get(i) == 0 && _ms.get(i) == 1;
        continue;
      }
      assert isNA2(i) || _xs.get(i) >= 0:"unexpected exponent " + _xs.get(i); // assert we have int or NA
      final long lval = isNA2(i) ? NAS[log] : _ms.get(i)*PrettyPrint.pow10i(_xs.get(i));
      switch(valsz){
        case 1:
          buf[off+ridsz] = (byte)lval;
          break;
        case 2:
          short sval = (short)lval;
          UnsafeUtils.set2(buf,off+ridsz,sval);
          break;
        case 4:
          int ival = (int)lval;
          UnsafeUtils.set4(buf, off + ridsz, ival);
          break;
        case 8:
          UnsafeUtils.set8(buf, off + ridsz, lval);
          break;
        default:
          throw H2O.fail();
      }
    }
    assert off==buf.length;
    return buf;
  }

  // Compute a sparse float buffer
  private byte[] bufD(final int valsz){
    int log = 0;
    while((1 << log) < valsz)++log;
    assert (1 << log) == valsz;
    final int ridsz = _len >= 65535?4:2;
    final int elmsz = ridsz + valsz;
    int off = CXDChunk._OFF;
    byte [] buf = MemoryManager.malloc1(off + _sparseLen *elmsz,true);
    for(int i = 0; i< _sparseLen; i++, off += elmsz ) {
      if(ridsz == 2)
        UnsafeUtils.set2(buf,off,(short)_id[i]);
      else
        UnsafeUtils.set4(buf,off,_id[i]);
      final double dval = _ds == null?isNA2(i)?Double.NaN:_ms.get(i)*PrettyPrint.pow10(_xs.get(i)):_ds[i];
      switch(valsz){
        case 4:
          UnsafeUtils.set4f(buf, off + ridsz, (float) dval);
          break;
        case 8:
          UnsafeUtils.set8d(buf, off + ridsz, dval);
          break;
        default:
          throw H2O.fail();
      }
    }
    assert off==buf.length;
    return buf;
  }
  // Compute a compressed integer buffer
  private byte[] bufX( long bias, int scale, int off, int log ) {
    byte[] bs = MemoryManager.malloc1((_len <<log)+off);
    int j = 0;
    for( int i=0; i< _len; i++ ) {
      long le = -bias;
      if(_id == null || _id.length == 0 || (j < _id.length && _id[j] == i)){
        if( isNA2(j) ) {
          le = NAS[log];
        } else {
          int x = (_xs.get(j)==Integer.MIN_VALUE+1 ? 0 : _xs.get(j))-scale;
          le += x >= 0
              ? _ms.get(j)*PrettyPrint.pow10i( x)
              : _ms.get(j)/PrettyPrint.pow10i(-x);
        }
        ++j;
      }
      switch( log ) {
      case 0:          bs [i    +off] = (byte)le ; break;
      case 1: UnsafeUtils.set2(bs,(i<<1)+off,  (short)le); break;
      case 2: UnsafeUtils.set4(bs, (i << 2) + off, (int) le); break;
      case 3: UnsafeUtils.set8(bs, (i << 3) + off, le); break;
      default: throw H2O.fail();
      }
    }
    assert j == _sparseLen :"j = " + j + ", _sparseLen = " + _sparseLen;
    return bs;
  }

  // Compute a compressed double buffer
  private Chunk chunkD() {
    HashMap<Long,Byte> hs = new HashMap<>(CUDChunk.MAX_UNIQUES);
    Byte dummy = 0;
    final byte [] bs = MemoryManager.malloc1(_len *8,true);
    int j = 0;
    boolean fitsInUnique = true;
    for(int i = 0; i < _len; ++i){
      double d = 0;
      if(_id == null || _id.length == 0 || (j < _id.length && _id[j] == i)) {
        d = _ds != null?_ds[j]:(isNA2(j)|| isCategorical(j))?Double.NaN:_ms.get(j)*PrettyPrint.pow10(_xs.get(j));
        ++j;
      }
      if (fitsInUnique) {
        if (hs.size() < CUDChunk.MAX_UNIQUES) //still got space
          hs.put(Double.doubleToLongBits(d),dummy); //store doubles as longs to avoid NaN comparison issues during extraction
        else fitsInUnique = (hs.size() == CUDChunk.MAX_UNIQUES) && // full, but might not need more space because of repeats
                            hs.containsKey(Double.doubleToLongBits(d));
      }
      UnsafeUtils.set8d(bs, 8*i, d);
    }
    assert j == _sparseLen :"j = " + j + ", _len = " + _sparseLen;
    if (fitsInUnique && CUDChunk.computeByteSize(hs.size(), len()) < 0.8 * bs.length)
      return new CUDChunk(bs, hs, len());
    else
      return new C8DChunk(bs);
  }

  // Compute a compressed UUID buffer
  private Chunk chunkUUID() {
    final byte [] bs = MemoryManager.malloc1(_len *16,true);
    int j = 0;
    for( int i = 0; i < _len; ++i ) {
      long lo = 0, hi=0;
      if( _id == null || _id.length == 0 || (j < _id.length && _id[j] == i ) ) {
        if(_missing != null && _missing.get(j)) {
          lo = C16Chunk._LO_NA;
          hi = C16Chunk._HI_NA;
        } else {
          lo = _ms.get(j);
          hi = Double.doubleToRawLongBits(_ds[j]);
        }
        j++;
      }
      UnsafeUtils.set8(bs, 16*i  , lo);
      UnsafeUtils.set8(bs, 16 * i + 8, hi);
    }
    assert j == _sparseLen :"j = " + j + ", _sparselen = " + _sparseLen;
    return new C16Chunk(bs);
  }

  // Compute compressed boolean buffer
  private byte[] bufB(int bpv) {
    assert bpv == 1 || bpv == 2 : "Only bit vectors with/without NA are supported";
    final int off = CBSChunk._OFF;
    int clen  = off + CBSChunk.clen(_len, bpv);
    byte bs[] = new byte[clen];
    // Save the gap = number of unfilled bits and bpv value
    bs[0] = (byte) (((_len *bpv)&7)==0 ? 0 : (8-((_len *bpv)&7)));
    bs[1] = (byte) bpv;

    // Dense bitvector
    int  boff = 0;
    byte b    = 0;
    int  idx  = CBSChunk._OFF;
    int j = 0;
    for (int i=0; i< _len; i++) {
      byte val = 0;
      if(_id == null || (j < _id.length && _id[j] == i)) {
        assert bpv == 2 || !isNA2(j);
        val = (byte)(isNA2(j)?CBSChunk._NA:_ms.get(j));
        ++j;
      }
      if( bpv==1 )
        b = CBSChunk.write1b(b, val, boff);
      else
        b = CBSChunk.write2b(b, val, boff);
      boff += bpv;
      if (boff>8-bpv) { assert boff == 8; bs[idx] = b; boff = 0; b = 0; idx++; }
    }
    assert j == _sparseLen;
    assert bs[0] == (byte) (boff == 0 ? 0 : 8-boff):"b[0] = " + bs[0] + ", boff = " + boff + ", bpv = " + bpv;
    // Flush last byte
    if (boff>0) bs[idx] = b;
    return bs;
  }

  // Set & At on NewChunks are weird: only used after inflating some other
  // chunk.  At this point the NewChunk is full size, no more appends allowed,
  // and the xs exponent array should be only full of zeros.  Accesses must be
  // in-range and refer to the inflated values of the original Chunk.
  @Override boolean set_impl(int i, long l) {
    if( _ds   != null ) return set_impl(i,(double)l);
    if(_sparseLen != _len){ // sparse?
      int idx = Arrays.binarySearch(_id,0, _sparseLen,i);
      if(idx >= 0)i = idx;
      else cancel_sparse(); // for now don't bother setting the sparse value
    }
    _ms.set(i,l);
    _xs.set(i,0);
    if(_missing != null)_missing.clear(i);
    _naCnt = -1;
    return true;
  }

  @Override public boolean set_impl(int i, double d) {
    if(_ds == null && (long)d == d)
      return set_impl(i,(long)d);
    if(_ds == null) {
      if (_is == null) { //not a string
        assert _sparseLen == 0 || _ms != null;
        switch_to_doubles();
      } else {
        if (_is[i] == -1) return true; //nothing to do: already NA
        assert(Double.isNaN(d)) : "can only set strings to <NA>, nothing else";
        set_impl(i, null); //null encodes a missing string: <NA>
        return true;
      }
    }
    if(_sparseLen != _len){ // sparse?
      int idx = Arrays.binarySearch(_id,0, _sparseLen,i);
      if(idx >= 0)i = idx;
      else cancel_sparse(); // for now don't bother setting the sparse value
    }
    assert i < _sparseLen;
    _ds[i] = d;
    _naCnt = -1;
    return true;
  }
  @Override boolean set_impl(int i, float f) {  return set_impl(i,(double)f); }

  @Override boolean set_impl(int i, String str) {
    if (str == null) {
      return setNA_impl(i);
    }
    if(_is == null && _len > 0) {
      assert _sparseLen == 0;
      alloc_str_indices(_len);
      Arrays.fill(_is,-1);
    }
    if(_sparseLen != _len){ // sparse?
      int idx = Arrays.binarySearch(_id,0, _sparseLen,i);
      if(idx >= 0)i = idx;
      else cancel_sparse(); // for now don't bother setting the sparse value
    }
    if (_is != null) _is[i] = _sslen;
    append_ss(str);
    return true;
  }

  protected final boolean setNA_impl2(int i) {
    if(!isUUID() && _ds != null) {
      _ds[i] = Double.NaN;
      return true;
    }
    if(isString()) {
      _is[i] = -1;
      return true;
    }
    if(_missing == null) _missing = new BitSet();
    _missing.set(i);
    _ms.set(i,0); // do not double count non-zeros
    _naCnt = -1;
    return true;
  }
  @Override boolean setNA_impl(int i) {
    if( isNA_impl(i) ) return true;
    if(_sparseLen != _len){
      int idx = Arrays.binarySearch(_id,0, _sparseLen,i);
      if(idx >= 0) i = idx;
      else cancel_sparse(); // todo - do not necessarily cancel sparse here
    }
    return setNA_impl2(i);
  }

  protected final long at8_impl2(int i) {
    if(isNA2(i))throw new RuntimeException("Attempting to access NA as integer value.");
    if( _ms == null ) return (long)_ds[i];
    return _ms.get(i)*PrettyPrint.pow10i(_xs.get(i));
  }

  @Override public long at8_impl( int i ) {
    if( _len != _sparseLen) {
      int idx = Arrays.binarySearch(_id,0, _sparseLen,i);
      if(idx >= 0) i = idx;
      else {
        if (_sparseNA) throw new RuntimeException("Attempting to access NA as integer value.");
        return 0;
      }
    }
    return at8_impl2(i);
  }
  @Override public double atd_impl( int i ) {
    if( _len != _sparseLen) {
      int idx = Arrays.binarySearch(_id,0, _sparseLen,i);
      if(idx >= 0) i = idx;
      else return sparseNA() ? Double.NaN : 0;
    }
    if (isNA2(i)) return Double.NaN;
    // if exponent is Integer.MIN_VALUE (for missing value) or >=0, then go the integer path (at8_impl)
    // negative exponents need to be handled right here
    if( _ds == null ) return _xs.get(i) >= 0 ? at8_impl2(i) : _ms.get(i)*Math.pow(10,_xs.get(i));
    assert _xs==null; 
    return _ds[i];
  }

  private long loAt(int idx) { return _ms.get(idx); }
  private long hiAt(int idx) { return Double.doubleToRawLongBits(_ds[idx]); }

  @Override protected long at16l_impl(int idx) {
    long lo = loAt(idx);
    if(lo == C16Chunk._LO_NA && hiAt(idx) == C16Chunk._HI_NA) {
      throw new RuntimeException("Attempting to access NA as integer lo value at " + idx);
    }
    return _ms.get(idx);
  }
  @Override protected long at16h_impl(int idx) {
    long hi = Double.doubleToRawLongBits(_ds[idx]);
    if(hi == C16Chunk._HI_NA && loAt(idx) == C16Chunk._LO_NA) {
      throw new RuntimeException("Attempting to access NA as integer hi value at " + idx);
    }
    return hi;
  }
  @Override public boolean isNA_impl( int i ) {
    if (_len != _sparseLen) {
      int idx = Arrays.binarySearch(_id, 0, _sparseLen, i);
      if (idx >= 0) i = idx;
      else return sparseNA();
    }
    return !sparseNA() && isNA2(i);
  }
  @Override public BufferedString atStr_impl( BufferedString bStr, int i ) {
    if( _sparseLen != _len ) {
      int idx = Arrays.binarySearch(_id,0, _sparseLen,i);
      if(idx >= 0) i = idx;
      else return null;
    }

    if( _is[i] == CStrChunk.NA ) return null;

    int len = 0;
    while( _ss[_is[i] + len] != 0 ) len++;
    return bStr.set(_ss, _is[i], len);
  }
  @Override protected final void initFromBytes () {throw H2O.fail();}
  public static AutoBuffer write_impl(NewChunk nc,AutoBuffer bb) { throw H2O.fail(); }
  @Override public NewChunk inflate_impl(NewChunk nc) { throw H2O.fail(); }
  @Override public String toString() { return "NewChunk._sparseLen="+ _sparseLen; }

  // We have to explicitly override cidx implementation since we hide _cidx field with new version
  @Override
  public int cidx() {
    return _cidx;
  }
}
