package hex.tree.gbm;

import hex.Distribution;
import hex.ModelCategory;
import hex.quantile.Quantile;
import hex.quantile.QuantileModel;
import hex.tree.*;
import hex.tree.DTree.DecidedNode;
import hex.tree.DTree.LeafNode;
import hex.tree.DTree.UndecidedNode;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.*;
import water.util.*;

import java.util.Arrays;
import java.util.Random;

/** Gradient Boosted Trees
 *
 *  Based on "Elements of Statistical Learning, Second Edition, page 387"
 */
public class GBM extends SharedTree<GBMModel,GBMModel.GBMParameters,GBMModel.GBMOutput> {
  @Override public ModelCategory[] can_build() {
    return new ModelCategory[]{
      ModelCategory.Regression,
      ModelCategory.Binomial,
      ModelCategory.Multinomial,
    };
  }

  // Called from an http request
  public GBM( GBMModel.GBMParameters parms                   ) { super(parms     ); init(false); }
  public GBM( GBMModel.GBMParameters parms, Key<GBMModel> key) { super(parms, key); init(false); }
  public GBM(boolean startup_once) { super(new GBMModel.GBMParameters(),startup_once); }

  @Override protected int nModelsInParallel() {
    if (!_parms._parallelize_cross_validation || _parms._max_runtime_secs != 0) return 1; //user demands serial building (or we need to honor the time constraints for all CV models equally)
    if (_train.byteSize() < 1e6) return _parms._nfolds; //for small data, parallelize over CV models
    return 2; //GBM always has some serial work, so it's fine to build two models at once
  }

  /** Start the GBM training Job on an F/J thread. */
  @Override protected GBMDriver trainModelImpl() {
    return new GBMDriver();
  }

  /** Initialize the ModelBuilder, validating all arguments and preparing the
   *  training frame.  This call is expected to be overridden in the subclasses
   *  and each subclass will start with "super.init();".  This call is made
   *  by the front-end whenever the GUI is clicked, and needs to be fast;
   *  heavy-weight prep needs to wait for the trainModel() call.
   *
   *  Validate the learning rate and distribution family. */
  @Override public void init(boolean expensive) {
    super.init(expensive);

    // Initialize response based on given distribution family.
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
    if (expensive) {
      if (error_count() > 0)
        throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(GBM.this);
      if (_parms._distribution == Distribution.Family.AUTO) {
        if (_nclass == 1) _parms._distribution = Distribution.Family.gaussian;
        if (_nclass == 2) _parms._distribution = Distribution.Family.bernoulli;
        if (_nclass >= 3) _parms._distribution = Distribution.Family.multinomial;
      }
      checkDistributions();
      if (hasOffsetCol() && isClassifier() && _parms._distribution == Distribution.Family.multinomial) {
        error("_offset_column", "Offset is not supported for multinomial distribution.");
      }
      if (hasOffsetCol() && _parms._distribution == Distribution.Family.bernoulli) {
        if (_offset.max() > 1)
          error("_offset_column", "Offset cannot be larger than 1 for Bernoulli distribution.");
      }
    }

    switch( _parms._distribution) {
    case bernoulli:
      if( _nclass != 2 /*&& !couldBeBool(_response)*/)
        error("_distribution", H2O.technote(2, "Binomial requires the response to be a 2-class categorical"));
      break;
    case multinomial:
      if (!isClassifier()) error("_distribution", H2O.technote(2, "Multinomial requires an categorical response."));
      break;
    case poisson:
      if (isClassifier()) error("_distribution", H2O.technote(2, "Poisson requires the response to be numeric."));
      break;
    case gamma:
      if (isClassifier()) error("_distribution", H2O.technote(2, "Gamma requires the response to be numeric."));
      break;
    case tweedie:
      if (isClassifier()) error("_distribution", H2O.technote(2, "Tweedie requires the response to be numeric."));
      break;
    case gaussian:
      if (isClassifier()) error("_distribution", H2O.technote(2, "Gaussian requires the response to be numeric."));
      break;
    case laplace:
      if (isClassifier()) error("_distribution", H2O.technote(2, "Laplace requires the response to be numeric."));
      break;
    case quantile:
      if (isClassifier()) error("_distribution", H2O.technote(2, "Quantile requires the response to be numeric."));
      break;
    case AUTO:
      break;
    default:
      error("_distribution","Invalid distribution: " + _parms._distribution);
    }

    if( !(0. < _parms._learn_rate && _parms._learn_rate <= 1.0) )
      error("_learn_rate", "learn_rate must be between 0 and 1");
    if( !(0. < _parms._col_sample_rate && _parms._col_sample_rate <= 1.0) )
      error("_col_sample_rate", "col_sample_rate must be between 0 and 1");
  }

