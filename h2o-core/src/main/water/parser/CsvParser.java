package water.parser;

import water.H2O;

class CsvParser extends Parser {
  private static final byte CHAR_TAB = '\t';
          static final byte CHAR_LF = 10;
          static final byte CHAR_SPACE = ' ';
  private static final byte CHAR_CR = 13;
  private static final byte CHAR_VT = 11;
  private static final byte CHAR_FF = 12;
          static final byte CHAR_DOUBLE_QUOTE = '"';
          static final byte CHAR_SINGLE_QUOTE = '\'';
  private static final byte CHAR_NULL = 0;
  private static final byte CHAR_COMMA = ',';

  protected static final boolean isWhitespace(byte c) { return (c == CHAR_SPACE) || (c == CHAR_TAB); }
  protected static final boolean isEOL(byte c) { return (c == CHAR_LF) || (c == CHAR_CR); }

  @Override boolean parallelParseSupported() { return true; }

  // Parse this one Chunk (in parallel with other Chunks)
  @Override DataOut parallelParse(int cidx, final DataIn din, final DataOut dout) { 
    throw H2O.unimpl();
  }

  CsvParser( ParserSetup ps ) { super(ps); throw H2O.unimpl(); }
}
