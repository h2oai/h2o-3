package hex.schemas;

import hex.example.Example;
import hex.example.ExampleModel;
import hex.schemas.ExampleHandler.ExamplePojo;
import water.H2O;
import water.Iced;
import water.api.Handler;

public class ExampleHandler extends Handler<ExampleHandler.ExamplePojo,ExampleV2> {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  public static final class ExamplePojo extends Iced {
    // Inputs
    public ExampleModel.ExampleParameters _parms;

    // Outputs
    public Example _job;           // The modelling job
  }

  public double[] _maxs;

  public ExampleHandler() {}
  public ExampleV2 work(int version, ExamplePojo e) {
    assert e._parms != null;
    e._job = new Example(e._parms);
    return schema(version).fillFromImpl(e);
  }
  @Override protected ExampleV2 schema(int version) { return new ExampleV2(); }
  @Override public void compute2() { throw H2O.fail(); }
}