  // ----------------------
  private class GBMDriver extends Driver {
    @Override protected boolean doOOBScoring() { return false; }
    @Override protected void initializeModelSpecifics() {
      _mtry_per_tree = Math.max(1, (int)(_parms._col_sample_rate_per_tree * _ncols)); //per-tree
      if (!(1 <= _mtry_per_tree && _mtry_per_tree <= _ncols)) throw new IllegalArgumentException("Computed mtry_per_tree should be in interval <1,"+_ncols+"> but it is " + _mtry_per_tree);
      _mtry = Math.max(1, (int)(_parms._col_sample_rate * _parms._col_sample_rate_per_tree * _ncols)); //per-split
      if (!(1 <= _mtry && _mtry <= _ncols)) throw new IllegalArgumentException("Computed mtry should be in interval <1,"+_ncols+"> but it is " + _mtry);

      // for Bernoulli, we compute the initial value with Newton-Raphson iteration, otherwise it might be NaN here
      _initialPrediction = _nclass > 2 || _parms._distribution == Distribution.Family.laplace || _parms._distribution == Distribution.Family.quantile ? 0 : getInitialValue();
      if (_parms._distribution == Distribution.Family.bernoulli) {
        if (hasOffsetCol()) _initialPrediction = getInitialValueBernoulliOffset(_train);
      } else if (_parms._distribution == Distribution.Family.laplace) {
        _initialPrediction = getInitialValueQuantile(0.5);
      } else if (_parms._distribution == Distribution.Family.quantile) {
        _initialPrediction = getInitialValueQuantile(_parms._quantile_alpha);
      }
      _model._output._init_f = _initialPrediction; //always write the initial value here (not just for Bernoulli)

      // Set the initial prediction into the tree column 0
      if( _initialPrediction != 0.0 ) {
        final double init = _initialPrediction;
        new MRTask() {
          @Override
          public void map(Chunk tree) {
            for (int i = 0; i < tree._len; i++) tree.set(i, init);
          }
        }.doAll(vec_tree(_train, 0), _parms._build_tree_one_node); // Only setting tree-column 0
      }
    }

    /**
     * Helper to compute the initial value for Laplace (incl. optional offset and obs weights)
     * @return weighted median of response - offset
     */
    private double getInitialValueQuantile(double quantile) {
      // obtain y - o
      Vec y = hasOffsetCol() ? new MRTask() {
        @Override public void map(Chunk[] chks, NewChunk[] nc) {
          final Chunk resp = chk_resp(chks);
          final Chunk offset = chk_offset(chks);
          for (int i=0; i<chks[0]._len; ++i)
            nc[0].addNum(resp.atd(i) - offset.atd(i)); //y - o
        }
      }.doAll(1, (byte)3 /*numeric*/, _train).outputFrame().anyVec() : response();

      // Now compute (weighted) quantile of y - o
      double res = Double.NaN;
      QuantileModel qm = null;
      Frame tempFrame = null;
      try {
        tempFrame = new Frame(Key.make(H2O.SELF), new String[]{"y"}, new Vec[]{y});
        if (hasWeightCol()) tempFrame.add("w", _weights);
        DKV.put(tempFrame);
        QuantileModel.QuantileParameters parms = new QuantileModel.QuantileParameters();
        parms._train = tempFrame._key;
        parms._probs = new double[]{quantile};
        parms._weights_column = hasWeightCol() ? "w" : null;
        Job<QuantileModel> job1 = new Quantile(parms).trainModel();
        qm = job1.get();
        res = qm._output._quantiles[0][0];
      } finally {
        if (qm!=null) qm.remove();
        if (tempFrame!=null) DKV.remove(tempFrame._key);
      }
      return res;
    }

