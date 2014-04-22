package water.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import water.*;

/** A collection of utility classes for parsing.
 *
 *  Interfaces:
 *  DataIn - Manage bulk streaming input data to the parser.  Sometimes the data
 *           comes from parallel raw byte file reads, with speculative line
 *           starts.  Sometimes the data comes from an InputStream - probably a
 *           GZIP stream.
 *  DataOut- Interface for writing results of parsing, accumulating numbers and
 *           strings (enums) or handling invalid lines & parse errors.
 *
 *  static classes:
 *  StreamData  - Class implementing DataIn from a Stream (probably a GZIP stream)
 *  InspectDataOut - Class implementing DataOut, on behalf of the GUI, for
 *                parsing & previewing the first few lines & columns of a file.
 */
abstract class Parser extends Iced {
  private final ParserSetup _setup;
  Parser( ParserSetup setup ) { _setup = setup; }

  protected final boolean isCompatible(Parser p) { return _setup == p._setup || (_setup != null && _setup.isCompatible(p._setup)); }

  // Does this parser flavor support parallel parsing?
  abstract boolean parallelParseSupported();
  // Parse this one Chunk (in parallel with other Chunks)
  abstract DataOut parallelParse(int cidx, final DataIn din, final DataOut dout);

  private DataOut streamParse( final InputStream is, final DataOut dout) throws Exception {
    if(_setup._pType._parallelParseSupported){
      StreamData din = new StreamData(is);
      int cidx=0;
      while( is.available() > 0 )
        parallelParse(cidx++,din,dout);
      parallelParse(cidx++,din,dout);     // Parse the remaining partial 32K buffer
    } else {
      throw H2O.unimpl();
    }
    return dout;
  }

  // ------------------------------------------------------------------------
  // Zipped file; no parallel decompression; decompress into local chunks,
  // parse local chunks; distribute chunks later.
  private DataOut streamParse( final InputStream is, final StreamDataOut dout, ParseDataset2.ParseProgressMonitor pmon) throws IOException {
    // All output into a fresh pile of NewChunks, one per column
    if(_setup._pType._parallelParseSupported){
      StreamData din = new StreamData(is);
      int cidx=0;
      StreamDataOut nextChunk = dout;
      long lastProgress = pmon.progress();
      while( is.available() > 0 ){
        if(pmon.progress() > lastProgress){
          lastProgress = pmon.progress();
          nextChunk.close();
          if(dout != nextChunk)dout.reduce(nextChunk);
          nextChunk = nextChunk.nextChunk();
        }
        parallelParse(cidx++,din,nextChunk);
      }
      parallelParse(cidx++,din,nextChunk);     // Parse the remaining partial 32K buffer
      nextChunk.close();
      if(dout != nextChunk)dout.reduce(nextChunk);
    } else {
      throw H2O.unimpl();
    }
    return dout;
  }

  /** Manage bulk streaming input data to the parser.  Sometimes the data comes
   *  from parallel raw byte file reads, with speculative line starts.
   *  Sometimes the data comes from an InputStream - probably a GZIP stream.  */
  interface DataIn {
    // Get another chunk of byte data
    abstract byte[] getChunkData( int cidx );
    abstract int  getChunkDataStart( int cidx );
    abstract void setChunkDataStart( int cidx, int offset );
  }

  /** Interface for writing results of parsing, accumulating numbers and
   *  strings (enums) or handling invalid lines & parse errors.  */
  interface DataOut extends Freezable {
    void setColumnNames(String [] names);
    // Register a newLine from the parser
    void newLine();
    // True if already forced into a string column (skip number parsing)
    boolean isString(int colIdx);
    // Add a number column with given digits & exp
    void addNumCol(int colIdx, long number, int exp);
    // Add a number column with given digits & exp
    void addNumCol(int colIdx, double d);
    // An an invalid / missing entry
    void addInvalidCol(int colIdx);
    // Add a String column
    void addStrCol( int colIdx, ValueString str );
    // Final rolling back of partial line
    void rollbackLine();
    void invalidLine(String err);
    void invalidValue(int line, int col);
  }

