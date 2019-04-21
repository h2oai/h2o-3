package water.fvec;

import water.*;

/**
 * FileVec backed by an HTTP/HTTPS data source
 */
public class HTTPFileVec extends FileVec {

  private HTTPFileVec(Key key, long len) {
    super(key, len, Value.HTTP);
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
    Vec v = new HTTPFileVec(k2, size);
    DKV.put(k2, v, fs);
    Frame fr = new Frame(k, new String[]{path}, new Vec[]{v});
    fr.update();
    fr.unlock();
    return k;
  }

}