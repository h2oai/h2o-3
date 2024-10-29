package water.parser;

import water.*;
import water.api.schemas3.ParseSetupV3;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.*;
import water.util.ArrayUtils;
import water.util.FileUtils;
import water.util.Log;
import water.util.StringUtils;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;

import static water.parser.DefaultParserProviders.*;

/**
 * A generic configuration and base guesser for a parser.
 */
public class ParseSetup extends Iced {
  public static final byte GUESS_SEP = -1;
  public static final int NO_HEADER = -1;
  public static final int GUESS_HEADER = 0;
  public static final int HAS_HEADER = 1;
  public static final int GUESS_COL_CNT = -1;
  public static final byte DEFAULT_ESCAPE_CHAR = 0;

  ParserInfo _parse_type;     // CSV, XLS, XSLX, SVMLight, Auto, ARFF, ORC
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
  int[] _skipped_columns;     // column indices that are to be skipped
  String[][] _domains;        // Domains for each column (null if numeric)
  String[][] _na_strings;       // Strings for NA in a given column
  String[][] _data;           // First few rows of parsed/tokenized data
  int[] _parse_columns_indices; // store column indices to be parsed into the final file
  byte[] _nonDataLineMarkers;
  boolean _force_col_types = false; // at end of parsing, change column type to users specified ones
  boolean _tz_adjust_to_local = false;
  String[] _orig_column_types;  // copy over the original column type setup before translating to byte[]

  String[] _synthetic_column_names; // Columns with constant values to be added to parsed Frame
  String[][] _synthetic_column_values; // For each imported file contains array of values for each synthetic column
  byte _synthetic_column_type = Vec.T_STR; // By default, all synthetic columns are treated as strings
  byte _escapechar = DEFAULT_ESCAPE_CHAR; // One ASCII character used to escape other characters, by default '\'

  String [] _fileNames = new String[]{"unknown"};
  public boolean disableParallelParse;
  Key<DecryptionTool> _decrypt_tool;

  public void setFileName(String name) {_fileNames[0] = name;}

  private ParseWriter.ParseErr[] _errs;
  public final ParseWriter.ParseErr[] errs() { return _errs;}

  public void addErrs(ParseWriter.ParseErr... errs){
    _errs = ArrayUtils.append(_errs,errs);
  }

  public int _chunk_size = FileVec.DFLT_CHUNK_SIZE;  // Optimal chunk size to be used store values
  PreviewParseWriter _column_previews = null;
  public String[] parquetColumnTypes;  // internal parameters only

  public ParseSetup(ParseSetup ps) {
    this(ps._parse_type,
            ps._separator, ps._single_quotes, ps._check_header, ps._number_columns,
            ps._column_names, ps._column_types, ps._domains, ps._na_strings, ps._data,
            new ParseWriter.ParseErr[0], ps._chunk_size, ps._decrypt_tool, ps._skipped_columns,
            ps._nonDataLineMarkers, ps._escapechar, ps._tz_adjust_to_local);
  }

  public static ParseSetup makeSVMLightSetup(){
    return new ParseSetup(SVMLight_INFO, ParseSetup.GUESS_SEP,
        false,ParseSetup.NO_HEADER,1,null,new byte[]{Vec.T_NUM},null,null,null, new ParseWriter.ParseErr[0],
            null, false);
  }

  // This method was called during guess setup, lot of things are null, like ctypes.
  // when it is called again, it either contains the guess column types or it will have user defined column types
  public ParseSetup(ParserInfo parse_type, byte sep, boolean singleQuotes, int checkHeader, int ncols, String[] columnNames,
                    byte[] ctypes, String[][] domains, String[][] naStrings, String[][] data, ParseWriter.ParseErr[] errs,
                    int chunkSize, byte[] nonDataLineMarkers, byte escapeChar, boolean tzAdjustToLocal) {
    this(parse_type, sep, singleQuotes, checkHeader, ncols, columnNames, ctypes, domains, naStrings, data, errs,
        chunkSize, null, null, nonDataLineMarkers, escapeChar, tzAdjustToLocal);
  }

  public ParseSetup(ParserInfo parse_type, byte sep, boolean singleQuotes, int checkHeader, int ncols, String[] columnNames,
                    byte[] ctypes, String[][] domains, String[][] naStrings, String[][] data, ParseWriter.ParseErr[] errs,
                    int chunkSize, Key<DecryptionTool> decrypt_tool, int[] skipped_columns, byte[] nonDataLineMarkers, byte escapeChar, boolean tzAdjustToLocal) {
    this(parse_type, sep, singleQuotes, checkHeader, ncols, columnNames, ctypes, domains, naStrings, data, errs, 
            chunkSize, decrypt_tool, skipped_columns, nonDataLineMarkers, escapeChar, false, tzAdjustToLocal);
  }

