package water.parser;

import org.mortbay.log.Log;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.UploadFileVec;
import water.fvec.Vec;
import water.fvec.FileVec;
import water.fvec.ByteVec;
import water.util.IcedArrayList;
import water.parser.Parser.ColTypeInfo;

import java.util.Arrays;
import java.util.HashSet;

/**
* Configuration and base guesser for a parse;
*/
public final class ParseSetup extends Iced {
  static final byte AUTO_SEP = -1;
  Key[] _srcs;                      // Source Keys being parsed
  int _checkHeader;                 // 1st row: 0: guess, +1 header, -1 data
  // Whether or not single-quotes quote a field.  E.g. how do we parse:
  // raw data:  123,'Mally,456,O'Mally
  // singleQuotes==True  ==> 2 columns: 123  and  Mally,456,OMally
  // singleQuotes==False ==> 4 columns: 123  and  'Mally  and  456  and  O'Mally
  boolean _singleQuotes;

  String _hexName;            // Cleaned up result Key suggested name
  ParserType _pType;          // CSV, XLS, XSLX, SVMLight, Auto, ARFF
  byte _sep;                  // Field separator, usually comma ',' or TAB or space ' '
  int _ncols;                 // Columns to parse
  String[] _columnNames;
  String[][] _domains;        // Domains for each column (null if numeric)
  ColTypeInfo[] _ctypes;      // Column types
  String[][] _data;           // First few rows of parsed/tokenized data
  boolean _isValid;           // The initial parse is sane
  String[] _errors;           // Errors in this parse setup
  long _invalidLines; // Number of broken/invalid lines found
  long _headerlines; // Number of header lines found
  int _chunkSize = FileVec.DFLT_CHUNK_SIZE;  // Optimal chunk size to be used store values

  public ParseSetup(boolean isValid, long invalidLines, long headerlines, String[] errors, ParserType t, byte sep, int ncols, boolean singleQuotes, String[] columnNames, String[][] domains, String[][] data, int checkHeader, ColTypeInfo[] ctypes, int chunkSize) {
    _isValid = isValid;
    _invalidLines = invalidLines;
    _headerlines = headerlines;
    _errors = errors;
    _pType = t;
    _sep = sep;
    _ncols = ncols;
    _singleQuotes = singleQuotes;
    _columnNames = columnNames;
    _domains = domains;
    _data = data;
    _checkHeader = checkHeader;
    _ctypes = ctypes;
    _chunkSize = chunkSize;
  }

  /**
   *  Typically used by file type parsers for returning final valid results
   * _chunkSize will be set later using results from all files.
  * */
  public ParseSetup(boolean isValid, long invalidLines, long headerlines, String[] errors, ParserType t, byte sep, int ncols, boolean singleQuotes, String[] columnNames, String[][] domains, String[][] data, int checkHeader, ColTypeInfo[] ctypes) {
    this(isValid, invalidLines, headerlines, errors, t, sep, ncols, singleQuotes, columnNames, domains, data, checkHeader, ctypes, FileVec.DFLT_CHUNK_SIZE);
  }

  /**
   * Typically used by file type parsers for returning final invalid results
  */
  public ParseSetup(boolean isValid, long invalidLines, long headerlines, String[] errors, ParserType t, byte sep, int ncols, boolean singleQuotes, String[][] data, int checkHeader) {
    this(isValid, invalidLines, headerlines, errors, t, sep, ncols, singleQuotes, null, null, data, checkHeader, null, FileVec.DFLT_CHUNK_SIZE);
  }

  /**
   * Creates starting parse setup with auto detect turned on.
   * @param singleQuotes
   * @param checkHeader
   */
  public ParseSetup(boolean singleQuotes, int checkHeader) {
    this(false, 0, 0, null, ParserType.AUTO, AUTO_SEP, -1, singleQuotes, null, null, null, checkHeader, null, FileVec.DFLT_CHUNK_SIZE);
  }

