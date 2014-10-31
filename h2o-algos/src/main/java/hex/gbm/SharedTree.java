package hex.gbm;

import java.util.Arrays;

import hex.SupervisedModelBuilder;
import water.H2O.H2OCountedCompleter;
import water.*;
import water.fvec.Frame;
import water.util.*;

public abstract class SharedTree<M extends SharedTreeModel<M,P,O>, P extends SharedTreeModel.SharedTreeParameters, O extends SharedTreeModel.SharedTreeOutput> extends SupervisedModelBuilder<M,P,O> {
  public SharedTree( String name, P parms) { super(name,parms); }

  // Number of trees requested, including prior trees from a checkpoint
  int _ntrees;

  /** Initialize the ModelBuilder, validating all arguments and preparing the
   *  training frame.  This call is expected to be overridden in the subclasses
   *  and each subclass will start with "super.init();".  This call is made
   *  by the front-end whenever the GUI is clicked, and needs to be fast;
   *  heavy-weight prep needs to wait for the trainModel() call.
   *
   *  Validate the requested ntrees; precompute actual ntrees.  Validate
   *  the number of classes to predict on; validate a checkpoint.  */
  @Override public void init() {
    super.init();

    if( _nclass > SharedTreeModel.SharedTreeParameters.MAX_SUPPORTED_LEVELS )
      throw new IllegalArgumentException("Too many levels in response column!");
    if( _parms._requested_ntrees < 0 || _parms._requested_ntrees > 100000 ) 
      error("requested_ntrees", "Requested ntrees must be between 1 and 100000");
    _ntrees = _parms._requested_ntrees;
    if( _parms._checkpoint!=null ) {
      if( DKV.get(_parms._checkpoint)==null )
        error("_checkpoint", "Checkpoint "+_parms._checkpoint.toString() + " does not exists!");
      else {
        M checkpointModel = DKV.get(_parms._checkpoint).get();
        _ntrees = _parms._requested_ntrees + checkpointModel._output._ntrees; // Actual trees is requested plus prior actuals
      }
    }
  }

  // Top-level tree-algo driver
  abstract class Driver extends H2OCountedCompleter<Driver> {

    // Top-level tree-algo driver function
    @Override protected void compute2() {
      M model = null;    // Resulting model!
      try {
        Scope.enter();
        _parms.lock_frames(SharedTree.this); // Fetch & read-lock input frames
        // Compute the response domain
        String[] domain = _response.domain();
        assert (_nclass > 1 && domain != null) || (_nclass==1 && domain==null);
        if( _nclass==1 ) domain = new String[] {"r"}; // For regression, give a name to class 0

        // Create an INITIAL MODEL based on given parameters
        model = makeModel(_dest, _parms );
        model.delete_and_lock(_key);

        // Compute class distribution, used to for initial guesses and to
        // upsample minority classes (if asked for).
        Frame fr = _train;
        if( _nclass>1 ) {       // Classification?

          // Handle imbalanced classes by stratified over/under-sampling.
          // initWorkFrame sets the modeled class distribution, and
          // model.score() corrects the probabilities back using the
          // distribution ratios
          float[] trainSamplingFactors;
          if( _parms._balance_classes ) {
            trainSamplingFactors = new float[domain.length]; //leave initialized to 0 -> will be filled up below
            Frame stratified = water.util.MRUtils.sampleFrameStratified(fr, fr.lastVec(), trainSamplingFactors, (long)(_parms._max_after_balance_size*fr.numRows()), _parms._seed, true, false);
            if (stratified != fr) {
              throw H2O.unimpl();
              //_parms.setTrain(stratified);
              //response = _parms._response; // Reload from stratified data
              //// Recompute distribution since the input frame was modified
              //MRUtils.ClassDist cdmt2 = new MRUtils.ClassDist(_nclass).doAll(_response);
              //_distribution = cdmt2.dist();
              //_modelClassDist = cdmt2.rel_dist();
            }
          }
        }
        Log.info("Prior class distribution: " + Arrays.toString(model._output._priorClassDist));
        Log.info("Model class distribution: " + Arrays.toString(model._output._modelClassDist));

        // Also add to the basic working Frame these sets:
        //   nclass Vecs of current forest results (sum across all trees)
        //   nclass Vecs of working/temp data
        //   nclass Vecs of NIDs, allowing 1 tree per class
        
        // Current forest values: results of summing the prior M trees
        for( int i=0; i<_nclass; i++ )
          fr.add("Tree_"+domain[i], _response.makeZero());
        
        // Initial work columns.  Set-before-use in the algos.
        for( int i=0; i<_nclass; i++ )
          fr.add("Work_"+domain[i], _response.makeZero());

        // One Tree per class, each tree needs a NIDs.  For empty classes use a -1
        // NID signifying an empty regression tree.
        for( int i=0; i<_nclass; i++ )
          fr.add("NIDs_"+domain[i], _response.makeCon(model._output._distribution==null ? 0 : (model._output._distribution[i]==0?-1:0)));

        // Timer for model building
        Timer bm_timer =  new Timer();
        long before = System.currentTimeMillis();

        throw H2O.unimpl();

      } catch( Throwable t ) {
        if( model != null ) { model.remove(); model = null; }
        cancel2(t);
        throw t;
      } finally {
        if( model != null ) model.unlock(_key);
        _parms.unlock_frames(SharedTree.this);
        Scope.exit();
        done();                 // Job done!
      }
      //tryComplete();
    }

    protected abstract M makeModel( Key modelKey, P parms );
  }

}
