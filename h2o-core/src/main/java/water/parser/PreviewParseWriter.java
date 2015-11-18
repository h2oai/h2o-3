package water.parser;

import water.Iced;
import water.exceptions.H2OParseSetupException;
import water.fvec.Vec;
import water.util.IcedHashMap;

import java.util.ArrayList;

/** Class implementing ParseWriter, on behalf ParseSetup
 * to examine the contents of a file for guess the column types.
 */
public class PreviewParseWriter extends Iced implements ParseWriter {
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
  transient ArrayList<String> _errors;

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
        _data[_nlines][colIdx] = Double.toString(number * water.util.PrettyPrint.pow10(exp));
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
  @Override public void invalidLine(String err) {
    ++_invalidLines;
    if( _errors == null ) _errors = new ArrayList<>();
    if( _errors.size() < 10 )
      _errors.add("Error at line: " + _nlines + ", reason: " + err);
  }
  @Override public void setIsAllASCII(int colIdx, boolean b) {}

  String[] errors() { return _errors == null ? null : _errors.toArray(new String[_errors.size()]); }

  public byte[] guessTypes() {
    byte[] types = new byte[_ncols];
    for (int i = 0; i < _ncols; ++i) {
      int nonemptyLines = _nlines - _nempty[i] - 1; //During guess, some columns may be shorted one line based on 4M boundary

      //Very redundant tests, but clearer and not speed critical

      // is it clearly numeric?
      if ((_nnums[i] + _nzeros[i]) >= _ndates[i]
              && (_nnums[i] + _nzeros[i]) >= _nUUID[i]
              && _nnums[i] >= _nstrings[i]) { // 0s can be an NA among categoricals, ignore
        types[i] = Vec.T_NUM;
        continue;
      }

      // All same string, but not obvious NA, declare categorical
      if (_domains[i].size() == 1
              && !_domains[i].containsKey("NA")
              && !_domains[i].containsKey("na")
              && !_domains[i].containsKey("Na")
              &&  _nstrings[i] >= nonemptyLines) {
        types[i] = Vec.T_CAT;
        continue;
      }

      // All same string (NA), so just guess numeric
      if (_domains[i].size() == 1
          && (_domains[i].containsKey("NA")
          || !_domains[i].containsKey("na")
          || !_domains[i].containsKey("Na")
          ||  _nstrings[i] >= nonemptyLines)) {
        types[i] = Vec.T_NUM;
        continue;
      }

      // with NA, but likely numeric
      if (_domains[i].size() <= 1
              && (_nnums[i] + _nzeros[i]) > _ndates[i] + _nUUID[i]) {
        types[i] = Vec.T_NUM;
        continue;
      }

      // Datetime
      if (_ndates[i] > _nUUID[i]
              && _ndates[i] > (_nnums[i] + _nzeros[i])
              && (_ndates[i] > _nstrings[i] || _domains[i].size() <= 1)) {
        types[i] = Vec.T_TIME;
        continue;
      }

      // UUID
      if (_nUUID[i] > _ndates[i]
              && _nUUID[i] > (_nnums[i] + _nzeros[i])
              && (_nUUID[i] > _nstrings[i] || _domains[i].size() <= 1)) {
        types[i] = Vec.T_UUID;
        continue;
      }

      // Strings, almost no dups
      if (_nstrings[i] > _ndates[i]
              && _nstrings[i] > _nUUID[i]
              && _nstrings[i] > (_nnums[i] + _nzeros[i])
              && _domains[i].size() >= 0.95 * _nstrings[i]) {
        types[i] = Vec.T_STR;
        continue;
      }

      // categorical or string?
      // categorical with 0s for NAs
      if(_nzeros[i] > 0
              && ((_nzeros[i] + _nstrings[i]) >= nonemptyLines) //just strings and zeros for NA (thus no empty lines)
              && (_domains[i].size() <= 0.95 * _nstrings[i]) ) { // not all unique strings
        types[i] = Vec.T_CAT;
        continue;
      }
      // categorical mixed with numbers
      if(_nstrings[i] >= (_nnums[i]+_nzeros[i]) // mostly strings
              && (_domains[i].size() <= 0.95 * _nstrings[i]) ) { // but not all unique
        types[i] = Vec.T_CAT;
        continue;
      }

      // All guesses failed
      types[i] = Vec.T_NUM;
    }
    return types;
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
        throw new H2OParseSetupException("Files conflict in number of columns. "
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
}
