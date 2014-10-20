package hex.gbm;

import hex.Model;
import hex.SupervisedModel;
import hex.schemas.SharedTreeModelV2;
import water.H2O;
import water.Key;
import water.api.ModelSchema;
import water.fvec.Frame;
import water.fvec.Vec;

public abstract class SharedTreeModel<M extends SharedTreeModel<M,P,O>, P extends SharedTreeModel.SharedTreeParameters, O extends SharedTreeModel.SharedTreeOutput> extends SupervisedModel<M,P,O> {
  public abstract static class SharedTreeParameters extends SupervisedModel.SupervisedParameters {
    public int _ntrees=50; // Number of trees. Grid Search, comma sep values:50,100,150,200

    @Override public int sanityCheckParameters() {
      if( _ntrees < 0 || _ntrees > 100000 ) validation_error("_ntrees", "ntrees must be between 1 and 100000");

      Frame train = _training_frame.get();
      Vec response = train.vecs()[train.find(_response_column)];

      // Should be handled by input
      assert (_classification && (response.isInt() || response.isEnum())) ||   // Classify Int or Enums
        (!_classification && !response.isEnum()) : "Classification="+_classification + " and response="+response.isInt();  // Regress  Int or Float
      
      //if (source.numRows() - response.naCnt() <=0)
      //  throw new IllegalArgumentException("Dataset contains too many NAs!");
      //
      //_ncols = _train.length;
      //_nrows = source.numRows() - response.naCnt();
      //
      //assert (_nrows>0) : "Dataset contains no rows - validation of input parameters is probably broken!";
      //// Transform response to enum
      //// TODO: moved to shared model job
      //if( !response.isEnum() && classification ) {
      //  response = response.toEnum();
      //  gtrash(response); //_gen_enum = true;
      //}
      //_nclass = response.isEnum() ? (char)(response.domain().length) : 1;
      //if (classification && _nclass <= 1)
      //  throw new IllegalArgumentException("Constant response column!");
      //if (_nclass > MAX_SUPPORTED_LEVELS)
      //  throw new IllegalArgumentException("Too many levels in response column!");
      //
      //int usableColumns = 0;
      //assert _ncols == _train.length : "Number of selected train columns does not correspond to a number of columns!";
      //for (int i = 0; i < _ncols; i++) {
      //  Vec v = _train[i];
      //  if (v.isBad() || v.isConst()) continue;
      //  usableColumns++;
      //}
      //if (usableColumns==0) throw new IllegalArgumentException("There is no usable column to generate model!");
      //
      //if (checkpoint!=null && DKV.get(checkpoint)==null) throw new IllegalArgumentException("Checkpoint "+checkpoint.toString() + " does not exists!");
      //
      throw H2O.unimpl();
      //return validation_error_count;
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

