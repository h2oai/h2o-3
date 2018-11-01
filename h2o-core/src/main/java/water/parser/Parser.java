package water.parser;

import water.H2O;
import water.Iced;
import water.Job;
import water.Key;
import water.fvec.Vec;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static water.parser.DefaultParserProviders.GUESS_INFO;

/** A collection of utility classes for parsing.
 *
 *  Interfaces:
 *  DataIn - Manage bulk streaming input data to the parser.  Sometimes the data
 *           comes from parallel raw byte file reads, with speculative line
 *           starts.  Sometimes the data comes from an InputStream - probably a
 *           GZIP stream.
 *  DataOut- Interface for writing results of parsing, accumulating numbers and
 *           strings or handling invalid lines &amp; parse errors.
 *
 *  static classes:
 *  StreamData  - Class implementing DataIn from a Stream (probably a GZIP stream)
 *  InspectDataOut - Class implementing DataOut, on behalf of the GUI, for
 *                parsing &amp; previewing the first few lines &amp; columns of a file.
 */
public abstract class Parser extends Iced {
  static final byte CHAR_TAB = '\t';
  static final byte CHAR_CR = 13;
  static final byte CHAR_LF = 10;
  static final byte CHAR_SPACE = ' ';
  static final byte CHAR_DOUBLE_QUOTE = '"';
  static final byte CHAR_SINGLE_QUOTE = '\'';

  // State for the CSV & SVMLight Parser's FSAs
  protected static final byte SKIP_LINE = 0;
  protected static final byte EXPECT_COND_LF = 1;
  protected static final byte EOL = 2;
  protected static final byte TOKEN = 3;
  protected static final byte COND_QUOTED_TOKEN = 4;
  protected static final byte NUMBER = 5;
  protected static final byte NUMBER_SKIP = 6;
  protected static final byte NUMBER_SKIP_NO_DOT = 7;
  protected static final byte NUMBER_FRACTION = 8;
  protected static final byte NUMBER_EXP = 9;
  protected static final byte NUMBER_EXP_START = 11;
  protected static final byte NUMBER_END = 12;
  protected static final byte STRING = 13;
  protected static final byte COND_QUOTE = 14;
  protected static final byte SEPARATOR_OR_EOL = 15;
  protected static final byte WHITESPACE_BEFORE_TOKEN = 16;
  protected static final byte STRING_END = 17;
  protected static final byte COND_QUOTED_NUMBER_END = 18;
  protected static final byte POSSIBLE_EMPTY_LINE = 19;
  protected static final byte POSSIBLE_CURRENCY = 20;
  protected static final byte HASHTAG = 35;
  protected static final byte POSSIBLE_ESCAPED_QUOTE = 36;

  protected final byte CHAR_DECIMAL_SEP = '.';
  protected final byte CHAR_SEPARATOR;

  protected static final long LARGEST_DIGIT_NUMBER = Long.MAX_VALUE/10;
  protected static boolean isEOL(byte c) { return (c == CHAR_LF) || (c == CHAR_CR); }
  public boolean[] _keepColumns;

  protected final ParseSetup _setup;
  protected final Key<Job> _jobKey;
  protected Parser( ParseSetup setup, Key<Job> jobKey ) {
    _setup = setup;  CHAR_SEPARATOR = setup._separator; _jobKey = jobKey;
    if (_setup!=null && _setup._number_columns > 0) {
      _keepColumns = new boolean[_setup._number_columns];
      for (int colIdx = 0; colIdx < _setup._number_columns; colIdx++)
        _keepColumns[colIdx] = true;
      if (_setup._skipped_columns!=null) {
        for (int colIdx : _setup._skipped_columns)
          if (colIdx < _setup._number_columns)
            _keepColumns[colIdx] = false;
          else
            throw new IllegalArgumentException("Skipped column index "+colIdx+" is illegal.  It exceeds the actual" +
                    " number of columns in your file.");
      }
    }
  }
  protected int fileHasHeader(byte[] bits, ParseSetup ps) { return ParseSetup.NO_HEADER; }

  // Parse this one Chunk (in parallel with other Chunks)
  protected abstract ParseWriter parseChunk(int cidx, final ParseReader din, final ParseWriter dout);


  // Parse the Vec sequentially writing out one chunk after another
  protected StreamParseWriter sequentialParse(Vec vec, StreamParseWriter dout) {
    throw new UnsupportedOperationException("Sequential Parsing is not supported by " + this.getClass().getName());
  }

  protected ParseWriter streamParse( final InputStream is, final StreamParseWriter dout) throws IOException {
    return streamParseZip(is,dout,is);
  }

