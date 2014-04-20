package water.fvec;

import java.io.IOException;
import java.io.InputStream;
import water.Job.ProgressMonitor;
import water.Key;

/**
 * A vector of plain Bytes.
 */
public class ByteVec extends Vec {

  ByteVec( Key key, long espc[] ) { super(key,espc); }

  @Override public C1NChunk chunkForChunkIdx(int cidx) { return (C1NChunk)super.chunkForChunkIdx(cidx); }

  // Get an unspecified amount of initial bytes; typically a whole C1NChunk of
  // length Vec.CHUNK_SZ but no guarantees.
  public byte[] getFirstBytes() { return chunkForChunkIdx(0).getBytes(); }

  /** Open a stream view over the underlying data  */
  public InputStream openStream(final ProgressMonitor pmon) {
    return new InputStream() {
      final long [] sz = new long[1];
      private int _cidx, _sz;
      private C1NChunk _c0;
      @Override public int available() {
        if( _c0 == null || _sz >= _c0._len ) {
          sz[0] += _c0 != null?_c0._len:0;
          if( _cidx >= nChunks() ) return 0;
          _c0 = chunkForChunkIdx(_cidx++);
          _sz = C1NChunk.OFF;
          if( pmon != null ) pmon.update(_c0._len);
        }
        return _c0._len-_sz;
      }
      @Override public void close() { _cidx = nChunks(); _c0 = null; _sz = 0;}
      @Override public int read() throws IOException {
        return available() == 0 ? -1 : 0xFF&_c0._mem[_sz++];
      }
      @Override public int read(byte[] b, int off, int len) {
        int sz = available();
        if( sz == 0 )
          return -1;
        len = Math.min(len,sz);
        System.arraycopy(_c0._mem,_sz,b,off,len);
        _sz += len;
        return len;
      }
    };
  }
}
