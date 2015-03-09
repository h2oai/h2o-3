package hex.tree.drf;

import hex.Model;
import hex.schemas.DRFV2;
import hex.tree.*;
import hex.tree.DTree.DecidedNode;
import hex.tree.DTree.LeafNode;
import hex.tree.DTree.UndecidedNode;
import static hex.tree.drf.TreeMeasuresCollector.asSSE;
import static hex.tree.drf.TreeMeasuresCollector.asVotes;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;
import water.util.ModelUtils;
import water.util.Timer;

import java.util.Arrays;
import java.util.Random;

/** Gradient Boosted Trees
 *
 *  Based on "Elements of Statistical Learning, Second Edition, page 387"
 */
public class DRF extends SharedTree<hex.tree.drf.DRFModel, hex.tree.drf.DRFModel.DRFParameters, hex.tree.drf.DRFModel.DRFOutput> {
  protected int _mtry;
  protected long _actual_seed;

  @Override public Model.ModelCategory[] can_build() {
    return new Model.ModelCategory[]{
      Model.ModelCategory.Regression,
      Model.ModelCategory.Binomial,
      Model.ModelCategory.Multinomial,
    };
  }

  static final boolean DEBUG_DETERMINISTIC = false; //for debugging only


  // Called from an http request
  public DRF( hex.tree.drf.DRFModel.DRFParameters parms) { super("DRF",parms); init(false); }

  @Override public DRFV2 schema() { return new DRFV2(); }

  /** Start the DRF training Job on an F/J thread. */
  @Override public Job<hex.tree.drf.DRFModel> trainModel() {
    return start(new DRFDriver(), _parms._ntrees/*work for progress bar*/);
  }

  @Override public Vec vresponse() { return super.vresponse() == null ? response() : super.vresponse(); }

  /** Initialize the ModelBuilder, validating all arguments and preparing the
   *  training frame.  This call is expected to be overridden in the subclasses
   *  and each subclass will start with "super.init();".  This call is made
   *  by the front-end whenever the GUI is clicked, and needs to be fast;
   *  heavy-weight prep needs to wait for the trainModel() call.
   */
  @Override public void init(boolean expensive) {
    super.init(expensive);

    // Initialize local variables
    if (!(0.0 < _parms._sample_rate && _parms._sample_rate <= 1.0)) throw new IllegalArgumentException("Sample rate should be interval (0,1> but it is " + _parms._sample_rate);
    if (DEBUG_DETERMINISTIC && _parms._seed == -1) _parms._seed = 0x1321e74a0192470cL; // fixed version of seed
    else if (_parms._seed == -1) _actual_seed = new Random().nextLong(); else _actual_seed = _parms._seed;
    if (_parms._sample_rate==1f && _valid!=null)
      Log.warn("Sample rate is 100% and no validation dataset is specified. There are no OOB data to compute out-of-bag error estimation!");
    if (!_parms._convert_to_enum && _parms._do_grpsplit) {
      Log.info("Group splitting not supported for DRF regression. Forcing group splitting to false.");
      _parms._do_grpsplit = false;
    }




  }

  // A standard DTree with a few more bits.  Support for sampling during
  // training, and replaying the sample later on the identical dataset to
  // e.g. compute OOBEE.
  static class DRFTree extends DTree {
    final int _mtrys;           // Number of columns to choose amongst in splits
    final long _seeds[];        // One seed for each chunk, for sampling
    final transient Random _rand; // RNG for split decisions & sampling
    DRFTree( Frame fr, int ncols, char nbins, char nclass, int min_rows, int mtrys, long seed ) {
      super(fr._names, ncols, nbins, nclass, min_rows, seed);
      _mtrys = mtrys;
      _rand = new Random(seed);
      _seeds = new long[fr.vecs()[0].nChunks()];
      for( int i=0; i<_seeds.length; i++ )
        _seeds[i] = _rand.nextLong();
    }
    // Return a deterministic chunk-local RNG.  Can be kinda expensive.
    public Random rngForChunk( int cidx ) {
      long seed = _seeds[cidx];
      return new Random(seed);
    }
  }

  /** Fill work columns:
   *   - classification: set 1 in the corresponding wrk col according to row response
   *   - regression:     copy response into work column (there is only 1 work column)
   */
  private class SetWrkTask extends MRTask<SetWrkTask> {
    @Override public void map( Chunk chks[] ) {
      Chunk cy = chk_resp(chks);
      for( int i=0; i<cy._len; i++ ) {
        if( cy.isNA(i) ) continue;
        if (_parms._convert_to_enum) {
          int cls = (int)cy.at8(i);
          chk_work(chks,cls).set(i,1L);
        } else {
          float pred = (float) cy.atd(i);
          chk_work(chks,0).set(i,pred);
        }
      }
    }
  }

