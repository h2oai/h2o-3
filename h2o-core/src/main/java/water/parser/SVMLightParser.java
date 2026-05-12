package water.parser;

import java.util.Arrays;

import water.Key;
import water.fvec.Vec;
import water.util.PrettyPrint;

import static water.parser.DefaultParserProviders.SVMLight_INFO;

class SVMLightParser extends Parser {
  private static final byte SKIP_TOKEN = 21;
  private static final byte INVALID_NUMBER = 22;
  private static final byte QID0 = 23;
  private static final byte QID1 = 24;

  // line global states
  private static final int TGT = 1;
  private static final int COL = 2;
  private static final int VAL = 3;

  SVMLightParser( ParseSetup ps, Key jobkey ) { super(ps, jobkey); }

  /** Try to parse the bytes as svm light format, return a ParseSetupHandler with type 
   *  SVMLight if the input is in svm light format, throw an exception otherwise.
   */
  public static ParseSetup guessSetup(byte [] bits) {
    int lastNewline = bits.length-1;
    while(lastNewline > 0 && !CsvParser.isEOL(bits[lastNewline]))lastNewline--;
    if(lastNewline > 0) bits = Arrays.copyOf(bits,lastNewline+1);
    SVMLightParser p = new SVMLightParser(new ParseSetup(SVMLight_INFO,
            ParseSetup.GUESS_SEP, false,ParseSetup.GUESS_HEADER,ParseSetup.GUESS_COL_CNT,
            null,null,null,null,null, false), null);
    SVMLightInspectParseWriter dout = new SVMLightInspectParseWriter();
    p.parseChunk(0,new ByteAryData(bits,0), dout);
    if (dout._ncols > 0 && dout._nlines > 0 && dout._nlines > dout._invalidLines)
      return new ParseSetup(SVMLight_INFO, ParseSetup.GUESS_SEP,
            false,ParseSetup.NO_HEADER,dout._ncols,null,dout.guessTypes(),null,null,dout._data, dout.removeErrors());
    else throw new ParseDataset.H2OParseException("Could not parse file as an SVMLight file.");
  }

  public static byte[] col_types(int ncols) {
    byte[] res = new byte[ncols];
    Arrays.fill(res,Vec.T_NUM);
    return res;
  }

  final boolean isWhitespace(byte c){return c == ' '  || c == '\t';}