  /**
   * Invalid setup based on a prior valid one
   * @param ps Setup to be set to invalid
   * @param err Error message explain why the setup is invalid
   */
  ParseSetup(ParseSetup ps, String err) {
    this(false, ps._invalidLines, ps._headerlines, new String[]{err}, ps._pType, ps._sep, ps._ncols, ps._singleQuotes, ps._columnNames, ps._domains, ps._data, ps._checkHeader, null, ps._chunkSize);
  }

  /**
   *  Called from Nano request server with a set of Keys, produce a suitable parser setup guess.
   */
  public ParseSetup() {
  }

  final boolean hasHeaders() { return _columnNames != null; }
  final long headerLines() { return _headerlines; }

  public Parser parser() {
    switch( _pType ) {
      case CSV:      return new      CsvParser(this);
      case XLS:      return new      XlsParser(this);
      case SVMLight: return new SVMLightParser(this);
      case ARFF:     return new     ARFFParser(this);
    }
    throw H2O.fail();
  }

  // Set of duplicated column names
  HashSet<String> checkDupColumnNames() {
    HashSet<String> conflictingNames = new HashSet<>();
    if( _columnNames==null ) return conflictingNames;
    HashSet<String> uniqueNames = new HashSet<>();
    for( String n : _columnNames )
      (uniqueNames.contains(n) ? conflictingNames : uniqueNames).add(n);
    return conflictingNames;
  }

  @Override public String toString() {
    if (_errors != null) {
      StringBuilder sb = new StringBuilder();
      for (String e : _errors) sb.append(e).append("\n");
      return sb.toString();
    }
    return _pType.toString( _ncols, _sep );
  }

  static boolean allStrings(String [] line){
    ValueString str = new ValueString();
    for( String s : line ) {
      try {
        Double.parseDouble(s);
        return false;       // Number in 1st row guesses: No Column Header
      } catch (NumberFormatException e) { /*Pass - determining if number is possible*/ }
      if( ParseTime.attemptTimeParse(str.setTo(s)) != Long.MIN_VALUE ) return false;
      ParseTime.attemptUUIDParse0(str.setTo(s));
      ParseTime.attemptUUIDParse1(str);
      if( str.get_off() != -1 ) return false; // Valid UUID parse
    }
    return true;
  }
  // simple heuristic to determine if we have headers:
  // return true iff the first line is all strings and second line has at least one number
  static boolean hasHeader(String[] l1, String[] l2) {
    return allStrings(l1) && !allStrings(l2);
  }

  /**
   * Used by test harnesses for simple parsing of test data.  Presumes
   * auto-detection for file and separator types.
   *
   * @param fkeys Keys to input vectors to be parsed
   * @param singleQuote
   * @param checkHeader
   * @return
   */
  public static ParseSetup guessSetup(Key[] fkeys, boolean singleQuote, int checkHeader) {
    return guessSetup(fkeys, new ParseSetup(true, 0, 0, null, ParserType.AUTO, AUTO_SEP, 0, singleQuote, null, checkHeader));
  }

