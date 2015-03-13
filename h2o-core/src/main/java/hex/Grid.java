package hex;

import java.util.concurrent.Future;
import water.*;
import water.H2O.H2OFuture;
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
public abstract class Grid<G extends Grid<G>> extends Lockable<G> {
  protected Grid( ) { super(Key.make("Grid"+Key.rand())); }

  // Subclasses describe their hyperparameters

  /** @return Number of hyperparameters this Grid will Grid-over */
  protected abstract int nHyperParms();

  /** @param h The h-th hyperparameter
   *  @return h-th hyperparameter name; should correspond to a Model.Parameter field name */
  protected abstract String hyperName(int h);

  /** @param h The h-th hyperparameter
   *  @return Preferred string representation of h-th hyperparameter */
  protected abstract String hyperToString(int h, double val);

  /** @param h The h-th hyperparameter
   *  @param m A model to fetch the hyperparameter from
   *  @return The h-th hyperparameter value from Model m  */
  protected abstract double hyperValue( int h, Model m );
  
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
  protected abstract double suggestedNextHyperValue( int h, Model m, double[] hyperLimits );


  /** @return The data frame used to train all these models.  All models are
   *  trained on the same data frame, but might be validated on multiple
   *  different frames. */
  public Frame trainingFrame() { throw H2O.unimpl(); }

  /** @param A set of hyper parameter values
   *  @return A model run with these parameters, or null if the model does not exist. */
  public Model model( double[] hypers ) { throw H2O.unimpl(); }

  /** @param A set of hyper parameter values
   *  @return A model run with these parameters, typically built on demand and
   *  not cached - expected to be an expensive operation.  If the model in question
   *  is "in progress", a 2nd build will NOT be kicked off.  This is a blocking call. */
  public Model buildModel( double[] hypers ) {
    return startBuildModel(hypers).getResult(); 
  }
  
  /** @param hypers A set of hyper parameter values
   *  @return A Future of a model run with these parameters, typically built on
   *  demand and not cached - expected to be an expensive operation.  If the
   *  model in question is "in progress", a 2nd build will NOT be kicked off.
   *  This is a non-blocking call. */
  public H2OFuture<Model> startBuildModel( double[] hypers ) { throw H2O.unimpl(); }

  
  /** @param hyperSearch A set of arrays of hyper parameter values, used to
   *  specify a simple fully-filled-in grid search.
   *  @return A Future of this Grid, with models run with these parameters,
   *  built as needed - expected to be an expensive operation.  If the models
   *  in question are "in progress", a 2nd build will NOT be kicked off.  This
   *  is a non-blocking call. */
  public Future<Grid> startGridSearch( double[][] hyperSearch ) { throw H2O.unimpl(); }


}