  public ParseSetup(ParserInfo parse_type, byte sep, boolean singleQuotes, int checkHeader, int ncols, String[] columnNames,
                    byte[] ctypes, String[][] domains, String[][] naStrings, String[][] data, ParseWriter.ParseErr[] errs,
                    int chunkSize, Key<DecryptionTool> decrypt_tool, int[] skipped_columns, byte[] nonDataLineMarkers,
                    byte escapeChar, boolean force_col_types, boolean tz_adjust_to_local) {
    _parse_type = parse_type;
    _separator = sep;
    _nonDataLineMarkers = nonDataLineMarkers;
    _single_quotes = singleQuotes;
    _check_header = checkHeader;
    _number_columns = ncols;
    _column_names = columnNames;
    _column_types = ctypes;
    _domains = domains;
    _na_strings = naStrings;
    _data = data;
    _chunk_size = chunkSize;
    _errs = errs;
    _decrypt_tool = decrypt_tool;
    _skipped_columns = skipped_columns;
    _escapechar = escapeChar;
    _force_col_types = force_col_types;
    _tz_adjust_to_local = tz_adjust_to_local;
    setParseColumnIndices(ncols, _skipped_columns);
  }

  public void setParseColumnIndices(int ncols, int[] skipped_columns) {
    if (skipped_columns != null) {
      int num_parse_columns = ncols - skipped_columns.length;
      if (num_parse_columns >= 0) {
        _parse_columns_indices = new int[num_parse_columns];
        int counter = 0;
        for (int index = 0; index < ncols; index++) {
          if (!ArrayUtils.contains(skipped_columns, index)) {
            _parse_columns_indices[counter++] = index;
          }
        }
      }
    } else if (ncols > 0) {
      _parse_columns_indices = new int[ncols];
      for (int index=0; index < ncols; index++)
        _parse_columns_indices[index] = index;
    }
  }
  
  public void setSyntheticColumns(String[] names, String[][] valueMapping, byte synthetic_column_type) {
    _synthetic_column_names = names;
    _synthetic_column_values = valueMapping;
    _synthetic_column_type = synthetic_column_type;
  }

  public void setParquetColumnTypes(String[] columnTypes) {
    parquetColumnTypes = columnTypes.clone();
  }

  /**
   * Create a ParseSetup with parameters from the client.
   *
   * Typically used to guide sampling in the data
   * to verify chosen settings, and fill in missing settings.
   *
   * @param ps Parse setup settings from client
   */
  public ParseSetup(ParseSetupV3 ps) {
    this(ps.parse_type != null ? ParserService.INSTANCE.getByName(ps.parse_type).info() : GUESS_INFO,
        ps.separator != 0 ? ps.separator : GUESS_SEP,
        ps.single_quotes,
        ps.check_header,
        GUESS_COL_CNT,
        ps.column_names, strToColumnTypes(ps.column_types),
        null, ps.na_strings,
        null,
        new ParseWriter.ParseErr[0],
        ps.chunk_size,
        ps.decrypt_tool != null ? ps.decrypt_tool.key() : null, ps.skipped_columns,
        ps.custom_non_data_line_markers != null ? ps.custom_non_data_line_markers.getBytes() : null,
        ps.escapechar, ps.force_col_types, ps.tz_adjust_to_local);
    this._force_col_types = ps.force_col_types;
    this._orig_column_types = this._force_col_types ? (ps.column_types == null ? null : ps.column_types.clone()) : null;
  }

  /**
   * Create a ParseSetup with all parameters except chunk size.
   * <p>
   * Typically used by file type parsers for returning final valid results
   * _chunk_size will be set later using results from all files.
   */
  public ParseSetup(ParserInfo parseType, byte sep, boolean singleQuotes, int checkHeader,
                    int ncols, String[] columnNames, byte[] ctypes,
                    String[][] domains, String[][] naStrings, String[][] data, byte[] nonDataLineMarkers, byte escapeChar, boolean tzAdjustToLocal) {
    this(parseType, sep, singleQuotes, checkHeader, ncols, columnNames, ctypes,
        domains, naStrings, data, new ParseWriter.ParseErr[0], FileVec.DFLT_CHUNK_SIZE, nonDataLineMarkers, escapeChar, tzAdjustToLocal);
  }

  /**
   * Create a ParseSetup with all parameters except chunk size.
   * <p>
   * Typically used by file type parsers for returning final valid results
   * _chunk_size will be set later using results from all files.
   */
  public ParseSetup(ParserInfo parseType, byte sep, boolean singleQuotes, int checkHeader,
                    int ncols, String[] columnNames, byte[] ctypes,
                    String[][] domains, String[][] naStrings, String[][] data, byte escapeChar, boolean tzAdjustToLocal) {
    this(parseType, sep, singleQuotes, checkHeader, ncols, columnNames, ctypes,
        domains, naStrings, data, new ParseWriter.ParseErr[0], FileVec.DFLT_CHUNK_SIZE, null, escapeChar, tzAdjustToLocal);
  }