  // ----------------------
  private class DRFDriver extends Driver {
    protected int _ntreesFromCheckpoint;


    // --- Private data handled only on master node
    // Classification or Regression:
    // Tree votes/SSE of individual trees on OOB rows
    public transient TreeMeasuresCollector.TreeMeasures _treeMeasuresOnOOB;
    // Tree votes/SSE per individual features on permutated OOB rows
    public transient TreeMeasuresCollector.TreeMeasures[/*features*/] _treeMeasuresOnSOOB;
    // Variable importance beased on tree split decisions
    private transient float[/*nfeatures*/] _improvPerVar;

    private void initTreeMeasurements() {
      _improvPerVar = new float[_ncols];
      final int ntrees = _parms._ntrees;
      // Preallocate tree votes
      if (_model._output.isClassifier()) {
        _treeMeasuresOnOOB  = new TreeMeasuresCollector.TreeVotes(ntrees);
        _treeMeasuresOnSOOB = new TreeMeasuresCollector.TreeVotes[_ncols];
        for (int i=0; i<_ncols; i++) _treeMeasuresOnSOOB[i] = new TreeMeasuresCollector.TreeVotes(ntrees);
      } else {
        _treeMeasuresOnOOB  = new TreeMeasuresCollector.TreeSSE(ntrees);
        _treeMeasuresOnSOOB = new TreeMeasuresCollector.TreeSSE[_ncols];
        for (int i=0; i<_ncols; i++) _treeMeasuresOnSOOB[i] = new TreeMeasuresCollector.TreeSSE(ntrees);
      }
    }

    @Override protected void buildModel() {
      _mtry = (_parms._mtries==-1) ? // classification: mtry=sqrt(_ncols), regression: mtry=_ncols/3
              ( _parms._convert_to_enum ? Math.max((int)Math.sqrt(_ncols),1) : Math.max(_ncols/3,1))  : _parms._mtries;
      if (!(1 <= _mtry && _mtry <= _ncols)) throw new IllegalArgumentException("Computed mtry should be in interval <1,#cols> but it is " + _mtry);
      // Initialize TreeVotes for classification, MSE arrays for regression
      initTreeMeasurements();
      // Append number of trees participating in on-the-fly scoring
      _train.add("OUT_BAG_TREES", _response.makeZero());
      // Prepare working columns
      new SetWrkTask().doAll(_train);
      // If there was a check point recompute tree_<_> and oob columns based on predictions from previous trees
      // but only if OOB validation is requested.
      if (_valid==null && _parms._checkpoint) {
        Timer t = new Timer();
        // Compute oob votes for each output level
        new OOBScorer(_ncols, _nclass, _parms._sample_rate, _model._output._treeKeys).doAll(_train);
        Log.info("Reconstructing oob stats from checkpointed model took " + t);
      }


      // The RNG used to pick split columns
      Random rand = new Random(_actual_seed);
      // To be deterministic get random numbers for previous trees and
      // put random generator to the same state
      for (int i=0; i<_ntreesFromCheckpoint; i++) rand.nextLong();

      int tid;
      DTree[] ktrees = null;
      // Prepare tree statistics
      TreeStats tstats = _model._output._treeStats!=null ? _model._output._treeStats : new TreeStats();
      // Build trees until we hit the limit
      for( tid=0; tid<_parms._ntrees; tid++) { // Building tid-tree
        if (tid!=0 || !_parms._checkpoint) { // do not make initial scoring if model already exist
          doScoringAndSaveModel(false, _valid==null, _parms._build_tree_one_node);
        }
        // At each iteration build K trees (K = nclass = response column domain size)

        // TODO: parallelize more? build more than k trees at each time, we need to care about temporary data
        // Idea: launch more DRF at once.
        Timer kb_timer = new Timer();
        ktrees = buildNextKTrees(_train,_mtry,_parms._sample_rate,rand,tid);
        Log.info((tid+1) + ". tree was built " + kb_timer.toString());
        if( !isRunning() ) return; // If canceled during building, do not bulkscore

        // Check latest predictions
        tstats.updateBy(ktrees);
      }
      doScoringAndSaveModel(true, _valid==null, _parms._build_tree_one_node);
    }



