package water.parser;

import java.io.*;
import java.util.Arrays;
import water.H2O;

class XlsParser extends Parser {
  XlsParser( ParserSetup ps ) { super(ps); }
  @Override protected boolean parallelParseSupported() { return false; }

  /** Try to parse the bytes as XLS format  */
  public static ParserSetup guessSetup(byte [] bytes) {
    InputStream is = new ByteArrayInputStream(bytes);
    XlsParser p = new XlsParser(new ParserSetup(true, 0, null, ParserType.XLS, ParserSetup.AUTO_SEP, -1, false, null));
    InspectDataOut dout = new InspectDataOut();
    try{ p.streamParse(is, dout); } catch(IOException e) { throw new RuntimeException(e); }
    return new ParserSetup(dout._ncols > 0 && dout._nlines > 0 && dout._nlines > dout._invalidLines,
                           dout._invalidLines, dout.errors(), ParserType.XLS, ParserSetup.AUTO_SEP, dout._ncols,false,null);
  }

  @Override public DataOut streamParse( final InputStream is, final DataOut dout) throws IOException {
    //_dout = dout;
    //_firstRow = true;
    //try {
    //  _fs = new POIFSFileSystem(is);
    //  MissingRecordAwareHSSFListener listener = new MissingRecordAwareHSSFListener(this);
    //  _formatListener = new FormatTrackingHSSFListener(listener);
    //  HSSFEventFactory factory = new HSSFEventFactory();
    //  HSSFRequest request = new HSSFRequest();
    //  request.addListenerForAllRecords(_formatListener);
    //  factory.processWorkbookEvents(request, _fs);
    //} finally {
    //  try { is.close(); } catch (IOException e) { }
    //}
    //return dout;
    throw H2O.unimpl();
  }

  @Override DataOut parallelParse(int cidx, final DataIn din, final DataOut dout) { throw H2O.fail(); }
}
