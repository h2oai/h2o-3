package water.parser;

import java.util.ArrayList;
import java.util.HashSet;
import water.H2O;
import water.Iced;

/** A class holding parser-setup flags: kind of parser, field separator, column
 *  header labels, whether or not to allow single-quotes to quote, number of
 *  columns discovered.
 */
class ParserSetup extends Iced {
  private       boolean _isValid;   // The initial parse is sane
  private final long _invalidLines; // Number of broken/invalid lines found
  private final String[] _errors;   // A collection of error messages
  // Parse Flavor
          final ParserType _pType; // CSV, XLS, XSLX, SVMLight, Auto
  private static byte AUTO_SEP = -1;
          final byte _sep; // Field separator, usually comma ',' or TAB or space ' '
          final int _ncols;     // Columns to parse
  // Whether or not single-quotes quote a field.  E.g. how do we parse:
  // raw data:  123,'Mally,456,O'Mally
  // singleQuotes==True  ==> 2 columns: 123  and  Mally,456,OMally
  // singleQuotes==False ==> 4 columns: 123  and  'Mally  and  456  and  O'Mally
          final boolean _singleQuotes;
  private final String[] _columnNames;
  private       String[][] _data;   // Preview data; a few lines and columns of varying length

  // The unspecified ParserSetup
  protected ParserSetup(ParserType t) {
    this(true,0,null,t,AUTO_SEP,0,false,null);
  }