    /**
     * Helper to compute the initial value for Bernoulli for offset != 0
     * @return
     */
    private double getInitialValueBernoulliOffset(Frame train) {
      Log.info("Running Newton-Raphson iteration to find the initial value since offsets are specified.");
      double delta;
      int count = 0;
      double tol = 1e-4;

      //From R GBM vignette:
      //For speed, gbm() does only one step of the Newton-Raphson algorithm
      //rather than iterating to convergence. No appreciable loss of accuracy
      //since the next boosting iteration will simply correct for the prior iterations
      //inadequacy.
      int N = 1; //one step is enough - same as R

      double init = 0; //start with initial value of 0 for convergence
      do {
        double newInit = new NewtonRaphson(init).doAll(train).value();
        delta = Math.abs(init - newInit);
        init = newInit;
        Log.info("Iteration " + ++count + ": initial value: " + init);
      } while (count < N && delta >= tol);
      if (delta > tol) Log.warn("Not fully converged.");
      Log.info("Newton-Raphson iteration ran for " + count + " iteration(s). Final residual: " + delta);
      return init;
    }

    /**
     * Newton-Raphson fixpoint iteration to find a self-consistent initial value
     */
    private class NewtonRaphson extends MRTask<NewtonRaphson> {
      double _init;
      double _num;
      double _denom;
      public double value() {
        return _init + _num/_denom;
      }
      NewtonRaphson(double init) { _init = init; }
      @Override public void map( Chunk chks[] ) {
        Chunk ys = chk_resp(chks);
        Chunk offset = chk_offset(chks);
        Chunk weight = hasWeightCol() ? chk_weight(chks) : new C0DChunk(1, chks[0]._len);
        Distribution dist = new Distribution(_parms);
        for( int row = 0; row < ys._len; row++) {
          double w = weight.atd(row);
          if (w == 0) continue;
          if (ys.isNA(row)) continue;
          double y = ys.atd(row);
          double o = offset.atd(row);
          double p = dist.linkInv(o + _init);
          _num += w*(y-p);
          _denom += w*p*(1.-p);
        }
      }

      @Override
      public void reduce(NewtonRaphson mrt) {
        _num += mrt._num;
        _denom += mrt._denom;
      }
    }

    // --------------------------------------------------------------------------
    // Compute Residuals
    // Do this for all rows, whether OOB or not
    class ComputePredAndRes extends MRTask<ComputePredAndRes> {
      @Override public void map( Chunk chks[] ) {
        Chunk ys = chk_resp(chks);
        Chunk offset = hasOffsetCol() ? chk_offset(chks) : new C0DChunk(0, chks[0]._len);
        Chunk preds = chk_tree(chks, 0); // Prior tree sums
        Chunk wk = chk_work(chks, 0); // Place to store residuals
        double fs[] = _nclass > 1 ? new double[_nclass+1] : null;
        Distribution dist = new Distribution(_parms);
        for( int row = 0; row < wk._len; row++) {
          if( ys.isNA(row) ) continue;
          double f = preds.atd(row) + offset.atd(row);
          double y = ys.atd(row);
          if( _parms._distribution == Distribution.Family.multinomial ) {
            double weight = hasWeightCol() ? chk_weight(chks).atd(row) : 1;
            double sum = score1(chks, weight,0.0 /*offset not used for multiclass*/,fs,row);
            if( Double.isInfinite(sum) ) { // Overflow (happens for constant responses)
              for (int k = 0; k < _nclass; k++) {
                wk = chk_work(chks, k);
                wk.set(row, ((int)y == k ? 1f : 0f) - (Double.isInfinite(fs[k + 1]) ? 1.0f : 0.0f));
              }
            } else {
              for( int k=0; k<_nclass; k++ ) { // Save as a probability distribution
                if( _model._output._distribution[k] != 0 ) {
                  wk = chk_work(chks, k);
                  wk.set(row, ((int)y == k ? 1f : 0f) - (float)(fs[k + 1] / sum));
                }
              }
            }
          } else {
            wk.set(row, (float) dist.gradient(y, f));
          }
        }
      }
    }

