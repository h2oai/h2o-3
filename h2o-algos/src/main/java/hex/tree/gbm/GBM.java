package hex.tree.gbm;

import hex.Model;
import hex.schemas.GBMV2;
import hex.tree.*;
import hex.tree.DTree.DecidedNode;
import hex.tree.DTree.LeafNode;
import hex.tree.DTree.UndecidedNode;
import water.*;
import water.fvec.Chunk;
import water.fvec.Vec;
import water.util.Log;
import water.util.Timer;
import water.util.ArrayUtils;

/** Gradient Boosted Trees
 *
 *  Based on "Elements of Statistical Learning, Second Edition, page 387"
 */
public class GBM extends SharedTree<GBMModel,GBMModel.GBMParameters,GBMModel.GBMOutput> {
  @Override public Model.ModelCategory[] can_build() {
    return new Model.ModelCategory[]{
      Model.ModelCategory.Regression,
      Model.ModelCategory.Binomial,
      Model.ModelCategory.Multinomial,
    };
  }

  // Called from an http request
  public GBM( GBMModel.GBMParameters parms) { super("GBM",parms); init(false); }

  @Override public GBMV2 schema() { return new GBMV2(); }

  /** Start the GBM training Job on an F/J thread. */
  @Override public Job<GBMModel> trainModel() {
    return start(new GBMDriver(), _parms._ntrees/*work for progress bar*/);
  }

  @Override public Vec vresponse() { return super.vresponse() == null ? response() : super.vresponse(); }

  /** Initialize the ModelBuilder, validating all arguments and preparing the
   *  training frame.  This call is expected to be overridden in the subclasses
   *  and each subclass will start with "super.init();".  This call is made
   *  by the front-end whenever the GUI is clicked, and needs to be fast;
   *  heavy-weight prep needs to wait for the trainModel() call.
   *
   *  Validate the learning rate and loss family. */
  @Override public void init(boolean expensive) {
    super.init(expensive);

    switch( _parms._loss ) {
    case AUTO:               // Guess the loss by examining the response column
      _parms._convert_to_enum = couldBeBool(_response);
      break; 
    case bernoulli:
      if( _parms._convert_to_enum && _nclass != 2 && !couldBeBool(_response))
        error("_loss", "Bernoulli requires the response to be a 2-class categorical");
      else if( _response != null ) {
        // Bernoulli: initial prediction is log( mean(y)/(1-mean(y)) )
        double mean = _response.mean();
        _initialPrediction = Math.log(mean / (1.0f - mean));
      }
      _parms._convert_to_enum = true;
      break;
    case multinomial:  
      _parms._convert_to_enum = true;   
      break;
    case gaussian:     
      if( _nclass != 1 ) error("_loss","Gaussian requires the response to be numeric");
      _parms._convert_to_enum = false;  
      break;
    default:
      error("_loss","Loss must be specified");
    }
    
    if( !(0. < _parms._learn_rate && _parms._learn_rate <= 1.0) )
      error("_learn_rate", "learn_rate must be between 0 and 1");
  }
  private static boolean couldBeBool(Vec v) { return v != null && v.isInt() && v.min()+1==v.max(); }

  // ----------------------
  private class GBMDriver extends Driver {

    @Override protected void buildModel() {
      // For GBM multinomial, initial predictions are class-distributions
      // For GBM, keep the original zero guesses
      //if( _nclass > 2 ) {
      //  for( int c=0; c<_nclass; c++ ) {
      //    final double init = _model._output._priorClassDist[c];
      //    new MRTask() {
      //      @Override public void map(Chunk tree) { for( int i=0; i<tree._len; i++ ) tree.set(i, init); }
      //    }.doAll(vec_tree(_train,c));
      //  }
      //  throw H2O.unimpl("untested");
      //}

      // Reconstruct the working tree state from the checkpoint
      if( _parms._checkpoint ) {
        Timer t = new Timer();
        new ResidualsCollector(_ncols, _nclass, _model._output._treeKeys).doAll(_train);
        Log.info("Reconstructing tree residuals stats from checkpointed model took " + t);
      }

      // Loop over the K trees
      for( int tid=0; tid<_parms._ntrees; tid++) {
        // During first iteration model contains 0 trees, then 1-tree, ...
        // No need to score a checkpoint with no extra trees added
        if( tid!=0 || !_parms._checkpoint ) // do not make initial scoring if model already exist
          doScoringAndSaveModel(false, false, false);

        // ESL2, page 387
        // Step 2a: Compute prediction (prob distribution) from prior tree results:
        //   Work <== f(Tree)
        new ComputeProb().doAll(_train);

        // ESL2, page 387
        // Step 2b i: Compute residuals from the prediction (probability distribution)
        //   Work <== f(Work)
        new ComputeRes().doAll(_train);

        // ESL2, page 387, Step 2b ii, iii, iv
        Timer kb_timer = new Timer();
        buildNextKTrees();
        Log.info((tid+1) + ". tree was built in " + kb_timer.toString());
        GBM.this.update(1);
        if( !isRunning() ) return; // If canceled during building, do not bulkscore
      }
      // Final scoring (skip if job was cancelled)
      doScoringAndSaveModel(true, false, false);
    }

