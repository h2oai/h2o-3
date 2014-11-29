package hex;

import water.Key;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;
import water.util.ModelUtils;
import water.util.MRUtils;

/** Supervised Model
 *  There is a response column used in training.
 */
public abstract class SupervisedModel<M extends Model<M,P,O>, P extends SupervisedModel.SupervisedParameters, O extends SupervisedModel.SupervisedOutput> extends Model<M,P,O> {

  public SupervisedModel( Key selfKey, P parms, O output ) { super(selfKey,parms,output);  }

  /** Supervised Model Parameters includes a response column, and whether or
   *  not rebalancing classes is desirable.  Also includes a bunch of cheap
   *  cached convenience fields.  */
  public abstract static class SupervisedParameters extends Model.Parameters {
    /** Supervised models have an expected response they get to train with! */
    public String _response_column; // response column name

    /** Convert the response column to an enum (forcing a classification
     *  instead of a regression) as needed.  The default is false, which means
     *  "do nothing" - accept the response column as-is and that alone drives
     *  the decision to do a classification vs regression. */
    public boolean _convert_to_enum = false;

    /** Should the minority classes be upsampled to balance the class
     *  distribution? */
    public boolean _balance_classes = false;

    /** When classes are being balanced, limit the resulting dataset size to
     *  the specified multiple of the original dataset size.  Maximum relative
     *  size of the training data after balancing class counts (can be less
     *  than 1.0) */
    public float _max_after_balance_size = Float.POSITIVE_INFINITY;

    @Override public long checksum() {
      return super.checksum()^_response_column.hashCode()^(_convert_to_enum ?1:0)^(_balance_classes?1:0);
    }
  }

  /** Output from all Supervised Models, includes class distribtion
   */
  public abstract static class SupervisedOutput extends Model.Output {
    // Includes the class distribution for all supervised models
    public long [/*nclass*/] _distribution;  // Count of rows-per-class
    public float[/*nclass*/] _priorClassDist;// Fraction of classes out of 1.0
    public float[/*nclass*/] _modelClassDist;// Distribution, after balancing classes

    public SupervisedOutput() { this(null); }

    /** Any final prep-work just before model-building starts, but after the
     *  user has clicked "go".  E.g., converting a response column to an enum
     *  touches the entire column (can be expensive), makes a parallel vec
     *  (Key/Data leak management issues), and might throw IAE if there are too
     *  many classes. */
    public SupervisedOutput( SupervisedModelBuilder b ) {
      super(b);
      if( b==null ) return;     // This Output will be filled by the GUI not a Builder

      // flip the response to an ENUM here

      // Capture the data "shape" the model is valid on, after the response is moved to the end
      _names  = b._train.names  ();
      _domains= b._train.domains();

      // Compute class distribution, handy for most builders
      if( b.isClassifier() ) {
        MRUtils.ClassDist cdmt = new MRUtils.ClassDist(b._nclass).doAll(b._response);
        _distribution   = cdmt.dist();
        _priorClassDist = cdmt.rel_dist();
      } else {                    // Regression; only 1 "class"
        _distribution   = new long[] { b._train.numRows() };
        _priorClassDist = new float[] { 1.0f };
      }
      _modelClassDist = _priorClassDist;
    }

    /** @return Returns number of input features */
    @Override public int nfeatures() { return _names.length - 1; }