    class ComputeMinMax extends MRTask<ComputeMinMax> {
      public ComputeMinMax(int start, int end) {
        _start = start;
        _end = end;
      }
      int _start;
      int _end;
      float[] _mins;
      float[] _maxs;
      @Override public void map( Chunk chks[] ) {
        int _len = _end-_start;
        _mins = new float[_len];
        _maxs = new float[_len];
        Arrays.fill(_mins, Float.MAX_VALUE);
        Arrays.fill(_maxs, -Float.MAX_VALUE);

        Chunk ys = chk_resp(chks);
        Chunk offset = hasOffsetCol() ? chk_offset(chks) : new C0DChunk(0, chks[0]._len);
        Chunk preds = chk_tree(chks, 0); // Prior tree sums
        Chunk nids = chk_nids(chks, 0);
        for( int row = 0; row < preds._len; row++) {
          if( ys.isNA(row) ) continue;
          float f = (float)(preds.atd(row) + offset.atd(row));
          int nidx = (int)nids.at8(row);
          _mins[nidx-_start] = Math.min(_mins[nidx-_start], f);
          _maxs[nidx-_start] = Math.max(_maxs[nidx-_start], f);
        }
      }

      @Override
      public void reduce(ComputeMinMax mrt) {
        ArrayUtils.reduceMin(_mins, mrt._mins);
        ArrayUtils.reduceMax(_maxs, mrt._maxs);
      }
    }

    final static private double MIN_LOG_TRUNC = -19;
    final static private double MAX_LOG_TRUNC = 19;

    private void truncatePreds(DTree[] ktrees, int[] leafs, Distribution.Family dist, ComputeMinMax minMax) {
//        Log.info("Number of leaf nodes: " + minValues.length);
//        Log.info("Min: " + java.util.Arrays.toString(minValues));
//        Log.info("Max: " + java.util.Arrays.toString(maxValues));
      assert(_nclass == 1);
      final DTree tree = ktrees[0];
      assert (tree != null);
      //loop over leaf nodes only
      for (int i = 0; i < tree._len - leafs[0]; i++) {
        final LeafNode node = ((LeafNode) tree.node(leafs[0] + i));
        int nidx = node.nid();
        float nodeMin = minMax._mins[nidx-leafs[0]];
        float nodeMax = minMax._maxs[nidx-leafs[0]];
//        Log.info("Node: " + nidx + " min/max: " + nodeMin + "/" + nodeMax);

        // https://github.com/cran/gbm/blob/master/src/poisson.cpp
        // https://github.com/harrysouthworth/gbm/blob/master/src/poisson.cpp
        // https://github.com/gbm-developers/gbm/blob/master/src/poisson.cpp

        // https://github.com/harrysouthworth/gbm/blob/master/src/gamma.cpp
        // https://github.com/gbm-developers/gbm/blob/master/src/gamma.cpp

        // https://github.com/harrysouthworth/gbm/blob/master/src/tweedie.cpp
        // https://github.com/gbm-developers/gbm/blob/master/src/tweedie.cpp
        double val = node._pred;
        if (dist == Distribution.Family.gamma || dist == Distribution.Family.tweedie) //only for gamma/tweedie
          val += nodeMax;
        if (val > MAX_LOG_TRUNC) {
//          Log.warn("Truncating large positive leaf prediction (log): " + node._pred + " to " + (MAX_LOG_TRUNC - nodeMax));
          node._pred = (float) (MAX_LOG_TRUNC - nodeMax);
        }
        val = node._pred;
        if (dist == Distribution.Family.gamma || dist == Distribution.Family.tweedie) //only for gamma/tweedie
          val += nodeMin;
        if (val < MIN_LOG_TRUNC) {
//          Log.warn("Truncating large negative leaf prediction (log): " + node._pred + " to " + (MIN_LOG_TRUNC - nodeMin));
          node._pred = (float) (MIN_LOG_TRUNC - nodeMin);
        }
        if (node._pred < MIN_LOG_TRUNC && node._pred > MAX_LOG_TRUNC) {
          Log.warn("Terminal node prediction outside of allowed interval in log-space: "
                  + node._pred + " (should be in " + MIN_LOG_TRUNC + "..." + MAX_LOG_TRUNC + ").");
        }
      }
    }