  public ParseSetup(ParserInfo parseType, byte sep, boolean singleQuotes, int checkHeader,
                    int ncols, String[] columnNames, byte[] ctypes,
                    String[][] domains, String[][] naStrings, String[][] data, boolean tzAdjustToLocal) {
    this(parseType, sep, singleQuotes, checkHeader, ncols, columnNames, ctypes,
        domains, naStrings, data, new ParseWriter.ParseErr[0], FileVec.DFLT_CHUNK_SIZE, null, ParseSetup.DEFAULT_ESCAPE_CHAR, tzAdjustToLocal);
  }

  public ParseSetup(ParserInfo parseType, byte sep, boolean singleQuotes, int checkHeader,
                    int ncols, String[] columnNames, byte[] ctypes,
                    String[][] domains, String[][] naStrings, String[][] data, ParseWriter.ParseErr[] errs, byte[] nonDataLineMarkers, boolean tzAdjustToLocal) {
    this(parseType, sep, singleQuotes, checkHeader, ncols, columnNames, ctypes,
        domains, naStrings, data, errs, FileVec.DFLT_CHUNK_SIZE, nonDataLineMarkers, ParseSetup.DEFAULT_ESCAPE_CHAR, tzAdjustToLocal);
  }

  public ParseSetup(ParserInfo parseType, byte sep, boolean singleQuotes, int checkHeader,
                    int ncols, String[] columnNames, byte[] ctypes,
                    String[][] domains, String[][] naStrings, String[][] data, ParseWriter.ParseErr[] errs) {
    this(parseType, sep, singleQuotes, checkHeader, ncols, columnNames, ctypes,
        domains, naStrings, data, errs, FileVec.DFLT_CHUNK_SIZE, null, ParseSetup.DEFAULT_ESCAPE_CHAR, false);
  }

  /**
   * Create a ParseSetup without any column information
   * <p>
   * Typically used by file type parsers for returning final invalid results
   */
  public ParseSetup(ParserInfo parseType, byte sep, boolean singleQuotes, int checkHeader, int ncols, String[][] data, ParseWriter.ParseErr[] errs) {
    this(parseType, sep, singleQuotes, checkHeader, ncols, null, null, null, null, data, errs, FileVec.DFLT_CHUNK_SIZE, null, ParseSetup.DEFAULT_ESCAPE_CHAR, false);
  }

  /**
   * Create a default ParseSetup
   *
   * Used by Ray's schema magic
   */
  public ParseSetup() {}

  public String[] getColumnNames() { return _column_names; }
  public int[] getSkippedColumns() { return _skipped_columns; }
  public int[] get_parse_columns_indices() { return _parse_columns_indices; }
  public String[][] getData() { return _data; }

  public String[] getColumnTypeStrings() {
    String[] types = new String[_column_types.length];
    for(int i=0; i< types.length; i++)
      types[i] = Vec.TYPE_STR[_column_types[i]];
    return types;
  }
  public String[] getOrigColumnTypes() {
    return _orig_column_types;
  }
  
  public boolean getForceColTypes() {
    return _force_col_types;
  }

  public boolean gettzAdjustToLocal() {
    return _tz_adjust_to_local;
  }
  
  public byte[] getColumnTypes() { return _column_types; }

  public static byte[] strToColumnTypes(String[] strs) {
    if (strs == null) return null;
    byte[] types = new byte[strs.length];
    for(int i=0; i< types.length;i++) {
      switch (strs[i].toLowerCase()) {
      case "unknown": types[i] = Vec.T_BAD;  break;
      case "uuid":    types[i] = Vec.T_UUID; break;
      case "string":  types[i] = Vec.T_STR;  break;
      case "float":
      case "real":
      case "double":
      case "int":
      case "long":
      case "numeric": types[i] = Vec.T_NUM;  break;
      case "categorical":
      case "factor":
      case "enum":    types[i] = Vec.T_CAT;  break;
      case "time":    types[i] = Vec.T_TIME; break;
      default:        types[i] = Vec.T_BAD;
        throw new H2OIllegalArgumentException("Provided column type "+ strs[i] + " is unknown.  Cannot proceed with parse due to invalid argument.");
      }
    }
    return types;
  }

