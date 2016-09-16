package water.fvec;

import java.util.Arrays;
import java.io.InputStream;
import water.*;
import water.util.Log;

/** Build a Vec by reading from an InputStream
 */
public class UploadFileVec extends FileVec {
  protected UploadFileVec(Key key) { super(key,0,Value.ICE); }

  @Override public boolean writable() { return false; }

  public Futures addAndCloseChunk(int cidx, Chunk c, Futures fs) {
    _len += c.len();
    DKV.put(chunkKey(cidx),new Chunks(c),fs);
    return fs;
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
    public long total_chunks;
    public long total_bytes;
  }

  static public Key readPut(String keyname, InputStream is, ReadPutStats stats) throws Exception {
    return readPut(Key.make(keyname), is, stats);
  }

  static public Key readPut(Key k, InputStream is, ReadPutStats stats) throws Exception {
    return readPut_impl(k, is, stats);
  }

  static private Key readPut_impl(Key key, InputStream is, ReadPutStats stats) throws Exception {
    Log.info("Reading byte InputStream into Frame:");
    Log.info("    frameKey:    " + key.toString());
    Key newVecKey = Vec.newKey();
    UploadFileVec uv = null;
    try {
      new Frame(key,new String[0],new VecAry()).delete_and_lock();
      uv = new UploadFileVec(newVecKey);
      assert uv.writable();
      Futures fs = new Futures();
      byte bytebuf[] = new byte[FileVec.DFLT_CHUNK_SIZE];
      int bytesInChunkSoFar = 0;
      int cidx = 0;
      while (true) {
        int rv = is.read(bytebuf, bytesInChunkSoFar, FileVec.DFLT_CHUNK_SIZE - bytesInChunkSoFar);
        if (rv < 0) break;
        bytesInChunkSoFar += rv;
        if( bytesInChunkSoFar == FileVec.DFLT_CHUNK_SIZE ) {
          // Write full chunk of size FileVec.CHUNK_SZ.
          C1NChunk c = new C1NChunk(bytebuf);
          uv.addAndCloseChunk(cidx++,c, fs);
          bytebuf = new byte[FileVec.DFLT_CHUNK_SIZE];
          bytesInChunkSoFar = 0;
        }
      }
      if(bytesInChunkSoFar > 0) { // last chunk can be a little smaller
        byte [] buf2 = Arrays.copyOf(bytebuf,bytesInChunkSoFar);
        uv.addAndCloseChunk(cidx-1,new C1NChunk(buf2),fs);
      }
      if( stats != null ) {
        stats.total_chunks = uv.nChunks();
        stats.total_bytes  = uv.length();
      }
      Log.info("    totalChunks: " + uv.nChunks());
      Log.info("    totalBytes:  " + uv.length());

      DKV.put(newVecKey, uv, fs);
      fs.blockForPending();
      Frame f = new Frame(key,new String[]{"bytes"}, new VecAry(uv));
      f.unlock();

      Log.info("    Success.");
    }
    catch (Exception e) {
      // Clean up and do not leak keys.
      Log.err("Exception caught in Frame::readPut; attempting to clean up the new frame and vector");
      Log.err(e);
      Lockable.delete(key);
      if( uv != null ) uv.remove(newVecKey);
      Log.err("Frame::readPut cleaned up new frame and vector successfully");
      throw e;
    }
    return key;
  }

  @Override // not supported for now, can do rebalance later
  public int setChunkSize(Frame fr, int chunkSize) {return _chunkSize;}
}
