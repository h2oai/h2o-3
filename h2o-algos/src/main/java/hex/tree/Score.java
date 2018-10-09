package hex.tree;

import hex.*;
import hex.genmodel.GenModel;
import hex.genmodel.utils.DistributionFamily;
import water.Iced;
import water.Key;
import water.fvec.C0DChunk;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;
import water.udf.CFuncRef;

/** Score the tree columns, and produce a confusion matrix and AUC
 */
public class Score extends CMetricScoringTask<Score> {
  final SharedTree _bldr;
  final boolean _is_train;      // Scoring on pre-scored training data vs full-score data
  final boolean _oob;           // Computed on OOB
  final Key<Vec> _kresp;        // Response vector key (might be either train or validation)
  final ModelCategory _mcat;    // Model category (Binomial, Regression, etc)

  final boolean _computeGainsLift;
  final ScoreIncInfo _sii;      // Incremental scoring (on a validation dataset), null indicates full scoring
  final Frame _preds;           // Prediction cache (typically not too many Vecs => it is not too costly embed the object in MRTask)
  
  /** Output parameter: Metric builder */
  ModelMetrics.MetricBuilder _mb;

  /** Compute ModelMetrics on the testing dataset.
   *  It expect already adapted validation dataset which is adapted to a model
   *  and contains a response which is adapted to confusion matrix domain.
   */
  public Score(SharedTree bldr, boolean is_train, boolean oob, Vec kresp, ModelCategory mcat, boolean computeGainsLift, Frame preds, CFuncRef customMetricFunc) {
    this(bldr, is_train, null, oob, kresp, mcat, computeGainsLift, preds, customMetricFunc);
  }

  public Score(SharedTree bldr, ScoreIncInfo sii, boolean oob, Vec kresp, ModelCategory mcat, boolean computeGainsLift, Frame preds, CFuncRef customMetricFunc) {
    this(bldr, false, sii, oob, kresp, mcat, computeGainsLift, preds, customMetricFunc);
  }

  private Score(SharedTree bldr, boolean is_train, ScoreIncInfo sii, boolean oob, Vec kresp, ModelCategory mcat, boolean computeGainsLift, Frame preds, CFuncRef customMetricFunc) {
  super(customMetricFunc);
    _bldr = bldr; _is_train = is_train; _sii = sii; _oob = oob; _kresp = kresp != null ? kresp._key : null; _mcat = mcat; _computeGainsLift = computeGainsLift;
    _preds = computeGainsLift ? preds : null; // don't keep the prediction cache if we don't need to compute gainslift
    assert _kresp != null || !_bldr.isSupervised();
    assert (! _is_train) || (_sii == null);
  }

  @Override public void map(Chunk allchks[]) {
    final Chunk[] chks = getScoringChunks(allchks);
    Chunk ys = _bldr.isSupervised() ? _bldr.chk_resp(chks) : new C0DChunk(0, chks[0]._len);  // Response
    SharedTreeModel m = _bldr._model;
    Chunk weightsChunk = m._output.hasWeights() ? chks[m._output.weightsIdx()] : null;
    Chunk offsetChunk = m._output.hasOffset() ? chks[m._output.offsetIdx()] : null;
    final int nclass = _bldr.nclasses();
    // Because of adaption - the validation training set has at least as many
    // classes as the training set (it may have more).  The Confusion Matrix
    // needs to be at least as big as the training set domain.
    String[] domain = _kresp != null ? _kresp.get().domain() : null;
    if (domain == null && m._parms._distribution == DistributionFamily.quasibinomial)
      domain = new String[]{"0", "1"};
    // If this is a score-on-train AND DRF, then oobColIdx makes sense,
    // otherwise this field is unused.
    final int oobColIdx = _bldr.idx_oobt();
    _mb = m.makeMetricBuilder(domain);
//    _gainsLiftBuilder = _bldr._model._output.nclasses()==2 ? new GainsLift.GainsLiftBuilder(_fr.vec(_bldr.idx_tree(0)).pctiles()) : null;
    final double[] cdists = _mb._work; // Temp working array for class distributions
    // If working a validation set, need to push thru official model scoring
    // logic which requires a temp array to hold the features.
    final double[] tmp = _is_train && _bldr._ntrees > 0 ? null : new double[_bldr._ncols];
//    final double[] tmp = new double[_bldr._ncols];

    // Score all Rows
    float [] val= new float[1];
    for( int row=0; row<ys._len; row++ ) {
      if( ys.isNA(row) ) continue; // Ignore missing response vars only if it was actual NA
      // Ignore out-of-bag rows
      if( _oob && chks[oobColIdx].atd(row)==0 ) continue;
      double weight = weightsChunk!=null?weightsChunk.atd(row):1;
      if (weight == 0) continue; //ignore holdout rows
      double offset = offsetChunk!=null?offsetChunk.atd(row):0;
      if( _is_train ) // Passed in the model-specific columns
        _bldr.score2(chks, weight, offset, cdists, row); // Use the training data directly (per-row predictions already made)
      else if (_sii != null)
        m.score0Incremental(_sii, chks, offset, row, tmp, cdists); // Incremental scoring (only use new trees)
      else            // Must score "the hard way"
        m.score0(chks, offset, row, tmp, cdists);

      // fill tmp with training data for null model - to have proper tie breaking
      if (_is_train && _bldr._ntrees == 0)
        for( int i=0; i< tmp.length; i++ )
          tmp[i] = chks[i].atd(row);

      if( nclass > 1 ) cdists[0] = GenModel.getPrediction(cdists, m._output._priorClassDist, tmp, m.defaultThreshold()); // Fill in prediction
      val[0] = (float)ys.atd(row);
      _mb.perRow(cdists, val, weight, offset, m);

      if (_preds != null)
        _mb.cachePrediction(cdists, allchks, row, chks.length, m);

      // Compute custom metric if necessary
      customMetricPerRow(cdists, val, weight, offset, m);
    }
  }

