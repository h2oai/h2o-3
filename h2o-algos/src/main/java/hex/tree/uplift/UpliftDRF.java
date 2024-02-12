package hex.tree.uplift;

import hex.*;
import hex.genmodel.MojoModel;
import hex.genmodel.algos.upliftdrf.UpliftDrfMojoModel;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.*;
import org.apache.log4j.Logger;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import water.H2O;
import water.Job;
import water.Key;
import water.MRTask;
import water.fvec.C0DChunk;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.PrettyPrint;
import water.util.TwoDimTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class UpliftDRF extends SharedTree<UpliftDRFModel, UpliftDRFModel.UpliftDRFParameters, UpliftDRFModel.UpliftDRFOutput> {

    private static final Logger LOG = Logger.getLogger(UpliftDRF.class);

    public enum UpliftMetricType { AUTO, KL, ChiSquared, Euclidean }

    @Override
    public boolean isUplift() {return true;}

    // Called from an http request
    public UpliftDRF(hex.tree.uplift.UpliftDRFModel.UpliftDRFParameters parms) {
        super(parms);
        init(false);
    }

    public UpliftDRF(hex.tree.uplift.UpliftDRFModel.UpliftDRFParameters parms, Key<UpliftDRFModel> key) {
        super(parms, key);
        init(false);
    }

    public UpliftDRF(hex.tree.uplift.UpliftDRFModel.UpliftDRFParameters parms, Job job) {
        super(parms, job);
        init(false);
    }

    public UpliftDRF(boolean startup_once) {
        super(new hex.tree.uplift.UpliftDRFModel.UpliftDRFParameters(), startup_once);
    }

    @Override
    public boolean haveMojo() {
        return true;
    }

    @Override
    public boolean havePojo() {
        return false;
    }

    @Override
    public ModelCategory[] can_build() {
        return new ModelCategory[]{
                ModelCategory.BinomialUplift
        };
    }
    
    /** Start the DRF training Job on an F/J thread. */
    @Override protected Driver trainModelImpl() { return new UpliftDRFDriver(); }


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
        if (_parms._sample_rate == 1f && _valid == null)
            warn("_sample_rate", "Sample rate is 100% and no validation dataset. There are no out-of-bag data to compute error estimates on the training data!");
        if (hasOffsetCol())
            error("_offset_column", "Offsets are not yet supported for Uplift DRF.");
        if (hasWeightCol())
            error("_weight_column", "Weights are not yet supported for Uplift DRF.");
        if (hasFoldCol())
            error("_fold_column", "Cross-validation is not yet supported for Uplift DRF.");
        if (_parms._nfolds > 0)
            error("_nfolds", "Cross-validation is not yet supported for Uplift DRF.");
        if (_nclass == 1)
            error("_distribution", "UpliftDRF currently support binomial classification problems only.");
        if (_nclass > 2 || _parms._distribution.equals(DistributionFamily.multinomial)) 
            error("_distribution", "UpliftDRF currently does not support multinomial distribution.");
        if (_parms._treatment_column == null) 
            error("_treatment_column", "The treatment column has to be defined.");
        if (_parms._custom_distribution_func != null)
            error("_custom_distribution_func", "The custom distribution is not yet supported for Uplift DRF.");
    }
    
    

    // ----------------------
    private class UpliftDRFDriver extends Driver {

        @Override
        protected boolean doOOBScoring() {
            return true;
        }

        @Override protected void initializeModelSpecifics() {
            _mtry_per_tree = Math.max(1, (int) (_parms._col_sample_rate_per_tree * _ncols));
            if (!(1 <= _mtry_per_tree && _mtry_per_tree <= _ncols))
                throw new IllegalArgumentException("Computed mtry_per_tree should be in interval <1," + _ncols + "> but it is " + _mtry_per_tree);
            if (_parms._mtries == -2) { //mtries set to -2 would use all columns in each split regardless of what column has been dropped during train
                _mtry = _ncols;
            } else if (_parms._mtries == -1) {
                _mtry = (isClassifier() ? Math.max((int) Math.sqrt(_ncols), 1) : Math.max(_ncols / 3, 1)); // classification: mtry=sqrt(_ncols), regression: mtry=_ncols/3
            } else {
                _mtry = _parms._mtries;
            }
            if (!(1 <= _mtry && _mtry <= _ncols)) {
                throw new IllegalArgumentException("Computed mtry should be in interval <1," + _ncols + "> but it is " + _mtry);
            }
            new MRTask() {
                @Override public void map(Chunk chks[]) {
                    Chunk cy = chk_resp(chks);
                    for (int i = 0; i < cy._len; i++) {
                        if (cy.isNA(i)) continue;
                        int cls = (int) cy.at8(i);
                        chk_work(chks, cls).set(i, 1L);
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
            UpliftCollectPreds cp = new UpliftCollectPreds(ktrees,leafs).doAll(_train,_parms._build_tree_one_node);
            
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
                    new DTree.UndecidedNode(ktrees[k], -1, DHistogram.initialHist(_train, _ncols, adj_nbins, hcs[k][0], rseed, _parms, getGlobalSplitPointsKeys(), null, false, null), null, null); // The "root" node
                }
            }

            // Sample - mark the lines by putting 'OUT_OF_BAG' into nid(<klass>) vector
            Sample s = new Sample(ktrees[0], _parms._sample_rate, _parms._sample_rate_per_class).dfork(null,new Frame(vec_nids(_train,0),vec_resp(_train)), _parms._build_tree_one_node).getResult();

            // ----
            // One Big Loop till the ktrees are of proper depth.
            // Adds a layer to the trees each pass.
            int depth=0;
            for( ; depth<_parms._max_depth; depth++ ) {
                hcs = buildLayer(_train, _parms._nbins, ktrees[0], leafs, hcs, _parms._build_tree_one_node);
                
                // If we did not make any new splits, then the tree is split-to-death
                if( hcs == null ) break;
            }
            // Each tree bottomed-out in a DecidedNode; go 1 more level and insert
            // LeafNodes to hold predictions.
            DTree treeTr = ktrees[0];
            ktrees[1] = new DTree(ktrees[0]); // make a deep copy of the tree to assign control prediction to the leaves
            DTree treeCt = ktrees[1]; 
            int leaf = leafs[0] = treeTr.len();
            for (int nid = 0; nid < leaf; nid++) {
                if (treeTr.node(nid) instanceof DTree.DecidedNode) { // Should be the same for treatment and control tree
                    DTree.DecidedNode dnTr = treeTr.decided(nid); // Treatment tree node
                    DTree.DecidedNode dnCt = treeCt.decided(nid); // Control tree node
                    if (dnTr._split == null) { // No decision here, no row should have this NID now
                        if (nid == 0) { // Handle the trivial non-splitting tree
                            DTree.LeafNode lnTr = new DTree.LeafNode(treeTr, -1, 0);
                            lnTr._pred = (float) (_model._output._priorClassDist[1]);
                            DTree.LeafNode lnCt = new DTree.LeafNode(treeCt, -1, 0);
                            lnCt._pred = (float) (_model._output._priorClassDist[0]);
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
                            lnTr._pred = (float) dnTr.predTreatment(i);  // Set prediction into the treatment leaf
                            dnTr._nids[i] = lnTr.nid(); // Mark a leaf here for treatment 
                            DTree.LeafNode lnCt = new DTree.LeafNode(treeCt, nid);
                            lnCt._pred = (float) dnCt.predControl(i);  // Set prediction into the control leaf
                            dnCt._nids[i] = lnCt.nid(); // Mark a leaf here for control
                        }
                    }
                } 
            }
        }

        // Collect and write predictions into leaves.
        private class UpliftCollectPreds extends MRTask<UpliftCollectPreds> {
            /* @IN  */ final DTree _trees[]; // Read-only, shared (except at the histograms in the Nodes)
            /* @OUT */ double allRows;    // number of all OOB rows (sampled by this tree)
            
            UpliftCollectPreds(DTree trees[], int leafs[]) { _trees=trees;}
            @Override public void map( Chunk[] chks ) {
                final Chunk    y       = chk_resp(chks); // Response
                final Chunk   oobt     = chk_oobt(chks); // Out-of-bag rows counter over all trees
                final Chunk   weights  = hasWeightCol() ? chk_weight(chks) : new C0DChunk(1, chks[0]._len); // Out-of-bag rows counter over all trees
                // Iterate over all rows
                for( int row=0; row<oobt._len; row++ ) {
                    double weight = weights.atd(row);
                    final boolean wasOOBRow = ScoreBuildHistogram.isOOBRow((int)chk_nids(chks,0).at8(row));
                    //final boolean wasOOBRow = false;
                    // For all tree (i.e., k-classes)
                    final Chunk nids = chk_nids(chks, 0); // Node-ids  for treatment group class
                    final Chunk nids1 = chk_nids(chks, 1); // Node-ids  for control group class
                    if (weight!=0) {
                        final DTree treeT = _trees[0];
                        final DTree treeC = _trees[1];
                        if (treeT == null) continue; // Empty class is ignored
                        int nid = (int) nids.at8(row);         // Get Node to decide from
                        // Update only out-of-bag rows
                        // This is out-of-bag row - but we would like to track on-the-fly prediction for the row
                        if (wasOOBRow) {
                            nid = ScoreBuildHistogram.oob2Nid(nid);
                            if (treeT.node(nid) instanceof DTree.UndecidedNode) // If we bottomed out the tree
                                nid = treeT.node(nid).pid();                 // Then take parent's decision
                            int leafnid;
                            if (treeT.root() instanceof DTree.LeafNode) {
                                leafnid = 0;
                            } else {
                                DTree.DecidedNode dn = treeT.decided(nid);           // Must have a decision point
                                if (dn._split == null)     // Unable to decide?
                                    dn = treeT.decided(treeT.node(nid).pid());    // Then take parent's decision
                                leafnid = dn.getChildNodeID(chks, row); // Decide down to a leafnode
                            }
                            // Setup Tree(i) - on the fly prediction of i-tree for row-th row
                            //   - for uplift: cumulative sum of prediction of each tree - has to be normalized by number of trees
                            final Chunk ct1 = chk_tree(chks, 0); // treatment tree working column holding votes for given row
                            ct1.set(row, (float) (ct1.atd(row) + ((DTree.LeafNode) treeT.node(leafnid)).pred()));
                            final Chunk ct0 = chk_tree(chks, 1); // control group tree working column holding votes for given row
                            ct0.set(row, (float) (ct0.atd(row) + ((DTree.LeafNode) treeC.node(leafnid)).pred()));
                        }
                    }
                    // reset help column for this row and this k-class
                    nids.set(row, 0);
                    nids1.set(row, 0);
                    // For this tree this row is out-of-bag - i.e., a tree voted for this row
                    if (wasOOBRow) oobt.set(row, oobt.atd(row) + weight); // track number of trees
                    if (weight != 0) {
                        if (wasOOBRow && !y.isNA(row)) {
                            allRows+=weight;
                        }
                    }
                }
            }
            @Override public void reduce(UpliftCollectPreds mrt) {
                allRows    += mrt.allRows;
            }
        }



        @Override protected UpliftDRFModel makeModel( Key modelKey, UpliftDRFModel.UpliftDRFParameters parms) {
            return new UpliftDRFModel(modelKey,parms,new UpliftDRFModel.UpliftDRFOutput(UpliftDRF.this));
        }

    }

    /**
     * Read the 'tree' columns, do model-specific math and put the results in the
     * fs[] array, and return the sum.  Dividing any fs[] element by the sum
     * turns the results into a probability distribution.
     */
    @Override protected double score1( Chunk chks[], double weight, double offset, double fs[], int row ) {
        double sum = 0;
        fs[1] = weight * chk_tree(chks, 0).atd(row) / chk_oobt(chks).atd(row);
        fs[2] = weight * chk_tree(chks, 1).atd(row) / chk_oobt(chks).atd(row);
        fs[0] = fs[1] - fs[2];
        return sum;
    }

    protected DHistogram[][][] buildLayer(final Frame fr, final int nbins, final DTree tree, final int leafs[], final DHistogram hcs[][][], boolean build_tree_one_node) {
        // Build 1 uplift tree

        // Build up the next-generation tree splits from the current histograms.
        // Nearly all leaves will split one more level.  This loop nest is
        //           O( #active_splits * #bins * #ncols )
        // but is NOT over all the data.
        ScoreBuildOneTree sb1t = null;
        Vec vecs[] = fr.vecs();
        // Build a frame with just a single tree (& work & nid) columns, so the
        // nested MRTask ScoreBuildHistogram in ScoreBuildOneTree does not try
        // to close other tree's Vecs when run in parallel.
        int k = 0;
        if( tree != null ) {
            int selectedCol = _ncols + 2;
            final String[] fr2cols = Arrays.copyOf(fr._names, selectedCol);
            final Vec[] fr2vecs = Arrays.copyOf(vecs, selectedCol);
            Frame fr2 = new Frame(fr2cols, fr2vecs); //predictors, weights and the actual response
            if (isSupervised() && fr2.find(_parms._response_column) == -1) {
                fr2.add(_parms._response_column, fr.vec(_parms._response_column));
            }

            // Add temporary workspace vectors (optional weights are taken over from fr)
            int respIdx = fr2.find(_parms._response_column);
            int weightIdx = fr2.find(_parms._weights_column);
            int treatmentIdx = fr2.find(_parms._treatment_column);
            int predsIdx = fr2.numCols(); fr2.add(fr._names[idx_tree(k)],vecs[idx_tree(k)]); //tree predictions
            int workIdx =  fr2.numCols(); fr2.add(fr._names[idx_work(k)],vecs[idx_work(k)]); //target value to fit (copy of actual response for DRF, residual for GBM)
            int nidIdx  =  fr2.numCols(); fr2.add(fr._names[idx_nids(k)],vecs[idx_nids(k)]); //node indices for tree construction
            if (LOG.isTraceEnabled()) LOG.trace("Building a layer for class " + k + ":\n" + fr2.toTwoDimTable());
            // Async tree building
            // step 1: build histograms
            // step 2: split nodes
            H2O.submitTask(sb1t = new ScoreBuildOneTree(this,k, nbins, tree, leafs, hcs, fr2, build_tree_one_node, _improvPerVar, _model._parms._distribution,
                    respIdx, weightIdx, predsIdx, workIdx, nidIdx, treatmentIdx));
        }
        // Block for all K trees to complete.
        boolean did_split=false;
        if( sb1t != null ) {
            sb1t.join();
            if( sb1t._did_split ) did_split=true;
            if (LOG.isTraceEnabled()) {
                LOG.info("Done with this layer for class " + k + ":\n" + new Frame(
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

    @Override
    protected TwoDimTable createScoringHistoryTable() {
        UpliftDRFModel.UpliftDRFOutput out = _model._output;
        return createUpliftScoringHistoryTable(out, out._scored_train, out._scored_valid, _job,
                out._training_time_ms, _parms._custom_metric_func != null);
    }

    static TwoDimTable createUpliftScoringHistoryTable(Model.Output _output,
                                                       ScoreKeeper[] _scored_train,
                                                       ScoreKeeper[] _scored_valid,
                                                       Job job, long[] _training_time_ms,
                                                       boolean hasCustomMetric) {
        List<String> colHeaders = new ArrayList<>();
        List<String> colTypes = new ArrayList<>();
        List<String> colFormat = new ArrayList<>();
        colHeaders.add("Timestamp"); colTypes.add("string"); colFormat.add("%s");
        colHeaders.add("Duration"); colTypes.add("string"); colFormat.add("%s");
        colHeaders.add("Number of Trees"); colTypes.add("long"); colFormat.add("%d");
        colHeaders.add("Training ATE"); colTypes.add("double"); colFormat.add("%d");
        colHeaders.add("Training ATT"); colTypes.add("double"); colFormat.add("%d");
        colHeaders.add("Training ATC"); colTypes.add("double"); colFormat.add("%d");
        colHeaders.add("Training AUUC nbins"); colTypes.add("int"); colFormat.add("%d");
        colHeaders.add("Training AUUC"); colTypes.add("double"); colFormat.add("%.5f");
        colHeaders.add("Training AUUC normalized"); colTypes.add("double"); colFormat.add("%.5f");
        colHeaders.add("Training Qini value"); colTypes.add("double"); colFormat.add("%.5f");
        if (hasCustomMetric) {
            colHeaders.add("Training Custom"); colTypes.add("double"); colFormat.add("%.5f");
        }

        if (_output._validation_metrics != null) {
            colHeaders.add("Validation ATE"); colTypes.add("double"); colFormat.add("%d");
            colHeaders.add("Validation ATT"); colTypes.add("double"); colFormat.add("%d");
            colHeaders.add("Validation ATC"); colTypes.add("double"); colFormat.add("%d");
            colHeaders.add("Validation AUUC nbins"); colTypes.add("int"); colFormat.add("%d");
            colHeaders.add("Validation AUUC"); colTypes.add("double"); colFormat.add("%.5f");
            colHeaders.add("Validation AUUC normalized"); colTypes.add("double"); colFormat.add("%.5f");
            colHeaders.add("Validation Qini value"); colTypes.add("double"); colFormat.add("%.5f");
            if (hasCustomMetric) {
                colHeaders.add("Validation Custom"); colTypes.add("double"); colFormat.add("%.5f");
            }
        }

        int rows = 0;
        for( int i = 0; i<_scored_train.length; i++ ) {
            if (i != 0 && Double.isNaN(_scored_train[i]._AUUC) && (_scored_valid == null || Double.isNaN(_scored_valid[i]._AUUC))) continue;
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
        for( int i = 0; i<_scored_train.length; i++ ) {
            if (i != 0 && Double.isNaN(_scored_train[i]._AUUC) && (_scored_valid == null || Double.isNaN(_scored_valid[i]._AUUC))) continue;
            int col = 0;
            DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
            table.set(row, col++, fmt.print(_training_time_ms[i]));
            table.set(row, col++, PrettyPrint.msecs(_training_time_ms[i] - job.start_time(), true));
            table.set(row, col++, i);
            ScoreKeeper st = _scored_train[i];
            table.set(row, col++, st._ate);
            table.set(row, col++, st._att);
            table.set(row, col++, st._atc);
            table.set(row, col++, st._auuc_nbins);
            table.set(row, col++, st._AUUC);
            table.set(row, col++, st._auuc_normalized);
            table.set(row, col++, st._qini);
            if (hasCustomMetric) table.set(row, col++, st._custom_metric);

            if (_output._validation_metrics != null) {
                st = _scored_valid[i];
                table.set(row, col++, st._ate);
                table.set(row, col++, st._att);
                table.set(row, col++, st._atc);
                table.set(row, col++, st._auuc_nbins);
                table.set(row, col++, st._AUUC);
                table.set(row, col++, st._auuc_normalized);
                table.set(row, col++, st._qini);
                if (hasCustomMetric) table.set(row, col++, st._custom_metric);
            }
            row++;
        }
        return table;
    }

    @Override
    protected void addCustomInfo(UpliftDRFModel.UpliftDRFOutput out) {
        if(out._validation_metrics != null){
            out._defaultAuucThresholds = ((ModelMetricsBinomialUplift)out._validation_metrics)._auuc._ths;
        } else {
            out._defaultAuucThresholds = ((ModelMetricsBinomialUplift)out._training_metrics)._auuc._ths;
        }
    }

    @Override
    protected UpliftScoreExtension makeScoreExtension() {
        return new UpliftScoreExtension();
    }

    private static class UpliftScoreExtension extends Score.ScoreExtension {
        public UpliftScoreExtension() {
        }

        @Override
        protected double getPrediction(double[] cdist) {
            return cdist[1] - cdist[2];
        }

        @Override
        protected int[] getResponseComplements(SharedTreeModel<?, ?, ?> m) {
            return new int[]{m._output.treatmentIdx()};
        }
    }

}
