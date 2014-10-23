package water.fvec;

import org.apache.hadoop.fs.FileStatus;
import water.DKV;
import water.Futures;
import water.Key;
import water.Value;

/**
 * Vec representation of file stored on HDFS.
 */
public class HDFSFileVec extends FileVec {
  private HDFSFileVec(Key key, long len) {
    super(key, len, Value.HDFS);
  }

  public static Key make(FileStatus f) {
    Futures fs = new Futures();
    Key key = make(f, fs);
    fs.blockForPending();
    return key;
  }
  public static Key make(FileStatus f, Futures fs) {
    long size = f.getLen();
    String fname = f.getPath().toString();
    Key k = Key.make(fname);
    Key k2 = Vec.newKey(k);
    new Frame(k).delete_and_lock(null);
    // Insert the top-level FileVec key into the store
    Vec v = new HDFSFileVec(k2,size);
    DKV.put(k2, v, fs);
    Frame fr = new Frame(k,new String[]{fname},new Vec[]{v});
    fr.update(null);
    fr.unlock(null);
    return k;
  }
}
