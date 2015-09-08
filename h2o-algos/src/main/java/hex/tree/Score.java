package hex.tree;

import hex.Model;
import hex.ModelCategory;
import hex.ModelMetrics;
import hex.ModelMetricsSupervised;
import hex.genmodel.GenModel;
import water.MRTask;
import water.fvec.C0DChunk;
import water.fvec.Chunk;
import water.fvec.Frame;

/** Score the tree columns, and produce a confusion matrix and AUC
 */
public class Score extends MRTask<Score> {
  final SharedTree _bldr;
  final boolean _is_train;      // Scoring on pre-scored training data vs full-score data
  final boolean _oob;           // Computed on OOB
  final ModelCategory _mcat;    // Model category (Binomial, Regression, etc)
  ModelMetrics.MetricBuilder _mb;

  /** Compute ModelMetrics on the testing dataset.
   *  It expect already adapted validation dataset which is adapted to a model
   *  and contains a response which is adapted to confusion matrix domain.
   */
  public Score(SharedTree bldr, boolean is_train, boolean oob, ModelCategory mcat) { _bldr = bldr; _is_train = is_train; _oob = oob; _mcat = mcat; }
  
  @Override public void map( Chunk chks[] ) {
    Chunk ys = _bldr.chk_resp(chks);  // Response
    Model m = _bldr._model;
    Chunk weightsChunk = m._output.hasWeights() ? chks[m._output.weightsIdx()] : new C0DChunk(1, chks[0]._len);
    Chunk offsetChunk = m._output.hasOffset() ? chks[m._output.offsetIdx()] : new C0DChunk(0, chks[0]._len);
    final int nclass = _bldr.nclasses();
    // Because of adaption - the validation training set has at least as many
    // classes as the training set (it may have more).  The Confusion Matrix
    // needs to be at least as big as the training set domain.
    String[] domain = _bldr.vresponse().domain();
    // If this is a score-on-train AND DRF, then oobColIdx makes sense,
    // otherwise this field is unused.
    final int oobColIdx = _bldr.idx_oobt();
    _mb = m.makeMetricBuilder(domain);
    final double[] cdists = _mb._work; // Temp working array for class distributions
    // If working a validation set, need to push thru official model scoring
    // logic which requires a temp array to hold the features.
    final double[] tmp = _is_train ? null : new double[_bldr._ncols];

    // Score all Rows
    float [] val= new float[1];
    for( int row=0; row<ys._len; row++ ) {
      if( ys.isNA(row) ) continue; // Ignore missing response vars only if it was actual NA
      // Ignore out-of-bag rows
      if( _oob && chks[oobColIdx].at8(row)==0 ) continue;
      double weight = weightsChunk.atd(row);
      if (weight == 0) continue; //ignore holdout rows
      double offset = offsetChunk.atd(row);
      if( _is_train ) // Passed in the model-specific columns
        _bldr.score2(chks, weight, offset, cdists, row); // Use the training data directly (per-row predictions already made)
      else            // Must score "the hard way"
        m.score0(chks, weight, offset, row, tmp, cdists);
      if( nclass > 1 ) cdists[0] = GenModel.getPrediction(cdists, m._output._priorClassDist, tmp, m.defaultThreshold()); // Fill in prediction
      val[0] = (float)ys.atd(row);
      _mb.perRow(cdists, val, weight, offset, m);
    }
  }

  @Override public void reduce( Score t ) { _mb.reduce(t._mb); }

  // Run after the doAll scoring to convert the MetricsBuilder to a ModelMetrics
  ModelMetricsSupervised makeModelMetrics(SharedTreeModel model, Frame fr) {
    return (ModelMetricsSupervised)_mb.makeModelMetrics(model, fr);
  }
}
