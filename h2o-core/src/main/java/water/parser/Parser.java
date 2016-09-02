package water.parser;

import water.H2O;
import water.Iced;
import water.Job;
import water.Key;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.zip.ZipInputStream;

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

  protected final byte CHAR_DECIMAL_SEP = '.';
  protected final byte CHAR_SEPARATOR;

  protected static final long LARGEST_DIGIT_NUMBER = Long.MAX_VALUE/10;
  protected static boolean isEOL(byte c) { return (c == CHAR_LF) || (c == CHAR_CR); }

  protected final ParseSetup _setup;
  protected final Key<Job> _jobKey;
  protected Parser( ParseSetup setup, Key<Job> jobKey ) { _setup = setup;  CHAR_SEPARATOR = setup._separator; _jobKey = jobKey;}
  protected int fileHasHeader(byte[] bits, ParseSetup ps) { return ParseSetup.NO_HEADER; }

  // Parse this one Chunk (in parallel with other Chunks)
  protected abstract ParseWriter parseChunk(int cidx, final ParseReader din, final ParseWriter dout);

  ParseWriter streamParse( final InputStream is, final ParseWriter dout) throws IOException {
    if (!_setup._parse_type.isParallelParseSupported) throw H2O.unimpl();
    StreamData din = new StreamData(is);
    int cidx=0;
    // FIXME leaving _jobKey == null until sampling is done, this mean entire zip files
    // FIXME are parsed for parseSetup
    while( is.available() > 0 && (_jobKey == null || _jobKey.get().stop_requested()) )
      parseChunk(cidx++, din, dout);
    parseChunk(cidx, din, dout);     // Parse the remaining partial 32K buffer
    return dout;
  }
  // ------------------------------------------------------------------------
  // Zipped file; no parallel decompression; decompress into local chunks,
  // parse local chunks; distribute chunks later.
  ParseWriter streamParseZip( final InputStream is, final StreamParseWriter dout, InputStream bvs ) throws IOException {
    // All output into a fresh pile of NewChunks, one per column
    if (!_setup._parse_type.isParallelParseSupported) throw H2O.unimpl();
    StreamData din = new StreamData(is);
    int cidx = 0;
    StreamParseWriter nextChunk = dout;
    int zidx = bvs.read(null, 0, 0); // Back-channel read of chunk index
    assert zidx == 1;
//    int count = 0;

    while (is.available() > 0) {
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

      if (cidx == 0) {
        // assume column names must appear in the first file.  It can appear elsewhere as well.
        if (this._setup._check_header == ParseSetup.HAS_HEADER) { //check for header on local file
          this._setup._check_header =
                  this._setup.parser(_jobKey).fileHasHeader(ZipUtil.unzipForHeader(din.getChunkData(cidx),
                          this._setup._chunk_size), this._setup);
        }
      }

      parseChunk(cidx++, din, nextChunk);

      if (is.available() <= 0) {
        parseChunk(cidx, din, nextChunk);     // Parse the remaining partial 32K buffer

        if (is instanceof  java.util.zip.ZipInputStream)
          ((ZipInputStream) is).getNextEntry();   // move to next file if it exists

        if (is.available() > 0) {
          din = new StreamData(is);
          cidx = 0;
        }
      }
    }

    nextChunk.close();
    bvs.close();
    is.close();

    if( dout != nextChunk ) dout.reduce(nextChunk);
    return dout;
  }

  /** Class implementing DataIns from a Stream (probably a GZIP stream)
   *  Implements a classic double-buffer reader.
   */
  final static class StreamData implements ParseReader {
    public static int bufSz = 64*1024;
    final transient InputStream _is;
    private byte[] _bits0 = new byte[bufSz];
    private byte[] _bits1 = new byte[bufSz];
    private int _cidx0=-1, _cidx1=-1; // Chunk #s
    private int _coff0=-1, _coff1=-1; // Last used byte in a chunk
    private StreamData(InputStream is){_is = is;}
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
