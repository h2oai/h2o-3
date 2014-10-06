package water.fvec;

import java.io.IOException;
import java.io.InputStream;

import water.Key;
import water.Job;

/**
 * A vector of plain Bytes.
 */
public class ByteVec extends Vec {

  public ByteVec( Key key, long espc[] ) { super(key,espc); }

  @Override public C1NChunk chunkForChunkIdx(int cidx) { return (C1NChunk)super.chunkForChunkIdx(cidx); }

  /** Return column missing-element-count - ByteVecs do not allow any "missing elements" */
  @Override public long naCnt() { return 0; }
  /** Is all integers?  Yes, it's all bytes */
  @Override public boolean isInt(){return true; }

  /** Get an unspecified amount of initial bytes; typically a whole C1NChunk of
   *  length Vec.CHUNK_SZ but no guarantees.  Useful for previewing the start
   *  of large files.
   *  @return array of initial bytes */
  public byte[] getFirstBytes() { return chunkForChunkIdx(0)._mem; }

  /** Open a stream view over the underlying data  */
  public InputStream openStream(final Key job_key) {
    return new InputStream() {
      final long [] sz = new long[1];
      private int _cidx, _sz;
      private C1NChunk _c0;
      @Override public int available() {
        if( _c0 == null || _sz >= _c0._len) {
          sz[0] += _c0 != null? _c0._len :0;
          if( _cidx >= nChunks() ) return 0;
          _c0 = chunkForChunkIdx(_cidx++);
          _sz = C1NChunk._OFF;
          Job.update(_c0._len,job_key);
        }
        return _c0._len -_sz;
      }
      @Override public void close() { _cidx = nChunks(); _c0 = null; _sz = 0;}
      @Override public int read() throws IOException {
        return available() == 0 ? -1 : 0xFF&_c0._mem[_sz++];
      }
      @Override public int read(byte[] b, int off, int len) {
        if( b==null ) return _cidx;// Back-channel read of cidx
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
