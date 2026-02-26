package water.fvec;

import water.DKV;
import water.Futures;
import water.Key;
import water.Value;

/**
 * A FileVec backed by Azure Blob Storage (wasb/wasbs) or Azure Data Lake Storage Gen2 (abfs/abfss).
 * Chunks are loaded lazily from Azure on demand, avoiding the need to download the entire file at import time.
 */
public class AzureFileVec extends FileVec {

  private AzureFileVec(Key key, long len) {
    super(key, len, Value.AZURE);
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
    Vec vec = new AzureFileVec(vecKey, size);
    DKV.put(vecKey, vec, fs);
    Frame frame = new Frame(frameKey, new String[]{path}, new Vec[]{vec});
    frame.update();
    frame.unlock();
    return frameKey;
  }
}
