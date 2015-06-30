package hex.example;

import hex.Model;
import hex.ModelCategory;
import hex.ModelMetrics;
import water.H2O;
import water.Key;

public class ExampleModel extends Model<ExampleModel,ExampleModel.ExampleParameters,ExampleModel.ExampleOutput> {

  public static class ExampleParameters extends Model.Parameters {
    public int _max_iterations = 1000; // Max iterations
  }

  public static class ExampleOutput extends Model.Output {
    // Iterations executed
    public int _iterations;
    public double[] _maxs;
    public ExampleOutput( Example b ) { super(b); }
    @Override public ModelCategory getModelCategory() { return ModelCategory.Unknown; }
  }

  ExampleModel( Key selfKey, ExampleParameters parms, ExampleOutput output) { super(selfKey,parms,output); }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    throw H2O.unimpl("No Model Metrics for ExampleModel.");
  }

  @Override protected double[] score0(double data[/*ncols*/], double preds[/*nclasses+1*/]) {
    throw H2O.unimpl();
  }

}
