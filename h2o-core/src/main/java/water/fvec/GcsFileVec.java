package water.fvec;

import water.DKV;
import water.Futures;
import water.Key;
import water.Value;

public class GcsFileVec extends FileVec {

  private GcsFileVec(Key key, long len) {
    super(key, len, Value.GCS);
  }

  public static Key make(String path, long size) {
    Futures fs = new Futures();
    Key<Frame> key = make(path, size, fs);
    fs.blockForPending();
    return key;
  }

  public static Key<Frame> make(String path, long size, Futures fs) {
    Key<Frame> frameKey = Key.make(path);
    Key<Vec> vecKey = Vec.newKey(frameKey);
    new Frame(frameKey).delete_and_lock();
    // Insert the top-level FileVec key into the store
    Vec vec = new GcsFileVec(vecKey, size);
    DKV.put(vecKey, vec, fs);
    Frame frame = new Frame(frameKey, new String[]{path}, new Vec[]{vec});
    frame.update();
    frame.unlock();
    return frameKey;
  }
}