    // --------------------------------------------------------------------------
    // Compute Prediction from prior tree results.
    // Classification (multinomial): Probability Distribution of loglikelyhoods
    //   Prob_k = exp(Work_k)/sum_all_K exp(Work_k)
    // Classification (bernoulli): Probability of y = 1 given logit link function
    //   Prob_0 = 1/(1 + exp(Work)), Prob_1 = 1/(1 + exp(-Work))
    // Regression: Just prior tree results
    // Work <== f(Tree)
    class ComputeProb extends MRTask<ComputeProb> {
      @Override public void map( Chunk chks[] ) {
        Chunk ys = chk_resp(chks);
        if( _parms._loss == GBMModel.GBMParameters.Family.bernoulli ) {
          Chunk tr = chk_tree(chks,0);
          Chunk wk = chk_work(chks,0);
          for( int row = 0; row < ys._len; row++)
            // wk.set(row, 1.0f/(1f+Math.exp(-tr.atd(row))) ); // Prob_1
            wk.set(row, 1.0f / (1f + Math.exp(tr.atd(row))));     // Prob_0
        } else if( _nclass > 1 ) {       // Classification
          float fs[] = new float[_nclass+1];
          for( int row=0; row<ys._len; row++ ) {
            float sum = score1(chks,fs,row);
            if( Float.isInfinite(sum) ) // Overflow (happens for constant responses)
              for( int k=0; k<_nclass; k++ )
                chk_work(chks,k).set(row,Float.isInfinite(fs[k+1])?1.0f:0.0f);
            else
              for( int k=0; k<_nclass; k++ ) // Save as a probability distribution
                chk_work(chks,k).set(row,fs[k+1]/sum);
          }
        } else {                  // Regression
          Chunk tr = chk_tree(chks,0); // Prior tree sums
          Chunk wk = chk_work(chks,0); // Predictions
          for( int row=0; row<ys._len; row++ )
            wk.set(row,(float)tr.atd(row));
        }
      }
    }

    // --------------------------------------------------------------------------
    // Compute Residuals from Actuals
    // Work <== f(Work)
    class ComputeRes extends MRTask<ComputeRes> {
      @Override public void map( Chunk chks[] ) {
        Chunk ys = chk_resp(chks);
        if( _parms._loss == GBMModel.GBMParameters.Family.bernoulli ) {
          for(int row = 0; row < ys._len; row++) {
            if( ys.isNA(row) ) continue;
            int y = (int)ys.at8(row); // zero-based response variable
            Chunk wk = chk_work(chks,0);
            // wk.set(row, y-(float)wk.atd(row));  // wk.atd(row) is Prob_1
            wk.set(row, y-1f+(float)wk.atd(row));  // wk.atd(row) is Prob_0
          }
        } else if( _nclass > 1 ) {       // Classification

          for( int row=0; row<ys._len; row++ ) {
            if( ys.isNA(row) ) continue;
            int y = (int)ys.at8(row); // zero-based response variable
            // Actual is '1' for class 'y' and '0' for all other classes
            for( int k=0; k<_nclass; k++ ) {
              if( _model._output._distribution[k] != 0 ) {
                Chunk wk = chk_work(chks,k);
                wk.set(row, (y==k?1f:0f)-(float)wk.atd(row) );
              }
            }
          }

        } else {                  // Regression
          Chunk wk = chk_work(chks,0); // Prediction==>Residuals
          for( int row=0; row<ys._len; row++ )
            wk.set(row, (float)(ys.atd(row)-wk.atd(row)) );
        }
      }
    }