    // --------------------------------------------------------------------------
    // Build the next k-trees, which is trying to correct the residual error from
    // the prior trees.
    @Override protected void buildNextKTrees() {
      // We're going to build K (nclass) trees - each focused on correcting
      // errors for a single class.
      final DTree[] ktrees = new DTree[_nclass];

      // Define a "working set" of leaf splits, from here to tree._len
      int[] leafs = new int[_nclass];

      // Compute predictions and resulting residuals for trees built so far
      // ESL2, page 387, Steps 2a, 2b
      new ComputePredAndRes().doAll(_train, _parms._build_tree_one_node); //fills "Work" columns for all rows (incl. OOB)

      // ----
      // ESL2, page 387.  Step 2b ii.
      // One Big Loop till the ktrees are of proper depth.
      // Adds a layer to the trees each pass.
      growTrees(ktrees, leafs, _rand); //assign to OOB and split using non-OOB only

      // ----
      // ESL2, page 387.  Step 2b iii.  Compute the gammas (leaf node predictions === fit best constant), and store them back
      // into the tree leaves.  Includes learn_rate.
      GammaPass gp = new GammaPass(ktrees, leafs, _parms._distribution).doAll(_train);
      if (_parms._distribution == Distribution.Family.laplace) {
        fitBestConstantsQuantile(ktrees, 0.5); //special case for Laplace: compute the median for each leaf node and store that as prediction
      } else if (_parms._distribution == Distribution.Family.quantile) {
        fitBestConstantsQuantile(ktrees, _parms._quantile_alpha); //compute the alpha-quantile for each leaf node and store that as prediction
      } else {
        fitBestConstants(ktrees, leafs, gp);
      }

      // Apply a correction for strong mispredictions (otherwise deviance can explode)
      if (_parms._distribution == Distribution.Family.gamma ||
              _parms._distribution == Distribution.Family.poisson ||
              _parms._distribution == Distribution.Family.tweedie) {
        truncatePreds(ktrees, leafs, _parms._distribution, new ComputeMinMax(leafs[0],ktrees[0].len()).doAll(_train));
      }

      // ----
      // ESL2, page 387.  Step 2b iv.  Cache the sum of all the trees, plus the
      // new tree, in the 'tree' columns.  Also, zap the NIDs for next pass.
      // Tree <== f(Tree)
      // Nids <== 0
      new AddTreeContributions(ktrees).doAll(_train);

      // Grow the model by K-trees
      _model._output.addKTrees(ktrees);
    }

