package hex.example;

import hex.*;
import hex.schemas.ExampleModelV2;
import water.*;
import water.api.ModelSchema;
import water.fvec.Frame;

public class ExampleModel extends SupervisedModel<ExampleModel,ExampleModel.ExampleParameters,ExampleModel.ExampleOutput> {

  public static class ExampleParameters extends SupervisedModel.SupervisedParameters {
    public int _max_iters;        // Max iterations

    @Override
    public int sanityCheckParameters() {
      if (_max_iters < 0) validation_error("max_iters", "max_iters must be > 0");
      return _validation_error_count;
    }
  }

  public static class ExampleOutput extends Model.Output {
    // Iterations executed
    public int _iters;
    public double[] _maxs;
    @Override public ModelCategory getModelCategory() { return Model.ModelCategory.Unknown; }
  }

  ExampleModel( Key selfKey, Frame fr, ExampleParameters parms, ExampleOutput output) {
    super(selfKey,fr,parms,output,null);
  }

  // Default publically visible Schema is V2
  @Override public ModelSchema schema() { return new ExampleModelV2(); }

  @Override protected float[] score0(double data[/*ncols*/], float preds[/*nclasses+1*/]) {  
    throw H2O.unimpl();
  }

}
