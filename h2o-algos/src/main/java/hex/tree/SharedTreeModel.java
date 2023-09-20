package hex.tree;

import hex.*;
import hex.genmodel.CategoricalEncoding;
import hex.genmodel.algos.tree.SharedTreeMojoModel;
import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.tree.uplift.UpliftDRFModel;
import hex.util.LinearAlgebraUtils;
import org.apache.log4j.Logger;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.util.*;

import java.util.ArrayList;
import java.util.Arrays;

import static hex.genmodel.GenModel.createAuxKey;
import static hex.genmodel.algos.tree.SharedTreeMojoModel.__INTERNAL_MAX_TREE_DEPTH;
import static hex.tree.SharedTree.createModelSummaryTable;

public abstract class SharedTreeModel<
        M extends SharedTreeModel<M, P, O>,
        P extends SharedTreeModel.SharedTreeParameters,
        O extends SharedTreeModel.SharedTreeOutput
        > extends Model<M, P, O> implements Model.LeafNodeAssignment, Model.GetMostImportantFeatures, Model.FeatureFrequencies, Model.UpdateAuxTreeWeights {

  private static final Logger LOG = Logger.getLogger(SharedTreeModel.class);

  @Override
  public String[] getMostImportantFeatures(int n) {
    if (_output == null) return null;
    TwoDimTable vi = _output._variable_importances;
    if (vi==null) return null;
    n = Math.min(n, vi.getRowHeaders().length);
    String[] res = new String[n];
    System.arraycopy(vi.getRowHeaders(), 0, res, 0, n);
    return res;
  }

  @Override public ToEigenVec getToEigenVec() { return LinearAlgebraUtils.toEigen; }

  public abstract static class SharedTreeParameters extends Model.Parameters implements Model.GetNTrees, CalibrationHelper.ParamsWithCalibration {

    public int _ntrees=50; // Number of trees in the final model. Grid Search, comma sep values:50,100,150,200

    public int _max_depth = 5; // Maximum tree depth. Grid Search, comma sep values:5,7

    public double _min_rows = 10; // Fewest allowed observations in a leaf (in R called 'nodesize'). Grid Search, comma sep values

    public int _nbins = 20; // Numerical (real/int) cols: Build a histogram of this many bins, then split at the best point

    public int _nbins_cats = 1024; // Categorical (factor) cols: Build a histogram of this many bins, then split at the best point

    public double _min_split_improvement = 1e-5; // Minimum relative improvement in squared error reduction for a split to happen

    public enum HistogramType {
      AUTO, UniformAdaptive, Random, QuantilesGlobal, RoundRobin, UniformRobust;
      public static HistogramType[] ROUND_ROBIN_CANDIDATES = {
              AUTO, // Note: the inclusion of AUTO means UniformAdaptive has effectively higher chance of being used
              UniformAdaptive, Random, QuantilesGlobal
      };
    }
    public HistogramType _histogram_type = HistogramType.AUTO; // What type of histogram to use for finding optimal split points

    public double _r2_stopping = Double.MAX_VALUE; // Stop when the r^2 metric equals or exceeds this value

    public int _nbins_top_level = 1<<10; //hardcoded maximum top-level number of bins for real-valued columns

    public boolean _build_tree_one_node = false;

    public int _score_tree_interval = 0; // score every so many trees (no matter what)

    public int _initial_score_interval = 4000; //Adding this parameter to take away the hard coded value of 4000 for scoring the first  4 secs

    public int _score_interval = 4000; //Adding this parameter to take away the hard coded value of 4000 for scoring each iteration every 4 secs

    public double _sample_rate = 0.632; //fraction of rows to sample for each tree

    public double[] _sample_rate_per_class; //fraction of rows to sample for each tree, per class

    public boolean useRowSampling() {
      return _sample_rate < 1 || _sample_rate_per_class != null;
    }

    // Platt scaling (by default)
    public boolean _calibrate_model;
    public Key<Frame> _calibration_frame;
    public CalibrationHelper.CalibrationMethod _calibration_method = CalibrationHelper.CalibrationMethod.AUTO;

    @Override public long progressUnits() { return _ntrees + (_histogram_type==HistogramType.QuantilesGlobal || _histogram_type==HistogramType.RoundRobin ? 1 : 0); }

    public double _col_sample_rate_change_per_level = 1.0f; //relative change of the column sampling rate for every level
    public double _col_sample_rate_per_tree = 1.0f; //fraction of columns to sample for each tree

    public boolean useColSampling() {
      return _col_sample_rate_change_per_level != 1.0f || _col_sample_rate_per_tree != 1.0f;
    }

    public boolean isStochastic() {
      return useRowSampling() || useColSampling();
    }

    public boolean _parallel_main_model_building = false;

    public boolean _use_best_cv_iteration = true; // when early stopping is enabled, cv models will pick the iteration that produced the best score instead of the stopping iteration

    public String _in_training_checkpoints_dir;

    public int _in_training_checkpoints_tree_interval = 1;  // save model checkpoint every so many trees (no matter what)

    /** Fields which can NOT be modified if checkpoint is specified.
     * FIXME: should be defined in Schema API annotation
     */
    static final String[] CHECKPOINT_NON_MODIFIABLE_FIELDS = { "_build_tree_one_node", "_sample_rate", "_max_depth", "_min_rows", "_nbins", "_nbins_cats", "_nbins_top_level"};

    @Override
    public int getNTrees() {
      return _ntrees;
    }

    @Override
    public Frame getCalibrationFrame() { 
      return _calibration_frame == null ? null : _calibration_frame.get(); 
    }

    @Override
    public boolean calibrateModel() {
      return _calibrate_model;
    }

    @Override
    public CalibrationHelper.CalibrationMethod getCalibrationMethod() {
      return _calibration_method;
    }

    @Override
    public void setCalibrationMethod(CalibrationHelper.CalibrationMethod calibrationMethod) {
      _calibration_method = calibrationMethod;
    }

    @Override
    public Parameters getParams() {
      return this;
    }

    /**
     * Do we need to enable strictly deterministic way of building histograms?
     *
     * Used eg. when monotonicity constraints in GBM are enabled, by default disabled
     *
     * @return true if histograms should be built in deterministic way
     */
    public boolean forceStrictlyReproducibleHistograms() {
      return false;
    }

  }

  @Override public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    switch(_output.getModelCategory()) {
      case Binomial:    return new ModelMetricsBinomial.MetricBuilderBinomial(domain);
      case Multinomial: return new ModelMetricsMultinomial.MetricBuilderMultinomial(_output.nclasses(),domain, _parms._auc_type);
      case Regression:  return new ModelMetricsRegression.MetricBuilderRegression();
      case BinomialUplift: return new ModelMetricsBinomialUplift.MetricBuilderBinomialUplift(domain, ((UpliftDRFModel.UpliftDRFOutput)_output)._defaultAuucThresholds);
      default: throw H2O.unimpl();
    }
  }

  public abstract static class SharedTreeOutput extends Model.Output implements Model.GetNTrees, CalibrationHelper.OutputWithCalibration {
    /** InitF value (for zero trees)
     *  f0 = mean(yi) for gaussian
     *  f0 = log(yi/1-yi) for bernoulli
     *
     *  For GBM bernoulli, the initial prediction for 0 trees is
     *  p = 1/(1+exp(-f0))
     *
     *  From this, the mse for 0 trees (null model) can be computed as follows:
     *  mean((yi-p)^2)
     * */
    public double _init_f;

    /** Number of trees actually in the model (as opposed to requested) */
    public int _ntrees;

    /** More indepth tree stats */
    public final TreeStats _treeStats;

    /** Trees get big, so store each one separately in the DKV. */
    public Key<CompressedTree>[/*_ntrees*/][/*_nclass*/] _treeKeys;
    public Key<CompressedTree>[/*_ntrees*/][/*_nclass*/] _treeKeysAux;

    public ScoreKeeper[/*ntrees+1*/] _scored_train;
    public ScoreKeeper[/*ntrees+1*/] _scored_valid;
    public ScoreKeeper[] scoreKeepers() {
      ArrayList<ScoreKeeper> skl = new ArrayList<>();
      ScoreKeeper[] ska = _validation_metrics != null ? _scored_valid : _scored_train;
      for( ScoreKeeper sk : ska )
        if (!sk.isEmpty())
          skl.add(sk);
      return skl.toArray(new ScoreKeeper[skl.size()]);
    }
    /** Training time */
    public long[/*ntrees+1*/] _training_time_ms = {System.currentTimeMillis()};

    /**
     * Variable importances computed during training
     */
    public TwoDimTable _variable_importances;
    public VarImp _varimp;
    @Override
    public TwoDimTable getVariableImportances() {
      return _variable_importances;
    }

    public Model<?, ?, ?> _calib_model;

    public SharedTreeOutput( SharedTree b) {
      super(b);
      _ntrees = 0;              // No trees yet
      _treeKeys = new Key[_ntrees][]; // No tree keys yet
      _treeKeysAux = new Key[_ntrees][]; // No tree keys yet
      _treeStats = new TreeStats();
      _scored_train = new ScoreKeeper[]{new ScoreKeeper(Double.NaN)};
      _scored_valid = new ScoreKeeper[]{new ScoreKeeper(Double.NaN)};
      _modelClassDist = _priorClassDist;
    }

    @Override
    public TwoDimTable createInputFramesInformationTable(ModelBuilder modelBuilder) {
      SharedTreeParameters params = (SharedTreeParameters) modelBuilder._parms;
      TwoDimTable table = super.createInputFramesInformationTable(modelBuilder);
      table.set(2, 0, "calibration_frame");
      table.set(2, 1, params.getCalibrationFrame() != null ? params.getCalibrationFrame().checksum() : -1);
      table.set(2, 2, params.getCalibrationFrame() != null ? Arrays.toString(params.getCalibrationFrame().anyVec().espc()) : -1);
      return table;
    }

    @Override
    public int getInformationTableNumRows() {
      return super.getInformationTableNumRows() + 1;// +1 row for calibration frame
    }

    // Append next set of K trees
    public void addKTrees( DTree[] trees) {
      // DEBUG: Print the generated K trees
      //SharedTree.printGenerateTrees(trees);
      assert nclasses()==trees.length;
      // Compress trees and record tree-keys
      _treeKeys = Arrays.copyOf(_treeKeys ,_ntrees+1);
      _treeKeysAux = Arrays.copyOf(_treeKeysAux ,_ntrees+1);
      Key[] keys = _treeKeys[_ntrees] = new Key[trees.length];
      Key[] keysAux = _treeKeysAux[_ntrees] = new Key[trees.length];
      Futures fs = new Futures();
      for( int i=0; i<nclasses(); i++ ) if( trees[i] != null ) {
        CompressedTree ct = trees[i].compress(_ntrees,i,_domains);
        DKV.put(keys[i]=ct._key,ct,fs);
        _treeStats.updateBy(trees[i]); // Update tree shape stats

        CompressedTree ctAux = new CompressedTree(trees[i]._abAux.buf(),-1,-1,-1);
        keysAux[i] = ctAux._key = Key.make(createAuxKey(ct._key.toString()));
        DKV.put(ctAux,fs);
      }
      _ntrees++;
      // 1-based for errors; _scored_train[0] is for zero trees, not 1 tree
      _scored_train = ArrayUtils.copyAndFillOf(_scored_train, _ntrees+1, new ScoreKeeper());
      _scored_valid = _scored_valid != null ? ArrayUtils.copyAndFillOf(_scored_valid, _ntrees+1, new ScoreKeeper()) : null;
      _training_time_ms = ArrayUtils.copyAndFillOf(_training_time_ms, _ntrees+1, System.currentTimeMillis());
      fs.blockForPending();
    }

    public void trimTo(final int ntrees) {
      Futures fs = new Futures();
      for (int i = ntrees; i < _treeKeys.length; i++) {
        for (int tc = 0; tc < _treeKeys[i].length; tc++) {
          if (_treeKeys[i][tc] == null)
            continue;
          DKV.remove(_treeKeys[i][tc], fs);
          DKV.remove(_treeKeysAux[i][tc], fs);
        }
      }
      _ntrees = ntrees;
      _treeKeys = Arrays.copyOf(_treeKeys ,_ntrees);
      _treeKeysAux = Arrays.copyOf(_treeKeysAux ,_ntrees);
      // 1-based for errors; _scored_train[0] is for zero trees, not 1 tree
      _scored_train = Arrays.copyOf(_scored_train, _ntrees + 1);
      _scored_valid = _scored_valid != null ? Arrays.copyOf(_scored_valid, _ntrees + 1) : null;
      _training_time_ms = Arrays.copyOf(_training_time_ms, _ntrees + 1);
      _model_summary = createModelSummaryTable(_ntrees, _treeStats);
      fs.blockForPending();
    }

    @Override
    public int getNTrees() {
      return _ntrees;
    }

    @Override
    public Model<?, ?, ?> calibrationModel() {
      return _calib_model;
    }

    @Override
    public void setCalibrationModel(Model<?, ?, ?> model) {
      _calib_model = model;
    }

    public CompressedTree ctree(int tnum, int knum ) { return _treeKeys[tnum][knum].get(); }
    public String toStringTree ( int tnum, int knum ) { return ctree(tnum,knum).toString(this); }
  }

  public SharedTreeModel(Key<M> selfKey, P parms, O output) {
    super(selfKey, parms, output);
  }

  protected String[] makeAllTreeColumnNames() {
    int classTrees = 0;
    for (int i = 0; i < _output._treeKeys[0].length; ++i) {
      if (_output._treeKeys[0][i] != null) classTrees++;
    }
    final int outputcols = _output._treeKeys.length * classTrees;
    final String[] names = new String[outputcols];
    int col = 0;
    for (int tidx = 0; tidx < _output._treeKeys.length; tidx++) {
      Key[] keys = _output._treeKeys[tidx];
      for (int c = 0; c < keys.length; c++) {
        if (keys[c] != null) {
          names[col++] = "T" + (tidx + 1) + (keys.length == 1 ? "" : (".C" + (c + 1)));
        }
      }
    }
    return names;
  }

  @Override
  public Frame scoreLeafNodeAssignment(Frame frame, LeafNodeAssignmentType type, Key<Frame> destination_key) {
    Frame adaptFrm = new Frame(frame);
    adaptTestForTrain(adaptFrm, true, false);

    final String[] names = makeAllTreeColumnNames();
    AssignLeafNodeTaskBase task = AssignLeafNodeTaskBase.make(_output, type);
    return task.execute(adaptFrm, names, destination_key);
  }

  @Override
  public UpdateAuxTreeWeightsReport updateAuxTreeWeights(Frame frame, String weightsColumn) {
    if (weightsColumn == null) {
      throw new IllegalArgumentException("Weights column name is not defined");
    }
    Frame adaptFrm = new Frame(frame);
    Vec weights = adaptFrm.remove(weightsColumn);
    if (weights == null) {
      throw new IllegalArgumentException("Input frame doesn't contain weights column `" + weightsColumn + "`");
    }
    adaptTestForTrain(adaptFrm, true, false);
    // keep features only and re-introduce weights column at the end of the frame
    Frame featureFrm = new Frame(_output.features(), frame.vecs(_output.features()));
    featureFrm.add(weightsColumn, weights);

    UpdateAuxTreeWeightsTask t = new UpdateAuxTreeWeightsTask(_output).doAll(featureFrm);
    UpdateAuxTreeWeights.UpdateAuxTreeWeightsReport report = new UpdateAuxTreeWeights.UpdateAuxTreeWeightsReport();
    report._warn_trees = t._warnTrees;
    report._warn_classes = t._warnClasses;
    return report;
  }

  public static class BufStringDecisionPathTracker implements SharedTreeMojoModel.DecisionPathTracker<BufferedString> {
    private final byte[] _buf = new byte[__INTERNAL_MAX_TREE_DEPTH];
    private final BufferedString _bs = new BufferedString(_buf, 0, 0);
    private int _pos = 0;
    @Override
    public boolean go(int depth, boolean right) {
      _buf[depth] = right ? (byte) 'R' : (byte) 'L';
      if (right) _pos = depth;
      return true;
    }
    @Override
    public BufferedString terminate() {
      _bs.setLen(_pos);
      _pos = 0;
      return _bs;
    }
    @Override
    public BufferedString invalidPath() {
      return null;
    }
  }

  private static abstract class AssignLeafNodeTaskBase extends MRTask<AssignLeafNodeTaskBase> {
    final Key<CompressedTree>[/*_ntrees*/][/*_nclass*/] _treeKeys;
    final String[][] _domains;

    AssignLeafNodeTaskBase(SharedTreeOutput output) {
      _treeKeys = output._treeKeys;
      _domains = output._domains;
    }

    protected abstract void initMap();

    protected abstract void assignNode(final int tidx, final int cls, final CompressedTree tree, final double[] input,
                                       final NewChunk out);

    @Override
    public void map(Chunk[] chks, NewChunk[] ncs) {
      double[] input = new double[chks.length];

      initMap();
      for (int row = 0; row < chks[0]._len; row++) {
        for (int i = 0; i < chks.length; i++)
          input[i] = chks[i].atd(row);

        int col = 0;
        for (int tidx = 0; tidx < _treeKeys.length; tidx++) {
          Key[] keys = _treeKeys[tidx];
          for (int cls = 0; cls < keys.length; cls++) {
            Key key = keys[cls];
            if (key != null) {
              CompressedTree tree = DKV.get(key).get();
              assignNode(tidx, cls, tree, input, ncs[col++]);
            }
          }
        }
        assert (col == ncs.length);
      }
    }

    protected abstract Frame execute(Frame adaptFrm, String[] names, Key<Frame> destKey);

    private static AssignLeafNodeTaskBase make(SharedTreeOutput modelOutput, LeafNodeAssignmentType type) {
      switch (type) {
        case Path:
          return new AssignTreePathTask(modelOutput);
        case Node_ID:
          return new AssignLeafNodeIdTask(modelOutput);
        default:
          throw new UnsupportedOperationException("Unknown leaf node assignment type: " + type);
      }
    }
  }
  
  private static class AssignTreePathTask extends AssignLeafNodeTaskBase {
    private transient BufStringDecisionPathTracker _tr;

    private AssignTreePathTask(SharedTreeOutput output) {
      super(output);
    }

    @Override
    protected void initMap() {
      _tr = new BufStringDecisionPathTracker();
    }

    @Override
    protected void assignNode(int tidx, int cls, CompressedTree tree, double[] input, 
                              NewChunk nc) {
      BufferedString pred = tree.getDecisionPath(input, _domains, _tr);
      nc.addStr(pred);
    }

    @Override
    protected Frame execute(Frame adaptFrm, String[] names, Key<Frame> destKey) {
      Frame res = doAll(names.length, Vec.T_STR, adaptFrm).outputFrame(destKey, names, null);
      // convert to categorical
      Vec vv;
      Vec[] nvecs = new Vec[res.vecs().length];
      boolean hasInvalidPaths = false;
      for(int c=0;c<res.vecs().length;++c) {
        vv = res.vec(c);
        try {
          hasInvalidPaths = hasInvalidPaths || vv.naCnt() > 0;
          nvecs[c] = vv.toCategoricalVec();
        } catch (Exception e) {
          VecUtils.deleteVecs(nvecs, c);
          throw e;
        }
      }
      res.delete();
      res = new Frame(destKey, names, nvecs);
      if (destKey != null) {
        DKV.put(res);
      }
      if (hasInvalidPaths) {
        LOG.warn("Some of the leaf node assignments were skipped (NA), " +
                "only tree-paths up to length 64 are supported.");
      }
      return res;
    }
  }
  
  private static class AssignLeafNodeIdTask extends AssignLeafNodeTaskBase {
    final Key<CompressedTree>[/*_ntrees*/][/*_nclass*/] _auxTreeKeys;

    private AssignLeafNodeIdTask(SharedTreeOutput output) {
      super(output);
      _auxTreeKeys = output._treeKeysAux;
    }

    @Override
    protected void initMap() {
    }

    @Override
    protected void assignNode(int tidx, int cls, CompressedTree tree, double[] input, NewChunk nc) {
      CompressedTree auxTree = _auxTreeKeys[tidx][cls].get();
      assert auxTree != null;

      final double d = SharedTreeMojoModel.scoreTree(tree._bits, input, true, _domains);
      final int nodeId = SharedTreeMojoModel.getLeafNodeId(d, auxTree._bits);

      nc.addNum(nodeId, 0);
    }

    @Override
    protected Frame execute(Frame adaptFrm, String[] names, Key<Frame> destKey) {
      Frame result = doAll(names.length, Vec.T_NUM, adaptFrm).outputFrame(destKey, names, null);
      if (result.vec(0).min() < 0) {
        LOG.warn("Some of the observations were not assigned a Leaf Node ID (-1), " +
                "only tree-paths up to length 64 are supported.");
      }
      return result;
    }
  }

  private static class UpdateAuxTreeWeightsTask extends MRTask<UpdateAuxTreeWeightsTask> {
    // IN
    private final Key<CompressedTree>[/*_ntrees*/][/*_nclass*/] _treeKeys;
    private final Key<CompressedTree>[/*_ntrees*/][/*_nclass*/] _auxTreeKeys;
    private final String[][] _domains;
    // WORKING
    private transient int[/*treeId*/][/*classId*/] _maxNodeIds;
    // OUT
    private double[/*treeId*/][/*classId*/][/*leafNodeId*/] _leafNodeWeights;
    private int[] _warnTrees;
    private int[] _warnClasses;

    private UpdateAuxTreeWeightsTask(SharedTreeOutput output) {
      _treeKeys = output._treeKeys;
      _auxTreeKeys = output._treeKeysAux;
      _domains = output._domains;
    }

    @Override
    protected void setupLocal() {
      _maxNodeIds = new int[_auxTreeKeys.length][];
      for (int treeId = 0; treeId < _auxTreeKeys.length; treeId++) {
        Key<CompressedTree>[] classAuxTreeKeys = _auxTreeKeys[treeId];
        _maxNodeIds[treeId] = new int[classAuxTreeKeys.length];
        for (int classId = 0; classId < classAuxTreeKeys.length; classId++) {
          if (classAuxTreeKeys[classId] == null) {
            _maxNodeIds[treeId][classId] = -1;
            continue;
          }
          CompressedTree tree = classAuxTreeKeys[classId].get();
          assert tree != null;
          _maxNodeIds[treeId][classId] = tree.findMaxNodeId();
        }
      }
    }

    protected void initMap() {
      _leafNodeWeights = new double[_maxNodeIds.length][][];
      for (int treeId = 0; treeId < _maxNodeIds.length; treeId++) {
        int[] classMaxNodeIds = _maxNodeIds[treeId];
        _leafNodeWeights[treeId] = new double[classMaxNodeIds.length][];
        for (int classId = 0; classId < classMaxNodeIds.length; classId++) {
          if (classMaxNodeIds[classId] < 0)
            continue;
          _leafNodeWeights[treeId][classId] = new double[classMaxNodeIds[classId] + 1];
        }
      }
    }

    @Override
    public void map(Chunk[] chks) {
      double[] input = new double[chks.length - 1];

      initMap();
      for (int row = 0; row < chks[0]._len; row++) {
        double weight = chks[input.length].atd(row);
        if (weight == 0 || Double.isNaN(weight))
          continue;
        for (int i = 0; i < input.length; i++)
          input[i] = chks[i].atd(row);

        for (int tidx = 0; tidx < _treeKeys.length; tidx++) {
          Key<CompressedTree>[] keys = _treeKeys[tidx];
          for (int cls = 0; cls < keys.length; cls++) {
            Key<CompressedTree> key = keys[cls];
            if (key != null) {
              CompressedTree tree = DKV.get(key).get();
              CompressedTree auxTree = _auxTreeKeys[tidx][cls].get();
              assert auxTree != null;

              final double d = SharedTreeMojoModel.scoreTree(tree._bits, input, true, _domains);
              final int nodeId = SharedTreeMojoModel.getLeafNodeId(d, auxTree._bits);

              _leafNodeWeights[tidx][cls][nodeId] += weight;
            }
          }
        }
      }
    }

    @Override
    public void reduce(UpdateAuxTreeWeightsTask mrt) {
      ArrayUtils.add(_leafNodeWeights, mrt._leafNodeWeights);
    }

    @Override
    protected void postGlobal() {
      _warnTrees = new int[0];
      _warnClasses = new int[0];
      Futures fs = new Futures();
      for (int treeId = 0; treeId < _leafNodeWeights.length; treeId++) {
        double[][] classWeights = _leafNodeWeights[treeId];
        for (int classId = 0; classId < classWeights.length; classId++) {
          double[] nodeWeights = classWeights[classId];
          if (nodeWeights == null)
            continue;
          CompressedTree auxTree = _auxTreeKeys[treeId][classId].get();
          assert auxTree != null;
          CompressedTree updatedTree = auxTree.updateLeafNodeWeights(nodeWeights);
          assert auxTree._key.equals(updatedTree._key);
          DKV.put(updatedTree, fs);
          if (updatedTree.hasZeroWeight()) {
            _warnTrees = ArrayUtils.append(_warnTrees, treeId);
            _warnClasses = ArrayUtils.append(_warnClasses, classId);
          }
        }
      }
      fs.blockForPending();
      assert _warnTrees.length == _warnClasses.length;
    }
  }
  
  @Override
  public Frame scoreFeatureFrequencies(Frame frame, Key<Frame> destination_key) {
    Frame adaptFrm = new Frame(frame);
    adaptTestForTrain(adaptFrm, true, false);

    // remove non-feature columns
    adaptFrm.remove(_parms._response_column);
    adaptFrm.remove(_parms._fold_column);
    adaptFrm.remove(_parms._weights_column);
    adaptFrm.remove(_parms._offset_column);
    if(_parms._treatment_column != null){
      adaptFrm.remove(_parms._treatment_column);
    }

    assert adaptFrm.numCols() == _output.nfeatures();

    return new ScoreFeatureFrequenciesTask(_output)
            .doAll(adaptFrm.numCols(), Vec.T_NUM, adaptFrm)
            .outputFrame(destination_key, adaptFrm.names(), null);
  }

  private static class ComputeSharedTreesFun extends MrFun<ComputeSharedTreesFun> {
    final Key<CompressedTree>[/*_ntrees*/][/*_nclass*/] _treeKeys;
    final Key<CompressedTree>[/*_ntrees*/][/*_nclass*/] _auxTreeKeys;
    final String[] _names;
    final String[][] _domains;
    
    transient SharedTreeSubgraph[/*_ntrees*/][/*_nclass*/] _trees;

    ComputeSharedTreesFun(SharedTreeSubgraph[][] trees, 
                          Key<CompressedTree>[][] treeKeys, Key<CompressedTree>[][] auxTreeKeys,
                          String[] names, String[][] domains) {
      _trees = trees;
      _treeKeys = treeKeys;
      _auxTreeKeys = auxTreeKeys;
      _names = names;
      _domains = domains;
    }

    @Override
    protected void map(int t) {
      for (int c = 0; c < _treeKeys[t].length; c++) {
        if (_treeKeys[t][c] == null)
          continue;
        _trees[t][c] = SharedTreeMojoModel.computeTreeGraph(0, "T",
                _treeKeys[t][c].get()._bits, _auxTreeKeys[t][c].get()._bits, _names, _domains);
      }
    }
  }

  private static class ScoreFeatureFrequenciesTask extends MRTask<ScoreFeatureFrequenciesTask> {
    final Key<CompressedTree>[/*_ntrees*/][/*_nclass*/] _treeKeys;
    final Key<CompressedTree>[/*_ntrees*/][/*_nclass*/] _auxTreeKeys;
    final String _domains[][];

    transient SharedTreeSubgraph[/*_ntrees*/][/*_nclass*/] _trees;
    
    ScoreFeatureFrequenciesTask(SharedTreeOutput output) {
      _treeKeys = output._treeKeys;
      _auxTreeKeys = output._treeKeysAux;
      _domains = output._domains;
    }

    @Override
    protected void setupLocal() {
      _trees = new SharedTreeSubgraph[_treeKeys.length][];
      for (int t = 0; t < _treeKeys.length; t++) {
        _trees[t] = new SharedTreeSubgraph[_treeKeys[t].length];
      }
      MrFun<?> getSharedTreesFun = new ComputeSharedTreesFun(_trees, _treeKeys, _auxTreeKeys, _fr.names(), _domains);
      H2O.submitTask(new LocalMR(getSharedTreesFun, _trees.length)).join();
    }

    @Override
    public void map(Chunk[] cs, NewChunk[] ncs) {
      double[] input = new double[cs.length];
      int[] output = new int[ncs.length];
      for (int r = 0; r < cs[0]._len; r++) {
        for (int i = 0; i < cs.length; i++)
          input[i] = cs[i].atd(r);
        Arrays.fill(output, 0);
        
        for (int t = 0; t < _treeKeys.length; t++) {
          for (int c = 0; c < _treeKeys[t].length; c++) {
            if (_treeKeys[t][c] == null)
              continue;
            double d = SharedTreeMojoModel.scoreTree(_treeKeys[t][c].get()._bits, input, true, _domains);
            String decisionPath = SharedTreeMojoModel.getDecisionPath(d);
            SharedTreeNode n = _trees[t][c].walkNodes(decisionPath);
            updateStats(n, output);
          }
        }

        for (int i = 0; i < ncs.length; i++) {
          ncs[i].addNum(output[i]);
        }
      }
    }

    private void updateStats(final SharedTreeNode leaf, int[] stats) {
      SharedTreeNode n = leaf.getParent();
      while (n != null) {
        stats[n.getColId()]++;
        n = n.getParent();
      }
    }
  }

  @Override
  protected Frame postProcessPredictions(Frame adaptedFrame, Frame predictFr, Job j) {
    return CalibrationHelper.postProcessPredictions(predictFr, j, _output);
  }

  protected double[] score0Incremental(Score.ScoreIncInfo sii, Chunk chks[], double offset, int row_in_chunk, double[] tmp, double[] preds) {
    return score0(chks, offset, row_in_chunk, tmp, preds); // by default delegate to non-incremental implementation
  }

  @Override protected double[] score0(double[] data, double[] preds, double offset) {
    return score0(data, preds, offset, _output._treeKeys.length);
  }
  @Override protected double[] score0(double[/*ncols*/] data, double[/*nclasses+1*/] preds) {
    return score0(data, preds, 0.0);
  }

  protected double[] score0(double[] data, double[] preds, double offset, int ntrees) {
    Arrays.fill(preds,0);
    return score0(data, preds, offset, 0, ntrees);
  }

  protected double[] score0(double[] data, double[] preds, double offset, int startTree, int ntrees) {
    // Prefetch trees into the local cache if it is necessary
    // Invoke scoring
    for( int tidx=startTree; tidx<ntrees; tidx++ )
      score0(data, preds, tidx);
    return preds;
  }

  // Score per line per tree
  private void score0(double[] data, double[] preds, int treeIdx) {
    Key[] keys = _output._treeKeys[treeIdx];
    for( int c=0; c<keys.length; c++ ) {
      if (keys[c] != null) {
        double pred = DKV.get(keys[c]).<CompressedTree>get().score(data,_output._domains);
        assert (!Double.isInfinite(pred));
        preds[keys.length == 1 ? 0 : c + 1] += pred;
      }
    }
  }

  /** Performs deep clone of given model.  */
  protected M deepClone(Key<M> result) {
    M newModel = IcedUtils.deepCopy(self());
    newModel._key = result;
    // Do not clone model metrics
    newModel._output.clearModelMetrics(false);
    newModel._output._training_metrics = null;
    newModel._output._validation_metrics = null;
    // Clone trees
    Key[][] treeKeys = newModel._output._treeKeys;
    for (int i = 0; i < treeKeys.length; i++) {
      for (int j = 0; j < treeKeys[i].length; j++) {
        if (treeKeys[i][j] == null) continue;
        CompressedTree ct = DKV.get(treeKeys[i][j]).get();
        CompressedTree newCt = IcedUtils.deepCopy(ct);
        newCt._key = CompressedTree.makeTreeKey(i, j);
        DKV.put(treeKeys[i][j] = newCt._key,newCt);
      }
    }
    // Clone Aux info
    Key[][] treeKeysAux = newModel._output._treeKeysAux;
    if (treeKeysAux!=null) {
      for (int i = 0; i < treeKeysAux.length; i++) {
        for (int j = 0; j < treeKeysAux[i].length; j++) {
          if (treeKeysAux[i][j] == null) continue;
          CompressedTree ct = DKV.get(treeKeysAux[i][j]).get();
          CompressedTree newCt = IcedUtils.deepCopy(ct);
          newCt._key = Key.make(createAuxKey(treeKeys[i][j].toString()));
          DKV.put(treeKeysAux[i][j] = newCt._key,newCt);
        }
      }
    }
    return newModel;
  }

  @Override protected Futures remove_impl(Futures fs, boolean cascade) {
    for (Key[] ks : _output._treeKeys)
      for (Key k : ks)
        Keyed.remove(k, fs, true);
    for (Key[] ks : _output._treeKeysAux)
      for (Key k : ks)
        Keyed.remove(k, fs, true);
    if (_output._calib_model != null)
      _output._calib_model.remove(fs);
    return super.remove_impl(fs, cascade);
  }

  /** Write out K/V pairs */
  @Override protected AutoBuffer writeAll_impl(AutoBuffer ab) {
    for (Key<CompressedTree>[] ks : _output._treeKeys)
      for (Key<CompressedTree> k : ks)
        ab.putKey(k);
    for (Key<CompressedTree>[] ks : _output._treeKeysAux)
      for (Key<CompressedTree> k : ks)
        ab.putKey(k);
    return super.writeAll_impl(ab);
  }

  @Override protected Keyed readAll_impl(AutoBuffer ab, Futures fs) {
    for (Key<CompressedTree>[] ks : _output._treeKeys)
      for (Key<CompressedTree> k : ks)
        ab.getKey(k,fs);
    for (Key<CompressedTree>[] ks : _output._treeKeysAux)
      for (Key<CompressedTree> k : ks)
        ab.getKey(k,fs);
    return super.readAll_impl(ab,fs);
  }

  @SuppressWarnings("unchecked")  // `M` is really the type of `this`
  private M self() { return (M)this; }

  /**
   * Converts a given tree of the ensemble to a user-understandable representation.
   * @param tidx tree index
   * @param cls tree class
   * @return instance of SharedTreeSubgraph
   */
  public SharedTreeSubgraph getSharedTreeSubgraph(final int tidx, final int cls) {
    if (tidx < 0 || tidx >= _output._ntrees) {
      throw new IllegalArgumentException("Invalid tree index: " + tidx +
              ". Tree index must be in range [0, " + (_output._ntrees -1) + "].");
    }
    Key<CompressedTree> treeKey = _output._treeKeysAux[tidx][cls];
    if (treeKey == null)
      return null;
    final CompressedTree auxCompressedTree = treeKey.get();
    return _output._treeKeys[tidx][cls].get().toSharedTreeSubgraph(auxCompressedTree, _output._names, _output._domains);
  }

  @Override
  public boolean isFeatureUsedInPredict(String featureName) {
    if (featureName.equals(_output.responseName())) return false;
    int featureIdx = ArrayUtils.find(_output._varimp._names, featureName);
    return featureIdx != -1 && (double) _output._varimp._varimp[featureIdx] != 0d;
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Serialization into a POJO
  //--------------------------------------------------------------------------------------------------------------------

  public boolean binomialOpt() {
    return true;
  }

  @Override
  public CategoricalEncoding getGenModelEncoding() {
    switch (_parms._categorical_encoding) {
      case AUTO:
      case Enum:
      case SortByResponse:
        return CategoricalEncoding.AUTO;
      case OneHotExplicit:
        return CategoricalEncoding.OneHotExplicit;
      case Binary:
        return CategoricalEncoding.Binary;
      case EnumLimited:
        return CategoricalEncoding.EnumLimited;
      case Eigen:
        return CategoricalEncoding.Eigen;
      case LabelEncoder:
        return CategoricalEncoding.LabelEncoder;
      default:
        return null;
    }
  }

  protected SharedTreePojoWriter makeTreePojoWriter() {
    throw new UnsupportedOperationException("POJO is not supported for model " + _parms.algoName() + ".");
  }

  @Override
  protected final PojoWriter makePojoWriter() {
    CategoricalEncoding encoding = getGenModelEncoding();
    if (encoding == null) {
      throw new IllegalArgumentException("Only default, SortByResponse, EnumLimited and 1-hot explicit scheme is supported for POJO/MOJO");
    }
    return makeTreePojoWriter();
  }

}