  /**
   * Discover the parse setup needed to correctly parse all files.
   * This takes a ParseSetup as guidance.  Each file is examined
   * individually and then results merged.  If a conflict exists
   * between any results all files are re-examined using the
   * best guess from the first examination.
   *
   * @param fkeys Keys to input vectors to be parsed
   * @param userSetup Setup guidance from user
   * @return ParseSetup settings from looking at all files
   */
  public static ParseSetup guessSetup( Key[] fkeys, ParseSetup userSetup ) {
    //Guess setup of each file and collect results
    GuessSetupTsk t = new GuessSetupTsk(userSetup);
    t.doAll(fkeys).getResult();

    //check results
 /*   if (t._gblSetup._isValid && (!t._failedSetup.isEmpty() || !t._conflicts.isEmpty())) {
      // run guess setup once more, this time knowing the global setup to get rid of conflicts (turns them into failures) and bogus failures (i.e. single line files with unexpected separator)
      GuessSetupTsk t2 = new GuessSetupTsk(t._gblSetup);
      HashSet<Key> keySet = new HashSet<Key>(t._conflicts);
      keySet.addAll(t._failedSetup);
      Key[] keys2 = new Key[keySet.size()];
      t2.doAll(keySet.toArray(keys2));
      t._failedSetup = t2._failedSetup;
      t._conflicts = t2._conflicts;
        if(!gSetup._setup._header && t2._gblSetup._setup._header){
          gSetup._setup._header = true;
          gSetup._setup._columnNames = t2._gblSetup._setup._columnNames;
          t._gblSetup._hdrFromFile = t2._gblSetup._hdrFromFile;
        }*/

      //Calc chunk-size
      Iced ice = DKV.getGet(fkeys[0]);
      if (ice instanceof Frame && ((Frame) ice).vec(0) instanceof UploadFileVec) {
        t._gblSetup._chunkSize = FileVec.DFLT_CHUNK_SIZE;
      } else {
        t._gblSetup._chunkSize = FileVec.calcOptimalChunkSize(t._totalParseSize, t._gblSetup._ncols);
      }
//      assert t._conflicts.isEmpty(); // we should not have any conflicts here, either we failed to find any valid global setup, or conflicts should've been converted into failures in the second pass
//      if (!t._failedSetup.isEmpty()) {
//        // TODO throw and exception ("Can not parse: Got incompatible files.", gSetup, t._failedSetup.keys);
//      }
//    }
//    if (t._gblSetup == null || !t._gblSetup._isValid) {
//      //TODO throw an exception
//    }
    return t._gblSetup;
  }

  /**
   * Try to determine the ParseSetup on a file by file basis
   * and merge results.
   */
  public static class GuessSetupTsk extends MRTask<GuessSetupTsk> {
    // Input
    final ParseSetup _userSetup;
    boolean _empty = true;

    // Output
    public ParseSetup _gblSetup;
    //IcedArrayList<Key> _failedSetup;
    //IcedArrayList<Key> _conflicts;
    public long _totalParseSize;

    /**
     *
     * @param userSetup ParseSetup to guide examination of files
     */
    public GuessSetupTsk(ParseSetup userSetup) { _userSetup = userSetup; }

    public static final int MAX_ERRORS = 64;

    /**
     * Runs once on each file to guess that file's ParseSetup
     */
    @Override public void map(Key key) {
      Iced ice = DKV.getGet(key);
      if(ice == null) throw new H2OIllegalArgumentException("Missing data","Did not find any data under key " + key);
      ByteVec bv = (ByteVec)(ice instanceof ByteVec ? ice : ((Frame)ice).vecs()[0]);
      byte [] bits = ZipUtil.getFirstUnzippedBytes(bv);

      // guess setup
      if(bits.length > 0) {
        _empty = false;
//        _failedSetup = new IcedArrayList<Key>();
//        _conflicts = new IcedArrayList<Key>();
        _gblSetup = guessSetup(bits, _userSetup);
//        if (_gblSetup == null || !_gblSetup._isValid)
//          _failedSetup.add(key);
//        else {
          //  _gblSetup._setupFromFile = key;
          //  if (_checkHeader && _gblSetup._setup._header)
          //    _gblSetup._hdrFromFile = key;

          // get file size
          float decompRatio = ZipUtil.decompressionRatio(bv);
          if (decompRatio > 1.0)
            _totalParseSize += bv.length() * decompRatio; // estimate file size
          else  // avoid numerical distortion of file size when not compressed
            _totalParseSize += bv.length();
//        }
      }

      // guesser chunk uses default
      if (bv instanceof FileVec)
        ((FileVec) bv).clearCachedChunk(0);
    }

