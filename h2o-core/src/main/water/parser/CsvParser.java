package water.parser;

import water.H2O;
import java.util.Arrays;

class CsvParser extends Parser {
  private static final byte CHAR_TAB = '\t';
  private static final byte CHAR_CR = 13;
          static final byte CHAR_LF = 10;
          static final byte CHAR_SPACE = ' ';
          static final byte CHAR_DOUBLE_QUOTE = '"';
          static final byte CHAR_SINGLE_QUOTE = '\'';

  public final byte CHAR_DECIMAL_SEPARATOR = '.';
  public final byte CHAR_SEPARATOR;

  // State for the Parser's FSA
  private static final byte SKIP_LINE = 0;
  private static final byte EXPECT_COND_LF = 1;
  private static final byte EOL = 2;
  private static final byte TOKEN = 3;
  private static final byte COND_QUOTED_TOKEN = 4;
  private static final byte NUMBER = 5;
  private static final byte NUMBER_SKIP = 6;
  private static final byte NUMBER_SKIP_NO_DOT = 7;
  private static final byte NUMBER_FRACTION = 8;
  private static final byte NUMBER_EXP = 9;
  private static final byte NUMBER_EXP_NEGATIVE = 10;
  private static final byte NUMBER_EXP_START = 11;
  private static final byte NUMBER_END = 12;
  private static final byte STRING = 13;
  private static final byte COND_QUOTE = 14;
  private static final byte SEPARATOR_OR_EOL = 15;
  private static final byte WHITESPACE_BEFORE_TOKEN = 16;
  private static final byte STRING_END = 17;
  private static final byte COND_QUOTED_NUMBER_END = 18;
  private static final byte POSSIBLE_EMPTY_LINE = 19;
  private static final byte POSSIBLE_CURRENCY = 20;

  private static final long LARGEST_DIGIT_NUMBER = 1000000000000000000L;

  CsvParser( ParserSetup ps ) { 
    super(ps); 
    CHAR_SEPARATOR = ps._sep;
  }

  protected static final boolean isWhitespace(byte c) { return (c == CHAR_SPACE) || (c == CHAR_TAB); }
  protected static final boolean isEOL(byte c) { return (c == CHAR_LF) || (c == CHAR_CR); }

  @Override boolean parallelParseSupported() { return true; }

