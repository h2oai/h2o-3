package water.parser;

import water.Futures;
import water.Iced;
import water.fvec.*;
import water.util.ArrayUtils;

import java.util.Arrays;

/** Parsed data output specialized for fluid vecs.
 * @author tomasnykodym
 */
public class FVecParseWriter extends Iced implements StreamParseWriter {
  protected AppendableVec _vecs;
  protected transient NewChunkAry _nvs;
  protected transient final Categorical [] _categoricals;
  protected transient final byte[] _ctypes;
  int _nLines;
  int _nCols;
  int _col = -1;
  final int _cidx;
  final int _chunkSize;
  ParseErr [] _errs = new ParseErr[0];
  private final Vec.VectorGroup _vg;
  private long _errCnt;

  public FVecParseWriter(Vec.VectorGroup vg, int cidx, Categorical[] categoricals, byte[] ctypes, int chunkSize, AppendableVec avs){
    _ctypes = ctypes;           // Required not-null
    _vecs = avs;
    _nvs =  avs.chunkForChunkIdx(cidx);
    _categoricals = categoricals;
    _nCols = avs.numCols();
    _cidx = cidx;
    _vg = vg;
    _chunkSize = chunkSize;
  }

  @Override public FVecParseWriter reduce(StreamParseWriter sdout){
    FVecParseWriter dout = (FVecParseWriter)sdout;
    _nCols = Math.max(_nCols,dout._nCols); // SVMLight: max of columns
    if( _vecs != dout._vecs ) {
      if( dout._vecs.numCols() > _vecs.numCols() ) { // Swap longer one over the returned value
        AppendableVec tmpv = _vecs;  _vecs = dout._vecs;  dout._vecs = tmpv;
      }
      _vecs.reduce(dout._vecs);
    }
    _errCnt += ((FVecParseWriter) sdout)._errCnt;
    if(_errs.length < 20 && ((FVecParseWriter) sdout)._errs.length > 0) {
      _errs = ArrayUtils.append(_errs, ((FVecParseWriter) sdout)._errs);
      if(_errs.length > 20)
        _errs = Arrays.copyOf(_errs,20);
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
    if( _nvs == null ) return this; // Might call close twice
    _nvs.close(fs);
    _nvs = null;  // Free for GC
    return this;
  }
  @Override public FVecParseWriter nextChunk(){
    return  new FVecParseWriter(_vg, _cidx+1, _categoricals, _ctypes, _chunkSize, _vecs);
  }

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
      _nvs.addNum(_col = colIdx,number, exp);
      if(_ctypes != null && _ctypes[colIdx] == Vec.T_BAD ) _ctypes[colIdx] = Vec.T_NUM;
    }
  }

  @Override public final void addInvalidCol(int colIdx) {
    if(colIdx < _nCols) _nvs.addNA(_col = colIdx);
  }
  @Override public boolean isString(int colIdx) { return (colIdx < _nCols) && (_ctypes[colIdx] == Vec.T_CAT || _ctypes[colIdx] == Vec.T_STR);}

  @Override public void addStrCol(int colIdx, BufferedString str) {
    if(colIdx < _nvs._numCols){
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
        else  addNumCol(colIdx, l, 0);               // Record time in msec
      } else if( _ctypes[colIdx] == Vec.T_UUID ) { // UUID column?  Only allow UUID parses
        long[] uuid = ParseUUID.attemptUUIDParse(str);
        // FIXME: what if colIdx > _nCols
        if( colIdx < _nCols ) _nvs.addUUID(_col = colIdx,uuid[0], uuid[1]);
      } else if( _ctypes[colIdx] == Vec.T_STR ) {
        _nvs.addStr(_col = colIdx,str);
      } else { // categoricals
        if(!_categoricals[colIdx].isMapFull()) {
          int id = _categoricals[_col = colIdx].addKey(str);
          if (_ctypes[colIdx] == Vec.T_BAD && id > 1) _ctypes[colIdx] = Vec.T_CAT;
          if(_ctypes[colIdx] == Vec.T_CAT) {
            _nvs.addInteger(colIdx,id); // if we are sure we have a categorical column, we can only store the integer (more efficient than remembering this value was categorical)
          } else
            _nvs.addInteger(colIdx,id);
        } else { // maxed out categorical map
          throw new ParseDataset.H2OParseException("Exceeded categorical limit on column #"+(colIdx+1)+" (using 1-based indexing).  Consider reparsing this column as a string.");
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
        d *= 10;
        --exp;
        number = (long)d;
      }
      addNumCol(colIdx, number, exp);
    }
  }
  @Override public void setColumnNames(String [] names){}
  @Override public final void rollbackLine() {}

  @Override public void invalidLine(ParseErr err) {
    addErr(err);
    newLine();
  }

  @Override
  public void addError(ParseErr err) {
    if(_errs == null)
      _errs = new ParseErr[]{err};
    else  if(_errs.length < 20)
      _errs = ArrayUtils.append(_errs,err);
    _errCnt++;
  }

  @Override public void setIsAllASCII(int colIdx, boolean b) {
    if(colIdx < _nvs._numCols)
      ((NewChunk)_nvs.getChunk(colIdx))._isAllASCII = b;
  }

  @Override
  public boolean hasErrors() {
    return _errs != null && _errs.length > 0;
  }
  @Override
  public ParseErr[] removeErrors() {
    ParseErr [] res = _errs;
    _errs = null;
    return res;
  }

  @Override
  public long lineNum() {return _nLines;}

  public void addErr(ParseErr err){
    if(_errs.length < 20)
      _errs = ArrayUtils.append(_errs,err);
    ++_errCnt;
  }
}
