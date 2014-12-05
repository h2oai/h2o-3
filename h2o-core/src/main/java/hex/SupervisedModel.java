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

  @Override public boolean isSupervised() { return true; }

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

    /** The maximum number (top K) of predictions to use for hit ratio
     *  computation (for multi-class only, 0 to disable) */
    public int _max_hit_ratio_k = 10;

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

  /** Bulk scoring API for one row.  Chunks are all compatible with the model,
   *  and expect the last Chunks are for the final distribution and prediction.
   *  Default method is to just load the data into the tmp array, then call
   *  subclass scoring logic. */
  @Override public float[] score0( Chunk chks[], int row_in_chunk, double[] tmp, float[] preds ) {
    assert chks.length>=_output._names.length; // Last chunk is for the response
    for( int i=0; i<_output._names.length-1; i++ ) // Do not include last value since it can contains a response
      tmp[i] = chks[i].at0(row_in_chunk);
    float[] scored = score0(tmp,preds);
    // Correct probabilities obtained from training on oversampled data back to original distribution
    // C.f. http://gking.harvard.edu/files/0s.pdf Eq.(27)
    if( _output.isClassifier() && _output._priorClassDist != null && _output._modelClassDist != null) {
      ModelUtils.correctProbabilities(scored,_output._priorClassDist, _output._modelClassDist);
      //set label based on corrected probabilities (max value wins, with deterministic tie-breaking)
      scored[0] = ModelUtils.getPrediction(scored, tmp);
    }
    return scored;
  }

}

