package hex.tree;

import hex.ModelMetrics;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.ModelUtils;

/** Score the tree columns, and produce a confusion matrix and AUC
 */
public class Score extends MRTask<Score> {
  final SharedTree _bldr;
  final boolean _oob;           // Computed on OOB
  ModelMetrics.MetricBuilder _mb;

  /** Compute ModelMetrics on the testing dataset.
   *  It expect already adapted validation dataset which is adapted to a model
   *  and contains a response which is adapted to confusion matrix domain.
   */
  public Score(SharedTree bldr, boolean oob) { _bldr = bldr; _oob = oob; }
  
  @Override public void map( Chunk chks[] ) {
    Chunk ys = _bldr.chk_resp(chks);  // Response
    final int nclass = _bldr._nclass; 
    // Because of adaption - the validation training set has at least as many
    // classes as the training set (it may have more).  The Confusion Matrix
    // needs to be at least as big as the training set domain.
    String[] domain = _bldr.vresponse().domain();
    // If this is a score-on-train AND DRF, then oobColIdx makes sense,
    // otherwise this field is unused.
    final int oobColIdx = _bldr.idx_oobt();
    _mb = new ModelMetrics.MetricBuilder(domain,nclass==2 ? ModelUtils.DEFAULT_THRESHOLDS : new float[]{0.5f} );
    final float[] cdists = _mb._work; // Temp working array for class distributions
    // If working a validation set, need to push thru official model scoring
    // logic which requires a temp array to hold the features.
    final double[] tmp = _bldr._parms._valid!=null ? new double[_bldr._ncols] : null;

    // Score all Rows
    for( int row=0; row<ys._len; row++ ) {
      if( ys.isNA0(row) ) continue; // Ignore missing response vars only if it was actual NA
      // Ignore out-of-bag rows
      if( _oob && chks[oobColIdx].at80(row)!=0 ) continue;
      if( _bldr._parms._valid!=null )   // Must score "the hard way"
        _bldr._model.score0(chks,row,tmp,cdists);
      else                      // Passed in the model-specific columns
        _bldr.score2(chks,cdists,row); // Use the training data directly (per-row predictions already made)
      if( nclass > 1 ) cdists[0] = ModelUtils.getPrediction(cdists,row); // Fill in prediction
      _mb.perRow(cdists,(float)ys.at0(row));
    }
  }

  @Override public void reduce( Score t ) { _mb.reduce(t._mb); }

  // Run after the doAll scoring to convert the MetricsBuilder to a ModelMetrics
  ModelMetrics makeModelMetrics(SharedTreeModel model, Frame fr, String resp) { return _mb.makeModelMetrics(model,fr, fr.vec(resp).sigma()); }
}