  /** This is a single entry-point to create a parser.
   *
   * Should be override in subclasses. */
  protected Parser parser(Key jobKey) {
    ParserProvider pp = ParserService.INSTANCE.getByInfo(_parse_type);
    if (pp != null) { // fill up parquet setup here
      return pp.createParser(this, jobKey);
    }

    throw new H2OIllegalArgumentException("Unknown file type.  Parse cannot be completed.",
          "Attempted to invoke a parser for ParseType:" + _parse_type + ", which doesn't exist.");
  }

  /** Return create a final parser-specific setup
   * for this configuration.
   *
   * @param inputKeys  inputs
   * @param demandedSetup  setup demanded by a user
   *
   * @return a parser specific setup based on demanded setup
   */
  public final ParseSetup getFinalSetup(Key[] inputKeys, ParseSetup demandedSetup) {
    ParserProvider pp = ParserService.INSTANCE.getByInfo(_parse_type);
    if (pp != null) {
      ParseSetup ps = pp.createParserSetup(inputKeys, demandedSetup);
      if (demandedSetup._decrypt_tool != null)
        ps._decrypt_tool = demandedSetup._decrypt_tool;
      ps.setSkippedColumns(demandedSetup.getSkippedColumns());
      ps.setParseColumnIndices(demandedSetup.getNumberColumns(), demandedSetup.getSkippedColumns()); // final consistent check between skipped_columns and parse_columns_indices
      return ps;
    }

    throw new H2OIllegalArgumentException("Unknown parser configuration! Configuration=" + this);
  }

  public int getNumberColumns() {
    return _number_columns;
  }

  public final DecryptionTool getDecryptionTool() {
    return DecryptionTool.get(_decrypt_tool);
  }
  
  public final String[] getParquetColumnTypes() {
    return parquetColumnTypes;
  }

  public final ParserInfo.ParseMethod parseMethod(int nfiles, Vec v) {
    boolean isEncrypted = ! getDecryptionTool().isTransparent();
    return _parse_type.parseMethod(nfiles, v.nChunks(), disableParallelParse, isEncrypted);
  }

  // Set of duplicated column names
  HashSet<String> checkDupColumnNames() {
    HashSet<String> conflictingNames = new HashSet<>();
    if( null==_column_names ) return conflictingNames;
    HashSet<String> uniqueNames = new HashSet<>();
    for( String n : _column_names)
      if( !uniqueNames.add(n) ) conflictingNames.add(n);
    return conflictingNames;
  }

  @Override public String toString() {
    return _parse_type.toString();
  }

