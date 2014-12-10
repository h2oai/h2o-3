package water.api;

import water.Key;
import water.Keyed;
import water.fvec.Frame;

public class KeyV1<T extends Keyed> extends KeySchema<T> {
  public KeyV1(Key<T> key) {
    super(key);
  }

  public static class FrameKeyV1 extends KeySchema<Frame> {
    public FrameKeyV1(Key<Frame> key) {
      super(key);
    }
  }
}
