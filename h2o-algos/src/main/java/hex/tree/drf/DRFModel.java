package hex.tree.drf;

import static hex.genmodel.GenModel.getPrediction;
import hex.tree.SharedTreeModel;
import water.Key;
import water.fvec.Chunk;
import water.util.MathUtils;
import water.util.SB;

public class DRFModel extends SharedTreeModel<DRFModel,DRFModel.DRFParameters,DRFModel.DRFOutput> {

  public static class DRFParameters extends SharedTreeModel.SharedTreeParameters {
    int _mtries = -1;
    float _sample_rate = 2f/3f;
    public boolean _build_tree_one_node = false;
    public DRFParameters() {
      super();
      // Set DRF-specific defaults (that differ from SharedTreeModel's defaults)
      _ntrees = 50;
      _max_depth = 20;
      _min_rows = 1;
    }
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
    super.score0(data, preds);
    int N = _parms._ntrees;
    if (_output.nclasses() == 1) { // regression - compute avg over all trees
      preds[0] /= N;
      return preds;
    }
    else { // classification
      float sum = MathUtils.sum(preds);
      if (sum>0) MathUtils.div(preds, sum);
      preds[0] = getPrediction(preds, data);
    }
    return preds;
  }

  @Override protected void toJavaUnifyPreds(SB body, SB file) {
    if (_output.nclasses() == 1) { // Regression
      body.ip("preds[0] /= " + _output._ntrees + ";").nl();
    } else { // Classification
      body.ip("float sum = 0;").nl();
      body.ip("for(int i=1; i<preds.length; i++) { sum += preds[i]; }").nl();
      body.ip("if (sum>0) for(int i=1; i<preds.length; i++) { preds[i] /= sum; }").nl();
      body.ip("preds[0] = hex.genmodel.GenModel.getPrediction(preds, data);").nl();
    }
  }

}
