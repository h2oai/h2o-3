package hex.tree;

import hex.*;
import hex.genmodel.GenModel;
import hex.genmodel.utils.DistributionFamily;
import hex.quantile.Quantile;
import hex.quantile.QuantileModel;
import hex.util.LinearAlgebraUtils;
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
  final protected static boolean DEV_DEBUG = false;
  protected int _mtry;
  protected int _mtry_per_tree;

  public static final int MAX_NTREES = 100000;

  public SharedTree(P parms            ) { super(parms    ); /*only call init in leaf classes*/ }
  public SharedTree(P parms, Key<M> key) { super(parms,key); /*only call init in leaf classes*/ }
  public SharedTree(P parms, Job job   ) { super(parms,job); /*only call init in leaf classes*/ }
  public SharedTree(P parms, boolean startup_once) { super(parms,startup_once); /*only call init in leaf classes*/ }

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

  @Override public boolean haveMojo() { return true; }
  @Override public boolean havePojo() { return true; }

  public boolean scoreZeroTrees(){return true;}

  @Override protected boolean computePriorClassDistribution(){ return true;}

  @Override
  public ToEigenVec getToEigenVec() {
    return LinearAlgebraUtils.toEigen;
  }

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
      error("_build_tree_one_node", "Cannot run on a single node in client mode.");

    if( _parms._min_rows < 0 )
      error("_min_rows", "Requested min_rows must be greater than 0");

    if (_parms._categorical_encoding == Model.Parameters.CategoricalEncodingScheme.OneHotInternal) {
      error("_categorical_encoding", "Cannot use OneHotInternal categorical encoding for tree methods.");
    }

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
    if (_parms._r2_stopping!=Double.MAX_VALUE) warn("_r2_stopping", "_r2_stopping is no longer supported - please use stopping_rounds, stopping_metric and stopping_tolerance instead.");
    if (_parms._score_tree_interval < 0) error ("_score_tree_interval", "_score_tree_interval must be >= 0.");
    if (_parms._sample_rate_per_class != null) {
      warn("_sample_rate", "_sample_rate is ignored if _sample_rate_per_class is specified.");
      if (_parms._sample_rate_per_class.length != nclasses()) error("_sample_rate_per_class", "_sample_rate_per_class must have " + nclasses() + " values (one per class).");
      for (int i=0;i<_parms._sample_rate_per_class.length;++i) {
        if (!(0.0 < _parms._sample_rate_per_class[i] && _parms._sample_rate_per_class[i] <= 1.0))
          error("_sample_rate_per_class", "sample_rate_per_class for class " + response().domain()[i] + " should be in interval ]0,1] but it is " + _parms._sample_rate_per_class[i] + ".");
      }
    }
    if (!(0.0 < _parms._sample_rate && _parms._sample_rate <= 1.0))
      error("_sample_rate", "sample_rate should be in interval ]0,1] but it is " + _parms._sample_rate + ".");
    if (_parms._min_split_improvement < 0)
      error("_min_split_improvement", "min_split_improvement must be >= 0, but is " + _parms._min_split_improvement + ".");
    if (!(0.0 < _parms._col_sample_rate_per_tree && _parms._col_sample_rate_per_tree <= 1.0))
      error("_col_sample_rate_per_tree", "col_sample_rate_per_tree should be in interval ]0,1] but it is " + _parms._col_sample_rate_per_tree + ".");
    if( !(0. < _parms._col_sample_rate_change_per_level && _parms._col_sample_rate_change_per_level <= 2) )
      error("_col_sample_rate_change_per_level", "col_sample_rate_change_per_level must be between 0 and 2");
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
  abstract protected class Driver extends ModelBuilder<M,P,O>.Driver {

    @Override public void computeImpl() {
      _model = null;            // Resulting model!
      try {
        init(true);             // Do any expensive tests & conversions now
        if( error_count() > 0 )
          throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(SharedTree.this);

        // Create a New Model or continuing from a checkpoint
        if (_parms.hasCheckpoint()) {
          // Get the model to continue
          _model = DKV.get(_parms._checkpoint).<M>get().deepClone(_result);
          // Override original parameters by new parameters
          _model._parms = _parms;
          // We create a new model
          _model.delete_and_lock(_job);
        } else {                   // New Model
          // Compute the zero-tree error - guessing only the class distribution.
          // MSE is stddev squared when guessing for regression.
          // For classification, guess the largest class.
          _model = makeModel(dest(), _parms);
          _model.delete_and_lock(_job); // and clear & write-lock it (smashing any prior)
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
          if (_parms._sample_rate_per_class != null) {
            Log.info("Sample rates per tree (this affects the distribution of probabilities):");
            for (int i = 0; i < nclasses(); ++i)
              Log.info(" sample rate for class '" + response().domain()[i] + "' : " + _parms._sample_rate_per_class[i]);
          }
        }

        // top-level quantiles for all columns
        // non-numeric columns get a vector full of NAs
        if (_parms._histogram_type == SharedTreeModel.SharedTreeParameters.HistogramType.QuantilesGlobal
                || _parms._histogram_type == SharedTreeModel.SharedTreeParameters.HistogramType.RoundRobin) {
          int N = _parms._nbins;
          QuantileModel.QuantileParameters p = new QuantileModel.QuantileParameters();
          Key rndKey = Key.make();
          if (DKV.get(rndKey)==null) DKV.put(rndKey, _train);
          p._train = rndKey;
          p._weights_column = _parms._weights_column;
          p._combine_method = QuantileModel.CombineMethod.INTERPOLATE;
          p._probs = new double[N];
          for (int i = 0; i < N; ++i) //compute quantiles such that they span from (inclusive) min...maxEx (exclusive)
            p._probs[i] = i * 1./N;
          Job<QuantileModel> job = new Quantile(p).trainModel();
          _job.update(1, "Computing top-level histogram splitpoints.");
          QuantileModel qm = job.get();
          job.remove();
          double[][] origQuantiles = qm._output._quantiles;
          //pad the quantiles until we have nbins_top_level bins
          double[][] splitPoints = new double[origQuantiles.length][];
          Key[] keys = new Key[splitPoints.length];
          for (int i=0;i<keys.length;++i)
            keys[i] = getGlobalQuantilesKey(i);
          for (int i=0;i<origQuantiles.length;++i) {
            if (!_train.vec(i).isNumeric() || _train.vec(i).isCategorical() || _train.vec(i).isBinary() || origQuantiles[i].length <= 1) {
              keys[i] = null;
              continue;
            }
            // make the quantiles split points unique
            splitPoints[i] = ArrayUtils.makeUniqueAndLimitToRange(origQuantiles[i], _train.vec(i).min(), _train.vec(i).max());
            if (splitPoints[i].length <= 1) //not enough split points left - fall back to regular binning
              splitPoints[i] = null;
            else
              splitPoints[i] = ArrayUtils.padUniformly(splitPoints[i], _parms._nbins_top_level);
            assert splitPoints[i] == null || splitPoints[i].length > 1;
            if (splitPoints[i]!=null && keys[i]!=null) {
//              Log.info("Creating quantiles for column " + i + " (key: "+ keys[i] +")");
//              Log.info("Quantiles for column " + i + ": " + Arrays.toString(quantiles[i]));
              DKV.put(new DHistogram.HistoQuantiles(keys[i], splitPoints[i]));
            }
          }
          qm.delete();
          DKV.remove(rndKey);
        }

        // Also add to the basic working Frame these sets:
        //   nclass Vecs of current forest results (sum across all trees)
        //   nclass Vecs of working/temp data
        //   nclass Vecs of NIDs, allowing 1 tree per class


        String [] twNames = new String[_nclass*2];

        for(int i = 0; i < _nclass; ++i){
          twNames[i] = "Tree_" + domain[i];
          twNames[_nclass+i] = "Work_" + domain[i];
        }
        Vec [] twVecs = _response.makeVolatileDoubles(_nclass*2);
        _train.add(twNames,twVecs);


        // One Tree per class, each tree needs a NIDs.  For empty classes use a -1
        // NID signifying an empty regression tree.
        String [] names = new String[_nclass];
        final int [] cons = new int[_nclass];
        for( int i=0; i<_nclass; i++ ) {
          names[i] = "NIDs_" + domain[i];
          cons[i] = (_model._output._distribution[i]==0?-1:0);
        }
        Vec [] vs = _response.makeVolatileInts(cons);
        _train.add(names, vs);
        // Append number of trees participating in on-the-fly scoring
        _train.add("OUT_BAG_TREES", _response.makeZero());

        // Variable importance: squared-error-improvement-per-variable-per-split
        _improvPerVar = new float[_ncols];
        _rand = RandomUtils.getRNG(_parms._seed);

        initializeModelSpecifics();
        resumeFromCheckpoint(SharedTree.this);
        scoreAndBuildTrees(doOOBScoring());

      } finally {
        if( _model!=null ) _model.unlock(_job);
        for (Key k : getGlobalQuantilesKeys()) if (k!=null) k.remove();
      }
    }

    // Abstract classes implemented by the tree builders
    abstract protected M makeModel( Key modelKey, P parms);
    abstract protected boolean doOOBScoring();
    abstract protected boolean buildNextKTrees();
    abstract protected void initializeModelSpecifics();

    // Common methods for all tree builders


    // Helpers to store quantiles in DKV - keep a cache on each node (instead of sending around over and over)
    protected Key getGlobalQuantilesKey(int i) {
      if (_model==null || _model._key == null || _parms._histogram_type!= SharedTreeModel.SharedTreeParameters.HistogramType.QuantilesGlobal
              && _parms._histogram_type!= SharedTreeModel.SharedTreeParameters.HistogramType.RoundRobin) return null;
      return Key.makeSystem(_model._key+"_quantiles_col_"+i);
    }
    protected Key[] getGlobalQuantilesKeys() {
      Key[] keys = new Key[_ncols];
      for (int i=0;i<keys.length;++i)
        keys[i] = getGlobalQuantilesKey(i);
      return keys;
    }

    /**
     * Restore the workspace from a previous model (checkpoint)
     */
    protected final void resumeFromCheckpoint(SharedTree st) {
      if( !_parms.hasCheckpoint() ) return;
      // Reconstruct the working tree state from the checkpoint
      Timer t = new Timer();
      int ntreesFromCheckpoint = ((SharedTreeModel.SharedTreeParameters) _parms._checkpoint.<SharedTreeModel>get()._parms)._ntrees;
      new ReconstructTreeState(_ncols, _nclass, st /*large, but cleaner code this way*/, _parms._sample_rate,_model._output._treeKeys, doOOBScoring()).doAll(_train, _parms._build_tree_one_node);
      for (int i = 0; i < ntreesFromCheckpoint; i++) _rand.nextLong(); //for determinism
      Log.info("Reconstructing OOB stats from checkpoint took " + t);
      if (DEV_DEBUG) {
        System.out.println(_train.toTwoDimTable());
      }
    }

    /**
     * Build more trees, as specified by the model parameters
     * @param oob Whether or not Out-Of-Bag scoring should be performed
     */
    protected final void scoreAndBuildTrees(boolean oob) {
      for( int tid=0; tid< _ntrees; tid++) {
        // During first iteration model contains 0 trees, then 1-tree, ...
        boolean scored = doScoringAndSaveModel(false, oob, _parms._build_tree_one_node);
        if (scored && ScoreKeeper.stopEarly(_model._output.scoreKeepers(), _parms._stopping_rounds, _nclass > 1, _parms._stopping_metric, _parms._stopping_tolerance, "model's last", true)) {
          doScoringAndSaveModel(true, oob, _parms._build_tree_one_node);
          _job.update(_ntrees-_model._output._ntrees); //finish
          return;
        }
        Timer kb_timer = new Timer();
        boolean converged = buildNextKTrees();
        Log.info((tid + 1) + ". tree was built in " + kb_timer.toString());
        _job.update(1);
        if (_model._output._treeStats._max_depth==0) {
          Log.warn("Nothing to split on: Check that response and distribution are meaningful (e.g., you are not using laplace/quantile regression with a binary response).");
        }
        if (converged || timeout()) {
          _job.update(_parms._ntrees-tid-1); // add remaining trees to progress bar
          break; // If timed out, do the final scoring
        }
        if (stop_requested()) throw new Job.JobCancelledException();
      }
      // Final scoring (skip if job was cancelled)
      doScoringAndSaveModel(true, oob, _parms._build_tree_one_node);
    }
  }

  // --------------------------------------------------------------------------
  // Build an entire layer of all K trees
  protected DHistogram[][][] buildLayer(final Frame fr, final int nbins, int nbins_cats, final DTree ktrees[], final int leafs[], final DHistogram hcs[][][], boolean build_tree_one_node) {
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

      // Add temporary workspace vectors (optional weights are taken over from fr)
      int weightIdx = fr2.find(_parms._weights_column);
      fr2.add(fr._names[idx_tree(k)],vecs[idx_tree(k)]);                              //tree predictions
      int workIdx = fr2.numCols(); fr2.add(fr._names[idx_work(k)],vecs[idx_work(k)]); //target value to fit (copy of actual response for DRF, residual for GBM)
      int nidIdx  = fr2.numCols(); fr2.add(fr._names[idx_nids(k)],vecs[idx_nids(k)]); //node indices for tree construction
      if (DEV_DEBUG) {
        System.out.println("Building a layer for class " + k + ":\n" + fr2.toTwoDimTable());
      }
      // Async tree building
      // step 1: build histograms
      // step 2: split nodes
      H2O.submitTask(sb1ts[k] = new ScoreBuildOneTree(this,k,nbins, nbins_cats, tree, leafs, hcs, fr2, build_tree_one_node, _improvPerVar, _model._parms._distribution, weightIdx, workIdx, nidIdx));
    }
    // Block for all K trees to complete.
    boolean did_split=false;
    for( int k=0; k<_nclass; k++ ) {
      final DTree tree = ktrees[k]; // Tree for class K
      if( tree == null ) continue;
      sb1ts[k].join();
      if( sb1ts[k]._did_split ) did_split=true;
      if (DEV_DEBUG) {
        System.out.println("Done with this layer for class " + k + ":\n" + new Frame(
                new String[]{"TREE", "WORK", "NIDS"},
                new Vec[]{
                        vecs[idx_tree(k)],
                        vecs[idx_work(k)],
                        vecs[idx_nids(k)]
                }
        ).toTwoDimTable());
      }
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
    final int _leafOffsets[/*nclass*/]; //Index of the first leaf node. Leaf indices range from _leafOffsets[k] to _tree._len-1
    final DHistogram _hcs[/*nclass*/][][];
    final Frame _fr2;
    final boolean _build_tree_one_node;
    final float[] _improvPerVar;      // Squared Error improvement per variable per split
    final DistributionFamily _family;
    final int _weightIdx;
    final int _workIdx;
    final int _nidIdx;

    boolean _did_split;

    ScoreBuildOneTree(SharedTree st, int k, int nbins, int nbins_cats, DTree tree, int leafs[], DHistogram hcs[][][], Frame fr2, boolean build_tree_one_node, float[] improvPerVar, DistributionFamily family, int weightIdx, int workIdx, int nidIdx) {
      _st   = st;
      _k    = k;
      _nbins= nbins;
      _nbins_cats= nbins_cats;
      _tree = tree;
      _leafOffsets = leafs;
      _hcs  = hcs;
      _fr2  = fr2;
      _build_tree_one_node = build_tree_one_node;
      _improvPerVar = improvPerVar;
      _family = family;
      _weightIdx = weightIdx;
      _workIdx = workIdx;
      _nidIdx = nidIdx;
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
//      new ScoreBuildHistogram(this,_k, _st._ncols, _nbins, _nbins_cats, _tree, _leafOffsets[_k], _hcs[_k], _family, _weightIdx, _workIdx, _nidIdx).dfork2(null,_fr2,_build_tree_one_node);
      new ScoreBuildHistogram2(this,_k, _st._ncols, _nbins, _nbins_cats, _tree, _leafOffsets[_k], _hcs[_k], _family, _weightIdx, _workIdx, _nidIdx).dfork2(null,_fr2,_build_tree_one_node);
    }
    @Override public void onCompletion(CountedCompleter caller) {
      ScoreBuildHistogram sbh = (ScoreBuildHistogram) caller;
      final int leafOffset = _leafOffsets[_k];
      int tmax = _tree.len();   // Number of total splits in tree K
      for (int leaf = leafOffset; leaf < tmax; leaf++) { // Visit all the new splits (leaves)
        DTree.UndecidedNode udn = _tree.undecided(leaf);
//        System.out.println((_st._nclass==1?"Regression":("Class "+_st._response.domain()[_k]))+",\n  Undecided node:"+udn);
        // Replace the Undecided with the Split decision
        DTree.DecidedNode dn = _st.makeDecided(udn, sbh._hcs[leaf - leafOffset]);
//        System.out.println(dn + "\n" + dn._split);
        if (dn._split == null) udn.do_not_split();
        else {
          _did_split = true;
          DTree.Split s = dn._split; // Accumulate squared error improvements per variable
          float improvement = (float) (s.pre_split_se() - s.se());
          assert (improvement >= 0);
          AtomicUtils.FloatArray.add(_improvPerVar, s.col(), improvement);
        }
      }
      _leafOffsets[_k] = tmax;          // Setup leafs for next tree level
      int new_leafs = _tree.len() - tmax; //new_leafs can be 0 if no actual splits were made
      _hcs[_k] = new DHistogram[new_leafs][/*ncol*/];
      for (int nl = tmax; nl < _tree.len(); nl++)
        _hcs[_k][nl - tmax] = _tree.undecided(nl)._hs;
//      if (_did_split && new_leafs > 0) _tree._depth++;
      if (_did_split) _tree._depth++; //
    }
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

  public Chunk chk_weight( Chunk chks[]      ) { return chks[idx_weight()]; }
  protected Chunk chk_offset( Chunk chks[]      ) { return chks[idx_offset()]; }
  public Chunk chk_resp(Chunk chks[]) { return chks[idx_resp()]; }
  public Chunk chk_tree(Chunk chks[], int c) { return chks[idx_tree(c)]; }
  protected Chunk chk_work( Chunk chks[], int c ) { return chks[idx_work(c)]; }
  protected Chunk chk_nids( Chunk chks[], int c ) { return chks[idx_nids(c)]; }
  protected Chunk chk_oobt(Chunk chks[])          { return chks[idx_oobt()]; }

  protected final Vec vec_weight(Frame fr      ) { return fr.vecs()[idx_weight()]; }
  protected final Vec vec_offset(Frame fr      ) { return fr.vecs()[idx_offset()]; }
  protected final Vec vec_resp( Frame fr       ) { return fr.vecs()[idx_resp() ]; }
  protected final Vec vec_tree( Frame fr, int c) { return fr.vecs()[idx_tree(c)]; }
  protected final Vec vec_work( Frame fr, int c) { return fr.vecs()[idx_work(c)]; }
  protected final Vec vec_nids( Frame fr, int c) { return fr.vecs()[idx_nids(c)]; }
  protected final Vec vec_oobt( Frame fr       ) { return fr.vecs()[idx_oobt()]; }

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
  transient long _timeLastScoreStart, _timeLastScoreEnd, _firstScore;
  protected final boolean doScoringAndSaveModel(boolean finalScoring, boolean oob, boolean build_tree_one_node ) {
    long now = System.currentTimeMillis();
    if( _firstScore == 0 ) _firstScore=now;
    long sinceLastScore = now-_timeLastScoreStart;
    boolean updated = false;
    _job.update(0,"Built " + _model._output._ntrees + " trees so far (out of " + _parms._ntrees + ").");

    boolean timeToScore = (now-_firstScore < _parms._initial_score_interval) || // Score every time for 4 secs
        // Throttle scoring to keep the cost sane; limit to a 10% duty cycle & every 4 secs
        (sinceLastScore > _parms._score_interval && // Limit scoring updates to every 4sec
            (double)(_timeLastScoreEnd-_timeLastScoreStart)/sinceLastScore < 0.1); //10% duty cycle

    boolean manualInterval = _parms._score_tree_interval > 0 && _model._output._ntrees % _parms._score_tree_interval == 0;

    // Now model already contains tid-trees in serialized form
    if( _parms._score_each_iteration || finalScoring || // always score under these circumstances
        (timeToScore && _parms._score_tree_interval == 0) || // use time-based duty-cycle heuristic only if the user didn't specify _score_tree_interval
        manualInterval) {
      checkMemoryFootPrint();
      if (error_count() > 0)
        throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(SharedTree.this);

      // If validation is specified we use a model for scoring, so we need to
      // update it!  First we save model with trees (i.e., make them available
      // for scoring) and then update it with resulting error
      _model.update(_job);
      updated = true;

      Log.info("============================================================== ");
      SharedTreeModel.SharedTreeOutput out = _model._output;
      _timeLastScoreStart = now;

      final boolean printout = (_parms._score_each_iteration || finalScoring || sinceLastScore > _parms._score_interval);
//      final boolean computeGainsLift = printout;  //only compute Gains/Lift during final scoring
      final boolean computeGainsLift = true;

      // Score on training data
      _job.update(0,"Scoring the model.");
      _model._output._job = _job; // to allow to share the job for quantiles task
      Score sc = new Score(this,_model._output._ntrees>0/*score 0-tree model from scratch*/,oob,response()._key,_model._output.getModelCategory(),computeGainsLift).doAll(train(), build_tree_one_node);
      ModelMetrics mm = sc.makeModelMetrics(_model, _parms.train());
      out._training_metrics = mm;
      if (oob) out._training_metrics._description = "Metrics reported on Out-Of-Bag training samples";
      out._scored_train[out._ntrees].fillFrom(mm);

      // Score again on validation data
      if( _parms._valid != null ) {
        Score scv = new Score(this,false,false,vresponse()._key,_model._output.getModelCategory(),computeGainsLift).doAll(valid(), build_tree_one_node);
        ModelMetrics mmv = scv.makeModelMetrics(_model,_parms.valid());
        out._validation_metrics = mmv;
        if (_model._output._ntrees>0 || scoreZeroTrees()) //don't score the 0-tree model - the error is too large
          out._scored_valid[out._ntrees].fillFrom(mmv);
      }
      out._model_summary = createModelSummaryTable(out);
      out._scoring_history = createScoringHistoryTable(out);
      if( out._ntrees > 0 ) {    // Compute variable importances
        out._varimp = new hex.VarImp(_improvPerVar, out._names);
        out._variable_importances = hex.ModelMetrics.calcVarImp(out._varimp);
      }
      if (printout) {
        Log.info(_model.toString());
      }
      _timeLastScoreEnd = System.currentTimeMillis();
    }

    // Double update - after either scoring or variable importance
    if( updated ) _model.update(_job);
    return updated;
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

  private TwoDimTable createScoringHistoryTable(SharedTreeModel.SharedTreeOutput _output) {
    List<String> colHeaders = new ArrayList<>();
    List<String> colTypes = new ArrayList<>();
    List<String> colFormat = new ArrayList<>();
    colHeaders.add("Timestamp"); colTypes.add("string"); colFormat.add("%s");
    colHeaders.add("Duration"); colTypes.add("string"); colFormat.add("%s");
    colHeaders.add("Number of Trees"); colTypes.add("long"); colFormat.add("%d");
    colHeaders.add("Training RMSE"); colTypes.add("double"); colFormat.add("%.5f");
    if (_output.getModelCategory() == ModelCategory.Regression) {
      colHeaders.add("Training MAE"); colTypes.add("double"); colFormat.add("%.5f");
      colHeaders.add("Training Deviance"); colTypes.add("double"); colFormat.add("%.5f");
    }
    if (_output.isClassifier()) {
      colHeaders.add("Training LogLoss"); colTypes.add("double"); colFormat.add("%.5f");
    }
    if (_output.getModelCategory() == ModelCategory.Binomial) {
      colHeaders.add("Training AUC"); colTypes.add("double"); colFormat.add("%.5f");
      colHeaders.add("Training Lift"); colTypes.add("double"); colFormat.add("%.5f");
    }
    if (_output.getModelCategory() == ModelCategory.Binomial || _output.getModelCategory() == ModelCategory.Multinomial) {
      colHeaders.add("Training Classification Error"); colTypes.add("double"); colFormat.add("%.5f");
    }

    if (valid() != null) {
      colHeaders.add("Validation RMSE"); colTypes.add("double"); colFormat.add("%.5f");
      if (_output.getModelCategory() == ModelCategory.Regression) {
        colHeaders.add("Validation MAE"); colTypes.add("double"); colFormat.add("%.5f");
        colHeaders.add("Validation Deviance"); colTypes.add("double"); colFormat.add("%.5f");
      }
      if (_output.isClassifier()) {
        colHeaders.add("Validation LogLoss"); colTypes.add("double"); colFormat.add("%.5f");
      }
      if (_output.getModelCategory() == ModelCategory.Binomial) {
        colHeaders.add("Validation AUC"); colTypes.add("double"); colFormat.add("%.5f");
        colHeaders.add("Validation Lift"); colTypes.add("double"); colFormat.add("%.5f");
      }
      if (_output.isClassifier()) {
        colHeaders.add("Validation Classification Error"); colTypes.add("double"); colFormat.add("%.5f");
      }
    }

    int rows = 0;
    for( int i = 0; i<_output._scored_train.length; i++ ) {
      if (i != 0 && Double.isNaN(_output._scored_train[i]._rmse) && (_output._scored_valid == null || Double.isNaN(_output._scored_valid[i]._rmse))) continue;
      rows++;
    }
    TwoDimTable table = new TwoDimTable(
            "Scoring History", null,
            new String[rows],
            colHeaders.toArray(new String[0]),
            colTypes.toArray(new String[0]),
            colFormat.toArray(new String[0]),
            "");
    int row = 0;
    for( int i = 0; i<_output._scored_train.length; i++ ) {
      if (i != 0 && Double.isNaN(_output._scored_train[i]._rmse) && (_output._scored_valid == null || Double.isNaN(_output._scored_valid[i]._rmse))) continue;
      int col = 0;
      DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
      table.set(row, col++, fmt.print(_output._training_time_ms[i]));
      table.set(row, col++, PrettyPrint.msecs(_output._training_time_ms[i] - _job.start_time(), true));
      table.set(row, col++, i);
      ScoreKeeper st = _output._scored_train[i];
      table.set(row, col++, st._rmse);
      if (_output.getModelCategory() == ModelCategory.Regression) {
        table.set(row, col++, st._mae);
        table.set(row, col++, st._mean_residual_deviance);
      }
      if (_output.isClassifier()) table.set(row, col++, st._logloss);
      if (_output.getModelCategory() == ModelCategory.Binomial) {
        table.set(row, col++, st._AUC);
        table.set(row, col++, st._lift);
      }
      if (_output.isClassifier()) table.set(row, col++, st._classError);

      if (_valid != null) {
        st = _output._scored_valid[i];
        table.set(row, col++, st._rmse);
        if (_output.getModelCategory() == ModelCategory.Regression) {
          table.set(row, col++, st._mae);
          table.set(row, col++, st._mean_residual_deviance);
        }
        if (_output.isClassifier()) table.set(row, col++, st._logloss);
        if (_output.getModelCategory() == ModelCategory.Binomial) {
          table.set(row, col++, st._AUC);
          table.set(row, col++, st._lift);
        }
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
    colHeaders.add("Number of Internal Trees"); colTypes.add("long"); colFormat.add("%d");
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
    table.set(row, col++, _output._ntrees);
    table.set(row, col++, _output._treeStats._num_trees); //internal number of trees (more for multinomial)
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
    long max_mem = H2O.SELF._heartbeat.get_free_mem();
    if (_parms._ntrees * avg_tree_mem_size > max_mem) {
      String msg = "The tree model will not fit in the driver node's memory ("
              + PrettyPrint.bytes((long)avg_tree_mem_size)
              + " per tree x " + _parms._ntrees + " > "
              + PrettyPrint.bytes(max_mem)
              + ") - try decreasing ntrees and/or max_depth or increasing min_rows!";
      error("_ntrees", msg);
    }
  }

  /**
   * Compute the inital value for a given distribution
   * @return initial value
   */
  protected double getInitialValue() {
    return new InitialValue(_parms).doAll(
            _response,
            hasWeightCol() ? _weights : _response.makeCon(1),
            hasOffsetCol() ? _offset : _response.makeCon(0)
    ).initialValue();
  }

  // Helper MRTask to compute the initial value
  private static class InitialValue extends MRTask<InitialValue> {
    public  InitialValue(Model.Parameters parms) { _dist = new Distribution(parms); }
    final private Distribution _dist;
    private double _num;
    private double _denom;

    public  double initialValue() {
      if (_dist.distribution == DistributionFamily.multinomial)
        return -0.5*new Distribution(DistributionFamily.bernoulli).link(_num/_denom);
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
        _denom += _dist.initFDenom(w,o,y);
      }
    }
    @Override public void reduce(InitialValue mrt) {
      _num += mrt._num;
      _denom += mrt._denom;
    }
  }

  @Override public void cv_computeAndSetOptimalParameters(ModelBuilder<M, P, O>[] cvModelBuilders) {
    if( _parms._stopping_rounds == 0 && _parms._max_runtime_secs == 0) return; // No exciting changes to stopping conditions
    // Extract stopping conditions from each CV model, and compute the best stopping answer
    _parms._stopping_rounds = 0;
    _parms._max_runtime_secs = 0;
    int sum = 0;
    for( int i=0; i<cvModelBuilders.length; ++i )
      sum += ((SharedTreeModel.SharedTreeOutput)DKV.<Model>getGet(cvModelBuilders[i].dest())._output)._ntrees;
    _parms._ntrees = (int)((double)sum/cvModelBuilders.length);
    warn("_ntrees", "Setting optimal _ntrees to " + _parms._ntrees + " for cross-validation main model based on early stopping of cross-validation models.");
    warn("_stopping_rounds", "Disabling convergence-based early stopping for cross-validation main model.");
    warn("_max_runtime_secs", "Disabling maximum allowed runtime for cross-validation main model.");
  }
}