  static boolean allStrings(String [] line){
    BufferedString str = new BufferedString();
    for( String s : line ) {
      try {
        Double.parseDouble(s);
        return false;       // Number in 1st row guesses: No Column Header
      } catch (NumberFormatException e) { /*Pass - determining if number is possible*/ }
      str.set(s);
      if(ParseTime.isTime(str)) return false;
      if(ParseUUID.isUUID(str)) return false;
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
    return guessSetup(fkeys, new ParseSetup(GUESS_INFO, GUESS_SEP, singleQuote, checkHeader, GUESS_COL_CNT, null, new ParseWriter.ParseErr[0]));
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

    //Calc chunk-size
    // FIXME: should be a parser specific - or at least parser should be able to override defaults
    Iced ice = DKV.getGet(fkeys[0]);
    if (ice instanceof Frame && ((Frame) ice).vec(0) instanceof UploadFileVec) {
      t._gblSetup._chunk_size = FileVec.DFLT_CHUNK_SIZE;
    } else {
      t._gblSetup._chunk_size = FileVec.calcOptimalChunkSize(t._totalParseSize, t._gblSetup._number_columns, t._maxLineLength,
              H2ORuntime.availableProcessors(), H2O.getCloudSize(), false /*use new heuristic*/, true);
    }
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
    public long _totalParseSize;
    public long _maxLineLength;
    String _file;

    /**
     *
     * @param userSetup ParseSetup to guide examination of files
     */
    public GuessSetupTsk(ParseSetup userSetup) {
      _userSetup = userSetup;
    }

    /**
     * Runs once on each file to guess that file's ParseSetup
     *
     * For ByteVecs, UploadFileVecs, compressed files and small files,
     * the ParseSetup is guessed from a single DFLT_CHUNK_SIZE chunk from
     * the start of the file.  This is because UploadFileVecs and compressed
     * files don't allow random sampling, small files don't need it, and
     * ByteVecs tend to be small.
     *
     * For larger NSFFileVecs and HDFSFileVecs 1M samples are taken at the
     * beginning of every 100M, and an additional sample is taken from the
     * last chunk of the file.  The results of these samples are merged
     * together (and compared for consistency).
     *
     * Sampling more than the first bytes is preferred, since large data sets
     * with sorted columns may have all the same value in their first bytes,
     * making for poor type guesses.
     *
     */
    @Override public void map(Key key) {
      _file = key.toString();
      Iced ice = DKV.getGet(key);
      if(ice == null) throw new H2OIllegalArgumentException("Missing data","Did not find any data under key " + key);
      ByteVec bv = (ByteVec)(ice instanceof ByteVec ? ice : ((Frame)ice).vecs()[0]);
      byte[] bits;
      try {
        bits = bv.getFirstBytes();
      } catch (Exception e) {
        throw new RuntimeException("This H2O node couldn't read data from '" + _file + "'. " +
                "Please make sure the file is available on all H2O nodes and/or check the working directories.", e);
      }
      Key<DecryptionTool> decryptToolKey = _userSetup._decrypt_tool != null ?
              _userSetup._decrypt_tool : H2O.defaultDecryptionTool();
      DecryptionTool decrypt = DKV.getGet(decryptToolKey);
      if (decrypt != null) {
        byte[] plainBits = decrypt.decryptFirstBytes(bits);
        if (plainBits != bits)
          bits = plainBits;
        else
          decryptToolKey = null;
      }
      bits = ZipUtil.getFirstUnzippedBytes(bits);
      // The bits can be null
      if (bits != null && bits.length > 0) {
        _empty = false;

        // get file size
//        float decompRatio = ZipUtil.decompressionRatio(bv);
//        if (decompRatio > 1.0)
//          _totalParseSize += bv.length() * decompRatio; // estimate file size
//        else  // avoid numerical distortion of file size when not compressed

        // since later calculation of chunk size and later number of chunks do not consider the
        // compression ratio, we should not do that here either.  Quick fix proposed by Tomas.  Sleek!
        _totalParseSize += bv.length();

        // Check for supported encodings
        checkEncoding(bits);

        // Compute the max line length (to help estimate the number of bytes to read per Parse map)
        _maxLineLength = maxLineLength(bits);
        if (_maxLineLength==-1) throw new H2OIllegalArgumentException("The first 4MB of the data don't contain any line breaks. Cannot parse.");

        // only preview 1 DFLT_CHUNK_SIZE for ByteVecs, UploadFileVecs, compressed, and small files
/*        if (ice instanceof ByteVec
                || ((Frame)ice).vecs()[0] instanceof UploadFileVec
                || bv.length() <= FileVec.DFLT_CHUNK_SIZE
                || decompRatio > 1.0) { */
        try {
          _gblSetup = guessSetup(bv, bits, _userSetup);
          _gblSetup._decrypt_tool = decryptToolKey;
          for(ParseWriter.ParseErr e:_gblSetup._errs) {
//            e._byteOffset += e._cidx*Parser.StreamData.bufSz;
            e._cidx = 0;
            e._file = _file;
          }
        } catch (ParseDataset.H2OParseException pse) {
          throw pse.resetMsg(pse.getMessage()+" for "+key);
        }
/*        } else { // file is aun uncompressed NFSFileVec or HDFSFileVec & larger than the DFLT_CHUNK_SIZE
          FileVec fv = (FileVec) ((Frame) ice).vecs()[0];
          // reset chunk size to 1M (uncompressed)
          int chkSize = (int) ((1<<20) /decompRatio);
          fv.setChunkSize((Frame) ice, chkSize);

          // guessSetup from first chunk
          _gblSetup = guessSetup(fv.getPreviewChunkBytes(0), _userSetup);
          _userSetup._check_header = -1; // remaining chunks shouldn't check for header
          _userSetup._parse_type = _gblSetup._parse_type; // or guess parse type

          //preview 1M data every 100M
          int numChunks = fv.nChunks();
          for (int i=100; i < numChunks;i += 100) {
            bits = fv.getPreviewChunkBytes(i);
            if (bits != null)
              _gblSetup = mergeSetups(_gblSetup, guessSetup(bits, _userSetup));
          }

          // grab sample at end of file (if not done by prev loop)
          if (numChunks % 100 > 1){
            bits = fv.getPreviewChunkBytes(numChunks - 1);
            if (bits != null)
              _gblSetup = mergeSetups(_gblSetup, guessSetup(bits, _userSetup));
          }

          // return chunk size to DFLT
          fv.setChunkSize((Frame) ice, FileVec.DFLT_CHUNK_SIZE);
        } */
        // report if multiple files exist in zip archive
/*        if (ZipUtil.getFileCount(bv) > 1) {
          if (_gblSetup._errors != null)
            _gblSetup._errors = Arrays.copyOf(_gblSetup._errors, _gblSetup._errors.length + 1);
          else
            _gblSetup._errors = new String[1];

          _gblSetup._errors[_gblSetup._errors.length - 1] = "Only single file zip " +
                  "archives are currently supported, only the first file has been parsed.  " +
                  "Remaining files have been ignored.";
        }*/
      }
      if (_gblSetup==null)
        throw new RuntimeException("This H2O node couldn't find the file(s) to parse. Please check files and/or working directories.");
      _gblSetup.settzAdjustToLocal(_userSetup.gettzAdjustToLocal());
      _gblSetup.setFileName(FileUtils.keyToFileName(key));
    }

    /**
     * Merges ParseSetup results, conflicts, and errors from several files
     */
    @Override
    public void reduce(GuessSetupTsk other) {
      if (other._empty) return;

      if (_gblSetup == null) {
        _empty = false;
        _gblSetup = other._gblSetup;
        assert (_gblSetup != null);
        return;
      }
      _gblSetup = mergeSetups(_gblSetup, other._gblSetup, _file, other._file);
      _totalParseSize += other._totalParseSize;
      _maxLineLength = Math.max(_maxLineLength, other._maxLineLength);
    }

    @Override public void postGlobal() {
      if (_gblSetup._column_previews != null && !_gblSetup._parse_type.equals(ARFF_INFO)) {
        _gblSetup._column_types = _gblSetup._column_previews.guessTypes();
        if (_userSetup._na_strings == null)
          _gblSetup._na_strings = _gblSetup._column_previews.guessNAStrings(_gblSetup._column_types);
        else
          _gblSetup._na_strings = _userSetup._na_strings;
      }
      _gblSetup._tz_adjust_to_local = _gblSetup._tz_adjust_to_local || _userSetup._tz_adjust_to_local;
//      if(_gblSetup._errs != null)
        for(ParseWriter.ParseErr err:_gblSetup._errs)
          Log.warn("ParseSetup: " + err.toString());
    }
    private ParseSetup mergeSetups(ParseSetup setupA, ParseSetup setupB, String fileA, String fileB) {
      // FIXME: have a merge function defined on a specific parser setup (each parser setup is responsible for merge)
      if (setupA == null) return setupB;
      if(setupA._parse_type.equals(DefaultParserProviders.SVMLight_INFO) && setupB._parse_type.equals(DefaultParserProviders.SVMLight_INFO)){
        // no merging for svm light, all columns are numeric and we take the max of number of columns (it's an estimate anyways)
        return setupA._number_columns >= setupB._number_columns?setupA:setupB;
      }
      ParseSetup mergedSetup = setupA;

      mergedSetup._tz_adjust_to_local = setupA._tz_adjust_to_local || setupB._tz_adjust_to_local;
      mergedSetup._check_header = unifyCheckHeader(setupA._check_header, setupB._check_header);

      mergedSetup._separator = unifyColumnSeparators(setupA._separator, setupB._separator);
      if (setupA._parse_type.equals(ARFF_INFO) && setupB._parse_type.equals(CSV_INFO))
        ;// do nothing parse_type and col_types are already set correctly
      else if (setupA._parse_type.equals(CSV_INFO) && setupB._parse_type.equals(ARFF_INFO)) {
        mergedSetup._parse_type = ARFF_INFO;
        mergedSetup._column_types = setupB._column_types;
        mergedSetup._nonDataLineMarkers = setupB._nonDataLineMarkers;
      } else if (setupA.isCompatible(setupB)) {
        mergedSetup._column_previews = PreviewParseWriter.unifyColumnPreviews(setupA._column_previews, setupB._column_previews);
      } else
        throw new ParseDataset.H2OParseException("File type mismatch. Cannot parse files " + setupA.file() + " and " + setupB.file() + " of type "
                + setupA._parse_type.name() + " and " + setupB._parse_type.name() + " as one dataset.");
      mergedSetup._column_names = unifyColumnNames(setupA._column_names, setupB._column_names);
      mergedSetup._number_columns = mergedSetup._parse_type.equals(CSV_INFO) ? Math.max(setupA._number_columns,setupB._number_columns):unifyColumnCount(setupA._number_columns, setupB._number_columns,mergedSetup, fileA, fileB);
      if (mergedSetup._data.length < PreviewParseWriter.MAX_PREVIEW_LINES) {
        int n = mergedSetup._data.length;
        int m = Math.min(PreviewParseWriter.MAX_PREVIEW_LINES, n + setupB._data.length - 1);
        mergedSetup._data = Arrays.copyOf(mergedSetup._data, m);
        System.arraycopy(setupB._data, 1, mergedSetup._data, n, m - n);
      }
      mergedSetup._errs = ArrayUtils.append(setupA._errs,setupB._errs);
      mergedSetup._fileNames = ArrayUtils.append(setupA._fileNames,setupB._fileNames);
      if(mergedSetup._errs.length > 20)
        mergedSetup._errs = Arrays.copyOf(mergedSetup._errs,20);
      return mergedSetup;
    }

    private static int unifyCheckHeader(int chkHdrA, int chkHdrB){
      if (chkHdrA == GUESS_HEADER || chkHdrB == GUESS_HEADER)
        throw new ParseDataset.H2OParseException("Unable to determine header on a file. Not expected.");
      if (chkHdrA == HAS_HEADER || chkHdrB == HAS_HEADER) return HAS_HEADER;
      else return NO_HEADER;

    }

    private static byte unifyColumnSeparators(byte sepA, byte sepB) {
      if( sepA == sepB) return sepA;
      else if (sepA == GUESS_SEP) return sepB;
      else if (sepB == GUESS_SEP) return sepA;
      // TODO: Point out which file is problem
      throw new ParseDataset.H2OParseException("Column separator mismatch. One file seems to use \""
              + (char) sepA + "\" and the other uses \"" + (char) sepB + "\".");
    }

    private int unifyColumnCount(int cntA, int cntB, ParseSetup mergedSetup, String fileA, String fileB) {
      if (cntA == cntB) return cntA;
      else if (cntA == 0) return cntB;
      else if (cntB == 0) return cntA;
      else { // files contain different numbers of columns
        ParseWriter.ParseErr err = new ParseWriter.ParseErr();
        err._err = "Incompatible number of columns, " + cntA + " != " + cntB;
        err._file = fileA + ", " + fileB;
        mergedSetup._errs = ArrayUtils.append(mergedSetup._errs,err);
        return Math.max(cntA,cntB);
      }
    }

    private static String[] unifyColumnNames(String[] namesA, String[] namesB){
      if (namesA == null) return namesB;
      else if (namesB == null) return namesA;
      else {
        for (int i = 0; i < namesA.length; i++) {
          if (i > namesB.length || !namesA[i].equals(namesB[i])) {
            // TODO improvement: if files match except for blanks, merge?
            throw new ParseDataset.H2OParseException("Column names do not match between files.");
          }
        }
        return namesA;
      }
    }
  }

  private String file() {
    String [] names = _fileNames;
    if(names.length > 5)
      names = Arrays.copyOf(names,5);
    return Arrays.toString(names);
  }

  protected boolean isCompatible(ParseSetup setupB) {
    return _parse_type.equals(setupB._parse_type) && _parse_type.equals(DefaultParserProviders.SVMLight_INFO) ||  _number_columns == setupB._number_columns;
  }

  /**
   * Guess everything from a single pile-o-bits.  Used in tests, or in initial
   * parser inspections when the user has not told us anything about separators
   * or headers.
   *
   * @param bits Initial bytes from a parse source
   * @return ParseSetup settings from looking at all files
   */
  public static ParseSetup guessSetup(ByteVec bv, byte [] bits, ParseSetup userSetup) {
    ParserProvider pp = ParserService.INSTANCE.getByInfo(userSetup._parse_type);
    if (pp != null) {
      return pp.guessSetup(bv, bits, userSetup.toInitialSetup());
    }
    throw new ParseDataset.H2OParseException("Cannot determine file type.");
  }

  /**
   * Sanitizes a user-provided Parse Setup
   * @return initial ParseSetup object to be passed to the ParserProvider
   */
  private ParseSetup toInitialSetup() {
    return new ParseSetup(_parse_type, _separator, _single_quotes, _check_header, GUESS_COL_CNT, _column_names,
            _column_types, null, null, null, _nonDataLineMarkers, _escapechar, false);
  }

  /**
   * Cleans up the file name to make .hex name
   * to be used as a destination key.  Eliminates
   * common file extensions, and replaces odd
   * characters.
   *
   * @param n filename to be cleaned
   * @return cleaned name
   */
  public static String createHexName(String n) {
    // blahblahblah/myName.ext ==> myName
    // blahblahblah/myName.csv.ext ==> myName
    int sep = n.lastIndexOf(java.io.File.separatorChar);
    if( sep > 0 ) n = n.substring(sep+1);
    int dot = n.lastIndexOf('.');
    while ( dot > 0 &&
            (n.endsWith("zip")
            || n.endsWith("gz")
            || n.endsWith("csv")
            || n.endsWith("xls")
            || n.endsWith("txt")
            || n.endsWith("svm")
            || n.endsWith("orc")
            || n.endsWith("arff"))) {
      n = n.substring(0, dot);
      dot = n.lastIndexOf('.');
    }
    // "2012_somedata" ==> "X2012_somedata"
    if( !Character.isJavaIdentifierStart(n.charAt(0)) ) n = "X"+n;
    // "human%Percent" ==> "human_Percent"
    n = StringUtils.sanitizeIdentifier(n);
    // "myName" ==> "myName.hex"
    int i = 0;
    String res = n + ".hex";
    Key k = Key.make(res);
    // Renumber to handle dup names
    while(DKV.get(k) != null)
      k = Key.make(res = n + ++i + ".hex");
    return res;
  }

  /**
   *  Reject unsupported encodings
   *
   * For the curious, this is hardly a complete test, it only catches the
   * most polite UTF-16 cases.  Switch to jChardet or guessEncoding libraries
   * for more robust solutions.  WARNING: not all UTF-16 files
   * use BOM to indicate their encoding.  Even worse, some datasets may be
   * made from disparate sources, and could used a mix that wouldn't be
   * detected by this.
   *
   * @param bits data to be examined for encoding
   */
  private static final void checkEncoding(byte[] bits) {
    if (bits.length >= 2) {
      if ((bits[0] == (byte) 0xff && bits[1] == (byte) 0xfe) /* UTF-16, little endian */ ||
              (bits[0] == (byte) 0xfe && bits[1] == (byte) 0xff) /* UTF-16, big endian */) {
        throw new ParseDataset.H2OParseException("UTF16 encoding detected, but is not supported.");
      }
    }
  }

  /**
   * Compute the longest line length in an array of bytes
   * @param bytes Array of bytes (containing 0 or more newlines)
   * @return The longest line length in the given bytes
   */
  private static final int maxLineLength(byte[] bytes) {
    int start = bytes.length;
    int max = -1;
    for(int i = 0; i < bytes.length; ++i){
      if(CsvParser.isEOL(bytes[i])){
        int delta = i-start+1;
        max = Math.max(max,delta);
        start = i+1;
      }
    }
    return Math.max(max,bytes.length-start+1);
  }

  /**
   * Copies the common setup to another object (that is possibly and extension of the base setup).
   * Note: this method only copies fields directly declared in ParseSetup class, it doesn't handle
   * fields that are declared in classes derived from ParseSetup.
   * @param setup target setup object
   * @param <T> class derived from ParseSetup
   * @return the target setup object (for convenience)
   */
  public <T extends ParseSetup> T copyTo(T setup) {
    try {
      for (Field field : ParseSetup.class.getDeclaredFields()) {
        if (! java.lang.reflect.Modifier.isStatic(field.getModifiers())) field.set(setup, field.get(this));
      }
      return setup;
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Tests whether a given string represents a NA in a given column.
   * Note: NAs are expected to be made ONLY of ASCII (7-bit) characters, NA constants in unicode won't be recognized.
   * @param colIdx index of the column
   * @param str string to be tested for NA
   * @return true - string is one of the column's NAs, false otherwise
   */
  public boolean isNA(int colIdx, BufferedString str) {
    if (_na_strings == null || colIdx >= _na_strings.length || _na_strings[colIdx] == null)
      return false;
    for (String naStr : _na_strings[colIdx])
      if (str.equalsAsciiString(naStr)) return true;
    return false;
  }
  
  public ParserInfo getParseType() {
    return _parse_type;
  }

  public ParseSetup setParseType(ParserInfo parse_type) {
    this._parse_type = parse_type;
    return this;
  }

  public ParseSetup setSeparator(byte separator) {
    this._separator = separator;
    return this;
  }

  public ParseSetup setSingleQuotes(boolean single_quotes) {
    this._single_quotes = single_quotes;
    return this;
  }

  public ParseSetup setCheckHeader(int check_header) {
    this._check_header = check_header;
    return this;
  }

  public ParseSetup setNumberColumns(int number_columns) {
    this._number_columns = number_columns;
    return this;
  }

  public ParseSetup setColumnNames(String[] column_names) {
    this._column_names = column_names;
    return this;
  }

  public ParseSetup setSkippedColumns(int[] skipped_columns) {
    this._skipped_columns = skipped_columns;
    return this;
  }

  public ParseSetup setColumnTypes(byte[] column_types) {
    this._column_types = column_types;
    return this;
  }
  
  public ParseSetup setOrigColumnTypes(String[] col_types) {
    this._orig_column_types = col_types;
    return this;
  }
  
  public ParseSetup setForceColTypes(boolean force_col_types) {
    this._force_col_types = force_col_types;
    return this;
  }

  public ParseSetup settzAdjustToLocal(boolean tz_adjust_to_local) {
    this._tz_adjust_to_local = tz_adjust_to_local;
    return this;
  }

  public ParseSetup setDomains(String[][] domains) {
    this._domains = domains;
    return this;
  }

  public ParseSetup setNAStrings(String[][] na_strings) {
    this._na_strings = na_strings;
    return this;
  }

  public ParseSetup setChunkSize(int chunk_size) {
    this._chunk_size = chunk_size;
    return this;
  }

  public ParseSetup setDecryptTool(Key<DecryptionTool> decrypt_tool) {
    this._decrypt_tool = decrypt_tool;
    return this;
  }
  
} // ParseSetup state class
