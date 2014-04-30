package water.parser;

import java.io.*;
import java.util.Arrays;
import java.util.ArrayList;
import water.H2O;
import water.UDP;

class XlsParser extends Parser {
  XlsParser( ParserSetup ps ) { super(ps); }
  @Override protected boolean parallelParseSupported() { return false; }
  @Override DataOut parallelParse(int cidx, final DataIn din, final DataOut dout) { throw H2O.fail(); }

  // A Stream, might be a Zip stream
  private InputStream _is;
  // The unpacked data.  We expect we can fully hold the unzipped data.
  private byte[] _buf;
  private int _lim;             // What was read so-far

  // Simple offset / lim over the underlying buffer
  private class Buf {
    final byte[] _buf;
    byte[] _bbuf;
    int _off, _lim;
    Buf( byte[] buf, int off, int size ) throws IOException { _buf = _bbuf = buf; _off = off; _lim = off+size; readAtLeast(_lim); }
    Buf( Buf B, int off, int size ) { _buf = _bbuf = B._bbuf; _off = off; _lim = off+size; assert _lim <= _buf.length; }

    void concat( int off, int size ) throws IOException {
      readAtLeast(off+size);
      if( _off == _lim ) {      // Empty Buf, so concat is really assign
        _off = off; _lim = off+size; 
        return;
      }
      if( off == _lim ) {       // Adjacent, so just extend
        _lim += size; 
        return;
      }
      _bbuf = Arrays.copyOfRange(_bbuf,_off,_lim+size);
      _lim = _lim-_off+size;
      _off = 0;
      System.arraycopy(_buf,off,_bbuf,_lim-size,size);
    }

    char get1(int pos ) { assert _off+pos+1<_lim; return (char)_bbuf[_off+pos]; }
    int get2( int pos ) { assert _off+pos+2<_lim; return UDP.get2(_bbuf,_off+pos); }
    int get4( int pos ) { assert _off+pos+4<_lim; return UDP.get4(_bbuf,_off+pos); }
  }

  // Read & keep in _buf from the unpacked stream at least 'lim' bytes.
  // Toss a range-check if the stream runs dry too soon.
  private void readAtLeast(int lim) throws IOException{
    if( lim <= _lim ) return;   // Already read at least
    if( lim > _buf.length ) {   // Need to grow buffer
      int oldlen = _buf.length,  newlen = oldlen;
      while( newlen < lim ) newlen<<=1;
      _buf = Arrays.copyOf(_buf,newlen);
    }
    // Now read/unzip until lim
    int x;
    while( _lim < lim && (x = _is.read(_buf,_lim,_buf.length-_lim)) != -1 )
      _lim += x;
    if( _lim < lim )
      throw new java.lang.ArrayIndexOutOfBoundsException("not an XLS file: reading at "+lim+" but file is only "+_lim+" bytes");
  }

  // Wrapper to fetch an int at a random offset
  private int get4( int pos ) throws IOException { readAtLeast(pos+4); return UDP.get4(_buf,pos); }


