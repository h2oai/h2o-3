package hex.gbm;

import hex.schemas.*;
import water.*;
import water.util.Timer;

/** Gradient Boosted Trees
 *
 *  Based on "Elements of Statistical Learning, Second Edition, page 387"
 */
public class GBM extends SharedTree<GBMModel,GBMModel.GBMParameters,GBMModel.GBMOutput> {
  // Called from an http request
  public GBM( GBMModel.GBMParameters parms) { super("GBM",parms); init(); }

  @Override public GBMV2 schema() { return new GBMV2(); }

  /** Start the GBM training Job on an F/J thread. */
  @Override public Job<GBMModel> trainModel() {
    return start(new GBMDriver(), _parms._requested_ntrees/*work for progress bar*/);
  }

  /** Initialize the ModelBuilder, validating all arguments and preparing the
   *  training frame.  This call is expected to be overridden in the subclasses
   *  and each subclass will start with "super.init();".  This call is made
   *  by the front-end whenever the GUI is clicked, and needs to be fast;
   *  heavy-weight prep needs to wait for the trainModel() call.
   *
   *  Validate the learning rate and loss family. */
  @Override public void init() {
    super.init();
    if( !(0. < _parms._learn_rate && _parms._learn_rate <= 1.0) ) 
      error("learn_rate", "learn_rate must be between 0 and 1");
    if( _parms._loss == GBMModel.GBMParameters.Family.bernoulli ) {
      if( _nclass != 2 ) 
        error("loss","Bernoulli requires the response to be a 2-class categorical");
      // Bernoulli: initial prediction is log( mean(y)/(1-mean(y)) )
      double mean = _response.mean();
      _initialPrediction = Math.log(mean/(1.0f-mean));
    }
  }

  // ----------------------
  private class GBMDriver extends Driver {

    /** Sum of variable empirical improvement in squared-error. The value is not scaled! */
    private transient float[/*nfeatures*/] _improvPerVar;

    @Override void buildModel() {
      // Initialize gbm-specific data structures
      if( _parms._importance ) _improvPerVar = new float[_nclass];

      // Reconstruct the working tree state from the checkopoint
      if( _parms._checkpoint ) {
        Timer t = new Timer();
        //new ResidualsCollector(_ncols, _nclass, initialModel.treeKeys).doAll(fr);
        //Log.info("Reconstructing tree residuals stats from checkpointed model took " + t);
        throw H2O.unimpl();
      }

      throw H2O.unimpl();
    }

    @Override GBMModel makeModel( Key modelKey, GBMModel.GBMParameters parms ) {
      return new GBMModel(modelKey,parms,new GBMModel.GBMOutput(GBM.this)); 
    }
  }
}