  // Parse this one Chunk (in parallel with other Chunks)
  @SuppressWarnings("fallthrough")
  @Override public final DataOut parallelParse(int cidx, final Parser.DataIn din, final Parser.DataOut dout) {
    ValueString _str = new ValueString();
    byte[] bits = din.getChunkData(cidx);
    if( bits == null ) return dout;
    int offset  = din.getChunkDataStart(cidx); // General cursor into the giant array of bytes
    final byte[] bits0 = bits;  // Bits for chunk0
    boolean firstChunk = true;  // Have not rolled into the 2nd chunk
    byte[] bits1 = null;        // Bits for chunk1, loaded lazily.
    // Starting state.  Are we skipping the first (partial) line, or not?  Skip
    // a header line, or a partial line if we're in the 2nd and later chunks.
    int state = (_setup.hasHeaders() || cidx > 0) ? SKIP_LINE : WHITESPACE_BEFORE_TOKEN;
    // If handed a skipping offset, then it points just past the prior partial line.
    if( offset >= 0 ) state = WHITESPACE_BEFORE_TOKEN;
    else offset = 0; // Else start skipping at the start
    int quotes = 0;
    long number = 0;
    int exp = 0;
    int sgn_exp = 1;
    boolean decimal = false;
    int fractionDigits = 0;
    int numStart = 0;
    int tokenStart = 0; // used for numeric token to backtrace if not successful
    int colIdx = 0;
    byte c = bits[offset];
    // skip comments for the first chunk (or if not a chunk)
    if( cidx == 0 ) {
      while (c == '#' || c == '@'/*also treat as comments leading '@' from ARFF format*/) {
        while ((offset   < bits.length) && (bits[offset] != CHAR_CR) && (bits[offset  ] != CHAR_LF)) ++offset;
        if    ((offset+1 < bits.length) && (bits[offset] == CHAR_CR) && (bits[offset+1] == CHAR_LF)) ++offset;
        ++offset;
        if (offset >= bits.length)
          return dout;
        c = bits[offset];
      }
    }
    dout.newLine();

MAIN_LOOP:
    while (true) {
NEXT_CHAR:
      switch (state) {
        // ---------------------------------------------------------------------
        case SKIP_LINE:
          if (isEOL(c)) {
            state = EOL;
          } else {
            break NEXT_CHAR;
          }
          continue MAIN_LOOP;
        // ---------------------------------------------------------------------
        case EXPECT_COND_LF:
          state = POSSIBLE_EMPTY_LINE;
          if (c == CHAR_LF)
            break NEXT_CHAR;
          continue MAIN_LOOP;
        // ---------------------------------------------------------------------
        case STRING:
          if (c == quotes) {
            state = COND_QUOTE;
            break NEXT_CHAR;
          }
          if (!isEOL(c) && ((quotes != 0) || (c != CHAR_SEPARATOR))) {
            _str.addChar();
            break NEXT_CHAR;
          }
          // fallthrough to STRING_END
        // ---------------------------------------------------------------------
        case STRING_END:
          if ((c != CHAR_SEPARATOR) && (c == CHAR_SPACE))
            break NEXT_CHAR;
          // we have parsed the string enum correctly
          if((_str.get_off() + _str.get_length()) > _str.get_buf().length){ // crossing chunk boundary
            assert _str.get_buf() != bits;
            _str.addBuff(bits);
          }
          dout.addStrCol(colIdx, _str);
          _str.set(null, 0, 0);
          ++colIdx;
          state = SEPARATOR_OR_EOL;
          // fallthrough to SEPARATOR_OR_EOL
        // ---------------------------------------------------------------------
        case SEPARATOR_OR_EOL:
          if (c == CHAR_SEPARATOR) {
            state = WHITESPACE_BEFORE_TOKEN;
            break NEXT_CHAR;
          }
          if (c==CHAR_SPACE)
            break NEXT_CHAR;
          // fallthrough to EOL
        // ---------------------------------------------------------------------
        case EOL:
          if(quotes != 0){
            System.err.println("Unmatched quote char " + ((char)quotes) + " " + (((_str.get_length()+1) < offset && _str.get_off() > 0)?new String(Arrays.copyOfRange(bits,_str.get_off()-1,offset)):""));
            dout.invalidLine("Unmatched quote char " + ((char)quotes));
            colIdx = 0;
            quotes = 0;
          }else if (colIdx != 0) {
            dout.newLine();
            colIdx = 0;
          }
          state = (c == CHAR_CR) ? EXPECT_COND_LF : POSSIBLE_EMPTY_LINE;
          if( !firstChunk )
            break MAIN_LOOP; // second chunk only does the first row
          break NEXT_CHAR;
        // ---------------------------------------------------------------------
        case POSSIBLE_CURRENCY:
          if (((c >= '0') && (c <= '9')) || (c == '-') || (c == CHAR_DECIMAL_SEPARATOR) || (c == '+')) {
            state = TOKEN;
          } else {
            _str.set(bits,offset-1,0);
            _str.addChar();
            if (c == quotes) {
              state = COND_QUOTE;
              break NEXT_CHAR;
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
            break NEXT_CHAR;
          }
          state = WHITESPACE_BEFORE_TOKEN;
          // fallthrough to WHITESPACE_BEFORE_TOKEN
        // ---------------------------------------------------------------------
        case WHITESPACE_BEFORE_TOKEN:
          if (c == CHAR_SPACE || (c == CHAR_TAB && CHAR_TAB!=CHAR_SEPARATOR)) {
              break NEXT_CHAR;
          } else if (c == CHAR_SEPARATOR) {
            // we have empty token, store as NaN
            dout.addInvalidCol(colIdx++);
            break NEXT_CHAR;
          } else if (isEOL(c)) {
            dout.addInvalidCol(colIdx++);
            state = EOL;
            continue MAIN_LOOP;
          }
          // fallthrough to COND_QUOTED_TOKEN
        // ---------------------------------------------------------------------
        case COND_QUOTED_TOKEN:
          state = TOKEN;
          if( CHAR_SEPARATOR!=ParserSetup.HIVE_SEP && // Only allow quoting in CSV not Hive files
              ((_setup._singleQuotes && c == CHAR_SINGLE_QUOTE) || (c == CHAR_DOUBLE_QUOTE))) {
            assert (quotes == 0);
            quotes = c;
            break NEXT_CHAR;
          }
          // fallthrough to TOKEN
        // ---------------------------------------------------------------------
        case TOKEN:
          if( dout.isString(colIdx) ) { // Forced already to a string col?
            state = STRING; // Do not attempt a number parse, just do a string parse
            _str.set(bits, offset, 0);
            continue MAIN_LOOP;
          } else if (((c >= '0') && (c <= '9')) || (c == '-') || (c == CHAR_DECIMAL_SEPARATOR) || (c == '+')) {
            state = NUMBER;
            number = 0;
            fractionDigits = 0;
            decimal = false;
            numStart = offset;
            tokenStart = offset;
            if (c == '-') {
              exp = -1;
              ++numStart;
              break NEXT_CHAR;
            } else if(c == '+'){
              exp = 1;
              ++numStart;
              break NEXT_CHAR;
            } else {
              exp = 1;
            }
            // fallthrough
          } else if (c == '$') {
            state = POSSIBLE_CURRENCY;
            break NEXT_CHAR;
          } else {
            state = STRING;
            _str.set(bits, offset, 0);
            continue MAIN_LOOP;
          }
          // fallthrough to NUMBER
        // ---------------------------------------------------------------------
        case NUMBER:
          if ((c >= '0') && (c <= '9')) {
            number = (number*10)+(c-'0');
            if (number >= LARGEST_DIGIT_NUMBER)
              state = NUMBER_SKIP;
            break NEXT_CHAR;
          } else if (c == CHAR_DECIMAL_SEPARATOR) {
            ++numStart;
            state = NUMBER_FRACTION;
            fractionDigits = offset;
            decimal = true;
            break NEXT_CHAR;
          } else if ((c == 'e') || (c == 'E')) {
            ++numStart;
            state = NUMBER_EXP_START;
            sgn_exp = 1;
            break NEXT_CHAR;
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
            break NEXT_CHAR;
          }
          // fallthrough NUMBER_END
        case NUMBER_END:
          if (c == CHAR_SEPARATOR && quotes == 0) {
            exp = exp - fractionDigits;
            dout.addNumCol(colIdx,number,exp);
            ++colIdx;
            // do separator state here too
            state = WHITESPACE_BEFORE_TOKEN;
            break NEXT_CHAR;
          } else if (isEOL(c)) {
            exp = exp - fractionDigits;
            dout.addNumCol(colIdx,number,exp);
            // do EOL here for speedup reasons
            colIdx = 0;
            dout.newLine();
            state = (c == CHAR_CR) ? EXPECT_COND_LF : POSSIBLE_EMPTY_LINE;
            if( !firstChunk )
              break MAIN_LOOP; // second chunk only does the first row
            break NEXT_CHAR;
          } else if ((c == '%')) {
            state = NUMBER_END;
            exp -= 2;
            break NEXT_CHAR;
          } else if ((c != CHAR_SEPARATOR) && ((c == CHAR_SPACE) || (c == CHAR_TAB))) {
            state = NUMBER_END;
            break NEXT_CHAR;
          } else {
            state = STRING;
            offset = tokenStart-1;
            _str.set(bits,tokenStart,0);
            break NEXT_CHAR; // parse as String token now
          }
        // ---------------------------------------------------------------------
        case NUMBER_SKIP:
          ++numStart;
          if ((c >= '0') && (c <= '9')) {
            break NEXT_CHAR;
          } else if (c == CHAR_DECIMAL_SEPARATOR) {
            state = NUMBER_SKIP_NO_DOT;
            break NEXT_CHAR;
          } else if ((c == 'e') || (c == 'E')) {
            state = NUMBER_EXP_START;
            sgn_exp = 1;
            break NEXT_CHAR;
          }
          state = COND_QUOTED_NUMBER_END;
          continue MAIN_LOOP;
        // ---------------------------------------------------------------------
        case NUMBER_SKIP_NO_DOT:
          ++numStart;
          if ((c >= '0') && (c <= '9')) {
            break NEXT_CHAR;
          } else if ((c == 'e') || (c == 'E')) {
            state = NUMBER_EXP_START;
            sgn_exp = 1;
            break NEXT_CHAR;
          }
          state = COND_QUOTED_NUMBER_END;
          continue MAIN_LOOP;
        // ---------------------------------------------------------------------
        case NUMBER_FRACTION:
          if ((c >= '0') && (c <= '9')) {
            if (number >= LARGEST_DIGIT_NUMBER) {
              if (decimal)
                fractionDigits = offset - 1 - fractionDigits;
              state = NUMBER_SKIP;
            } else {
              number = (number*10)+(c-'0');
            }
            break NEXT_CHAR;
          } else if ((c == 'e') || (c == 'E')) {
            ++numStart;
            if (decimal)
              fractionDigits = offset - 1 - fractionDigits;
            state = NUMBER_EXP_START;
            sgn_exp = 1;
            break NEXT_CHAR;
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
            ++numStart;
            sgn_exp *= -1;
            break NEXT_CHAR;
          } else if (c == '+'){
            ++numStart;
            break NEXT_CHAR;
          }
          if ((c < '0') || (c > '9')){
            state = STRING;
            offset = tokenStart-1;
            _str.set(bits,tokenStart,0);
            break NEXT_CHAR; // parse as String token now
          }
          state = NUMBER_EXP;  // fall through to NUMBER_EXP
        // ---------------------------------------------------------------------
        case NUMBER_EXP:
          if ((c >= '0') && (c <= '9')) {
            ++numStart;
            exp = (exp*10)+(c-'0');
            break NEXT_CHAR;
          }
          exp *= sgn_exp;
          state = COND_QUOTED_NUMBER_END;
          continue MAIN_LOOP;

        // ---------------------------------------------------------------------
        case COND_QUOTE:
          if (c == quotes) {
            _str.addChar();
//            _str.skipChar();
            state = STRING;
            break NEXT_CHAR;
          } else {
            quotes = 0;
            state = STRING_END;
            continue MAIN_LOOP;
          }
        // ---------------------------------------------------------------------
        default:
          assert (false) : " We have wrong state "+state;
      } // end NEXT_CHAR
      ++offset; // do not need to adjust for offset increase here - the offset is set to tokenStart-1!
      if (offset < 0) {         // Offset is negative?
        assert !firstChunk;     // Caused by backing up from 2nd chunk into 1st chunk
        firstChunk = true;
        bits = bits0;
        offset += bits.length;
        _str.set(bits,offset,0);
      } else if (offset >= bits.length) { // Off end of 1st chunk?  Parse into 2nd chunk
        // Attempt to get more data.
        if( firstChunk && bits1 == null )
          bits1 = din.getChunkData(cidx+1);
        // if we can't get further we might have been the last one and we must
        // commit the latest guy if we had one.
        if( !firstChunk || bits1 == null ) { // No more data available or allowed
          // If we are mid-parse of something, act like we saw a LF to end the
          // current token.
          if ((state != EXPECT_COND_LF) && (state != POSSIBLE_EMPTY_LINE)) {
            c = CHAR_LF;  continue MAIN_LOOP;
          }
          break MAIN_LOOP;      // Else we are just done
        }

        // Now parsing in the 2nd chunk.  All offsets relative to the 2nd chunk start.
        firstChunk = false;
        numStart -= bits.length;
        if (state == NUMBER_FRACTION)
          fractionDigits -= bits.length;
        offset -= bits.length;
        tokenStart -= bits.length;
        bits = bits1;           // Set main parsing loop bits
        if( bits[0] == CHAR_LF && state == EXPECT_COND_LF )
          break MAIN_LOOP; // when the first character we see is a line end
      }
      c = bits[offset];
      if(isEOL(c) && state != COND_QUOTE && quotes != 0) // quoted string having newline character => fail the line!
        state = EOL;

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
}