  /** Try to parse the bytes as XLS format  */
  public static ParserSetup guessSetup( byte[] bytes ) {
    XlsParser p = new XlsParser(new ParserSetup(true, 0, null, ParserType.XLS, ParserSetup.AUTO_SEP, -1, false, null));
    p._buf = bytes;             // No need to copy already-unpacked data; just use it directly
    p._lim = bytes.length;
    InspectDataOut dout = new InspectDataOut();
    try{ p.streamParse(new ByteArrayInputStream(bytes), dout); } catch(IOException e) { throw new RuntimeException(e); }
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
  private int[] _bigBlockChain;
  private ArrayList<Props> _props = new ArrayList<>();
  private class Props {
    final String _name;
    final int _type, _startBlock, _size;
    Props( String name, int type, int startBlock, int size ) { _name = name; _type = type; _startBlock = startBlock; _size = size; }
  }

  @Override public DataOut streamParse( final InputStream is, final DataOut dout) throws IOException {
    // Check for magic first
    readAtLeast(IDENTIFIER_OLE.length);
    for( int i=0; i<IDENTIFIER_OLE.length; i++ ) 
      if( _buf[i] != IDENTIFIER_OLE[i] )
        throw new IOException("not an XLS file");

    _numBigBlockDepotBlocks = get4(NUM_BIG_BLOCK_DEPOT_BLOCKS_POS);
    _sbdStartBlock = get4(SMALL_BLOCK_DEPOT_BLOCK_POS);
    _rootStartBlock = get4(ROOT_START_BLOCK_POS);
    _extensionBlock = get4(EXTENSION_BLOCK_POS);
    _numExtensionBlocks = get4(NUM_EXTENSION_BLOCK_POS);

    int pos = BIG_BLOCK_DEPOT_BLOCKS_POS;
    int bbdBlocks = _numExtensionBlocks == 0 ? _numBigBlockDepotBlocks : (BIG_BLOCK_SIZE - BIG_BLOCK_DEPOT_BLOCKS_POS)/4;
    final int[] bigBlockDepotBlocks = new int[bbdBlocks];
    for( int i = 0; i < bbdBlocks; i++ )
      bigBlockDepotBlocks[i] = get4((pos+=4)-4);

    for( int j = 0; j < _numExtensionBlocks; j++ ) {
      pos = (_extensionBlock + 1) * BIG_BLOCK_SIZE;
      final int blocksToRead = Math.min(_numBigBlockDepotBlocks - bbdBlocks, BIG_BLOCK_SIZE / 4 - 1);
      for( int i = bbdBlocks; i < bbdBlocks + blocksToRead; i++ )
        bigBlockDepotBlocks[i] = get4((pos+=4)-4);
      bbdBlocks += blocksToRead;
      if( bbdBlocks < _numBigBlockDepotBlocks )
        _extensionBlock = get4(pos);
    }

    // readBigBlockDepot
    int index = 0;
    _bigBlockChain = new int[1];
    for( int i = 0; i < _numBigBlockDepotBlocks; i++ ) {
      pos = (bigBlockDepotBlocks[i] + 1) * BIG_BLOCK_SIZE;
      for( int j = 0 ; j < BIG_BLOCK_SIZE / 4; j++ ) {
        _bigBlockChain[index++] = get4((pos+=4)-4);
        if( index==_bigBlockChain.length ) _bigBlockChain = Arrays.copyOf(_bigBlockChain,index<<1);
      }
    }

    // readSmallBlockDepot();
    index = 0;
    int sbdBlock = _sbdStartBlock;
    int[] smallBlockChain = new int[1];
    while( sbdBlock != -2 ) {
      pos = (sbdBlock + 1) * BIG_BLOCK_SIZE;
      for( int j = 0; j < BIG_BLOCK_SIZE / 4; j++ ) {
        smallBlockChain[index++] = get4((pos+=4)-4);
        if( index==smallBlockChain.length ) smallBlockChain = Arrays.copyOf(smallBlockChain,index<<1);
      }
      sbdBlock = _bigBlockChain[sbdBlock];
    }

    // readData(rootStartBlock)
    Buf entry = __readData(_rootStartBlock);
    __readPropertySets(entry);

    //$this->data = $this->_ole->getWorkBook();
    //$this->_parse();

    System.out.println("parse xls");
    throw H2O.unimpl();
  }

  private Buf __readData(int block) throws IOException {
    Buf data = new Buf(_buf,0,0);
    while( block != -2 )  {
      int pos = (block + 1) * BIG_BLOCK_SIZE;
      data.concat(pos, BIG_BLOCK_SIZE);
      block = _bigBlockChain[block];
    }
    return data;
  }

  private int _wrkbook, _rootentry;
  private void __readPropertySets(Buf entry) {
    int offset = 0;
    while( offset < entry._lim ) {
      Buf d = new Buf(entry, offset, PROPERTY_STORAGE_BLOCK_SIZE);
      int nameSize = d.get2(SIZE_OF_NAME_POS);
      int type = d._bbuf[TYPE_POS];
      int startBlock = d.get4(START_BLOCK_POS);
      int size = d.get4(SIZE_POS);
      String name = "";
      for( int i = 0; i < nameSize ; i+=2 ) name += (char)d.get2(i);
      name = name.replaceAll("\0", ""); // remove trailing nul (C string?)
      _props.add( new Props(name,type,startBlock,size) );
      if( name.equalsIgnoreCase("workbook") || name.equalsIgnoreCase("book") )
        _wrkbook = _props.size()-1;
      if( name.equals("Root Entry") )
        _rootentry = _props.size()-1;
      offset += PROPERTY_STORAGE_BLOCK_SIZE;
    }
  }


}
