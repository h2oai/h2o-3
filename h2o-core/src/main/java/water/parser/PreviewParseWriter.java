package water.parser;

import water.Futures;
import water.H2O;
import water.Iced;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.IcedHashMap;

/** Class implementing ParseWriter, on behalf ParseSetup
 * to examine the contents of a file for guess the column types.
 */
public class PreviewParseWriter extends Iced implements StreamParseWriter {
  protected final static int MAX_PREVIEW_COLS  = 100;
  protected final static int MAX_PREVIEW_LINES = 10;
  protected int _nlines;
  protected int _ncols;
  protected int _invalidLines;
  private   String []   _colNames;
  protected String [][] _data = new String[MAX_PREVIEW_LINES][];
  private IcedHashMap<String,String>[] _domains;  //used in leiu of a HashSet
  int [] _nnums;
  int [] _nstrings;
  int [] _ndates;
  int [] _nUUID;
  int [] _nzeros;
  int [] _nempty;

  protected ParseErr [] _errs  = new ParseErr[0];

  protected PreviewParseWriter() {}
  protected PreviewParseWriter(int ncols) { setColumnCount(ncols); }

  String[] colNames() { return _colNames; }

  @Override public void setColumnNames(String[] names) {
    _colNames = names;
    _data[0] = names;
    ++_nlines;
    setColumnCount(names.length);
  }
  private void setColumnCount(int n) {
    // initialize
    if (_ncols == 0 && n > 0) {
      _ncols = n;
      _nzeros = new int[n];
      _nstrings = new int[n];
      _nUUID = new int[n];
      _ndates = new int[n];
      _nnums = new int[n];
      _nempty = new int[n];
      _domains = new IcedHashMap[n];
      for(int i = 0; i < n; ++i)
        _domains[i] = new IcedHashMap<>();
      for(int i =0; i < MAX_PREVIEW_LINES; i++)
        _data[i] = new String[n];
    } /*else if (n > _ncols) { // resize
        _nzeros = Arrays.copyOf(_nzeros, n);
        _nstrings = Arrays.copyOf(_nstrings, n);
        _nUUID = Arrays.copyOf(_nUUID, n);
        _ndates = Arrays.copyOf(_ndates, n);
        _nnums = Arrays.copyOf(_nnums, n);
        _nempty = Arrays.copyOf(_nempty, n);
        _domains = Arrays.copyOf(_domains, n);
        for (int i=_ncols; i < n; i++)
          _domains[i] = new HashSet<String>();
        for(int i =0; i < MAX_PREVIEW_LINES; i++)
          _data[i] = Arrays.copyOf(_data[i], n);
        _ncols = n;
      }*/
  }
  @Override public void newLine() { ++_nlines; }
  @Override public boolean isString(int colIdx) { return false; }
  @Override public void addNumCol(int colIdx, long number, int exp) {
    if(colIdx < _ncols) {
      if (number == 0)
        ++_nzeros[colIdx];
      else
        ++_nnums[colIdx];
      if (_nlines < MAX_PREVIEW_LINES)
        _data[_nlines][colIdx] = Double.toString(water.util.PrettyPrint.pow10(number,exp));
    }
  }
  @Override public void addNumCol(int colIdx, double d) {
    if(colIdx < _ncols) {
      if (d == 0)
        ++_nzeros[colIdx];
      else
        ++_nnums[colIdx];
      if (_nlines < MAX_PREVIEW_LINES)
        _data[_nlines][colIdx] = Double.toString(d);
    }
  }
  @Override public void addInvalidCol(int colIdx) {
    if(colIdx < _ncols) {
      ++_nempty[colIdx];
      if (_nlines < MAX_PREVIEW_LINES)
        _data[_nlines][colIdx] = "NA";
    }
  }

  @Override
  public void addNAs(int colIdx, int nrow) {
    throw H2O.unimpl();
  }

  @Override public void addStrCol(int colIdx, BufferedString str) {
    if(colIdx < _ncols) {
      // Check for time
      if (ParseTime.isTime(str)) {
        ++_ndates[colIdx];
        return;
      }

      //Check for UUID
      if(ParseUUID.isUUID(str)) {
        ++_nUUID[colIdx];
        return;
      }

      //Add string to domains list for later determining string, NA, or categorical
      ++_nstrings[colIdx];
      _domains[colIdx].put(str.toString(),"");

      if (_nlines < MAX_PREVIEW_LINES)
        _data[_nlines][colIdx] = str.toString();
    }
  }

  @Override public void rollbackLine() {--_nlines;}

  @Override public void setIsAllASCII(int colIdx, boolean b) {}

  public byte[] guessTypes() { 
    byte[] types = new byte[_ncols];
    for (int i = 0; i < _ncols; ++i) {
      IcedHashMap<String, String> sourceDomain = _domains[i];
      IDomain domain = new IDomain() {
        public int size() { return sourceDomain.size(); }
        public boolean contains(String value) { return sourceDomain.containsKey(value); }
      };
      types[i] = PreviewParseWriter.guessType(
        _nlines,
        _nnums[i],
        _nstrings[i],
        _ndates[i],
        _nUUID[i],
        _nzeros[i],
        _nempty[i],
        domain);
    }
    return types;
  }
  
  public interface IDomain {
    int size();
    boolean contains(String value);
  }