  @SuppressWarnings("fallthrough")
  @Override public final ParseWriter parseChunk(int cidx, final ParseReader din, final ParseWriter dout) {
      BufferedString _str = new BufferedString();
      byte[] bits = din.getChunkData(cidx);
      if( bits == null ) return dout;
      final byte[] bits0 = bits;  // Bits for chunk0
      boolean firstChunk = true;  // Have not rolled into the 2nd chunk
      byte[] bits1 = null;        // Bits for chunk1, loaded lazily.
      int offset = 0;             // General cursor into the giant array of bytes
      // Starting state.  Are we skipping the first (partial) line, or not?  Skip
      // a header line, or a partial line if we're in the 2nd and later chunks.
      int lstate = (cidx > 0)? SKIP_LINE : WHITESPACE_BEFORE_TOKEN;
      int gstate = TGT;
      long number = 0;
      int zeros = 0;
      int exp = 0;
      int sgnExp = 1;
      boolean decimal = false;
      int fractionDigits = 0;
      int colIdx = 0;
      byte c = bits[offset];
      // skip comments for the first chunk (or if not a chunk)
      if( cidx == 0 ) {
        while (c == '#') {
          while ((offset   < bits.length) && (bits[offset] != CHAR_CR) && (bits[offset  ] != CHAR_LF)) ++offset;
          if    ((offset+1 < bits.length) && (bits[offset] == CHAR_CR) && (bits[offset+1] == CHAR_LF)) ++offset;
          ++offset;
          if (offset >= bits.length)
            return dout;
          c = bits[offset];
        }
      }
  MAIN_LOOP:
      while (true) {
  NEXT_CHAR:
        switch (lstate) {
          // ---------------------------------------------------------------------
          case SKIP_LINE:
            if (!isEOL(c))
              break;
            // fall through
          case EOL:
            if (colIdx != 0) {
              colIdx = 0;
              if(lstate != SKIP_LINE)
                dout.newLine();
            }
            if( !firstChunk )
              break MAIN_LOOP; // second chunk only does the first row
            lstate = (c == CHAR_CR) ? EXPECT_COND_LF : POSSIBLE_EMPTY_LINE;
            gstate = TGT;
            break;
          // ---------------------------------------------------------------------
          case EXPECT_COND_LF:
            lstate = POSSIBLE_EMPTY_LINE;
            if (c == CHAR_LF)
              break;
            continue MAIN_LOOP;
          // ---------------------------------------------------------------------

          // ---------------------------------------------------------------------

          // ---------------------------------------------------------------------
          case POSSIBLE_EMPTY_LINE:
            if (isEOL(c)) {
              if (c == CHAR_CR)
                lstate = EXPECT_COND_LF;
              break;
            }
            lstate = WHITESPACE_BEFORE_TOKEN;
            // fallthrough to WHITESPACE_BEFORE_TOKEN
          // ---------------------------------------------------------------------
          case WHITESPACE_BEFORE_TOKEN:
            if (isWhitespace(c))
                break;
            if (isEOL(c)){
              lstate = EOL;
              continue MAIN_LOOP;
            }
          // fallthrough to TOKEN
          case TOKEN:
            if (((c >= '0') && (c <= '9')) || (c == '-') || (c == CHAR_DECIMAL_SEP) || (c == '+')) {
              lstate = NUMBER;
              number = 0;
              fractionDigits = 0;
              decimal = false;

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
            } else if(c == 'q'){
              lstate = QID0;
            } else { // failed, skip the line
              String err = "Unexpected character, expected number or qid, got '" + new String(Arrays.copyOfRange(bits, offset,Math.min(bits.length,offset+5))) + "...'";
              dout.invalidLine(new ParseWriter.ParseErr(err,cidx,dout.lineNum(),offset + din.getGlobalByteOffset()));
              lstate = SKIP_LINE;
              continue MAIN_LOOP;
            }
            // fallthrough to NUMBER
          // ---------------------------------------------------------------------
          case NUMBER:
            if ((c >= '0') && (c <= '9')) {
              number = (number*10)+(c-'0');
              if (number >= LARGEST_DIGIT_NUMBER)
                lstate = INVALID_NUMBER;
              break;
            } else if (c == CHAR_DECIMAL_SEP) {
              lstate = NUMBER_FRACTION;
              fractionDigits = offset;
              decimal = true;
              break;
            } else if ((c == 'e') || (c == 'E')) {
              lstate = NUMBER_EXP_START;
              sgnExp = 1;
              break;
            }
            if (exp == -1) {
              number = -number;
            }
            exp = 0;
            // fallthrough NUMBER_END
          case NUMBER_END:
            exp = exp - fractionDigits;
            switch(gstate){
              case COL:
                if(c == ':'){
                  if(exp == 0 && number >= colIdx && (int)number == number){
                    colIdx = (int)number;
                    gstate = VAL;
                    lstate = WHITESPACE_BEFORE_TOKEN;
                  } else {
                    // wrong col Idx, just skip the token and try to continue
                    // col idx is either too small (according to spec, cols must come in strictly increasing order)
                    // or too small (col ids currently must fit into int)
                    String err;
                    if(number <= colIdx)
                      err = "Columns come in non-increasing sequence. Got " + number + " after " + colIdx + ". Rest of the line is skipped.";
                    else if(exp != 0)
                      err = "Got non-integer as column id: " + PrettyPrint.pow10(number,exp) + ". Rest of the line is skipped.";
                    else
                      err = "column index out of range, " + number + " does not fit into integer." + " Rest of the line is skipped.";
                    dout.invalidLine(new ParseWriter.ParseErr(err,cidx,dout.lineNum(),offset + din.getGlobalByteOffset()));
                    lstate = SKIP_LINE;
                  }
                } else { // we're probably out of sync, skip the rest of the line
                  String err = "Unexpected character after column id: " + c;
                  dout.invalidLine(new ParseWriter.ParseErr(err,cidx,dout.lineNum(),offset + din.getGlobalByteOffset()));
                  lstate = SKIP_LINE;
                }
                break NEXT_CHAR;
              case TGT:
              case VAL:
                dout.addNumCol(colIdx++,number,exp);
                lstate = WHITESPACE_BEFORE_TOKEN;
                gstate = COL;
                continue MAIN_LOOP;
            }
          // ---------------------------------------------------------------------
          case NUMBER_FRACTION:
            if(c == '0'){
              ++zeros;
              break;
            }
            if ((c > '0') && (c <= '9')) {
              if (number < LARGEST_DIGIT_NUMBER) {
                number = (number*PrettyPrint.pow10i(zeros+1))+(c-'0');
              } else {
                String err = "number " + number + " is out of bounds.";
                dout.invalidLine(new ParseWriter.ParseErr(err,cidx,dout.lineNum(),offset + din.getGlobalByteOffset()));
                lstate = SKIP_LINE;
              }
              zeros = 0;
              break;
            } else if ((c == 'e') || (c == 'E')) {
              if (decimal)
                fractionDigits = offset - zeros - 1 - fractionDigits;
              lstate = NUMBER_EXP_START;
              sgnExp = 1;
              zeros = 0;
              break;
            }
            lstate = NUMBER_END;
            if (decimal)
              fractionDigits = offset - zeros - fractionDigits-1;
            if (exp == -1) {
              number = -number;
            }
            exp = 0;
            zeros = 0;
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
              lstate = INVALID_NUMBER;
              continue MAIN_LOOP;
            }
            lstate = NUMBER_EXP;  // fall through to NUMBER_EXP
          // ---------------------------------------------------------------------
          case NUMBER_EXP:
            if ((c >= '0') && (c <= '9')) {
              exp = (exp*10)+(c-'0');
              break;
            }
            exp *= sgnExp;
            lstate = NUMBER_END;
            continue MAIN_LOOP;
          // ---------------------------------------------------------------------
          case INVALID_NUMBER:
            if(gstate == TGT) { // invalid tgt -> skip the whole row
              lstate = SKIP_LINE;
              String err = "invalid number (expecting target)";
              dout.invalidLine(new ParseWriter.ParseErr(err,cidx,dout.lineNum(),offset + din.getGlobalByteOffset()));
              continue MAIN_LOOP;
            }
            if(gstate == VAL){ // add invalid value and skip until whitespace or eol
              dout.addInvalidCol(colIdx++);
              gstate = COL;
            }
          case QID0:
            if(c == 'i'){
              lstate = QID1;
              break;
            } else {
              lstate = SKIP_TOKEN;
              break;
            }
          case QID1:
            if(c == 'd'){
              lstate = SKIP_TOKEN; // skip qid for now
              break;
            } else {
              // TODO report an error
              lstate = SKIP_TOKEN;
              break;
            }
            // fall through
          case SKIP_TOKEN:
            if(isEOL(c))
              lstate = EOL;
            else if(isWhitespace(c))
              lstate = WHITESPACE_BEFORE_TOKEN;
            break;
          default:
            assert (false) : " We have wrong state "+lstate;
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
          if( firstChunk && bits1 == null ){
            bits1 = din.getChunkData(cidx+1);
//            linePrefix = new String(Arrays.copyOfRange(bits, linestart, bits.length));
          }
          // if we can't get further we might have been the last one and we must
          // commit the latest guy if we had one.
          if( !firstChunk || bits1 == null ) { // No more data available or allowed
            // If we are mid-parse of something, act like we saw a LF to end the
            // current token.
            if ((lstate != EXPECT_COND_LF) && (lstate != POSSIBLE_EMPTY_LINE)) {
              c = CHAR_LF;
              continue;
            }
            break;      // Else we are just done
          }
          // Now parsing in the 2nd chunk.  All offsets relative to the 2nd chunk start.
          firstChunk = false;
          if (lstate == NUMBER_FRACTION)
            fractionDigits -= bits.length;
          offset -= bits.length;
          bits = bits1;           // Set main parsing loop bits
          if( bits[0] == CHAR_LF && lstate == EXPECT_COND_LF )
            break; // when the first character we see is a line end
        }
        c = bits[offset];
      } // end MAIN_LOOP
      return dout;
  }

  // --------------------------------------------------------
  // Used for previewing datasets.
  // Fill with zeros not NAs, and grow columns on-demand.
  private static class SVMLightInspectParseWriter extends PreviewParseWriter {
    public SVMLightInspectParseWriter() {
      for (int i = 0; i < MAX_PREVIEW_LINES;++i)
        _data[i] = new String[MAX_PREVIEW_COLS];
      for (String[] datum : _data) Arrays.fill(datum, "0");
    }

    // Expand columns on-demand
    @Override public void addNumCol(int colIdx, long number, int exp) {
      _ncols = Math.max(_ncols,colIdx);
      if(colIdx < MAX_PREVIEW_COLS && _nlines < MAX_PREVIEW_LINES)
        _data[_nlines][colIdx] = Double.toString(PrettyPrint.pow10(number,exp));
    }

    @Override public void addNumCol(int colIdx, double d) {
      _ncols = Math.max(_ncols,colIdx);
      if(colIdx < MAX_PREVIEW_COLS && _nlines < MAX_PREVIEW_LINES)
        _data[_nlines][colIdx] = Double.toString(d);
    }

    public byte[] guessTypes() { return col_types(_ncols); }
  }
}
