package water.api;

import water.*;
import water.fvec.ByteVec;
import water.fvec.Frame;
import water.schemas.FramesBase;
import water.schemas.FramesV2;

public class FramesHandler extends Handler<FramesHandler, FramesBase> {
  // TODO: handlers should return an object that has the result as well as the needed http headers including status code

  public Key key;
  public Frame[] frames;

  protected void list() {
    // was:    H2O.KeySnapshot.globalSnapshot().fetchAll(Frame.class); // Sort for pretty display and reliable ordering.
  }

  protected void fetch() {
    if (null == key)
      return;

    Iced ice = DKV.get(key).get();
    if (! (ice instanceof Frame)) {
        throw H2O.fail("Expected a Frame for key: " + key.toString() + "; got a: " + ice.getClass());
    }

    frames = new Frame[1];
    frames[0] = (Frame)ice;
    return;
  }

  @Override protected FramesBase schema(int version) {
    switch (version) {
    case 2:
      return new FramesV2();
    default:
      throw H2O.fail("Bad version for Frames schema: " + version);
    }
  }

  // Need to stub this because it's required by H2OCountedCompleter:
  @Override public void compute2() { throw H2O.fail(); }

}
