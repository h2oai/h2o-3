package hex.tree.drf;

import hex.ModelCategory;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.*;
import hex.tree.DTree.DecidedNode;
import hex.tree.DTree.LeafNode;
import hex.tree.DTree.UndecidedNode;
import water.Job;
import water.Key;
import water.MRTask;
import water.fvec.C0DChunk;
import water.fvec.Chunk;
import water.fvec.Frame;

import java.util.Random;

import static hex.genmodel.GenModel.getPrediction;
import static hex.tree.drf.TreeMeasuresCollector.asSSE;
import static hex.tree.drf.TreeMeasuresCollector.asVotes;

/** Gradient Boosted Trees
 *
 *  Based on "Elements of Statistical Learning, Second Edition, page 387"
 */
public class DRF extends SharedTree<hex.tree.drf.DRFModel, hex.tree.drf.DRFModel.DRFParameters, hex.tree.drf.DRFModel.DRFOutput> {
  private static final double ONEBOUND=1+1e-12;    // due to fixed precision
  private static final double ZEROBOUND=-1e-12;    // due to fixed precision
  @Override public ModelCategory[] can_build() {
    return new ModelCategory[]{
      ModelCategory.Regression,
      ModelCategory.Binomial,
      ModelCategory.Multinomial,
    };
  }

  // Called from an http request
  public DRF( hex.tree.drf.DRFModel.DRFParameters parms                   ) { super(parms     ); init(false); }
  public DRF( hex.tree.drf.DRFModel.DRFParameters parms, Key<DRFModel> key) { super(parms, key); init(false); }
  public DRF( hex.tree.drf.DRFModel.DRFParameters parms, Job job          ) { super(parms, job); init(false); }
  public DRF(boolean startup_once) { super(new hex.tree.drf.DRFModel.DRFParameters(),startup_once); }

  /** Start the DRF training Job on an F/J thread. */
  @Override protected Driver trainModelImpl() { return new DRFDriver(); }

  @Override public boolean scoreZeroTrees() { return false; }

  /** Initialize the ModelBuilder, validating all arguments and preparing the
   *  training frame.  This call is expected to be overridden in the subclasses
   *  and each subclass will start with "super.init();".  This call is made
   *  by the front-end whenever the GUI is clicked, and needs to be fast;
   *  heavy-weight prep needs to wait for the trainModel() call.
   */
  @Override public void init(boolean expensive) {
    super.init(expensive);
    // Initialize local variables
    if( _parms._mtries < 1 && _parms._mtries != -1 && _parms._mtries != -2 )
      error("_mtries", "mtries must be -1 (converted to sqrt(features)) or -2 (All features) or >= 1 but it is " + _parms._mtries);
    if( _train != null ) {
      int ncols = _train.numCols();
      if( _parms._mtries != -1 && _parms._mtries != -2 && !(1 <= _parms._mtries && _parms._mtries < ncols /*ncols includes the response*/))
        error("_mtries","Computed mtries should be -1 or -2 or in interval [1,"+ncols+"[ but it is " + _parms._mtries);
    }
    if (_parms._distribution == DistributionFamily.quasibinomial)
      error("_distribution", "Quasibinomial is not supported for DRF in current H2O.");
    if (_parms._distribution == DistributionFamily.AUTO) {
      if (_nclass == 1) _parms._distribution = DistributionFamily.gaussian;
      if (_nclass >= 2) _parms._distribution = DistributionFamily.multinomial;
    }
    if (_parms._sample_rate == 1f && _valid == null && _parms._nfolds == 0)
      warn("_sample_rate", "Sample rate is 100% and no validation dataset and no cross-validation. There are no out-of-bag data to compute error estimates on the training data!");
    if (hasOffsetCol())
      error("_offset_column", "Offsets are not yet supported for DRF.");
    if (hasOffsetCol() && isClassifier()) {
      error("_offset_column", "Offset is only supported for regression.");
    }
  }

  // ----------------------
  private class DRFDriver extends Driver {
    @Override protected boolean doOOBScoring() { return true; }

