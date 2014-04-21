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


  // ==========================================================================
  /** Separators recognized by the parser.  You can add new separators to this
   *  list and the parser will automatically attempt to recognize them.  In
   *  case of doubt the separators are listed in descending order of
   *  probability, with space being the last one - space must always be the
   *  last one as it is used if all other fails because multiple spaces can be
   *  used as a single separator.
   */
  private static byte[] separators = new byte[] { HIVE_SEP/* '^A',  Hive table column separator */, ',', ';', '|', '\t',  ' '/*space is last in this list, because we allow multiple spaces*/ };

  /** Dermines the number of separators in given line.  Correctly handles quoted tokens. */
  private static int[] determineSeparatorCounts(String from, int single_quote) {
    int[] result = new int[separators.length];
    byte[] bits = from.getBytes();
    boolean in_quote = false;
    for( int j=0; j< bits.length; j++ ) {
      byte c = bits[j];
      if( (c == single_quote) || (c == CHAR_DOUBLE_QUOTE) )
        in_quote ^= true;
      if( !in_quote || c == HIVE_SEP )
        for( int i = 0; i < separators.length; ++i)
          if (c == separators[i])
            ++result[i];
    }
    return result;
  }

  /** Determines the tokens that are inside a line and returns them as strings
   *  in an array.  Assumes the given separator.
   */
  private static String[] determineTokens(String from, byte separator, int single_quote) {
    ArrayList<String> tokens = new ArrayList();
    byte[] bits = from.getBytes();
    int offset = 0;
    int quotes = 0;
    while (offset < bits.length) {
      while ((offset < bits.length) && (bits[offset] == CHAR_SPACE)) ++offset; // skip first whitespace
      if(offset == bits.length)break;
      StringBuilder t = new StringBuilder();
      byte c = bits[offset];
      if ((c == CHAR_DOUBLE_QUOTE) || (c == single_quote)) {
        quotes = c;
        ++offset;
      }
      while (offset < bits.length) {
        c = bits[offset];
        if ((c == quotes)) {
          ++offset;
          if ((offset < bits.length) && (bits[offset] == c)) {
            t.append((char)c);
            ++offset;
            continue;
          }
          quotes = 0;
        } else if ((quotes == 0) && ((c == separator) || (c == CHAR_CR) || (c == CHAR_LF))) {
          break;
        } else {
          t.append((char)c);
          ++offset;
        }
      }
      c = (offset == bits.length) ? CHAR_LF : bits[offset];
      tokens.add(t.toString());
      if ((c == CHAR_CR) || (c == CHAR_LF) || (offset == bits.length))
        break;
      if (c != separator)
        return new String[0]; // an error
      ++offset;               // Skip separator
    }
    // If we have trailing empty columns (split by seperators) such as ",,\n"
    // then we did not add the final (empty) column, so the column count will
    // be down by 1.  Add an extra empty column here
    if( bits[bits.length-1] == separator  && bits[bits.length-1] != CHAR_SPACE)
      tokens.add("");
    return tokens.toArray(new String[tokens.size()]);
  }

  private static boolean allStrings(String [] line){
    ValueString str = new ValueString();
    for( String s : line ) {
      try {
        Double.parseDouble(s);
        return false;       // Number in 1st row guesses: No Column Header
      } catch (NumberFormatException e) { /*Pass - determining if number is possible*/ }
      if( ParseTime.attemptTimeParse(str.setTo(s)) != Long.MIN_VALUE ) return false;
    }
    return true;
  }
  // simple heuristic to determine if we have headers:
  // return true iff the first line is all strings and second line has at least one number
  private static boolean hasHeader(String[] l1, String[] l2) {
    return allStrings(l1) && !allStrings(l2);
  }

  private static byte guessSeparator(String l1, String l2, int single_quote){
    int[] s1 = determineSeparatorCounts(l1, single_quote);
    int[] s2 = determineSeparatorCounts(l2, single_quote);
    // Now we have the counts - if both lines have the same number of separators
    // the we assume it is the separator.  Separators are ordered by their
    // likelyhoods.  
    int max = 0;
    for( int i = 0; i < s1.length; ++i ) {
      if( s1[i] == 0 ) continue;   // Separator does not appear; ignore it
      if( s1[max] < s1[i] ) max=i; // Largest count sep on 1st line
      if( s1[i] == s2[i] ) {       // Sep counts are equal?
        try {
          String[] t1 = determineTokens(l1, separators[i], single_quote);
          String[] t2 = determineTokens(l2, separators[i], single_quote);
          if( t1.length != s1[i]+1 || t2.length != s2[i]+1 )
            continue;           // Token parsing fails
          return separators[i];
        } catch (Exception e) { /*pass; try another parse attempt*/ }
      }
    }
    // No sep's appeared, or no sep's had equal counts on lines 1 & 2.  If no
    // separators have same counts, the largest one will be used as the default
    // one.  If there's no largest one, space will be used.
    if( s1[max]==0 ) max=separators.length-1; // Try last separator (space)
    if( s1[max]!=0 ) {
      String[] t1 = determineTokens(l1, separators[max], single_quote);
      String[] t2 = determineTokens(l2, separators[max], single_quote);
      if( t1.length == s1[max]+1 && t2.length == s2[max]+1 )
        return separators[max];
    }

    return AUTO_SEP;
  }


  private static int guessNcols(ParserSetup setup,String [][] data){
    int res = data[0].length;
    if(setup._header)return res;
    boolean samelen = true;     // True if all are same length
    boolean longest0 = true;    // True if no line is longer than 1st line
    for(String [] s:data) {
      samelen  &= (s.length == res);
      if( s.length > res ) longest0=false;
    }
    if(samelen)return res;      // All same length, take it
    if( longest0 ) return res;  // 1st line is longer than all the rest; take it

    // we don't have lines of same length, pick the most common length
    HashMap<Integer, Integer> lengths = new HashMap<Integer, Integer>();
    for(String [] s:data){
      if(!lengths.containsKey(s.length))lengths.put(s.length, 1);
      else
        lengths.put(s.length, lengths.get(s.length)+1);
    }
    int maxCnt = 0;
    for(Map.Entry<Integer, Integer> e:lengths.entrySet())
      if(e.getValue() > maxCnt){
        maxCnt = e.getValue();
        res = e.getKey();
      }
    return res;
  }

  /** Determines the CSV parser setup from the first two lines.  Also parses
   *  the next few lines, tossing out comments and blank lines.
   *
   *  A separator is given or it is selected if both two lines have the same ammount of them
   *  and the tokenization then returns same number of columns.
   */
  public static CustomParser.PSetupGuess guessSetup(byte[] bits) { return guessSetup(bits, new ParserSetup(ParserType.CSV),true); }
  public static CustomParser.PSetupGuess guessSetup(byte[] bits, ParserSetup setup){return guessSetup(bits,setup,false);}
  public static CustomParser.PSetupGuess guessSetup(byte[] bits, ParserSetup setup, boolean checkHeader) {
    ArrayList<String> lines = new ArrayList();
    int offset = 0;
    while (offset < bits.length && lines.size() < 10) {
      int lineStart = offset;
      while ((offset < bits.length) && (bits[offset] != CHAR_CR) && (bits[offset] != CHAR_LF)) ++offset;
      int lineEnd = offset;
      ++offset;
      if ((offset < bits.length) && (bits[offset] == CHAR_LF)) ++offset;
      if (bits[lineStart] == '#') continue; // Ignore      comment lines
      if (bits[lineStart] == '@') continue; // Ignore ARFF comment lines
      if (lineEnd>lineStart){
        String str = new String(bits, lineStart,lineEnd-lineStart).trim();
        if(!str.isEmpty())lines.add(str);
      }
    }
    if(lines.isEmpty())
      return new PSetupGuess(new ParserSetup(ParserType.AUTO,CsvParser.AUTO_SEP,0,false,null,setup._singleQuotes),0,0,null,false,new String[]{"No data!"});
    boolean hasHeader = false;
    final int single_quote = setup._singleQuotes ? CHAR_SINGLE_QUOTE : -1;
    byte sep = setup._separator;
    final String [][] data = new String[lines.size()][];
    int ncols;
    if( lines.size() < 2 ) {
      if(sep == AUTO_SEP){
        if(lines.get(0).split(",").length > 2)
          sep = (byte)',';
        else if(lines.get(0).split(" ").length > 2)
          sep = ' ';
        else {
          data[0] = new String[]{lines.get(0)};
          return new PSetupGuess(new ParserSetup(ParserType.CSV,CsvParser.AUTO_SEP,1,false,null,setup._singleQuotes),lines.size(),0,data,false,new String[]{"Failed to guess separator."});
        }
      }
      if(lines.size() == 1)
        data[0] = determineTokens(lines.get(0), sep, single_quote);
      ncols = (setup._ncols > 0)?setup._ncols:data[0].length;
      hasHeader = (checkHeader && allStrings(data[0])) || setup._header;
    } else {
      if(setup._separator == AUTO_SEP){ // first guess the separator
        sep = guessSeparator(lines.get(0), lines.get(1), single_quote);
        if(sep == AUTO_SEP && lines.size() > 2){
          if(sep == AUTO_SEP)sep = guessSeparator(lines.get(1), lines.get(2), single_quote);
          if(sep == AUTO_SEP)sep = guessSeparator(lines.get(0), lines.get(2), single_quote);
        }
        if(sep == AUTO_SEP)sep = (byte)' ';
      }
      for(int i = 0; i < lines.size(); ++i)
        data[i] = determineTokens(lines.get(i), sep, single_quote);
      // we do not have enough lines to decide
      ncols = (setup._ncols > 0)?setup._ncols:guessNcols(setup,data);
      if(checkHeader){
        assert !setup._header;
        assert setup._columnNames == null;
        hasHeader = hasHeader(data[0],data[1]) && (data[0].length == ncols);
      } else if(setup._header){
        if(setup._columnNames != null){ // we know what the header looks like, check if the current file has matching header
          hasHeader = data[0].length == setup._columnNames.length;
          for(int i = 0; hasHeader && i < data[0].length; ++i)
            hasHeader = data[0][i].equalsIgnoreCase(setup._columnNames[i]);
        } else // otherwise we're told to take the first line as header whatever it might be
          hasHeader = true;
      }
    }
    ParserSetup resSetup = new ParserSetup(ParserType.CSV, sep, ncols,hasHeader, hasHeader?data[0]:null,setup._singleQuotes);
    ArrayList<String> errors = new ArrayList<String>();
    int ilines = 0;
    for(int i = 0; i < data.length; ++i){
      if(data[i].length != resSetup._ncols){
        errors.add("error at line " + i + " : incompatible line length. Got " + data[i].length + " columns.");
        ++ilines;
      }
    }
    String [] err = null;
    if(!errors.isEmpty()){
      err = new String[errors.size()];
      errors.toArray(err);
    }
    return new PSetupGuess(resSetup,lines.size()-ilines,ilines,data,setup.isSpecified() || lines.size() > ilines, err);
  }

  @Override public boolean isCompatible(CustomParser p) {
    return (p instanceof CsvParser) && p._setup._separator == _setup._separator && p._setup._ncols == _setup._ncols;
  }
 }