    private void growTrees(DTree[] ktrees, int[] leafs, Random rand) {
      // Initial set of histograms.  All trees; one leaf per tree (the root
      // leaf); all columns
      DHistogram hcs[][][] = new DHistogram[_nclass][1/*just root leaf*/][_ncols];

      // Adjust real bins for the top-levels
      int adj_nbins = Math.max(_parms._nbins_top_level,_parms._nbins);

      long rseed = rand.nextLong();
      // initialize trees
      for (int k = 0; k < _nclass; k++) {
        // Initially setup as-if an empty-split had just happened
        if (_model._output._distribution[k] != 0) {
          if (k == 1 && _nclass == 2) continue; // Boolean Optimization (only one tree needed for 2-class problems)
          ktrees[k] = new DTree(_train, _ncols, (char)_parms._nbins, (char)_parms._nbins_cats, (char)_nclass, _parms._min_rows, _mtry, _mtry_per_tree, rseed, _parms._min_split_improvement);
          new UndecidedNode(ktrees[k], -1, DHistogram.initialHist(_train, _ncols, adj_nbins, _parms._nbins_cats, _parms._min_split_improvement, hcs[k][0])); // The "root" node
        }
      }

      // Sample - mark the lines by putting 'OUT_OF_BAG' into nid(<klass>) vector
      if (_parms._sample_rate < 1 || _parms._sample_rate_per_class != null) {
        Sample ss[] = new Sample[_nclass];
        for (int k = 0; k < _nclass; k++)
          if (ktrees[k] != null)
            ss[k] = new Sample(ktrees[k], _parms._sample_rate, _parms._sample_rate_per_class).dfork(null, new Frame(vec_nids(_train, k), vec_resp(_train)), _parms._build_tree_one_node);
        for (int k = 0; k < _nclass; k++)
          if (ss[k] != null) ss[k].getResult();
      }

      // ----
      // ESL2, page 387.  Step 2b ii.
      // One Big Loop till the ktrees are of proper depth.
      // Adds a layer to the trees each pass.
      int depth = 0;
      for (; depth < _parms._max_depth; depth++) {
        hcs = buildLayer(_train, _parms._nbins, _parms._nbins_cats, ktrees, leafs, hcs, _mtry < _model._output.nfeatures(), _parms._build_tree_one_node);
        // If we did not make any new splits, then the tree is split-to-death
        if (hcs == null) break;
      }

      // Each tree bottomed-out in a DecidedNode; go 1 more level and insert
      // LeafNodes to hold predictions.
      for (int k = 0; k < _nclass; k++) {
        DTree tree = ktrees[k];
        if (tree == null) continue;
        int leaf = leafs[k] = tree.len();
        for (int nid = 0; nid < leaf; nid++) {
          if (tree.node(nid) instanceof DecidedNode) {
            DecidedNode dn = tree.decided(nid);
            if (dn._split._col == -1) { // No decision here, no row should have this NID now
              if (nid == 0)               // Handle the trivial non-splitting tree
                new LeafNode(tree, -1, 0);
              continue;
            }
            for (int i = 0; i < dn._nids.length; i++) {
              int cnid = dn._nids[i];
              if (cnid == -1 || // Bottomed out (predictors or responses known constant)
                      tree.node(cnid) instanceof UndecidedNode || // Or chopped off for depth
                      (tree.node(cnid) instanceof DecidedNode &&  // Or not possible to split
                              ((DecidedNode) tree.node(cnid))._split.col() == -1))
                dn._nids[i] = new LeafNode(tree, nid).nid(); // Mark a leaf here
            }
          }
        }
      } // -- k-trees are done
    }

    private void fitBestConstantsQuantile(DTree[] ktrees, double quantile) {
      assert(_nclass==1);
      Vec response = new MRTask() {
        @Override
        public void map(Chunk[] chks, NewChunk[] nc) {
          final Chunk resp = chk_resp(chks);
          final Chunk offset = hasOffsetCol() ? chk_offset(chks) : new C0DChunk(0, chks[0]._len); // Residuals for this tree/class
          final Chunk preds = chk_tree(chks,0);
          for (int i=0; i<chks[0].len(); ++i)
            nc[0].addNum(resp.atd(i) - preds.atd(i) - offset.atd(i)); //y - (f+o)
        }
      }.doAll(1, (byte)3 /*numeric*/, _train).outputFrame().anyVec();
      Vec weights = hasWeightCol() ? _train.vecs()[idx_weight()] : null;
      Vec strata = _train.vecs()[idx_nids(0)];

      // compute quantile for all leaf nodes
      Quantile.StratifiedQuantilesTask sqt = new Quantile.StratifiedQuantilesTask(null, quantile, response, weights, strata, QuantileModel.CombineMethod.INTERPOLATE);
      H2O.submitTask(sqt);
      sqt.join();

      for (int i = 0; i < sqt._quantiles.length; i++) {
        float val = (float) (_parms._learn_rate * sqt._quantiles[i]);
        assert !Float.isNaN(val) && !Float.isInfinite(val);
        ((LeafNode) ktrees[0].node(Math.max(0,(int)strata.min()) + i))._pred = val;
//        Log.info("Leaf " + ((int)strata.min()+i) + " has quantile: " + sqt._quantiles[i]);
      }
    }

