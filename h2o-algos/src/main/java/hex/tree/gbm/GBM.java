package hex.tree.gbm;

import hex.Distribution;
import hex.DistributionFactory;
import hex.ModelCategory;
import hex.genmodel.utils.DistributionFamily;
import hex.quantile.Quantile;
import hex.quantile.QuantileModel;
import hex.tree.*;
import hex.tree.DTree.DecidedNode;
import hex.tree.DTree.LeafNode;
import hex.tree.DTree.UndecidedNode;
import org.apache.log4j.Logger;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.*;
import water.util.ArrayUtils;
import water.util.MathUtils;
import water.util.RandomUtils;
import water.util.VecUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static hex.util.LinearAlgebraUtils.toEigenArray;

/** Gradient Boosted Trees
 *
 *  Based on "Elements of Statistical Learning, Second Edition, page 387"
 */
public class GBM extends SharedTree<GBMModel,GBMModel.GBMParameters,GBMModel.GBMOutput> {

  private static final Logger LOG = Logger.getLogger(GBM.class);
  
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

  @Override protected int nModelsInParallel(int folds) {
    return nModelsInParallel(folds, 2);
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
      if (_parms._distribution == DistributionFamily.AUTO) {
        if (_nclass == 1) _parms._distribution = DistributionFamily.gaussian;
        if (_nclass == 2) _parms._distribution = DistributionFamily.bernoulli;
        if (_nclass >= 3) _parms._distribution = DistributionFamily.multinomial;
      }
      checkDistributions();
      if (hasOffsetCol() && isClassifier() && (_parms._distribution == DistributionFamily.multinomial || _parms._distribution == DistributionFamily.custom)) {
        error("_offset_column", "Offset is not supported for "+_parms._distribution+" distribution.");
      }
      if (_parms._monotone_constraints != null && _parms._monotone_constraints.length > 0 && !supportMonotoneConstraints(_parms._distribution)) {
        error("_monotone_constraints", "Monotone constraints are only supported for Gaussian, Bernoulli, Tweedie and Quantile distributions, your distribution: " + _parms._distribution + ".");
      }

      if (_origTrain != null && _origTrain != _train) {
        List<Double> projections = new ArrayList<>();
        for (int i = 0; i < _origTrain.numCols(); i++) {
          Vec currentCol = _origTrain.vec(i);
          if (currentCol.isCategorical()) {
            double[] actProjection = toEigenArray(currentCol);
            for (double v : actProjection) {
              projections.add(v);
            }
          }
        }
        double[] primitive_projections = new double[projections.size()];
        for (int i = 0; i < projections.size(); i++) {
          primitive_projections[i] = projections.get(i);
        }
        _orig_projection_array = primitive_projections;
      }
    }

    switch( _parms._distribution) {
      case bernoulli:
        if( _nclass != 2 /*&& !couldBeBool(_response)*/)
          error("_distribution", H2O.technote(2, "Binomial requires the response to be a 2-class categorical"));
        break;
      case quasibinomial:
        if ( !_response.isNumeric() )
          error("_distribution", H2O.technote(2, "Quasibinomial requires the response to be numeric."));
        if ( _nclass != 2)
          error("_distribution", H2O.technote(2, "Quasibinomial requires the response to be binary."));
        break;
      case modified_huber:
        if( _nclass != 2 /*&& !couldBeBool(_response)*/)
          error("_distribution", H2O.technote(2, "Modified Huber requires the response to be a 2-class categorical."));
        break;
      case multinomial:
        if (!isClassifier()) error("_distribution", H2O.technote(2, "Multinomial requires an categorical response."));
        break;
      case huber:
        if (isClassifier()) error("_distribution", H2O.technote(2, "Huber requires the response to be numeric."));
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
      case custom:
        if(_parms._custom_distribution_func == null) error("_distribution", H2O.technote(2, "Custom requires custom function loaded."));
        break;
      case AUTO:
        break;
      default:
        error("_distribution","Invalid distribution: " + _parms._distribution);
    }

    if( !(0. < _parms._learn_rate && _parms._learn_rate <= 1.0) )
      error("_learn_rate", "learn_rate must be between 0 and 1");
    if( !(0. < _parms._learn_rate_annealing && _parms._learn_rate_annealing <= 1.0) )
      error("_learn_rate_annealing", "learn_rate_annealing must be between 0 and 1");
    if( !(0. < _parms._col_sample_rate && _parms._col_sample_rate <= 1.0) )
      error("_col_sample_rate", "col_sample_rate must be between 0 and 1");
    if (_parms._max_abs_leafnode_pred <= 0)
      error("_max_abs_leafnode_pred", "max_abs_leafnode_pred must be larger than 0.");
    if (_parms._pred_noise_bandwidth < 0)
      error("_pred_noise_bandwidth", "pred_noise_bandwidth must be >= 0.");

