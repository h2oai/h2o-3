package hex.gbm;

import hex.Model;
import hex.schemas.GBMModelV2;
import java.util.Arrays;
import water.H2O;
import water.Key;
import water.api.ModelSchema;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.ArrayUtils;

public class GBMModel extends SharedTreeModel<GBMModel,GBMModel.GBMParameters,GBMModel.GBMOutput> {

  public static class GBMParameters extends SharedTreeModel.SharedTreeParameters {
    // SharedTreeBuilder
    public int ntrees;          // Number of trees. Grid Search, comma sep values:50,100,150,200

    // GBM specific parms
    public float learn_rate;    // Learning rate from 0.0 to 1.0
    public long seed;           // RNG seed for balancing classes
    public GBM.Family loss = GBM.Family.AUTO;

    @Override public int sanityCheckParameters() {
      if( !(0. < learn_rate && learn_rate <= 1.0) ) validation_error("learn_rate", "learn_rate must be between 0 and 1");
      return validation_error_count;
    }
  }

  public static class GBMOutput extends SharedTreeModel.SharedTreeOutput {

    /** Initially predicted value (for zero trees) */
    double initialPrediction;


    @Override public int nfeatures() { return _names.length; }

    @Override public ModelCategory getModelCategory() {
      throw H2O.unimpl();       // Can be regression or multinomial
      //return Model.ModelCategory.Clustering;
    }
  }

  public GBMModel(Key selfKey, Frame fr, GBMParameters parms, GBMOutput output, int ncats) {
    super(selfKey,fr,parms,output);
  }

  // Default publically visible Schema is V2
  @Override public ModelSchema schema() { return new GBMModelV2(); }

  /** Bulk scoring API for one row.  Chunks are all compatible with the model,
   *  and expect the last Chunks are for the final distribution and prediction.
   *  Default method is to just load the data into the tmp array, then call
   *  subclass scoring logic. */
  @Override protected float[] score0( Chunk chks[], int row_in_chunk, double[] tmp, float[] preds ) {
    assert chks.length>=_output._names.length;
    for( int i=0; i<_output._names.length; i++ )
      tmp[i] = chks[i].at0(row_in_chunk);
    return score0(tmp,preds);
  }

  @Override protected float[] score0(double data[/*ncols*/], float preds[/*nclasses+1*/]) {
    float[] p = super.score0(data, preds);    // These are f_k(x) in Algorithm 10.4
    if( _parms.loss == GBM.Family.bernoulli ) {
      double fx = p[1] + _output.initialPrediction;
      p[2] = 1.0f/(float)(1f+Math.exp(-fx));
      p[1] = 1f-p[2];
      p[0] = water.util.ModelUtils.getPrediction(p, data);
      return p;
    }
    if( _output.nclasses()>1 ) { // classification
      // Because we call Math.exp, we have to be numerically stable or else
      // we get Infinities, and then shortly NaN's.  Rescale the data so the
      // largest value is +/-1 and the other values are smaller.
      // See notes here:  http://www.hongliangjie.com/2011/01/07/logsum/
      float maxval=Float.NEGATIVE_INFINITY;
      float dsum=0;
      if( _output.nclasses()==2 )  p[2] = - p[1];
      // Find a max
      for( int k=1; k<p.length; k++) maxval = Math.max(maxval,p[k]);
      assert !Float.isInfinite(maxval) : "Something is wrong with GBM trees since returned prediction is " + Arrays.toString(p);
      for(int k=1; k<p.length;k++)
        dsum+=(p[k]=(float)Math.exp(p[k]-maxval));
      ArrayUtils.div(p,dsum);
      p[0] = water.util.ModelUtils.getPrediction(p, data);
    } else { // regression
      // Prediction starts from the mean response, and adds predicted residuals
      preds[0] += _output.initialPrediction;
    }
    return p;
  }

}
