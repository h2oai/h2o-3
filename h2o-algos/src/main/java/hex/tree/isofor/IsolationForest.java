package hex.tree.isofor;

import hex.ModelCategory;
import hex.ScoreKeeper;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.*;
import hex.tree.DTree.DecidedNode;
import hex.tree.DTree.LeafNode;
import hex.tree.DTree.UndecidedNode;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import water.Job;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.PrettyPrint;
import water.util.TwoDimTable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static water.util.RandomUtils.getRNG;
import static hex.tree.isofor.IsolationForestModel.IsolationForestParameters;
import static hex.tree.isofor.IsolationForestModel.IsolationForestOutput;

/**
 * Isolation Forest
 */
public class IsolationForest extends SharedTree<IsolationForestModel, IsolationForestParameters, IsolationForestOutput> {

  @Override public ModelCategory[] can_build() {
    return new ModelCategory[]{
      ModelCategory.AnomalyDetection
    };
  }
  @Override public BuilderVisibility builderVisibility() {
    return BuilderVisibility.Beta;
  }

  // Called from an http request
  public IsolationForest(IsolationForestParameters parms                               ) { super(parms     ); init(false); }
  public IsolationForest(IsolationForestParameters parms, Key<IsolationForestModel> key) { super(parms, key); init(false); }
  public IsolationForest(IsolationForestParameters parms, Job job                      ) { super(parms, job); init(false); }
  public IsolationForest(boolean startup_once) { super(new IsolationForestParameters(), startup_once); }

  @Override protected Driver trainModelImpl() { return new IsolationForestDriver(); }

  @Override public boolean scoreZeroTrees() { return false; }

