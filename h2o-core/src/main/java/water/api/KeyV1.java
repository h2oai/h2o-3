package water.api;

import hex.Model;
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

  public static class ModelKeyV1 extends KeySchema<Model> {
    public ModelKeyV1(Key<Model> key) {
      super(key);
    }
  }
}
