package hex.kmeans;

import hex.schemas.KMeansHandler;
import water.*;
import water.fvec.Frame;

public class KMeansModel extends Model {
  private final KMeansHandler _parms; // K, max_iter, seed, normalize

  // Iterations executed
  int _iters;
  
  // Cluster centers.  During model init, might be null or might have a "K"
  // which is oversampled alot.
  public double[/*K*/][/*features*/] _clusters;

  // Mean-Squared-Error: avg squared-distance from point to nearest cluster
  public double _mse;

  KMeansModel( Key selfKey, Frame fr, KMeansHandler parms) { 
    super(selfKey,fr); 
    _parms=parms; 
  }

  protected float[] score0(double data[/*ncols*/], float preds[/*nclasses+1*/]) {
    throw H2O.unimpl();
  }

  protected String errStr() {
    throw H2O.unimpl();
  }
}
