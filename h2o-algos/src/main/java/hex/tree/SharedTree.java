package hex.tree;

import hex.*;
import hex.genmodel.GenModel;
import hex.schemas.SharedTreeModelV3;
import jsr166y.CountedCompleter;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import water.*;
import water.H2O.H2OCountedCompleter;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.*;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public abstract class SharedTree<M extends SharedTreeModel<M,P,O>, P extends SharedTreeModel.SharedTreeParameters, O extends SharedTreeModel.SharedTreeOutput> extends ModelBuilder<M,P,O> {
  protected int _mtry;

  public static final int MAX_NTREES = 100000;

  public SharedTree( String name, P parms) { super(name,parms); /*only call init in leaf classes*/ }

  // Number of trees requested, including prior trees from a checkpoint
  protected int _ntrees;

  // The in-progress model being built
  protected M _model;

  // Number of columns in training set, not counting the response column
  protected int _ncols;

  // Initially predicted value (for zero trees)
  protected double _initialPrediction;

  // Sum of variable empirical improvement in squared-error.  The value is not scaled.
  private transient float[/*nfeatures*/] _improvPerVar;

  protected Random _rand;

  public boolean isSupervised(){return true;}

  Key _response_key;
  Key _vresponse_key;

  @Override
  public Vec response() {
    return _response == null ? (_response = DKV.getGet(_response_key)) : _response;
  }

  @Override
  public Vec vresponse() {
    if(_vresponse_key == null) return response();
    return _vresponse != null ? _vresponse:(_vresponse = DKV.getGet(_vresponse_key));
  }

  @Override public long progressUnits() { return _parms._ntrees; }

  @Override
  protected boolean computePriorClassDistribution(){ return true;}

  /** Initialize the ModelBuilder, validating all arguments and preparing the
   *  training frame.  This call is expected to be overridden in the subclasses
   *  and each subclass will start with "super.init();".  This call is made
   *  by the front-end whenever the GUI is clicked, and needs to be fast;
   *  heavy-weight prep needs to wait for the trainModel() call.
   *
   *  Validate the requested ntrees; precompute actual ntrees.  Validate
   *  the number of classes to predict on; validate a checkpoint.  */
  @Override public void init(boolean expensive) {
    super.init(expensive);
    if (H2O.ARGS.client && _parms._build_tree_one_node)
      error("_build_tree_one_node", "Cannot run on a single node in client mode");
    if(_vresponse != null)
      _vresponse_key = _vresponse._key;
    if(_response != null)
      _response_key = _response._key;

    if( _parms._min_rows < 0 )
      error("_min_rows", "Requested min_rows must be greater than 0");

    if( _parms._ntrees < 0 || _parms._ntrees > MAX_NTREES)
      error("_ntrees", "Requested ntrees must be between 1 and " + MAX_NTREES);
    _ntrees = _parms._ntrees;   // Total trees in final model
    if( _parms.hasCheckpoint() ) {  // Asking to continue from checkpoint?
      Value cv = DKV.get(_parms._checkpoint);
      if( cv != null ) {          // Look for prior model
        M checkpointModel = cv.get();
        try {
          _parms.validateWithCheckpoint(checkpointModel._parms);
          if( isClassifier() != checkpointModel._output.isClassifier() )
            throw new IllegalArgumentException("Response type must be the same as for the checkpointed model.");
          if (!Arrays.equals(_train.names(), checkpointModel._output._names)) {
            throw new IllegalArgumentException("The columns of the training data must be the same as for the checkpointed model");
          }
          if (!Arrays.deepEquals(_train.domains(), checkpointModel._output._domains)) {
            throw new IllegalArgumentException("Categorical factor levels of the training data must be the same as for the checkpointed model");
          }
        } catch (H2OIllegalArgumentException e) {
          error(e.values.get("argument").toString(), e.values.get("value").toString());
        }
        if( _parms._ntrees < checkpointModel._output._ntrees+1 )
          error("_ntrees", "If checkpoint is specified then requested ntrees must be higher than " + (checkpointModel._output._ntrees+1));

        // Compute number of trees to build for this checkpoint
        _ntrees = _parms._ntrees - checkpointModel._output._ntrees; // Needed trees
      }
    }
    if (_parms._nbins <= 1) error ("_nbins", "nbins must be > 1.");
    if (_parms._nbins >= 1<<16) error ("_nbins", "nbins must be < " + (1<<16));
    if (_parms._nbins_cats <= 1) error ("_nbins_cats", "nbins_cats must be > 1.");
    if (_parms._nbins_cats >= 1<<16) error ("_nbins_cats", "nbins_cats must be < " + (1<<16));
    if (_parms._nbins_top_level < _parms._nbins) error ("_nbins_top_level", "nbins_top_level must be >= nbins (" + _parms._nbins + ").");
    if (_parms._nbins_top_level >= 1<<16) error ("_nbins_top_level", "nbins_top_level must be < " + (1<<16));
    if (_parms._max_depth <= 0) error ("_max_depth", "_max_depth must be > 0.");
    if (_parms._min_rows <=0) error ("_min_rows", "_min_rows must be > 0.");
    if (!(0.0 < _parms._sample_rate && _parms._sample_rate <= 1.0))
      error("_sample_rate", "sample_rate should be in interval ]0,1] but it is " + _parms._sample_rate);
    if (_train != null) {
      double sumWeights = _train.numRows() * (hasWeightCol() ? _train.vec(_parms._weights_column).mean() : 1);
      if (sumWeights < 2*_parms._min_rows ) // Need at least 2*min_rows weighted rows to split even once
        error("_min_rows", "The dataset size is too small to split for min_rows=" + _parms._min_rows
                + ": must have at least " + 2*_parms._min_rows + " (weighted) rows, but have only " + sumWeights + ".");
    }
    if( _train != null )
      _ncols = _train.numCols()-1-numSpecialCols();
  }

  // --------------------------------------------------------------------------
  // Top-level tree-algo driver
  abstract protected class Driver extends H2OCountedCompleter<Driver> {
    protected Driver() { super(true); } // bump priority of model drivers

    @Override protected void compute2() {
      _model = null;            // Resulting model!
      try {
        Scope.enter();          // Cleanup temp keys
        init(true);             // Do any expensive tests & conversions now
        // Do lock even before checking the errors, since this block is finalized by unlock
        // (not the best solution, but the code is more readable)
        _parms.read_lock_frames(SharedTree.this); // Fetch & read-lock input frames
        if( error_count() > 0 ) {
          SharedTree.this.updateValidationMessages();
          throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(SharedTree.this);
        }

        // Create a New Model or continuing from a checkpoint
        if (_parms.hasCheckpoint()) {
          // Get the model to continue
          _model = getModelDeepClone(DKV.get(_parms._checkpoint).<M>get());
          // Override original parameters by new parameters
          _model._parms = _parms;
          // We create a new model
          _model.delete_and_lock(_key); //
        } else {                   // New Model
          // Compute the zero-tree error - guessing only the class distribution.
          // MSE is stddev squared when guessing for regression.
          // For classification, guess the largest class.
          _model = makeModel(_dest, _parms,
                  initial_MSE(_response, _response),
                  _valid == null ? Double.NaN : initial_MSE(_response,_vresponse)); // Make a fresh model
          _model.delete_and_lock(_key);       // and clear & write-lock it (smashing any prior)
          _model._output._init_f = _initialPrediction;
        }

        // Compute the response domain; makes for nicer printouts
        String[] domain = _response.domain();
        assert (_nclass > 1 && domain != null) || (_nclass==1 && domain==null);
        if( _nclass==1 ) domain = new String[] {"r"}; // For regression, give a name to class 0

        // Compute class distribution, used to for initial guesses and to
        // upsample minority classes (if asked for).
        if( _nclass>1 ) {       // Classification?

          // Handle imbalanced classes by stratified over/under-sampling.
          // initWorkFrame sets the modeled class distribution, and
          // model.score() corrects the probabilities back using the
          // distribution ratios
          if(_model._output.isClassifier() && _parms._balance_classes ) {

            float[] trainSamplingFactors = new float[_train.lastVec().domain().length]; //leave initialized to 0 -> will be filled up below
            if (_parms._class_sampling_factors != null) {
              if (_parms._class_sampling_factors.length != _train.lastVec().domain().length)
                throw new IllegalArgumentException("class_sampling_factors must have " + _train.lastVec().domain().length + " elements");
              trainSamplingFactors = _parms._class_sampling_factors.clone(); //clone: don't modify the original
            }
            Frame stratified = water.util.MRUtils.sampleFrameStratified(_train, _train.lastVec(), _train.vec(_model._output.weightsName()), trainSamplingFactors, (long)(_parms._max_after_balance_size*_train.numRows()), _parms._seed, true, false);
            if (stratified != _train) {
              _train = stratified;
              _response = stratified.vec(_parms._response_column);
              _weights = stratified.vec(_parms._weights_column);
              // Recompute distribution since the input frame was modified
              MRUtils.ClassDist cdmt2 = _weights != null ?
                      new MRUtils.ClassDist(_nclass).doAll(_response, _weights) : new MRUtils.ClassDist(_nclass).doAll(_response);
              _model._output._distribution = cdmt2.dist();
              _model._output._modelClassDist = cdmt2.rel_dist();
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
          _train.add("Tree_"+domain[i], _response.makeZero());

        // Initial work columns.  Set-before-use in the algos.
        for( int i=0; i<_nclass; i++ )
          _train.add("Work_"+domain[i], _response.makeZero());

        // One Tree per class, each tree needs a NIDs.  For empty classes use a -1
        // NID signifying an empty regression tree.
        for( int i=0; i<_nclass; i++ )
          _train.add("NIDs_"+domain[i], _response.makeCon(_model._output._distribution==null ? 0 : (_model._output._distribution[i]==0?-1:0)));

        // Append number of trees participating in on-the-fly scoring
        _train.add("OUT_BAG_TREES", _response.makeZero());

        // Tag out rows missing the response column
        new ExcludeNAResponse().doAll(_train);

        // Variable importance: squared-error-improvement-per-variable-per-split
        _improvPerVar = new float[_ncols];
        _rand = createRNG(_parms._seed);

        initializeModelSpecifics();
        resumeFromCheckpoint();
        scoreAndBuildTrees(doOOBScoring());

        done();                 // Job done!
      } catch( Throwable t ) {
        Job thisJob = DKV.getGet(_key);
        if (thisJob._state == JobState.CANCELLED) {
          Log.info("Job cancelled by user.");
        } else {
          t.printStackTrace();
          failed(t);
          throw t;
        }
      } finally {
        updateModelOutput();
        if (_model != null) _model.unlock(_key);
        _parms.read_unlock_frames(SharedTree.this);
        if (_model==null) {
          Scope.exit();
        } else {
          Scope.exit(_model._key, ModelMetrics.buildKey(_model,_parms.train()), ModelMetrics.buildKey(_model,_parms.valid()));
        }
      }
      tryComplete();
    }

    // Abstract classes implemented by the tree builders
    abstract protected M makeModel( Key modelKey, P parms, double mse_train, double mse_valid );
    abstract protected boolean doOOBScoring();
    abstract protected void buildNextKTrees();
    abstract protected void initializeModelSpecifics();

    // Common methods for all tree builders

    /**
     * Restore the workspace from a previous model (checkpoint)
     */
    protected final void resumeFromCheckpoint() {
      if( !_parms.hasCheckpoint() ) return;
      // Reconstruct the working tree state from the checkpoint
      Timer t = new Timer();
      int ntreesFromCheckpoint = ((SharedTreeModel.SharedTreeParameters) _parms._checkpoint.<SharedTreeModel>get()._parms)._ntrees;
      new ReconstructTreeState(_ncols, _nclass, numSpecialCols(), _parms._sample_rate,_model._output._treeKeys, doOOBScoring()).doAll(_train, _parms._build_tree_one_node);
      for (int i = 0; i < ntreesFromCheckpoint; i++) _rand.nextLong(); //for determinism
      Log.info("Reconstructing OOB stats from checkpoint took " + t);
    }

    /**
     * Build more trees, as specified by the model parameters
     * @param oob Whether or not Out-Of-Bag scoring should be performed
     */
    protected final void scoreAndBuildTrees(boolean oob) {
      for( int tid=0; tid< _ntrees; tid++) {
        // During first iteration model contains 0 trees, then 1-tree, ...
        // No need to score a checkpoint with no extra trees added
        if( tid!=0 || !_parms.hasCheckpoint() ) { // do not make initial scoring if model already exist
          double training_r2 = doScoringAndSaveModel(false, oob, _parms._build_tree_one_node);
          if( training_r2 >= _parms._r2_stopping ) {
            doScoringAndSaveModel(true, oob, _parms._build_tree_one_node);
            update(_ntrees-_model._output._ntrees); //finish
            return;             // Stop when approaching round-off error
          }
          if (!Double.isNaN(training_r2)  //HACK to detect whether we scored at all
                  && ScoreKeeper.earlyStopping(_model._output.scoreKeepers(), _parms._stopping_rounds, _nclass > 1, _parms._stopping_metric, _parms._stopping_tolerance)) {
            doScoringAndSaveModel(true, oob, _parms._build_tree_one_node);
            update(_ntrees-_model._output._ntrees); //finish
            return;
          }
        }
        Timer kb_timer = new Timer();
        buildNextKTrees();
        Log.info((tid + 1) + ". tree was built in " + kb_timer.toString());
        update(1);
        if( !isRunning() ) return; // If canceled during building, do not bulkscore
      }
      // Final scoring (skip if job was cancelled)
      doScoringAndSaveModel(true, oob, _parms._build_tree_one_node);
    }

    /** Performs deep clone of given model.
     *
     * FIXME: fetch all data to the caller node
     */
    protected M getModelDeepClone(M model) {
      M newModel = IcedUtils.clone(model, _dest);
      // Do not clone model metrics
      newModel._output.clearModelMetrics();
      newModel._output._training_metrics = null;
      newModel._output._validation_metrics = null;
      // Clone trees
      Key[][] treeKeys = newModel._output._treeKeys;
      for (int i = 0; i < treeKeys.length; i++) {
        for (int j = 0; j < treeKeys[i].length; j++) {
          if (treeKeys[i][j] == null) continue;
          CompressedTree ct = DKV.get(treeKeys[i][j]).get();
          CompressedTree newCt = IcedUtils.clone(ct, CompressedTree.makeTreeKey(i, j));
          DKV.put(treeKeys[i][j] = newCt._key,newCt);
        }
      }
      return newModel;
    }
  }

  // --------------------------------------------------------------------------
  // Build an entire layer of all K trees
  protected DHistogram[][][] buildLayer(final Frame fr, final int nbins, int nbins_cats, final DTree ktrees[], final int leafs[], final DHistogram hcs[][][], boolean subset, boolean build_tree_one_node) {
    // Build K trees, one per class.

    // Build up the next-generation tree splits from the current histograms.
    // Nearly all leaves will split one more level.  This loop nest is
    //           O( #active_splits * #bins * #ncols )
    // but is NOT over all the data.
    ScoreBuildOneTree sb1ts[] = new ScoreBuildOneTree[_nclass];
    Vec vecs[] = fr.vecs();
    for( int k=0; k<_nclass; k++ ) {
      final DTree tree = ktrees[k]; // Tree for class K
      if( tree == null ) continue;
      // Build a frame with just a single tree (& work & nid) columns, so the
      // nested MRTask ScoreBuildHistogram in ScoreBuildOneTree does not try
      // to close other tree's Vecs when run in parallel.
      Frame fr2 = new Frame(Arrays.copyOf(fr._names,_ncols+1), Arrays.copyOf(vecs,_ncols+1)); //predictors and actual response
      fr2.add(fr._names[idx_tree(k)],vecs[idx_tree(k)]); //tree predictions
      fr2.add(fr._names[idx_work(k)],vecs[idx_work(k)]); //target value to fit (copy of actual response for DRF, residual for GBM)
      fr2.add(fr._names[idx_nids(k)],vecs[idx_nids(k)]); //node indices
      if (idx_weight() >= 0)
        fr2.add(fr._names[idx_weight()],vecs[idx_weight()]); //observation weights
      // Start building one of the K trees in parallel
      H2O.submitTask(sb1ts[k] = new ScoreBuildOneTree(this,k,nbins, nbins_cats, tree, leafs, hcs, fr2, subset, build_tree_one_node, _improvPerVar, _model._parms._distribution));
    }
    // Block for all K trees to complete.
    boolean did_split=false;
    for( int k=0; k<_nclass; k++ ) {
      final DTree tree = ktrees[k]; // Tree for class K
      if( tree == null ) continue;
      sb1ts[k].join();
      if( sb1ts[k]._did_split ) did_split=true;
    }
    // The layer is done.
    return did_split ? hcs : null;
  }

  private static class ScoreBuildOneTree extends H2OCountedCompleter {
    final SharedTree _st;
    final int _k;               // The tree
    final int _nbins;           // Numerical columns: Number of histogram bins
    final int _nbins_cats;      // Categorical columns: Number of histogram bins
    final DTree _tree;
    final int _leafs[/*nclass*/];
    final DHistogram _hcs[/*nclass*/][][];
    final Frame _fr2;
    final boolean _subset;      // True if working a subset of cols
    final boolean _build_tree_one_node;
    float[] _improvPerVar;      // Squared Error improvement per variable per split
    Distribution.Family _family;

    boolean _did_split;
    ScoreBuildOneTree(SharedTree st, int k, int nbins, int nbins_cats, DTree tree, int leafs[], DHistogram hcs[][][], Frame fr2, boolean subset, boolean build_tree_one_node, float[] improvPerVar, Distribution.Family family) {
      _st   = st;
      _k    = k;
      _nbins= nbins;
      _nbins_cats= nbins_cats;
      _tree = tree;
      _leafs= leafs;
      _hcs  = hcs;
      _fr2  = fr2;
      _subset = subset;
      _build_tree_one_node = build_tree_one_node;
      _improvPerVar = improvPerVar;
      _family = family;
      // Raise the priority, so that if a thread blocks here, we are guaranteed
      // the task completes (perhaps using a higher-priority thread from the
      // upper thread pools).  This prevents thread deadlock.
      _priority = nextThrPriority();
    }
    @Override public void compute2() {
      // Fuse 2 conceptual passes into one:
      // Pass 1: Score a prior DHistogram, and make new Node assignments
      // to every row.  This involves pulling out the current assigned Node,
      // "scoring" the row against that Node's decision criteria, and assigning
      // the row to a new child Node (and giving it an improved prediction).
      // Pass 2: Build new summary DHistograms on the new child Nodes every row
      // got assigned into.  Collect counts, mean, variance, min, max per bin,
      // per column.
      new ScoreBuildHistogram(this,_k, _st._ncols, _nbins, _nbins_cats, _tree, _leafs[_k], _hcs[_k], _subset, _family).dfork(null,_fr2,_build_tree_one_node);
    }
    @Override public void onCompletion(CountedCompleter caller) {
      ScoreBuildHistogram sbh = (ScoreBuildHistogram)caller;
      //System.out.println(sbh.profString());

      final int leafk = _leafs[_k];
      int tmax = _tree.len();   // Number of total splits in tree K
      for( int leaf=leafk; leaf<tmax; leaf++ ) { // Visit all the new splits (leaves)
        DTree.UndecidedNode udn = _tree.undecided(leaf);
//        System.out.println((_st._nclass==1?"Regression":("Class "+_fr2.vecs()[_st._ncols].domain()[_k]))+",\n  Undecided node:"+udn);
        // Replace the Undecided with the Split decision
        DTree.DecidedNode dn = _st.makeDecided(udn,sbh._hcs[leaf-leafk]);
//        System.out.println(dn +
//                           "  > Split: " + dn._split + " L/R:" + dn._split._n0+" + "+dn._split._n1);
        if( dn._split._col == -1 ) udn.do_not_split();
        else {
          _did_split = true;
          DTree.Split s = dn._split; // Accumulate squared error improvements per variable
          AtomicUtils.FloatArray.add(_improvPerVar,s.col(),(float)(s.pre_split_se()-s.se()));
        }
      }
      _leafs[_k]=tmax;          // Setup leafs for next tree level
      int new_leafs = _tree.len()-tmax;
      _hcs[_k] = new DHistogram[new_leafs][/*ncol*/];
      for( int nl = tmax; nl<_tree.len(); nl ++ )
        _hcs[_k][nl-tmax] = _tree.undecided(nl)._hs;
      if (_did_split) _tree._depth++;
    }
    @Override public byte priority() { return _priority; }
    private final byte _priority;
  }

  // --------------------------------------------------------------------------
  // Convenience accessor for a complex chunk layout.
  // Wish I could name the array elements nicer...
  protected int idx_weight(   ) { return _model._output.weightsIdx(); }
  protected int idx_offset(   ) { return _model._output.offsetIdx(); }
  protected int idx_resp(     ) { return _model._output.responseIdx(); }
  protected int idx_tree(int c) { return _ncols+1+c+numSpecialCols(); }
  protected int idx_work(int c) { return idx_tree(c) + _nclass; }
  protected int idx_nids(int c) { return idx_work(c) + _nclass; }
  protected int idx_oobt()      { return idx_nids(0) + _nclass; }

  protected Chunk chk_weight( Chunk chks[]      ) { return chks[idx_weight()]; }
  protected Chunk chk_offset( Chunk chks[]      ) { return chks[idx_offset()]; }
  protected Chunk chk_resp( Chunk chks[]        ) { return chks[idx_resp()]; }
  protected Chunk chk_tree( Chunk chks[], int c ) { return chks[idx_tree(c)]; }
  protected Chunk chk_work( Chunk chks[], int c ) { return chks[idx_work(c)]; }
  protected Chunk chk_nids( Chunk chks[], int c ) { return chks[idx_nids(c)]; }
  // Out-of-bag trees counter - only one since it is shared via k-trees
  protected Chunk chk_oobt(Chunk chks[])          { return chks[idx_oobt()]; }

  protected final Vec vec_nids( Frame fr, int c) { return fr.vecs()[idx_nids(c)]; }
  protected final Vec vec_resp( Frame fr       ) { return fr.vecs()[idx_resp() ]; }
  protected final Vec vec_tree( Frame fr, int c) { return fr.vecs()[idx_tree(c)]; }

  protected double[] data_row( Chunk chks[], int row, double[] data) {
    assert data.length == _ncols;
    for(int f=0; f<_ncols; f++) data[f] = chks[f].atd(row);
    return data;
  }

  // Builder-specific decision node
  protected DTree.DecidedNode makeDecided( DTree.UndecidedNode udn, DHistogram hs[] ) {
    return new DTree.DecidedNode(udn, hs);
  }

  // Read the 'tree' columns, do model-specific math and put the results in the
  // fs[] array, and return the sum.  Dividing any fs[] element by the sum
  // turns the results into a probability distribution.
  abstract protected double score1( Chunk chks[], double offset, double weight, double fs[/*nclass*/], int row );

  // Call builder specific score code and then correct probabilities
  // if it is necessary.
  void score2(Chunk chks[], double weight, double offset, double fs[/*nclass*/], int row ) {
    double sum = score1(chks, weight, offset, fs, row);
    if( isClassifier()) {
      if( !Double.isInfinite(sum) && sum>0f && sum!=1f) ArrayUtils.div(fs, sum);
      if (_parms._balance_classes)
        GenModel.correctProbabilities(fs, _model._output._priorClassDist, _model._output._modelClassDist);
    }
  }

  // --------------------------------------------------------------------------
  // Tag out rows missing the response column
  class ExcludeNAResponse extends MRTask<ExcludeNAResponse> {
    @Override public void map( Chunk chks[] ) {
      Chunk ys = chk_resp(chks);
      for( int row=0; row<ys._len; row++ )
        if( ys.isNA(row) )
          for( int t=0; t<_nclass; t++ )
            chk_nids(chks,t).set(row, -1);
    }
  }

  // --------------------------------------------------------------------------
  transient long _timeLastScoreStart, _timeLastScoreEnd, _firstScore;
  protected final double doScoringAndSaveModel(boolean finalScoring, boolean oob, boolean build_tree_one_node ) {
    double training_r2 = Double.NaN; // Training R^2 value, if computed
    long now = System.currentTimeMillis();
    if( _firstScore == 0 ) _firstScore=now;
    long sinceLastScore = now-_timeLastScoreStart;
    boolean updated = false;
    new ProgressUpdate("Built " + _model._output._ntrees + " trees so far (out of " + _parms._ntrees + ").").fork(_progressKey);
    // Now model already contains tid-trees in serialized form
    if( _parms._score_each_iteration ||
            finalScoring ||
            (now-_firstScore < _parms._initial_score_interval) || // Score every time for 4 secs
            // Throttle scoring to keep the cost sane; limit to a 10% duty cycle & every 4 secs
            (sinceLastScore > _parms._score_interval && // Limit scoring updates to every 4sec
                    (double)(_timeLastScoreEnd-_timeLastScoreStart)/sinceLastScore < 0.1) ) { // 10% duty cycle

      checkMemoryFootPrint();

      // If validation is specified we use a model for scoring, so we need to
      // update it!  First we save model with trees (i.e., make them available
      // for scoring) and then update it with resulting error
      _model.update(_key);  updated = true;

      Log.info("============================================================== ");
      SharedTreeModel.SharedTreeOutput out = _model._output;
      _timeLastScoreStart = now;
      // Score on training data
      new ProgressUpdate("Scoring the model.").fork(_progressKey);
      Score sc = new Score(this,true,oob,_model._output.getModelCategory()).doAll(train(), build_tree_one_node);
      ModelMetrics mm = sc.makeModelMetrics(_model, _parms.train());
      out._training_metrics = mm;
      training_r2 = ((ModelMetricsSupervised)mm).r2();
      if (oob) out._training_metrics._description = "Metrics reported on Out-Of-Bag training samples";
      out._scored_train[out._ntrees].fillFrom(mm);

      // Score again on validation data
      if( _parms._valid != null ) {
        Score scv = new Score(this,false,false,_model._output.getModelCategory()).doAll(valid(), build_tree_one_node);
        ModelMetrics mmv = scv.makeModelMetrics(_model,_parms.valid());
        out._validation_metrics = mmv;
        out._scored_valid[out._ntrees].fillFrom(mmv);
      }
      if( out._ntrees > 0 ) {    // Compute variable importances
        out._model_summary = createModelSummaryTable(out);
        out._scoring_history = createScoringHistoryTable(out);
        out._varimp = new hex.VarImp(_improvPerVar, out._names);
        out._variable_importances = hex.ModelMetrics.calcVarImp(out._varimp);
      }
      if (_parms._score_each_iteration || finalScoring || sinceLastScore > _parms._score_interval) {
        Log.info(_model.toString());
      }
      _timeLastScoreEnd = System.currentTimeMillis();
    }

    // Double update - after either scoring or variable importance
    if( updated ) _model.update(_key);
    return training_r2;
  }

  static int counter = 0;
  // helper for debugging
  @SuppressWarnings("unused")
  static protected void printGenerateTrees(DTree[] trees) {
    for( DTree dtree : trees )
      if( dtree != null ) {
        try {
          PrintWriter writer = new PrintWriter("/tmp/h2o-3.tree" + ++counter + ".txt", "UTF-8");
          writer.println(dtree.root().toString2(new StringBuilder(), 0));
          writer.close();
        } catch (FileNotFoundException|UnsupportedEncodingException e) {
          e.printStackTrace();
        }
        System.out.println(dtree.root().toString2(new StringBuilder(), 0));
      }
  }

  //FIXME: Use weights
  double initial_MSE( Vec train, Vec test ) {
    if( train.isCategorical() ) {
      // Guess the class of the most populous class; call the fraction of those
      // Q.  Then Q of them are "mostly correct" - error is (1-Q) per element.
      // The remaining 1-Q elements are "mostly wrong", error is Q (our guess,
      // which is wrong).
      int cls = ArrayUtils.maxIndex(train.bins());
      double guess = train.bins()[cls]/(train.length()-train.naCnt());
      double actual= test .bins()[cls]/(test .length()-test .naCnt());
      return guess*guess+actual-2.0*actual*guess;
    } else {              // Regression
      // Guessing the training data mean, but actual is validation set mean
      double stddev = test.sigma();
      double bias = train.mean()-test.mean();
      return stddev*stddev+bias*bias;
    }
  }

  // Helper to unify use of M-T RNG
  public static Random createRNG(long seed) {
    return new RandomUtils.MersenneTwisterRNG((int)(seed>>32L),(int)seed );
//    return RandomUtils.getRNG((int)(seed>>32L),(int)seed ); //for later
  }

  private TwoDimTable createScoringHistoryTable(SharedTreeModel.SharedTreeOutput _output) {
    List<String> colHeaders = new ArrayList<>();
    List<String> colTypes = new ArrayList<>();
    List<String> colFormat = new ArrayList<>();
    colHeaders.add("Timestamp"); colTypes.add("string"); colFormat.add("%s");
    colHeaders.add("Duration"); colTypes.add("string"); colFormat.add("%s");
    colHeaders.add("Number of Trees"); colTypes.add("long"); colFormat.add("%d");
    colHeaders.add("Training MSE"); colTypes.add("double"); colFormat.add("%.5f");
    if (_output.getModelCategory() == ModelCategory.Regression) {
      colHeaders.add("Training Deviance"); colTypes.add("double"); colFormat.add("%.5f");
    }
    if (_output.isClassifier()) {
      colHeaders.add("Training LogLoss"); colTypes.add("double"); colFormat.add("%.5f");
    }
    if (_output.getModelCategory() == ModelCategory.Binomial) {
      colHeaders.add("Training AUC"); colTypes.add("double"); colFormat.add("%.5f");
    }
    if (_output.getModelCategory() == ModelCategory.Binomial || _output.getModelCategory() == ModelCategory.Multinomial) {
      colHeaders.add("Training Classification Error"); colTypes.add("double"); colFormat.add("%.5f");
    }

    if (valid() != null) {
      colHeaders.add("Validation MSE"); colTypes.add("double"); colFormat.add("%.5f");
      if (_output.getModelCategory() == ModelCategory.Regression) {
        colHeaders.add("Validation Deviance"); colTypes.add("double"); colFormat.add("%.5f");
      }
      if (_output.isClassifier()) {
        colHeaders.add("Validation LogLoss"); colTypes.add("double"); colFormat.add("%.5f");
      }
      if (_output.getModelCategory() == ModelCategory.Binomial) {
        colHeaders.add("Validation AUC"); colTypes.add("double"); colFormat.add("%.5f");
      }
      if (_output.isClassifier()) {
        colHeaders.add("Validation Classification Error"); colTypes.add("double"); colFormat.add("%.5f");
      }
    }

    int rows = 0;
    for( int i = 1; i<_output._scored_train.length; i++ ) {
      if (!Double.isNaN(_output._scored_train[i]._mse)) ++rows;
    }
    TwoDimTable table = new TwoDimTable(
            "Scoring History", null,
            new String[rows],
            colHeaders.toArray(new String[0]),
            colTypes.toArray(new String[0]),
            colFormat.toArray(new String[0]),
            "");
    int row = 0;
    for( int i = 1; i<_output._scored_train.length; i++ ) {
      if (Double.isNaN(_output._scored_train[i]._mse)) continue;
      int col = 0;
      assert(row < table.getRowDim());
      assert(col < table.getColDim());
      DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
      table.set(row, col++, fmt.print(_output._training_time_ms[i]));
      table.set(row, col++, PrettyPrint.msecs(_output._training_time_ms[i] - _start_time, true));
      table.set(row, col++, i);
      ScoreKeeper st = _output._scored_train[i];
      table.set(row, col++, st._mse);
      if (_output.getModelCategory() == ModelCategory.Regression) table.set(row, col++, st._mean_residual_deviance);
      if (_output.isClassifier()) table.set(row, col++, st._logloss);
      if (_output.getModelCategory() == ModelCategory.Binomial) table.set(row, col++, st._AUC);
      if (_output.isClassifier()) table.set(row, col++, st._classError);

      if (_valid != null) {
        st = _output._scored_valid[i];
        table.set(row, col++, st._mse);
        if (_output.getModelCategory() == ModelCategory.Regression) table.set(row, col++, st._mean_residual_deviance);
        if (_output.isClassifier()) table.set(row, col++, st._logloss);
        if (_output.getModelCategory() == ModelCategory.Binomial) table.set(row, col++, st._AUC);
        if (_output.isClassifier()) table.set(row, col++, st._classError);
      }
      row++;
    }
    return table;
  }

  private TwoDimTable createModelSummaryTable(SharedTreeModel.SharedTreeOutput _output) {
    List<String> colHeaders = new ArrayList<>();
    List<String> colTypes = new ArrayList<>();
    List<String> colFormat = new ArrayList<>();

    colHeaders.add("Number of Trees"); colTypes.add("long"); colFormat.add("%d");
    colHeaders.add("Model Size in Bytes"); colTypes.add("long"); colFormat.add("%d");

    colHeaders.add("Min. Depth"); colTypes.add("long"); colFormat.add("%d");
    colHeaders.add("Max. Depth"); colTypes.add("long"); colFormat.add("%d");
    colHeaders.add("Mean Depth"); colTypes.add("double"); colFormat.add("%.5f");

    colHeaders.add("Min. Leaves"); colTypes.add("long"); colFormat.add("%d");
    colHeaders.add("Max. Leaves"); colTypes.add("long"); colFormat.add("%d");
    colHeaders.add("Mean Leaves"); colTypes.add("double"); colFormat.add("%.5f");

    final int rows = 1;
    TwoDimTable table = new TwoDimTable(
            "Model Summary", null,
            new String[rows],
            colHeaders.toArray(new String[0]),
            colTypes.toArray(new String[0]),
            colFormat.toArray(new String[0]),
            "");
    int row = 0;
    int col = 0;
    table.set(row, col++, _output._treeStats._num_trees);
    table.set(row, col++, _output._treeStats._byte_size);
    table.set(row, col++, _output._treeStats._min_depth);
    table.set(row, col++, _output._treeStats._max_depth);
    table.set(row, col++, _output._treeStats._mean_depth);
    table.set(row, col++, _output._treeStats._min_leaves);
    table.set(row, col++, _output._treeStats._max_leaves);
    table.set(row, col++, _output._treeStats._mean_leaves);
    return table;
  }

  /**
   * Compute the *actual* byte size of a tree model in the KV store
   */
  private static class ComputeModelSize extends MRTask<ComputeModelSize> {
    long _model_mem_size; //OUTPUT
    final int trees_so_far; //INPUT
    final public Key<CompressedTree>[/*_ntrees*/][/*_nclass*/] _treeKeys; //INPUT

    public ComputeModelSize(int trees_so_far, Key<CompressedTree>[][] _treeKeys) {
      this.trees_so_far = trees_so_far;
      this._treeKeys = _treeKeys;
    }

    @Override protected void setupLocal() {
      _model_mem_size = 0;
      for (int i=0; i< trees_so_far; ++i) {
        Key<CompressedTree>[] per_class = _treeKeys[i];
        for (int j=0; j<per_class.length; ++j) {
          if (per_class[j] == null) continue;
          if (!per_class[j].home()) continue;
          // only look at homed tree keys
          _model_mem_size += DKV.get(per_class[j])._max;
        }
      }
    }

    @Override public void reduce(ComputeModelSize cms){
      if (cms != null)
        _model_mem_size += cms._model_mem_size;
    }
  }

  @Override protected void checkMemoryFootPrint() {
    if (_model._output._ntrees == 0) return;
    int trees_so_far = _model._output._ntrees; //existing trees
    long model_mem_size = new ComputeModelSize(trees_so_far, _model._output._treeKeys).doAllNodes()._model_mem_size;
    _model._output._treeStats._byte_size = model_mem_size;
    double avg_tree_mem_size = (double)model_mem_size / trees_so_far;
    Log.debug("Average tree size (for all classes): " + PrettyPrint.bytes((long)avg_tree_mem_size));

    // all the compressed trees are stored on the driver node
    long max_mem = H2O.SELF.get_max_mem();
    if (_parms._ntrees * avg_tree_mem_size > max_mem) {
      String msg = "The tree model will not fit in the driver node's memory ("
              + PrettyPrint.bytes((long)avg_tree_mem_size)
              + " per tree x " + _parms._ntrees + " > "
              + PrettyPrint.bytes(max_mem)
              + ") - try decreasing ntrees and/or max_depth or increasing min_rows!";
      error("_ntrees", msg);
      cancel(msg);
    }
  }

  /**
   * Compute the inital value for a given distribution
   * @return initial value
   */
  protected double getInitialValue() {
    return new InitialValue(_parms._distribution, _parms._tweedie_power).doAll(
            _response,
            hasWeightCol() ? _weights : _response.makeCon(1),
            hasOffsetCol() ? _offset : _response.makeCon(0)
    ).initialValue();
  }

  // Helper MRTask to compute the initial value
  private static class InitialValue extends MRTask<InitialValue> {
    public  InitialValue(Distribution.Family family, double power) { _dist = new Distribution(family, power); }
    final private Distribution _dist;
    private double _num;
    private double _denom;

    public  double initialValue() {
      if (_dist.distribution == Distribution.Family.multinomial)
        return -0.5*new Distribution(Distribution.Family.bernoulli).link(_num/_denom);
      else return _dist.link(_num / _denom);
    }
    @Override public void map(Chunk response, Chunk weight, Chunk offset) {
      for (int i=0;i<response._len;++i) {
        if (response.isNA(i)) continue;
        double w = weight.atd(i);
        if (w == 0) continue;
        double y = response.atd(i);
        double o = offset.atd(i);
        _num += _dist.initFNum(w,o,y);
        _denom += _dist.initFDenom(w,o);
      }
    }
    @Override public void reduce(InitialValue mrt) {
      _num += mrt._num;
      _denom += mrt._denom;
    }
  }

  @Override
  public void modifyParmsForCrossValidationMainModel(int N, Key<Model>[] cvModelBuilderKeys) {
    super.modifyParmsForCrossValidationMainModel(N, cvModelBuilderKeys);
    if (cvModelBuilderKeys !=null) {
      if (_parms._stopping_rounds > 0) {
        int[] ntrees = new int[cvModelBuilderKeys.length];
        for (int i=0;i<ntrees.length;++i) {
          ntrees[i] = ((SharedTreeModel.SharedTreeOutput)((SharedTreeModel)DKV.getGet((((SharedTree)DKV.getGet(cvModelBuilderKeys[i])).dest())))._output)._ntrees;
        }
        _parms._ntrees = ArrayUtils.sum(ntrees)/ntrees.length;
        warn("_epochs", "Setting optimal _ntrees to " + _parms._ntrees + " for cross-validation main model based on early stopping of cross-validation models.");
        _parms._stopping_rounds = 0;
        warn("_stopping_rounds", "Disabling convergence-based early stopping for cross-validation main model.");
      }
    }
  }
}
