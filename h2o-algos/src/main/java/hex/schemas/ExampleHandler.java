package hex.schemas;

import hex.example.Example;
import hex.example.ExampleModel;
import water.H2O;
import water.api.Handler;

public class ExampleHandler extends Handler<ExampleHandler,ExampleV2> {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }
  // Inputs
  public ExampleModel.ExampleParameters _parms;

  // Outputs
  public Example _job;           // The modelling job

  public double[] _maxs;

  public ExampleHandler() {}
  public void work() { assert _parms != null; _job = new Example(_parms); }
  @Override protected ExampleV2 schema(int version) { return new ExampleV2(); }
  @Override public void compute2() { throw H2O.fail(); }
}