    private void fitBestConstants(DTree[] ktrees, int[] leafs, GammaPass gp) {
      double m1class = _nclass > 1 && _parms._distribution != Distribution.Family.bernoulli ? (double) (_nclass - 1) / _nclass : 1.0; // K-1/K for multinomial
      for (int k = 0; k < _nclass; k++) {
        final DTree tree = ktrees[k];
        if (tree == null) continue;
        for (int i = 0; i < tree._len - leafs[k]; i++) {
          float gf = (float) (_parms._learn_rate * m1class * gp.gamma(k, i));
          // In the multinomial case, check for very large values (which will get exponentiated later)
          // Note that gss can be *zero* while rss is non-zero - happens when some rows in the same
          // split are perfectly predicted true, and others perfectly predicted false.
          if (_parms._distribution == Distribution.Family.multinomial) {
            if (gf > 1e4) gf = 1e4f; // Cap prediction, will already overflow during Math.exp(gf)
            else if (gf < -1e4) gf = -1e4f;
          }
          assert !Float.isNaN(gf) && !Float.isInfinite(gf);
          ((LeafNode) tree.node(leafs[k] + i))._pred = gf;
        }
      }
    }

    // Set terminal node estimates (gamma)
    // ESL2, page 387.  Step 2b iii.
    // Nids <== f(Nids)
    // For classification (bernoulli):
    //    gamma_i = sum (w_i * res_i) / sum (w_i*p_i*(1 - p_i)) where p_i = y_i - res_i
    // For classification (multinomial):
    //    gamma_i_k = (nclass-1)/nclass * (sum res_i / sum (|res_i|*(1-|res_i|)))
    // For regression (gaussian):
    //    gamma_i = sum res_i / count(res_i)
    private class GammaPass extends MRTask<GammaPass> {
      final DTree _trees[]; // Read-only, shared (except at the histograms in the Nodes)
      final int   _leafs[]; // Number of active leaves (per tree)
      final Distribution.Family _dist;
      private double _num[/*tree/klass*/][/*tree-relative node-id*/];
      private double _denom[/*tree/klass*/][/*tree-relative node-id*/];

      double gamma(int tree, int nid) {
        if (_denom[tree][nid] == 0) return 0;
        double g = _num[tree][nid]/ _denom[tree][nid];
        assert(!Double.isInfinite(g)) : "numeric overflow";
        assert(!Double.isNaN(g)) : "numeric overflow";
        if (_dist == Distribution.Family.poisson
                || _dist == Distribution.Family.gamma
                || _dist == Distribution.Family.tweedie)
        {
          return new Distribution(_parms).link(g);
        } else {
          return g;
        }
      }

      GammaPass(DTree trees[],
                int leafs[],
                Distribution.Family distribution
      ) {
        _leafs=leafs;
        _trees=trees;
        _dist = distribution;
      }
      @Override public void map( Chunk[] chks ) {
        _denom = new double[_nclass][];
        _num = new double[_nclass][];
        final Chunk resp = chk_resp(chks); // Response for this frame

        // For all tree/klasses
        for( int k=0; k<_nclass; k++ ) {
          final DTree tree = _trees[k];
          final int   leaf = _leafs[k];
          if( tree == null ) continue; // Empty class is ignored

          // A leaf-biased array of all active Tree leaves.
          final double denom[] = _denom[k] = new double[tree._len-leaf];
          final double num[] = _num[k] = new double[tree._len-leaf];
          final Chunk nids = chk_nids(chks, k); // Node-ids  for this tree/class
          final Chunk ress = chk_work(chks, k); // Residuals for this tree/class
          final Chunk offset = hasOffsetCol() ? chk_offset(chks) : new C0DChunk(0, chks[0]._len); // Residuals for this tree/class
          final Chunk preds = chk_tree(chks,k);

          // If we have all constant responses, then we do not split even the
          // root and the residuals should be zero.
          if( tree.root() instanceof LeafNode ) continue;
          Distribution dist = new Distribution(_parms);
          for( int row=0; row<nids._len; row++ ) { // For all rows
            int nid = (int)nids.at8(row);          // Get Node to decide from

            final boolean wasOOBRow = ScoreBuildHistogram.isOOBRow((int)chk_nids(chks,k).at8(row)); //same for all k
            if (wasOOBRow) nid = ScoreBuildHistogram.oob2Nid(nid);

            if( nid < 0 ) continue;                // Missing response
            if( tree.node(nid) instanceof UndecidedNode ) // If we bottomed out the tree
              nid = tree.node(nid).pid();                  // Then take parent's decision
            DecidedNode dn = tree.decided(nid);           // Must have a decision point
            if( dn._split._col == -1 )                    // Unable to decide?
              dn = tree.decided(dn.pid());  // Then take parent's decision
            int leafnid = dn.ns(chks,row); // Decide down to a leafnode
            assert leaf <= leafnid && leafnid < tree._len :
                    "leaf: " + leaf + " leafnid: " + leafnid + " tree._len: " + tree._len + "\ndn: " + dn;
            assert tree.node(leafnid) instanceof LeafNode;
            // Note: I can tell which leaf/region I end up in, but I do not care for
            // the prediction presented by the tree.  For GBM, we compute the
            // sum-of-residuals (and sum/abs/mult residuals) for all rows in the
            // leaf, and get our prediction from that.
            nids.set(row, leafnid);
            assert !ress.isNA(row);

            // OOB rows get placed properly (above), but they don't affect the computed Gamma (below)
            // For Laplace/Quantile distribution, we need to compute the median of (y-offset-preds == y-f), will be done outside of here
            if (wasOOBRow || _parms._distribution == Distribution.Family.laplace || _parms._distribution == Distribution.Family.quantile) continue;

            // Compute numerator and denominator of terminal node estimate (gamma)
            double w = hasWeightCol() ? chk_weight(chks).atd(row) : 1; //weight
            double y = resp.atd(row); //response
            double z = ress.atd(row); //residual
            double f = preds.atd(row) + offset.atd(row);
            int idx=leafnid-leaf;
            num[idx] += dist.gammaNum(w, y, z, f);
            denom[idx] += dist.gammaDenom(w, y, z, f);
          }
        }
      }
      @Override public void reduce( GammaPass gp ) {
        ArrayUtils.add(_denom,gp._denom);
        ArrayUtils.add(_num,gp._num);
      }
    }

