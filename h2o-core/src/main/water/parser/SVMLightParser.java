package water.parser;

import water.H2O;

class SVMLightParser extends Parser {
  SVMLightParser( ParserSetup ps ) { super(ps); throw H2O.unimpl(); }
  @Override boolean parallelParseSupported() { return false; }
  @Override DataOut parallelParse(int cidx, final DataIn din, final DataOut dout) { throw H2O.fail(); }
}
