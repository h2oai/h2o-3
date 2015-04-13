package water.parser;

import water.*;
import water.api.ParseSetupV2;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OInternalParseException;
import water.exceptions.H2OParseException;
import water.exceptions.H2OParseSetupException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.fvec.UploadFileVec;
import water.fvec.FileVec;
import water.fvec.ByteVec;
import water.util.ArrayUtils;
import water.util.Log;

import java.util.Arrays;
import java.util.HashSet;

/**
* Configuration and base guesser for a parse;
*/
public final class ParseSetup extends Iced {
  public static final byte GUESS_SEP = -1;
  public static final int NO_HEADER = -1;
  public static final int GUESS_HEADER = 0;
  public static final int HAS_HEADER = 1;
  public static final int GUESS_COL_CNT = -1;
  boolean _is_valid;          // The initial parse is sane
  long _invalid_lines;        // Number of broken/invalid lines found
  String[] _errors;           // Errors in this parse setup
  ParserType _parse_type;     // CSV, XLS, XSLX, SVMLight, Auto, ARFF
  byte _separator;            // Field separator, usually comma ',' or TAB or space ' '
  // Whether or not single-quotes quote a field.  E.g. how do we parse:
  // raw data:  123,'Mally,456,O'Mally
  // singleQuotes==True  ==> 2 columns: 123  and  Mally,456,OMally
  // singleQuotes==False ==> 4 columns: 123  and  'Mally  and  456  and  O'Mally
  boolean _single_quotes;
  int _check_header;                 // 1st row: 0: guess, +1 header, -1 data
  int _number_columns;                 // Columns to parse
  String[] _column_names;
  byte[] _column_types;       // Column types
  String[][] _domains;        // Domains for each column (null if numeric)
  String[] _na_strings;       // Strings for NA in a given column
  String[][] _data;           // First few rows of parsed/tokenized data
  int _chunk_size = FileVec.DFLT_CHUNK_SIZE;  // Optimal chunk size to be used store values

  public ParseSetup(ParseSetup ps) {
    this(ps._is_valid, ps._invalid_lines, ps._errors, ps._parse_type,
            ps._separator, ps._single_quotes, ps._check_header, ps._number_columns,
            ps._column_names, ps._column_types, ps._domains, ps._na_strings, ps._data, ps._chunk_size);
  }

  public ParseSetup(boolean isValid, long invalidLines, String[] errors, ParserType t, byte sep, boolean singleQuotes, int checkHeader, int ncols, String[] columnNames, byte[] ctypes, String[][] domains, String[] naStrings, String[][] data, int chunkSize) {
    _is_valid = isValid;
    _invalid_lines = invalidLines;
    _errors = errors;
    _parse_type = t;
    _separator = sep;
    _single_quotes = singleQuotes;
    _check_header = checkHeader;
    _number_columns = ncols;
    _column_names = columnNames;
    _column_types = ctypes;
    _domains = domains;
    _na_strings = naStrings;
    _data = data;
    _chunk_size = chunkSize;
  }

  /**
   * Create a ParseSetup with parameters from the client.
   *
   * Typically used to guide sampling in the data
   * to verify chosen settings, and fill in missing settings.
   *
   * @param ps Parse setup settings from client
   */
  public ParseSetup(ParseSetupV2 ps) {
    this(false, 0, null, ps.parse_type, ps.separator, ps.single_quotes,
            ps.check_header, GUESS_COL_CNT, ps.column_names, strToColumnTypes(ps.column_types),
            null, ps.na_strings, null, ps.chunk_size);
    if(ps.parse_type == null) _parse_type = ParserType.AUTO;
    if(ps.separator == 0) _separator = GUESS_SEP;
  }

  /**
   * Create a ParseSetup with all parameters except chunk size.
   *
   * Typically used by file type parsers for returning final valid results
   * _chunk_size will be set later using results from all files.
   */
  public ParseSetup(boolean isValid, long invalidLines, String[] errors, ParserType t, byte sep, boolean singleQuotes, int checkHeader, int ncols, String[] columnNames, byte[] ctypes, String[][] domains, String[] naStrings, String[][] data) {
    this(isValid, invalidLines, errors, t, sep, singleQuotes, checkHeader, ncols, columnNames, ctypes, domains, naStrings, data, FileVec.DFLT_CHUNK_SIZE);
  }

