package water.fvec;

import water.*;

/**
 * Vec representation of file stored on HDFS.
 */
public final class HDFSFileVec extends FileVec {
  private HDFSFileVec(Key key, long len) {
    super(key, len, Value.HDFS);
  }

  public static Key make(String path, long size) {
    Futures fs = new Futures();
    Key key = make(path, size, fs);
    fs.blockForPending();
    return key;
  }
  public static Key make(String path, long size, Futures fs) {
    Key k = Key.make(path);
    Key k2 = Vec.newKey(k);
    new Frame(k).delete_and_lock();
    // Insert the top-level FileVec key into the store
    Vec v = new HDFSFileVec(k2,size);
    DKV.put(k2, v, fs);
    Frame fr = new Frame(k,new String[]{path},new Vec[]{v});
    fr.update();
    fr.unlock();
    return k;
  }

  @Override public int setChunkSize(Frame fr, int chunkSize) {
    // Clear cached chunks first
    // Peeking into a file before the chunkSize has been set
    // will load chunks of the file in DFLT_CHUNK_SIZE amounts.
    // If this side-effect is not reversed when _chunkSize differs
    // from the default value, parsing will either double read
    // sections (_chunkSize < DFLT_CHUNK_SIZE) or skip data
    // (_chunkSize > DFLT_CHUNK_SIZE). This reverses this side-effect.
    Futures fs = new Futures();
    Keyed.remove(_key, fs);
    fs.blockForPending();

    return super.setChunkSize(fr, chunkSize);
  }
}
