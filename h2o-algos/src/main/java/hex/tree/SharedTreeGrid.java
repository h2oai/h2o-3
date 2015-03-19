package hex.tree;

import hex.Grid;
import water.Key;
import water.H2O;
import water.fvec.Frame;

/** A Grid of Models
 *  Used to explore Model hyper-parameter space.  Lazily filled in, this object
 *  represents the potentially infinite variety of hyperparameters of a given
 *  model & dataset.
 */
public abstract class SharedTreeGrid<G extends SharedTreeGrid<G>> extends Grid<G> {

  public static final String MODEL_NAME = "SharedTree";
  /** @return Model name */
  @Override protected String modelName() { return MODEL_NAME; }

  protected static final String[] HYPER_NAMES    = new String[] {"_ntrees", "_max_depth", "_min_rows", "_nbins" };
  protected static final double[] HYPER_DEFAULTS = new double[] {    50   ,       5     ,     10     ,    20    };

  // Factory for returning a grid based on an algorithm flavor
  protected SharedTreeGrid( Key key, Frame fr ) { super(key,fr); }

  protected void getBuilder( SharedTreeModel.SharedTreeParameters parms, double[] hypers ) {
    parms._train = _fr._key;
    parms._response_column = _fr.lastVecName();
    parms._ntrees = (int)hypers[0];
    parms._max_depth = (int)hypers[1];
    parms._min_rows = (int)hypers[2];
    parms._nbins = (int)hypers[3];
  }

  protected void getHypers( SharedTreeModel.SharedTreeParameters parms, double[] hypers ) {
    hypers[0] = parms._ntrees;
    hypers[1] = parms._max_depth;
    hypers[2] = parms._min_rows;
    hypers[3] = parms._nbins;
  }

  @Override protected long checksum_impl() { throw H2O.unimpl(); }
}
