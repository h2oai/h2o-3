package hex.example;

import hex.schemas.ExampleModelV2;
import water.*;
import water.api.ModelSchema;
import water.api.Schema;
import water.fvec.Frame;
import water.fvec.Chunk;

public class ExampleModel extends SupervisedModel<ExampleModel,ExampleModel.ExampleParameters,ExampleModel.ExampleOutput> {

  public static class ExampleParameters extends Model.Parameters<ExampleModel,ExampleParameters,ExampleOutput> {
    public int _max_iters;        // Max iterations
  }

  public static class ExampleOutput extends Model.Output<ExampleModel,ExampleParameters,ExampleOutput> {
    // Iterations executed
    public int _iters;
    public double[] _maxs;
  }

  ExampleModel( Key selfKey, Frame fr, ExampleParameters parms, ExampleOutput output) {
    super(selfKey,fr,parms,output,null);
  }

  // Default publically visible Schema is V2
  @Override public ModelSchema schema() { return new ExampleModelV2(); }

  @Override protected float[] score0(double data[/*ncols*/], float preds[/*nclasses+1*/]) {  
    throw H2O.unimpl();
  }

  @Override protected String errStr() { throw H2O.unimpl(); }
  @Override public ModelCategory getModelCategory() { return Model.ModelCategory.Unknown; }
}
