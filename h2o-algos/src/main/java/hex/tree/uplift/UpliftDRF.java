package hex.tree.uplift;

import hex.ModelCategory;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.DHistogram;
import hex.tree.DTree;
import hex.tree.Sample;
import hex.tree.ScoreBuildHistogram;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import water.Job;
import water.Key;
import water.MRTask;
import water.fvec.C0DChunk;
import water.fvec.Chunk;
import water.fvec.Frame;

import java.util.Random;

import static hex.genmodel.GenModel.getPrediction;

public class UpliftDRF extends DRF {

    // Called from an http request
    public UpliftDRF(hex.tree.drf.DRFModel.DRFParameters parms) {
        super(parms);
        init(false);
    }

    public UpliftDRF(hex.tree.drf.DRFModel.DRFParameters parms, Key<DRFModel> key) {
        super(parms, key);
        init(false);
    }

    public UpliftDRF(hex.tree.drf.DRFModel.DRFParameters parms, Job job) {
        super(parms, job);
        init(false);
    }

    public UpliftDRF(boolean startup_once) {
        super(new hex.tree.uplift.UpliftDRFModel.UpliftDRFParameters(), startup_once);
    }

    @Override
    public ModelCategory[] can_build() {
        return new ModelCategory[]{
                ModelCategory.Regression, 
                ModelCategory.Binomial,
        };
    }

    /** Start the DRF training Job on an F/J thread. */
    @Override protected Driver trainModelImpl() { return new UpliftDRFDriver(); }
    
    /** Initialize the ModelBuilder, validating all arguments and preparing the
     *  training frame.  This call is expected to be overridden in the subclasses
     *  and each subclass will start with "super.init();".  This call is made
     *  by the front-end whenever the GUI is clicked, and needs to be fast;
     *  heavy-weight prep needs to wait for the trainModel() call.
     */
    @Override public void init(boolean expensive) {
        super.init(expensive);
        // Initialize local variables
        if (_nclass > 2 || _parms._distribution.equals(DistributionFamily.multinomial)) {
            error("_distribution", "UpliftDRF currently does not support multinomial distribution.");
        }
        if(_parms._uplift_column == null){
            error("_uplift_column", "The uplift column has to be defined.");
        }
    }

    // ----------------------
    private class UpliftDRFDriver extends DRFDriver {

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
            UpliftDRF.UpliftDRFDriver.CollectPreds cp = new UpliftDRF.UpliftDRFDriver.CollectPreds(ktrees,leafs,_model.defaultThreshold()).doAll(_train,_parms._build_tree_one_node);
            
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
                    ktrees[k] = new DTree(_train, _ncols, _mtry, _mtry_per_tree, rseed, _parms);
                    new DTree.UndecidedNode(ktrees[k], -1, DHistogram.initialHist(_train, _ncols, adj_nbins, hcs[k][0], rseed, _parms, getGlobalQuantilesKeys(), null), null); // The "root" node
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
            DTree treeTr = ktrees[0];
            DTree treeCt = ktrees[1];
            int leaf = leafs[0] = treeTr.len();
            for (int nid = 0; nid < leaf; nid++) {
                if (treeTr.node(nid) instanceof DTree.DecidedNode) {
                    DTree.DecidedNode dnTr = treeTr.decided(nid);
                    DTree.DecidedNode dnCt = treeCt.decided(nid);
                    if (dnTr._split == null) { // No decision here, no row should have this NID now
                        if (nid == 0) { // Handle the trivial non-splitting tree
                            DTree.LeafNode lnTr = new DTree.LeafNode(treeTr, -1, 0);
                            lnTr._pred = (float) (_model._output._priorClassDist[0]);
                            DTree.LeafNode lnCt = new DTree.LeafNode(treeCt, -1, 0);
                            lnCt._pred = (float) (_model._output._priorClassDist[1]);
                        }
                        continue;
                    }
                    for (int i = 0; i < dnTr._nids.length; i++) {
                        int cnid = dnTr._nids[i];
                        if (cnid == -1 || // Bottomed out (predictors or responses known constant)
                                treeTr.node(cnid) instanceof DTree.UndecidedNode || // Or chopped off for depth
                                (treeTr.node(cnid) instanceof DTree.DecidedNode &&  // Or not possible to split
                                        ((DTree.DecidedNode) treeTr.node(cnid))._split == null)) {
                            DTree.LeafNode lnTr = new DTree.LeafNode(treeTr, nid);
                            lnTr._pred = (float) dnTr.predTreatment(i);  // Set prediction into the leaf
                            dnTr._nids[i] = lnTr.nid(); // Mark a leaf here
                            DTree.LeafNode lnCt = new DTree.LeafNode(treeCt, nid);
                            lnCt._pred = (float) dnCt.predControl(i);  // Set prediction into the leaf
                            dnCt._nids[i] = lnCt.nid(); // Mark a leaf here
                        }
                    }
                }
            }
        }

        // Collect and write predictions into leafs.
        private class CollectPreds extends MRTask<UpliftDRF.UpliftDRFDriver.CollectPreds> {
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
                                if (tree.node(nid) instanceof DTree.UndecidedNode) // If we bottomed out the tree
                                    nid = tree.node(nid).pid();                 // Then take parent's decision
                                int leafnid;
                                if (tree.root() instanceof DTree.LeafNode) {
                                    leafnid = 0;
                                } else {
                                    DTree.DecidedNode dn = tree.decided(nid);           // Must have a decision point
                                    if (dn._split == null)     // Unable to decide?
                                        dn = tree.decided(tree.node(nid).pid());    // Then take parent's decision
                                    leafnid = dn.getChildNodeID(chks,row); // Decide down to a leafnode
                                }
                                // Setup Tree(i) - on the fly prediction of i-tree for row-th row
                                //   - for classification: cumulative number of votes for this row
                                //   - for regression: cumulative sum of prediction of each tree - has to be normalized by number of trees
                                double prediction = ((DTree.LeafNode) tree.node(leafnid)).pred(); // Prediction for this k-class and this row
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
                                // TODO solve for uplift
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
            @Override public void reduce(UpliftDRF.UpliftDRFDriver.CollectPreds mrt) {
                rightVotes += mrt.rightVotes;
                allRows    += mrt.allRows;
                sse        += mrt.sse;
            }
        }



        @Override protected DRFModel makeModel( Key modelKey, DRFModel.DRFParameters parms) {
            return new DRFModel(modelKey,parms,new DRFModel.DRFOutput(UpliftDRF.this));
        }

    }

    // Read the 'tree' columns, do model-specific math and put the results in the
    // fs[] array, and return the sum.  Dividing any fs[] element by the sum
    // turns the results into a probability distribution.
    @Override protected double score1( Chunk chks[], double weight, double offset, double fs[/*nclass*/], int row ) {
        double sum = 0;
        if(nclasses() == 2) {
            fs[1] = weight * chk_tree(chks, 0).atd(row) / chk_oobt(chks).atd(row);
            fs[2] = weight * chk_tree(chks, 1).atd(row) / chk_oobt(chks).atd(row);
            fs[0] = fs[1] - fs[2];
        } else { //regression
            // average per trees voted for this row (only trees which have row in "out-of-bag"
            sum += (fs[0] = weight * chk_tree(chks, 0).atd(row) / chk_oobt(chks).atd(row) );
            fs[1] = 0;
        }
        return sum;
    }

}
