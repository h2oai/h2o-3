package water.parser;

import org.apache.commons.lang.math.NumberUtils;
import water.Key;
import water.fvec.FileVec;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;

import static water.parser.DefaultParserProviders.CSV_INFO;

class CsvParser extends Parser {
  private static final byte GUESS_SEP = ParseSetup.GUESS_SEP;
  private static final int NO_HEADER = ParseSetup.NO_HEADER;
  private static final int GUESS_HEADER = ParseSetup.GUESS_HEADER;
  private static final int HAS_HEADER = ParseSetup.HAS_HEADER;
  private static final byte[] NON_DATA_LINE_MARKERS = {'#'};

  CsvParser( ParseSetup ps, Key jobKey ) { super(ps, jobKey); }

  protected byte[] nonDataLineMarkers() {
    return NON_DATA_LINE_MARKERS;
  }

  // Parse this one Chunk (in parallel with other Chunks)
  @SuppressWarnings("fallthrough")
  @Override public ParseWriter parseChunk(int cidx, final ParseReader din, final ParseWriter dout) {
    CharSkippingBufferedString str = new CharSkippingBufferedString();
    byte[] bits = din.getChunkData(cidx);
    if( bits == null ) return dout;
    int offset  = din.getChunkDataStart(cidx); // General cursor into the giant array of bytes
    final byte[] bits0 = bits;  // Bits for chunk0
    boolean firstChunk = true;  // Have not rolled into the 2nd chunk
    byte[] bits1 = null;        // Bits for chunk1, loaded lazily.
    int state;
    boolean isNa = false;
    boolean isAllASCII = true;
    // If handed a skipping offset, then it points just past the prior partial line.
    if( offset >= 0 ) state = WHITESPACE_BEFORE_TOKEN;
    else {
      offset = 0; // Else start skipping at the start
      // Starting state.  Are we skipping the first (partial) line, or not?  Skip
      // a header line, or a partial line if we're in the 2nd and later chunks.
      if (_setup._check_header == ParseSetup.HAS_HEADER || cidx > 0) state = SKIP_LINE;
      else state = POSSIBLE_EMPTY_LINE;
    }

    int quotes = 0;
    byte quoteCount = 0;
    long number = 0;
    int escaped = -1;
    int exp = 0;
    int sgnExp = 1;
    boolean decimal = false;
    int fractionDigits = 0;
    int tokenStart = 0; // used for numeric token to backtrace if not successful
    int parsedColumnCounter = 0;  // index into parsed columns only, exclude skipped columns.
    int colIdx = 0; // count each actual column in the dataset including the skipped columns
    byte c = bits[offset];
    // skip comments for the first chunk (or if not a chunk)
    byte[] nonDataLineMarkers = nonDataLineMarkers();
    if( cidx == 0 ) {
      while (ArrayUtils.contains(nonDataLineMarkers, c) || isEOL(c)) {
        while ((offset < bits.length) && (bits[offset] != CHAR_CR) && (bits[offset  ] != CHAR_LF)) {
//          System.out.print(String.format("%c",bits[offset]));
          ++offset;
        }
        if ((offset + 1 < bits.length) && (bits[offset] == CHAR_CR) && (bits[offset + 1] == CHAR_LF)) ++offset;
        ++offset;
//        System.out.println();
        if (offset >= bits.length)
          return dout;
        c = bits[offset];
      }
    }
    dout.newLine();

    final boolean forceable = dout instanceof FVecParseWriter && ((FVecParseWriter)dout)._ctypes != null && _setup._column_types != null;
    int colIndexNum = _keepColumns.length-1;

    if (_setup._parse_columns_indices==null) {  // _parse_columns_indices not properly set
      _setup.setParseColumnIndices(_setup.getNumberColumns(), _setup.getSkippedColumns());
    }
    int parseIndexNum = _setup._parse_columns_indices.length-1;
MAIN_LOOP:
    while (true) {
      final boolean forcedCategorical = forceable && colIdx < _setup._column_types.length &&
              _setup._column_types[_setup._parse_columns_indices[parsedColumnCounter]] == Vec.T_CAT;
      final boolean forcedString = forceable && colIdx  < _setup._column_types.length &&
              _setup._column_types[_setup._parse_columns_indices[parsedColumnCounter]] == Vec.T_STR;

      switch (state) {
        // ---------------------------------------------------------------------
        case SKIP_LINE:
          if (isEOL(c)) {
            state = EOL;
          } else {
            break;
          }
          continue MAIN_LOOP;
        // ---------------------------------------------------------------------
        case EXPECT_COND_LF:
          state = POSSIBLE_EMPTY_LINE;
          if (c == CHAR_LF)
            break;
          continue MAIN_LOOP;
        // ---------------------------------------------------------------------
        case POSSIBLE_ESCAPED_QUOTE:
          if (c == quotes) {
            quoteCount--;
            str.skipIndex(offset);
            state = STRING;
            break;
          } else if (quoteCount > 1) {
            state = STRING_END;
            str.removeChar();
            quoteCount = 0;
            continue MAIN_LOOP;
          } else {
            state = STRING;
          }

        case STRING:
          if (c == quotes) {
            if (quoteCount>1) {
              str.addChar();
              quoteCount--;
            }

            state = COND_QUOTE;
            continue MAIN_LOOP;
          }
          if ((!isEOL(c) || quoteCount == 1) && (c != CHAR_SEPARATOR)) {
            if (str.getBuffer() == null && isEOL(c)) str.set(bits, offset, 0);
            str.addChar();
            if ((c & 0x80) == 128) //value beyond std ASCII
              isAllASCII = false;
            break;
          }

          if(quoteCount == 1){
            str.addChar(); //Anything not enclosed properly by second quotes is considered to be part of the string
            break;
          }
          // fallthrough to STRING_END
        // ---------------------------------------------------------------------
        case STRING_END:
          if ((c != CHAR_SEPARATOR) && (c == CHAR_SPACE))
            break;
          // we have parsed the string categorical correctly
          if(str.isOverflown()){ // crossing chunk boundary
            assert str.getBuffer() != bits;
            str.addBuff(bits);
          }
          if( !isNa &&
              _setup.isNA(parsedColumnCounter, str.toBufferedString())) {
            isNa = true;
          }

          if (!isNa && (colIdx <= colIndexNum) && _keepColumns[colIdx]) {
            dout.addStrCol(parsedColumnCounter, str.toBufferedString());
            if (!isAllASCII)
              dout.setIsAllASCII(parsedColumnCounter, isAllASCII);
          } else {
            if ((colIdx <= colIndexNum) && _keepColumns[colIdx])
              dout.addInvalidCol(parsedColumnCounter);
            isNa = false;
          }
          str.set(null, 0, 0);
          quotes = 0;
          isAllASCII = true;
          if ((colIdx <= colIndexNum) &&  _keepColumns[colIdx++] && (parsedColumnCounter < parseIndexNum)) // only increment if not at the end
            parsedColumnCounter++;
          state = SEPARATOR_OR_EOL;
          // fallthrough to SEPARATOR_OR_EOL
        // ---------------------------------------------------------------------
        case SEPARATOR_OR_EOL:
          if (c == CHAR_SEPARATOR) {
            state = WHITESPACE_BEFORE_TOKEN;
            break;
          }
          if (c==CHAR_SPACE)
            break;
          // fallthrough to EOL
        // ---------------------------------------------------------------------
        case EOL:
          if (quoteCount == 1) { //There may be a new line character inside quotes
            state = STRING;
            continue MAIN_LOOP;
          } else if (quoteCount > 2) {
            String err = "Unmatched quote char " + ((char) quotes);
            dout.invalidLine(new ParseWriter.ParseErr(err, cidx, dout.lineNum(), offset + din.getGlobalByteOffset()));
            parsedColumnCounter =0;
            colIdx=0;
            quotes = 0;
          } else if (colIdx != 0) {
            dout.newLine();
            parsedColumnCounter = 0;
            colIdx=0;
          }
          state = (c == CHAR_CR) ? EXPECT_COND_LF : POSSIBLE_EMPTY_LINE;
          if( !firstChunk )
            break MAIN_LOOP; // second chunk only does the first row
          break;
        // ---------------------------------------------------------------------
        case POSSIBLE_CURRENCY:
          if (((c >= '0') && (c <= '9')) || (c == '-') || (c == CHAR_DECIMAL_SEP) || (c == '+')) {
            state = TOKEN;
          } else {
            str.set(bits, offset - 1, 0);
            str.addChar();
            if (c == quotes) {
              state = COND_QUOTE;
              break;
            }
            if ((quotes != 0) || ((!isEOL(c) && (c != CHAR_SEPARATOR)))) {
              state = STRING;
            } else {
              state = STRING_END;
            }
          }
          continue MAIN_LOOP;
        // ---------------------------------------------------------------------
        case POSSIBLE_EMPTY_LINE:
          if (isEOL(c)) {
            if (c == CHAR_CR)
              state = EXPECT_COND_LF;
            break;
          }
          if (ArrayUtils.contains(nonDataLineMarkers, c)) {
            state = SKIP_LINE;
            break;
          }
          // fallthrough to WHITESPACE_BEFORE_TOKEN
        // ---------------------------------------------------------------------
        case WHITESPACE_BEFORE_TOKEN:
          if (c == CHAR_SPACE || (c == CHAR_TAB && CHAR_TAB!=CHAR_SEPARATOR)) {
              break;
          } else if (c == CHAR_SEPARATOR) {
            // we have empty token, store as NaN

            if ((colIdx <= colIndexNum) && _keepColumns[colIdx])
              dout.addInvalidCol(parsedColumnCounter);

            if ((colIdx <= colIndexNum) && _keepColumns[colIdx++] && (parsedColumnCounter < parseIndexNum)) {
              parsedColumnCounter++;
            }
            state = WHITESPACE_BEFORE_TOKEN;
            break;
          } else if (isEOL(c)) {
            if ((colIdx <= colIndexNum) && _keepColumns[colIdx])
              dout.addInvalidCol(parsedColumnCounter);
            state = EOL;
            continue MAIN_LOOP;
          }
          // fallthrough to COND_QUOTED_TOKEN
        // ---------------------------------------------------------------------
        case COND_QUOTED_TOKEN:
          state = TOKEN;
          if( CHAR_SEPARATOR!=HIVE_SEP && // Only allow quoting in CSV not Hive files
              ((_setup._single_quotes && c == CHAR_SINGLE_QUOTE) || (c == CHAR_DOUBLE_QUOTE))) {
            quotes = c;
            quoteCount++;
            break;
          }
          // fallthrough to TOKEN
        // ---------------------------------------------------------------------
        case TOKEN:
          if( dout.isString(parsedColumnCounter) ) { // Forced already to a string col?
            state = STRING; // Do not attempt a number parse, just do a string parse
            str.set(bits, offset, 0);
            continue MAIN_LOOP;
          } else if (((c >= '0') && (c <= '9')) || (c == '-') || (c == CHAR_DECIMAL_SEP) || (c == '+')) {
            state = NUMBER;
            number = 0;
            fractionDigits = 0;
            decimal = false;
            tokenStart = offset;
            if (c == '-') {
              exp = -1;
              break;
            } else if(c == '+'){
              exp = 1;
              break;
            } else {
              exp = 1;
            }
            // fallthrough
          } else if (c == '$') {
            state = POSSIBLE_CURRENCY;
            break;
          } else {
            state = STRING;
            str.set(bits, offset, 0);
            continue MAIN_LOOP;
          }
          // fallthrough to NUMBER
        // ---------------------------------------------------------------------
        case NUMBER:
          if ((c >= '0') && (c <= '9')) {
            if (number >= LARGEST_DIGIT_NUMBER)  state = NUMBER_SKIP;
            else  number = (number*10)+(c-'0');
            break;
          } else if (c == CHAR_DECIMAL_SEP) {
            state = NUMBER_FRACTION;
            fractionDigits = offset;
            decimal = true;
            break;
          } else if ((c == 'e') || (c == 'E')) {
            state = NUMBER_EXP_START;
            sgnExp = 1;
            break;
          }
          if (exp == -1) {
            number = -number;
          }
          exp = 0;
          // fallthrough to COND_QUOTED_NUMBER_END
        // ---------------------------------------------------------------------
        case COND_QUOTED_NUMBER_END:
          if ( c == quotes) {
            state = NUMBER_END;
            quotes = 0;
            quoteCount = 0;
            break;
          }
          // fallthrough NUMBER_END
        case NUMBER_END:

          // forced
          if (forcedString || forcedCategorical ) {
            state = STRING;
            offset = tokenStart - 1;
            str.set(bits, tokenStart, 0);
            break; // parse as String token now
          }

          if (c == CHAR_SEPARATOR && quotes == 0) {
            exp = exp - fractionDigits;
            if ((colIdx <= colIndexNum) && _keepColumns[colIdx])
              dout.addNumCol(parsedColumnCounter,number,exp);

            if ((colIdx <= colIndexNum) && _keepColumns[colIdx++] && (parsedColumnCounter < parseIndexNum))
              parsedColumnCounter++;
            // do separator state here too
            state = WHITESPACE_BEFORE_TOKEN;
            break;
          } else if (isEOL(c)) {
            exp = exp - fractionDigits;
            if ((colIdx <= colIndexNum) && _keepColumns[colIdx])
              dout.addNumCol(parsedColumnCounter,number,exp);
            // do EOL here for speedup reasons
            parsedColumnCounter =0;
            colIdx=0;
            dout.newLine();
            state = (c == CHAR_CR) ? EXPECT_COND_LF : POSSIBLE_EMPTY_LINE;
            if( !firstChunk )
              break MAIN_LOOP; // second chunk only does the first row
            break;
          } else if ((c == '%')) {
            state = NUMBER_END;
            exp -= 2;
            break;
          } else if ((c != CHAR_SEPARATOR) && ((c == CHAR_SPACE) || (c == CHAR_TAB))) {
            state = NUMBER_END;
            break;
          } else {
            state = STRING;
            offset = tokenStart-1;
            str.set(bits, tokenStart, 0);
            break; // parse as String token now
          }
        // ---------------------------------------------------------------------
        case NUMBER_SKIP:
          if ((c >= '0') && (c <= '9')) {
            exp++;
            break;
          } else if (c == CHAR_DECIMAL_SEP) {
            state = NUMBER_SKIP_NO_DOT;
            break;
          } else if ((c == 'e') || (c == 'E')) {
            state = NUMBER_EXP_START;
            sgnExp = 1;
            break;
          }
          state = COND_QUOTED_NUMBER_END;
          continue MAIN_LOOP;
        // ---------------------------------------------------------------------
        case NUMBER_SKIP_NO_DOT:
          if ((c >= '0') && (c <= '9')) {
            break;
          } else if ((c == 'e') || (c == 'E')) {
            state = NUMBER_EXP_START;
            sgnExp = 1;
            break;
          }
          state = COND_QUOTED_NUMBER_END;
          continue MAIN_LOOP;
        // ---------------------------------------------------------------------
        case NUMBER_FRACTION:
          if ((c >= '0') && (c <= '9')) {
            if (number >= LARGEST_DIGIT_NUMBER) {
              if (decimal)
                fractionDigits = offset - 1 - fractionDigits;
              if (exp == -1) number = -number;
              exp = 0;
              state = NUMBER_SKIP_NO_DOT;
            } else {
              number = (number*10)+(c-'0');
            }
            break;
          } else if ((c == 'e') || (c == 'E')) {
            if (decimal)
              fractionDigits = offset - 1 - fractionDigits;
            state = NUMBER_EXP_START;
            sgnExp = 1;
            break;
          }
          state = COND_QUOTED_NUMBER_END;
          if (decimal)
            fractionDigits = offset - fractionDigits-1;
          if (exp == -1) {
            number = -number;
          }
          exp = 0;
          continue MAIN_LOOP;
        // ---------------------------------------------------------------------
        case NUMBER_EXP_START:
          if (exp == -1) {
            number = -number;
          }
          exp = 0;
          if (c == '-') {
            sgnExp *= -1;
            break;
          } else if (c == '+'){
            break;
          }
          if ((c < '0') || (c > '9')){
            state = STRING;
            offset = tokenStart-1;
            str.set(bits, tokenStart, 0);
            break; // parse as String token now
          }
          state = NUMBER_EXP;  // fall through to NUMBER_EXP
        // ---------------------------------------------------------------------
        case NUMBER_EXP:
          if ((c >= '0') && (c <= '9')) {
            exp = (exp*10)+(c-'0');
            break;
          }
          exp *= sgnExp;
          state = COND_QUOTED_NUMBER_END;
          continue MAIN_LOOP;

        // ---------------------------------------------------------------------
        case COND_QUOTE:
          if (c == quotes) {
            str.addChar();
            quoteCount++;
            state = POSSIBLE_ESCAPED_QUOTE;
            break;
          } else {
            quotes = 0;
            quoteCount = 0;
            state = STRING_END;
            continue MAIN_LOOP;
          }
        // ---------------------------------------------------------------------
        default:
          assert (false) : " We have wrong state "+state;
      } // end NEXT_CHAR
//      System.out.print(String.format("%c",bits[offset]));
      ++offset; // do not need to adjust for offset increase here - the offset is set to tokenStart-1!
      if (offset < 0) {         // Offset is negative?
        assert !firstChunk;     // Caused by backing up from 2nd chunk into 1st chunk
        firstChunk = true;
        bits = bits0;
        offset += bits.length;
        str.set(bits, offset, 0);
      } else if (offset >= bits.length) { // Off end of 1st chunk?  Parse into 2nd chunk
        // Attempt to get more data.
        if( firstChunk && bits1 == null )
          bits1 = din.getChunkData(cidx+1);
        // if we can't get further we might have been the last one and we must
        // commit the latest guy if we had one.
        if( !firstChunk || bits1 == null ) { // No more data available or allowed
          // If we are mid-parse of something, act like we saw a LF to end the
          // current token.
          if(c == CHAR_LF || c == CHAR_CR) quoteCount++;
          if ((state != EXPECT_COND_LF) && (state != POSSIBLE_EMPTY_LINE)) {
            c = CHAR_LF;  continue; // MAIN_LOOP;
          }
          break; // MAIN_LOOP;      // Else we are just done
        }

        // Now parsing in the 2nd chunk.  All offsets relative to the 2nd chunk start.
        firstChunk = false;
        if (state == NUMBER_FRACTION)
          fractionDigits -= bits.length;
        offset -= bits.length;
        tokenStart -= bits.length;
        bits = bits1;           // Set main parsing loop bits
        if( bits[0] == CHAR_LF && state == EXPECT_COND_LF )
          break; // MAIN_LOOP; // when the first character we see is a line end
      }
      c = bits[offset];

    } // end MAIN_LOOP
    if (colIdx == 0)
      dout.rollbackLine();
    // If offset is still validly within the buffer, save it so the next pass
    // can start from there.
    if( offset+1 < bits.length ) {
      if( state == EXPECT_COND_LF && bits[offset+1] == CHAR_LF ) offset++;
      if( offset+1 < bits.length ) din.setChunkDataStart(cidx+1, offset+1 );
    }
    return dout;
  }

  @Override protected int fileHasHeader(byte[] bits, ParseSetup ps) {
    boolean hasHdr = true;
    String[] lines = getFirstLines(bits, ps._single_quotes, nonDataLineMarkers());
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
    String[] lines = getFirstLines(bits, singleQuotes, NON_DATA_LINE_MARKERS);
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

  private static String[] getFirstLines(byte[] bits, boolean singleQuotes, byte[] nonDataLineMarkers) {
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
      if (ArrayUtils.contains(nonDataLineMarkers, bits[lineStart])) continue;
      if( lineEnd > lineStart ) {
        String str = new String(bits, lineStart,lineEnd-lineStart).trim();
        if( !str.isEmpty() ) lines[nlines++] = str;
      }
    }
    return Arrays.copyOf(lines, nlines);
  }

}