  @Override public boolean isSupervised() { return false; }

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
    if (_parms._distribution != DistributionFamily.AUTO && _parms._distribution != DistributionFamily.gaussian) {
      throw new IllegalStateException("Isolation Forest doesn't expect the distribution to be specified by the user");
    }
    _parms._distribution = DistributionFamily.gaussian;
  }

  @Override
  protected void validateRowSampleRate() {
    if (_parms._sample_rate == -1) {
      if (_parms._sample_size <= 0) {
        error("_sample_size", "Sample size needs to be a positive integer number but it is" + _parms._sample_size);
      } else if (_train != null && _train.numRows() > 0) {
        _parms._sample_rate = _parms._sample_size /  (double) _train.numRows();
      }
    }
  }

  private void randomResp(final long seed, final int iteration) {
    new MRTask() {
      @Override public void map(Chunk chks[]) {
        Chunk c = chk_work(chks, 0);
        final long chunk_seed = seed + (c.start() * (1 + iteration));
        for (int i = 0; i < c._len; i++) {
          double rnd = getRNG(chunk_seed + i).nextDouble();
          chk_work(chks, 0).set(i, rnd);
        }
      }
    }.doAll(_train);
  }

  @Override
  protected DTree.DecidedNode makeDecided(DTree.UndecidedNode udn, DHistogram hs[]) {
    return new IFDecidedNode(udn, hs);
  }

  private class IFDecidedNode extends DTree.DecidedNode {

    private IFDecidedNode(DTree.UndecidedNode n, DHistogram[] hs) {
      super(n, hs);
    }

    @Override
    public DTree.Split bestCol(DTree.UndecidedNode u, DHistogram hs[]) {
      if( hs == null ) return null;
      final int maxCols = u._scoreCols == null /* all cols */ ? hs.length : u._scoreCols.length;
      List<FindSplits> findSplits = new ArrayList<>();
      for (int i=0; i<maxCols; i++) {
        int col = u._scoreCols == null ? i : u._scoreCols[i];
        if( hs[col]==null || hs[col].nbins() <= 1 ) continue;
        findSplits.add(new FindSplits(hs, col, u));
      }
      Collections.shuffle(findSplits, _rand);
      for (FindSplits fs : findSplits) {
        DTree.Split s = fs.computeSplit();
        if (s != null) {
          return s;
        }
      }
      return null;
    }
  }

  // ----------------------
  private class IsolationForestDriver extends Driver {
    @Override protected boolean doOOBScoring() { return false; }

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

      _initialPrediction = 0;
    }

    // --------------------------------------------------------------------------
    // Build the next random k-trees representing tid-th tree
    @Override protected boolean buildNextKTrees() {
      // Create a Random response
      randomResp(_parms._seed, _model._output._ntrees);

      final long rseed = _rand.nextLong();
      final DTree tree = new DTree(_train, _ncols, _mtry, _mtry_per_tree, rseed, _parms);
      final DTree[] ktrees = {tree};

      new Sample(tree, _parms._sample_rate, null)
              .dfork(null, new Frame(vec_nids(_train, 0), vec_work(_train, 0)), _parms._build_tree_one_node)
              .getResult();

      // Assign rows to nodes - fill the "NIDs" column(s)
      growTree(rseed, ktrees);

      // Reset NIDs
      CalculatePaths stats = new CalculatePaths(ktrees[0]).doAll(_train, _parms._build_tree_one_node);

      // Grow the model by K-trees
      _model._output.addKTrees(ktrees);
      _model._output._min_path_length = stats._minPathLength;
      _model._output._max_path_length = stats._maxPathLength;

      return false; // never stop early
    }

    // Assumes that the "Work" column are filled with copy of a random generated response
    private void growTree(long rseed, final DTree[] ktrees) {
      // Initial set of histograms.  All trees; one leaf per tree (the root
      // leaf); all columns
      DHistogram hcs[][][] = new DHistogram[_nclass][1/*just root leaf*/][_ncols];

      // Adjust real bins for the top-levels
      int adj_nbins = Math.max(_parms._nbins_top_level,_parms._nbins);

      // Initially setup as-if an empty-split had just happened
      final DTree tree = ktrees[0];
      new UndecidedNode(tree, -1, DHistogram.initialHist(_train, _ncols, adj_nbins, hcs[0][0], rseed, _parms, getGlobalQuantilesKeys())); // The "root" node

      // ----
      // One Big Loop till the ktrees are of proper depth.
      // Adds a layer to the trees each pass.
      final int[] leafs = new int[1];
      for(int depth=0 ; depth<_parms._max_depth; depth++ ) {
        hcs = buildLayer(_train, _parms._nbins, _parms._nbins_cats, ktrees, leafs, hcs, _parms._build_tree_one_node);
        // If we did not make any new splits, then the tree is split-to-death
        if( hcs == null ) break;
      }

      // Each tree bottomed-out in a DecidedNode; go 1 more level and insert
      // LeafNodes to hold predictions.
      int leaf = tree.len();
      int depths[] = new int[leaf];
      for( int nid=0; nid<leaf; nid++ ) {
        if( tree.node(nid) instanceof DecidedNode ) {
          DecidedNode dn = tree.decided(nid);
          if( dn._split == null ) { // No decision here, no row should have this NID now
            if( nid==0 ) {               // Handle the trivial non-splitting tree
              LeafNode ln = new LeafNode(tree, -1, 0);
              ln._pred = 0;
            }
            continue;
          }
          depths[nid] = dn._pid >= 0 ? depths[dn._pid] + 1 : 0;
          for( int i=0; i<dn._nids.length; i++ ) {
            int cnid = dn._nids[i];
            if( cnid == -1 || // Bottomed out (predictors or responses known constant)
                    tree.node(cnid) instanceof UndecidedNode || // Or chopped off for depth
                    (tree.node(cnid) instanceof DecidedNode &&  // Or not possible to split
                            ((DecidedNode)tree.node(cnid))._split==null) ) {
              LeafNode ln = new LeafNode(tree,nid);
              ln._pred = depths[nid];  // Set depth as the prediction into the leaf
              dn._nids[i] = ln.nid(); // Mark a leaf here
            }
          }
        }
      }
    }

    // Collect and write predictions into leafs.
    private class CalculatePaths extends MRTask<CalculatePaths> {
      private final DTree _tree;
      private final int _ntrees;
      // OUT
      private long _minPathLength = Long.MAX_VALUE;
      private long _maxPathLength = 0;
      private CalculatePaths(DTree tree) { _tree = tree; _ntrees = _model._output._ntrees; }
      @Override public void map(Chunk[] chks) {
        final Chunk tree = chk_tree(chks, 0);
        final Chunk nids = chk_nids(chks, 0); // Node-ids  for this tree/class
        final Chunk oobt = chk_oobt(chks);
        for (int row = 0; row < nids._len; row++) {
          final boolean wasOOBRow = ScoreBuildHistogram.isOOBRow((int) chk_nids(chks,0).at8(row));
          if (wasOOBRow) {
            double oobcnt = oobt.atd(row) + 1;
            int nid = ScoreBuildHistogram.oob2Nid((int) nids.at8(row));
            int depth = getNodeDepth(chks, row, nid);
            long len = tree.at8(row) + depth;
            long total_len = (long) (len * _ntrees / oobcnt);
            tree.set(row, len);
            _maxPathLength = total_len > _maxPathLength ? total_len : _maxPathLength;
            _minPathLength = total_len < _minPathLength ? total_len : _minPathLength;
            oobt.set(row, oobcnt);
          }
          // reset NIds
          nids.set(row, 0);
        }
      }
      @Override public void reduce(CalculatePaths mrt) {
        _minPathLength = Math.min(_minPathLength, mrt._minPathLength);
        _maxPathLength = Math.max(_maxPathLength, mrt._maxPathLength);
      }
      int getNodeDepth(Chunk[] chks, int row, int nid) {
        if (_tree.root() instanceof LeafNode) {
          return 0;
        } else {
          if (_tree.node(nid) instanceof UndecidedNode)  // If we bottomed out the tree
            nid = _tree.node(nid).pid();                 // Then take parent's decision
          DecidedNode dn = _tree.decided(nid);           // Must have a decision point
          if (dn._split == null)                         // Unable to decide?
            dn = _tree.decided(_tree.node(nid).pid());   // Then take parent's decision
          int leafnid = dn.getChildNodeID(chks, row);    // Decide down to a leafnode
          double depth = ((LeafNode) _tree.node(leafnid)).pred();
          assert (int) depth == depth;
          return (int) depth;
        }
      }
    }

    @Override protected IsolationForestModel makeModel(Key modelKey, IsolationForestParameters parms) {
      return new IsolationForestModel(modelKey, parms, new IsolationForestOutput(IsolationForest.this));
    }

  }

  @Override protected double score1( Chunk chks[], double weight, double offset, double fs[/*nclass*/], int row ) {
    assert weight == 1;
    double len = chk_tree(chks, 0).atd(row);
    if (len < 0) {
      fs[0] = -1;
      fs[1] = 0;
      return fs[0];
    }
    fs[0] = _model.normalizePathLength(len); // score
    fs[1] = len / chk_oobt(chks).atd(row); // average tree path length
    return fs[0];
  }

  protected TwoDimTable createScoringHistoryTable() {
    List<String> colHeaders = new ArrayList<>();
    List<String> colTypes = new ArrayList<>();
    List<String> colFormat = new ArrayList<>();
    colHeaders.add("Timestamp"); colTypes.add("string"); colFormat.add("%s");
    colHeaders.add("Duration"); colTypes.add("string"); colFormat.add("%s");
    colHeaders.add("Number of Trees"); colTypes.add("long"); colFormat.add("%d");
    colHeaders.add("Mean Tree Path Length"); colTypes.add("double"); colFormat.add("%.5f");
    colHeaders.add("Mean Anomaly Score"); colTypes.add("double"); colFormat.add("%.5f");
    if (_parms._custom_metric_func != null) {
      colHeaders.add("Training Custom"); colTypes.add("double"); colFormat.add("%.5f");
    }

    ScoreKeeper[] sks = _model._output._scored_train;

    int rows = 0;
    for (int i = 0; i < sks.length; i++) {
      if (i != 0 && Double.isNaN(sks[i]._anomaly_score)) continue;
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
    for( int i = 0; i<sks.length; i++ ) {
      if (i != 0 && Double.isNaN(sks[i]._anomaly_score)) continue;
      int col = 0;
      DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
      table.set(row, col++, fmt.print(_model._output._training_time_ms[i]));
      table.set(row, col++, PrettyPrint.msecs(_model._output._training_time_ms[i] - _job.start_time(), true));
      table.set(row, col++, i);
      ScoreKeeper st = sks[i];
      table.set(row, col++, st._anomaly_score);
      table.set(row, col++, st._anomaly_score_normalized);
      if (_parms._custom_metric_func != null) {
        table.set(row, col++, st._custom_metric);
      }
      assert col == colHeaders.size();
      row++;
    }
    return table;
  }

  @Override
  public boolean havePojo() {
    return false;
  }

  @Override
  public boolean haveMojo() {
    return true;
  }

}
