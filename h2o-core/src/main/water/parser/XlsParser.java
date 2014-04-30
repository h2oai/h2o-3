package water.parser;

import java.io.*;
import java.util.Arrays;
import water.H2O;
import water.UDP;

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

  /** Ported to Java from excel_reader2.php.
   *  Found at:  http://code.google.com/p/php-excel-reader/downloads/detail?name=php-excel-reader-2.21.zip&can=2&q=
   *
   *  Originally developed by Vadim Tkachenko under the name PHPExcelReader.
   *  (http://sourceforge.net/projects/phpexcelreader)
   *  Based on the Java version by Andy Khan (http://www.andykhan.com).  Now
   *  maintained by David Sanders.  Reads only Biff 7 and Biff 8 formats.
   * 
   *  PHP versions 4 and 5
   * 
   *  LICENSE: This source file is subject to version 3.0 of the PHP license
   *  that is available through the world-wide-web at the following URI:
   *  http://www.php.net/license/3_0.txt.  If you did not receive a copy of
   *  the PHP License and are unable to obtain it through the web, please
   *  send a note to license@php.net so we can mail you a copy immediately.
   */

  private static final int NUM_BIG_BLOCK_DEPOT_BLOCKS_POS = 0x2c;
  private static final int SMALL_BLOCK_DEPOT_BLOCK_POS = 0x3c;
  private static final int ROOT_START_BLOCK_POS = 0x30;
  private static final int BIG_BLOCK_SIZE = 0x200;
  private static final int SMALL_BLOCK_SIZE = 0x40;
  private static final int EXTENSION_BLOCK_POS = 0x44;
  private static final int NUM_EXTENSION_BLOCK_POS = 0x48;
  private static final int PROPERTY_STORAGE_BLOCK_SIZE = 0x80;
  private static final int BIG_BLOCK_DEPOT_BLOCKS_POS = 0x4c;
  private static final int SMALL_BLOCK_THRESHOLD = 0x1000;
  // property storage offsets
  private static final int SIZE_OF_NAME_POS = 0x40;
  private static final int TYPE_POS = 0x42;
  private static final int START_BLOCK_POS = 0x74;
  private static final int SIZE_POS = 0x78;
  private static final byte[] IDENTIFIER_OLE = new byte[] { (byte)0xd0,(byte)0xcf,(byte)0x11,(byte)0xe0,(byte)0xa1,(byte)0xb1,(byte)0x1a,(byte)0xe1 };

  private int _numBigBlockDepotBlocks;
  private int _sbdStartBlock;
  private int _rootStartBlock;
  private int _extensionBlock;
  private int _numExtensionBlocks;

  @Override public DataOut streamParse( final InputStream is, final DataOut dout) throws IOException {
    byte[] header = new byte[256];
    int hdlen = readFully(is,header);
    if( hdlen < header.length ) throw new IOException("not an XLS file");
    for( int i=0; i<IDENTIFIER_OLE.length; i++ ) 
      if( header[i] != IDENTIFIER_OLE[i] )
        throw new IOException("not an XLS file");
    _numBigBlockDepotBlocks = UDP.get4(header, NUM_BIG_BLOCK_DEPOT_BLOCKS_POS);
    _sbdStartBlock = UDP.get4(header, SMALL_BLOCK_DEPOT_BLOCK_POS);
    _rootStartBlock = UDP.get4(header, ROOT_START_BLOCK_POS);
    _extensionBlock = UDP.get4(header, EXTENSION_BLOCK_POS);
    _numExtensionBlocks = UDP.get4(header, NUM_EXTENSION_BLOCK_POS);

    int pos = BIG_BLOCK_DEPOT_BLOCKS_POS;
    final int bbdBlocks = _numExtensionBlocks == 0 ? _numBigBlockDepotBlocks : (BIG_BLOCK_SIZE - BIG_BLOCK_DEPOT_BLOCKS_POS)/4;
    final int[] bigBlockDepotBlocks = new int[bbdBlocks];
    for( int i = 0; i < bbdBlocks; i++ ) {
      bigBlockDepotBlocks[i] = UDP.get4(header, pos); pos += 4;
    }

    System.out.println("parse xls");
    throw H2O.unimpl();
  }

  @Override DataOut parallelParse(int cidx, final DataIn din, final DataOut dout) { throw H2O.fail(); }

  private int readFully( InputStream is, byte[] buf ) throws IOException {
    int len=0, x;
    while( len < buf.length && (x = is.read(buf, len, buf.length-len)) != -1 )
      len += x;
    return len;
  }
}
