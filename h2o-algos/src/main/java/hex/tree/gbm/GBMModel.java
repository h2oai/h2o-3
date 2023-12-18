package hex.tree.gbm;

import hex.*;
import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.*;
import hex.util.EffectiveParametersUtils;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.FrameUtils;
import water.util.Log;
import water.util.TwoDimTable;

import java.util.*;

public class GBMModel extends SharedTreeModelWithContributions<GBMModel, GBMModel.GBMParameters, GBMModel.GBMOutput> 
        implements Model.StagedPredictions, FeatureInteractionsCollector, FriedmanPopescusHCollector, Model.RowToTreeAssignment {

  public static class GBMParameters extends SharedTreeModel.SharedTreeParameters {
    public double _learn_rate;
    public double _learn_rate_annealing;
    public double _col_sample_rate;
    public double _max_abs_leafnode_pred;
    public double _pred_noise_bandwidth;
    public KeyValue[] _monotone_constraints;
    public String[][] _interaction_constraints;

    public GBMParameters() {
      super();
      _learn_rate = 0.1;
      _learn_rate_annealing = 1.0;
      _col_sample_rate = 1.0;
      _sample_rate = 1.0;
      _ntrees = 50;
      _max_depth = 5;
      _max_abs_leafnode_pred = Double.MAX_VALUE;
      _pred_noise_bandwidth =0;
    }

    @Override
    public boolean useColSampling() {
      return super.useColSampling() || _col_sample_rate != 1.0;
    }

    public String algoName() { return "GBM"; }
    public String fullName() { return "Gradient Boosting Machine"; }
    public String javaName() { return GBMModel.class.getName(); }

    @Override
    public boolean forceStrictlyReproducibleHistograms() {
      // if monotone constraints are enabled -> use strictly reproducible histograms (we calculate values that
      // are not subject to reduce precision logic in DHistogram (the "float trick" cannot be applied)
      return usesMonotoneConstraints();
    }

    private boolean usesMonotoneConstraints() {
      if (areMonotoneConstraintsEmpty())
        return emptyConstraints(0) != null;
      return true;
    }

    private boolean areMonotoneConstraintsEmpty() {
      return _monotone_constraints == null || _monotone_constraints.length == 0;
    }

    public Constraints constraints(Frame f) {
      if (areMonotoneConstraintsEmpty()) {
        return emptyConstraints(f.numCols());
      }
      int[] cs = new int[f.numCols()];
      for (KeyValue spec : _monotone_constraints) {
        if (spec.getValue() == 0)
          continue;
        int col = f.find(spec.getKey());
        if (col < 0) {
          throw new IllegalStateException("Invalid constraint specification, column '" + spec.getKey() + "' doesn't exist.");
        }
        cs[col] = spec.getValue() < 0 ? -1 : 1;
      }
      boolean useBounds = _distribution == DistributionFamily.gaussian ||
              _distribution == DistributionFamily.bernoulli ||
              _distribution == DistributionFamily.tweedie ||
              _distribution == DistributionFamily.quasibinomial ||
              _distribution == DistributionFamily.multinomial ||
              _distribution == DistributionFamily.quantile;
      return new Constraints(cs, DistributionFactory.getDistribution(this), useBounds);
    }

    // allows to override the behavior in tests (eg. create empty constraints and test execution as if constraints were used)
    Constraints emptyConstraints(int nCols) {
      return null;
    }
    
    public GlobalInteractionConstraints interactionConstraints(Frame frame){
      return new GlobalInteractionConstraints(this._interaction_constraints, frame.names());
    }
    
    public BranchInteractionConstraints initialInteractionConstraints(GlobalInteractionConstraints ics){
      return new BranchInteractionConstraints(ics.getAllAllowedColumnIndices());
    }
  }

  public static class GBMOutput extends SharedTreeModel.SharedTreeOutput {
    public String[] _quasibinomialDomains;
    boolean _quasibinomial;
    int _nclasses;
    public int nclasses() {
      return _nclasses;
    }
    public GBMOutput(GBM b) {
      super(b);
      _quasibinomial = b._parms._distribution == DistributionFamily.quasibinomial;
      _nclasses = b.nclasses();
    }
    @Override
    public String[] classNames() {
      String [] res = super.classNames();
      if(_quasibinomial){
        return _quasibinomialDomains;
      }
      return res;
    }
  }


  public GBMModel(Key<GBMModel> selfKey, GBMParameters parms, GBMOutput output) {
    super(selfKey,parms,output);
  }

  @Override
  public void initActualParamValues() {
    super.initActualParamValues();
    EffectiveParametersUtils.initFoldAssignment(_parms);
    EffectiveParametersUtils.initHistogramType(_parms);
    EffectiveParametersUtils.initCategoricalEncoding(_parms, Parameters.CategoricalEncodingScheme.Enum);
    EffectiveParametersUtils.initCalibrationMethod(_parms);
  }
  
  public void initActualParamValuesAfterOutputSetup(int nclasses, boolean isClassifier) {
    EffectiveParametersUtils.initStoppingMetric(_parms, isClassifier);
    EffectiveParametersUtils.initDistribution(_parms, nclasses);
  }

  @Override
  protected ScoreContributionsTask getScoreContributionsTask(SharedTreeModel model) {
      return new ScoreContributionsTask(this);
  }

  @Override
  protected ScoreContributionsTask getScoreContributionsSoringTask(SharedTreeModel model, ContributionsOptions options) {
    return new ScoreContributionsSortingTask(model, options);
  }

  @Override
  public Frame scoreStagedPredictions(Frame frame, Key<Frame> destination_key) {
    Frame adaptFrm = new Frame(frame);
    adaptTestForTrain(adaptFrm, true, false);

    final String[] names = makeAllTreeColumnNames();
    final int outputcols = names.length;

    return new StagedPredictionsTask(this)
            .doAll(outputcols, Vec.T_NUM, adaptFrm)
            .outputFrame(destination_key, names, null);
  }

  private static class StagedPredictionsTask extends MRTask<StagedPredictionsTask> {
    private final Key<GBMModel> _modelKey;

    private transient GBMModel _model;

    private StagedPredictionsTask(GBMModel model) {
      _modelKey = model._key;
    }

    @Override
    protected void setupLocal() {
      _model = _modelKey.get();
      assert _model != null;
    }

    @Override
    public void map(Chunk chks[], NewChunk[] nc) {
      double[] input = new double[chks.length];
      int contribOffset = _model._output.nclasses() == 1 ? 0 : 1;

      for (int row = 0; row < chks[0]._len; row++) {
        for (int i = 0; i < chks.length; i++)
          input[i] = chks[i].atd(row);

        double[] contribs = new double[contribOffset + _model._output.nclasses()];
        double[] preds = new double[contribs.length];

        int col = 0;
        for (int tidx = 0; tidx < _model._output._treeKeys.length; tidx++) {
          Key[] keys = _model._output._treeKeys[tidx];
          for (int i = 0; i < keys.length; i++) {
            if (keys[i] != null)
              contribs[contribOffset + i] += DKV.get(keys[i]).<CompressedTree>get().score(input, _model._output._domains);
            preds[contribOffset + i] = contribs[contribOffset + i];
          }
          _model.score0Probabilities(preds, 0);
          _model.score0PostProcessSupervised(preds, input);
          for (int i = 0; i < keys.length; i++) {
            if (keys[i] != null)
              nc[col++].addNum(preds[contribOffset + i]);
          }
        }
        assert (col == nc.length);
      }
    }
  }

  @Override
  protected final double[] score0Incremental(Score.ScoreIncInfo sii, Chunk[] chks, double offset, int row_in_chunk, double[] tmp, double[] preds) {
    assert _output.nfeatures() == tmp.length;
    for (int i = 0; i < tmp.length; i++)
      tmp[i] = chks[i].atd(row_in_chunk);

    if (sii._startTree == 0)
      Arrays.fill(preds,0);
    else
      for (int i = 0; i < sii._workspaceColCnt; i++)
        preds[sii._predsAryOffset + i] = chks[sii._workspaceColIdx + i].atd(row_in_chunk);

    score0(tmp, preds, offset, sii._startTree, _output._treeKeys.length);

    for (int i = 0; i < sii._workspaceColCnt; i++)
      chks[sii._workspaceColIdx + i].set(row_in_chunk, preds[sii._predsAryOffset + i]);

    score0Probabilities(preds, offset);
    score0PostProcessSupervised(preds, tmp);
    return preds;
  }

  /** Bulk scoring API for one row.  Chunks are all compatible with the model,
   *  and expect the last Chunks are for the final distribution and prediction.
   *  Default method is to just load the data into the tmp array, then call
   *  subclass scoring logic. */
  @Override protected double[] score0(double data[/*ncols*/], double preds[/*nclasses+1*/], double offset, int ntrees) {
    super.score0(data, preds, offset, ntrees);    // These are f_k(x) in Algorithm 10.4
    return score0Probabilities(preds, offset);
  }

  private double[] score0Probabilities(double preds[/*nclasses+1*/], double offset) {
    if (_parms._distribution == DistributionFamily.bernoulli
        || _parms._distribution == DistributionFamily.quasibinomial
        || _parms._distribution == DistributionFamily.modified_huber
        || (_parms._distribution == DistributionFamily.custom && _output.nclasses() == 2)) { // custom distribution could be also binomial
      double f = preds[1] + _output._init_f + offset; //Note: class 1 probability stored in preds[1] (since we have only one tree)
      preds[2] = DistributionFactory.getDistribution(_parms).linkInv(f);
      preds[1] = 1.0 - preds[2];
    } else if (_parms._distribution == DistributionFamily.multinomial // Kept the initial prediction for binomial
                || (_parms._distribution == DistributionFamily.custom && _output.nclasses() > 2) ) { // custom distribution could be also multinomial
      if (_output.nclasses() == 2) { //1-tree optimization for binomial
        preds[1] += _output._init_f + offset; //offset is not yet allowed, but added here to be future-proof
        preds[2] = -preds[1];
      }
      hex.genmodel.GenModel.GBM_rescale(preds);
    } else { //Regression
      double f = preds[0] + _output._init_f + offset;
      preds[0] = DistributionFactory.getDistribution(_parms).linkInv(f);
    }
    return preds;
  }

  @Override
  protected SharedTreePojoWriter makeTreePojoWriter() {
    CompressedForest compressedForest = new CompressedForest(_output._treeKeys, _output._domains);
    CompressedForest.LocalCompressedForest localCompressedForest = compressedForest.fetch();
    return new GbmPojoWriter(this, localCompressedForest._trees);
  }

  @Override
  public GbmMojoWriter getMojo() {
    return new GbmMojoWriter(this);
  }

  public FeatureInteractions getFeatureInteractions(int maxInteractionDepth, int maxTreeDepth, int maxDeepening) {
    FeatureInteractions featureInteractions = new FeatureInteractions();

    int nclasses = this._output._nclasses > 2 ? this._output._nclasses : 1;
    for (int i = 0; i < this._parms._ntrees; i++) {
      for (int j = 0; j < nclasses; j++) {
        FeatureInteractions currentTreeFeatureInteractions = new FeatureInteractions();
        SharedTreeSubgraph tree = this.getSharedTreeSubgraph(i, j);
        
        List<SharedTreeNode> interactionPath = new ArrayList<>();
        Set<String> memo = new HashSet<>();

        FeatureInteractions.collectFeatureInteractions(tree.rootNode, interactionPath, 0, 0, 1, 0, 0, currentTreeFeatureInteractions,
                memo, maxInteractionDepth, maxTreeDepth, maxDeepening, i, true);
        featureInteractions.mergeWith(currentTreeFeatureInteractions);
      }
    }
    if(featureInteractions.isEmpty()){
      Log.warn("There is no feature interaction for this model.");
      return null;
    }
    return featureInteractions;
  }

  @Override
  public TwoDimTable[][] getFeatureInteractionsTable(int maxInteractionDepth, int maxTreeDepth, int maxDeepening) {
    return FeatureInteractions.getFeatureInteractionsTable(this.getFeatureInteractions(maxInteractionDepth,maxTreeDepth,maxDeepening));
  }

  @Override
  public double getFriedmanPopescusH(Frame frame, String[] vars) {
    Frame adaptFrm = removeSpecialColumns(frame);

    for(int colId = 0; colId < adaptFrm.numCols(); colId++) {
      Vec col = adaptFrm.vec(colId);
      if (col.isBad()) {
        throw new UnsupportedOperationException(
                "Calculating of H statistics error: row " + adaptFrm.name(colId) + " is missing.");
      }
    }

    int nclasses = this._output._nclasses > 2 ? this._output._nclasses : 1;
    SharedTreeSubgraph[][] sharedTreeSubgraphs = new SharedTreeSubgraph[this._parms._ntrees][nclasses];
    for (int i = 0; i < this._parms._ntrees; i++) {
      for (int j = 0; j < nclasses; j++) {
        sharedTreeSubgraphs[i][j] = this.getSharedTreeSubgraph(i, j);
      }
    }
    
    return FriedmanPopescusH.h(adaptFrm, vars, this._parms._learn_rate, sharedTreeSubgraphs);
  }

  @Override
  public Frame rowToTreeAssignment(Frame frame, Key<Frame> destination_key, Job<Frame> j) {
    Key<CompressedTree>[/*_ntrees*/][/*_nclass*/] treeKeys = _output._treeKeys;

    Sample[] ss = new Sample[treeKeys.length];
    int[] cons = new int[treeKeys.length];
    Vec[] vs = frame.vec(_parms._response_column).makeVolatileInts(cons);
    for (int treeId = 0; treeId < treeKeys.length; treeId++) {
      Key<CompressedTree> compressedTreeKey = treeKeys[treeId][0]; // Always pick the zero one, multinomial trees use the same subsample
      if (compressedTreeKey == null)
        continue;
      CompressedTree ct = DKV.getGet(compressedTreeKey);
      long seed = ct.getSeed();
      ss[treeId] = new Sample(seed, _parms._sample_rate, _parms._sample_rate_per_class, 1, 0).dfork(vs[treeId], frame.vec(_parms._response_column));
    }
    for (int treeId = 0; treeId < treeKeys.length; treeId++) {
      ss[treeId].getResult();
    }

    int outputSize = treeKeys.length + 1;
    String[] names = new String[outputSize];
    byte[] types = new byte[outputSize];
    String[][] domains = new String[outputSize][2];

    names[0] = "row_id";
    types[0] = Vec.T_NUM;
    domains[0] = null;

    for (int i = 1; i < outputSize; i++) {
      types[i] = Vec.T_CAT;
      domains[i] = new String[]{"0", "1"};
      names[i] = "tree_" + i;
    }

     return new MRTask(){
       public void map(Chunk[] chk, NewChunk[] nchk) {
         for (int row = 0; row < chk[0]._len; row++) {
           nchk[0].addNum(row + chk[0].start());
           for (int col = 0; col < chk.length; col++) {
             nchk[col+1].addNum(chk[col].atd(row));
           }
         }
       }
     }.withPostMapAction(JobUpdatePostMap.forJob(j)).doAll(types, vs).outputFrame(destination_key, names, domains);
  }

}