  /**
   *   This method performs guess setup with each file.  If will return true only if the number of columns/separator
   *   found in the current file match that of files parsed earlier.  In addition, it will also check for headers
   *   within a file.  However, it will only check for headers if the user has included column names in the very
   *   first file.
   *
   * @param is
   * @param dout
   * @param din
   * @param cidx
   * @return
   * @throws IOException
   */
  private boolean checkFileNHeader(final InputStream is, final StreamParseWriter dout, StreamData din, int cidx)
          throws IOException {
    byte[] headerBytes = ZipUtil.unzipForHeader(din.getChunkData(cidx), this._setup._chunk_size);
    ParseSetup ps = ParseSetup.guessSetup(null, headerBytes, new ParseSetup(GUESS_INFO, ParseSetup.GUESS_SEP,
            this._setup._single_quotes, ParseSetup.GUESS_HEADER, ParseSetup.GUESS_COL_CNT, null, null));
    // check to make sure datasets in file belong to the same dataset
    // just check for number for number of columns/separator here.  Ignore the column type, user can force it
    if ((this._setup._number_columns != ps._number_columns) || (this._setup._separator != ps._separator)) {
      String warning = "Your zip file contains a file that belong to another dataset with different " +
              "number of column or separator.  Number of columns for files that have been parsed = "+
              this._setup._number_columns + ".  Number of columns in new file = "+ps._number_columns+
              ".  This new file is skipped and not parsed.";
      dout.addError(new ParseWriter.ParseErr(warning, -1, -1L, -2L));
      // something is wrong
      return false;
    } else {
      // assume column names must appear in the first file.  If column names appear in first and other
      // files, they will be recognized.  Otherwise, if no column name ever appear in the first file, the other
      // column names in the other files will not be recognized.
      if (ps._check_header == ParseSetup.HAS_HEADER) {
        if (this._setup._column_names != null) {
          // found header in later files, only incorporate it if the column names are the same as before
          String[] thisColumnName = this._setup.getColumnNames();
          String[] psColumnName = ps.getColumnNames();
          Boolean sameColumnNames = true;
          for (int index = 0; index < this._setup._number_columns; index++) {
            if (!(thisColumnName[index].equals(psColumnName[index]))) {
              sameColumnNames = false;
              break;
            }
          }
          if (sameColumnNames)  // only recognize current file header if it has the same column names as previous files
            this._setup.setCheckHeader(ps._check_header);
        }
      } else  // should refresh _setup with correct check_header
        this._setup.setCheckHeader(ps._check_header);
    }
    return true;  // everything is fine
  }

  /**
   * This method will try to get the next file to be parsed.  It will skip over directories if encountered.
   *
   * @param is
   * @throws IOException
   */
  private void getNextFile(final InputStream is) throws IOException {
    if (is instanceof  java.util.zip.ZipInputStream) {
      ZipEntry ze = ((ZipInputStream) is).getNextEntry();
      while (ze != null && ze.isDirectory())
        ze = ((ZipInputStream) is).getNextEntry();
    }
  }

  private class StreamInfo {
    int _zidx;
    StreamParseWriter _nextChunk;
    StreamInfo(int zidx, StreamParseWriter nextChunk) {
      this._zidx = zidx;
      this._nextChunk = nextChunk;
    }
  }

  /**
   * This method reads in one zip file.  Before reading the file, it will check if the current file has the same
   * number of columns and separator type as the previous files it has parssed.  If they do not match, no file will
   * be parsed in this case.
   *
   * @param is
   * @param dout
   * @param bvs
   * @param nextChunk
   * @param zidx
   * @return
   * @throws IOException
   */
  private StreamInfo readOneFile(final InputStream is, final StreamParseWriter dout, InputStream bvs,
                                 StreamParseWriter nextChunk, int zidx, int fileIndex) throws IOException {
    int cidx = 0;
    StreamData din = new StreamData(is);
    // only check header for 2nd file onward since guess setup is already done on first file.
    if ((fileIndex > 0) && (!checkFileNHeader(is, dout, din, cidx))) // cidx should be the actual column index
      return new StreamInfo(zidx, nextChunk);  // header is bad, quit now
    int streamAvailable = is.available();
    while (streamAvailable > 0) {
      parseChunk(cidx++, din, nextChunk); // cidx here actually goes and get the right column chunk.
      streamAvailable = is.available(); // Can (also!) rollover to the next input chunk
      int xidx = bvs.read(null, 0, 0); // Back-channel read of chunk index
      if (xidx > zidx) {  // Advanced chunk index of underlying ByteVec stream?
        zidx = xidx;       // Record advancing of chunk
        nextChunk.close(); // Match output chunks to input zipfile chunks
        if (dout != nextChunk) {
          dout.reduce(nextChunk);
          if (_jobKey != null && _jobKey.get().stop_requested()) break;
        }
        nextChunk = nextChunk.nextChunk();
      }
    }
    parseChunk(cidx, din, nextChunk);
    return new StreamInfo(zidx, nextChunk);
  }