    // --------------------------------------------------------------------------
    // Build the next k-trees, which is trying to correct the residual error from
    // the prior trees.  From ESL2, page 387.  Step 2b ii, iii.
    private void buildNextKTrees() {
      // We're going to build K (nclass) trees - each focused on correcting
      // errors for a single class.
      final DTree[] ktrees = new DTree[_nclass];
      
      // Initial set of histograms.  All trees; one leaf per tree (the root
      // leaf); all columns
      DHistogram hcs[][][] = new DHistogram[_nclass][1/*just root leaf*/][_ncols];

      // Adjust nbins for the top-levels
      final int top_level_extra_bins = 1<<10;
      int nbins = Math.max(top_level_extra_bins,_parms._nbins);

      for( int k=0; k<_nclass; k++ ) {
        // Initially setup as-if an empty-split had just happened
        if( _model._output._distribution[k] != 0 ) {
          // The Boolean Optimization
          // This optimization assumes the 2nd tree of a 2-class system is the
          // inverse of the first.  This is false for DRF (and true for GBM) -
          // DRF picks a random different set of columns for the 2nd tree.
          if( k==1 && _nclass==2 ) continue;
          ktrees[k] = new DTree(_train._names,_ncols,(char)_parms._nbins,(char)_nclass,_parms._min_rows);
          new GBMUndecidedNode(ktrees[k],-1,DHistogram.initialHist(_train,_ncols,nbins,hcs[k][0],false) ); // The "root" node
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

        hcs = buildLayer(_train, nbins, ktrees, leafs, hcs, false, false);

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
            for( int i=0; i<dn._nids.length; i++ ) {
              int cnid = dn._nids[i];
              if( cnid == -1 || // Bottomed out (predictors or responses known constant)
                  tree.node(cnid) instanceof UndecidedNode || // Or chopped off for depth
                  (tree.node(cnid) instanceof DecidedNode &&  // Or not possible to split
                   ((DecidedNode)tree.node(cnid))._split.col()==-1) )
                dn._nids[i] = new GBMLeafNode(tree,nid).nid(); // Mark a leaf here
            }
            // Handle the trivial non-splitting tree
            if( nid==0 && dn._split.col() == -1 )
              new GBMLeafNode(tree,-1,0);
          }
        }
      } // -- k-trees are done

