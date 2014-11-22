package hex.tree;

import java.util.Arrays;

import hex.AUC;
import hex.ConfusionMatrix2;
import water.DKV;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.ModelUtils;

/** Score the tree columns, and produce a confusion matrix
 */
public class Score extends MRTask<Score> {
  final SharedTree _bldr;
  final int     _nclass;        // Number of classes (1 for regression)
  final int     _ncols;         // Total number of columns
  final boolean _oob;           // Computed on OOB
  final boolean _validation;    // Computed on separate validation dataset
  final int     _cmlen;         // Union of train/test response categoricals
  double  _sum;                 // Sum-squared-error
  long    _snrows;              // Count of voted-on rows
  long    _cm[/*actual*/][/*predicted*/]; // Confusion matrix
  long    _cms[/*threshold*/][/*actual*/][/*predicted*/]; // Compute CM per threshold for binary classifiers

  public double mse() { return _sum/_snrows; }

  // r2 = 1- MSE(gbm) / MSE(response)
  public double r2() {
    double stddev = DKV.get(_bldr._response_key).<Vec>get().sigma();
    double var = stddev*stddev;
    return 1.0-(mse()/var);
  }

  // Confusion Matrix for classification, or null otherwise
  public ConfusionMatrix2 cm() { return _cm == null ? null : new ConfusionMatrix2(_cm); }

  public AUC auc() { 
    if( _nclass != 2 ) return null; // Only for binomial models
    final int n = _cms.length;
    ConfusionMatrix2[] res = new ConfusionMatrix2[n];
    for( int i = 0; i < n; i++ ) res[i] = new ConfusionMatrix2(_cms[i]);
    return new AUC(res, ModelUtils.DEFAULT_THRESHOLDS, _bldr.vresponse().domain());
  }


  /** Compute CM and MSE on the testing dataset.
   *  It expect already adapted validation dataset which is adapted to a model
   *  and contains a response which is adapted to confusion matrix domain.
   *  @param bldr  a tree-model builder with a (possibly partial) model built
   *  @param oob   perform out-of-bag validation on training frame   */
  public Score(SharedTree bldr, boolean oob) {
    _bldr = bldr;
    _nclass = bldr._nclass;
    _ncols = bldr._ncols;
    _oob = oob;
    _validation = bldr._parms._valid!=null; // True if using a separate validation dataset
    // Because of adaption - the validation training set has at least as many
    // classes as the training set (it may have more).  The Confusion Matrix
    // needs to be at least as big as the training set domain.
    _cmlen = bldr.vresponse().cardinality();
  }

  Score doIt( final boolean build_tree_one_node ) {
    // TODO: Better answer: keep incremental scores for validation set.  As new
    // trees are added, only need to score on the new trees (not on all trees,
    // as this does).
    if( _bldr._parms._valid == null ) return doAll(_bldr.train(), build_tree_one_node);

    // If given a validation dataset, score it "the hard way" (apply the model)
    // getting the class distribution.
    Frame res = _bldr._model.score(_bldr.valid(), false);
    // Now compute CM & AUC on the pre-scored data
    doAll(new Frame(_bldr.valid()).add(res));
    res.delete();    // Delete temp keys
    return this;
  }

  @Override public void map( Chunk chks[] ) {
    Chunk ys = _bldr.chk_resp(chks); // Response
    float fs[] = new float[_nclass+1]; // Array to hold prediction and distribution given by the model.
    _cm  = _cmlen == -1 ? null : new long[_cmlen][_cmlen];
    // For binary classifier allocate cms for individual thresholds
    _cms = _cmlen == -1 ? null : new long[ModelUtils.DEFAULT_THRESHOLDS.length][2][2];
    // Score all Rows
    for( int row=0; row<ys._len; row++ ) {
      if( ys.isNA0(row) ) continue; // Ignore missing response vars only if it was actual NA
      float sum;
      if( _validation ) {     // Passed in a class distribution from scoring
        for( int i=0; i<_nclass; i++ )
          fs[i+1] = (float)_bldr.chk_tree(chks,i+1).at0(row); // Get the class distros
        if (_nclass > 1 ) sum = 1.0f;  // Sum of a distribution is 1.0 for classification
        else              sum = fs[1]; // Sum is the same as prediction for regression.
      } else {               // Passed in the model-specific columns
        sum = _bldr.score2(chks,fs,row); // Use the training data directly (per-row predictions already made)
      }
      float err;  int yact=0; // actual response from dataset
      if (_oob && _bldr.outOfBagRow(chks, row)) continue; // score only on out-of-bag rows
      if( _nclass > 1 ) {    // Classification
        // Compute error
        yact = (int) ys.at80(row);  // OPS: Pick an actual prediction adapted to model values <0, nclass-1)
        if( sum == 0 ) {            // This tree does not predict this row *at all* ! In prediction we will make random decision, but here compute error based on number of classes
          err = 1.0f-1.0f/_nclass;  // Then take ycls=0, uniform predictive power
        } else {
          assert 0 <= yact && yact < _nclass : "weird ycls="+yact+", y="+ys.at0(row);
          err = Float.isInfinite(sum)
            ? (Float.isInfinite(fs[yact+1]) ? 0f : 1f)
            : 1.0f-fs[yact+1]/sum;              // Error: distance from predicting ycls as 1.0
        }
        assert !Double.isNaN(err) : "fs[cls]="+fs[yact+1] + ", sum=" + sum;
      } else {                // Regression
        err = (float)ys.at0(row) - sum;
      }
      _sum += err*err;               // Squared error
      assert !Double.isNaN(_sum);
      // Pick highest prob for our prediction.
      if (_nclass > 1) {    // fill CM only for classification
        if(_nclass == 2) {  // Binomial classification -> compute AUC, draw ROC
          float snd = _validation ? fs[2] : (!Float.isInfinite(sum) ? fs[2] / sum : Float.isInfinite(fs[2]) ? 1 : 0); // for validation dataset sum is always 1
          for(int i = 0; i < ModelUtils.DEFAULT_THRESHOLDS.length; i++) {
            int p = snd >= ModelUtils.DEFAULT_THRESHOLDS[i] ? 1 : 0; // Compute prediction based on threshold
            _cms[i][yact][p]++; // Increase matrix
          }
        }
        int ypred = _validation ? (int) _bldr.chk_tree(chks,0).at80(row) : ModelUtils.getPrediction(fs, row);
        _cm[yact][ypred]++;      // actual v. predicted
      }
      _snrows++;
    }
  }

  @Override public void reduce( Score t ) {
    _sum += t._sum;
    if (_cm != null)
      ArrayUtils.add(_cm,t._cm);
    _snrows += t._snrows;
    if (_cms!=null)
      for (int i = 0; i < _cms.length; i++) ArrayUtils.add(_cms[i], t._cms[i]);
  }

  public Score report(int ntrees, DTree[] trees) {
    assert !Double.isNaN(_sum);
    Log.info("============================================================== ");
    int lcnt=0;
    if( trees!=null ) for( DTree t : trees ) if( t != null ) lcnt += t._len;
    long err=_snrows;
    Log.info("r2 is "+r2()+", with "+ntrees+"x"+_nclass+" trees (average of "+((float)lcnt/_nclass)+" nodes)");
    if( _nclass > 1 ) {
      for( int c=0; c<_nclass; c++ ) err -= _cm[c][c];
      Log.info("Total of "+err+" errors on "+_snrows+" rows, CM= "+Arrays.deepToString(_cm));
    } else
      Log.info("Reported on "+_snrows+" rows.");
    return this;
  }
}
