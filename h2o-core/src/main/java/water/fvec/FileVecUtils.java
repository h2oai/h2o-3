package water.fvec;

import water.H2O;
import water.Key;
import water.Value;
import water.persist.PersistManager;

import java.io.IOException;
import java.util.Map;

public class FileVecUtils {
  
  public static byte[] getFirstBytes(FileVec vec) {
    return getFirstBytes(H2O.STORE, H2O.getPM(), vec);
  }

  static byte[] getFirstBytes(Map<Key, Value> store, PersistManager pm,
                              FileVec vec) {
    if (store.get(vec.chunkKey(0)) != null) {
      // if it looks like we have the chunk cached attempt to use it instead of fetching it again
      return vec.getFirstChunkBytes();
    }
    try {
      int max = (long) vec._chunkSize > vec._len ? (int) vec._len : vec._chunkSize;
      return pm.load(Value.HDFS, vec._key, 0L, max);
    } catch (IOException e) {
      throw new RuntimeException("HDFS read failed", e);
    }
  }


}
