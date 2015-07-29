package hex.tree.gbm;

import hex.*;
import hex.tree.SharedTreeGrid;
import water.DKV;
import water.util.ArrayUtils;
import water.H2O;
import water.Key;
import water.fvec.Frame;

/** A Grid of Models
 *  Used to explore Model hyper-parameter space.  Lazily filled in, this object
 *  represents the potentially infinite variety of hyperparameters of a given
 *  model & dataset.
 *
 *  One subclass per kind of Model, e.g. GBM or GLM or GBM or DL.  The Grid
 *  tracks Models and their hyperparameters, and will allow discovery of
 *  existing Models by hyperparameter, or building Models on demand by
 *  hyperparameter.  The Grid can manage a (simplistic) hyperparameter search
 *  space.
 *
 *  Hyperparameter values are limited to doubles in the API, but can be
 *  anything the subclass Grid desires internally.  E.g. the Grid for GBM
 *  will convert the initial center selection Enum to and from a simple integer
 *  value internally.
 */
public class GBMGrid extends SharedTreeGrid<GBMModel.GBMParameters, GBMGrid> {

  public static final String MODEL_NAME = "GBM";

  @Override protected String modelName() { return MODEL_NAME; }

  private static final String[] HYPER_NAMES    = ArrayUtils.append(SharedTreeGrid.HYPER_NAMES   ,new String[] {    "_distribution"               , "_learn_rate"});
  private static final double[] HYPER_DEFAULTS = ArrayUtils.append(SharedTreeGrid.HYPER_DEFAULTS,new double[] { Distribution.Family.AUTO.ordinal(),     0.1f     });

  @Override protected String[] hyperNames() { return HYPER_NAMES; }

  @Override protected double[] hyperDefaults() { return HYPER_DEFAULTS; }

  @Override protected double suggestedNextHyperValue( int h, Model m, double[] hyperLimits ) {
    throw H2O.unimpl();
  }

  @Override protected GBM createBuilder(GBMModel.GBMParameters params) {
    return new GBM(params);
  }

  @Override
  protected GBMModel.GBMParameters applyHypers(GBMModel.GBMParameters parms, double[] hypers) {
    GBMModel.GBMParameters p = super.applyHypers(parms, hypers);
    int slen = SharedTreeGrid.HYPER_NAMES.length;
    p._distribution = Distribution.Family.values()[(int)hypers[slen+0]];
    p._learn_rate =         (float)hypers[slen+1];
    return p;
  }

  @Override public double[] getHypers(GBMModel.GBMParameters params) {
    double[] hypers = new double[HYPER_NAMES.length];
    super.getHypers(params,hypers);
    int slen = SharedTreeGrid.HYPER_NAMES.length;
    hypers[slen+0] = params._distribution.ordinal();
    hypers[slen+1] = params._learn_rate;
    return hypers;
  }

  // Factory for returning a grid based on an algorithm flavor
  private GBMGrid( Key key, Frame fr ) {
    super(key,fr);
  }

  public static GBMGrid get( Frame fr ) { 
    Key k = Grid.keyName(MODEL_NAME, fr);
    GBMGrid kmg = DKV.getGet(k);
    if( kmg != null ) return kmg;
    kmg = new GBMGrid(k,fr);
    DKV.put(kmg);
    return kmg;
  }
  /** FIXME: Rest API requirement - do not call directly */
  public GBMGrid() { super(null, null); }
}
