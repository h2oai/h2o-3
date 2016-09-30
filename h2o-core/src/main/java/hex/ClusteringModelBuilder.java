package hex;

import water.Job;
import water.Key;
import water.util.Log;

abstract public class ClusteringModelBuilder<M extends ClusteringModel<M,P,O>, P extends ClusteringModel.ClusteringParameters, O extends ClusteringModel.ClusteringOutput> extends ModelBuilder<M,P,O> {
  public boolean isSupervised() { return false; }

  /** Constructor called from an http request; MUST override in subclasses. */
  public ClusteringModelBuilder(P parms         ) { super(parms    );  /*only call init in leaf classes*/ }
  public ClusteringModelBuilder(P parms, Job job) { super(parms,job);  /*only call init in leaf classes*/ }
  public ClusteringModelBuilder(P parms, boolean startup_once) { super(parms,startup_once);  /*only call init in leaf classes*/ }

  /** Initialize the ModelBuilder, validating all arguments and preparing the
   *  training frame.  This call is expected to be overridden in the subclasses
   *  and each subclass will start with "super.init();".  This call is made
   *  by the front-end whenever the GUI is clicked, and needs to be fast;
   *  heavy-weight prep needs to wait for the trainModel() call. */
  @Override public void init(boolean expensive) {
    super.init(expensive);
    if( _parms._k < 1 || _parms._k > 1e7 ) error("_k", "k must be between 1 and 1e7");
    if( _train != null && _train.numRows() < _parms._k ) error("_k","Cannot make " + _parms._k + " clusters out of " + _train.numRows() + " rows");
  }
}
