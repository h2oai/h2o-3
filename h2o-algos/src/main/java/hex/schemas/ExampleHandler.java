package hex.schemas;

import hex.example.Example;
import water.H2O;
import water.api.Handler;

/** TODO: only used by old-school web ui: remove!  ModelBuilderHander does this for all the algos.  */
@Deprecated
public class ExampleHandler extends Handler<Example,ExampleV2> {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  public ExampleHandler() {}
  public ExampleV2 train(int version, Example e) {
    assert e._parms != null;
    e.trainModel();
    return schema(version).fillFromImpl(e);
  }
  @Override protected ExampleV2 schema(int version) { ExampleV2 schema = new ExampleV2(); schema.parameters = schema.createParametersSchema(); return schema; }
  @Override public void compute2() { throw H2O.fail(); }
}
