package hex.tree;

import hex.*;
import jsr166y.CountedCompleter;
import water.*;
import water.H2O.H2OCountedCompleter;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.*;

import java.util.Arrays;

public abstract class SharedTree<M extends SharedTreeModel<M,P,O>, P extends SharedTreeModel.SharedTreeParameters, O extends SharedTreeModel.SharedTreeOutput> extends SupervisedModelBuilder<M,P,O> {
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

    if (_response == null) return;

    // Initialize response based on given loss function.
    // Regression: initially predict the response mean
    // Binomial: just class 0 (class 1 in the exact inverse prediction)
    // Multinomial: Class distribution which is not a single value.

    // However there is this weird tension on the initial value for
    // classification: If you guess 0's (no class is favored over another),
    // then with your first GBM tree you'll typically move towards the correct
    // answer a little bit (assuming you have decent predictors) - and
    // immediately the Confusion Matrix shows good results which gradually
    // improve... BUT the Means Squared Error will suck for unbalanced sets,
    // even as the CM is good.  That's because we want the predictions for the
    // common class to be large and positive, and the rare class to be negative
    // and instead they start around 0.  Guessing initial zero's means the MSE
    // is so bad, that the R^2 metric is typically negative (usually it's
    // between 0 and 1).

    // If instead you guess the mean (reversed through the loss function), then
    // the zero-tree GBM model reports an MSE equal to the response variance -
    // and an initial R^2 of zero.  More trees gradually improves the R^2 as
    // expected.  However, all the minority classes have large guesses in the
    // wrong direction, and it takes a long time (lotsa trees) to correct that
    // - so your CM sucks for a long time.
    double mean = _response.mean();
    _initialPrediction = _nclass == 1 ? mean
      : (_nclass==2 ? -0.5*Math.log(mean/(1.0-mean))/*0.0*/ : 0.0/*not a single value*/);
  }

  // --------------------------------------------------------------------------
  // Top-level tree-algo driver
  abstract protected class Driver extends H2OCountedCompleter<Driver> {

    // Top-level tree-algo driver function
    @Override protected void compute2() {
      _model = null;            // Resulting model!
      try {
        Scope.enter();          // Cleanup temp keys
        _parms.read_lock_frames(SharedTree.this); // Fetch & read-lock input frames
        init(true);             // Do any expensive tests & conversions now
        if( error_count() > 0 ) throw new IllegalArgumentException("Found validation errors: "+validationErrors());

        // New Model?  Or continuing from a checkpoint?
        if( _parms._checkpoint && DKV.get(_parms._destination_key) != null ) {
          _model = DKV.get(_dest).get();
          _model.write_lock(_key); // do not delete previous model; we are extending it
        } else {                   // New Model
          // Compute the zero-tree error - guessing only the class distribution.
          // MSE is stddev squared when guessing for regression.
          // For classification, guess the largest class.
          _model = makeModel(_dest, _parms, 
                             initial_MSE(response(), response()), 
                             initial_MSE(response(),vresponse())); // Make a fresh model
          _model.delete_and_lock(_key);       // and clear & write-lock it (smashing any prior)
          _model._output._initialPrediction = _initialPrediction;
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
          if( _parms._balance_classes ) {
            float[] csf = _parms._class_sampling_factors;
            if( csf != null && csf.length != domain.length )
              throw new IllegalArgumentException("class_sampling_factors must have " + domain.length + " elements");
            Frame stratified = water.util.MRUtils.sampleFrameStratified(fr, fr.lastVec(), csf, (long)(_parms._max_after_balance_size*fr.numRows()), _parms._seed, true, false);
            if (stratified != fr) {
              throw H2O.unimpl();
              //_parms._train = stratified._key;
              //_response_key = _parms._response; // Reload from stratified data
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
            @Override public void map(Chunk tree) { for( int i=0; i<tree._len; i++ ) tree.set(i, init); }
          }.doAll(vec_tree(_train,0)); // Only setting tree-column 0

        // Variable importance: squared-error-improvement-per-variable-per-split
        _improvPerVar = new float[_ncols];

        // Sub-class tree-model-builder specific build code
        buildModel();
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
        if( _model != null ) _model.unlock(_key);
        _parms.read_unlock_frames(SharedTree.this);
        if( _model==null ) Scope.exit();
        else {
          Key[] mms = _model._output._model_metrics;
          Scope.exit(_model._key,mms.length==0 ? null : mms[mms.length-1]);
        }
      }
      tryComplete();
    }

    // Abstract classes implemented by the tree builders
    abstract protected M makeModel( Key modelKey, P parms, double mse_train, double mse_valid );
    abstract protected void buildModel();
  }

  // --------------------------------------------------------------------------
  // Build an entire layer of all K trees
  protected DHistogram[][][] buildLayer(final Frame fr, final int nbins, final DTree ktrees[], final int leafs[], final DHistogram hcs[][][], boolean subset, boolean build_tree_one_node) {
    // Build K trees, one per class.

    // Build up the next-generation tree splits from the current histograms.
    // Nearly all leaves will split one more level.  This loop nest is
    //           O( #active_splits * #bins * #ncols )
    // but is NOT over all the data.
    H2OCountedCompleter sb1ts[] = new H2OCountedCompleter[_nclass];
    Vec vecs[] = fr.vecs();
    for( int k=0; k<_nclass; k++ ) {
      final DTree tree = ktrees[k]; // Tree for class K
      if( tree == null ) continue;
      // Build a frame with just a single tree (& work & nid) columns, so the
      // nested MRTask ScoreBuildHistogram in ScoreBuildOneTree does not try
      // to close other tree's Vecs when run in parallel.
      Frame fr2 = new Frame(Arrays.copyOf(fr._names,_ncols+1), Arrays.copyOf(vecs,_ncols+1));
      fr2.add(fr._names[_ncols+1+k],vecs[_ncols+1+k]);
      fr2.add(fr._names[_ncols+1+_nclass+k],vecs[_ncols+1+_nclass+k]);
      fr2.add(fr._names[_ncols+1+_nclass+_nclass+k],vecs[_ncols+1+_nclass+_nclass+k]);
      // Start building one of the K trees in parallel
      H2O.submitTask(sb1ts[k] = new ScoreBuildOneTree(k,nbins,tree,leafs,hcs,fr2, subset, build_tree_one_node, _improvPerVar));
    }
    // Block for all K trees to complete.
    boolean did_split=false;
    for( int k=0; k<_nclass; k++ ) {
      final DTree tree = ktrees[k]; // Tree for class K
      if( tree == null ) continue;
      sb1ts[k].join();
      if( ((ScoreBuildOneTree)sb1ts[k])._did_split ) did_split=true;
    }
    // The layer is done.
    return did_split ? hcs : null;
  }

  private class ScoreBuildOneTree extends H2OCountedCompleter {
    final int _k;               // The tree
    final int _nbins;           // Number of histogram bins
    final DTree _tree;
    final int _leafs[/*nclass*/];
    final DHistogram _hcs[/*nclass*/][][];
    final Frame _fr2;
    final boolean _subset;      // True if working a subset of cols
    final boolean _build_tree_one_node;
    float[] _improvPerVar;      // Squared Error improvement per variable per split
    
    boolean _did_split;
    ScoreBuildOneTree( int k, int nbins, DTree tree, int leafs[], DHistogram hcs[][][], Frame fr2, boolean subset, boolean build_tree_one_node, float[] improvPerVar ) {
      _k    = k;
      _nbins= nbins;
      _tree = tree;
      _leafs= leafs;
      _hcs  = hcs;
      _fr2  = fr2;
      _subset = subset;
      _build_tree_one_node = build_tree_one_node;
      _improvPerVar = improvPerVar;
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
      new ScoreBuildHistogram(this,_k,_ncols, _nbins,_tree, _leafs[_k],_hcs[_k],_subset).dfork(0,_fr2,_build_tree_one_node);
    }
    @Override public void onCompletion(CountedCompleter caller) {
      ScoreBuildHistogram sbh = (ScoreBuildHistogram)caller;
      //System.out.println(sbh.profString());

      final int leafk = _leafs[_k];
      int tmax = _tree.len();   // Number of total splits in tree K
      for( int leaf=leafk; leaf<tmax; leaf++ ) { // Visit all the new splits (leaves)
        DTree.UndecidedNode udn = _tree.undecided(leaf);
        //System.out.println((_nclass==1?"Regression":("Class "+_fr2.vecs()[_ncols].domain()[_k]))+",\n  Undecided node:"+udn);
        // Replace the Undecided with the Split decision
        DTree.DecidedNode dn = makeDecided(udn,sbh._hcs[leaf-leafk]);
        //System.out.println("--> Decided node: " + dn +
        //                   "  > Split: " + dn._split + " L/R:" + dn._split.rowsLeft()+" + "+dn._split.rowsRight());
        if( dn._split.col() == -1 ) udn.do_not_split();
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
      if (new_leafs>0) _tree._depth++; // Next layer done but update tree depth only if new leaves are generated
    }
  }

  // --------------------------------------------------------------------------
  // Convenience accessor for a complex chunk layout.
  // Wish I could name the array elements nicer...
  protected int idx_resp(     ) { return _ncols; }
  protected int idx_oobt(     ) { return _ncols+1+_nclass+_nclass+_nclass; }
  protected int idx_tree(int c) { return _ncols+1+c; }

  protected Chunk chk_resp( Chunk chks[]        ) { return chks[idx_resp( )]; }
  protected Chunk chk_tree( Chunk chks[], int c ) { return chks[idx_tree(c)]; }
  protected Chunk chk_work( Chunk chks[], int c ) { return chks[_ncols+1+_nclass+c]; }
  protected Chunk chk_nids( Chunk chks[], int t ) { return chks[_ncols+1+_nclass+_nclass+t]; }

  protected final Vec vec_tree( Frame fr, int c) { return fr.vecs()[idx_tree(c)]; }

  // Builder-specific decision node
  abstract protected DTree.DecidedNode makeDecided( DTree.UndecidedNode udn, DHistogram hs[] );

  // Read the 'tree' columns, do model-specific math and put the results in the
  // fs[] array, and return the sum.  Dividing any fs[] element by the sum
  // turns the results into a probability distribution.
  abstract protected float score1( Chunk chks[], float fs[/*nclass*/], int row );

  // Call builder specific score code and then correct probabilities
  // if it is necessary.
  void score2(Chunk chks[], float fs[/*nclass*/], int row ) {
    float sum = score1(chks, fs, row);
    if( isClassifier() && !Float.isInfinite(sum) && sum>0f ) ArrayUtils.div(fs, sum);
    if( _model._output._priorClassDist!=null && _model._output._modelClassDist!=null )
      ModelUtils.correctProbabilities(fs, _model._output._priorClassDist, _model._output._modelClassDist);
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
      _model.update(_key);  updated = true;

      _timeLastScoreStart = now;
      Score sc = new Score(this,oob,_model._output.getModelCategory()).doAll(_parms._valid == null ? train() : valid(), build_tree_one_node);
      ModelMetricsSupervised mm = sc.makeModelMetrics(_model,_parms._valid==null ? _parms.train() : _parms.valid(), _parms._response_column);
      Log.info("============================================================== ");
      // Store score results in the model output
      SharedTreeModel.SharedTreeOutput out = _model._output;
      out._mse_train[out._ntrees] = _parms._valid == null ? mm._mse : Double.NaN;
      out._mse_valid[out._ntrees] = _parms._valid == null ? Double.NaN : mm._mse;
      if( out._ntrees > 0 )
        out._variable_importances = hex.ModelMetrics.calcVarImp(new hex.VarImp(_improvPerVar,out._names));
      Log.info("r2 is "+mm.r2()+", with "+_model._output._ntrees+"x"+_nclass+" trees (average of "+(_model._output._treeStats._mean_leaves)+" nodes)");
      ConfusionMatrix cm = (mm instanceof ModelMetricsBinomial) ? ((ModelMetricsBinomial)mm)._cm :
        ((mm instanceof ModelMetricsMultinomial) ? ((ModelMetricsMultinomial)mm)._cm : null);
      if( cm != null ) {
        Log.info(cm.toASCII());
        Log.info((_nclass > 1 ? "Total of " + cm.errCount() + " errors" : "Reported") + " on " + cm.totalRows() + " rows");
      }
      _timeLastScoreEnd = System.currentTimeMillis();
    }

    // Double update - after either scoring or variable importance
    if( updated ) _model.update(_key);
  }

  // helper for debugging
  @SuppressWarnings("unused")
  static protected void printGenerateTrees(DTree[] trees) {
    for( DTree dtree : trees )
      if( dtree != null )
        System.out.println(dtree.root().toString2(new StringBuilder(),0));
  }

  double initial_MSE( Vec train, Vec test ) {
    if( train.isEnum() ) {
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

}
