package hex.tree.drf;

import hex.tree.SharedTreeModel;
import hex.tree.drf.DRF;
import water.Key;
import water.fvec.Chunk;
import water.util.ArrayUtils;
import water.util.MathUtils;
import water.util.ModelUtils;

import java.util.Arrays;

public class DRFModel extends SharedTreeModel<DRFModel,DRFModel.DRFParameters,DRFModel.DRFOutput> {

  public static class DRFParameters extends SharedTreeModel.SharedTreeParameters {
    int _mtries = -1;
    float _sample_rate = 2f/3f;
    boolean _do_grpsplit = true;
    public boolean _build_tree_one_node = false;
  }

  public static class DRFOutput extends SharedTreeModel.SharedTreeOutput {
    public DRFOutput( DRF b, double mse_train, double mse_valid ) { super(b,mse_train,mse_valid); }
  }

  public DRFModel(Key selfKey, DRFParameters parms, DRFOutput output ) { super(selfKey,parms,output); }

  /** Bulk scoring API for one row.  Chunks are all compatible with the model,
   *  and expect the last Chunks are for the final distribution and prediction.
   *  Default method is to just load the data into the tmp array, then call
   *  subclass scoring logic. */
  @Override public float[] score0( Chunk chks[], int row_in_chunk, double[] tmp, float[] preds ) {
    assert chks.length>=tmp.length;
    for( int i=0; i<tmp.length; i++ )
      tmp[i] = chks[i].atd(row_in_chunk);
    return score0(tmp,preds);
  }


  @Override protected float[] score0(double data[], float preds[]) {
    float[] p = super.score0(data, preds);
    int N = _parms._ntrees;
    if (p.length==1) { if (N>0) MathUtils.div(p, N); } // regression - compute avg over all trees
    else { // classification
      float s = MathUtils.sum(p);
      if (s>0) MathUtils.div(p, s); // unify over all classes
      p[0] = ModelUtils.getPrediction(p, data);
    }
    return p;
  }

//  @Override protected float[] score0(double data[/*ncols*/], float preds[/*nclasses+1*/]) {
//    float[] p = super.score0(data, preds);
//    if( _output.nclasses()>1 ) { // classification
//      if( _output.nclasses()==2 ) { // Kept the initial prediction for binomial
//        p[1] += _output._initialPrediction;
//        p[2] = - p[1];
//      }
//      // Because we call Math.exp, we have to be numerically stable or else
//      // we get Infinities, and then shortly NaN's.  Rescale the data so the
//      // largest value is +/-1 and the other values are smaller.
//      // See notes here:  http://www.hongliangjie.com/2011/01/07/logsum/
//      float maxval=Float.NEGATIVE_INFINITY;
//      float dsum=0;
//      // Find a max
//      for( int k=1; k<p.length; k++) maxval = Math.max(maxval,p[k]);
//      assert !Float.isInfinite(maxval) : "Something is wrong with DRF trees since returned prediction is " + Arrays.toString(p);
//      for(int k=1; k<p.length;k++)
//        dsum+=(p[k]=(float)Math.exp(p[k]-maxval));
//      ArrayUtils.div(p,dsum);
//      p[0] = water.util.ModelUtils.getPrediction(p, data);
//    } else { // regression
//      // Prediction starts from the mean response, and adds predicted residuals
//      preds[0] += _output._initialPrediction;
//    }
//    return p;
//  }

}
