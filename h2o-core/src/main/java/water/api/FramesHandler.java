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

  // /2/Frames backward compatibility
  protected void list_or_fetch() {
    //if (this.version != 2)
    //  throw H2O.fail("list_or_fetch should not be routed for version: " + this.version + " of route: " + this.route);

    if (null != key) {
      fetch();
    } else {
      list();
    }
  }

  protected void list() {
    // was:    H2O.KeySnapshot.globalSnapshot().fetchAll(Frame.class); // Sort for pretty display and reliable ordering.
  }

  /** NOTE: We really want to return a different schema here! */
  protected void columns() {
    // TODO: return *only* the columns. . .
    fetch();
  }

  public static Frame getFromDKV(String key_str) {
    return getFromDKV(Key.make(key_str));
  }

  public static Frame getFromDKV(Key key) {
    if (null == key)
      throw new IllegalArgumentException("Got null key.");

    Value v = DKV.get(key);
    if (null == v)
      throw new IllegalArgumentException("Did not find key: " + key.toString());

    Iced ice = v.get();
    if (! (ice instanceof Frame))
      throw new IllegalArgumentException("Expected a Frame for key: " + key.toString() + "; got a: " + ice.getClass());

    return (Frame)ice;
  }

  protected void column() {
    Frame frame = this.getFromDKV(key);

    // NOTE: We really want to return a different schema here!
    Vec vec = frame.vec(column);
    if (null == vec)
      throw new IllegalArgumentException("Did not find column: " + column + " in frame: " + key.toString());

    Vec[] vecs = { vec };
    String[] names = { column };
    Frame f = new Frame(names, vecs);
    frames = new Frame[1];
    frames[0] = f;
  }

  protected void fetch() {
    Frame frame = this.getFromDKV(key);
    frames = new Frame[1];
    frames[0] = frame;
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
