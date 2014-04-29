package water.parser;

import water.H2O;

class XlsParser extends Parser {
  XlsParser( ParserSetup ps ) { super(ps); throw H2O.unimpl(); }
  @Override protected boolean parallelParseSupported() { return false; }
  @Override DataOut parallelParse(int cidx, final DataIn din, final DataOut dout) { throw H2O.fail(); }
}
