package water.api;

import water.*;
import water.fvec.Frame;
import water.fvec.Vec;

class FramesHandler extends Handler<FramesHandler, FramesBase> {
  // TODO: handlers should return an object that has the result as well as the needed http headers including status code
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  Key key;
  Frame[] frames;
  String column; // NOTE: this is needed for request handling, but isn't really part of
                 // state.  We should be able to have verb request params that aren't part
                 // of the state.  Another example: find_compatible_models is not part of
                 // the Schema.

  protected void list() {
    // was:    H2O.KeySnapshot.globalSnapshot().fetchAll(Frame.class); // Sort for pretty display and reliable ordering.
  }

  /** NOTE: We really want to return a different schema here! */
  protected void columns() {
    fetch();
  }

  protected void column() {
    if (null == key)
      return;

    Value value = DKV.get(key);
    if (null == value)  // TODO: 404
      throw H2O.fail("Did not find key in DKV: " + key.toString());

    Iced ice = value.get();
    if (! (ice instanceof Frame))  // TODO: 404
      throw H2O.fail("Expected a Frame for key: " + key.toString() + "; got a: " + ice.getClass());

    // NOTE: We really want to return a different schema here!
    Vec vec = ((Frame)ice).vec(column);
    if (null == vec)
      throw H2O.fail("Did not find column: " + column + " in frame: " + key.toString());

    Vec[] vecs = { vec };
    String[] names = { column };
    Frame f = new Frame(names, vecs);
    frames = new Frame[1];
    frames[0] = f;
  }


  protected void fetch() {
    if (null == key)
      return;

    Value v = DKV.get(key);
    if (null == v)  // TODO: 404
      throw H2O.fail("Did not find key in DKV: " + key.toString());

    Iced ice = v.get();
    if (! (ice instanceof Frame))  // TODO: 404
      throw H2O.fail("Expected a Frame for key: " + key.toString() + "; got a: " + ice.getClass());

    frames = new Frame[1];
    frames[0] = (Frame)ice;
  }

  @Override protected FramesBase schema(int version) {
    switch (version) {
    case 2:   return new FramesV2();
    case 3:   return new FramesV3();
    default:  throw H2O.fail("Bad version for Frames schema: " + version);
    }
  }

  // Need to stub this because it's required by H2OCountedCompleter:
  @Override public void compute2() { throw H2O.fail(); }

}
