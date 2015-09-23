package water.parser;

// ------------------------------------------------------------------------

import water.Futures;
import water.Iced;
import water.exceptions.H2OParseException;
import water.fvec.AppendableVec;
import water.fvec.NewChunk;
import water.fvec.Vec;

/** Parsed data output specialized for fluid vecs.
 * @author tomasnykodym
 */
public class FVecParseWriter extends Iced implements StreamParseWriter {
  protected transient NewChunk[] _nvs;
  protected AppendableVec[]_vecs;
  protected final Categorical [] _enums;
  protected transient byte[] _ctypes;
  long _nLines;
  int _nCols;
  int _col = -1;
  final int _cidx;
  final int _chunkSize;
  boolean _closedVecs = false;
  int _nChunks;
  private final Vec.VectorGroup _vg;

  public int nChunks(){return _nChunks;}

  public FVecParseWriter(Vec.VectorGroup vg, int cidx, Categorical[] enums, byte[] ctypes, int chunkSize, AppendableVec[] avs){
    if (ctypes != null) _ctypes = ctypes;
    else _ctypes = new byte[avs.length];
    _vecs = avs;
    _nvs = new NewChunk[avs.length];
    for(int i = 0; i < avs.length; ++i)
      _nvs[i] = _vecs[i].chunkForChunkIdx(cidx);
    _enums = enums;
    _nCols = avs.length;
    _cidx = cidx;
    _vg = vg;
    _chunkSize = chunkSize;
  }

  @Override public FVecParseWriter reduce(StreamParseWriter sdout){
    FVecParseWriter dout = (FVecParseWriter)sdout;
    if( dout == null ) return this;
    _nCols = Math.max(_nCols,dout._nCols);
    _nChunks += dout._nChunks;
    if( dout!=null && _vecs != dout._vecs) {
      if(dout._vecs.length > _vecs.length) {
        AppendableVec [] v = _vecs;
        _vecs = dout._vecs;
        for(int i = 1; i < _vecs.length; ++i)
          _vecs[i]._tmp_espc = _vecs[0]._tmp_espc;
        dout._vecs = v;
      }
      for(int i = 0; i < dout._vecs.length; ++i) {
        _vecs[i].reduce(dout._vecs[i]);
      }
    }

    return this;
  }
  @Override public FVecParseWriter close(){
    Futures fs = new Futures();
    close(fs);
    fs.blockForPending();
    return this;
  }
  @Override public FVecParseWriter close(Futures fs){
    ++_nChunks;
    if( _nvs == null ) return this; // Might call close twice
    for(NewChunk nv:_nvs) nv.close(_cidx, fs);
    _nvs = null;  // Free for GC
    return this;
  }
  @Override public FVecParseWriter nextChunk(){
    return  new FVecParseWriter(_vg, _cidx+1, _enums, _ctypes, _chunkSize, _vecs);
  }

  /* never called
  private Vec [] closeVecs(){
    Futures fs = new Futures();
    _closedVecs = true;
    Vec [] res = new Vec[_vecs.length];
    for(int i = 0; i < _vecs[0]._espc.length; ++i){
      int j = 0;
      while(j < _vecs.length && _vecs[j]._espc[i] == 0)++j;
      if(j == _vecs.length)break;
      final long clines = _vecs[j]._espc[i];
      for(AppendableVec v:_vecs) {
        if(v._espc[i] == 0)v._espc[i] = clines;
        else assert v._espc[i] == clines:"incompatible number of lines: " +  v._espc[i] +  " != " + clines;
      }
    }
    for(int i = 0; i < _vecs.length; ++i)
      res[i] = _vecs[i].close(fs);
    _vecs = null;  // Free for GC
    fs.blockForPending();
    return res;
  } */

  @Override public void newLine() {
    if(_col >= 0){
      ++_nLines;
      for(int i = _col+1; i < _nCols; ++i)
        addInvalidCol(i);
    }
    _col = -1;
  }
  @Override public void addNumCol(int colIdx, long number, int exp) {
    if( colIdx < _nCols ) {
      _nvs[_col = colIdx].addNum(number, exp);
      if(_ctypes[colIdx] == Vec.T_BAD ) _ctypes[colIdx] = Vec.T_NUM;
    }
  }

  @Override public final void addInvalidCol(int colIdx) {
    if(colIdx < _nCols) _nvs[_col = colIdx].addNA();
  }
  @Override public boolean isString(int colIdx) { return (colIdx < _nCols) && (_ctypes[colIdx] == Vec.T_ENUM || _ctypes[colIdx] == Vec.T_STR);}

  @Override public void addStrCol(int colIdx, ValueString str) {
    if(colIdx < _nvs.length){
      if(_ctypes[colIdx] == Vec.T_NUM){ // support enforced types
        addInvalidCol(colIdx);
        return;
      }
      if(_ctypes[colIdx] == Vec.T_BAD && ParseTime.isTime(str))
        _ctypes[colIdx] = Vec.T_TIME;
      if( _ctypes[colIdx] == Vec.T_BAD && ParseUUID.isUUID(str))
        _ctypes[colIdx] = Vec.T_UUID;

      if( _ctypes[colIdx] == Vec.T_TIME ) {
        long l = ParseTime.attemptTimeParse(str);
        if( l == Long.MIN_VALUE ) addInvalidCol(colIdx);
        else {
          addNumCol(colIdx, l, 0);               // Record time in msec
          _nvs[_col]._timCnt++; // Count histo of time parse patterns
        }
      } else if( _ctypes[colIdx] == Vec.T_UUID ) { // UUID column?  Only allow UUID parses
        long[] uuid = ParseUUID.attemptUUIDParse(str);
        // FIXME: what if colIdx > _nCols
        if( colIdx < _nCols ) _nvs[_col = colIdx].addUUID(uuid[0], uuid[1]);
      } else if( _ctypes[colIdx] == Vec.T_STR ) {
        _nvs[_col = colIdx].addStr(str);
      } else { // Enums
        if(!_enums[colIdx].isMapFull()) {
          int id = _enums[_col = colIdx].addKey(str);
          if (_ctypes[colIdx] == Vec.T_BAD && id > 1) _ctypes[colIdx] = Vec.T_ENUM;
          _nvs[colIdx].addEnum(id);
        } else { // maxed out enum map
          throw new H2OParseException("Exceeded enumeration limit on column #"+(colIdx+1)+" (using 1-based indexing).  Consider reparsing this column as a string.");
        }
      }
    }
  }

  /** Adds double value to the column. */
  @Override public void addNumCol(int colIdx, double value) {
    if (Double.isNaN(value)) {
      addInvalidCol(colIdx);
    } else {
      double d= value;
      int exp = 0;
      long number = (long)d;
      while (number != d) {
        d = d * 10;
        --exp;
        number = (long)d;
      }
      addNumCol(colIdx, number, exp);
    }
  }
  @Override public void setColumnNames(String [] names){}
  @Override public final void rollbackLine() {}
  @Override public void invalidLine(String err) { newLine(); }
  @Override public void setIsAllASCII(int colIdx, boolean b) {_nvs[colIdx]._isAllASCII = b;}
}
