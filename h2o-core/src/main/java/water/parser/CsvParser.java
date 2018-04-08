package water.parser;

import org.apache.commons.lang.math.NumberUtils;
import water.Key;
import water.fvec.FileVec;
import water.fvec.Vec;
import water.parser.csv.reader.RowReader;
import water.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import static water.parser.DefaultParserProviders.CSV_INFO;

class CsvParser extends Parser {
  private static final byte GUESS_SEP = ParseSetup.GUESS_SEP;
  private static final int NO_HEADER = ParseSetup.NO_HEADER;
  private static final int GUESS_HEADER = ParseSetup.GUESS_HEADER;
  private static final int HAS_HEADER = ParseSetup.HAS_HEADER;

  CsvParser( ParseSetup ps, Key jobKey ) { super(ps, jobKey); }

  // Parse this one Chunk (in parallel with other Chunks)
  @SuppressWarnings("fallthrough")
  @Override public ParseWriter parseChunk(int cidx, final ParseReader din, final ParseWriter dout) {
    if (din.getChunkData(cidx) == null) return dout;
    byte[] bits = din.getChunkData(cidx);
    System.out.println("CIDX: " + cidx + " bits: " + bits.length);
    RowReader rowReader = new RowReader(din,cidx, (char) _setup._separator, (char) (_setup._single_quotes ? CHAR_SINGLE_QUOTE : CHAR_DOUBLE_QUOTE));
    boolean headerSkipped = false;

    try {
      int rowNum = 0;

      while (!rowReader.isFinished() && !rowReader.isChunkOverflow()) {
        RowReader.Line line = rowReader.readLine();


        if (cidx == 0 && _setup._check_header == ParseSetup.HAS_HEADER && !headerSkipped) {
          headerSkipped = true;
          continue;
        }
        dout.newLine();
        int i = 0;
        for (String cell : line.getFields()) {

          if (_setup.isNA(i, new BufferedString(cell))) {
            dout.addNAs(i++, rowNum);
            continue;
          }

          // This way of parsing is slow
          try {
            double d = Double.valueOf(cell);
            dout.addNumCol(i++, d);
            continue;
          } catch (NumberFormatException e) {
          }

          dout.addStrCol(i++, new BufferedString(cell));
        }
        rowNum++;
      }
      System.out.println("Number of rows CIDX " + cidx + " is " + rowNum);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return dout;
  }

  @Override protected int fileHasHeader(byte[] bits, ParseSetup ps) {
    boolean hasHdr = true;
    String[] lines = getFirstLines(bits, ps._single_quotes);
    if (lines != null && lines.length > 0) {
      String[] firstLine = determineTokens(lines[0], _setup._separator, _setup._single_quotes);
      if (_setup._column_names != null) {
        for (int i = 0; hasHdr && i < firstLine.length; ++i)
          hasHdr = (_setup._column_names[i] == firstLine[i]) || (_setup._column_names[i] != null && _setup._column_names[i].equalsIgnoreCase(firstLine[i]));
      } else { // declared to have header, but no column names provided, assume header exist in all files
        _setup._column_names = firstLine;
      }
    } // else FIXME Throw exception
    return hasHdr ? ParseSetup.HAS_HEADER: ParseSetup.NO_HEADER;
    // consider making insensitive to quotes
  }

  // ==========================================================================
  /** Separators recognized by the CSV parser.  You can add new separators to
   *  this list and the parser will automatically attempt to recognize them.
   *  In case of doubt the separators are listed in descending order of
   *  probability, with space being the last one - space must always be the
   *  last one as it is used if all other fails because multiple spaces can be
   *  used as a single separator.
   */
  static final byte HIVE_SEP = 0x1; // '^A',  Hive table column separator
  private static byte[] separators = new byte[] { HIVE_SEP, ',', ';', '|', '\t',  ' '/*space is last in this list, because we allow multiple spaces*/ };

  /** Dermines the number of separators in given line.  Correctly handles quoted tokens. */
  private static int[] determineSeparatorCounts(String from, byte singleQuote) {
    int[] result = new int[separators.length];
    byte[] bits = StringUtils.bytesOf(from);
    boolean inQuote = false;
    for( byte c : bits ) {
      if( (c == singleQuote) || (c == CsvParser.CHAR_DOUBLE_QUOTE) )
        inQuote ^= true;
      if( !inQuote || c == HIVE_SEP )
        for( int i = 0; i < separators.length; ++i )
          if( c == separators[i] )
            ++result[i];
    }
    return result;
  }

  /** Determines the tokens that are inside a line and returns them as strings
   *  in an array.  Assumes the given separator.
   */
  public static String[] determineTokens(String from, byte separator, boolean singleQuotes) {
    final byte singleQuote = singleQuotes ? CsvParser.CHAR_SINGLE_QUOTE : -1;
    return determineTokens(from, separator, singleQuote);
  }
  public static String[] determineTokens(String from, byte separator, byte singleQuote) {
    ArrayList<String> tokens = new ArrayList<>();
    byte[] bits = StringUtils.bytesOf(from);
    int offset = 0;
    int quotes = 0;
    while (offset < bits.length) {
      while ((offset < bits.length) && (bits[offset] == CsvParser.CHAR_SPACE)) ++offset; // skip first whitespace
      if(offset == bits.length)break;
      final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      byte c = bits[offset];
      if ((c == CsvParser.CHAR_DOUBLE_QUOTE) || (c == singleQuote)) {
        quotes = c;
        ++offset;
      }
      while (offset < bits.length) {
        c = bits[offset];
        if ((c == quotes)) {
          ++offset;
          if ((offset < bits.length) && (bits[offset] == c)) {
            byteArrayOutputStream.write(c);
            ++offset;
            continue;
          }
          quotes = 0;
        } else if( quotes == 0 && ((c == separator) || CsvParser.isEOL(c)) ) {
          break;
        } else {
          byteArrayOutputStream.write(c);
          ++offset;
        }
      }
      c = (offset == bits.length) ? CsvParser.CHAR_LF : bits[offset];
      tokens.add(byteArrayOutputStream.toString());
      if( CsvParser.isEOL(c) || (offset == bits.length) )
        break;
      if (c != separator)
        return new String[0]; // an error
      ++offset;               // Skip separator
    }
    // If we have trailing empty columns (split by separators) such as ",,\n"
    // then we did not add the final (empty) column, so the column count will
    // be down by 1.  Add an extra empty column here
    if( bits.length > 0 && bits[bits.length-1] == separator  && bits[bits.length-1] != CsvParser.CHAR_SPACE)
      tokens.add("");
    return tokens.toArray(new String[tokens.size()]);
  }


  public static byte guessSeparator(String l1, String l2, boolean singleQuotes) {
    final byte singleQuote = singleQuotes ? CsvParser.CHAR_SINGLE_QUOTE : -1;
    int[] s1 = determineSeparatorCounts(l1, singleQuote);
    int[] s2 = determineSeparatorCounts(l2, singleQuote);
    // Now we have the counts - if both lines have the same number of
    // separators the we assume it is the separator.  Separators are ordered by
    // their likelyhoods.
    int max = 0;
    for( int i = 0; i < s1.length; ++i ) {
      if( s1[i] == 0 ) continue;   // Separator does not appear; ignore it
      if( s1[max] < s1[i] ) max=i; // Largest count sep on 1st line
      if( s1[i] == s2[i] && s1[i] >= s1[max]>>1 ) {  // Sep counts are equal?  And at nearly as large as the larger header sep?
        try {
          String[] t1 = determineTokens(l1, separators[i], singleQuote);
          String[] t2 = determineTokens(l2, separators[i], singleQuote);
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
      String[] t1 = determineTokens(l1, separators[max], singleQuote);
      String[] t2 = determineTokens(l2, separators[max], singleQuote);
      if( t1.length == s1[max]+1 && t2.length == s2[max]+1 )
        return separators[max];
    }

    return GUESS_SEP;
  }

  // Guess number of columns
  public static int guessNcols( String[] columnNames, String[][] data ) {
    if( columnNames != null ) return columnNames.length;
    int longest = 0;            // Longest line
    for( String[] s : data ) if( s.length > longest ) longest = s.length;
    if( longest == data[0].length ) 
      return longest; // 1st line is longer than all the rest; take it

    // we don't have lines of same length, pick the most common length
    int lengths[] = new int[longest+1];
    for( String[] s : data ) lengths[s.length]++;
    int maxCnt = 0;             // Most common line length
    for( int i=0; i<=longest; i++ ) if( lengths[i] > lengths[maxCnt] ) maxCnt = i;
    return maxCnt;
  }

  /** Determines the CSV parser setup from the first few lines.  Also parses
   *  the next few lines, tossing out comments and blank lines.
   *
   *  If the separator is GUESS_SEP, then it is guessed by looking at tokenization
   *  and column count of the first few lines.
   *
   *  If ncols is -1, then it is guessed similarly to the separator.
   *
   *  singleQuotes is honored in all cases (and not guessed).
   *
   */
  static ParseSetup guessSetup(byte[] bits, byte sep, int ncols, boolean singleQuotes, int checkHeader, String[] columnNames, byte[] columnTypes, String[][] naStrings) {
    int lastNewline = bits.length-1;
    while(lastNewline > 0 && !CsvParser.isEOL(bits[lastNewline]))lastNewline--;
    if(lastNewline > 0) bits = Arrays.copyOf(bits,lastNewline+1);
    String[] lines = getFirstLines(bits, singleQuotes);
    if(lines.length==0 )
      throw new ParseDataset.H2OParseException("No data!");

    // Guess the separator, columns, & header
    String[] labels;
    final String[][] data = new String[lines.length][];
    if( lines.length == 1 ) {       // Ummm??? Only 1 line?
      if( sep == GUESS_SEP) {
        if (lines[0].split(",").length > 1) sep = (byte) ',';
        else if (lines[0].split(" ").length > 1) sep = ' ';
        else { //one item, guess type
          data[0] = new String[]{lines[0]};
          byte[] ctypes = new byte[1];
          String[][] domains = new String[1][];
          if (NumberUtils.isNumber(data[0][0])) {
            ctypes[0] = Vec.T_NUM;
          } else { // non-numeric
            BufferedString str = new BufferedString(data[0][0]);
            if (ParseTime.isTime(str))
              ctypes[0] = Vec.T_TIME;
            else if (ParseUUID.isUUID(str))
                ctypes[0] = Vec.T_UUID;
            else { // give up and guess categorical
                ctypes[0] = Vec.T_CAT;
                domains[0] = new String[]{data[0][0]};
            }
          }
          //FIXME should set warning message and let fall through
          return new ParseSetup(CSV_INFO, GUESS_SEP, singleQuotes, checkHeader, 1, null, ctypes, domains, naStrings, data, new ParseWriter.ParseErr[0],FileVec.DFLT_CHUNK_SIZE);
        }
      }
      data[0] = determineTokens(lines[0], sep, singleQuotes);
      ncols = (ncols > 0) ? ncols : data[0].length;
      if( checkHeader == GUESS_HEADER) {
        if (ParseSetup.allStrings(data[0]) && !data[0][0].isEmpty()) {
          labels = data[0];
          checkHeader = HAS_HEADER;
        } else {
          labels = null;
          checkHeader = NO_HEADER;
        }
      }
      else if( checkHeader == HAS_HEADER ) labels = data[0];
      else labels = null;
    } else {                    // 2 or more lines

      // First guess the field separator by counting occurrences in first few lines
      if( sep == GUESS_SEP) {   // first guess the separator
        sep = guessSeparator(lines[0], lines[1], singleQuotes);
        if( sep == GUESS_SEP && lines.length > 2 ) {
          sep = guessSeparator(lines[1], lines[2], singleQuotes);
          if( sep == GUESS_SEP) sep = guessSeparator(lines[0], lines[2], singleQuotes);
        }
        if( sep == GUESS_SEP) sep = (byte)' '; // Bail out, go for space
      }

      // Tokenize the first few lines using the separator
      for( int i = 0; i < lines.length; ++i )
        data[i] = determineTokens(lines[i], sep, singleQuotes );
      // guess columns from tokenization
      ncols = guessNcols(columnNames,data);

      // Asked to check for a header, so see if 1st line looks header-ish
      if( checkHeader == HAS_HEADER
        || ( checkHeader == GUESS_HEADER && ParseSetup.hasHeader(data[0], data[1]))) {
        checkHeader = HAS_HEADER;
        labels = data[0];
      } else {
        checkHeader = NO_HEADER;
        labels = columnNames;
      }

      // See if compatible headers
      if( columnNames != null && labels != null ) {
        if( labels.length != columnNames.length )
          throw new ParseDataset.H2OParseException("Already have "+columnNames.length+" column labels, but found "+labels.length+" in this file");
        else {
          for( int i = 0; i < labels.length; ++i )
            if( !labels[i].equalsIgnoreCase(columnNames[i]) ) {
              throw new ParseDataset.H2OParseException("Column "+(i+1)+" label '"+labels[i]+"' does not match '"+columnNames[i]+"'");
            }
          labels = columnNames; // Keep prior case & count in any case
        }
      }
    }

    // Assemble the setup understood so far
    ParseSetup resSetup = new ParseSetup(CSV_INFO, sep, singleQuotes, checkHeader, ncols, labels, null, null /*domains*/, naStrings, data);

    // now guess the types
    if (columnTypes == null || ncols != columnTypes.length) {
      int i = bits.length-1;
      for(; i > 0; --i)
        if(bits[i] == '\n') break;
      if(i > 0) bits = Arrays.copyOf(bits,i); // stop at the last full line
      CsvParser p = new CsvParser(resSetup, null);
      PreviewParseWriter dout = new PreviewParseWriter(resSetup._number_columns);
      try {
        p.parseChunk(0,new ByteAryData(bits,0), dout);
        resSetup._column_previews = dout;
        resSetup.addErrs(dout._errs);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    } else {
      // If user sets column type as unknown/bad, guess numeric.
      for(int i=0; i < columnTypes.length; i++) if (columnTypes[i] == Vec.T_BAD) columnTypes[i] = Vec.T_NUM;
      resSetup._column_types = columnTypes;
      resSetup._na_strings = null;
    }

    // Return the final setup
    return resSetup;
  }

  private static String[] getFirstLines(byte[] bits, boolean singleQuotes) {
    // Parse up to 10 lines (skipping hash-comments & ARFF comments)
    String[] lines = new String[10]; // Parse 10 lines
    int nlines = 0;
    int offset = 0;
    boolean comment = false;
    while( offset < bits.length && nlines < lines.length ) {
      if (bits[offset] == HASHTAG) comment = true;
      int lineStart = offset;
      int quoteCount = 0;
      while (offset < bits.length) {
        if (!comment && (
            (!singleQuotes && bits[offset] == CHAR_DOUBLE_QUOTE)
            || (singleQuotes && bits[offset] == CHAR_SINGLE_QUOTE)))
          quoteCount++;
        if (CsvParser.isEOL(bits[offset]) && quoteCount % 2 == 0){
          comment = false;
          break;
        }
        ++offset;
      }
      int lineEnd = offset;
      ++offset;
      // For Windoze, skip a trailing LF after CR
      if( (offset < bits.length) && (bits[offset] == CsvParser.CHAR_LF)) ++offset;
      if( bits[lineStart] == '#') continue; // Ignore      comment lines
      if( bits[lineStart] == '%') continue; // Ignore ARFF comment lines
      if( bits[lineStart] == '@') continue; // Ignore ARFF lines
      if( lineEnd > lineStart ) {
        String str = new String(bits, lineStart,lineEnd-lineStart).trim();
        if( !str.isEmpty() ) lines[nlines++] = str;
      }
    }
    return Arrays.copyOf(lines, nlines);
  }

}
