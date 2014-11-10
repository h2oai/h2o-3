package hex.example;

import hex.*;
import hex.schemas.ExampleModelV2;
import water.*;
import water.api.ModelSchema;
import water.fvec.Frame;

public class ExampleModel extends SupervisedModel<ExampleModel,ExampleModel.ExampleParameters,ExampleModel.ExampleOutput> {

  public static class ExampleParameters extends SupervisedModel.SupervisedParameters {
    public int _max_iters = 1000; // Max iterations
  }

  public static class ExampleOutput extends SupervisedModel.SupervisedOutput {
    // Iterations executed
    public int _iters;
    public double[] _maxs;
    public ExampleOutput( Example b ) { super(b); }
    @Override public ModelCategory getModelCategory() { return Model.ModelCategory.Unknown; }
  }

  ExampleModel( Key selfKey, ExampleParameters parms, ExampleOutput output) { super(selfKey,parms,output); }

  // Default publically visible Schema is V2
  @Override public ModelSchema schema() { return new ExampleModelV2(); }

  @Override protected float[] score0(double data[/*ncols*/], float preds[/*nclasses+1*/]) {  
    throw H2O.unimpl();
  }

}
