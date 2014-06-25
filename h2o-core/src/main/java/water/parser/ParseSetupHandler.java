package water.parser;

import java.util.HashSet;
import water.*;
import water.api.Handler;

/** A class holding parser-setup flags: kind of parser, field separator, column
 *  header labels, whether or not to allow single-quotes to quote, number of
 *  columns discovered.
 */
public class ParseSetupHandler extends Handler<ParseSetupHandler,ParseSetupV2> {
  static final byte AUTO_SEP = -1;
  Key[] _srcs;                      // Source Keys being parsed
  String _hexName;                  // Cleaned up result Key suggested name

  boolean _isValid;   // The initial parse is sane
  // Parse Flavor
  ParserType _pType; // CSV, XLS, XSLX, SVMLight, Auto
  byte _sep; // Field separator, usually comma ',' or TAB or space ' '
  int _ncols;     // Columns to parse
  // Whether or not single-quotes quote a field.  E.g. how do we parse:
  // raw data:  123,'Mally,456,O'Mally
  // singleQuotes==True  ==> 2 columns: 123  and  Mally,456,OMally
  // singleQuotes==False ==> 4 columns: 123  and  'Mally  and  456  and  O'Mally
  boolean _singleQuotes;
  String[] _columnNames;
  private long _invalidLines; // Number of broken/invalid lines found
  String[][] _data;           // First few rows of parsed/tokenized data
  String[] _errors;           // Errors in this parse setup
  
  public ParseSetupHandler( boolean isValid, long invalidLines, String[] errors, ParserType t, byte sep, int ncols, boolean singleQuotes, String[] columnNames, String[][] data ) {
    _isValid = isValid;
    _invalidLines = invalidLines;
    _pType = t;
    _sep = sep;
    _ncols = ncols;
    _singleQuotes = singleQuotes;
    _columnNames = columnNames;
    _data = data;
    _errors = errors;
  }

  // Invalid setup based on a prior valid one
  ParseSetupHandler(ParseSetupHandler ps, String err) {
    this(false,ps._invalidLines,new String[]{err},ps._pType,ps._sep,ps._ncols,ps._singleQuotes,ps._columnNames,ps._data);
  }

  // Called from Nano request server with a set of Keys, produce a suitable parser setup guess.
  public ParseSetupHandler() {}
  public void guessSetup( ) {
    _hexName = hex(_srcs[0].toString());
    byte[] bits = ZipUtil.getFirstUnzippedBytes(ParseDataset2.getByteVec(_srcs[0]));
    ParseSetupHandler psh = guessSetup(bits,false,0/*guess header*/);
    // Update in-place
    _isValid = psh._isValid;
    _pType = psh._pType;
    _sep = psh._sep;
    _ncols = psh._ncols;
    _singleQuotes = psh._singleQuotes;
    _columnNames = psh._columnNames == null ? ParseDataset2.genericColumnNames(_ncols) : psh._columnNames;
    _invalidLines = psh._invalidLines;
    _data = psh._data;
  }


  final boolean hasHeaders() { return _columnNames != null; }

  public Parser parser() {
    switch( _pType ) {
    case CSV:      return new      CsvParser(this);
    case XLS:      return new      XlsParser(this);
    case SVMLight: return new SVMLightParser(this);
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

  @Override public String toString() { return _pType.toString( _ncols, _sep ); }

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

  // Guess everything from a single pile-o-bits.  Used in tests, or in initial
  // parser inspections when the user has not told us anything about separators
  // or headers.
  public static ParseSetupHandler guessSetup( byte[] bits, boolean singleQuotes, int checkHeader ) { return guessSetup(bits, ParserType.AUTO, AUTO_SEP, -1, singleQuotes, checkHeader, null); }

  private static final ParserType guessTypeOrder[] = {ParserType.XLS,ParserType.XLSX,ParserType.SVMLight,ParserType.CSV};
  public static ParseSetupHandler guessSetup( byte[] bits, ParserType pType, byte sep, int ncols, boolean singleQuotes, int checkHeader, String[] columnNames ) {
    switch( pType ) {
    case CSV:      return      CsvParser.CSVguessSetup(bits,sep,ncols,singleQuotes,checkHeader,columnNames);
    case SVMLight: return SVMLightParser.   guessSetup(bits);
    case XLS:      return      XlsParser.   guessSetup(bits);
    case AUTO:
      for( ParserType pType2 : guessTypeOrder ) {
        try {
          ParseSetupHandler ps = guessSetup(bits,pType2,sep,ncols,singleQuotes,checkHeader,columnNames);
          if( ps != null && ps._isValid ) return ps;
        } catch( Throwable ignore ) { /*ignore failed parse attempt*/ }
      }
    }
    return new ParseSetupHandler( false, 0, new String[]{"Cannot determine file type"}, pType, sep, ncols, singleQuotes, columnNames, null );
  }

  // Guess a local setup that is compatible to the given global (this) setup.
  // If they are not compatible, there will be _errors set.
  ParseSetupHandler guessSetup( byte[] bits ) {
    assert _isValid;
    ParseSetupHandler ps = guessSetup(bits, _singleQuotes, 0/*guess header*/);
    if( !ps._isValid ) return ps; // Already invalid
    if( _pType != ps._pType ||
        (_pType == ParserType.CSV && (_sep != ps._sep || _ncols != ps._ncols)) )
      return new ParseSetupHandler(ps,"Conflicting file layouts, expecting: "+this+" but found "+ps+"\n");
    return ps;
  }

  private static String hex( String n ) {
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

  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }
  // ParseSetup Schemas are at V2
  @Override protected ParseSetupV2 schema(int version) { return new ParseSetupV2(); }
  @Override protected void compute2() { throw H2O.fail(); }
}
