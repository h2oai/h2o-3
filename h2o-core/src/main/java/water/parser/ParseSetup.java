package water.parser;

import water.*;
import water.api.schemas3.ParseSetupV3;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.*;
import water.util.ArrayUtils;
import water.util.FileUtils;
import water.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
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
  String[][] _domains;        // Domains for each column (null if numeric)
  String[][] _na_strings;       // Strings for NA in a given column
  String[][] _data;           // First few rows of parsed/tokenized data

  String [] _fileNames = new String[]{"unknown"};

  public void setFileName(String name) {_fileNames[0] = name;}

  public ParseWriter.ParseErr[] _errs;
  public int _chunk_size = FileVec.DFLT_CHUNK_SIZE;  // Optimal chunk size to be used store values
  PreviewParseWriter _column_previews = null;

  public ParseSetup(ParseSetup ps) {
    this(ps._parse_type,
         ps._separator, ps._single_quotes, ps._check_header, ps._number_columns,
         ps._column_names, ps._column_types, ps._domains, ps._na_strings, ps._data,
         new ParseWriter.ParseErr[0], ps._chunk_size);
  }


  public static ParseSetup makeSVMLightSetup(){
    return new ParseSetup(SVMLight_INFO, ParseSetup.GUESS_SEP,
        false,ParseSetup.NO_HEADER,1,null,new byte[]{Vec.T_NUM},null,null,null, new ParseWriter.ParseErr[0]);
  }

  // This method was called during guess setup, lot of things are null, like ctypes.
  // when it is called again, it either contains the guess column types or it will have user defined column types
  public ParseSetup(ParserInfo parse_type, byte sep, boolean singleQuotes, int checkHeader, int ncols, String[] columnNames, byte[] ctypes, String[][] domains, String[][] naStrings, String[][] data, ParseWriter.ParseErr[] errs, int chunkSize) {
    _parse_type = parse_type;
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
    _errs = errs;
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
         ps.chunk_size);
  }

  /**
   * Create a ParseSetup with all parameters except chunk size.
   *
   * Typically used by file type parsers for returning final valid results
   * _chunk_size will be set later using results from all files.
   */
  public ParseSetup(ParserInfo parseType, byte sep, boolean singleQuotes, int checkHeader,
                    int ncols, String[] columnNames, byte[] ctypes,
                    String[][] domains, String[][] naStrings, String[][] data){
    this(parseType, sep, singleQuotes, checkHeader, ncols, columnNames, ctypes,
        domains, naStrings, data, new ParseWriter.ParseErr[0], FileVec.DFLT_CHUNK_SIZE);
  }
  public ParseSetup(ParserInfo parseType, byte sep, boolean singleQuotes, int checkHeader,
                    int ncols, String[] columnNames, byte[] ctypes,
                    String[][] domains, String[][] naStrings, String[][] data, ParseWriter.ParseErr[] errs) {
    this(parseType, sep, singleQuotes, checkHeader, ncols, columnNames, ctypes,
            domains, naStrings, data, errs, FileVec.DFLT_CHUNK_SIZE);
  }

  /**
   * Create a ParseSetup without any column information
   *
   * Typically used by file type parsers for returning final invalid results
   */
  public ParseSetup(ParserInfo parseType, byte sep, boolean singleQuotes, int checkHeader, int ncols, String[][] data, ParseWriter.ParseErr[] errs) {
    this(parseType, sep, singleQuotes, checkHeader, ncols, null, null, null, null, data, errs, FileVec.DFLT_CHUNK_SIZE);
  }

  /**
   * Create a default ParseSetup
   *
   * Used by Ray's schema magic
   */
  public ParseSetup() {}

  public String[] getColumnNames() { return _column_names; }
  public String[][] getData() { return _data; }

  public String[] getColumnTypeStrings() {
    String[] types = new String[_column_types.length];
    for(int i=0; i< types.length; i++)
      types[i] = Vec.TYPE_STR[_column_types[i]];
    return types;
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
    if (pp != null) {
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
      return pp.createParserSetup(inputKeys, demandedSetup);
    }

    throw new H2OIllegalArgumentException("Unknown parser configuration! Configuration=" + this);
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
              Runtime.getRuntime().availableProcessors(), H2O.getCloudSize(), false /*use new heuristic*/, true);
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
      byte [] bits = ZipUtil.getFirstUnzippedBytes(bv);
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
          for(ParseWriter.ParseErr e:_gblSetup._errs) {
            e._byteOffset += e._cidx*Parser.StreamData.bufSz;
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
//      if(_gblSetup._errs != null)
        for(ParseWriter.ParseErr err:_gblSetup._errs)
          Log.warn("ParseSetup: " + err.toString());
    }

    private ParseSetup mergeSetups(ParseSetup setupA, ParseSetup setupB, String fileA, String fileB) {
      // FIXME: have a merge function defined on a specific parser setup (each parser setup is responsible for merge)
      if (setupA == null) return setupB;
      ParseSetup mergedSetup = setupA;

      mergedSetup._check_header = unifyCheckHeader(setupA._check_header, setupB._check_header);

      mergedSetup._separator = unifyColumnSeparators(setupA._separator, setupB._separator);
      if (setupA._parse_type.equals(ARFF_INFO) && setupB._parse_type.equals(CSV_INFO))
        ;// do nothing parse_type and col_types are already set correctly
      else if (setupA._parse_type.equals(CSV_INFO) && setupB._parse_type.equals(ARFF_INFO)) {
        mergedSetup._parse_type = ARFF_INFO;
        mergedSetup._column_types = setupB._column_types;
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
    return _parse_type.equals(setupB._parse_type) && _number_columns == setupB._number_columns;
  }

  /**
   * Guess everything from a single pile-o-bits.  Used in tests, or in initial
   * parser inspections when the user has not told us anything about separators
   * or headers.
   *
   * @param bits Initial bytes from a parse source
   * @return ParseSetup settings from looking at all files
   */
  public static ParseSetup guessSetup( ByteVec bv, byte [] bits, ParseSetup userSetup ) {
    return guessSetup(bv, bits, userSetup._parse_type, userSetup._separator, GUESS_COL_CNT, userSetup._single_quotes, userSetup._check_header, userSetup._column_names, userSetup._column_types, null, null);
  }

  public static ParseSetup guessSetup(ByteVec bv, byte [] bits, ParserInfo parserType, byte sep, int ncols, boolean singleQuotes, int checkHeader, String[] columnNames, byte[] columnTypes, String[][] domains, String[][] naStrings ) {
    ParserProvider pp = ParserService.INSTANCE.getByInfo(parserType);
    if (pp != null) {
      return pp.guessSetup(bv, bits, sep, ncols, singleQuotes, checkHeader, columnNames, columnTypes, domains, naStrings);
    }
    throw new ParseDataset.H2OParseException("Cannot determine file type.");
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
  private static final long maxLineLength(byte[] bytes) {
    if (bytes.length >= 2) {
      String st = new String(bytes);
      StringReader sr = new StringReader(st);
      BufferedReader br = new BufferedReader(sr);
      String line;
      long maxLineLength=0;
      try {
        while(true) {
          line = br.readLine();
          if (line == null) break;
          maxLineLength = Math.max(line.length(), maxLineLength);
        }
      } catch (IOException e) {
        return -1;
      }
      return maxLineLength;
    }
    return -1;
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

  public ParseSetup setColumnTypes(byte[] column_types) {
    this._column_types = column_types;
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

} // ParseSetup state class
