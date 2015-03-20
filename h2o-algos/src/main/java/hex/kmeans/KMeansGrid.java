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
public class KMeansGrid extends Grid<KMeansGrid> {

  public static final String MODEL_NAME = "KMeans";
  /** @return Model name */
  @Override protected String modelName() { return MODEL_NAME; }

  private static final String[] HYPER_NAMES    = new String[] {"_k", "_standardize",          "_init",                         "_seed" };
  private static final double[] HYPER_DEFAULTS = new double[] {  0,          1     , KMeans.Initialization.PlusPlus.ordinal(),123456789L};
  /** @return hyperparameter names corresponding to a Model.Parameter field names */
  @Override protected String[] hyperNames() { return HYPER_NAMES; }
  /** @return hyperparameter defaults, aligned with the field names */
  @Override protected double[] hyperDefaults() { return HYPER_DEFAULTS; }

  /** Ask the Grid for a suggested next hyperparameter value, given an existing
   *  Model as a starting point and the complete set of hyperparameter limits.
   *  Returning a NaN signals there is no next suggestion, which is reasonable
   *  if the obvious "next" value does not exist (e.g. exhausted all
   *  possibilities of an enum).  It is OK if a Model for the suggested value
   *  already exists; this will be checked before building any model.
   *  @param h The h-th hyperparameter 
   *  @param m A model to act as a starting point 
   *  @param hyperLimits Upper bounds for this search 
   *  @return Suggested next value for hyperparameter h or NaN if no next value */
  @Override protected double suggestedNextHyperValue( int h, Model m, double[] hyperLimits ) {
    throw H2O.unimpl();
  }

  /** @param hypers A set of hyper parameter values
   *  @return A ModelBuilder, blindly filled with parameters.  Assumed to be
   *  cheap; used to check hyperparameter sanity or make models */
  @Override protected KMeans getBuilder( double[] hypers ) {
    KMeansModel.KMeansParameters parms = new KMeansModel.KMeansParameters();
    parms._train = _fr._key;
    parms._k = (int)hypers[0];
    parms._standardize = hypers[1]!=0;
    parms._init = KMeans.Initialization.values()[(int)hypers[2]];
    parms._seed = (long)hypers[3];
    return new KMeans(parms);
  }

  /** @param parms Model parameters
   *  @return Gridable parameters pulled out of the parms */
  @Override public double[] getHypers( Model.Parameters parms ) {
    double[] ds = new double[HYPER_NAMES.length];
    KMeansModel.KMeansParameters kp = (KMeansModel.KMeansParameters)parms;
    ds[0] = kp._k;
    ds[1] = kp._standardize ? 1 : 0;
    ds[2] = kp._init.ordinal();
    ds[3] = kp._seed;
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

  @Override protected long checksum_impl() { throw H2O.unimpl(); }
}
