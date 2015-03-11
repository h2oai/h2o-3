package hex.example;

import hex.Model;
import hex.ModelMetrics;
import hex.SupervisedModel;
import water.H2O;
import water.Key;

public class ExampleModel extends SupervisedModel<ExampleModel,ExampleModel.ExampleParameters,ExampleModel.ExampleOutput> {

  public static class ExampleParameters extends SupervisedModel.SupervisedParameters {
    public int _max_iterations = 1000; // Max iterations
  }

  public static class ExampleOutput extends SupervisedModel.SupervisedOutput {
    // Iterations executed
    public int _iterations;
    public double[] _maxs;
    public ExampleOutput( Example b ) { super(b); }
    @Override public ModelCategory getModelCategory() { return Model.ModelCategory.Unknown; }
  }

  ExampleModel( Key selfKey, ExampleParameters parms, ExampleOutput output) { super(selfKey,parms,output); }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    throw H2O.unimpl("No Model Metrics for ExampleModel.");
  }

  @Override protected float[] score0(double data[/*ncols*/], float preds[/*nclasses+1*/]) {
    throw H2O.unimpl();
  }

}
