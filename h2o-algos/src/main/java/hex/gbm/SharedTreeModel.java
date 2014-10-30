package hex.gbm;

import hex.SupervisedModel;
import hex.schemas.SharedTreeModelV2;
import water.*;
import water.api.ModelSchema;
import water.fvec.Frame;

public abstract class SharedTreeModel<M extends SharedTreeModel<M,P,O>, P extends SharedTreeModel.SharedTreeParameters, O extends SharedTreeModel.SharedTreeOutput> extends SupervisedModel<M,P,O> {

  public abstract static class SharedTreeParameters extends SupervisedModel.SupervisedParameters {
    /** Maximal number of supported levels in response. */
    static final int MAX_SUPPORTED_LEVELS = 1000;

    public int _requested_ntrees=50; // Number of trees. Grid Search, comma sep values:50,100,150,200

    public boolean _importance = false; // compute variable importance

    public long _seed;          // Seed for psuedo-random redistribution

    // A Model Key for restarting a checkpointed Model, or null
    public Key _checkpoint;
  }

  public abstract static class SharedTreeOutput extends SupervisedModel.SupervisedOutput {

    /** Initially predicted value (for zero trees) */
    double _initialPrediction;

    /** Number of trees actually in the model (as opposed to requested) */
    int _ntrees;

    public SharedTreeOutput( SharedTree b ) { super(b); }

    @Override public int nfeatures() { return _names.length; }

    @Override public ModelCategory getModelCategory() {
      throw H2O.unimpl();       // Can be regression or multinomial
      //return Model.ModelCategory.Clustering;
    }
  }

  public SharedTreeModel(Key selfKey, P parms, O output) { super(selfKey,parms,output); }

  @Override public boolean isSupervised() {return true;}

  @Override protected float[] score0(double data[/*ncols*/], float preds[/*nclasses+1*/]) {
    throw H2O.unimpl();
  }
}

