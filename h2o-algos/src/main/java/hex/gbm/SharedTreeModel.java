package hex.gbm;

import hex.Model;
import hex.SupervisedModel;
import hex.schemas.SharedTreeModelV2;
import water.H2O;
import water.Key;
import water.api.ModelSchema;
import water.fvec.Frame;

public abstract class SharedTreeModel<M extends SharedTreeModel<M,P,O>, P extends SharedTreeModel.SharedTreeParameters, O extends SharedTreeModel.SharedTreeOutput> extends SupervisedModel<M,P,O> {
  public abstract static class SharedTreeParameters extends SupervisedModel.Parameters {
    public int ntrees;          // Number of trees. Grid Search, comma sep values:50,100,150,200

    @Override public int sanityCheckParameters() {
      if( ntrees < 0 || ntrees > 100000 ) validation_error("ntrees", "ntrees must be between 1 and 100000");
      return validation_error_count;
    }
  }

  public abstract static class SharedTreeOutput extends SupervisedModel.Output {

    /** Initially predicted value (for zero trees) */
    double initialPrediction;

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