    /**
     * Merges ParseSetup results, conflicts, and errors from several files
     */
    @Override
    public void reduce(GuessSetupTsk other) {
      if (other._empty) return;
      if (_gblSetup == null || !_gblSetup._isValid) {
        _empty = false;
        _gblSetup = other._gblSetup;
        if (_gblSetup == null)
          System.out.println("haha");
/*        try {
          _gblSetup._hdrFromFile = other._gblSetup._hdrFromFile;
          _gblSetup._setupFromFile = other._gblSetup._setupFromFile;
//        }
        } catch (Throwable t) {
          t.printStackTrace();
        }*/
      }

      //unify parse types to ARFF if present
      if(_gblSetup._pType == ParserType.ARFF && other._gblSetup._pType == ParserType.CSV)
        other._gblSetup._pType = ParserType.ARFF;
      if(_gblSetup._pType == ParserType.CSV && other._gblSetup._pType == ParserType.ARFF)
        _gblSetup._pType = ParserType.ARFF;

      if (other._gblSetup._isValid && !_gblSetup.isCompatible(other._gblSetup)) {
        //   if (_conflicts.contains(_gblSetup._setupFromFile) && !other._conflicts.contains(other._gblSetup._setupFromFile)) {
        //     _gblSetup = other._gblSetup; // setups are not compatible, select random setup to send up (thus, the most common setup should make it to the top)
        //     _gblSetup._setupFromFile = other._gblSetup._setupFromFile;
        //     _gblSetup._hdrFromFile = other._gblSetup._hdrFromFile;
        //   } else if (!other._conflicts.contains(other._gblSetup._setupFromFile)) {
        //     _conflicts.add(_gblSetup._setupFromFile);
        //    _conflicts.add(other._gblSetup._setupFromFile);
        //  }
      } else if (other._gblSetup._isValid) { // merge the two setups
/*        if (!_gblSetup._setup._header && other._gblSetup._setup._header) {
          _gblSetup._setup._header = true;
          _gblSetup._hdrFromFile = other._gblSetup._hdrFromFile;*/

        // unify column names
        if (other._gblSetup._columnNames != null) {
          if (_gblSetup._columnNames == null) {
            _gblSetup._columnNames = other._gblSetup._columnNames;
          } else {
            for (int i=0; i < _gblSetup._columnNames.length; i++) {
              if (_gblSetup._columnNames[i].equals(other._gblSetup._columnNames[i]))
                //TODO throw something more serious
                Log.warn("Column names do not match between files");
            }
          }
        }

        if (_gblSetup._data.length < Parser.InspectDataOut.MAX_PREVIEW_LINES) {
          int n = _gblSetup._data.length;
          int m = Math.min(Parser.InspectDataOut.MAX_PREVIEW_LINES, n + other._gblSetup._data.length - 1);
          _gblSetup._data = Arrays.copyOf(_gblSetup._data, m);
          for (int i = n; i < m; ++i) {
            _gblSetup._data[i] = other._gblSetup._data[i - n + 1];
          }
        }
        _totalParseSize += other._totalParseSize;
      }
      // merge failures
/*      if (_failedSetup == null) {
        _failedSetup = other._failedSetup;
        _conflicts = other._conflicts;
      } else {
        _failedSetup.addAll(other._failedSetup);
        _conflicts.addAll(other._conflicts);
      } */
    }
  }

  /**
   * Guess everything from a single pile-o-bits.  Used in tests, or in initial
   * parser inspections when the user has not told us anything about separators
   * or headers.
   *
   * @param bits Initial bytes from a parse source
   * @param singleQuotes
   * @param checkHeader
   * @return
   */
  public static ParseSetup guessSetup( byte[] bits, boolean singleQuotes, int checkHeader ) {
    return guessSetup(bits, ParserType.AUTO, AUTO_SEP, -1, singleQuotes, checkHeader, null, null);
  }
  public static ParseSetup guessSetup( byte[] bits, ParseSetup userSetup ) {
    return guessSetup(bits, ParserType.AUTO, AUTO_SEP, -1, userSetup._singleQuotes, userSetup._checkHeader, null, null);
  }