      // ----
      // ESL2, page 387.  Step 2b iii.  Compute the gammas, and store them back
      // into the tree leaves.  Includes learn_rate.
      // For classification (bernoulli):
      //    gamma_i = sum res_i / sum p_i*(1 - p_i) where p_i = y_i - res_i
      // For classification (multinomial):
      //    gamma_i_k = (nclass-1)/nclass * (sum res_i / sum (|res_i|*(1-|res_i|)))
      // For regression (gaussian):
      //    gamma_i = sum res_i / count(res_i)
      GammaPass gp = new GammaPass(ktrees,leafs,_parms._loss == GBMModel.GBMParameters.Family.bernoulli).doAll(_train);
      double m1class = _nclass > 1 && _parms._loss != GBMModel.GBMParameters.Family.bernoulli ? (double)(_nclass-1)/_nclass : 1.0; // K-1/K for multinomial
      for( int k=0; k<_nclass; k++ ) {
        final DTree tree = ktrees[k];
        if( tree == null ) continue;
        for( int i=0; i<tree._len-leafs[k]; i++ ) {
          double g = gp._gss[k][i] == 0 // Constant response?
            ? (gp._rss[k][i]==0?0:1000) // Cap (exponential) learn, instead of dealing with Inf
            : _parms._learn_rate*m1class*gp._rss[k][i]/gp._gss[k][i];
          assert !Double.isNaN(g);
          ((LeafNode)tree.node(leafs[k]+i))._pred = g;
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
              ct.set(row, (float)(ct.atd(row) + (float) ((LeafNode)tree.node(nid))._pred));
              nids.set(row, 0);
            }
          }
        }
      }.doAll(_train);

      // Collect leaves stats
      for (int i=0; i<ktrees.length; i++)
        if( ktrees[i] != null )
          ktrees[i]._leaves = ktrees[i].len() - leafs[i];
      // DEBUG: Print the generated K trees
      //printGenerateTrees(ktrees);
      // Grow the model by K-trees
      _model._output.addKTrees(ktrees);
    }

    // ---
    // ESL2, page 387.  Step 2b iii.
    // Nids <== f(Nids)
    private class GammaPass extends MRTask<GammaPass> {
      final DTree _trees[]; // Read-only, shared (except at the histograms in the Nodes)
      final int   _leafs[]; // Number of active leaves (per tree)
      final boolean _isBernoulli;
      // Per leaf: sum(res);
      double _rss[/*tree/klass*/][/*tree-relative node-id*/];
      // Per leaf: multinomial: sum(|res|*1-|res|), gaussian: sum(1), bernoulli: sum((y-res)*(1-y+res))
      double _gss[/*tree/klass*/][/*tree-relative node-id*/];
      GammaPass(DTree trees[], int leafs[], boolean isBernoulli) { _leafs=leafs; _trees=trees; _isBernoulli = isBernoulli; }
      @Override public void map( Chunk[] chks ) {
        _gss = new double[_nclass][];
        _rss = new double[_nclass][];
        final Chunk resp = chk_resp(chks); // Response for this frame

        // For all tree/klasses
        for( int k=0; k<_nclass; k++ ) {
          final DTree tree = _trees[k];
          final int   leaf = _leafs[k];
          if( tree == null ) continue; // Empty class is ignored

          // A leaf-biased array of all active Tree leaves.
          final double gs[] = _gss[k] = new double[tree._len-leaf];
          final double rs[] = _rss[k] = new double[tree._len-leaf];
          final Chunk nids = chk_nids(chks,k); // Node-ids  for this tree/class
          final Chunk ress = chk_work(chks,k); // Residuals for this tree/class

          // If we have all constant responses, then we do not split even the
          // root and the residuals should be zero.
          if( tree.root() instanceof LeafNode ) continue;
          for( int row=0; row<nids._len; row++ ) { // For all rows
            int nid = (int)nids.at8(row);         // Get Node to decide from
            if( nid < 0 ) continue;                // Missing response
            if( tree.node(nid) instanceof UndecidedNode ) // If we bottomed out the tree
              nid = tree.node(nid)._pid;                  // Then take parent's decision
            DecidedNode dn = tree.decided(nid);           // Must have a decision point
            if( dn._split._col == -1 )                    // Unable to decide?
              dn = tree.decided(nid = dn._pid); // Then take parent's decision
            int leafnid = dn.ns(chks,row); // Decide down to a leafnode
            assert leaf <= leafnid && leafnid < tree._len;
            assert tree.node(leafnid) instanceof LeafNode;
            // Note: I can which leaf/region I end up in, but I do not care for
            // the prediction presented by the tree.  For GBM, we compute the
            // sum-of-residuals (and sum/abs/mult residuals) for all rows in the
            // leaf, and get our prediction from that.
            nids.set(row, leafnid);
            assert !ress.isNA(row);

            // Compute numerator (rs) and denominator (gs) of gamma
            double res = ress.atd(row);
            double ares = Math.abs(res);
            if( _isBernoulli ) {
              double prob = resp.atd(row) - res;
              gs[leafnid-leaf] += prob*(1-prob);
            } else
              gs[leafnid-leaf] += _nclass > 1 ? ares*(1-ares) : 1;
            rs[leafnid-leaf] += res;
          }
        }
      }
      @Override public void reduce( GammaPass gp ) {
        ArrayUtils.add(_gss,gp._gss);
        ArrayUtils.add(_rss,gp._rss);
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
        if( best == null || s.se() < best.se() )
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
    @Override protected AutoBuffer compress(AutoBuffer ab) { assert !Double.isNaN(_pred); return ab.put4f((float)_pred); }
    @Override protected int size() { return 4; }
  }

  // Read the 'tree' columns, do model-specific math and put the results in the
  // fs[] array, and return the sum.  Dividing any fs[] element by the sum
  // turns the results into a probability distribution.
  @Override protected float score1( Chunk chks[], float fs[/*nclass*/], int row ) {
    if( _parms._loss == GBMModel.GBMParameters.Family.bernoulli ) {
      fs[1] = 1.0f/(float)(1f+Math.exp(chk_tree(chks,0).atd(row)));
      fs[2] = 1f-fs[1];
      return fs[1]+fs[2];
    }
    if( _nclass == 1 )          // Classification?
      return fs[0]=(float)chk_tree(chks,0).atd(row); // Regression.
    if( _nclass == 2 ) {        // The Boolean Optimization
      // This optimization assumes the 2nd tree of a 2-class system is the
      // inverse of the first.  Fill in the missing tree
      fs[1] = (float)Math.exp(chk_tree(chks,0).atd(row));
      fs[2] = 1.0f/fs[1]; // exp(-d) === 1/exp(d)
      return fs[1]+fs[2];
    }
    float sum=0;
    for( int k=0; k<_nclass; k++ ) // Sum across of likelyhoods
      sum+=(fs[k+1]=(float)Math.exp(chk_tree(chks,k).atd(row)));
    return sum;
  }

}