  /**
   * Create a ParseSetup without any column information
   *
   * Typically used by file type parsers for returning final invalid results
   */
  public ParseSetup(boolean isValid, long invalidLines, String[] errors, ParserType t, byte sep, boolean singleQuotes, int checkHeader, int ncols, String[][] data) {
    this(isValid, invalidLines, errors, t, sep, singleQuotes, checkHeader, ncols, null, null, null, null, data, FileVec.DFLT_CHUNK_SIZE);
  }

  /**
   * Create a default ParseSetup
   *
   * Used by Ray's schema magic
   */
  public ParseSetup() {}

  public String[] getColumnTypeStrings() {
    String[] types = new String[_column_types.length];
    for(int i=0; i< types.length; i++)
      types[i] = Vec.TYPE_STR[_column_types[i]];
    return types;
  }

  public static byte[] strToColumnTypes(String[] strs) {
    if (strs == null) return null;
    byte[] types = new byte[strs.length];
    for(int i=0; i< types.length;i++) {
      switch (strs[i].toLowerCase()) {
        case "unknown": types[i] = Vec.T_BAD; break;
        case "uuid": types[i] = Vec.T_UUID; break;
        case "string": types[i] = Vec.T_STR; break;
        case "numeric": types[i] = Vec.T_NUM; break;
        case "enum": types[i] = Vec.T_ENUM; break;
        case "time": types[i] = Vec.T_TIME; break;
        default: types[i] = Vec.T_BAD;
          // TODO throw an exception
          Log.err("Column type "+ strs[i] + " is unknown.");
      }
    }
    return types;
  }

  public Parser parser() {
    switch(_parse_type) {
      case CSV:      return new      CsvParser(this);
      case XLS:      return new      XlsParser(this);
      case SVMLight: return new SVMLightParser(this);
      case ARFF:     return new     ARFFParser(this);
    }
    throw new H2OInternalParseException("Unknown file type.  Parse cannot be completed.",
            "Attempted to invoke a parser for ParseType:" + _parse_type +", which doesn't exist.");
  }

  // Set of duplicated column names
  HashSet<String> checkDupColumnNames() {
    HashSet<String> conflictingNames = new HashSet<>();
    if( _column_names ==null ) return conflictingNames;
    HashSet<String> uniqueNames = new HashSet<>();
    for( String n : _column_names)
      (uniqueNames.contains(n) ? conflictingNames : uniqueNames).add(n);
    return conflictingNames;
  }

  @Override public String toString() {
    if (_errors != null) {
      StringBuilder sb = new StringBuilder();
      for (String e : _errors) sb.append(e).append("\n");
      return sb.toString();
    }
    return _parse_type.toString(_number_columns, _separator);
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
   * @param singleQuote single quotes quote fields
   * @param checkHeader check for a header
   * @return ParseSetup settings from looking at all files
   */
  public static ParseSetup guessSetup(Key[] fkeys, boolean singleQuote, int checkHeader) {
    return guessSetup(fkeys, new ParseSetup(false, 0, null, ParserType.AUTO, GUESS_SEP, singleQuote, checkHeader, GUESS_COL_CNT, null));
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
 /*   if (t._gblSetup._is_valid && (!t._failedSetup.isEmpty() || !t._conflicts.isEmpty())) {
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
          gSetup._setup._column_names = t2._gblSetup._setup._column_names;
          t._gblSetup._hdrFromFile = t2._gblSetup._hdrFromFile;
        }*/

      //Calc chunk-size
      Iced ice = DKV.getGet(fkeys[0]);
      if (ice instanceof Frame && ((Frame) ice).vec(0) instanceof UploadFileVec) {
        t._gblSetup._chunk_size = FileVec.DFLT_CHUNK_SIZE;
      } else {
        t._gblSetup._chunk_size = FileVec.calcOptimalChunkSize(t._totalParseSize, t._gblSetup._number_columns);
      }
//      assert t._conflicts.isEmpty(); // we should not have any conflicts here, either we failed to find any valid global setup, or conflicts should've been converted into failures in the second pass
//      if (!t._failedSetup.isEmpty()) {
//        // TODO throw and exception ("Can not parse: Got incompatible files.", gSetup, t._failedSetup.keys);
//      }
//    }
//    if (t._gblSetup == null || !t._gblSetup._is_valid) {
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
        try {
          _gblSetup = guessSetup(bits, _userSetup);
        } catch (H2OParseException pse) {
          throw new H2OParseSetupException(key, pse);
        }
//        if (_gblSetup == null || !_gblSetup._is_valid)
//          _failedSetup.add(key);
//        else {
          //  _gblSetup._setupFromFile = key;
          //  if (_check_header && _gblSetup._setup._header)
          //    _gblSetup._hdrFromFile = key;

          // get file size
          float decompRatio = ZipUtil.decompressionRatio(bv);
          if (decompRatio > 1.0)
            _totalParseSize += bv.length() * decompRatio; // estimate file size
          else  // avoid numerical distortion of file size when not compressed
            _totalParseSize += bv.length();

        // report if multiple files exist in zip archive
        if (ZipUtil.getFileCount(bv) > 1) {
          if (_gblSetup._errors != null)
            _gblSetup._errors = Arrays.copyOf(_gblSetup._errors, _gblSetup._errors.length + 1);
          else
            _gblSetup._errors = new String[1];

          _gblSetup._errors[_gblSetup._errors.length - 1] = "Only single file zip " +
                  "archives are currently supported, only the first file has been parsed.  " +
                  "Remaining files have been ignored.";
        }
      }
    }