    private class AddTreeContributions extends MRTask<AddTreeContributions> {
      DTree[] _ktrees;
      AddTreeContributions(DTree[] ktrees) { _ktrees = ktrees; }
      @Override public void map( Chunk chks[] ) {
        // For all tree/klasses
        for( int k=0; k<_nclass; k++ ) {
          final DTree tree = _ktrees[k];
          if( tree == null ) continue;
          final Chunk nids = chk_nids(chks,k);
          final Chunk ct   = chk_tree(chks, k);
          for( int row=0; row<nids._len; row++ ) {
            int nid = (int)nids.at8(row);
            if( nid < 0 ) continue;
            // Prediction stored in Leaf is cut to float to be deterministic in reconstructing
            // <tree_klazz> fields from tree prediction
            ct.set(row, (float)(ct.atd(row) + ((LeafNode)tree.node(nid))._pred));
            nids.set(row, 0);
          }
        }
      }
    }

    @Override protected GBMModel makeModel( Key modelKey, GBMModel.GBMParameters parms) {
      return new GBMModel(modelKey,parms,new GBMModel.GBMOutput(GBM.this));
    }

  }

  // Read the 'tree' columns, do model-specific math and put the results in the
  // fs[] array, and return the sum.  Dividing any fs[] element by the sum
  // turns the results into a probability distribution.
  @Override protected double score1( Chunk chks[], double weight, double offset, double fs[/*nclass*/], int row ) {
    double f = chk_tree(chks,0).atd(row) + offset;
    double p = new Distribution(_parms).linkInv(f);
    if( _parms._distribution == Distribution.Family.bernoulli ) {
      fs[2] = p;
      fs[1] = 1.0-p;
      return 1;                 // f2 = 1.0 - f1; so f1+f2 = 1.0
    } else if (_parms._distribution == Distribution.Family.multinomial) {
      if (_nclass == 2) {
        // This optimization assumes the 2nd tree of a 2-class system is the
        // inverse of the first.  Fill in the missing tree
        fs[1] = p;
        fs[2] = 1 / p;
        return fs[1] + fs[2];
      }
      // Multinomial loss function; sum(exp(data)).  Load tree data
      fs[0+1] = f;
      for( int k=1; k<_nclass; k++ )
        fs[k+1]=chk_tree(chks,k).atd(row);
      // Rescale to avoid Infinities; return sum(exp(data))
      return hex.genmodel.GenModel.log_rescale(fs);
    }
    else {
      return fs[0] = p;
    }
  }
}
