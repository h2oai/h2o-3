package water;

import water.api.AUC;
import water.api.ConfusionMatrix;
import water.api.HitRatio;
import water.fvec.*;
import water.util.Log;
import water.util.ModelUtils;

/** Supervised Model
 *  There is a response column used in training.
 */
public abstract class SupervisedModel<M extends Model<M,P>, P extends Model.Parameters<M,P>> extends Model<M,P> {

  protected float[] _priorClassDist;
  protected float[] _modelClassDist;
  public void setModelClassDistribution(float[] classdist) { _modelClassDist = classdist.clone(); }

  public SupervisedModel( Key selfKey, Frame fr, Parameters parms, float[] priorClassDist ) {
    this(selfKey,fr.names(),fr.domains(),parms,priorClassDist);
  }

  public SupervisedModel( Key selfKey, String[] names, String[][] domains, Parameters parms, float[] priorClassDist ) {
    super(selfKey,names,domains,parms);
    _priorClassDist = priorClassDist;
  }

  /**
   * Compute the model error for a given test data set
   * For multi-class classification, this is the classification error based on assigning labels for the highest predicted per-class probability.
   * For binary classification, this is the classification error based on assigning labels using the optimal threshold for maximizing the F1 score.
   * For regression, this is the mean squared error (MSE).
   * @param ftest Frame containing test data
   * @param vactual The response column Vec
   * @param fpreds Frame containing ADAPTED (domain labels from train+test data) predicted data (classification: label + per-class probabilities, regression: target)
   * @param hitratio_fpreds Frame containing predicted data (domain labels from test data) (classification: label + per-class probabilities, regression: target)
   * @param label Name for the scored data set to be printed
   * @param printMe Whether to print the scoring results to Log.info
   * @param max_conf_mat_size Largest size of Confusion Matrix (#classes) for it to be printed to Log.info
   * @param cm Confusion Matrix object to populate for multi-class classification (also used for regression)
   * @param auc AUC object to populate for binary classification
   * @param hr HitRatio object to populate for classification
   * @return model error, see description above
   */
  public double calcError(final Frame ftest, final Vec vactual,
                          final Frame fpreds, final Frame hitratio_fpreds,
                          final String label, final boolean printMe,
                          final int max_conf_mat_size,
                          final ConfusionMatrix cm,
                          final AUC auc,
                          final HitRatio hr)
  {
    StringBuilder sb = new StringBuilder();
    double error = Double.POSITIVE_INFINITY;
    // populate AUC
    if (auc != null) {
      assert(isClassifier());
      assert(nclasses() == 2);
      auc.actual = ftest;
      auc.vactual = vactual;
      auc.predict = fpreds;
      auc.vpredict = fpreds.vecs()[2]; //binary classifier (label, prob0, prob1 (THIS ONE), adaptedlabel)
      auc.threshold_criterion = AUC.ThresholdCriterion.maximum_F1;
      auc.execImpl();
      auc.toASCII(sb);
      error = auc.err(); //using optimal threshold for F1
    }
    // populate CM
    if (cm != null) {
      cm.actual = ftest;
      cm.vactual = vactual;
      cm.predict = fpreds;
      cm.vpredict = fpreds.vecs()[0]; // prediction (either label or regression target)
      cm.execImpl();
      if (isClassifier()) {
        if (auc != null) {
          //override the CM with the one computed by AUC (using optimal threshold)
          //Note: must still call invoke above to set the domains etc.
          cm.cm = new long[3][3]; // 1 extra layer for NaNs (not populated here, since AUC skips them)
          cm.cm[0][0] = auc.cm()[0][0];
          cm.cm[1][0] = auc.cm()[1][0];
          cm.cm[0][1] = auc.cm()[0][1];
          cm.cm[1][1] = auc.cm()[1][1];
          assert(new ConfusionMatrix2(cm.cm).err() == auc.err()); //check consistency with AUC-computed error
        } else {
          error = new ConfusionMatrix2(cm.cm).err(); //only set error if AUC didn't already set the error
        }
        if (cm.cm.length <= max_conf_mat_size) cm.toASCII(sb);
      } else {
        assert(auc == null);
        error = cm.mse;
        cm.toASCII(sb);
      }
    }
    // populate HitRatio
    if (hr != null) {
      assert(isClassifier());
      hr.actual = ftest;
      hr.vactual = vactual;
      hr.predict = hitratio_fpreds;
      hr.execImpl();
      hr.toASCII(sb);
    }
    if (printMe && sb.length() > 0) {
      Log.info("Scoring on " + label + " data:");
      for (String s : sb.toString().split("\n")) Log.info(s);
    }
    return error;
  }

  /** Bulk scoring API for one row.  Chunks are all compatible with the model,
   *  and expect the last Chunks are for the final distribution and prediction.
   *  Default method is to just load the data into the tmp array, then call
   *  subclass scoring logic. */
  @Override protected float[] score0( Chunk chks[], int row_in_chunk, double[] tmp, float[] preds ) {
    assert chks.length>=_names.length; // Last chunk is for the response
    for( int i=0; i<_names.length-1; i++ ) // Do not include last value since it can contains a response
      tmp[i] = chks[i].at0(row_in_chunk);
    float[] scored = score0(tmp,preds);
    // Correct probabilities obtained from training on oversampled data back to original distribution
    // C.f. http://gking.harvard.edu/files/0s.pdf Eq.(27)
    if (isClassifier() && _priorClassDist != null && _modelClassDist != null) {
      assert(scored.length == nclasses()+1); //1 label + nclasses probs
      double probsum=0;
      for( int c=1; c<scored.length; c++ ) {
        final double original_fraction = _priorClassDist[c-1];
        assert(original_fraction > 0);
        final double oversampled_fraction = _modelClassDist[c-1];
        assert(oversampled_fraction > 0);
        assert(!Double.isNaN(scored[c]));
        scored[c] *= original_fraction / oversampled_fraction;
        probsum += scored[c];
      }
      for (int i=1;i<scored.length;++i) scored[i] /= probsum;
      //set label based on corrected probabilities (max value wins, with deterministic tie-breaking)
      scored[0] = ModelUtils.getPrediction(scored, tmp);
    }
    return scored;
  }

}

