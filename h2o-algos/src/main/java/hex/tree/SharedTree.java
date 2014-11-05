package hex.tree;

import java.util.Arrays;

import hex.SupervisedModelBuilder;
import hex.VarImp;
import water.*;
import water.H2O.H2OCountedCompleter;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.*;

public abstract class SharedTree<M extends SharedTreeModel<M,P,O>, P extends SharedTreeModel.SharedTreeParameters, O extends SharedTreeModel.SharedTreeOutput> extends SupervisedModelBuilder<M,P,O> {
  public SharedTree( String name, P parms) { super(name,parms); }

  // Number of trees requested, including prior trees from a checkpoint
  protected int _ntrees;

  // The in-progress model being built
  protected M _model;

  // Number of columns in training set, not counting the response column
  protected int _ncols;

  // Initially predicted value (for zero trees)
  protected double _initialPrediction;

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
    if( _parms._ntrees < 0 || _parms._ntrees > 100000 )
      error("_ntrees", "Requested ntrees must be between 1 and 100000");
    _ntrees = _parms._ntrees;   // Total trees in final model
    if( _parms._checkpoint ) {  // Asking to continue from checkpoint?
      Value cv = DKV.get(_parms._destination_key);
      if( cv!=null ) {          // Look for prior model
        M checkpointModel = cv.get();
        if( _parms._ntrees < checkpointModel._output._ntrees+1 )
          error("_ntrees", "Requested ntrees must be between "+checkpointModel._output._ntrees+1+" and 100000");
        _ntrees = _parms._ntrees - checkpointModel._output._ntrees; // Needed trees
      }
    }
    if( _train != null )
      _ncols = _train.numCols()-1;