    // --- Private data handled only on master node
    // Classification or Regression:
    // Tree votes/SSE of individual trees on OOB rows
    public transient TreeMeasuresCollector.TreeMeasures _treeMeasuresOnOOB;
    // Tree votes/SSE per individual features on permutated OOB rows
    public transient TreeMeasuresCollector.TreeMeasures[/*features*/] _treeMeasuresOnSOOB;
    // Variable importance based on tree split decisions
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

    @Override protected void initializeModelSpecifics() {
      _mtry_per_tree = Math.max(1, (int)(_parms._col_sample_rate_per_tree * _ncols));
      if (!(1 <= _mtry_per_tree && _mtry_per_tree <= _ncols)) throw new IllegalArgumentException("Computed mtry_per_tree should be in interval <1,"+_ncols+"> but it is " + _mtry_per_tree);
      if(_parms._mtries==-2){ //mtries set to -2 would use all columns in each split regardless of what column has been dropped during train
        _mtry = _ncols;
      }else if(_parms._mtries==-1) {
        _mtry = (isClassifier() ? Math.max((int) Math.sqrt(_ncols), 1) : Math.max(_ncols / 3, 1)); // classification: mtry=sqrt(_ncols), regression: mtry=_ncols/3
      }else{
        _mtry = _parms._mtries;
      }
      if (!(1 <= _mtry && _mtry <= _ncols)) {
        throw new IllegalArgumentException("Computed mtry should be in interval <1," + _ncols + "> but it is " + _mtry);
      }

      _initialPrediction = isClassifier() ? 0 : getInitialValue();
      // Initialize TreeVotes for classification, MSE arrays for regression
      initTreeMeasurements();

      /** Fill work columns:
       *   - classification: set 1 in the corresponding wrk col according to row response
       *   - regression:     copy response into work column (there is only 1 work column)
       */
      new MRTask() {
        @Override public void map(Chunk chks[]) {
          Chunk cy = chk_resp(chks);
          for (int i = 0; i < cy._len; i++) {
            if (cy.isNA(i)) continue;
            if (isClassifier()) {
              int cls = (int) cy.at8(i);
              chk_work(chks, cls).set(i, 1L);
            } else {
              float pred = (float) cy.atd(i);
              chk_work(chks, 0).set(i, pred);
            }
          }
        }
      }.doAll(_train);
    }

    // --------------------------------------------------------------------------
    // Build the next random k-trees representing tid-th tree
    @Override protected boolean buildNextKTrees() {
      // We're going to build K (nclass) trees - each focused on correcting
      // errors for a single class.
      final DTree[] ktrees = new DTree[_nclass];

      // Define a "working set" of leaf splits, from leafs[i] to tree._len for each tree i
      int[] leafs = new int[_nclass];

      // Assign rows to nodes - fill the "NIDs" column(s)
      growTrees(ktrees, leafs, _rand);

      // Move rows into the final leaf rows - fill "Tree" and OUT_BAG_TREES columns and zap the NIDs column
      CollectPreds cp = new CollectPreds(ktrees,leafs,_model.defaultThreshold()).doAll(_train,_parms._build_tree_one_node);

      if (isClassifier())   asVotes(_treeMeasuresOnOOB).append(cp.rightVotes, cp.allRows); // Track right votes over OOB rows for this tree
      else /* regression */ asSSE  (_treeMeasuresOnOOB).append(cp.sse, cp.allRows);

      // Grow the model by K-trees
      _model._output.addKTrees(ktrees);

      return false; //never stop early
    }