  public static byte guessType(
      int nlines,
      int nnums,
      int nstrings,
      int ndates,
      int nUUID,
      int nzeros,
      int nempty,
      IDomain domain) {
    int nonemptyLines = nlines - nempty - 1; //During guess, some columns may be shorted one line based on 4M boundary
    
    //Very redundant tests, but clearer and not speed critical
    
    // is it clearly numeric?
    if ((nnums + nzeros) >= ndates
        && (nnums + nzeros) >= nUUID
        && nnums >= nstrings) { // 0s can be an NA among categoricals, ignore
          return Vec.T_NUM;
    }
    
    // All same string or empty?
    if (domain.size() == 1 && ndates==0 ) {
      // Obvious NA, or few instances of the single string, declare numeric
      // else categorical
      return (domain.contains("NA") ||
              domain.contains("na") ||
              domain.contains("Na") ||
              domain.contains("N/A") ||
              nstrings < nnums+nzeros) ? Vec.T_NUM : Vec.T_CAT;
    }
    
    // with NA, but likely numeric
    if (domain.size() <= 1 && (nnums + nzeros) > ndates + nUUID) {
      return Vec.T_NUM;
    }
    
    // Datetime
    if (ndates > nUUID && ndates > (nnums + nzeros) && (ndates > nstrings || domain.size() <= 1)) {
      return Vec.T_TIME;
    }
    
    // UUID
    if (nUUID > ndates && nUUID > (nnums + nzeros) && (nUUID > nstrings || domain.size() <= 1)) {
      return Vec.T_UUID;
    }
    
    // Strings, almost no dups
    if (nstrings > ndates
        && nstrings > nUUID
        && nstrings > (nnums + nzeros)
        && domain.size() >= 0.95 * nstrings) {
      return Vec.T_STR;
    }
    
    // categorical or string?
    // categorical with 0s for NAs
    if (nzeros > 0
        && ((nzeros + nstrings) >= nonemptyLines) //just strings and zeros for NA (thus no empty lines)
        && (domain.size() <= 0.95 * nstrings)) { // not all unique strings
      return Vec.T_CAT;
    }
    // categorical mixed with numbers
    if (nstrings >= (nnums+nzeros) // mostly strings
        && (domain.size() <= 0.95 * nstrings) ) { // but not all unique
      return Vec.T_CAT;
    }
    
    // All guesses failed
    return Vec.T_NUM;
  }

  public String[][] guessNAStrings(byte[] types) {
    //For now just catch 0's as NA in categoricals
    String[][] naStrings = new String[_ncols][];
    boolean empty = true;
    for (int i = 0; i < _ncols; ++i) {
      int nonemptyLines = _nlines - _nempty[i] - 1; //During guess, some columns may be shorted one line (based on 4M boundary)
      if (types[i] == Vec.T_CAT
              && _nzeros[i] > 0
              && ((_nzeros[i] + _nstrings[i]) >= nonemptyLines) //just strings and zeros for NA (thus no empty lines)
              && (_domains[i].size() <= 0.95 * _nstrings[i])) { // not all unique strings
        naStrings[i] = new String[1];
        naStrings[i][0] = "0";
        empty = false;
      }
    }
    if (empty) return null;
    else return naStrings;
  }

  public static PreviewParseWriter unifyColumnPreviews(PreviewParseWriter prevA, PreviewParseWriter prevB) {
    if (prevA == null) return prevB;
    else if (prevB == null) return prevA;
    else {
      //sanity checks
      if (prevA._ncols != prevB._ncols)
        throw new ParseDataset.H2OParseException("Files conflict in number of columns. "
                + prevA._ncols + " vs. " + prevB._ncols + ".");
      prevA._nlines += prevB._nlines;
      prevA._invalidLines += prevB._invalidLines;
      for (int i = 0; i < prevA._ncols; i++) {
        prevA._nnums[i] += prevB._nnums[i];
        prevA._nstrings[i] += prevB._nstrings[i];
        prevA._ndates[i] += prevB._ndates[i];
        prevA._nUUID[i] += prevB._nUUID[i];
        prevA._nzeros[i] += prevB._nzeros[i];
        prevA._nempty[i] += prevB._nempty[i];
        if (prevA._domains[i] != null) {
          if (prevB._domains[i] != null)
            for(String s:prevB._domains[i].keySet())
              prevA._domains[i].put(s,"");
        } else if (prevB._domains[i] != null)
          prevA._domains = prevB._domains;
      }
    }
    return prevA;
  }
  @Override
  public void invalidLine(ParseErr err) {
    addError(err);
    ++_invalidLines;
  }

  @Override
  public void addError(ParseErr err) {
    if(_errs == null) _errs = new ParseErr[]{err};
    else if(_errs.length < 20)
      _errs = ArrayUtils.append(_errs,err);
  }

  @Override
  public boolean hasErrors() {return _errs != null && _errs.length > 0;}

  @Override
  public ParseErr[] removeErrors() {return _errs;}

  @Override
  public long lineNum() {return _nlines;}

  @Override
  public StreamParseWriter nextChunk() {throw H2O.unimpl();}

  @Override
  public StreamParseWriter reduce(StreamParseWriter dout) {throw H2O.unimpl();}

  @Override
  public StreamParseWriter close() {return this;}

  @Override
  public StreamParseWriter close(Futures fs) {return this;}
}