    // Initialize response based on given loss function.
    // Regression: initially predict the response mean
    // Multinomial: Preserve 0s in working columns; use 1-of-K binary trees
    _initialPrediction = _nclass == 1 ? _response.mean() : 0;
  }

  // --------------------------------------------------------------------------
  // Top-level tree-algo driver
  abstract protected class Driver extends H2OCountedCompleter<Driver> {

    // Top-level tree-algo driver function
    @Override protected void compute2() {
      Timer _bm_timer = new Timer();  // Timer for model building
      _model = null;            // Resulting model!
      try {
        Scope.enter();          // Cleanup temp keys

        _parms.lock_frames(SharedTree.this); // Fetch & read-lock input frames

        // New Model?  Or continuing from a checkpoint?
        if( _parms._checkpoint && DKV.get(_parms._destination_key) != null ) {
          _model = DKV.get(_dest).get();
          _model.write_lock(_key); // do not delete previous model; we are extending it
        } else {
          _model = makeModel(_dest, _parms ); // Make a fresh model
          _model.delete_and_lock(_key);       // and clear & write-lock it (smashing any prior)
        }

        // Compute the response domain; makes for nicer printouts
        String[] domain = _response.domain();
        assert (_nclass > 1 && domain != null) || (_nclass==1 && domain==null);
        if( _nclass==1 ) domain = new String[] {"r"}; // For regression, give a name to class 0

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
          Log.info("Prior class distribution: " + Arrays.toString(_model._output._priorClassDist));
          Log.info("Model class distribution: " + Arrays.toString(_model._output._modelClassDist));
        }

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
          fr.add("NIDs_"+domain[i], _response.makeCon(_model._output._distribution==null ? 0 : (_model._output._distribution[i]==0?-1:0)));

        // Tag out rows missing the response column
        new ExcludeNAResponse().doAll(fr);

        // Set initial predictions (computed in init())
        final double init = _initialPrediction;
        if( init != 0.0 )       // Only non-zero for regression or bernoulli
          new MRTask() {
            @Override public void map(Chunk tree) { for( int i=0; i<tree._len; i++ ) tree.set0(i, init); }
          }.doAll(vec_tree(_train,0)); // Only setting tree-column 0

        // Sub-class tree-model-builder specific build code
        buildModel();

      } catch( Throwable t ) {
        cancel2(t);
        throw t;
      } finally {
        if( _model != null ) _model.unlock(_key);
        _parms.unlock_frames(SharedTree.this);
        Scope.exit();
        done();                 // Job done!
      }
      tryComplete();
    }

    // Abstract classes implemented by the tree builders
    abstract protected M makeModel( Key modelKey, P parms );
    abstract protected void buildModel();
  }

  // --------------------------------------------------------------------------
  // Convenience accessor for a complex chunk layout.
  // Wish I could name the array elements nicer...
  protected Chunk chk_resp( Chunk chks[]        ) { return chks[_ncols]; }
  protected Chunk chk_tree( Chunk chks[], int c ) { return chks[_ncols+1+c]; }
  protected Chunk chk_work( Chunk chks[], int c ) { return chks[_ncols+1+_nclass+c]; }
  protected Chunk chk_nids( Chunk chks[], int t ) { return chks[_ncols+1+_nclass+_nclass+t]; }
  // Out-of-bag trees counter - only one since it is shared via k-trees
  protected Chunk chk_oobt(Chunk chks[]) { return chks[_ncols+1+_nclass+_nclass+_nclass]; }

  protected final Vec vec_nids( Frame fr, int t) { return fr.vecs()[_ncols+1+_nclass+_nclass+t]; }
  protected final Vec vec_resp( Frame fr, int t) { return fr.vecs()[_ncols]; }
  protected final Vec vec_tree( Frame fr, int c ) { return fr.vecs()[_ncols+1+c]; }

  protected double[] data_row( Chunk chks[], int row, double[] data) {
    assert data.length == _ncols;
    for(int f=0; f<_ncols; f++) data[f] = chks[f].at0(row);
    return data;
  }

  // Builder-specific decision node
  abstract protected DTree.DecidedNode makeDecided( DTree.UndecidedNode udn, DHistogram hs[] );

  /** Which rows are in-bag vs out-of-bag for sampling */
  abstract protected boolean outOfBagRow(Chunk[] chks, int row);

  // Read the 'tree' columns, do model-specific math and put the results in the
  // fs[] array, and return the sum.  Dividing any fs[] element by the sum
  // turns the results into a probability distribution.
  abstract protected float score1( Chunk chks[], float fs[/*nclass*/], int row );

  abstract protected VarImp doVarImpCalc(boolean scale);

  // Call builder specific score code and then correct probabilities
  // if it is necessary.
  float score2(Chunk chks[], float fs[/*nclass*/], int row ) {
    float sum = score1(chks, fs, row);
    if( isClassifier() && _model._output._priorClassDist!=null && _model._output._modelClassDist!=null && !Float.isInfinite(sum)  && sum>0f) {
      ArrayUtils.div(fs, sum);
      ModelUtils.correctProbabilities(fs, _model._output._priorClassDist, _model._output._modelClassDist);
      sum = 1.0f;
    }
    return sum;
  }

  // --------------------------------------------------------------------------
  // Tag out rows missing the response column
  class ExcludeNAResponse extends MRTask<ExcludeNAResponse> {
    @Override public void map( Chunk chks[] ) {
      Chunk ys = chk_resp(chks);
      for( int row=0; row<ys._len; row++ )
        if( ys.isNA0(row) )
          for( int t=0; t<_nclass; t++ )
            chk_nids(chks,t).set0(row,-1);
    }
  }

  // --------------------------------------------------------------------------
  transient long _timeLastScoreStart, _timeLastScoreEnd, _firstScore;
  protected void doScoringAndSaveModel(boolean finalScoring, boolean oob, boolean build_tree_one_node ) {
    long now = System.currentTimeMillis();
    if( _firstScore == 0 ) _firstScore=now;
    long sinceLastScore = now-_timeLastScoreStart;
    boolean updated = false;
    // Now model already contains tid-trees in serialized form
    if( _parms._score_each_iteration ||
        finalScoring ||
        (now-_firstScore < 4000) || // Score every time for 4 secs
        // Throttle scoring to keep the cost sane; limit to a 10% duty cycle & every 4 secs
        (sinceLastScore > 4000 && // Limit scoring updates to every 4sec
         (double)(_timeLastScoreEnd-_timeLastScoreStart)/sinceLastScore < 0.1) ) { // 10% duty cycle
      // If validation is specified we use a model for scoring, so we need to
      // update it!  First we save model with trees (i.e., make them available
      // for scoring) and then update it with resulting error
      if( !updated ) _model.update(_key);  updated = true;

      _timeLastScoreStart = now;
      Score sc = new Score(this,oob).doIt(build_tree_one_node).report(_model._output._ntrees,null);
      _model._output._nrmse = sc.nrmse();
      _model._output._cm = sc.cm();
      _model._output._auc = sc.auc();
      _timeLastScoreEnd = System.currentTimeMillis();
    }

    // Compute variable importance for this tree if asked; must be done on each tree however
    if( _parms._importance && _model._output._ntrees > 0 ) { // compute this tree votes but skip the first scoring call which is done over empty forest
      if( !updated ) _model.update(_key);  updated = true;
      Timer vi_timer = new Timer();
      _model._output._varimp = doVarImpCalc(false);
      Log.info("Computation of variable importance took: " + vi_timer.toString());
    }

    // Double update - after either scoring or variable importance
    if( updated ) _model.update(_key);
  }

}
