package hex.tree.gbm;

import hex.VarImp;
import hex.schemas.GBMV2;
import hex.tree.*;
import water.*;
import water.fvec.Chunk;
import water.util.Log;
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
    return start(new GBMDriver(), _parms._ntrees/*work for progress bar*/);
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
      error("_learn_rate", "learn_rate must be between 0 and 1");
    if( _parms._loss == GBMModel.GBMParameters.Family.bernoulli ) {
      if( _nclass != 2 )
        error("_loss","Bernoulli requires the response to be a 2-class categorical");
      // Bernoulli: initial prediction is log( mean(y)/(1-mean(y)) )
      double mean = _response.mean();
      _initialPrediction = Math.log(mean/(1.0f-mean));
    }
  }

  // ----------------------
  private class GBMDriver extends Driver {

    /** Sum of variable empirical improvement in squared-error. The value is not scaled! */
    private transient float[/*nfeatures*/] _improvPerVar;

    @Override protected void buildModel() {
      // Initialize gbm-specific data structures
      if( _parms._importance ) _improvPerVar = new float[_nclass];

      // Reconstruct the working tree state from the checkopoint
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
            // wk.set0(row, 1.0f/(1f+Math.exp(-tr.at0(row))) ); // Prob_1
            wk.set0(row, 1.0f/(1f+Math.exp(tr.at0(row))) );     // Prob_0
        } else if( _nclass > 1 ) {       // Classification
          float fs[] = new float[_nclass+1];
          for( int row=0; row<ys._len; row++ ) {
            float sum = score1(chks,fs,row);
            if( Float.isInfinite(sum) ) // Overflow (happens for constant responses)
              for( int k=0; k<_nclass; k++ )
                chk_work(chks,k).set0(row,Float.isInfinite(fs[k+1])?1.0f:0.0f);
            else
              for( int k=0; k<_nclass; k++ ) // Save as a probability distribution
                chk_work(chks,k).set0(row,fs[k+1]/sum);
          }
        } else {                  // Regression
          Chunk tr = chk_tree(chks,0); // Prior tree sums
          Chunk wk = chk_work(chks,0); // Predictions
          for( int row=0; row<ys._len; row++ )
            wk.set0(row,(float)tr.at0(row));
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
            if( ys.isNA0(row) ) continue;
            int y = (int)ys.at80(row); // zero-based response variable
            Chunk wk = chk_work(chks,0);
            // wk.set0(row, y-(float)wk.at0(row));  // wk.at0(row) is Prob_1
            wk.set0(row, y-1f+(float)wk.at0(row));  // wk.at0(row) is Prob_0
          }
        } else if( _nclass > 1 ) {       // Classification

          for( int row=0; row<ys._len; row++ ) {
            if( ys.isNA0(row) ) continue;
            int y = (int)ys.at80(row); // zero-based response variable
            // Actual is '1' for class 'y' and '0' for all other classes
            for( int k=0; k<_nclass; k++ ) {
              if( _model._output._distribution[k] != 0 ) {
                Chunk wk = chk_work(chks,k);
                wk.set0(row, (y==k?1f:0f)-(float)wk.at0(row) );
              }
            }
          }

        } else {                  // Regression
          Chunk wk = chk_work(chks,0); // Prediction==>Residuals
          for( int row=0; row<ys._len; row++ )
            wk.set0(row, (float)(ys.at0(row)-wk.at0(row)) );
        }
      }
    }

    // --------------------------------------------------------------------------
    // Build the next k-trees, which is trying to correct the residual error from
    // the prior trees.  From LSE2, page 387.  Step 2b ii, iii.
    private void buildNextKTrees() {

      //_model._output.addKTrees(trees);
      throw H2O.unimpl();
    }

    @Override protected GBMModel makeModel( Key modelKey, GBMModel.GBMParameters parms ) {
      return new GBMModel(modelKey,parms,new GBMModel.GBMOutput(GBM.this));
    }

  }

  // No rows out-of-bag, all rows are in-bag and used for training
  @Override protected boolean outOfBagRow(Chunk[] chks, int row) { return false; }

  @Override protected VarImp doVarImpCalc(boolean scale) { throw H2O.unimpl(); }

  // Read the 'tree' columns, do model-specific math and put the results in the
  // fs[] array, and return the sum.  Dividing any fs[] element by the sum
  // turns the results into a probability distribution.
  @Override protected float score1( Chunk chks[], float fs[/*nclass*/], int row ) {
    if( _parms._loss == GBMModel.GBMParameters.Family.bernoulli ) {
      fs[1] = 1.0f/(float)(1f+Math.exp(chk_tree(chks,0).at0(row)));
      fs[2] = 1f-fs[1];
      return fs[1]+fs[2];
    }
    if( _nclass == 1 )          // Classification?
      return fs[0]=(float)chk_tree(chks,0).at0(row); // Regression.
    if( _nclass == 2 ) {        // The Boolean Optimization
      // This optimization assumes the 2nd tree of a 2-class system is the
      // inverse of the first.  Fill in the missing tree
      fs[1] = (float)Math.exp(chk_tree(chks,0).at0(row));
      fs[2] = 1.0f/fs[1]; // exp(-d) === 1/d
      return fs[1]+fs[2];
    }
    float sum=0;
    for( int k=0; k<_nclass; k++ ) // Sum across of likelyhoods
      sum+=(fs[k+1]=(float)Math.exp(chk_tree(chks,k).at0(row)));
    return sum;
  }

}
