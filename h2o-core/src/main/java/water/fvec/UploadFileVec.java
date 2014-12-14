package water.fvec;

import java.util.Arrays;
import java.io.InputStream;
import water.*;
import water.util.Log;

/** Build a Vec by reading from an InputStream
 */
public class UploadFileVec extends FileVec {
  int _nchunks;
  protected UploadFileVec(Key key) { super(key,-1,Value.ICE); }

  @Override public boolean writable() { return _len==-1; }

  public void addAndCloseChunk(Chunk c, Futures fs) {
    assert _len==-1;            // Not closed
    assert (c._vec == null);    // Don't try to re-purpose a chunk.
    c._vec = this;              // Attach chunk to this vec.
    DKV.put(chunkKey(_nchunks++),c,fs); // Write updated chunk back into K/V
  }

  // Close, and possible replace the prior chunk with a new, larger Chunk
  public void close(C1NChunk c, int cidx, Futures fs) {
    assert _len==-1;            // Not closed
    c._vec = this;              // Attach chunk to this vec.
    DKV.put(chunkKey(cidx),c,fs); // Write updated chunk back into K/V
    _len = ((_nchunks-1L)<<LOG_CHK)+c._len;
  }

  private boolean checkMissing(int cidx, Value val) {
    if( val != null ) return true;
    Log.err("Missing chunk " + cidx + " for " + _key);
    return false;
  }

  @Override public Value chunkIdx( int cidx ) {
    Value val = DKV.get(chunkKey(cidx));
    assert checkMissing(cidx,val);
    return val;
  }

  // ---------------------------------------------------------------------------
  // Store a file (byte by byte) into a frame.
  // This file will generally come from a POST through the REST interface.
  // ---------------------------------------------------------------------------

  public static class ReadPutStats {
    public ReadPutStats() {}

    public long total_frames;
    public long total_vecs;
    public long total_chunks;
    public long total_bytes;
  }

  static public Key readPut(String keyname, InputStream is, ReadPutStats stats) throws Exception {
    return readPut(Key.make(keyname), is, stats);
  }

  static public Key readPut(Key k, InputStream is, ReadPutStats stats) throws Exception {
    readPut(k, is, stats, new Futures()).blockForPending();
    return k;
  }

  static private Futures readPut(Key key, InputStream is, ReadPutStats stats, final Futures fs) throws Exception {
    Log.info("Reading byte InputStream into Frame:");
    Log.info("    frameKey:    " + key.toString());
    Key newVecKey = Vec.newKey();

    try {
      new Frame(key,new String[0],new Vec[0]).delete_and_lock(null);

      UploadFileVec uv = new UploadFileVec(newVecKey);
      assert uv.writable();

      byte prev[] = null;
      byte bytebuf[] = new byte[FileVec.CHUNK_SZ];
      int bytesInChunkSoFar = 0;
      while (true) {
        int rv = is.read(bytebuf, bytesInChunkSoFar, FileVec.CHUNK_SZ - bytesInChunkSoFar);
        if (rv < 0) break;
        bytesInChunkSoFar += rv;
        if( bytesInChunkSoFar == FileVec.CHUNK_SZ ) {
          // Write full chunk of size FileVec.CHUNK_SZ.
          C1NChunk c = new C1NChunk(bytebuf);
          uv.addAndCloseChunk(c, fs);
          prev = bytebuf;
          bytebuf = new byte[FileVec.CHUNK_SZ];
          bytesInChunkSoFar = 0;
        }
      }

      // Add last bytes onto last chunk, which may be bigger than CHUNK_SZ.
      if( prev==null ) {      // No chunks at all
        uv._nchunks++;        // Put a 1st chunk
        uv.close(new C1NChunk(Arrays.copyOf(bytebuf,bytesInChunkSoFar)),0,fs);
      } else if (bytesInChunkSoFar != 0 ) {
        byte buf2[] = Arrays.copyOf(prev,bytesInChunkSoFar+prev.length);
        System.arraycopy(bytebuf,0,buf2,prev.length,bytesInChunkSoFar);
        uv.close(new C1NChunk(buf2),uv._nchunks-1,fs);
      }

      ReadPutStats stats_internal = (stats != null) ? stats : new ReadPutStats();
      stats_internal.total_frames = 1;
      stats_internal.total_vecs = 1;
      stats_internal.total_chunks = uv.nChunks();
      stats_internal.total_bytes = uv.length();
      Log.info("    totalFrames: " + stats_internal.total_frames);
      Log.info("    totalVecs:   " + stats_internal.total_vecs);
      Log.info("    totalChunks: " + stats_internal.total_chunks);
      Log.info("    totalBytes:  " + stats_internal.total_bytes);

      DKV.put(newVecKey, uv, fs);
      String[] sarr = {"bytes"};
      Vec[] varr = {uv};
      Frame f = new Frame(key,sarr, varr);
      f.unlock(null);

      Log.info("    Success.");
    }
    catch (Exception e) {
      // Clean up and do not leak keys.
      Log.err("Exception caught in Frame::readPut; attempting to clean up the new frame and vector");
      Log.err(e);
      Lockable.delete(key);
      DKV.remove(newVecKey);
      Log.err("Frame::readPut cleaned up new frame and vector successfully");
      throw e;
    }

    return fs;
  }
}
