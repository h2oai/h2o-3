package hex.kmeans2;

import hex.*;
import hex.schemas.KMeans2ModelV2;
import water.*;
import water.api.ModelSchema;

public class KMeans2Model extends Model<KMeans2Model,KMeans2Model.KMeans2Parameters,KMeans2Model.KMeans2Output> {

  public static class KMeans2Parameters extends Model.Parameters {
    public int _max_iters = 1000; // Max iterations
    public int _K = 0;
  }

  public static class KMeans2Output extends Model.Output {
    public int _iters;      // Iterations executed
    public double _clusters[/*K*/][/*N*/]; // Our K clusters, each an N-dimensional point
    public double _mse;     // Mean Squared Error
    public KMeans2Output( KMeans2 b ) { super(b); }
    @Override public ModelCategory getModelCategory() { return Model.ModelCategory.Clustering; }
  }

  KMeans2Model( Key selfKey, KMeans2Parameters parms, KMeans2Output output) { super(selfKey,parms,output); }

  // Default publically visible Schema is V2
  @Override public ModelSchema schema() { return new KMeans2ModelV2(); }

  @Override protected float[] score0(double data[/*ncols*/], float preds[/*nclasses+1*/]) {  
    throw H2O.unimpl();
  }

}
