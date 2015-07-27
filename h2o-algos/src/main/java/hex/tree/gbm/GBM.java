package hex.tree.gbm;

import hex.Distribution;
import hex.ModelCategory;
import hex.schemas.GBMV3;
import hex.tree.*;
import hex.tree.DTree.DecidedNode;
import hex.tree.DTree.LeafNode;
import hex.tree.DTree.UndecidedNode;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.C0DChunk;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.*;

import java.util.Map;

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

  @Override public BuilderVisibility builderVisibility() { return BuilderVisibility.Stable; }

  // Called from an http request
  public GBM( GBMModel.GBMParameters parms) { super("GBM",parms); init(false); }

  @Override public GBMV3 schema() { return new GBMV3(); }

  /** Start the GBM training Job on an F/J thread.
   * @param work*/
  @Override public Job<GBMModel> trainModelImpl(long work) {
    return start(new GBMDriver(), work);
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
    double mean = 0;
    if (expensive) {
      if (error_count() > 0) {
        GBM.this.updateValidationMessages();
        throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(GBM.this);
      }
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
      // for Bernoulli, we compute the initial value with Newton-Raphson iteration, otherwise it might be NaN here
      _initialPrediction = _nclass > 2 ? 0 : getInitialValue();
    }

    switch( _parms._distribution) {
    case bernoulli:
      if( _nclass != 2 /*&& !couldBeBool(_response)*/)
        error("_distribution", "Binomial requires the response to be a 2-class categorical");
      break;
    case multinomial:
      if (!isClassifier()) error("_distribution", "Multinomial requires an enum response.");
      break;
    case poisson:
      if (isClassifier()) error("_distribution", "Poisson requires the response to be numeric.");
      break;
    case gamma:
      if (isClassifier()) error("_distribution", "Gamma requires the response to be numeric.");
      break;
    case tweedie:
      if (isClassifier()) error("_distribution", "Tweedie requires the response to be numeric.");
      break;
    case gaussian:
      if (isClassifier()) error("_distribution", "Gaussian requires the response to be numeric.");
      break;
    case AUTO:
      break;
    default:
      error("_distribution","Invalid distribution: " + _parms._distribution);
    }
    
    if( !(0. < _parms._learn_rate && _parms._learn_rate <= 1.0) )
      error("_learn_rate", "learn_rate must be between 0 and 1");
  }

  // ----------------------
  private class GBMDriver extends Driver {

    @Override protected void buildModel() {
      if (hasOffsetCol() && _parms._distribution == Distribution.Family.bernoulli) {
        _initialPrediction = getInitialValueBernoulliOffset(_train);
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

      // Reconstruct the working tree state from the checkpoint
      if( _parms._checkpoint ) {
        Timer t = new Timer();
        new ResidualsCollector(_ncols, _nclass, numSpecialCols(),_model._output._treeKeys).doAll(_train, _parms._build_tree_one_node);
        Log.info("Reconstructing tree residuals stats from checkpointed model took " + t);
      }

      // Loop over the K trees
      for( int tid=0; tid<_parms._ntrees; tid++) {
        // During first iteration model contains 0 trees, then 1-tree, ...
        // No need to score a checkpoint with no extra trees added
        if( tid!=0 || !_parms._checkpoint ) { // do not make initial scoring if model already exist
          double training_r2 = doScoringAndSaveModel(false, false, _parms._build_tree_one_node);
          if( training_r2 >= _parms._r2_stopping )
            return;             // Stop when approaching round-off error
        }

        // Compute predictions and resulting residuals for trees built so far
        // ESL2, page 387, Steps 2a, 2b
        ComputePredAndRes cpr = new ComputePredAndRes();
        cpr.doAll(_train, _parms._build_tree_one_node);

        // Build more trees, based on residuals above
        // ESL2, page 387, Step 2b ii, iii, iv
        Timer kb_timer = new Timer();

        buildNextKTrees(cpr.minValues, cpr.maxValues);
        Log.info((tid + 1) + ". tree was built in " + kb_timer.toString());
        GBM.this.update(1);
        if( !isRunning() ) return; // If canceled during building, do not bulkscore
      }
      // Final scoring (skip if job was cancelled)
      doScoringAndSaveModel(true, false, _parms._build_tree_one_node);
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
        Distribution dist = new Distribution(Distribution.Family.bernoulli);
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
    class ComputePredAndRes extends MRTask<ComputePredAndRes> {
      public IcedHashMap<IcedLong,IcedDouble> minValues;
      public IcedHashMap<IcedLong,IcedDouble> maxValues;
      @Override public void map( Chunk chks[] ) {
        minValues = new IcedHashMap<>();
        maxValues = new IcedHashMap<>();
        Chunk ys = chk_resp(chks);
        Chunk offset = hasOffsetCol() ? chk_offset(chks) : new C0DChunk(0, chks[0]._len);
        Chunk tr = chk_tree(chks, 0); // Prior tree sums
        Chunk wk = chk_work(chks, 0); // Place to store residuals
        boolean computeMinMax = ( _parms._distribution == Distribution.Family.poisson
                || _parms._distribution == Distribution.Family.gamma
                || _parms._distribution == Distribution.Family.tweedie);
        Chunk nids = computeMinMax ? chk_nids(chks, 0) : null; // NID
        double fs[] = _nclass > 1 ? new double[_nclass+1] : null;
        Distribution dist = new Distribution(_parms._distribution, _parms._tweedie_power);
        IcedLong nidx = new IcedLong(0);
        for( int row = 0; row < wk._len; row++) {
          if( ys.isNA(row) ) continue;
          double f = tr.atd(row) + offset.atd(row);
          if (computeMinMax) {
            nidx._val = nids.at8(row);
            IcedDouble mins = minValues.get(nidx);
            double oldMin = mins == null ? Double.MAX_VALUE : mins._val;
            IcedDouble ff = new IcedDouble(f);
            if (f < oldMin) {
              if (mins == null) minValues.put(nidx, ff);
              else minValues.replace(nidx, ff);
            }
            IcedDouble maxs = maxValues.get(nidx);
            double oldMax = maxs == null ? Double.MIN_VALUE : maxs._val;
            if (f > oldMax) {
              if (maxs == null) maxValues.put(nidx, ff);
              else maxValues.replace(nidx, ff);
            }
          }
          double y = ys.at8(row);
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

      @Override
      public void reduce(ComputePredAndRes mrt) {
        if (mrt.minValues == null || minValues == null) return;
        if (mrt.maxValues == null || maxValues == null) return;
        for (Map.Entry<IcedLong,IcedDouble> e : mrt.minValues.entrySet()) {
          IcedDouble x = minValues.get(e.getKey());
          if (x != null) {
            x._val=Math.min(e.getValue()._val, x._val);
          } else {
            minValues.put(e.getKey(), e.getValue());
          }
        }
        for (Map.Entry<IcedLong,IcedDouble> e : mrt.maxValues.entrySet()) {
          IcedDouble x = maxValues.get(e.getKey());
          if (x != null) {
            x._val=Math.max(e.getValue()._val, x._val);
          } else {
            maxValues.put(e.getKey(), e.getValue());
          }
        }
      }
    }

    // --------------------------------------------------------------------------
    // Build the next k-trees, which is trying to correct the residual error from
    // the prior trees.  From ESL2, page 387.  Step 2b ii, iii.
    private void buildNextKTrees(
            final IcedHashMap<IcedLong,IcedDouble> minValues,
            final IcedHashMap<IcedLong,IcedDouble> maxValues
    ) {
      // We're going to build K (nclass) trees - each focused on correcting
      // errors for a single class.
      final DTree[] ktrees = new DTree[_nclass];
      
      // Initial set of histograms.  All trees; one leaf per tree (the root
      // leaf); all columns
      DHistogram hcs[][][] = new DHistogram[_nclass][1/*just root leaf*/][_ncols];

      // Adjust real bins for the top-levels
      int adj_nbins = Math.max(_parms._nbins_top_level,_parms._nbins);

      for( int k=0; k<_nclass; k++ ) {
        // Initially setup as-if an empty-split had just happened
        if( _model._output._distribution[k] != 0 ) {
          // The Boolean Optimization
          // This optimization assumes the 2nd tree of a 2-class system is the
          // inverse of the first.  This is false for DRF (and true for GBM) -
          // DRF picks a random different set of columns for the 2nd tree.
          if( k==1 && _nclass==2 ) continue;
          ktrees[k] = new DTree(_train._names,_ncols,(char)_parms._nbins,(char)_parms._nbins_cats, (char)_nclass,_parms._min_rows);
          new GBMUndecidedNode(ktrees[k],-1,DHistogram.initialHist(_train,_ncols,adj_nbins,_parms._nbins_cats,hcs[k][0]) ); // The "root" node
        }
      }
      int[] leafs = new int[_nclass]; // Define a "working set" of leaf splits, from here to tree._len

      // ----
      // ESL2, page 387.  Step 2b ii.
      // One Big Loop till the ktrees are of proper depth.
      // Adds a layer to the trees each pass.
      int depth=0;
      for( ; depth<_parms._max_depth; depth++ ) {
        if( !isRunning() ) return;
        hcs = buildLayer(_train, adj_nbins, _parms._nbins_cats, ktrees, leafs, hcs, false, _parms._build_tree_one_node);
        // If we did not make any new splits, then the tree is split-to-death
        if( hcs == null ) break;
      }

      // Each tree bottomed-out in a DecidedNode; go 1 more level and insert
      // LeafNodes to hold predictions.
      for( int k=0; k<_nclass; k++ ) {
        DTree tree = ktrees[k];
        if( tree == null ) continue;
        int leaf = leafs[k] = tree.len();
        for( int nid=0; nid<leaf; nid++ ) {
          if( tree.node(nid) instanceof DecidedNode ) {
            DecidedNode dn = tree.decided(nid);
            if( dn._split._col == -1 ) { // No decision here, no row should have this NID now
              if( nid==0 )               // Handle the trivial non-splitting tree
                new GBMLeafNode(tree,-1,0);
              continue;
            }
            for( int i=0; i<dn._nids.length; i++ ) {
              int cnid = dn._nids[i];
              if( cnid == -1 || // Bottomed out (predictors or responses known constant)
                  tree.node(cnid) instanceof UndecidedNode || // Or chopped off for depth
                  (tree.node(cnid) instanceof DecidedNode &&  // Or not possible to split
                   ((DecidedNode)tree.node(cnid))._split.col()==-1) )
                dn._nids[i] = new GBMLeafNode(tree,nid).nid(); // Mark a leaf here
            }
          }
        }
      } // -- k-trees are done

      // ----
      // ESL2, page 387.  Step 2b iii.  Compute the gammas, and store them back
      // into the tree leaves.  Includes learn_rate.
      // For classification (bernoulli):
      //    gamma_i = sum (w_i * res_i) / sum (w_i*p_i*(1 - p_i)) where p_i = y_i - res_i
      // For classification (multinomial):
      //    gamma_i_k = (nclass-1)/nclass * (sum res_i / sum (|res_i|*(1-|res_i|)))
      // For regression (gaussian):
      //    gamma_i = sum res_i / count(res_i)
      GammaPass gp = new GammaPass(ktrees, leafs, _parms._distribution, minValues, maxValues).doAll(_train);
      double m1class = _nclass > 1 && _parms._distribution != Distribution.Family.bernoulli ? (double)(_nclass-1)/_nclass : 1.0; // K-1/K for multinomial
      for( int k=0; k<_nclass; k++ ) {
        final DTree tree = ktrees[k];
        if( tree == null ) continue;
        for( int i=0; i<tree._len-leafs[k]; i++ ) {
          float gf = (float)(_parms._learn_rate * m1class * gp.gamma(k,i));
          // In the multinomial case, check for very large values (which will get exponentiated later)
          // Note that gss can be *zero* while rss is non-zero - happens when some rows in the same
          // split are perfectly predicted true, and others perfectly predicted false.
          if( _parms._distribution == Distribution.Family.multinomial ) {
            if     ( gf >  1e4 ) gf =  1e4f; // Cap prediction, will already overflow during Math.exp(gf)
            else if( gf < -1e4 ) gf = -1e4f;
          }
          assert !Float.isNaN(gf) && !Float.isInfinite(gf);
          ((LeafNode) tree.node(leafs[k] + i))._pred = gf;
        }
      }

      // ----
      // ESL2, page 387.  Step 2b iv.  Cache the sum of all the trees, plus the
      // new tree, in the 'tree' columns.  Also, zap the NIDs for next pass.
      // Tree <== f(Tree)
      // Nids <== 0
      new MRTask() {
        @Override public void map( Chunk chks[] ) {
          // For all tree/klasses
          for( int k=0; k<_nclass; k++ ) {
            final DTree tree = ktrees[k];
            if( tree == null ) continue;
            final Chunk nids = chk_nids(chks,k);
            final Chunk ct   = chk_tree(chks,k);
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
      }.doAll(_train);

      // Grow the model by K-trees
      _model._output.addKTrees(ktrees);
    }

    // Set terminal node estimates (gamma)
    // ESL2, page 387.  Step 2b iii.
    // Nids <== f(Nids)
    private class GammaPass extends MRTask<GammaPass> {
      final DTree _trees[]; // Read-only, shared (except at the histograms in the Nodes)
      final int   _leafs[]; // Number of active leaves (per tree)
      final Distribution.Family _dist;
      private double _num[/*tree/klass*/][/*tree-relative node-id*/];
      private double _denom[/*tree/klass*/][/*tree-relative node-id*/];
      final IcedHashMap<IcedLong,IcedDouble> _minValues;
      final IcedHashMap<IcedLong,IcedDouble> _maxValues;

      double gamma(int tree, int nid) {
        double g = _denom[tree][nid] == 0 ? 0 : _num[tree][nid]/ _denom[tree][nid];
        assert(!Double.isInfinite(g)) : "numeric overflow";
        assert(!Double.isNaN(g)) : "numeric overflow";
        if (_dist == Distribution.Family.poisson
                || _dist == Distribution.Family.gamma
                || _dist == Distribution.Family.tweedie )
        {
          double gamma = new Distribution(_dist, _parms._tweedie_power).link(g);

          // special case for overflows: Set to
          if (_minValues != null && _maxValues != null) {
            IcedLong nidx = new IcedLong(nid);
            if (_minValues.get(nidx) != null && _maxValues.get(nidx) != null) {
              double min = _minValues.get(nidx)._val;
              if (min + gamma < Distribution.MIN_LOG) {
                gamma = Distribution.MIN_LOG - min;
              }
              double max = _maxValues.get(nidx)._val;
              if (max + gamma > Distribution.MAX_LOG) {
                gamma = Distribution.MAX_LOG - max;
              }
            }
          }
          return gamma;
        } else {
          return g; //bernoulli/multinomial - leave alone //TODO: Check (bernoulli link won't be able to handle 0 or 1)
        }
      }

      GammaPass(DTree trees[],
                int leafs[],
                Distribution.Family distribution,
                final IcedHashMap<IcedLong,IcedDouble> minValues,
                final IcedHashMap<IcedLong,IcedDouble> maxValues
      ) {
        _leafs=leafs;
        _trees=trees;
        _dist = distribution;
        _minValues = minValues;
        _maxValues = maxValues;
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
          Distribution dist = new Distribution(_parms._distribution, _parms._tweedie_power);
          for( int row=0; row<nids._len; row++ ) { // For all rows
            int nid = (int)nids.at8(row);          // Get Node to decide from
            if( nid < 0 ) continue;                // Missing response
            if( tree.node(nid) instanceof UndecidedNode ) // If we bottomed out the tree
              nid = tree.node(nid)._pid;                  // Then take parent's decision
            DecidedNode dn = tree.decided(nid);           // Must have a decision point
            if( dn._split._col == -1 )                    // Unable to decide?
              dn = tree.decided(dn._pid);  // Then take parent's decision
            int leafnid = dn.ns(chks,row); // Decide down to a leafnode
            assert leaf <= leafnid && leafnid < tree._len :
                    "leaf: " + leaf + " leafnid: " + leafnid + " tree._len: " + tree._len + "\ndn: " + dn;
            assert tree.node(leafnid) instanceof LeafNode;
            // Note: I can which leaf/region I end up in, but I do not care for
            // the prediction presented by the tree.  For GBM, we compute the
            // sum-of-residuals (and sum/abs/mult residuals) for all rows in the
            // leaf, and get our prediction from that.
            nids.set(row, leafnid);
            assert !ress.isNA(row);

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

    @Override protected GBMModel makeModel( Key modelKey, GBMModel.GBMParameters parms, double mse_train, double mse_valid ) {
      return new GBMModel(modelKey,parms,new GBMModel.GBMOutput(GBM.this,mse_train,mse_valid));
    }

  }

  @Override protected DecidedNode makeDecided( UndecidedNode udn, DHistogram hs[] ) {
    return new GBMDecidedNode(udn,hs);
  }
  
  // ---
  // GBM DTree decision node: same as the normal DecidedNode, but
  // specifies a decision algorithm given complete histograms on all
  // columns.  GBM algo: find the lowest error amongst *all* columns.
  static class GBMDecidedNode extends DecidedNode {
    GBMDecidedNode( UndecidedNode n, DHistogram[] hs ) { super(n,hs); }
    @Override public UndecidedNode makeUndecidedNode(DHistogram[] hs ) {
      return new GBMUndecidedNode(_tree,_nid,hs);
    }
  
    // Find the column with the best split (lowest score).  Unlike RF, GBM
    // scores on all columns and selects splits on all columns.
    @Override public DTree.Split bestCol( UndecidedNode u, DHistogram[] hs ) {
      DTree.Split best = new DTree.Split(-1,-1,null,(byte)0,Double.MAX_VALUE,Double.MAX_VALUE,Double.MAX_VALUE,0L,0L,0,0);
      if( hs == null ) return best;
      for( int i=0; i<hs.length; i++ ) {
        if( hs[i]==null || hs[i].nbins() <= 1 ) continue;
        DTree.Split s = hs[i].scoreMSE(i,_tree._min_rows);
        if( s == null ) continue;
        if( s.se() < best.se() )
          best = s;
        if( s.se() <= 0 ) break; // No point in looking further!
      }
      return best;
    }
  }
  
  // ---
  // GBM DTree undecided node: same as the normal UndecidedNode, but specifies
  // a list of columns to score on now, and then decide over later.
  // GBM algo: use all columns
  static class GBMUndecidedNode extends UndecidedNode {
    GBMUndecidedNode( DTree tree, int pid, DHistogram hs[] ) { super(tree,pid,hs); }
    // Randomly select mtry columns to 'score' in following pass over the data.
    // In GBM, we use all columns (as opposed to RF, which uses a random subset).
    @Override public int[] scoreCols( DHistogram[] hs ) { return null; }
  }
  
  // ---
  static class GBMLeafNode extends LeafNode {
    GBMLeafNode( DTree tree, int pid ) { super(tree,pid); }
    GBMLeafNode( DTree tree, int pid, int nid ) { super(tree, pid, nid); }
    // Insert just the predictions: a single byte/short if we are predicting a
    // single class, or else the full distribution.
    @Override protected AutoBuffer compress(AutoBuffer ab) { assert !Double.isNaN(_pred); return ab.put4f(_pred); }
    @Override protected int size() { return 4; }
  }

  // Read the 'tree' columns, do model-specific math and put the results in the
  // fs[] array, and return the sum.  Dividing any fs[] element by the sum
  // turns the results into a probability distribution.
  @Override protected double score1( Chunk chks[], double weight, double offset, double fs[/*nclass*/], int row ) {
    double f = chk_tree(chks,0).atd(row) + offset;
    double p = new Distribution(_parms._distribution, _parms._tweedie_power).linkInv(f);
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
      for( int k=0; k<_nclass; k++ )
        fs[k+1]=chk_tree(chks,k).atd(row);
      // Rescale to avoid Infinities; return sum(exp(data))
      return hex.genmodel.GenModel.log_rescale(fs);
    }
    else {
      return fs[0] = p;
    }
  }
}