    /** @return number of classes; illegal to call before setting distribution */
    public int nclasses() { return _distribution.length; }
    public boolean isClassifier() { return nclasses()>1; }
    @Override public ModelCategory getModelCategory() {
      return nclasses()==1 
        ? Model.ModelCategory.Regression
        : (nclasses()==2 ? Model.ModelCategory.Binomial : Model.ModelCategory.Multinomial);
    }
  }


//  /**
//   * compute the model error for a given test data set
//   * For multi-class classification, this is the classification error based on assigning labels for the highest predicted per-class probability.
//   * For binary classification, this is the classification error based on assigning labels using the optimal threshold for maximizing the F1 score.
//   * For regression, this is the mean squared error (MSE).
//   * @param ftest Frame containing test data
//   * @param vactual The response column Vec
//   * @param fpreds Frame containing ADAPTED (domain labels from train+test data) predicted data (classification: label + per-class probabilities, regression: target)
//   * @param hitratio_fpreds Frame containing predicted data (domain labels from test data) (classification: label + per-class probabilities, regression: target)
//   * @param label Name for the scored data set to be printed
//   * @param printMe Whether to print the scoring results to Log.info
//   * @param max_conf_mat_size Largest size of Confusion Matrix (#classes) for it to be printed to Log.info
//   * @param cm Confusion Matrix object to populate for multi-class classification (also used for regression)
//   * @param auc AUC object to populate for binary classification
//   * @param hr HitRatio object to populate for classification
//   * @return model error, see description above
//   */
//  public double calcError(final Frame ftest, final Vec vactual,
//                          final Frame fpreds, final Frame hitratio_fpreds,
//                          final String label, final boolean printMe,
//                          final int max_conf_mat_size,
//                          final ConfusionMatrix cm,
//                          final AUC auc,
//                          final HitRatio hr)
//  {
//    StringBuilder sb = new StringBuilder();
//    double error = Double.POSITIVE_INFINITY;
//    // populate AUC
//    if (auc != null) {
//      assert(_output.isClassifier());
//      assert(_output.nclasses() == 2);
//      auc.actual = ftest;
//      auc.vactual = vactual;
//      auc.predict = fpreds;
//      auc.vpredict = fpreds.vecs()[2]; //binary classifier (label, prob0, prob1 (THIS ONE), adaptedlabel)
//      auc.threshold_criterion = AUC.ThresholdCriterion.maximum_F1;
//      auc.execImpl();
//      // auc.toASCII(sb);
//      error = auc.data().err(); //using optimal threshold for F1
//    }
//    // populate CM
//    if (cm != null) {
//      cm.actual = ftest;
//      cm.vactual = vactual;
//      cm.predict = fpreds;
//      cm.vpredict = fpreds.vecs()[0]; // prediction (either label or regression target)
//      cm.execImpl();
//      if (_output.isClassifier()) {
//        if (auc != null) {
//          //override the CM with the one computed by AUC (using optimal threshold)
//          //Note: must still call invoke above to set the domains etc.
//          cm.cm = new long[3][3]; // 1 extra layer for NaNs (not populated here, since AUC skips them)
//          cm.cm[0][0] = auc.data().cm()[0][0];
//          cm.cm[1][0] = auc.data().cm()[1][0];
//          cm.cm[0][1] = auc.data().cm()[0][1];
//          cm.cm[1][1] = auc.data().cm()[1][1];
//          assert(new ConfusionMatrix2(cm.cm).err() == auc.data().err()); //check consistency with AUC-computed error
//        } else {
//          error = new ConfusionMatrix2(cm.cm).err(); //only set error if AUC didn't already set the error
//        }
//        if (cm.cm.length <= max_conf_mat_size) cm.toASCII(sb);
//      } else {
//        assert(auc == null);
//        error = cm.mse;
//        cm.toASCII(sb);
//      }
//    }
//    // populate HitRatio
//    if (hr != null) {
//      assert(_output.isClassifier());
//      hr.actual = ftest;
//      hr.vactual = vactual;
//      hr.predict = hitratio_fpreds;
//      hr.execImpl();
//      hr.toASCII(sb);
//    }
//    if (printMe && sb.length() > 0) {
//      Log.info("Scoring on " + label + " data:");
//      for (String s : sb.toString().split("\n")) Log.info(s);
//    }
//    return error;
//  }

  /** Bulk scoring API for one row.  Chunks are all compatible with the model,
   *  and expect the last Chunks are for the final distribution and prediction.
   *  Default method is to just load the data into the tmp array, then call
   *  subclass scoring logic. */
  @Override protected float[] score0( Chunk chks[], int row_in_chunk, double[] tmp, float[] preds ) {
    assert chks.length>=_output._names.length; // Last chunk is for the response
    for( int i=0; i<_output._names.length-1; i++ ) // Do not include last value since it can contains a response
      tmp[i] = chks[i].at0(row_in_chunk);
    float[] scored = score0(tmp,preds);
    // Correct probabilities obtained from training on oversampled data back to original distribution
    // C.f. http://gking.harvard.edu/files/0s.pdf Eq.(27)
    if( _output.isClassifier() && _output._priorClassDist != null && _output._modelClassDist != null) {
      assert(scored.length == _output.nclasses()+1); //1 label + nclasses probs
      double probsum=0;
      for( int c=1; c<scored.length; c++ ) {
        final double original_fraction = _output._priorClassDist[c-1];
        assert(original_fraction > 0);
        final double oversampled_fraction = _output._modelClassDist[c-1];
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

