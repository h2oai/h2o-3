package hex.kmeans;

import water.*;
import water.fvec.Frame;

public class KMeansModel extends Model {

  KMeansModel( Key selfKey, Frame fr) { super(selfKey,fr); }

  protected float[] score0(double data[/*ncols*/], float preds[/*nclasses+1*/]) {
    throw H2O.unimpl();
  }

  protected String errStr() {
    throw H2O.unimpl();
  }

}
