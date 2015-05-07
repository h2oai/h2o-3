package hex.kmeans;

import hex.*;
import water.DKV;
import water.H2O;
import water.Key;
import water.fvec.Frame;

/** A Grid of Models
 *  Used to explore Model hyper-parameter space.  Lazily filled in, this object
 *  represents the potentially infinite variety of hyperparameters of a given
 *  model & dataset.
 *
 *  One subclass per kind of Model, e.g. KMeans or GLM or GBM or DL.  The Grid
 *  tracks Models and their hyperparameters, and will allow discovery of
 *  existing Models by hyperparameter, or building Models on demand by
 *  hyperparameter.  The Grid can manage a (simplistic) hyperparameter search
 *  space.
 *
 *  Hyperparameter values are limited to doubles in the API, but can be
 *  anything the subclass Grid desires internally.  E.g. the Grid for KMeans
 *  will convert the initial center selection Enum to and from a simple integer
 *  value internally.
 */
public class KMeansGrid extends Grid<KMeansModel.KMeansParameters, KMeansGrid> {

  public static final String MODEL_NAME = "KMeans";

  @Override protected String modelName() { return MODEL_NAME; }

  private static final String[] HYPER_NAMES    = new String[] {"_k", "_standardize",          "_init",                         "_seed" };
  private static final double[] HYPER_DEFAULTS = new double[] {  0,          1     , KMeans.Initialization.PlusPlus.ordinal(),123456789L};

  @Override protected String[] hyperNames() { return HYPER_NAMES; }

  @Override protected double[] hyperDefaults() { return HYPER_DEFAULTS; }

  @Override protected double suggestedNextHyperValue( int h, Model m, double[] hyperLimits ) {
    throw H2O.unimpl();
  }

  @Override
  protected ModelBuilder createBuilder(KMeansModel.KMeansParameters params) {
    return new KMeans(params);
  }

  @Override protected KMeansModel.KMeansParameters applyHypers(KMeansModel.KMeansParameters params, double[] hypers) {
    params._train = _fr._key;
    params._k = (int)hypers[0];
    params._standardize = hypers[1]!=0;
    params._init = KMeans.Initialization.values()[(int)hypers[2]];
    params._seed = (long)hypers[3];
    return params;
  }

  @Override public double[] getHypers( KMeansModel.KMeansParameters params ) {
    double[] ds = new double[HYPER_NAMES.length];
    ds[0] = params._k;
    ds[1] = params._standardize ? 1 : 0;
    ds[2] = params._init.ordinal();
    ds[3] = params._seed;
    return ds;
  }

  // Factory for returning a grid based on an algorithm flavor
  private KMeansGrid( Key key, Frame fr ) { super(key,fr); }
  public static KMeansGrid get( Frame fr ) { 
    Key k = Grid.keyName(MODEL_NAME, fr);
    KMeansGrid kmg = DKV.getGet(k);
    if( kmg != null ) return kmg;
    kmg = new KMeansGrid(k,fr);
    DKV.put(kmg);
    return kmg;
  }

  /** FIXME: Rest API requirement - do not call directly */
  public KMeansGrid() { super(null, null); }
}