    if ((_train != null) && (_parms._monotone_constraints != null)) {
      TreeUtils.checkMonotoneConstraints(this, _train, _parms._monotone_constraints);
    }
  }
  
  private boolean supportMonotoneConstraints(DistributionFamily distributionFamily){
    switch (distributionFamily) {
      case gaussian:
      case bernoulli:
      case tweedie:
      case quantile:
        return true;
      default:
        return false;
    }
  }

  // ----------------------
  private class GBMDriver extends Driver {
    private transient FrameMap frameMap;

    @Override
    protected Frame makeValidWorkspace() {
      // FIXME: this is not efficient, we need a sparse volatile chunks
      Vec[] tmp = _valid.anyVec().makeVolatileDoubles(numClassTrees());
      String[] tmpNames = new String[tmp.length];
      for (int i = 0; i < tmpNames.length; i++)
        tmpNames[i] = "__P_" + i;
      return new Frame(tmpNames, tmp);
    }

    @Override protected boolean doOOBScoring() { return false; }
    @Override protected void initializeModelSpecifics() {
      frameMap = new FrameMap(GBM.this);
      _mtry_per_tree = Math.max(1, (int)(_parms._col_sample_rate_per_tree * _ncols)); //per-tree
      if (!(1 <= _mtry_per_tree && _mtry_per_tree <= _ncols)) throw new IllegalArgumentException("Computed mtry_per_tree should be in interval <1,"+_ncols+"> but it is " + _mtry_per_tree);
      _mtry = Math.max(1, (int)(_parms._col_sample_rate * _parms._col_sample_rate_per_tree * _ncols)); //per-split
      if (!(1 <= _mtry && _mtry <= _ncols)) throw new IllegalArgumentException("Computed mtry should be in interval <1,"+_ncols+"> but it is " + _mtry);

      // for Bernoulli, we compute the initial value with Newton-Raphson iteration, otherwise it might be NaN here
      DistributionFamily distr = _parms._distribution;
      _initialPrediction = _nclass > 2 || distr == DistributionFamily.laplace || distr == DistributionFamily.huber || distr == DistributionFamily.quantile ? 0 : getInitialValue();
      if(distr == DistributionFamily.quasibinomial){
        _model._output._quasibinomialDomains = new VecUtils.CollectDoubleDomain(null,2).doAll(_response).stringDomain(_response.isInt());
      }
      if (distr == DistributionFamily.bernoulli || distr == DistributionFamily.quasibinomial) {
        if (hasOffsetCol())
          _initialPrediction = getInitialValueBernoulliOffset(_train);
      } else if (distr == DistributionFamily.laplace || distr == DistributionFamily.huber) {
        _initialPrediction = getInitialValueQuantile(0.5);
      } else if (distr == DistributionFamily.quantile) {
        _initialPrediction = getInitialValueQuantile(_parms._quantile_alpha);
      }
      _model._output._init_f = _initialPrediction; //always write the initial value here (not just for Bernoulli)
      if (_model.evalAutoParamsEnabled) {
        _model.initActualParamValuesAfterOutputSetup(_nclass, isClassifier());
      }

      // Set the initial prediction into the tree column 0
      if (_initialPrediction != 0.0) {
        new FillVecWithConstant(_initialPrediction)
                .doAll(vec_tree(_train, 0), _parms._build_tree_one_node);  // Only setting tree-column 0
      }
    }


    /**
     * Helper to compute the initial value for Laplace/Huber/Quantile (incl. optional offset and obs weights)
     * @return weighted median of response - offset
     */
    private double getInitialValueQuantile(double quantile) {
      // obtain y - o
      Vec y = hasOffsetCol() 
              ? new ResponseLessOffsetTask(frameMap).doAll(1, Vec.T_NUM, _train).outputFrame().anyVec() 
              : response();

      // Now compute (weighted) quantile of y - o
      double res;
      QuantileModel qm = null;
      Frame tempFrame = null;
      try {
        tempFrame = new Frame(Key.<Frame>make(H2O.SELF), new String[]{"y"}, new Vec[]{y});
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
     */
    private double getInitialValueBernoulliOffset(Frame train) {
      LOG.info("Running Newton-Raphson iteration to find the initial value since offsets are specified.");
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
        double newInit = new NewtonRaphson(frameMap, DistributionFactory.getDistribution(_parms), init).doAll(train).value();
        delta = Math.abs(init - newInit);
        init = newInit;
        LOG.info("Iteration " + (++count) + ": initial value: " + init);
      } while (count < N && delta >= tol);
      if (delta > tol) LOG.warn("Not fully converged.");
      LOG.info("Newton-Raphson iteration ran for " + count + " iteration(s). Final residual: " + delta);
      return init;
    }


    private static final double MIN_LOG_TRUNC = -19;
    private static final double MAX_LOG_TRUNC = 19;

    private void truncatePreds(final DTree tree, int firstLeafIndex, DistributionFamily dist) {
      if (firstLeafIndex==tree._len) return;
      ComputeMinMax minMax = new ComputeMinMax(frameMap, firstLeafIndex, tree._len).doAll(_train);
      if (LOG.isTraceEnabled()) {
        LOG.trace("Number of leaf nodes: " + minMax._mins.length);
        LOG.trace("Min: " + Arrays.toString(minMax._mins));
        LOG.trace("Max: " + Arrays.toString(minMax._maxs));
      }
      //loop over leaf nodes only: starting at leaf index
      for (int i = 0; i < tree._len - firstLeafIndex; i++) {
        final LeafNode node = ((LeafNode) tree.node(firstLeafIndex + i));
        int nidx = node.nid();
        float nodeMin = minMax._mins[nidx- firstLeafIndex];
        float nodeMax = minMax._maxs[nidx- firstLeafIndex];
        if (LOG.isTraceEnabled()) LOG.trace("Node: " + nidx + " min/max: " + nodeMin + "/" + nodeMax);

        // https://github.com/cran/gbm/blob/master/src/poisson.cpp
        // https://github.com/harrysouthworth/gbm/blob/master/src/poisson.cpp
        // https://github.com/gbm-developers/gbm/blob/master/src/poisson.cpp

        // https://github.com/harrysouthworth/gbm/blob/master/src/gamma.cpp
        // https://github.com/gbm-developers/gbm/blob/master/src/gamma.cpp

        // https://github.com/harrysouthworth/gbm/blob/master/src/tweedie.cpp
        // https://github.com/gbm-developers/gbm/blob/master/src/tweedie.cpp
        double val = node._pred;
        if (dist == DistributionFamily.gamma || dist == DistributionFamily.tweedie) //only for gamma/tweedie
          val += nodeMax;
        if (val > MAX_LOG_TRUNC) {
          if (LOG.isDebugEnabled()) LOG.debug("Truncating large positive leaf prediction (log): " + node._pred + " to " + (MAX_LOG_TRUNC - nodeMax));
          node._pred = (float) (MAX_LOG_TRUNC - nodeMax);
        }
        val = node._pred;
        if (dist == DistributionFamily.gamma || dist == DistributionFamily.tweedie) //only for gamma/tweedie
          val += nodeMin;
        if (val < MIN_LOG_TRUNC) {
          if (LOG.isDebugEnabled()) LOG.debug("Truncating large negative leaf prediction (log): " + node._pred + " to " + (MIN_LOG_TRUNC - nodeMin));
          node._pred = (float) (MIN_LOG_TRUNC - nodeMin);
        }
        if (node._pred < MIN_LOG_TRUNC && node._pred > MAX_LOG_TRUNC) {
          LOG.warn("Terminal node prediction outside of allowed interval in log-space: "
                  + node._pred + " (should be in " + MIN_LOG_TRUNC + "..." + MAX_LOG_TRUNC + ").");
        }
      }
    }

    // --------------------------------------------------------------------------
    // Build the next k-trees, which is trying to correct the residual error from
    // the prior trees.
    @Override protected boolean buildNextKTrees() {
      // We're going to build K (nclass) trees - each focused on correcting
      // errors for a single class.
      final DTree[] ktrees = new DTree[_nclass];

      // Define a "working set" of leaf splits, from here to tree._len
      int[] leaves = new int[_nclass];

      // Get distribution 
      Distribution distributionImpl = DistributionFactory.getDistribution(_parms);

      // Compute predictions and resulting residuals
      // ESL2, page 387, Steps 2a, 2b
      // fills "Work" columns for all rows (incl. OOB) with the residuals
      double huberDelta = Double.NaN;
      if (_parms._distribution == DistributionFamily.huber) {
        // Jerome Friedman 1999: Greedy Function Approximation: A Gradient Boosting Machine
        // https://statweb.stanford.edu/~jhf/ftp/trebst.pdf
        // compute absolute diff |y-(f+o)| for all rows
        Vec diff = new ComputeAbsDiff(frameMap).doAll(1, (byte)3 /*numeric*/, _train).outputFrame().anyVec();
        // compute weighted alpha-quantile of the absolute residual -> this is the delta for the huber loss
        huberDelta = MathUtils.computeWeightedQuantile(_weights, diff, _parms._huber_alpha);
        distributionImpl.setHuberDelta(huberDelta);
        // now compute residuals using the gradient of the huber loss (with a globally adjusted delta)
        new StoreResiduals(frameMap, distributionImpl).doAll(_train, _parms._build_tree_one_node);
      } else {
        // compute predictions and residuals in one shot
        new ComputePredAndRes(frameMap, _nclass, _model._output._distribution, distributionImpl)
                .doAll(_train, _parms._build_tree_one_node);
      }
      for (int k = 0; k < _nclass; k++) {
        if (LOG.isTraceEnabled() && ktrees[k]!=null) {
          LOG.trace("Updated predictions in WORK col for class " + k + ":\n" + new Frame(new String[]{"WORK"},new Vec[]{vec_work(_train, k)}).toTwoDimTable());
        }
      }

      // ----
      // ESL2, page 387.  Step 2b ii.
      // One Big Loop till the ktrees are of proper depth.
      // Adds a layer to the trees each pass.
      Constraints cs = _parms.constraints(_train);
      growTrees(ktrees, leaves, _rand, cs);
      for (int k = 0; k < _nclass; k++) {
        if (LOG.isTraceEnabled() && ktrees[k]!=null) {
          LOG.trace("Grew trees. Updated NIDs for class " + k + ":\n" + new Frame(new String[]{"NIDS"},new Vec[]{vec_nids(_train, k)}).toTwoDimTable());
        }
      }

      // ----
      // ESL2, page 387.  Step 2b iii.  Compute the gammas (leaf node predictions === fit best constant), and store them back
      // into the tree leaves.  Includes learn_rate.
      GammaPass gp = new GammaPass(frameMap, ktrees, leaves, distributionImpl, _nclass);
      gp.doAll(_train);
      if (_parms._distribution == DistributionFamily.laplace) {
        fitBestConstantsQuantile(ktrees, leaves[0], 0.5); //special case for Laplace: compute the median for each leaf node and store that as prediction
      } else if (_parms._distribution == DistributionFamily.quantile) {
        if(cs == null) {
          fitBestConstantsQuantile(ktrees, leaves[0], _parms._quantile_alpha); //compute the alpha-quantile for each leaf node and store that as prediction
        } else {
          fitBestConstants(ktrees, leaves, gp, cs); // compute quantile monotone constraints using precomputed parent prediction
          resetQuantileConstants(ktrees, _parms._quantile_alpha, cs);
        }
      } else if (_parms._distribution == DistributionFamily.huber) {
        fitBestConstantsHuber(ktrees, leaves[0], huberDelta); //compute the alpha-quantile for each leaf node and store that as prediction
      } else {
        fitBestConstants(ktrees, leaves, gp, cs);
      }

      // Apply a correction for strong mispredictions (otherwise deviance can explode)
      if (_parms._distribution == DistributionFamily.gamma ||
              _parms._distribution == DistributionFamily.poisson ||
              _parms._distribution == DistributionFamily.tweedie) {
        assert(_nclass == 1);
        truncatePreds(ktrees[0], leaves[0], _parms._distribution);
      }

      if ((cs != null) && constraintCheckEnabled()) {
        _job.update(0, "Checking monotonicity constraints on the final model");
        checkConstraints(ktrees, leaves, cs);
      }

      // ----
      // ESL2, page 387.  Step 2b iv.  Cache the sum of all the trees, plus the
      // new tree, in the 'tree' columns.  Also, zap the NIDs for next pass.
      // Tree <== f(Tree)
      // Nids <== 0
      new AddTreeContributions(
              frameMap, ktrees, _parms._pred_noise_bandwidth, _parms._seed, _parms._ntrees, _model._output._ntrees
      ).doAll(_train);

      // sanity check
      for (int k = 0; k < _nclass; k++) {
        if (ktrees[k]!=null) assert(vec_nids(_train,k).mean()==0);
      }

      // Grow the model by K-trees
      _model._output.addKTrees(ktrees);

      boolean converged = effective_learning_rate() < 1e-6;
      if (converged) {
        LOG.warn("Effective learning rate dropped below 1e-6 (" + _parms._learn_rate + " * " + _parms._learn_rate_annealing + "^" + (_model._output._ntrees-1) + ") - stopping the model now.");
      }
      return converged;
    }

    /**
     * How may trees are actually calculated for the number of classes the model uses.
     * @return number of trees
     */
    private int numClassTrees() {
      return _nclass == 2 ? 1 : _nclass; // Boolean Optimization (only one tree needed for 2-class problems)
    }

    /**
     * Grow k regression trees (k=1 for regression and binomial, k=N for classification with N classes)
     * @param ktrees k trees to grow (must be properly initialized)
     * @param leaves workspace to store the leaf node starting index (k-dimensional - one per tree)
     * @param rand PRNG for reproducibility
     */
    private void growTrees(DTree[] ktrees, int[] leaves, Random rand, Constraints cs) {
      // Initial set of histograms.  All trees; one leaf per tree (the root
      // leaf); all columns
      DHistogram hcs[][][] = new DHistogram[_nclass][1/*just root leaf*/][_ncols];

      // Adjust real bins for the top-levels
      int adj_nbins = Math.max(_parms._nbins_top_level,_parms._nbins);

      long rseed = rand.nextLong();
      // initialize trees
      for (int k = 0; k < numClassTrees(); k++) {
        // Initially setup as-if an empty-split had just happened
        if (_model._output._distribution[k] != 0) {
          ktrees[k] = new DTree(_train, _ncols, _mtry, _mtry_per_tree, rseed, _parms);
          DHistogram[] hist = DHistogram.initialHist(_train, _ncols, adj_nbins, hcs[k][0], rseed, _parms, getGlobalQuantilesKeys(), cs);
          new UndecidedNode(ktrees[k], DTree.NO_PARENT, hist, cs); // The "root" node
        }
      }

      // Sample - mark the lines by putting 'OUT_OF_BAG' into nid(<klass>) vector
      if (_parms._sample_rate < 1 || _parms._sample_rate_per_class != null) {
        Sample ss[] = new Sample[_nclass];
        for (int k = 0; k < _nclass; k++)
          if (ktrees[k] != null)
            ss[k] = new Sample(ktrees[k], _parms._sample_rate, _parms._sample_rate_per_class).dfork(null, new Frame(vec_nids(_train, k), _response), _parms._build_tree_one_node);
        for (int k = 0; k < _nclass; k++) {
          if (ss[k] != null) {
            ss[k].getResult();
            if (LOG.isTraceEnabled() && ktrees[k]!=null) {
              LOG.trace("Sampled OOB rows. NIDS:\n" + new Frame(vec_nids(_train, k)).toTwoDimTable());
            }
          }
        }
      }

      // ----
      // ESL2, page 387.  Step 2b ii.
      // One Big Loop till the ktrees are of proper depth.
      // Adds a layer to the trees each pass.
      int depth = 0;
      for (; depth < _parms._max_depth; depth++) {
        hcs = buildLayer(_train, _parms._nbins, _parms._nbins_cats, ktrees, leaves, hcs, _parms._build_tree_one_node);
        // If we did not make any new splits, then the tree is split-to-death
        if (hcs == null) break;
      }

      // Each tree bottomed-out in a DecidedNode; go 1 more level and insert
      // LeafNodes to hold predictions.
      for (int k = 0; k < _nclass; k++) {
        DTree tree = ktrees[k];
        if (tree == null) continue;
        int leaf = tree.len();
        leaves[k] = leaf; //record the size of the tree before splitting the bottom nodes as the starting index for the leaf node indices
        for (int nid = 0; nid < leaf; nid++) {
          if (tree.node(nid) instanceof DecidedNode) {
            DecidedNode dn = tree.decided(nid);
            if (dn._split == null) { // No decision here, no row should have this NID now
              if (nid == 0)               // Handle the trivial non-splitting tree
                new LeafNode(tree, DTree.NO_PARENT, 0);
              continue;
            }
            for (int i = 0; i < dn._nids.length; i++) { //L/R children
              int cnid = dn._nids[i];
              if (cnid == BuildHistogram.UNDECIDED_CHILD_NODE_ID ||    // Bottomed out (predictors or responses known constant)
                      tree.node(cnid) instanceof UndecidedNode || // Or chopped off for depth
                      (tree.node(cnid) instanceof DecidedNode &&  // Or not possible to split
                              ((DecidedNode) tree.node(cnid))._split == null)) {
                dn._nids[i] = new LeafNode(tree, nid).nid(); // Mark a leaf here
              }
            }
          }
        }
      } // -- k-trees are done
    }

    // Jerome Friedman 1999: Greedy Function Approximation: A Gradient Boosting Machine
    // https://statweb.stanford.edu/~jhf/ftp/trebst.pdf
    private void fitBestConstantsHuber(DTree[] ktrees, int firstLeafIndex, double huberDelta) {
      if (firstLeafIndex == ktrees[0]._len) return; // no splits happened - nothing to do
      assert(_nclass==1);

      // get diff y-(f+o) and weights and strata (node idx)
      Vec diff = new ComputeDiff(frameMap).doAll(1, (byte)3 /*numeric*/, _train).outputFrame().anyVec();
      Vec weights = hasWeightCol() ? _train.vecs()[idx_weight()] : null;
      Vec strata = vec_nids(_train,0);

      // compute median diff for each leaf node
      Quantile.StratifiedQuantilesTask sqt = new Quantile.StratifiedQuantilesTask(null, 0.5 /*median of weighted residuals*/, diff, weights, strata, QuantileModel.CombineMethod.INTERPOLATE);
      H2O.submitTask(sqt);
      sqt.join();

      // subtract median(diff) from residuals for all observations of each leaf
      DiffMinusMedianDiff hp = new DiffMinusMedianDiff(strata, sqt._quantiles /*median residuals per leaf*/);
      Frame tmpFrame1 = new Frame(new String[]{"strata","diff"}, new Vec[]{strata,diff});
      hp.doAll(tmpFrame1);
      Vec diffMinusMedianDiff = diff;

      // for each leaf, compute the mean of Math.signum(resMinusMedianRes) * Math.min(Math.abs(resMinusMedianRes), huberDelta),
      // where huberDelta is the alpha-percentile of the residual across all observations
      Frame tmpFrame2 = new Frame(_train.vecs());
      tmpFrame2.add("resMinusMedianRes", diffMinusMedianDiff);
      double[] huberGamma = new HuberLeafMath(frameMap, huberDelta, strata).doAll(tmpFrame2)._huberGamma;

      // now assign the median per leaf + the above _huberCorrection[i] to each leaf
      final DTree tree = ktrees[0];
      for (int i = 0; i < sqt._quantiles.length; i++) {
        double huber = (sqt._quantiles[i] /*median*/ + huberGamma[i]);
        if (Double.isNaN(sqt._quantiles[i])) continue; //no active rows for this NID
        double val = effective_learning_rate() * huber;
        assert !Double.isNaN(val) && !Double.isInfinite(val);
        if (val > _parms._max_abs_leafnode_pred) val = _parms._max_abs_leafnode_pred;
        if (val < -_parms._max_abs_leafnode_pred) val = -_parms._max_abs_leafnode_pred;
        ((LeafNode) tree.node(sqt._nids[i]))._pred = (float) val;
        if (LOG.isTraceEnabled()) { LOG.trace("Leaf " + sqt._nids[i] + " has huber value: " + huber); }
      }
      diffMinusMedianDiff.remove();
    }

    private double effective_learning_rate() {
      return _parms._learn_rate * Math.pow(_parms._learn_rate_annealing, (_model._output._ntrees-1));
    }

    private void fitBestConstantsQuantile(DTree[] ktrees, int firstLeafIndex, double quantile) {
      if (firstLeafIndex == ktrees[0]._len) return; // no splits happened - nothing to do
      assert(_nclass==1);
      Vec diff = new ComputeDiff(frameMap).doAll(1, (byte)3 /*numeric*/, _train).outputFrame().anyVec();
      Vec weights = hasWeightCol() ? _train.vecs()[idx_weight()] : null;
      Vec strata = vec_nids(_train,0);

      // compute quantile for all leaf nodes
      QuantileModel.CombineMethod combine_method = QuantileModel.CombineMethod.INTERPOLATE;
      Quantile.StratifiedQuantilesTask sqt = new Quantile.StratifiedQuantilesTask(null, quantile, diff, weights, strata, combine_method);
      H2O.submitTask(sqt);
      sqt.join();

      final DTree tree = ktrees[0];
      for (int i = 0; i < sqt._quantiles.length; i++) {
        double leafQuantile = sqt._quantiles[i];
        if (Double.isNaN(leafQuantile)) continue; //no active rows for this NID
        double val = effective_learning_rate() * leafQuantile;
        if (val > _parms._max_abs_leafnode_pred) val = _parms._max_abs_leafnode_pred;
        if (val < -_parms._max_abs_leafnode_pred) val = -_parms._max_abs_leafnode_pred;
        ((LeafNode) tree.node(sqt._nids[i]))._pred = (float) val;
      }
    }

    /**
     * This method recompute result leaf node prediction to get a more precise prediction but respecting 
     * the column constraints in subtrees
     * @param ktrees array of all trained trees
     * @param quantile quantile alpha parameter
     * @param cs constraint object to get information about constraints for each column 
     */
    private void resetQuantileConstants(DTree[] ktrees, double quantile, Constraints cs) {
      Vec diff = new ComputeDiff(frameMap).doAll(1, (byte)3,  _train).outputFrame().anyVec();
      Vec weights = hasWeightCol() ? _train.vecs()[idx_weight()] : null;
      Vec strata = vec_nids(_train,0);

      // compute quantile for all leaf nodes
      QuantileModel.CombineMethod combine_method = QuantileModel.CombineMethod.INTERPOLATE;
      Quantile.StratifiedQuantilesTask sqt = new Quantile.StratifiedQuantilesTask(null, quantile, diff, weights, strata, combine_method);
      H2O.submitTask(sqt);
      sqt.join();

      for (int k = 0; k < _nclass; k++) {
        final DTree tree = ktrees[k];
        if (tree == null || tree.len() < 2) continue;
        float[] mins = new float[tree._len];
        int[] min_ids = new int[tree._len];
        float[] maxs = new float[tree._len];
        int[] max_ids = new int[tree._len];
        int dnSize = tree._len - tree._leaves; // calculate index where leaves starts
        rollupMinMaxPreds(tree, tree.root(), mins, min_ids, maxs, max_ids);
        for (int i = dnSize; i < tree.len(); i++) {
          LeafNode node = (LeafNode)tree.node(i);
          int quantileId = i - dnSize;
          double leafQuantile = sqt._quantiles[quantileId];
          boolean canBeReplaced = true;
          DTree.Node tmpNode = tree.node(i);
          while(tmpNode.pid() != DTree.NO_PARENT) {
            DecidedNode parent = (DecidedNode) tree.node(tmpNode._pid);
            int constraint = cs.getColumnConstraint(parent._split._col);
            if (parent._split.naSplitDir() == DHistogram.NASplitDir.NAvsREST || constraint == 0) {
              tmpNode = parent;
              continue;
            }
            if (constraint > 0) {
              if (leafQuantile > mins[parent._nids[0]] || leafQuantile < maxs[parent._nids[1]]) {
                canBeReplaced = false;
                break;
              }
            } else if (leafQuantile < maxs[parent._nids[0]] || leafQuantile > mins[parent._nids[1]]) {
                canBeReplaced = false;
                break;
            }
            tmpNode = parent;
          }
          if(canBeReplaced){
            node._pred = (float) leafQuantile;
          }
        }
      }
    }


    private void fitBestConstants(DTree[] ktrees, int[] leafs, GammaPass gp, Constraints cs) {
      final boolean useSplitPredictions = cs != null && cs.useBounds();
      double m1class = (_nclass > 1 && _parms._distribution == DistributionFamily.multinomial) ||
              (_nclass > 2 && _parms._distribution == DistributionFamily.custom) ? (double) (_nclass - 1) / _nclass : 1.0; // K-1/K for multinomial
      for (int k = 0; k < _nclass; k++) {
        final DTree tree = ktrees[k];
        if (tree == null) continue;
        if (LOG.isTraceEnabled()) {
          for (int i=0;i<ktrees[k]._len-leafs[k];++i) 
            LOG.trace(ktrees[k].node(leafs[k]+i).toString());
        }
        for (int i = 0; i < tree._len - leafs[k]; i++) {
          LeafNode leafNode = (LeafNode) ktrees[k].node(leafs[k] + i);
          final double gamma;
          if (useSplitPredictions) {
            gamma = gp.gamma(leafNode.getSplitPrediction());
          } else {
            gamma = gp.gamma(k, i);
          }
          double gf = effective_learning_rate() * m1class * gamma;
          // In the multinomial case, check for very large values (which will get exponentiated later)
          // Note that gss can be *zero* while rss is non-zero - happens when some rows in the same
          // split are perfectly predicted true, and others perfectly predicted false.
          if (_parms._distribution == DistributionFamily.multinomial || (_parms._distribution == DistributionFamily.custom && _nclass > 2)) {
            if (gf > 1e4) gf = 1e4f; // Cap prediction, will already overflow during Math.exp(gf)
            else if (gf < -1e4) gf = -1e4f;
          }
          if (Double.isNaN(gf)) gf=0;
          else if (Double.isInfinite(gf)) gf=Math.signum(gf)*1e4f;
          if (gf > _parms._max_abs_leafnode_pred) gf = _parms._max_abs_leafnode_pred;
          if (gf < -_parms._max_abs_leafnode_pred) gf = -_parms._max_abs_leafnode_pred;
          leafNode._pred = (float) gf;
        }
      }
    }

    private boolean constraintCheckEnabled() {
      return Boolean.parseBoolean(getSysProperty("gbm.monotonicity.checkEnabled", "true"));
    }

    private void checkConstraints(DTree[] ktrees, int[] leafs, Constraints cs) {
      for (int k = 0; k < _nclass; k++) {
        final DTree tree = ktrees[k];
        if (tree == null) continue;
        float[] mins = new float[tree._len];
        int[] min_ids = new int[tree._len];
        float[] maxs = new float[tree._len];
        int[] max_ids = new int[tree._len];
        rollupMinMaxPreds(tree, tree.root(), mins, min_ids, maxs, max_ids);
        for (int i = 0; i < tree._len - leafs.length; i++) {
          DTree.Node node = tree.node(i);
          if (! (node instanceof DecidedNode))
            continue;
          DecidedNode dn = ((DecidedNode) node);
          if (dn._split == null)
            continue;
          int constraint = cs.getColumnConstraint(dn._split._col);
          if (dn._split.naSplitDir() == DHistogram.NASplitDir.NAvsREST) {
            // NAs are not subject to constraints, we don't have to check the monotonicity on "NA vs REST" type of splits
            continue;
          }
          if (constraint > 0) {
            if (maxs[dn._nids[0]] > mins[dn._nids[1]]) {
              String message = "Monotonicity constraint " + constraint + " violated on column '" + _train.name(dn._split._col) + "' (max(left) > min(right)): " +
                      maxs[dn._nids[0]] + " > " + mins[dn._nids[1]] +
                      "\nNode: " + node +
                      "\nLeft Node (max): " + tree.node(max_ids[dn._nids[0]]) +
                      "\nRight Node (min): " + tree.node(min_ids[dn._nids[1]]);
              throw new IllegalStateException(message);
            }
          } else if (constraint < 0) {
            if (mins[dn._nids[0]] < maxs[dn._nids[1]]) {
              String message = "Monotonicity constraint " + constraint + " violated on column '" + _train.name(dn._split._col) + "' (min(left) < max(right)): " +
                      mins[dn._nids[0]] + " < " + maxs[dn._nids[1]] +
                      "\nNode: " + node +
                      "\nLeft Node (min): " + tree.node(min_ids[dn._nids[0]]) +
                      "\nRight Node (max): " + tree.node(max_ids[dn._nids[1]]);
              throw new IllegalStateException(message);
            }
          }
        }
      }
    }

    private void rollupMinMaxPreds(DTree tree, DTree.Node node, float[] mins, int min_ids[], float[] maxs, int[] max_ids) {
      if (node instanceof LeafNode) {
        mins[node.nid()] = ((LeafNode) node)._pred;
        min_ids[node.nid()] = node.nid();
        maxs[node.nid()] = ((LeafNode) node)._pred;
        max_ids[node.nid()] = node.nid();
        return;
      }
      DecidedNode dn = (DecidedNode) node;
      rollupMinMaxPreds(tree, tree.node(dn._nids[0]), mins, min_ids, maxs, max_ids);
      rollupMinMaxPreds(tree, tree.node(dn._nids[1]), mins, min_ids, maxs, max_ids);
      final int min_id = mins[dn._nids[0]] < mins[dn._nids[1]] ? dn._nids[0] : dn._nids[1];
      mins[node.nid()] = mins[min_id];
      min_ids[node.nid()] = min_ids[min_id];
      final int max_id = maxs[dn._nids[0]] > maxs[dn._nids[1]] ? dn._nids[0] : dn._nids[1];
      maxs[node.nid()] = maxs[max_id];
      max_ids[node.nid()] = max_ids[max_id];
    }

    @Override protected GBMModel makeModel(Key<GBMModel> modelKey, GBMModel.GBMParameters parms) {
      return new GBMModel(modelKey, parms, new GBMModel.GBMOutput(GBM.this));
    }

  }


  //--------------------------------------------------------------------------------------------------------------------

  private static class FillVecWithConstant extends MRTask<FillVecWithConstant> {
    private double init;

    public FillVecWithConstant(double d) {
      init = d;
    }

    @Override
    protected boolean modifiesVolatileVecs() {
      return true;
    }

    @Override
    public void map(Chunk tree) {
      if (tree instanceof C8DVolatileChunk) {
        Arrays.fill(((C8DVolatileChunk) tree).getValues(), init);
      } else {
        for (int i = 0; i < tree._len; i++)
          tree.set(i, init);
      }
    }
  }


  private static class ResponseLessOffsetTask extends MRTask<ResponseLessOffsetTask> {
    private FrameMap fm;

    public ResponseLessOffsetTask(FrameMap frameMap) {
      fm = frameMap;
    }

    @Override
    public void map(Chunk[] chks, NewChunk[] nc) {
      final Chunk resp = chks[fm.responseIndex];
      final Chunk offset = chks[fm.offsetIndex];
      for (int i = 0; i < chks[0]._len; ++i)
        nc[0].addNum(resp.atd(i) - offset.atd(i));  // y - o
    }
  }


  /**
   * Newton-Raphson fixpoint iteration to find a self-consistent initial value
   */
  private static class NewtonRaphson extends MRTask<NewtonRaphson> {
    private FrameMap fm;
    private Distribution dist;
    private double _init;
    private double _numerator;
    private double _denominator;

    public NewtonRaphson(FrameMap frameMap, Distribution distribution, double initialValue) {
      assert frameMap != null && distribution != null;
      fm = frameMap;
      dist = distribution;
      _init = initialValue;
      _numerator = 0;
      _denominator = 0;
    }

    public double value() {
      return _init + _numerator / _denominator;
    }

    @Override
    public void map(Chunk[] chks) {
      Chunk ys = chks[fm.responseIndex];
      Chunk offset = chks[fm.offsetIndex];
      Chunk weight = fm.weightIndex >= 0 ? chks[fm.weightIndex] : new C0DChunk(1, chks[0]._len);
      for (int row = 0; row < ys._len; row++) {
        double w = weight.atd(row);
        if (w == 0) continue;
        if (ys.isNA(row)) continue;
        double y = ys.atd(row);
        double o = offset.atd(row);
        double p = dist.linkInv(o + _init);
        _numerator += w * (y - p);
        _denominator += w * p * (1. - p);
      }
    }

    @Override
    public void reduce(NewtonRaphson mrt) {
      _numerator += mrt._numerator;
      _denominator += mrt._denominator;
    }
  }


  /**
   * Compute Residuals
   * Do this for all rows, whether OOB or not
   */
  private static class ComputePredAndRes extends MRTask<ComputePredAndRes> {
    private FrameMap fm;
    private int nclass;
    private boolean[] out;
    private Distribution dist;

    public ComputePredAndRes(FrameMap frameMap, int nClasses, double[] outputDistribution, Distribution distribution) {
      fm = frameMap;
      nclass = nClasses;
      dist = distribution;
      out = new boolean[outputDistribution.length];
      for (int i = 0; i < out.length; i++) out[i] = (outputDistribution[i] != 0);
    }

    @Override
    public void map(Chunk[] chks) {
      Chunk ys = chks[fm.responseIndex];
      Chunk offset = fm.offsetIndex >= 0 ? chks[fm.offsetIndex] : new C0DChunk(0, chks[0]._len);
      Chunk preds = chks[fm.tree0Index];  // Prior tree sums
      C8DVolatileChunk wk = (C8DVolatileChunk) chks[fm.work0Index]; // Place to store residuals
      Chunk weights = fm.weightIndex >= 0 ? chks[fm.weightIndex] : new C0DChunk(1, chks[0]._len);
      double[] fs = nclass > 1 ? new double[nclass + 1] : null;
      for (int row = 0; row < wk._len; row++) {
        double weight = weights.atd(row);
        if (weight == 0) continue;
        if (ys.isNA(row)) continue;
        double f = preds.atd(row) + offset.atd(row);
        double y = ys.atd(row);
        if (LOG.isTraceEnabled()) LOG.trace(f + " vs " + y); //expect that the model predicts very negative values for 0 and very positive values for 1
        if ((dist._family == DistributionFamily.multinomial && fs != null) || (dist._family == DistributionFamily.custom && nclass > 2)) {
          double sum = score1static(chks, fm.tree0Index, 0.0 /*not used for multiclass*/, fs, row, dist, nclass);
          if (Double.isInfinite(sum)) {  // Overflow (happens for constant responses)
            for (int k = 0; k < nclass; k++) {
              wk = (C8DVolatileChunk) chks[fm.work0Index + k];
              wk.getValues()[row] = ((float) dist.negHalfGradient(y, (Double.isInfinite(fs[k + 1]) ? 1.0f : 0.0f), k));
            }
          } else {
            for (int k = 0; k < nclass; k++) { // Save as a probability distribution
              if (out[k]) {
                wk = (C8DVolatileChunk) chks[fm.work0Index + k];
                wk.getValues()[row] = ((float) dist.negHalfGradient(y, (float) (fs[k + 1] / sum), k));
              }
            }
          }
        } else {
          wk.getValues()[row] = ((float) dist.negHalfGradient(y, f));
        }
      }
    }
  }


  private static class ComputeMinMax extends MRTask<ComputeMinMax> {
    private FrameMap fm;
    int firstLeafIdx;
    int _totalNumNodes;
    float[] _mins;
    float[] _maxs;


    public ComputeMinMax(FrameMap frameMap, int firstLeafIndex, int totalNumNodes) {
      fm = frameMap;
      firstLeafIdx = firstLeafIndex;
      _totalNumNodes = totalNumNodes;
    }

    @Override
    public void map(Chunk[] chks) {
      int len = _totalNumNodes - firstLeafIdx;  // number of leaves
      _mins = new float[len];
      _maxs = new float[len];
      Arrays.fill(_mins, Float.MAX_VALUE);
      Arrays.fill(_maxs, -Float.MAX_VALUE);

      Chunk ys = chks[fm.responseIndex];
      Chunk offset = fm.offsetIndex >= 0 ? chks[fm.offsetIndex] : new C0DChunk(0, chks[0]._len);
      Chunk preds = chks[fm.tree0Index]; // Prior tree sums
      Chunk nids = chks[fm.nids0Index];
      Chunk weights = fm.weightIndex >= 0 ? chks[fm.weightIndex] : new C0DChunk(1, chks[0]._len);
      for (int row = 0; row < preds._len; row++) {
        if (ys.isNA(row)) continue;
        if (weights.atd(row) == 0) continue;
        int nid = (int) nids.at8(row);
        assert (nid != BuildHistogram.UNDECIDED_CHILD_NODE_ID);
        if (nid < 0) continue; //skip OOB and otherwise skipped rows
        float f = (float) (preds.atd(row) + offset.atd(row));
        int idx = nid - firstLeafIdx;
        _mins[idx] = Math.min(_mins[idx], f);
        _maxs[idx] = Math.max(_maxs[idx], f);
      }
    }

    @Override
    public void reduce(ComputeMinMax mrt) {
      ArrayUtils.reduceMin(_mins, mrt._mins);
      ArrayUtils.reduceMax(_maxs, mrt._maxs);
    }
  }


  private static class ComputeDiff extends MRTask<ComputeDiff> {
    private FrameMap fm;

    public ComputeDiff(FrameMap frameMap) {
      fm = frameMap;
    }

    @Override
    public void map(Chunk[] chks, NewChunk[] nc) {
      final Chunk y = chks[fm.responseIndex];
      final Chunk o = fm.offsetIndex >= 0 ? chks[fm.offsetIndex] : new C0DChunk(0, chks[0]._len);
      final Chunk f = chks[fm.tree0Index];
      for (int i = 0; i < chks[0].len(); ++i)
        nc[0].addNum(y.atd(i) - (f.atd(i) + o.atd(i)));
    }
  }


  private static class ComputeAbsDiff extends MRTask<ComputeAbsDiff> {
    private FrameMap fm;

    public ComputeAbsDiff(FrameMap frameMap) {
      fm = frameMap;
    }

    @Override
    public void map(Chunk[] chks, NewChunk[] nc) {
      final Chunk y = chks[fm.responseIndex];
      final Chunk o = fm.offsetIndex >= 0 ? chks[fm.offsetIndex] : new C0DChunk(0, chks[0]._len);
      final Chunk f = chks[fm.tree0Index];
      for (int i = 0; i < chks[0].len(); ++i)
        nc[0].addNum(Math.abs(y.atd(i) - (f.atd(i) + o.atd(i))));
    }
  }


  public static class DiffMinusMedianDiff extends MRTask<DiffMinusMedianDiff> {
    private final int _strataMin;
    private double[] _terminalMedians;

    public DiffMinusMedianDiff(Vec strata, double[] terminalMedians) {
      _strataMin = (int) strata.min();
      _terminalMedians = terminalMedians;
    }

    @Override
    public void map(Chunk[] chks) {
      final Chunk strata = chks[0];
      final Chunk diff = chks[1];
      for (int i = 0; i < chks[0].len(); ++i) {
        int nid = (int) strata.atd(i);
        diff.set(i, diff.atd(i) - _terminalMedians[nid - _strataMin]);
      }
    }
  }


  private static final class HuberLeafMath extends MRTask<HuberLeafMath> {
    // INPUT
    final FrameMap fm;
    final double _huberDelta;
    final Vec _strata;
    final int _strataMin;
    final int _strataMax;
    // OUTPUT
    double[/*leaves*/] _huberGamma, _wcounts;

    public HuberLeafMath(FrameMap frameMap, double huberDelta, Vec strata) {
      fm = frameMap;
      _huberDelta = huberDelta;
      _strata = strata;
      _strataMin = (int) _strata.min();
      _strataMax = (int) _strata.max();
    }

    @Override
    public void map(Chunk[] cs) {
      if (_strata.length() == 0) {
        LOG.warn("No Huber math can be done since there's no strata.");
        _huberGamma = new double[0];
        return;
      }
      final int nstrata = _strataMax - _strataMin + 1;
      LOG.info("Computing Huber math for (up to) " + nstrata + " different strata.");
      _huberGamma = new double[nstrata];
      _wcounts = new double[nstrata];
      Chunk weights = fm.weightIndex >= 0 ? cs[fm.weightIndex] : new C0DChunk(1, cs[0]._len);
      Chunk stratum = cs[fm.nids0Index];
      Chunk diffMinusMedianDiff = cs[cs.length - 1];
      for (int row = 0; row < cs[0]._len; ++row) {
        int nidx = (int) stratum.at8(row) - _strataMin; //get terminal node for this row
        _huberGamma[nidx] += weights.atd(row) * Math.signum(diffMinusMedianDiff.atd(row)) * Math.min(Math.abs(diffMinusMedianDiff.atd(row)), _huberDelta);
        _wcounts[nidx] += weights.atd(row);
      }
    }

    @Override
    public void reduce(HuberLeafMath mrt) {
      ArrayUtils.add(_huberGamma, mrt._huberGamma);
      ArrayUtils.add(_wcounts, mrt._wcounts);
    }

    @Override
    protected void postGlobal() {
      for (int i = 0; i < _huberGamma.length; ++i)
        _huberGamma[i] /= _wcounts[i];
    }
  }


  private static class StoreResiduals extends MRTask<StoreResiduals> {
    private FrameMap fm;
    private Distribution dist;

    public StoreResiduals(FrameMap frameMap, Distribution distribution) {
      fm = frameMap;
      dist = distribution;
    }

    @Override
    protected boolean modifiesVolatileVecs() {
      return true;
    }

    @Override
    public void map(Chunk[] chks) {
      Chunk ys = chks[fm.responseIndex];
      Chunk offset = fm.offsetIndex >= 0 ? chks[fm.offsetIndex] : new C0DChunk(0, chks[0]._len);
      Chunk preds = chks[fm.tree0Index];  // Prior tree sums
      C8DVolatileChunk wk = (C8DVolatileChunk) chks[fm.work0Index]; // Place to store residuals
      Chunk weights = fm.weightIndex >= 0 ? chks[fm.weightIndex] : new C0DChunk(1, chks[0]._len);
      for (int row = 0; row < wk._len; row++) {
        double weight = weights.atd(row);
        if (weight == 0) continue;
        if (ys.isNA(row)) continue;
        double f = preds.atd(row) + offset.atd(row);
        double y = ys.atd(row);
        wk.getValues()[row] = ((float) dist.negHalfGradient(y, f));
      }
    }
  }


  /**
   * Set terminal node estimates (gamma)
   * ESL2, page 387.  Step 2b iii.
   * Nids <== f(Nids)
   * For classification (bernoulli):
   * <pre>{@code    gamma_i = sum (w_i * res_i) / sum (w_i*p_i*(1 - p_i)) where p_i = y_i - res_i}</pre>
   * For classification (multinomial):
   * <pre>{@code    gamma_i_k = (nclass-1)/nclass * (sum res_i / sum (|res_i|*(1-|res_i|)))}</pre>
   * For regression (gaussian):
   * <pre>{@code    gamma_i = sum res_i / count(res_i)}</pre>
   */
  private static class GammaPass extends MRTask<GammaPass> {
    private final FrameMap fm;
    private final DTree[] _trees; // Read-only, shared (except at the histograms in the Nodes)
    private final int[] _leafs;  // Starting index of leaves (per class-tree)
    private final Distribution _dist;
    private final int _nclass;
    private double[/*tree/klass*/][/*tree-relative node-id*/] _num;
    private double[/*tree/klass*/][/*tree-relative node-id*/] _denom;

    public GammaPass(FrameMap frameMap, DTree[] trees, int[] leafs, Distribution distribution, int nClasses) {
      fm = frameMap;
      _leafs = leafs;
      _trees = trees;
      _dist = distribution;
      _nclass = nClasses;
    }

    double gamma(int tree, int nid) {
      return gamma(tree, nid, _num[tree][nid]);
    }

    double gamma(int tree, int nid, double num) {
      if (_denom[tree][nid] == 0)
        return 0;
      double g = num / _denom[tree][nid];
      assert !Double.isInfinite(g) && !Double.isNaN(g);
      return gamma(g);
    }

    double gamma(double g) {
      if (_dist._family == DistributionFamily.poisson ||
              _dist._family == DistributionFamily.gamma ||
              _dist._family == DistributionFamily.tweedie) {
        return _dist.link(g);
      } else {
        return g;
      }
    }

    @Override
    protected boolean modifiesVolatileVecs() {
      return true;
    }

    @Override
    public void map(Chunk[] chks) {
      _denom = new double[_nclass][];
      _num = new double[_nclass][];
      final Chunk resp = chks[fm.responseIndex]; // Response for this frame

      // For all tree/klasses
      for (int k = 0; k < _nclass; k++) {
        final DTree tree = _trees[k];
        final int leaf = _leafs[k];
        if (tree == null) continue; // Empty class is ignored
        assert (tree._len - leaf >= 0);

        // A leaf-biased array of all active Tree leaves.
        final double[] denom = _denom[k] = new double[tree._len - leaf];
        final double[] num = _num[k] = new double[tree._len - leaf];
        final C4VolatileChunk nids = (C4VolatileChunk) chks[fm.nids0Index + k]; // Node-ids  for this tree/class
        int[] nids_vals = nids.getValues();
        final Chunk ress = chks[fm.work0Index + k];  // Residuals for this tree/class
        final Chunk offset = fm.offsetIndex >= 0 ? chks[fm.offsetIndex] : new C0DChunk(0, chks[0]._len);
        final Chunk preds = chks[fm.tree0Index + k];
        final Chunk weights = fm.weightIndex >= 0 ? chks[fm.weightIndex] : new C0DChunk(1, chks[0]._len);

        // If we have all constant responses, then we do not split even the
        // root and the residuals should be zero.
        if (tree.root() instanceof LeafNode)
          continue;
        for (int row = 0; row < nids._len; row++) { // For all rows
          double w = weights.atd(row);
          if (w == 0)
            continue;

          double y = resp.atd(row); //response
          if (Double.isNaN(y))
            continue;

          // Compute numerator and denominator of terminal node estimate (gamma)
          int nid = (int) nids.at8(row);          // Get Node to decide from
          final boolean wasOOBRow = BuildHistogram.isOOBRow(nid); //same for all k
          if (wasOOBRow) nid = BuildHistogram.oob2Nid(nid);
          if (nid < 0)
            continue;
          DecidedNode dn = tree.decided(nid);           // Must have a decision point
          if (dn._split == null)                    // Unable to decide?
            dn = tree.decided(dn.pid());  // Then take parent's decision
          int leafnid = dn.getChildNodeID(chks, row); // Decide down to a leafnode
          assert leaf <= leafnid && leafnid < tree._len :
                  "leaf: " + leaf + " leafnid: " + leafnid + " tree._len: " + tree._len + "\ndn: " + dn;
          assert tree.node(leafnid) instanceof LeafNode;
          // Note: I can tell which leaf/region I end up in, but I do not care for
          // the prediction presented by the tree.  For GBM, we compute the
          // sum-of-residuals (and sum/abs/mult residuals) for all rows in the
          // leaf, and get our prediction from that.
          nids_vals[row] = leafnid;
          assert !ress.isNA(row);

          // OOB rows get placed properly (above), but they don't affect the computed Gamma (below)
          // For Laplace/Quantile distribution, we need to compute the median of (y-offset-preds == y-f), will be done outside of here
          if (wasOOBRow
                  || _dist._family == DistributionFamily.laplace
                  || _dist._family == DistributionFamily.huber
                  || _dist._family == DistributionFamily.quantile) continue;

          double z = ress.atd(row);  // residual
          double f = preds.atd(row) + offset.atd(row);
          int idx = leafnid - leaf;
          num[idx] += _dist.gammaNum(w, y, z, f);
          denom[idx] += _dist.gammaDenom(w, y, z, f);
        }
      }
    }

    @Override
    public void reduce(GammaPass gp) {
      ArrayUtils.add(_denom, gp._denom);
      ArrayUtils.add(_num, gp._num);
    }
  }


  private static class AddTreeContributions extends MRTask<AddTreeContributions> {
    private FrameMap fm;
    private DTree[] _ktrees;
    private int _nclass;
    private double _pred_noise_bandwidth;
    private long _seed;
    private int _ntrees1;
    private int _ntrees2;

    public AddTreeContributions(
            FrameMap frameMap, DTree[] ktrees, double predictionNoiseBandwidth, long seed, int nTreesInp, int nTreesOut
    ) {
      fm = frameMap;
      _ktrees = ktrees;
      _nclass = ktrees.length;
      _pred_noise_bandwidth = predictionNoiseBandwidth;
      _seed = seed;
      _ntrees1 = nTreesInp;
      _ntrees2 = nTreesOut;
    }

    @Override
    protected boolean modifiesVolatileVecs() {
      return true;
    }

    @Override
    public void map(Chunk[] chks) {
      Random rand = RandomUtils.getRNG(_seed);

      // For all tree/klasses
      for (int k = 0; k < _nclass; k++) {
        final DTree tree = _ktrees[k];
        if (tree == null) continue;
        final C4VolatileChunk nids = (C4VolatileChunk) chks[fm.nids0Index + k];
        final int[] nids_vals = nids.getValues();
        final C8DVolatileChunk ct = (C8DVolatileChunk) chks[fm.tree0Index + k];
        double[] ct_vals = ct.getValues();
        final Chunk y = chks[fm.responseIndex];
        final Chunk weights = fm.weightIndex >= 0 ? chks[fm.weightIndex] : new C0DChunk(1, chks[0]._len);
        long baseseed = (0xDECAF + _seed) * (0xFAAAAAAB + k * _ntrees1 + _ntrees2);
        for (int row = 0; row < nids._len; row++) {
          int nid = nids_vals[row];
          nids_vals[row] = BuildHistogram.FRESH;
          if (nid < 0) continue;
          if (y.isNA(row)) continue;
          if (weights.atd(row) == 0) continue;
          double factor = 1;
          if (_pred_noise_bandwidth != 0) {
            rand.setSeed(baseseed + nid); //bandwidth is a function of tree number, class and node id (but same for all rows in that node)
            factor += rand.nextGaussian() * _pred_noise_bandwidth;
          }
          // Prediction stored in Leaf is cut to float to be deterministic in reconstructing
          // <tree_klazz> fields from tree prediction
          ct_vals[row] = ((float) (ct.atd(row) + factor * ((LeafNode) tree.node(nid))._pred));
        }
      }
    }
  }



  //--------------------------------------------------------------------------------------------------------------------

  // Read the 'tree' columns, do model-specific math and put the results in the
  // fs[] array, and return the sum.  Dividing any fs[] element by the sum
  // turns the results into a probability distribution.
  @Override protected double score1(Chunk[] chks, double weight, double offset, double[/*nclass*/] fs, int row) {
    return score1static(chks, idx_tree(0), offset, fs, row, DistributionFactory.getDistribution(_parms), _nclass);
  }

  // Read the 'tree' columns, do model-specific math and put the results in the
  // fs[] array, and return the sum.  Dividing any fs[] element by the sum
  // turns the results into a probability distribution.
  private static double score1static(Chunk[] chks, int treeIdx, double offset, double[] fs, int row, Distribution dist, int nClasses) {
    double f = chks[treeIdx].atd(row) + offset;
    double p = dist.linkInv(f);
    if (dist._family == DistributionFamily.modified_huber || dist._family == DistributionFamily.bernoulli || dist._family == DistributionFamily.quasibinomial ||
            (dist._family == DistributionFamily.custom && nClasses == 2)) {
      fs[2] = p;
      fs[1] = 1.0 - p;
      return 1;                 // f2 = 1.0 - f1; so f1+f2 = 1.0
    } else if (dist._family == DistributionFamily.multinomial ||
            (dist._family == DistributionFamily.custom && nClasses > 2)) {
      if (nClasses == 2) {
        // This optimization assumes the 2nd tree of a 2-class system is the
        // inverse of the first.  Fill in the missing tree
        fs[1] = p;
        fs[2] = 1 / p;
        return fs[1] + fs[2];
      }
      // Multinomial loss function; sum(exp(data)).  Load tree data
      assert (offset == 0);
      fs[1] = f;
      for (int k = 1; k < nClasses; k++)
        fs[k + 1] = chks[treeIdx + k].atd(row);
      // Rescale to avoid Infinities; return sum(exp(data))
      return hex.genmodel.GenModel.log_rescale(fs);
    } else {
      return fs[0] = p;
    }
  }

}