  private ParserSetup( boolean isValid, long invalidLines, String[] errors, ParserType t, byte sep, int ncols, boolean singleQuotes, String[] columnNames ) {
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
  private ParserSetup(ParserSetup ps, String err) {
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

  // ==========================================================================
  /** Separators recognized by the parser.  You can add new separators to this
   *  list and the parser will automatically attempt to recognize them.  In
   *  case of doubt the separators are listed in descending order of
   *  probability, with space being the last one - space must always be the
   *  last one as it is used if all other fails because multiple spaces can be
   *  used as a single separator.
   */
  static final byte HIVE_SEP = 0x1; // '^A',  Hive table column separator
  private static byte[] separators = new byte[] { HIVE_SEP, ',', ';', '|', '\t',  ' '/*space is last in this list, because we allow multiple spaces*/ };

  /** Dermines the number of separators in given line.  Correctly handles quoted tokens. */
  private static int[] determineSeparatorCounts(String from, int single_quote) {
    int[] result = new int[separators.length];
    byte[] bits = from.getBytes();
    boolean in_quote = false;
    for( int j=0; j< bits.length; j++ ) {
      byte c = bits[j];
      if( (c == single_quote) || (c == CsvParser.CHAR_DOUBLE_QUOTE) )
        in_quote ^= true;
      if( !in_quote || c == HIVE_SEP )
        for( int i = 0; i < separators.length; ++i )
          if( c == separators[i] )
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
      while ((offset < bits.length) && (bits[offset] == CsvParser.CHAR_SPACE)) ++offset; // skip first whitespace
      if(offset == bits.length)break;
      StringBuilder t = new StringBuilder();
      byte c = bits[offset];
      if ((c == CsvParser.CHAR_DOUBLE_QUOTE) || (c == single_quote)) {
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
        } else if( quotes == 0 && ((c == separator) || CsvParser.isEOL(c)) ) {
          break;
        } else {
          t.append((char)c);
          ++offset;
        }
      }
      c = (offset == bits.length) ? CsvParser.CHAR_LF : bits[offset];
      tokens.add(t.toString());
      if( CsvParser.isEOL(c) || (offset == bits.length) )
        break;
      if (c != separator)
        return new String[0]; // an error
      ++offset;               // Skip separator
    }
    // If we have trailing empty columns (split by separators) such as ",,\n"
    // then we did not add the final (empty) column, so the column count will
    // be down by 1.  Add an extra empty column here
    if( bits[bits.length-1] == separator  && bits[bits.length-1] != CsvParser.CHAR_SPACE)
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

  private static byte guessSeparator(String l1, String l2, int single_quote) {
    int[] s1 = determineSeparatorCounts(l1, single_quote);
    int[] s2 = determineSeparatorCounts(l2, single_quote);
    // Now we have the counts - if both lines have the same number of
    // separators the we assume it is the separator.  Separators are ordered by
    // their likelyhoods.
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
        } catch( Exception ignore ) { /*pass; try another parse attempt*/ }
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

  // Guess number of columns
  private static int guessNcols( String[] columnNames, String[][] data ) {
    if( columnNames != null ) return data[0].length;
    int longest = 0;            // Longest line
    for( String[] s : data ) if( s.length > longest ) longest = s.length;
    if( longest == data[0].length ) 
      return longest; // 1st line is longer than all the rest; take it

    // we don't have lines of same length, pick the most common length
    int lengths[] = new int[longest];
    for( String[] s : data ) lengths[s.length]++;
    int maxCnt = 0;             // Most common line length
    for( int i=0; i<longest; i++ ) if( lengths[i] > lengths[maxCnt] ) maxCnt = i;
    return maxCnt;
  }

  // Guess everything from a single pile-o-bits.  Used in tests, or in initial
  // parser inspections when the user has not told us anything about separators
  // or headers.
  public static ParserSetup guessSetup( byte[] bits, int checkHeader ) { return guessSetup(bits, AUTO_SEP, -1, false, checkHeader, null); }

  /** Determines the CSV parser setup from the first few lines.  Also parses
   *  the next few lines, tossing out comments and blank lines.
   *
   *  If the separator is AUTO_SEP, then it is guessed by looking at tokenization 
   *  and column count of the first few lines.
   *
   *  If ncols is -1, then it is guessed similarly to the separator.
   *
   *  singleQuotes is honored in all cases (but not guessed).
   *
   *  checkHeader== -1 ==> 1st line is data, not header
   *  checkHeader== +1 ==> 1st line is header, not data.  Error if not compatible with prior header
   *  checkHeader==  0 ==> Guess 1st line header, only if compatible with prior
   */
  public static ParserSetup guessSetup( byte[] bits, byte sep, int ncols, boolean singleQuotes, int checkHeader, String[] columnNames ) {

    // Parse up to 10 lines (skipping hash-comments & ARFF comments)
    String[] lines = new String[10]; // Parse 10 lines
    int nlines = 0;
    int offset = 0;
    while( offset < bits.length && nlines < lines.length ) {
      int lineStart = offset;
      while( offset < bits.length && !CsvParser.isEOL(bits[offset]) ) ++offset;
      int lineEnd = offset;
      ++offset;
      // For Windoze, skip a trailing LF after CR
      if( (offset < bits.length) && (bits[offset] == CsvParser.CHAR_LF)) ++offset;
      if( bits[lineStart] == '#') continue; // Ignore      comment lines
      if( bits[lineStart] == '@') continue; // Ignore ARFF comment lines
      if( lineEnd > lineStart ) {
        String str = new String(bits, lineStart,lineEnd-lineStart).trim();
        if( !str.isEmpty() ) lines[nlines++] = str;
      }
    }
    if( nlines==0 )
      return new ParserSetup(false,0,new String[]{"No data!"},ParserType.AUTO,AUTO_SEP,0,false,null);

    // Guess the separator, columns, & header
    ArrayList<String> errors = new ArrayList<String>();
    String[] labels = null;
    final byte single_quote = singleQuotes ? CsvParser.CHAR_SINGLE_QUOTE : -1;
    final String[][] data = new String[nlines][];
    if( nlines == 1 ) {       // Ummm??? Only 1 line?
    //  if( sep == AUTO_SEP ) {
    //    if(lines.get(0).split(",").length > 2)
    //      sep = (byte)',';
    //    else if(lines.get(0).split(" ").length > 2)
    //      sep = ' ';
    //    else {
    //      data[0] = new String[]{lines.get(0)};
    //      return new PSetupGuess(new ParserSetup(ParserType.CSV,CsvParser.AUTO_SEP,1,false,null,setup._singleQuotes),lines.size(),0,data,false,new String[]{"Failed to guess separator."});
    //    }
    //  }
    //  if(lines.size() == 1)
    //    data[0] = determineTokens(lines.get(0), sep, single_quote);
    //  ncols = (setup._ncols > 0)?setup._ncols:data[0].length;
    //  hasHeader = (checkHeader && allStrings(data[0])) || setup._columnNames!=null; 
      throw H2O.unimpl();
    } else {                    // 2 or more lines

      // First guess the field separator by counting occurrences in first few lines
      if( sep == AUTO_SEP ) {   // first guess the separator
        sep = guessSeparator(lines[0], lines[1], single_quote);
        if( sep == AUTO_SEP && nlines > 2 ) {
          if( sep == AUTO_SEP ) sep = guessSeparator(lines[1], lines[2], single_quote);
          if( sep == AUTO_SEP ) sep = guessSeparator(lines[0], lines[2], single_quote);
        }
        if( sep == AUTO_SEP ) sep = (byte)' '; // Bail out, go for space
      }

      // Tokeninze the first few lines using the separator
      for( int i = 0; i < nlines; ++i )
        data[i] = determineTokens(lines[i], sep, single_quote );
      // guess columns from tokenization
      if( ncols == -1 ) ncols = guessNcols(columnNames,data);

      // Asked to check for a header, so see if 1st line looks header-ish
      if( checkHeader == 0 ) {  // Guess
        labels = hasHeader(data[0],data[1]) && (data[0].length == ncols) ? data[0] : null;
      } else if( checkHeader == 1 ) { // Told: take 1st line
        labels = data[0];
      } else {                  // Told: no headers
        labels = null;
      }
      
      // See if compatible headers
      if( columnNames != null && labels != null ) {
        if( labels.length != columnNames.length )
          errors.add("Already have "+columnNames.length+" column labels, but found "+labels.length+" in this file");
        else {
          for( int i = 0; i < labels.length; ++i )
            if( !labels[i].equalsIgnoreCase(columnNames[i]) ) {
              errors.add("Column "+(i+1)+" label '"+labels[i]+"' does not match '"+columnNames[i]+"'");
              break;
            }
          labels = columnNames; // Keep prior case & count in any case
        }
      }
    }

    // Count broken lines; gather error messages
    int ilines = 0;
    for( int i = 0; i < data.length; ++i ) {
      if( data[i].length != ncols ) {
        errors.add("error at line " + i + " : incompatible line length. Got " + data[i].length + " columns.");
        ++ilines;
      }
    }
    String[] err = null;
    if( !errors.isEmpty() )
      errors.toArray(err = new String[errors.size()]);

    // Return the final setup
    return new ParserSetup( true, ilines, err, ParserType.CSV, sep, ncols, singleQuotes, labels );
  }

  // Guess a local setup that is compatible to the given global (this) setup.
  // If they are not compatible, there will be _errors set.
  public ParserSetup guessSetup( byte[] bits ) {
    ParserSetup ps = guessSetup(bits, _sep, _ncols, _singleQuotes, 0/*guess header*/, _columnNames);
    if( ps._errors != null ) return ps; // Already dead
    if( _pType != ps._pType ||
        (_pType == ParserType.CSV && (_sep != ps._sep && _ncols != ps._ncols)) )
      return new ParserSetup(ps,"Conflicting file layouts, expecting: "+this+" but found "+ps+"\n");
    return ps;
  }
}
