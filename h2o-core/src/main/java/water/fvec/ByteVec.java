package water.fvec;

import water.Job;
import water.Key;
import water.Value;

import java.io.IOException;
import java.io.InputStream;

/**
 * A vector of plain Bytes.
 */
public class ByteVec extends Vec {

  public ByteVec( Key key, int rowLayout ) { super(key, rowLayout); }

  @Override public C1NChunk chunkForChunkIdx(int cidx) { return (C1NChunk)super.chunkForChunkIdx(cidx); }

  /** Return column missing-element-count - ByteVecs do not allow any "missing elements" */
  @Override public long naCnt() { return 0; }
  /** Is all integers?  Yes, it's all bytes */
  @Override public boolean isInt(){return true; }

  /** Get an unspecified amount of initial bytes; typically a whole C1NChunk of
   *  length Vec.DFLT_CHUNK_SIZE but no guarantees.  Useful for previewing the start
   *  of large files.
   *  @return array of initial bytes */
  public byte[] getFirstBytes() { 
    return getFirstChunkBytes();
  }

  final byte[] getFirstChunkBytes() {
    return chunkForChunkIdx(0)._mem;
  }

  public InputStream openStream() {
    return openStream(null);
  }

  /**
   * Open a stream view over the underlying data
   */
  public InputStream openStream(final Key job_key) {
    InputStream is = new InputStream() {
      final long[] sz = new long[1];
      private int _cidx, _pidx, _sz;
      private C1NChunk _c0;

      @Override
      public int available() {
        if (_c0 == null || _sz >= _c0._len) {
          sz[0] += _c0 != null ? _c0._len : 0;
          if (_cidx >= nChunks()) return 0;
          _c0 = chunkForChunkIdx(_cidx++);
          _sz = C1NChunk._OFF;
          if (job_key != null)
            Job.update(_c0._len, job_key);
        }
        return _c0._len - _sz;
      }

      @Override
      public void close() {
        _cidx = nChunks();
        _c0 = null;
        _sz = 0;
      }

      @Override
      public int read() throws IOException {
        return available() == 0 ? -1 : 0xFF & _c0._mem[_sz++];
      }

      @Override
      public int read(byte[] b, int off, int len) {
        if (b == null) { // Back-channel read of cidx
          if (_cidx > _pidx) { // Remove prev chunk from memory
            Value v = Value.STORE_get(chunkKey(_pidx++));
            if (v != null && v.isPersisted()) {
              v.freePOJO();           // Eagerly toss from memory
              v.freeMem();
            } // Else not found, or not on disk somewhere
          }
          return _cidx;
        }
        int sz = available();
        if (sz == 0)
          return -1;
        len = Math.min(len, sz);
        System.arraycopy(_c0._mem, _sz, b, off, len);
        _sz += len;
        return len;
      }
    };
    try {
      is.available();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return is;
  }
}
