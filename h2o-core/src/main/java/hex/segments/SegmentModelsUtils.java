package hex.segments;

import hex.Model;
import water.Key;

public class SegmentModelsUtils {

  static Key<Model> makeUniqueModelKey(Key<SegmentModels> smKey, long segmentIdx) {
    return Key.make(smKey.toString() + "_" + segmentIdx);
  }

}