  private interface StreamDataOut extends DataOut {
    StreamDataOut nextChunk();
    StreamDataOut reduce(StreamDataOut dout);
    StreamDataOut close();
    StreamDataOut close(Futures fs);
  }

  /** Class implementing DataIn from a Stream (probably a GZIP stream)
   */
  private static class StreamData implements Parser.DataIn {
    final transient InputStream _is;
    private byte[] _bits0 = new byte[64*1024];
    private byte[] _bits1 = new byte[64*1024];
    private int _cidx0=-1, _cidx1=-1; // Chunk #s
    private int _coff0=-1, _coff1=-1; // Last used byte in a chunk
    private StreamData(InputStream is){_is = is;}
    @Override public byte[] getChunkData(int cidx) {
      if( cidx == _cidx0 ) return _bits0;
      if( cidx == _cidx1 ) return _bits1;
      assert cidx==_cidx0+1 || cidx==_cidx1+1;
      byte[] bits = _cidx0<_cidx1 ? _bits0 : _bits1;
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
  }

  /** Class implementing DataOut, on behalf of the GUI, for parsing &
   *  previewing the first few lines & columns of a file.
   */
  protected static class InspectDataOut extends Iced implements DataOut {
    private int _nlines;
    private int _ncols;
    private int _invalidLines;
    private boolean _header;
    private String []   _colNames;
    private String [][] _data = new String[MAX_PREVIEW_LINES][MAX_PREVIEW_COLS];
    private final static int MAX_PREVIEW_COLS  = 100;
    private final static int MAX_PREVIEW_LINES = 50;
    transient ArrayList<String> _errors;
    private InspectDataOut() {
     for(int i = 0; i < MAX_PREVIEW_LINES;++i)
       Arrays.fill(_data[i],"NA");
    }
    private String [][] data(){
      String [][] res = Arrays.copyOf(_data, Math.min(MAX_PREVIEW_LINES, _nlines));
      for(int i = 0; i < res.length; ++i)
        res[i] = Arrays.copyOf(_data[i], Math.min(MAX_PREVIEW_COLS,_ncols));
      return (_data = res);
    }
    @Override public void setColumnNames(String[] names) {
      _colNames = names;
      _data[0] = names;
      ++_nlines;
      _ncols = names.length;
      _header = true;
    }
    @Override public void newLine() { ++_nlines; }
    @Override public boolean isString(int colIdx) { return false; }
    @Override public void addNumCol(int colIdx, long number, int exp) {
      if(colIdx < _ncols && _nlines < MAX_PREVIEW_LINES)
        _data[_nlines][colIdx] = Double.toString(number*water.util.PrettyPrint.pow10(exp));
    }
    @Override public void addNumCol(int colIdx, double d) {
      _ncols = Math.max(_ncols,colIdx);
      if(_nlines < MAX_PREVIEW_LINES && colIdx < MAX_PREVIEW_COLS)
        _data[_nlines][colIdx] = Double.toString(d);
    }
    @Override public void addInvalidCol(int colIdx) {
      if(colIdx < _ncols && _nlines < MAX_PREVIEW_LINES)
        _data[_nlines][colIdx] = "NA";
    }
    @Override public void addStrCol(int colIdx, ValueString str) {
      if(colIdx < _ncols && _nlines < MAX_PREVIEW_LINES)
        _data[_nlines][colIdx] = str.toString();
    }
    @Override public void rollbackLine() {--_nlines;}
    @Override public void invalidLine(String err) {
      ++_invalidLines;
      _errors.add("Error at line: " + _nlines + ", reason: " + err);
    }
    @Override public void invalidValue(int linenum, int colnum) {}
  }

}