    // Assumes that the "Work" column are filled with horizontalized (0/1) class memberships per row (or copy of regression response)
    private void growTrees(DTree[] ktrees, int[] leafs, Random rand) {
      // Initial set of histograms.  All trees; one leaf per tree (the root
      // leaf); all columns
      DHistogram hcs[][][] = new DHistogram[_nclass][1/*just root leaf*/][_ncols];

      // Adjust real bins for the top-levels
      int adj_nbins = Math.max(_parms._nbins_top_level,_parms._nbins);

      // Use for all k-trees the same seed. NOTE: this is only to make a fair
      // view for all k-trees
      long rseed = rand.nextLong();
      // Initially setup as-if an empty-split had just happened
      for (int k = 0; k < _nclass; k++) {
        if (_model._output._distribution[k] != 0) { // Ignore missing classes
          // The Boolean Optimization
          // This optimization assumes the 2nd tree of a 2-class system is the
          // inverse of the first (and that the same columns were picked)
          if( k==1 && _nclass==2 && _model.binomialOpt()) continue;
          ktrees[k] = new DTree(_train, _ncols, _mtry, _mtry_per_tree, rseed, _parms);
          new UndecidedNode(ktrees[k], -1, DHistogram.initialHist(_train, _ncols, adj_nbins, hcs[k][0], rseed, _parms, getGlobalQuantilesKeys())); // The "root" node
        }
      }

      // Sample - mark the lines by putting 'OUT_OF_BAG' into nid(<klass>) vector
      Sample ss[] = new Sample[_nclass];
      for( int k=0; k<_nclass; k++)
        if (ktrees[k] != null) ss[k] = new Sample(ktrees[k], _parms._sample_rate, _parms._sample_rate_per_class).dfork(null,new Frame(vec_nids(_train,k),vec_resp(_train)), _parms._build_tree_one_node);
      for( int k=0; k<_nclass; k++)
        if( ss[k] != null ) ss[k].getResult();

      // ----
      // One Big Loop till the ktrees are of proper depth.
      // Adds a layer to the trees each pass.
      int depth=0;
      for( ; depth<_parms._max_depth; depth++ ) {
        hcs = buildLayer(_train, _parms._nbins, _parms._nbins_cats, ktrees, leafs, hcs, _parms._build_tree_one_node);
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
            if( dn._split == null ) { // No decision here, no row should have this NID now
              if( nid==0 ) {               // Handle the trivial non-splitting tree
                LeafNode ln = new LeafNode(tree, -1, 0);
                ln._pred = (float)(isClassifier() ? _model._output._priorClassDist[k] : _initialPrediction);
              }
              continue;
            }
            for( int i=0; i<dn._nids.length; i++ ) {
              int cnid = dn._nids[i];
              if( cnid == -1 || // Bottomed out (predictors or responses known constant)
                      tree.node(cnid) instanceof UndecidedNode || // Or chopped off for depth
                      (tree.node(cnid) instanceof DecidedNode &&  // Or not possible to split
                              ((DecidedNode)tree.node(cnid))._split==null) ) {
                LeafNode ln = new LeafNode(tree,nid);
                ln._pred = (float)dn.pred(i);  // Set prediction into the leaf
                dn._nids[i] = ln.nid(); // Mark a leaf here
              }
            }
          }
        }
      } // -- k-trees are done
    }

    // Collect and write predictions into leafs.
    private class CollectPreds extends MRTask<CollectPreds> {
      /* @IN  */ final DTree _trees[]; // Read-only, shared (except at the histograms in the Nodes)
      /* @IN */  double _threshold;      // Sum of squares for this tree only
      /* @OUT */ double rightVotes; // number of right votes over OOB rows (performed by this tree) represented by DTree[] _trees
      /* @OUT */ double allRows;    // number of all OOB rows (sampled by this tree)
      /* @OUT */ float sse;      // Sum of squares for this tree only
      CollectPreds(DTree trees[], int leafs[], double threshold) { _trees=trees; _threshold = threshold; }
      final boolean importance = true;
      @Override public void map( Chunk[] chks ) {
        final Chunk    y       = importance ? chk_resp(chks) : null; // Response
        final double[] rpred   = importance ? new double[1+_nclass] : null; // Row prediction
        final double[] rowdata = importance ? new double[_ncols] : null; // Pre-allocated row data
        final Chunk   oobt  = chk_oobt(chks); // Out-of-bag rows counter over all trees
        final Chunk   weights  = hasWeightCol() ? chk_weight(chks) : new C0DChunk(1, chks[0]._len); // Out-of-bag rows counter over all trees
        // Iterate over all rows
        for( int row=0; row<oobt._len; row++ ) {
          double weight = weights.atd(row);
          final boolean wasOOBRow = ScoreBuildHistogram.isOOBRow((int)chk_nids(chks,0).at8(row));
          // For all tree (i.e., k-classes)
          for( int k=0; k<_nclass; k++ ) {
            final Chunk nids = chk_nids(chks, k); // Node-ids  for this tree/class
            if (weight!=0) {
              final DTree tree = _trees[k];
              if (tree == null) continue; // Empty class is ignored
              int nid = (int) nids.at8(row);         // Get Node to decide from
              // Update only out-of-bag rows
              // This is out-of-bag row - but we would like to track on-the-fly prediction for the row
              if (wasOOBRow) {
                final Chunk ct = chk_tree(chks, k); // k-tree working column holding votes for given row
                nid = ScoreBuildHistogram.oob2Nid(nid);
                if (tree.node(nid) instanceof UndecidedNode) // If we bottomed out the tree
                  nid = tree.node(nid).pid();                 // Then take parent's decision
                int leafnid;
                if (tree.root() instanceof LeafNode) {
                  leafnid = 0;
                } else {
                  DecidedNode dn = tree.decided(nid);           // Must have a decision point
                  if (dn._split == null)     // Unable to decide?
                    dn = tree.decided(tree.node(nid).pid());    // Then take parent's decision
                  leafnid = dn.getChildNodeID(chks,row); // Decide down to a leafnode
                }
                // Setup Tree(i) - on the fly prediction of i-tree for row-th row
                //   - for classification: cumulative number of votes for this row
                //   - for regression: cumulative sum of prediction of each tree - has to be normalized by number of trees
                double prediction = ((LeafNode) tree.node(leafnid)).pred(); // Prediction for this k-class and this row
                if (importance) rpred[1 + k] = (float) prediction; // for both regression and classification
                ct.set(row, (float) (ct.atd(row) + prediction));
              }
            }
            // reset help column for this row and this k-class
            nids.set(row, 0);
          } /* end of k-trees iteration */
          // For this tree this row is out-of-bag - i.e., a tree voted for this row
          if (wasOOBRow) oobt.set(row, oobt.atd(row) + weight); // track number of trees
          if (importance && weight!=0) {
            if (wasOOBRow && !y.isNA(row)) {
              if (isClassifier()) {
                int treePred = getPrediction(rpred, _model._output._priorClassDist, data_row(chks, row, rowdata), _threshold);
                int actuPred = (int) y.at8(row);
                if (treePred==actuPred) rightVotes+=weight; // No miss !
              } else { // regression
                double treePred = rpred[1];
                double actuPred = y.atd(row);
                sse += (actuPred-treePred)*(actuPred-treePred);
              }
              allRows+=weight;
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



    @Override protected DRFModel makeModel( Key modelKey, DRFModel.DRFParameters parms) {
      return new DRFModel(modelKey,parms,new DRFModel.DRFOutput(DRF.this));
    }

  }

  // Read the 'tree' columns, do model-specific math and put the results in the
  // fs[] array, and return the sum.  Dividing any fs[] element by the sum
  // turns the results into a probability distribution.
  @Override protected double score1( Chunk chks[], double weight, double offset, double fs[/*nclass*/], int row ) {
    double sum = 0;
    if (_nclass > 2 || (_nclass == 2 && !_model.binomialOpt())) {
      for (int k = 0; k < _nclass; k++)
        sum += (fs[k+1] = weight * chk_tree(chks, k).atd(row) / chk_oobt(chks).atd(row));
    }
    else if (_nclass==2 && _model.binomialOpt()) {
      fs[1] = weight * chk_tree(chks, 0).atd(row) / chk_oobt(chks).atd(row);
      if (fs[1]>1 && fs[1]<=ONEBOUND)
        fs[1] = 1.0;
      else if (fs[1]<0 && fs[1]>=ZEROBOUND)
        fs[1] = 0.0;
      assert(fs[1] >= 0 && fs[1] <= 1);
      fs[2] = 1. - fs[1];
    }
    else { //regression
      // average per trees voted for this row (only trees which have row in "out-of-bag"
      sum += (fs[0] = weight * chk_tree(chks, 0).atd(row) / chk_oobt(chks).atd(row) );
      fs[1] = 0;
    }
    return sum;
  }


}
