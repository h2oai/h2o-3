package hex.tree.drf;

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
 *  One subclass per kind of Model, e.g. DRF or GLM or DRF or DL.  The Grid
 *  tracks Models and their hyperparameters, and will allow discovery of
 *  existing Models by hyperparameter, or building Models on demand by
 *  hyperparameter.  The Grid can manage a (simplistic) hyperparameter search
 *  space.
 *
 *  Hyperparameter values are limited to doubles in the API, but can be
 *  anything the subclass Grid desires internally.  E.g. the Grid for DRF
 *  will convert the initial center selection Enum to and from a simple integer
 *  value internally.
 */
public class DRFGrid extends SharedTreeGrid<DRFModel.DRFParameters, DRFGrid> {

  public static final String MODEL_NAME = "DRF";
  /** @return Model name */
  @Override protected String modelName() { return MODEL_NAME; }

  private static final String[] HYPER_NAMES    = ArrayUtils.append(SharedTreeGrid.HYPER_NAMES   ,new String[] { "_mtries", "_sample_rate"});
  private static final double[] HYPER_DEFAULTS = ArrayUtils.append(SharedTreeGrid.HYPER_DEFAULTS,new double[] {    -1    ,     2f/3f     });

  @Override protected String[] hyperNames() { return HYPER_NAMES; }

  @Override protected double[] hyperDefaults() { return HYPER_DEFAULTS; }

  @Override protected double suggestedNextHyperValue( int h, Model m, double[] hyperLimits ) {
    throw H2O.unimpl();
  }

  @Override
  protected ModelBuilder createBuilder(DRFModel.DRFParameters params) {
    return new DRF(params);
  }

  @Override protected DRFModel.DRFParameters applyHypers(DRFModel.DRFParameters params, double[] hypers) {
    DRFModel.DRFParameters p = super.applyHypers(params, hypers);
    int slen = SharedTreeGrid.HYPER_NAMES.length;
    p._mtries      = (int)  hypers[slen  ];
    p._sample_rate = (float)hypers[slen+1];
    return p;
  }

  @Override public double[] getHypers(DRFModel.DRFParameters params) {
    double[] hypers = new double[HYPER_NAMES.length];
    super.getHypers(params,hypers);
    int slen = SharedTreeGrid.HYPER_NAMES.length;
    hypers[slen  ] = params._mtries;
    hypers[slen+1] = params._sample_rate;
    return hypers;
  }

  // Factory for returning a grid based on an algorithm flavor
  private DRFGrid( Key key, Frame fr ) { super(key,fr); }
  public static DRFGrid get( Frame fr ) { 
    Key k = Grid.keyName(MODEL_NAME, fr);
    DRFGrid kmg = DKV.getGet(k);
    if( kmg != null ) return kmg;
    kmg = new DRFGrid(k,fr);
    DKV.put(kmg);
    return kmg;
  }

  /** FIXME: Rest API requirement - do not call directly */
  public DRFGrid() { super(null, null); }
}