    // --------------------------------------------------------------------------
    // Build the next random k-trees representing tid-th tree
    private DTree[] buildNextKTrees(Frame fr, int mtrys, float sample_rate, Random rand, int tid) {
      // We're going to build K (nclass) trees - each focused on correcting
      // errors for a single class.
      final DTree[] ktrees = new DTree[_nclass];

      // Initial set of histograms.  All trees; one leaf per tree (the root
      // leaf); all columns
      DHistogram hcs[][][] = new DHistogram[_nclass][1/*just root leaf*/][_ncols];

      // Adjust nbins for the top-levels
      int adj_nbins = Math.max((1<<(10-0)),_parms._nbins);

      // Use for all k-trees the same seed. NOTE: this is only to make a fair
      // view for all k-trees
      final boolean classification = _parms._convert_to_enum;
      final long[] _distribution = _model._output._distribution;
      long rseed = rand.nextLong();
      // Initially setup as-if an empty-split had just happened
      for( int k=0; k<_nclass; k++ ) {
        assert (_distribution!=null && classification) || (_distribution==null && !classification);
        if( _distribution == null || _distribution[k] != 0 ) { // Ignore missing classes
          // The Boolean Optimization cannot be applied here for RF !
          // This optimization assumes the 2nd tree of a 2-class system is the
          // inverse of the first.  This is false for DRF (and true for GBM) -
          // DRF picks a random different set of columns for the 2nd tree.
          //if( k==1 && _nclass==2 ) continue;
          ktrees[k] = new DRFTree(fr,_ncols,(char)_parms._nbins,(char)_nclass,_parms._min_rows,mtrys,rseed);
          boolean isBinom = classification;
          new DRFUndecidedNode(ktrees[k],-1, DHistogram.initialHist(fr,_ncols,adj_nbins,hcs[k][0],isBinom) ); // The "root" node
        }
      }

      // Sample - mark the lines by putting 'OUT_OF_BAG' into nid(<klass>) vector
      Timer t_1 = new Timer();
      Sample ss[] = new Sample[_nclass];
      for( int k=0; k<_nclass; k++)
        if (ktrees[k] != null) ss[k] = new Sample((DRFTree)ktrees[k], sample_rate).dfork(0,new Frame(vec_nids(fr,k),vec_resp(fr,k)), _parms._build_tree_one_node);
      for( int k=0; k<_nclass; k++)
        if( ss[k] != null ) ss[k].getResult();
      Log.debug("Sampling took: + " + t_1);

      int[] leafs = new int[_nclass]; // Define a "working set" of leaf splits, from leafs[i] to tree._len for each tree i

      // ----
      // One Big Loop till the ktrees are of proper depth.
      // Adds a layer to the trees each pass.
      Timer t_2 = new Timer();
      int depth=0;
      for( ; depth<_parms._max_depth; depth++ ) {
        if( !isRunning() ) return null;
        hcs = buildLayer(fr, _parms._nbins, ktrees, leafs, hcs, true, _parms._build_tree_one_node);
        // If we did not make any new splits, then the tree is split-to-death
        if( hcs == null ) break;
      }
      Log.debug("Tree build took: " + t_2);

      // Each tree bottomed-out in a DecidedNode; go 1 more level and insert
      // LeafNodes to hold predictions.
      Timer t_3 = new Timer();
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
                              ((DecidedNode)tree.node(cnid))._split.col()==-1) ) {
                LeafNode ln = new DRFLeafNode(tree,nid);
                ln._pred = dn.pred(i);  // Set prediction into the leaf
                dn._nids[i] = ln.nid(); // Mark a leaf here
              }
            }
            // Handle the trivial non-splitting tree
            if( nid==0 && dn._split.col() == -1 )
              new DRFLeafNode(tree,-1,0);
          }
        }
      } // -- k-trees are done
      Log.debug("Nodes propagation: " + t_3);


      // ----
      // Move rows into the final leaf rows
      Timer t_4 = new Timer();
      CollectPreds cp = new CollectPreds(ktrees,leafs).doAll(fr,_parms._build_tree_one_node);

      final boolean importance = true; //FIXME: cheap enough?

      if (importance) {
        if (classification)   asVotes(_treeMeasuresOnOOB).append(cp.rightVotes, cp.allRows); // Track right votes over OOB rows for this tree
        else /* regression */ asSSE  (_treeMeasuresOnOOB).append(cp.sse, cp.allRows);
      }
      Log.debug("CollectPreds done: " + t_4);

      // Collect leaves stats
      for (int i=0; i<ktrees.length; i++)
        if( ktrees[i] != null )
          ktrees[i].leaves = ktrees[i].len() - leafs[i];
      // DEBUG: Print the generated K trees
      //printGenerateTrees(ktrees);

      return ktrees;
    }


    // Collect and write predictions into leafs.
    private class CollectPreds extends MRTask<CollectPreds> {
      /* @IN  */ final DTree _trees[]; // Read-only, shared (except at the histograms in the Nodes)
      /* @OUT */ long rightVotes; // number of right votes over OOB rows (performed by this tree) represented by DTree[] _trees
      /* @OUT */ long allRows;    // number of all OOB rows (sampled by this tree)
      /* @OUT */ float sse;      // Sum of squares for this tree only
      CollectPreds(DTree trees[], int leafs[]) { _trees=trees; }
      final boolean importance = true;
      @Override public void map( Chunk[] chks ) {
        final Chunk    y       = importance ? chk_resp(chks) : null; // Response
        final float [] rpred   = importance ? new float [1+_nclass] : null; // Row prediction
        final double[] rowdata = importance ? new double[_ncols] : null; // Pre-allocated row data
        final Chunk   oobt  = chk_oobt(chks); // Out-of-bag rows counter over all trees
        // Iterate over all rows
        for( int row=0; row<oobt._len; row++ ) {
          boolean wasOOBRow = false;
          // For all tree (i.e., k-classes)
          for( int k=0; k<_nclass; k++ ) {
            final DTree tree = _trees[k];
            if( tree == null ) continue; // Empty class is ignored
            // If we have all constant responses, then we do not split even the
            // root and the residuals should be zero.
            if( tree.root() instanceof LeafNode ) continue;
            final Chunk nids = chk_nids(chks,k); // Node-ids  for this tree/class
            final Chunk ct   = chk_tree(chks,k); // k-tree working column holding votes for given row
            int nid = (int)nids.at8(row);         // Get Node to decide from
            // Update only out-of-bag rows
            // This is out-of-bag row - but we would like to track on-the-fly prediction for the row
            if( ScoreBuildHistogram.isOOBRow(nid) ) { // The row should be OOB for all k-trees !!!
              assert k==0 || wasOOBRow : "Something is wrong: k-class trees oob row computing is broken! All k-trees should agree on oob row!";
              wasOOBRow = true;
              nid = ScoreBuildHistogram.oob2Nid(nid);
              if( tree.node(nid) instanceof UndecidedNode ) // If we bottomed out the tree
                nid = tree.node(nid).pid();                 // Then take parent's decision
              DecidedNode dn = tree.decided(nid);           // Must have a decision point
              if( dn._split.col() == -1 )     // Unable to decide?
                dn = tree.decided(tree.node(nid).pid());    // Then take parent's decision
              int leafnid = dn.ns(chks,row); // Decide down to a leafnode
              // Setup Tree(i) - on the fly prediction of i-tree for row-th row
              //   - for classification: cumulative number of votes for this row
              //   - for regression: cumulative sum of prediction of each tree - has to be normalized by number of trees
              double prediction = ((LeafNode)tree.node(leafnid)).pred(); // Prediction for this k-class and this row
              if (importance) rpred[1+k] = (float) prediction; // for both regression and classification
              ct.set(row, (float) (ct.atd(row) + prediction));
              // For this tree this row is out-of-bag - i.e., a tree voted for this row
              oobt.set(row, _nclass > 1 ? 1 : oobt.atd(row) + 1); // for regression track number of trees, for classification boolean flag is enough
            }
            // reset help column for this row and this k-class
            nids.set(row, 0);
          } /* end of k-trees iteration */
          if (importance) {
            if (wasOOBRow && !y.isNA(row)) {
              if (_parms._convert_to_enum) {
                int treePred = ModelUtils.getPrediction(rpred, data_row(chks, row, rowdata));
                int actuPred = (int) y.at8(row);
                if (treePred==actuPred) rightVotes++; // No miss !
              } else { // regression
                float  treePred = rpred[1];
                float  actuPred = (float) y.atd(row);
                sse += (actuPred-treePred)*(actuPred-treePred);
              }
              allRows++;
            }
          }
        }
      }
      @Override public void reduce(CollectPreds mrt) {
        rightVotes += mrt.rightVotes;
        allRows    += mrt.allRows;
        sse        += mrt.sse;
      }
    }



    @Override protected DRFModel makeModel( Key modelKey, DRFModel.DRFParameters parms, double mse_train, double mse_valid ) {
      return new DRFModel(modelKey,parms,new DRFModel.DRFOutput(DRF.this,mse_train,mse_valid));
    }

  }

  @Override protected DecidedNode makeDecided( UndecidedNode udn, DHistogram hs[] ) {
    return new DRFDecidedNode(udn,hs);
  }
  
  // ---
  // DRF DTree decision node: same as the normal DecidedNode, but
  // specifies a decision algorithm given complete histograms on all
  // columns.  DRF algo: find the lowest error amongst *all* columns.
  static class DRFDecidedNode extends DecidedNode {
    DRFDecidedNode( UndecidedNode n, DHistogram[] hs ) { super(n,hs); }
    @Override public UndecidedNode makeUndecidedNode(DHistogram[] hs ) {
      return new DRFUndecidedNode(_tree,_nid,hs);
    }
  
    // Find the column with the best split (lowest score).  Unlike RF, DRF
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
  // DRF DTree undecided node: same as the normal UndecidedNode, but specifies
  // a list of columns to score on now, and then decide over later.
  // DRF algo: use all columns
  static class DRFUndecidedNode extends UndecidedNode {
    DRFUndecidedNode( DTree tree, int pid, DHistogram hs[] ) { super(tree,pid,hs); }
    // Randomly select mtry columns to 'score' in following pass over the data.
    @Override public int[] scoreCols( DHistogram[] hs ) {
      DRFTree tree = (DRFTree)_tree;
      int[] cols = new int[hs.length];
      int len=0;
      // Gather all active columns to choose from.
      for( int i=0; i<hs.length; i++ ) {
        if( hs[i]==null ) continue; // Ignore not-tracked cols
        assert hs[i]._min < hs[i]._maxEx && hs[i].nbins() > 1 : "broken histo range "+hs[i];
        cols[len++] = i;        // Gather active column
      }
      int choices = len;        // Number of columns I can choose from
      assert choices > 0;

      // Draw up to mtry columns at random without replacement.
      for( int i=0; i<tree._mtrys; i++ ) {
        if( len == 0 ) break;   // Out of choices!
        int idx2 = tree._rand.nextInt(len);
        int col = cols[idx2];     // The chosen column
        cols[idx2] = cols[--len]; // Compress out of array; do not choose again
        cols[len] = col;          // Swap chosen in just after 'len'
      }
      assert choices - len > 0;
      return Arrays.copyOfRange(cols, len, choices);
    }
  }
  
  // ---
  static class DRFLeafNode extends LeafNode {
    DRFLeafNode( DTree tree, int pid ) { super(tree,pid); }
    DRFLeafNode( DTree tree, int pid, int nid ) { super(tree, pid, nid); }
    // Insert just the predictions: a single byte/short if we are predicting a
    // single class, or else the full distribution.
    @Override protected AutoBuffer compress(AutoBuffer ab) { assert !Double.isNaN(_pred); return ab.put4f((float)_pred); }
    @Override protected int size() { return 4; }
  }

  // Deterministic sampling
  static class Sample extends MRTask<Sample> {
    final DRFTree _tree;
    final float _rate;
    Sample( DRFTree tree, float rate ) { _tree = tree; _rate = rate; }
    @Override public void map( Chunk nids, Chunk ys ) {
      Random rand = _tree.rngForChunk(nids.cidx());
      for( int row=0; row<nids._len; row++ )
        if( rand.nextFloat() >= _rate || Double.isNaN(ys.atd(row)) ) {
          nids.set(row, ScoreBuildHistogram.OUT_OF_BAG);     // Flag row as being ignored by sampling
        }
    }
  }

  // Read the 'tree' columns, do model-specific math and put the results in the
  // fs[] array, and return the sum.  Dividing any fs[] element by the sum
  // turns the results into a probability distribution.
  @Override protected float score1( Chunk chks[], float fs[/*nclass*/], int row ) {
    float sum=0;
    for( int k=0; k<_nclass; k++ ) // Sum across of likelyhoods
      sum+=(fs[k+1]=(float)chk_tree(chks,k).atd(row));
    if (_nclass == 1) sum /= (float)chk_oobt(chks).atd(row); // for regression average per trees voted for this row (only trees which have row in "out-of-bag"
    return sum;
  }


}
