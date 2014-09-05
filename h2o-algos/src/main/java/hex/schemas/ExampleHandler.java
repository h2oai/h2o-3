package hex.schemas;

import hex.example.Example;
import water.H2O;
import water.api.Handler;

public class ExampleHandler extends Handler<Example,ExampleV2> {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  public ExampleHandler() {}
  public ExampleV2 train(int version, Example e) {
    assert e._parms != null;
    e.train();
    return schema(version).fillFromImpl(e);
  }
  @Override protected ExampleV2 schema(int version) { return new ExampleV2(); }
  @Override public void compute2() { throw H2O.fail(); }
}