    /**
     * Merges ParseSetup results, conflicts, and errors from several files
     */
    @Override
    public void reduce(GuessSetupTsk other) {
      if (other._empty) return;

      if (_gblSetup == null || !_gblSetup._is_valid) {
        _empty = false;
        _gblSetup = other._gblSetup;
        assert (_gblSetup != null);
/*        try {
          _gblSetup._hdrFromFile = other._gblSetup._hdrFromFile;
          _gblSetup._setupFromFile = other._gblSetup._setupFromFile;
//        }
        } catch (Throwable t) {
          t.printStackTrace();
        }*/
      }

      if (other._gblSetup._is_valid && !_gblSetup.isCompatible(other._gblSetup)) {
        //   if (_conflicts.contains(_gblSetup._setupFromFile) && !other._conflicts.contains(other._gblSetup._setupFromFile)) {
        //     _gblSetup = other._gblSetup; // setups are not compatible, select random setup to send up (thus, the most common setup should make it to the top)
        //     _gblSetup._setupFromFile = other._gblSetup._setupFromFile;
        //     _gblSetup._hdrFromFile = other._gblSetup._hdrFromFile;
        //   } else if (!other._conflicts.contains(other._gblSetup._setupFromFile)) {
        //     _conflicts.add(_gblSetup._setupFromFile);
        //    _conflicts.add(other._gblSetup._setupFromFile);
        //  }

        // unify column names
        if (other._gblSetup._column_names != null) {
          if (_gblSetup._column_names == null) {
            _gblSetup._column_names = other._gblSetup._column_names;
          } else {
            for (int i = 0; i < _gblSetup._column_names.length; i++) {
              if (i > other._gblSetup._column_names.length || !_gblSetup._column_names[i].equals(other._gblSetup._column_names[i])) {
                //TODO throw something more serious
                Log.warn("Column names do not match between files");
                _gblSetup._is_valid = false;
                other._gblSetup._is_valid = false;
                break;
              }
            }
          }
        }
      } else if (other._gblSetup._is_valid) { // merge the two setups
        //merge ARFF and CSV
        if (_gblSetup._parse_type == ParserType.CSV && other._gblSetup._parse_type == ParserType.ARFF) {
          _gblSetup._parse_type = ParserType.ARFF;
          _gblSetup._column_types = other._gblSetup._column_types;
        }

        // ARFF header files won't know the separator, pull from other file
        if (_gblSetup._separator == GUESS_SEP && other._gblSetup._separator != GUESS_SEP)
          _gblSetup._separator = other._gblSetup._separator;

        //merge header settings
        if (_gblSetup._check_header == NO_HEADER && other._gblSetup._check_header == HAS_HEADER)
          _gblSetup._check_header = HAS_HEADER;
        else if (_gblSetup._check_header == GUESS_HEADER) Log.err("Header guess failed.");

        // merge column names
        if (other._gblSetup._column_names != null) {
          if (_gblSetup._column_names == null) {
            _gblSetup._column_names = other._gblSetup._column_names;
          } else {
            for (int i = 0; i < _gblSetup._column_names.length; i++) {
              if (!_gblSetup._column_names[i].equals(other._gblSetup._column_names[i]))
                //TODO throw something more serious
                Log.warn("Column names do not match between files");
            }
          }

          //merge column types
          if (_gblSetup._column_types == null) _gblSetup._column_types = other._gblSetup._column_types;
          else if (other._gblSetup._column_types != null) {
            for (int i = 0; i < _gblSetup._column_types.length; ++i)
              if (_gblSetup._column_types[i] != other._gblSetup._column_types[i])
                if (_gblSetup._column_types[i] == Vec.T_BAD)
                  _gblSetup._column_types[i] = other._gblSetup._column_types[i];
          }
        }

        if (_gblSetup._data.length < Parser.InspectDataOut.MAX_PREVIEW_LINES) {
          int n = _gblSetup._data.length;
          int m = Math.min(Parser.InspectDataOut.MAX_PREVIEW_LINES, n + other._gblSetup._data.length - 1);
          _gblSetup._data = Arrays.copyOf(_gblSetup._data, m);
          System.arraycopy(other._gblSetup._data,1,_gblSetup._data,n,m-n);
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
   * @return ParseSetup settings from looking at all files
   */
  public static ParseSetup guessSetup( byte[] bits, ParseSetup userSetup ) {
    return guessSetup(bits, userSetup._parse_type, userSetup._separator, GUESS_COL_CNT, userSetup._single_quotes, userSetup._check_header, null, userSetup._column_types, null, null);
  }

  private static final ParserType guessFileTypeOrder[] = {ParserType.ARFF, ParserType.XLS,ParserType.XLSX,ParserType.SVMLight,ParserType.CSV};
  public static ParseSetup guessSetup( byte[] bits, ParserType pType, byte sep, int ncols, boolean singleQuotes, int checkHeader, String[] columnNames, byte[] columnTypes, String[][] domains, String[] naStrings ) {
    switch( pType ) {
      case CSV:      return      CsvParser.guessSetup(bits, sep, ncols, singleQuotes, checkHeader, columnNames, columnTypes, naStrings);
      case SVMLight: return SVMLightParser.guessSetup(bits);
      case XLS:      return      XlsParser.guessSetup(bits);
      case ARFF:     return      ARFFParser.guessSetup(bits, sep, singleQuotes, columnNames, naStrings);
      case AUTO:
        for( ParserType pTypeGuess : guessFileTypeOrder ) {
          try {
            ParseSetup ps = guessSetup(bits,pTypeGuess,sep,ncols,singleQuotes,checkHeader,columnNames,columnTypes, domains, naStrings);
            if( ps != null && ps._is_valid) return ps;
          } catch( Throwable ignore ) { /*ignore failed parse attempt*/ }
        }
    }
    return new ParseSetup( false, 0, new String[]{"Cannot determine file type"}, pType, sep, singleQuotes, checkHeader, ncols, columnNames, null, domains, naStrings, null, FileVec.DFLT_CHUNK_SIZE);
  }

  public boolean isCompatible(ParseSetup other){
    // incompatible file types
    if ((_parse_type != other._parse_type)
            && !(_parse_type == ParserType.ARFF && other._parse_type == ParserType.CSV )
              && !(_parse_type == ParserType.CSV && other._parse_type == ParserType.ARFF ))
      throw new H2OParseSetupException("Cannot parse files of type "+_parse_type+" and "+
              other._parse_type+" as one dataset","File type mismatch: "+_parse_type+", "+other._parse_type);

    //different separators or col counts
    if( _separator != other._separator && (_separator != GUESS_SEP || other._separator != GUESS_SEP))
      return false;
    // compatible column count
    return _number_columns == other._number_columns || other._number_columns == 0 || _number_columns == 0;
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
