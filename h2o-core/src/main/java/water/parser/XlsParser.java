package water.parser;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.ArrayList;
import water.Key;
import water.H2O;
import water.util.UnsafeUtils;

import static water.parser.DefaultParserProviders.XLS_INFO;

class XlsParser extends Parser {
  XlsParser( ParseSetup ps, Key jobKey ) { super(ps, jobKey); }

  @Override
  protected ParseWriter parseChunk(int cidx, final ParseReader din, final ParseWriter dout) { throw H2O.unimpl(); }

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
    int get2( int pos ) { assert _off+pos+2<_lim; return UnsafeUtils.get2(_bbuf, _off + pos); }
    int get4( int pos ) { assert _off+pos+4<_lim; return UnsafeUtils.get4(_bbuf,_off+pos); }
    double get8d( int pos ) { assert _off+pos+8<_lim; return UnsafeUtils.get8d(_bbuf,_off+pos); }
    String getStr( int pos, int len ) { return new String(_bbuf,_off+pos,len); }
  }

  // Read & keep in _buf from the unpacked stream at least 'lim' bytes.
  // Toss a range-check if the stream runs dry too soon.
  private void readAtLeast(int lim) throws IOException{
    if( lim <= _lim ) return;   // Already read at least
    if( _buf == null ) _buf = new byte[0];
    if( lim > _buf.length ) { // Need to grow buffer
      int oldlen = _buf.length,  newlen = oldlen;
      if( newlen==0 ) newlen=1024;
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
  private int get4( int pos ) throws IOException { readAtLeast(pos+4); return UnsafeUtils.get4(_buf,pos); }


  /** Try to parse the bytes as XLS format  */
  public static ParseSetup guessSetup( byte[] bytes ) {
    XlsParser p = new XlsParser(new ParseSetup(XLS_INFO, ParseSetup.GUESS_SEP, false,
                                ParseSetup.GUESS_HEADER, ParseSetup.GUESS_COL_CNT, null, null, null, null, null, false), null);
    p._buf = bytes;             // No need to copy already-unpacked data; just use it directly
    p._lim = bytes.length;
    PreviewParseWriter dout = new PreviewParseWriter();
    try{ p.streamParse(new ByteArrayInputStream(bytes), dout); } catch(IOException e) { throw new RuntimeException(e); }
    if (dout._ncols > 0 && dout._nlines > 0 && dout._nlines > dout._invalidLines)
      return new ParseSetup(XLS_INFO, ParseSetup.GUESS_SEP, false,
            dout.colNames()==null?ParseSetup.NO_HEADER:ParseSetup.HAS_HEADER,dout._ncols,
                                 dout.colNames(), dout.guessTypes(),null,null,dout._data, false);
    else throw new ParseDataset.H2OParseException("Could not parse file as an XLS file.");
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

  // Breakdown of the OLE structure
  private int _numBigBlockDepotBlocks;
  private int _sbdStartBlock;
  private int _rootStartBlock;
  private int _extensionBlock;
  private int _numExtensionBlocks;
  private int[] _bigBlockChain;
  private int[] _smallBlockChain;
  private ArrayList<Props> _props = new ArrayList<>();
  private static class Props {
    final String _name;
    final int _type, _startBlock, _size;
    Props( String name, int type, int startBlock, int size ) { _name = name; _type = type; _startBlock = startBlock; _size = size; }
  }
  private Props _wrkbook, _rootentry;

  @Override public ParseWriter streamParse( final InputStream is, final StreamParseWriter dout) throws IOException {
    _is = is;
    // Check for magic first
    readAtLeast(IDENTIFIER_OLE.length);
    for( int i=0; i<IDENTIFIER_OLE.length; i++ ) 
      if( _buf[i] != IDENTIFIER_OLE[i] )
        throw new ParseDataset.H2OParseException("Not a valid XLS file, lacks correct starting bits (aka magic number).");

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

    // Read workbook & root entries
    __readPropertySets(__readData(_rootStartBlock));
    // Read the workbook - this holds all the csv data
    Buf data = getWorkBook();
    // Parse the workbook
    boolean res = parseWorkbook(data,dout);
    if( !res ) throw new IOException("not an XLS file");

    return dout;
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

  // Find the workbook & root entries
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
      Props p = new Props(name,type,startBlock,size);
      _props.add(p);
      if( name.equalsIgnoreCase("workbook") || name.equalsIgnoreCase("book") )
        _wrkbook = p;
      if( name.equals("Root Entry") )
        _rootentry = p;
      offset += PROPERTY_STORAGE_BLOCK_SIZE;
    }
  }

  
  private Buf getWorkBook() throws IOException {
    if( _wrkbook._size < SMALL_BLOCK_THRESHOLD ) {
      Buf rootdata = __readData(_rootentry._startBlock);
      Buf streamData = new Buf(rootdata,0,0);
      int block = _wrkbook._startBlock;
      while( block != -2 ) {
        int pos = block * SMALL_BLOCK_SIZE;
        streamData.concat(pos, SMALL_BLOCK_SIZE);
        block = _smallBlockChain[block];
      }
      return streamData;
    } else {
      int numBlocks = _wrkbook._size / BIG_BLOCK_SIZE;
      if( _wrkbook._size % BIG_BLOCK_SIZE != 0 )
        numBlocks++;
      Buf streamData = new Buf(_buf,0,0);
      if( numBlocks == 0 ) return streamData;
      int block = _wrkbook._startBlock;
      while( block != -2 ) {
        int pos = (block + 1) * BIG_BLOCK_SIZE;
        streamData.concat(pos, BIG_BLOCK_SIZE);
        block = _bigBlockChain[block];
      }
      return streamData;
    }
  }


  private static final int SPREADSHEET_EXCEL_READER_BIFF8 = 0x600;
  private static final int SPREADSHEET_EXCEL_READER_BIFF7 = 0x500;
  private static final int SPREADSHEET_EXCEL_READER_WORKBOOKGLOBALS = 0x5;
  private static final int SPREADSHEET_EXCEL_READER_WORKSHEET = 0x10;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_BOF = 0x809;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_EOF = 0x0a;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_BOUNDSHEET = 0x85;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_DIMENSION = 0x200;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_ROW = 0x208;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_DBCELL = 0xd7;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_FILEPASS = 0x2f;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_NOTE = 0x1c;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_TXO = 0x1b6;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_RK = 0x7e;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_RK2 = 0x27e;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_MULRK = 0xbd;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_MULBLANK = 0xbe;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_INDEX = 0x20b;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_SST = 0xfc;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_EXTSST = 0xff;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_CONTINUE = 0x3c;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_LABEL = 0x204;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_LABELSST = 0xfd;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_NUMBER = 0x203;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_NAME = 0x18;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_ARRAY = 0x221;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_STRING = 0x207;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_FORMULA = 0x406;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_FORMULA2 = 0x6;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_FORMAT = 0x41e;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_XF = 0xe0;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_BOOLERR = 0x205;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_FONT = 0x0031;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_PALETTE = 0x0092;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_UNKNOWN = 0xffff;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_NINETEENFOUR = 0x22;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_MERGEDCELLS = 0xE5;
  private static final int SPREADSHEET_EXCEL_READER_UTCOFFSETDAYS = 25569;
  private static final int SPREADSHEET_EXCEL_READER_UTCOFFSETDAYS1904 = 24107;
  private static final int SPREADSHEET_EXCEL_READER_MSINADAY = 86400;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_HYPER = 0x01b8;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_COLINFO = 0x7d;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_DEFCOLWIDTH = 0x55;
  private static final int SPREADSHEET_EXCEL_READER_TYPE_STANDARDWIDTH = 0x99;
  private static final String SPREADSHEET_EXCEL_READER_DEF_NUM_FORMAT = "%s";

  // Excel spreadsheet specific stuff
  private int _version;
  private boolean _nineteenFour;
  private String[] _formatRecords = new String[1];
  private ArrayList<String> _sst = new ArrayList<>();
  private ArrayList<Sheet> _boundsheets = new ArrayList<>();
  private static class XF {
    final int _indexCode;
    enum Type { Date, Number, Other }
    final Type _type;
    XF( int code, Type type ) { _indexCode = code; _type = type; }
  }
  private ArrayList<XF> _xfRecords = new ArrayList<>();

  /** List of default date formats used by Excel */
  private static HashMap<Integer,String> DATEFORMATS = new HashMap<>();
  static {
    DATEFORMATS.put(0xe,"m/d/Y");
    DATEFORMATS.put(0xf,"M-d-Y");
    DATEFORMATS.put(0x10,"d-M");
    DATEFORMATS.put(0x11,"M-Y");
    DATEFORMATS.put(0x12,"h:i a");
    DATEFORMATS.put(0x13,"h:i:s a");
    DATEFORMATS.put(0x14,"H:i");
    DATEFORMATS.put(0x15,"H:i:s");
    DATEFORMATS.put(0x16,"d/m/Y H:i");
    DATEFORMATS.put(0x2d,"i:s");
    DATEFORMATS.put(0x2e,"H:i:s");
    DATEFORMATS.put(0x2f,"i:s.S");
  }

  /** Default number formats used by Excel */
  private static HashMap<Integer,String> NUMBERFORMATS = new HashMap<>();
  static {
    NUMBERFORMATS.put(0x1 ,"0");
    NUMBERFORMATS.put(0x2 ,"0.00");
    NUMBERFORMATS.put(0x3 ,"#,##0");
    NUMBERFORMATS.put(0x4 ,"#,##0.00");
    NUMBERFORMATS.put(0x5 ,"$#,##0;($#,##0)");
    NUMBERFORMATS.put(0x6 ,"$#,##0;[Red]($#,##0)");
    NUMBERFORMATS.put(0x7 ,"$#,##0.00;($#,##0.00)");
    NUMBERFORMATS.put(0x8 ,"$#,##0.00;[Red]($#,##0.00)");
    NUMBERFORMATS.put(0x9 ,"0%");
    NUMBERFORMATS.put(0xa ,"0.00%");
    NUMBERFORMATS.put(0xb ,"0.00E+00");
    NUMBERFORMATS.put(0x25,"#,##0;(#,##0)");
    NUMBERFORMATS.put(0x26,"#,##0;[Red](#,##0)");
    NUMBERFORMATS.put(0x27,"#,##0.00;(#,##0.00)");
    NUMBERFORMATS.put(0x28,"#,##0.00;[Red](#,##0.00)");
    NUMBERFORMATS.put(0x29,"#,##0;(#,##0)");  // Not exactly
    NUMBERFORMATS.put(0x2a,"$#,##0;($#,##0)");  // Not exactly
    NUMBERFORMATS.put(0x2b,"#,##0.00;(#,##0.00)");  // Not exactly
    NUMBERFORMATS.put(0x2c,"$#,##0.00;($#,##0.00)");  // Not exactly
    NUMBERFORMATS.put(0x30,"##0.0E+0");
  }


  /**
   * Parse a workbook
   */
  private boolean parseWorkbook(Buf data, final ParseWriter dout) {
    int pos = 0;

    int code = data.get2(pos);
    int length = data.get2(pos+2);
    int version = data.get2(pos+4);
    int substreamType = data.get2(pos+6);

    _version = version;

    if( version != SPREADSHEET_EXCEL_READER_BIFF8 &&
        version != SPREADSHEET_EXCEL_READER_BIFF7 )
      return false;

    if( substreamType != SPREADSHEET_EXCEL_READER_WORKBOOKGLOBALS )
      return false;

    pos += length + 4;
    code = data.get2(pos);
    length = data.get2(pos+2);

    while( code != SPREADSHEET_EXCEL_READER_TYPE_EOF ) {
      switch( code ) {
      case SPREADSHEET_EXCEL_READER_TYPE_SST: {
        int spos = pos + 4;
        int limitpos = spos + length;
        int uniqueStrings = data.get4(spos+4);
        spos += 8;
        for( int i = 0; i < uniqueStrings; i++ ) {
          // Read in the number of characters
          if (spos == limitpos) {
            int conlength = data.get2(spos+2);
            spos += 4;
            limitpos = spos + conlength;
          }
          int numChars = data.get2(spos);
          spos += 2;
          int optionFlags = data.get1(spos);
          spos++;
          boolean asciiEncoding = ((optionFlags & 0x01) == 0);
          boolean extendedString = ( (optionFlags & 0x04) != 0);

          // See if string contains formatting information
          boolean richString = ( (optionFlags & 0x08) != 0);

          int formattingRuns=0;
          if( richString )      // Read in the crun
            formattingRuns = data.get2((spos+=2)-2);

          int extendedRunLength=0;
          if( extendedString )  // Read in cchExtRst
            extendedRunLength = data.get4((spos+=4)-4);

          String retstr = null;
          int len = (asciiEncoding)? numChars : numChars*2;
          if( spos + len < limitpos ) {
            retstr = data.getStr((spos+=len)-len, len);
          }
          else {
            // found continue
            retstr = data.getStr(spos, limitpos - spos);
            int bytesRead = limitpos - spos;
            int charsLeft = numChars - ((asciiEncoding) ? bytesRead : (bytesRead / 2));
            spos = limitpos;

            while (charsLeft > 0) {
              int opcode = data.get2(spos);
              int conlength = data.get2(spos+2);
              if( opcode != 0x3c ) return false;
              spos += 4;
              limitpos = spos + conlength;
              int option = data.get1(spos);
              spos += 1;
      //        if (asciiEncoding && (option == 0)) {
      //          len = min(charsLeft, limitpos - spos); // min(charsLeft, conlength);
      //          retstr .= substr(data, spos, len);
      //          charsLeft -= len;
      //          asciiEncoding = true;
      //        }
      //        elseif (!asciiEncoding && (option != 0)) {
      //          len = min(charsLeft * 2, limitpos - spos); // min(charsLeft, conlength);
      //          retstr .= substr(data, spos, len);
      //          charsLeft -= len/2;
      //          asciiEncoding = false;
      //        }
      //        elseif (!asciiEncoding && (option == 0)) {
      //          // Bummer - the string starts off as Unicode, but after the
      //          // continuation it is in straightforward ASCII encoding
      //          len = min(charsLeft, limitpos - spos); // min(charsLeft, conlength);
      //          for (j = 0; j < len; j++) {
      //            retstr .= data[spos + j].chr(0);
      //          }
      //          charsLeft -= len;
      //          asciiEncoding = false;
      //        }
      //        else{
      //          newstr = '';
      //          for (j = 0; j < strlen(retstr); j++) {
      //            newstr = retstr[j].chr(0);
      //          }
      //          retstr = newstr;
      //          len = min(charsLeft * 2, limitpos - spos); // min(charsLeft, conlength);
      //          retstr .= substr(data, spos, len);
      //          charsLeft -= len/2;
      //          asciiEncoding = false;
      //        }
      //        spos += len;
              throw H2O.unimpl();
            }
          }
          retstr = (asciiEncoding) ? retstr : __encodeUTF16(retstr);

          if (richString) spos += 4 * formattingRuns;
          // For extended strings, skip over the extended string data
          if (extendedString) spos += extendedRunLength;
          _sst.add(retstr);
        }
        break;
      }
      case SPREADSHEET_EXCEL_READER_TYPE_FILEPASS:
        return false;
      case SPREADSHEET_EXCEL_READER_TYPE_NAME:
        break;
      case SPREADSHEET_EXCEL_READER_TYPE_FORMAT: {
        String formatString = version == SPREADSHEET_EXCEL_READER_BIFF8
          ? data.getStr(pos+9, data.get2(pos+6)*(data.get1(pos+8) == 0 ? 1 : 2))
          : data.getStr(pos+7, data.get1(pos+6)*2);
        int indexCode = data.get2(pos+4);
        while( indexCode >= _formatRecords.length ) 
          _formatRecords = Arrays.copyOf(_formatRecords,_formatRecords.length<<1);
        _formatRecords[indexCode] = formatString;
        break;
      }
      case SPREADSHEET_EXCEL_READER_TYPE_FONT: 
        break; // While the original php file parsed the font here, H2O just wants the data
      case SPREADSHEET_EXCEL_READER_TYPE_PALETTE:
        break; // While the original php file parsed the color palaette info here, H2O just wants the data
      case SPREADSHEET_EXCEL_READER_TYPE_XF: {
        // While the original php file parsed the extensive formatting info
        // here, H2O just wants the data.  Limit to figuring out if excel thinks
        // this is a date-formatted field or not
        int indexCode = data.get2(pos+6);
        XF.Type t=null;
        if( DATEFORMATS.containsKey(indexCode) )
          t = XF.Type.Date;
        else if( NUMBERFORMATS.containsKey(indexCode) )
          t = XF.Type.Number;
        else if( indexCode < _formatRecords.length && _formatRecords[indexCode] != null )
          t = XF.Type.Other;
        _xfRecords.add(new XF(indexCode,t));
        break; 
      }
      case SPREADSHEET_EXCEL_READER_TYPE_NINETEENFOUR:
        _nineteenFour = data.get1(pos+4) == 1;
        break;
      case SPREADSHEET_EXCEL_READER_TYPE_BOUNDSHEET:
        int recOffset = data.get4(pos+4);
        int recLength = data.get1(pos+10);
        String recName = version == SPREADSHEET_EXCEL_READER_BIFF8
          ? data.getStr(pos+12, recLength*(data.get1(pos+11) == 0 ? 1 : 2))
          : data.getStr(pos+11, recLength);
        _boundsheets.add(new Sheet(data,dout,recName,recOffset));
        break;
      default:
        // nothing; ignore this block typed
      }
      
      pos += length + 4;
      code = data.get2(pos);
      length = data.get2(pos+2);
    }

    // Parse all Sheets, although honestly H2O probably only wants the 1st sheet
    for( Sheet sheet : _boundsheets )
      sheet.parse();
    return true;
  }

  // ------------------------------
  // A single Excel Sheet
  private class Sheet { 
    final String _name;
    final Buf _data;
    final int _offset;
    final ParseWriter _dout;
    
    int _numRows, _numCols;
    String[] _labels;
    int _currow = 0;
    double[] _ds;

    Sheet( Buf data, ParseWriter dout, String name, int offset ) { _data = data; _dout = dout; _name = name; _offset = offset; }

    // Get the next row spec - and thus cleanup the prior row
    int row(int spos) {
      int row = _data.get2(spos);
      if( row < _currow ) throw new RuntimeException("XLS file but rows running backwards");
      return doRow(row);
    }
    int doRow(int row) {
      // Once we're done with row 0, look at the collection of Strings on this
      // row.  If all columns have a String, declare it a label row.  Else,
      // inject the partial Strings as categoricals.
      if( row > _currow && _currow == 0 ) {      // Moving off of row 0
        boolean header=true;
        for( String s : _labels ) header &= (s!=null); // All strings?
        if( header ) {          // It's a header row
          _dout.setColumnNames(_labels.clone());
          Arrays.fill(_labels,null); // Dont reuse them labels as categoricals
          _currow=1;                 // Done with this row
        }
      }
      // Advance to the next row
      while( _currow < row ) { 
        _currow++;              // Next row internally
        // Forward collected row to _dout.  
        for( int i=0; i<_ds.length; i++ ) {
          if( _labels[i] != null ) { _dout.addStrCol(i,new BufferedString(_labels[i])); _labels[i] = null; }
          else { _dout.addNumCol(i,_ds[i]); _ds[i] = Double.NaN; }
        }
        _dout.newLine();        // And advance dout a line
      }
      return row;
    }

    boolean parse() {
      // read BOF
      int spos = _offset;
      int code = _data.get2(spos);
      int length = _data.get2(spos+2);
      int version = _data.get2(spos + 4);
      if( (version != SPREADSHEET_EXCEL_READER_BIFF8) && (version != SPREADSHEET_EXCEL_READER_BIFF7) )
        return false;

      int substreamType = _data.get2(spos + 6);
      if( substreamType != SPREADSHEET_EXCEL_READER_WORKSHEET )
        return false;
      spos += length + 4;
      String recType = null;

      while(true) {
        code = _data.get1(spos);
        if( code != SPREADSHEET_EXCEL_READER_TYPE_EOF) {
          code = _data.get2(spos);
          length = _data.get2(spos+2);
          recType = null;
          spos += 4;
        }

        switch( code ) {
        case SPREADSHEET_EXCEL_READER_TYPE_DIMENSION:
          if( _numRows == 0 && _numCols == 0 ) {
            if( length == 10 || version == SPREADSHEET_EXCEL_READER_BIFF7 ) {
              _numRows = _data.get2(spos+ 2);
              _numCols = _data.get2(spos+ 6);
            } else {
              _numRows = _data.get2(spos+ 4);
              _numCols = _data.get2(spos+10);
            }
            _labels = new String[_numCols];
            _ds = new double[_numCols];
            Arrays.fill(_ds,Double.NaN);
          }
          break;
        case SPREADSHEET_EXCEL_READER_TYPE_MERGEDCELLS: break; // While the original php file parsed merged-cells here, H2O just wants the _data
        case SPREADSHEET_EXCEL_READER_TYPE_RK:
        case SPREADSHEET_EXCEL_READER_TYPE_RK2: {
          int row = row(spos);
          int col = _data.get2(spos+2);
          double d  = _GetIEEE754(_data.get4(spos+6));
          if( isDate(_data, spos) ) throw H2O.unimpl();
          _ds[col] = d;
          break;
        }
        case SPREADSHEET_EXCEL_READER_TYPE_LABELSST: {
          int row = row(spos);
          int col = _data.get2(spos+2);
          int index = _data.get4(spos+6);
          _labels[col] = _sst.get(index); // Set label
          break;
        }
        case SPREADSHEET_EXCEL_READER_TYPE_MULRK: {
          int row     = row(spos);
          int colFirst= _data.get2(spos+2);
          int colLast = _data.get2(spos+length-2);
          int columns  = colLast - colFirst + 1;
          int tmppos = spos+4;
          for( int i = 0; i < columns; i++ ) {
            double numValue = _GetIEEE754(_data.get4(tmppos + 2));
            if( isDate( _data, tmppos-4) ) throw H2O.unimpl();
            tmppos += 6;
            _ds[colFirst+i] = numValue;
          }
          break;
        }
        case SPREADSHEET_EXCEL_READER_TYPE_NUMBER: {
          int row = row(spos);
          int col = _data.get2(spos+2);
          double d = _data.get8d(spos+6);
          if( isDate(_data,spos) ) throw H2O.unimpl();
          _ds[col] = d;
          break;
        }
        case SPREADSHEET_EXCEL_READER_TYPE_MULBLANK: {
          int row = row(spos);
          int col = _data.get2(spos+2);
          int cols= (length / 2) - 3;
          for( int c = 0; c < cols; c++ ) {
            if( isDate( _data, spos+(c*2)) ) throw H2O.unimpl();
            _ds[col+c] = 0;
          }
          break;
        }

        case SPREADSHEET_EXCEL_READER_TYPE_FORMULA:
        case SPREADSHEET_EXCEL_READER_TYPE_FORMULA2: throw H2O.unimpl();
          //row	= ord(_data[spos]) | ord(_data[spos+1])<<8;
          //column = ord(_data[spos+2]) | ord(_data[spos+3])<<8;
          //if ((ord(_data[spos+6])==0) && (ord(_data[spos+12])==255) && (ord(_data[spos+13])==255)) {
          //  //String formula. Result follows in a STRING record
          //  // This row/col are stored to be referenced in that record
          //  // http://code.google.com/p/php-excel-reader/issues/detail?id=4
          //  previousRow = row;
          //  previousCol = column;
          //} elseif ((ord(_data[spos+6])==1) && (ord(_data[spos+12])==255) && (ord(_data[spos+13])==255)) {
          //  //Boolean formula. Result is in +2; 0=false,1=true
          //  // http://code.google.com/p/php-excel-reader/issues/detail?id=4
          //  if (ord(this->_data[spos+8])==1) {
          //    this->addcell(row, column, "TRUE");
          //  } else {
          //    this->addcell(row, column, "FALSE");
          //  }
          //} elseif ((ord(_data[spos+6])==2) && (ord(_data[spos+12])==255) && (ord(_data[spos+13])==255)) {
          //  //Error formula. Error code is in +2;
          //} elseif ((ord(_data[spos+6])==3) && (ord(_data[spos+12])==255) && (ord(_data[spos+13])==255)) {
          //  //Formula result is a null string.
          //  this->addcell(row, column, '');
          //} else {
          //  // result is a number, so first 14 bytes are just like a _NUMBER record
          //  tmp = unpack("ddouble", substr(_data, spos + 6, 8)); // It machine machine dependent
          //  if (this->isDate(spos)) {
          //    numValue = tmp['double'];
          //  }
          //  else {
          //    numValue = this->createNumber(spos);
          //  }
          //  info = this->_getCellDetails(spos,numValue,column);
          //  this->addcell(row, column, info['string'], info);
          //}
          //break;
        case SPREADSHEET_EXCEL_READER_TYPE_BOOLERR: throw H2O.unimpl();
          //row	= ord(_data[spos]) | ord(_data[spos+1])<<8;
          //column = ord(_data[spos+2]) | ord(_data[spos+3])<<8;
          //string = ord(_data[spos+6]);
          //this->addcell(row, column, string);
          //break;
        case SPREADSHEET_EXCEL_READER_TYPE_STRING: throw H2O.unimpl();
          //// http://code.google.com/p/php-excel-reader/issues/detail?id=4
          //if (version == SPREADSHEET_EXCEL_READER_BIFF8){
          //  // Unicode 16 string, like an SST record
          //  xpos = spos;
          //  numChars =ord(_data[xpos]) | (ord(_data[xpos+1]) << 8);
          //  xpos += 2;
          //  optionFlags =ord(_data[xpos]);
          //  xpos++;
          //  asciiEncoding = ((optionFlags &0x01) == 0) ;
          //  extendedString = ((optionFlags & 0x04) != 0);
          //  // See if string contains formatting information
          //  richString = ((optionFlags & 0x08) != 0);
          //  if (richString) {
          //    // Read in the crun
          //    formattingRuns =ord(_data[xpos]) | (ord(_data[xpos+1]) << 8);
          //    xpos += 2;
          //  }
          //  if (extendedString) {
          //    // Read in cchExtRst
          //    extendedRunLength =this->_GetInt4d(this->_data, xpos);
          //    xpos += 4;
          //  }
          //  len = (asciiEncoding)?numChars : numChars*2;
          //  retstr =substr(_data, xpos, len);
          //  xpos += len;
          //  retstr = (asciiEncoding)? retstr : this->_encodeUTF16(retstr);
          //}
          //elseif (version == SPREADSHEET_EXCEL_READER_BIFF7){
          //  // Simple byte string
          //  xpos = spos;
          //  numChars =ord(_data[xpos]) | (ord(_data[xpos+1]) << 8);
          //  xpos += 2;
          //  retstr =substr(_data, xpos, numChars);
          //}
          //this->addcell(previousRow, previousCol, retstr);
          //break;
        case SPREADSHEET_EXCEL_READER_TYPE_ROW: break; // While the original php file parsed the row info here, H2O just wants the _data
        case SPREADSHEET_EXCEL_READER_TYPE_DBCELL:
          break;
        case SPREADSHEET_EXCEL_READER_TYPE_LABEL: throw H2O.unimpl();
          //row	= ord(_data[spos]) | ord(_data[spos+1])<<8;
          //column = ord(_data[spos+2]) | ord(_data[spos+3])<<8;
          //this->addcell(row, column, substr(_data, spos + 8, ord(_data[spos + 6]) | ord(_data[spos + 7])<<8));
          //break;
        case SPREADSHEET_EXCEL_READER_TYPE_EOF:
          // Push out the final row
          doRow(_currow+1);
          return true;
        case SPREADSHEET_EXCEL_READER_TYPE_HYPER: throw H2O.unimpl();
          ////  Only handle hyperlinks to a URL
          //row	= ord(this->_data[spos]) | ord(this->_data[spos+1])<<8;
          //row2   = ord(this->_data[spos+2]) | ord(this->_data[spos+3])<<8;
          //column = ord(this->_data[spos+4]) | ord(this->_data[spos+5])<<8;
          //column2 = ord(this->_data[spos+6]) | ord(this->_data[spos+7])<<8;
          //linkData = Array();
          //flags = ord(this->_data[spos + 28]);
          //udesc = "";
          //ulink = "";
          //uloc = 32;
          //linkData['flags'] = flags;
          //if ((flags & 1) > 0 ) {   // is a type we understand
          //  //  is there a description ?
          //  if ((flags & 0x14) == 0x14 ) {   // has a description
          //    uloc += 4;
          //    descLen = ord(this->_data[spos + 32]) | ord(this->_data[spos + 33]) << 8;
          //    udesc = substr(this->_data, spos + uloc, descLen * 2);
          //    uloc += 2 * descLen;
          //  }
          //  ulink = this->read16bitstring(this->_data, spos + uloc + 20);
          //  if (udesc == "") {
          //    udesc = ulink;
          //  }
          //}
          //linkData['desc'] = udesc;
          //linkData['link'] = this->_encodeUTF16(ulink);
          //for (r=row; r<=row2; r++) { 
          //  for (c=column; c<=column2; c++) {
          //    this['cellsInfo'][r+1][c+1]['hyperlink'] = linkData;
          //  }
          //}
          //break;
        case SPREADSHEET_EXCEL_READER_TYPE_DEFCOLWIDTH: break; // Set default column width
        case SPREADSHEET_EXCEL_READER_TYPE_STANDARDWIDTH: break; // While the original php file parsed the standard width here, H2O just wants the _data
        case SPREADSHEET_EXCEL_READER_TYPE_COLINFO: break; // While the original php file parsed the column info here, H2O just wants the _data

        default:
          break;
        }
        spos += length;
      }
    }
  }

  boolean isDate( Buf data, int spos ) {
    int xfindex = data.get2(spos+4);
    return _xfRecords.get(xfindex)._type == XF.Type.Date;
  }

  static double _GetIEEE754(long rknum) {
    double value;
    if( (rknum & 0x02) != 0) {
      value = rknum >> 2;
    } else {
      //mmp
      // I got my info on IEEE754 encoding from
      // http://research.microsoft.com/~hollasch/cgindex/coding/ieeefloat.html
      // The RK format calls for using only the most significant 30 bits of the
      // 64 bit floating point value. The other 34 bits are assumed to be 0
      // So, we use the upper 30 bits of rknum as follows...
      int exp = (int)((rknum & 0x7ff00000L) >> 20);
      long mantissa = (0x100000 | (rknum & 0x000ffffc));
      value = mantissa / Math.pow( 2 , (20- (exp - 1023)));
      if( ((rknum & 0x80000000) >> 31) != 0 ) value *= -1;
      //end of changes by mmp
    }
    if( (rknum & 0x01) != 0 )
      value /= 100;
    return value;
  }


  // Ignore all encodings
  private String __encodeUTF16( String s ) { return s; }

}