  // scoring chunks are those chunks that make the input to one of the scoring functions
  private Chunk[] getScoringChunks(Chunk[] allChunks) {
    if (_preds == null)
      return allChunks;
    Chunk[] chks = new Chunk[allChunks.length - _preds.numCols()];
    System.arraycopy(allChunks, 0, chks, 0, chks.length);
    return chks;
  }

  @Override
  protected boolean modifiesVolatileVecs() {
    return _sii != null || _preds != null;
  }

  @Override public void reduce(Score t) {
    super.reduce(t);
    _mb.reduce(t._mb);
  }

  // We need to satsify MB invariant
  @Override protected void postGlobal() {
    super.postGlobal();
    if(_mb != null) {
      _mb.postGlobal(getComputedCustomMetric());
    }
  }

  ModelMetrics scoreAndMakeModelMetrics(SharedTreeModel model, Frame fr, Frame adaptedFr, boolean buildTreeOneNode) {
    Frame input = _preds != null ? new Frame(adaptedFr).add(_preds) : adaptedFr;
    return doAll(input, buildTreeOneNode)
            .makeModelMetrics(model, fr, adaptedFr, _preds);
  }

  // Run after the doAll scoring to convert the MetricsBuilder to a ModelMetrics
  private ModelMetrics makeModelMetrics(SharedTreeModel model, Frame fr, Frame adaptedFr, Frame preds) {
    ModelMetrics mm;
    if (model._output.nclasses() == 2 && _computeGainsLift) {
      assert preds != null : "Predictions were pre-created";
      mm = _mb.makeModelMetrics(model, fr, adaptedFr, preds);
    } else {
      boolean calculatePreds = preds == null && model._parms._distribution == DistributionFamily.huber;
      // FIXME: PUBDEV-4992 we should avoid doing full scoring!
      if (calculatePreds) {
        Log.warn("Going to calculate predictions from scratch. This can be expensive for large models! See PUBDEV-4992");
        preds = model.score(fr);
      }
      mm = _mb.makeModelMetrics(model, fr, null, preds);
      if (calculatePreds && (preds != null))
        preds.remove();
    }
    return mm;
  }

  static Frame makePredictionCache(SharedTreeModel model, Vec resp) {
    String[] domain = resp.domain();
    if (domain == null && model._parms._distribution == DistributionFamily.quasibinomial)
      domain = new String[]{"0", "1"};
    ModelMetrics.MetricBuilder mb = model.makeMetricBuilder(domain);
    return mb.makePredictionCache(model, resp);
  }

  public static class ScoreIncInfo extends Iced<ScoreIncInfo> {
    public final int _startTree;
    public final int _workspaceColIdx;
    public final int _workspaceColCnt;
    public final int _predsAryOffset;

    public ScoreIncInfo(int startTree, int workspaceColIdx, int workspaceColCnt, int predsAryOffset) {
      _startTree = startTree;
      _workspaceColIdx = workspaceColIdx;
      _workspaceColCnt = workspaceColCnt;
      _predsAryOffset = predsAryOffset;
    }
  }
}
