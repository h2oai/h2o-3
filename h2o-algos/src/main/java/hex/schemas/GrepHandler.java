package hex.schemas;

import hex.grep.Grep;
import water.H2O;
import water.api.Handler;

/** TODO: only used by old-school web ui: remove!  ModelBuilderHander does this for all the algos.  */
@Deprecated
public class GrepHandler extends Handler<Grep,GrepV2> {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  public GrepHandler() {}
  public GrepV2 train(int version, Grep e) {
    assert e._parms != null;
    e.trainModel();
    return schema(version).fillFromImpl(e);
  }
  @Override protected GrepV2 schema(int version) { GrepV2 schema = new GrepV2(); schema.parameters = schema.createParametersSchema(); return schema; }
  @Override public void compute2() { throw H2O.fail(); }
}
