package water.parser;

import java.util.HashSet;
import water.H2O;
import water.Iced;

/** A class holding parser-setup flags: kind of parser, field separator, column
 *  header labels, whether or not to allow single-quotes to quote, number of
 *  columns discovered.
 */
class ParserSetup extends Iced {
  private static byte AUTO_SEP = -1;
          final ParserType _pType;
  private final byte _separator;
  private final boolean _header;
  // Whether or not single-quotes quote a field.  E.g. how do we parse:
  // raw data:  123,'Mally,456,O'Mally
  // singleQuotes==True  ==> 2 columns: 123  and  Mally,456,OMally
  // singleQuotes==False ==> 4 columns: 123  and  'Mally  and  456  and  O'Mally
  private final boolean _singleQuotes;
  private final String [] _columnNames;
          final int _ncols;

  protected ParserSetup(ParserType t) {
    this(t,AUTO_SEP,0,false,null,false);
  }

  private ParserSetup(ParserType t, byte sep, boolean header) {
    _pType = t;
    _separator = sep;
    _header = header;
    _columnNames = null;
    _ncols = 0;
    _singleQuotes = false;
  }

  private ParserSetup(ParserType t, byte sep, int ncolumns, boolean header, String [] columnNames, boolean singleQuotes) {
    _pType = t;
    _separator = sep;
    _ncols = ncolumns;
    _header = header;
    _columnNames = columnNames;
    _singleQuotes = singleQuotes;
  }

  private boolean isSpecified(){
    return _pType != ParserType.AUTO && _separator != AUTO_SEP && (_header || _ncols > 0);
  }

  HashSet<String> checkDupColumnNames() {
    HashSet<String> uniqueNames = new HashSet<>();
    HashSet<String> conflictingNames = new HashSet<>();
    if(_header){
      for(String n:_columnNames){
        if(!uniqueNames.contains(n)){
          uniqueNames.add(n);
        } else {
          conflictingNames.add(n);
        }
      }
    }
    return conflictingNames;
  }
  boolean isCompatible( ParserSetup other ) {
    if( other == null || _pType != other._pType ) return false;
    return _pType != ParserType.CSV || (_separator == other._separator && _ncols == other._ncols);
  }

  private CustomParser makeParser() {
    switch(_pType) {
    case CSV:      return new CsvParser(this);
    case SVMLight: return new SVMLightParser(this);
    case XLS:      return new XlsParser(this);
    default:
      throw H2O.unimpl();
    }
  }
  @Override public String toString(){ return _pType.toString( _ncols, _separator ); }

  // Guess a setup from a single file of bits.
  static ParserSetup guessSetup( byte[] bits, boolean checkHeader ) {
    throw H2O.unimpl();
  }
}