  // ------------------------------------------------------------------------
  // Zipped file; no parallel decompression; decompress into local chunks,
  // parse local chunks; distribute chunks later.
  protected ParseWriter streamParseZip( final InputStream is, final StreamParseWriter dout, InputStream bvs ) throws IOException {
    // All output into a fresh pile of NewChunks, one per column
    if (!_setup._parse_type.isParallelParseSupported) throw H2O.unimpl();
    StreamParseWriter nextChunk = dout;
    int zidx = bvs.read(null, 0, 0); // Back-channel read of chunk index
    assert zidx == 1;
    int fileIndex = 0;  // count files being passed.  0 is first file, 1 is second and so on...
    StreamInfo streamInfo = new StreamInfo(zidx, nextChunk);
    while (is.available() > 0) {  // loop over all files in zip file
      streamInfo = readOneFile(is, dout, bvs, streamInfo._nextChunk, streamInfo._zidx, fileIndex++); // read one file in
//      streamInfo = readOneFile(is, dout, bvs, nextChunk, streamInfo._zidx, fileIndex++); // read one file in
      if (is.available() <= 0) {  // done reading one file, get the next one or quit if at the end
        getNextFile(is);
      }
    }
    streamInfo._nextChunk.close();
    bvs.close();
    is.close();
    if( dout != nextChunk ) dout.reduce(nextChunk);
    return dout;
  }

  final static class ByteAryData implements ParseReader {
    private final byte [] _bits;
    public int _off;
    final long _globalOffset;

    public ByteAryData(byte [] bits, long globalOffset){
      _bits = bits;
      _globalOffset = globalOffset;
    }

    @Override
    public byte[] getChunkData(int cidx) {
      return cidx == 0?_bits:null;
    }

    @Override
    public int getChunkDataStart(int cidx) {return -1;}

    @Override
    public void setChunkDataStart(int cidx, int offset) {
      if(cidx == 0) _off = offset;
    }

    @Override
    public long getGlobalByteOffset() {return _globalOffset;}
  }
  /** Class implementing DataIns from a Stream (probably a GZIP stream)
   *  Implements a classic double-buffer reader.
   */
  final static class StreamData implements ParseReader {
    final int bufSz;
    final transient InputStream _is;
    private byte[] _bits0;
    private byte[] _bits1;
    private int _cidx0=-1, _cidx1=-1; // Chunk #s
    private int _coff0=-1, _coff1=-1; // Last used byte in a chunk
    protected StreamData(InputStream is){this(is,64*1024);}
    protected StreamData(InputStream is, int bufSz){
      _is = is; this.bufSz = bufSz;
      _bits0 = new byte[bufSz];
      _bits1 = new byte[bufSz];
    }
    long _gOff;
    @Override public byte[] getChunkData(int cidx) {
      if( cidx == _cidx0 ) return _bits0;
      _gOff = _bits0.length;
      if( cidx == _cidx1 ) return _bits1;
      assert cidx==_cidx0+1 || cidx==_cidx1+1;
      byte[] bits = _cidx0<_cidx1 ? _bits0 : _bits1;
      _gOff += bits.length;
      if( _cidx0<_cidx1 ) { _cidx0 = cidx; _coff0 = -1; }
      else                { _cidx1 = cidx; _coff1 = -1; }
      // Read as much as the buffer will hold
      int off=0;
      try {
        while( off < bits.length ) {
          int len = _is.read(bits,off,bits.length-off);
          if( len == -1 ) break;
          off += len;
        }
        assert off == bits.length || _is.available() <= 0;
      } catch( IOException ioe ) {
        throw new RuntimeException(ioe);
      }
      if( off == bits.length ) return bits;
      // Final read is short; cache the short-read
      byte[] bits2 = (off == 0) ? null : Arrays.copyOf(bits,off);
      if( _cidx0==cidx ) _bits0 = bits2;
      else               _bits1 = bits2;
      return bits2;
    }
    @Override public int getChunkDataStart(int cidx) {
      if( _cidx0 == cidx ) return _coff0;
      if( _cidx1 == cidx ) return _coff1;
      return 0;
    }
    @Override public void setChunkDataStart(int cidx, int offset) {
      if( _cidx0 == cidx ) _coff0 = offset;
      if( _cidx1 == cidx ) _coff1 = offset;
    }

    @Override
    public long getGlobalByteOffset() {
      return 0;
    }
  }
}
