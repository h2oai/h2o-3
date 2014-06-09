package water.parser;

import java.util.HashSet;
import water.H2O;
import water.Iced;

/** A class holding parser-setup flags: kind of parser, field separator, column
 *  header labels, whether or not to allow single-quotes to quote, number of
 *  columns discovered.
 */
class ParserSetup extends Iced {
                boolean _isValid;   // The initial parse is sane
  private final long _invalidLines; // Number of broken/invalid lines found
  private final String[] _errors;   // A collection of error messages, but still could be a valid parse setup
  // Parse Flavor
          final ParserType _pType; // CSV, XLS, XSLX, SVMLight, Auto
  static  final byte AUTO_SEP = -1;
          final byte _sep; // Field separator, usually comma ',' or TAB or space ' '
          final int _ncols;     // Columns to parse
  // Whether or not single-quotes quote a field.  E.g. how do we parse:
  // raw data:  123,'Mally,456,O'Mally
  // singleQuotes==True  ==> 2 columns: 123  and  Mally,456,OMally
  // singleQuotes==False ==> 4 columns: 123  and  'Mally  and  456  and  O'Mally
          final boolean _singleQuotes;
          final String[] _columnNames;
  private       String[][] _data;   // Preview data; a few lines and columns of varying length

  ParserSetup( boolean isValid, long invalidLines, String[] errors, ParserType t, byte sep, int ncols, boolean singleQuotes, String[] columnNames ) {
    _isValid = isValid;
    _invalidLines = invalidLines;
    _errors = errors;
    _pType = t;
    _sep = sep;
    _ncols = ncols;
    _singleQuotes = singleQuotes;
    _columnNames = columnNames;
  }

  // Invalid setup based on a prior valid one
  ParserSetup(ParserSetup ps, String err) {
    this(false,ps._invalidLines,new String[]{err},ps._pType,ps._sep,ps._ncols,ps._singleQuotes,ps._columnNames);
  }

  // Got parse errors?
  final boolean hasErrors() { return _errors != null && _errors.length > 0; }
  final boolean hasHeaders() { return _columnNames != null; }

  Parser parser() {
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
  public String parseStatusString() {
    return "Parser setup "+
      (_isValid ? (hasErrors() ? "appears to work with some errors" : "working fine") : "appears to be broken")+
      ", got "+toString();
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

  // Guess everything from a single pile-o-bits.  Used in tests, or in initial
  // parser inspections when the user has not told us anything about separators
  // or headers.
  public static ParserSetup guessSetup( byte[] bits, int checkHeader ) { return guessSetup(bits, ParserType.AUTO, AUTO_SEP, -1, false, checkHeader, null); }

  private static final ParserType guessTypeOrder[] = {ParserType.XLS,ParserType.XLSX,ParserType.SVMLight,ParserType.CSV};
  public static ParserSetup guessSetup( byte[] bits, ParserType pType, byte sep, int ncols, boolean singleQuotes, int checkHeader, String[] columnNames ) {
    switch( pType ) {
    case CSV:      return      CsvParser.CSVguessSetup(bits,sep,ncols,singleQuotes,checkHeader,columnNames);
    case SVMLight: return SVMLightParser.   guessSetup(bits);
    case XLS:      return      XlsParser.   guessSetup(bits);
    case AUTO:
      for( ParserType pType2 : guessTypeOrder ) {
        try {
          ParserSetup ps = guessSetup(bits,pType2,sep,ncols,singleQuotes,checkHeader,columnNames);
          if( ps != null && ps._isValid ) return ps;
        } catch( Throwable ignore ) { /*ignore failed parse attempt*/ }
      }
    }
    return new ParserSetup( false, 0, new String[]{"Cannot determine file type"}, pType, sep, ncols, singleQuotes, columnNames );
  }

  // Guess a local setup that is compatible to the given global (this) setup.
  // If they are not compatible, there will be _errors set.
  ParserSetup guessSetup( byte[] bits ) {
    assert _isValid;
    ParserSetup ps = guessSetup(bits, _pType, _sep, _ncols, _singleQuotes, 0/*guess header*/, _columnNames);
    if( !ps._isValid ) return ps; // Already invalid
    if( _pType != ps._pType ||
        (_pType == ParserType.CSV && (_sep != ps._sep && _ncols != ps._ncols)) )
      return new ParserSetup(ps,"Conflicting file layouts, expecting: "+this+" but found "+ps+"\n");
    return ps;
  }
}