  private static final ParserType guessTypeOrder[] = {ParserType.ARFF, ParserType.XLS,ParserType.XLSX,ParserType.SVMLight,ParserType.CSV};
  public static ParseSetup guessSetup( byte[] bits, ParserType pType, byte sep, int ncols, boolean singleQuotes, int checkHeader, String[] columnNames, String[][] domains ) {
    switch( pType ) {
      case CSV:      return      CsvParser.CSVguessSetup(bits,sep,ncols,singleQuotes,checkHeader,columnNames);
      case SVMLight: return SVMLightParser.   guessSetup(bits);
      case XLS:      return      XlsParser.   guessSetup(bits);
      case ARFF:     return      ARFFParser.  guessSetup(bits, sep, ncols, singleQuotes, checkHeader, columnNames);
      case AUTO:
        for( ParserType pType2 : guessTypeOrder ) {
          try {
            ParseSetup ps = guessSetup(bits,pType2,sep,ncols,singleQuotes,checkHeader,columnNames,domains);
            if( ps != null && ps._isValid ) return ps;
          } catch( Throwable ignore ) { /*ignore failed parse attempt*/ }
        }
    }
    return new ParseSetup( false, 0, 0, new String[]{"Cannot determine file type"}, pType, sep, ncols, singleQuotes, columnNames, domains, null, checkHeader, null, FileVec.DFLT_CHUNK_SIZE);
  }

  // Guess a local setup that is compatible to the given global (this) setup.
  // If they are not compatible, there will be _errors set.
  ParseSetup guessSetup( byte[] bits, int checkHeader ) {
    assert _isValid;
    ParseSetup ps = guessSetup(bits, _singleQuotes, checkHeader);
    if( !ps._isValid ) return ps; // Already invalid

    // ARFF wins over CSV (Note: ARFF might not know separator or ncols yet)
    if ((_pType == ParserType.CSV || _pType == ParserType.AUTO) && ps._pType == ParserType.ARFF) {
      if (ps._sep == ParseSetup.AUTO_SEP && _sep != ParseSetup.AUTO_SEP) ps._sep = _sep; //use existing separator
      return ps;
    }
    if (_pType == ParserType.ARFF && (ps._pType == ParserType.CSV || _pType == ParserType.AUTO)) {
      if (ps._sep != ParseSetup.AUTO_SEP && _sep == ParseSetup.AUTO_SEP) _sep = ps._sep; //use existing separator
      return this;
    }

    if( _pType != ps._pType || ( (_pType == ParserType.CSV && (_sep != ps._sep || _ncols != ps._ncols)) || (_pType == ParserType.ARFF && (_sep != ps._sep || _ncols != ps._ncols)) ) )
      return new ParseSetup(ps,"Conflicting file layouts, expecting: "+this+" but found "+ps+"\n");
    return ps;
  }

  public boolean isCompatible(ParseSetup other){
    if(other == null || _pType != other._pType)return false;
    if(_pType == ParserType.CSV && (_sep != other._sep || _ncols != other._ncols))
      return false;
    if(_ctypes == null) _ctypes = other._ctypes;
    else if(other._ctypes != null){
      for(int i = 0; i < _ctypes.length; ++i)
        _ctypes[i].merge(other._ctypes[i]);
    }
    return true;
  }

  public static String hex( String n ) {
    // blahblahblah/myName.ext ==> myName
    // blahblahblah/myName.csv.ext ==> myName
    int sep = n.lastIndexOf(java.io.File.separatorChar);
    if( sep > 0 ) n = n.substring(sep+1);
    int dot = n.lastIndexOf('.');
    if( dot > 0 ) n = n.substring(0, dot);
    int dot2 = n.lastIndexOf('.');
    if( dot2 > 0 ) n = n.substring(0, dot2);
    // "2012_somedata" ==> "X2012_somedata"
    if( !Character.isJavaIdentifierStart(n.charAt(0)) ) n = "X"+n;
    // "human%Percent" ==> "human_Percent"
    char[] cs = n.toCharArray();
    for( int i=1; i<cs.length; i++ )
      if( !Character.isJavaIdentifierPart(cs[i]) )
        cs[i] = '_';
    // "myName" ==> "myName.hex"
    n = new String(cs);
    int i = 0;
    String res = n + ".hex";
    Key k = Key.make(res);
    // Renumber to handle dup names
    while(DKV.get(k) != null)
      k = Key.make(res = n + ++i + ".hex");
    return res;
  }
} // ParseSetup state class
