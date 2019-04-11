package hex;

import hex.Model;
import hex.ModelMetrics;
import hex.ModelMojoWriter;
import water.H2O;
import water.Key;

import java.io.IOException;

public interface ModelStubs {
  public class TestModel extends Model {
    public TestModel( Key key, Parameters p, Output o ) { super(key,p,o); }
    @Override
    public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) { throw H2O.unimpl(); }
    @Override
    protected double[] score0(double[] data, double[] preds) { throw H2O.unimpl(); }

    public static class TestParam extends Parameters {
      public String algoName() { return "A"; }
      public String fullName() { return "A"; }
      public String javaName() { return TestModel.class.getName(); }
      @Override public long progressUnits() { return 0; }
    }
    public static class TestOutput extends Output { }

    @Override
    public ModelMojoWriter getMojo() {
      return new TestMojoWriter(this);
    }
  }

  public static class TestMojoWriter extends ModelMojoWriter {
    public TestMojoWriter(TestModel model) { super(model); }
    @Override public String mojoVersion() { return "42"; }
    @Override protected void writeModelData() throws IOException { }

    @Override
    protected void writeExtraInfo() throws IOException {
      // do nothing
    }
  }
}
