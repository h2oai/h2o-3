package hex.gbm;

import hex.SupervisedModel;
import water.H2O;
import water.Key;
import water.fvec.Frame;

public abstract class SharedTreeModel<M extends SharedTreeModel<M,P,O>, P extends SharedTreeModel.SharedTreeParameters, O extends SharedTreeModel.SharedTreeOutput> extends SupervisedModel<M,P,O> {

  public abstract static class SharedTreeParameters extends SupervisedModel.SupervisedParameters {
    /** Maximal number of supported levels in response. */
    private static final int MAX_SUPPORTED_LEVELS = 1000;

    public int _ntrees=50; // Number of trees. Grid Search, comma sep values:50,100,150,200

    public boolean _importance = false; // compute variable importance

    @Override public int sanityCheckParameters() {
      super.sanityCheckParameters();
      if( _ntrees < 0 || _ntrees > 100000 ) validation_error("_ntrees", "ntrees must be between 1 and 100000");
      if (_nclass > MAX_SUPPORTED_LEVELS)
        throw new IllegalArgumentException("Too many levels in response column!");
      //if (checkpoint!=null && DKV.get(checkpoint)==null) throw new IllegalArgumentException("Checkpoint "+checkpoint.toString() + " does not exists!");
      return _validation_error_count;
    }
  }

  public abstract static class SharedTreeOutput extends SupervisedModel.Output {

    /** Initially predicted value (for zero trees) */
    double _initialPrediction;

    @Override public int nfeatures() { return _names.length; }

    @Override public ModelCategory getModelCategory() {
      throw H2O.unimpl();       // Can be regression or multinomial
      //return Model.ModelCategory.Clustering;
    }
  }

  public SharedTreeModel(Key selfKey, Frame fr, P parms, O output) {
    super(selfKey,fr,parms,output,null/*no prior class dist*/);
  }

  @Override public boolean isSupervised() {return true;}

  @Override protected float[] score0(double data[/*ncols*/], float preds[/*nclasses+1*/]) {
    throw H2O.unimpl();
  }
}

