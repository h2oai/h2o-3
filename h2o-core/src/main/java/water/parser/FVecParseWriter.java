package water.parser;

import water.Futures;
import water.Iced;
import water.fvec.AppendableVec;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.PrettyPrint;

import java.util.Arrays;
import java.util.UUID;

/** Parsed data output specialized for fluid vecs.
 * @author tomasnykodym
 */
public class FVecParseWriter extends Iced implements StreamParseWriter {

  private static final int MAX_ERR_CNT = 20;

  protected AppendableVec[] _vecs;
  protected transient NewChunk[] _nvs;
  protected transient final Categorical [] _categoricals;
  protected transient final byte[] _ctypes;
  long _nLines;
  int _nCols;
  int _col = -1;
  final int _cidx;
  final int _chunkSize;
  ParseErr [] _errs = new ParseErr[0];
  private final Vec.VectorGroup _vg;
  private long _errCnt;
  private int _maxMissingCol = -1;
  private transient StringBuilder _missingColVals = null;

  public FVecParseWriter(Vec.VectorGroup vg, int cidx, Categorical[] categoricals, byte[] ctypes, int chunkSize, AppendableVec[] avs){
    _ctypes = ctypes;           // Required not-null
    _vecs = avs;
    _nvs = new NewChunk[avs.length];
    for(int i = 0; i < avs.length; ++i)
      _nvs[i] = _vecs[i].chunkForChunkIdx(cidx);
    _categoricals = categoricals;
    _nCols = avs.length;
    _cidx = cidx;
    _vg = vg;
    _chunkSize = chunkSize;
  }

  @Override public FVecParseWriter reduce(StreamParseWriter sdout){
    FVecParseWriter dout = (FVecParseWriter)sdout;
    _nCols = Math.max(_nCols,dout._nCols); // SVMLight: max of columns
    if( _vecs != dout._vecs ) {
      if( dout._vecs.length > _vecs.length ) { // Swap longer one over the returned value
        AppendableVec[] tmpv = _vecs;  _vecs = dout._vecs;  dout._vecs = tmpv;
      }
      for(int i = 0; i < dout._vecs.length; ++i)
        _vecs[i].reduce(dout._vecs[i]);
    }
    _errCnt += ((FVecParseWriter) sdout)._errCnt;
    if(_errs.length < MAX_ERR_CNT && ((FVecParseWriter) sdout)._errs.length > 0) {
      _errs = ArrayUtils.append(_errs, ((FVecParseWriter) sdout)._errs);
      if(_errs.length > MAX_ERR_CNT)
        _errs = Arrays.copyOf(_errs,MAX_ERR_CNT);
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
    for(int i=0; i < _nvs.length; i++) {
      _nvs[i].close(_cidx, fs);
      _nvs[i] = null; // free immediately, don't wait for all columns to close
    }
    _nvs = null;  // Free for GC
    return this;
  }
  @Override public FVecParseWriter nextChunk(){
    return  new FVecParseWriter(_vg, _cidx+1, _categoricals, _ctypes, _chunkSize, _vecs);
  }

  @Override public void newLine() {
    if(_col >= 0){
      for(int i = _col+1; i < _nCols; ++i)
        addInvalidCol(i);
      if (_maxMissingCol != -1)
        addMissingColumnsError();
      ++_nLines;
    }
    _col = -1;
    _maxMissingCol = -1;
    _missingColVals = null;
  }

  @Override public void addNumCol(int colIdx, long number, int exp) {
    if( colIdx < _nCols ) {
      _nvs[_col = colIdx].addNum(number, exp);
      if(_ctypes != null && _ctypes[colIdx] == Vec.T_BAD ) _ctypes[colIdx] = Vec.T_NUM;
    } else
      recordMissingColumnError(colIdx, number * PrettyPrint.pow10(exp));
  }

  @Override public final void addInvalidCol(int colIdx) {
    if(colIdx < _nCols)
      _nvs[_col = colIdx].addNA();
    else
      recordMissingColumnError(colIdx, null);
  }
  @Override public boolean isString(int colIdx) { return (colIdx < _nCols) && (_ctypes[colIdx] == Vec.T_CAT || _ctypes[colIdx] == Vec.T_STR);}

  @Override public void addStrCol(int colIdx, BufferedString str) {
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
        UUID uuid = ParseUUID.attemptUUIDParse(str);
        // FIXME: what if colIdx > _nCols
        if( colIdx < _nCols ) _nvs[_col = colIdx].addUUID(uuid);
      } else if( _ctypes[colIdx] == Vec.T_STR ) {
        _nvs[_col = colIdx].addStr(str);
      } else { // categoricals
        if(!_categoricals[colIdx].isMapFull()) {
          int id = _categoricals[_col = colIdx].addKey(str);
          if (_ctypes[colIdx] == Vec.T_BAD && id > 1) _ctypes[colIdx] = Vec.T_CAT;
          if(_ctypes[colIdx] == Vec.T_CAT) {
            _nvs[colIdx].addNum(id, 0); // if we are sure we have a categorical column, we can only store the integer (more efficient than remembering this value was categorical)
          } else
            _nvs[colIdx].addCategorical(id);
        } else { // maxed out categorical map
          throw new ParseDataset.H2OParseException("Exceeded categorical limit on column #"+(colIdx+1)+" (using 1-based indexing).  Consider reparsing this column as a string.");
        }
      }
    } else
      recordMissingColumnError(colIdx, str);
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
    addError(err);
    newLine();
  }

  @Override
  public final void addError(ParseErr err) {
    if(_errs == null)
      _errs = new ParseErr[]{err};
    else if(_errs.length < MAX_ERR_CNT)
      _errs = ArrayUtils.append(_errs,err);
    _errCnt++;
  }

  private void addMissingColumnsError() {
    assert _maxMissingCol >= _nCols;
    String message = "Invalid line, found more columns than expected (found: " + (_maxMissingCol + 1) + ", expected: " + _nCols + ")" +
            ((_missingColVals == null) ? "" : "; values = {" + toStringTrimmedCleaned(_missingColVals, 50) + "}");
    addError(new ParseErr(message, _cidx, lineNum()));
  }

  private static String toStringTrimmedCleaned(StringBuilder sb, int maxLen) {
    String msg = sb.length() <= maxLen ? sb.toString() : sb.substring(0, maxLen) + "...(truncated)";
    return msg.replaceAll("[^\\x00-\\x7F]", ""); // Replace non-ASCII characters to avoid problems with displaying of the message in client
  }

  private void recordMissingColumnError(int colIdx, Object value) {
    // We only want to report values if they come sequentially as they were in the file (CSV parser is sequential, binary parsers might not be)
    // Cap the maximum number of reported values to 10
    if ((_maxMissingCol == -1) && (colIdx == _col + 1))
      _missingColVals = new StringBuilder().append(value);
    else
      if ((_missingColVals != null) && (_maxMissingCol + 1 == colIdx))
        _missingColVals = (colIdx - _nCols < 10) ? _missingColVals.append(", ").append(value) : _missingColVals;
      else
        _missingColVals = null; // out-of-order detected, cancel reporting
    _maxMissingCol = Math.max(colIdx, _maxMissingCol);
  }

  @Override public void setIsAllASCII(int colIdx, boolean b) {
    if(colIdx < _nvs.length)
      _nvs[colIdx]._isAllASCII = b;
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

}
