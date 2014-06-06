package water.api;

import water.H2O;
import water.schemas.FramesBase;
import water.schemas.FramesV3;

public class FramesHandler extends Handler<FramesHandler, FramesBase> {
  // TODO: handlers should return an object that has the result as well as the needed http headers including status code
  protected void list() {

  }

  @Override protected FramesBase schema(int version) {
    switch (version) {
    case 3:
      return new FramesV3();
    default:
      throw H2O.fail("Bad version for Frames schema: " + version);
    }
  }

  // Need to stub this because it's required by H2OCountedCompleter:
  @Override public void compute2() { throw H2O.fail(); }

}
