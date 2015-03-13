package hex.kmeans;

import hex.*;
import water.H2O;

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

  private static final String[] HYPER_NAMES = new String[] {"k", "max_iterations", "standardize" };

  /** @return Number of hyperparameters this Grid will Grid-over */
  @Override protected int nHyperParms() { return HYPER_NAMES.length; }

  /** @param h The h-th hyperparameter
   *  @return h-th hyperparameter name; should correspond to a Model.Parameter field name */
  @Override protected String hyperName(int h) { return HYPER_NAMES[h]; }

  /** @param h The h-th hyperparameter
   *  @return Preferred string representation of h-th hyperparameter */
  @Override protected String hyperToString(int h, double val) {
    switch( h ) {
    case 0: return Double.toString(val);
    case 1: return Integer.toString((int)val);
    case 2: return val==0 ? "false" : "true";
    default: throw H2O.fail();
    }
  }

  /** @param h The h-th hyperparameter
   *  @param m A model to fetch the hyperparameter from
   *  @return The h-th hyperparameter value from Model m  */
  @Override protected double hyperValue( int h, Model m ) {
    KMeansModel.KMeansParameters parms = ((KMeansModel)m)._parms;
    switch( h ) {
    case 0: return parms._k;
    case 1: return parms._max_iterations;
    case 2: return parms._standardize ? 1 : 0;
    default: throw H2O.fail();
    }
  }
  
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

  @Override protected long checksum_impl() { throw H2O.unimpl(); }
}
