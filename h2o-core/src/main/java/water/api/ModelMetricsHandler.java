package water.api;

import hex.Model;
import hex.ModelMetrics;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OKeyNotFoundArgumentException;
import water.exceptions.H2ONotFoundArgumentException;
import water.fvec.Frame;
import water.util.Log;

class ModelMetricsHandler extends Handler {
  /** Class which contains the internal representation of the ModelMetrics list and params. */
  public static final class ModelMetricsList extends Iced {
    public Model _model;
    public Frame _frame;
    public ModelMetrics[] _model_metrics;
    public String _predictions_name;
    public boolean _reconstruction_error;
    public int _deep_features_hidden_layer = -1;

    // Fetch all metrics that match model and/or frame
    ModelMetricsList fetch() {
      final Key[] modelMetricsKeys = KeySnapshot.globalSnapshot().filter(new KeySnapshot.KVFilter() {
        @Override public boolean filter(KeySnapshot.KeyInfo k) {
          try {
            if( !Value.isSubclassOf(k._type, ModelMetrics.class) ) return false; // Fast-path cutout
            ModelMetrics mm = DKV.getGet(k._key);
            // If we're filtering by model filter by Model.  :-)
            if( _model != null && !mm.isForModel((Model)DKV.getGet(_model._key)) ) return false;
            // If we're filtering by frame filter by Frame.  :-)
            if( _frame != null && !mm.isForFrame((Frame)DKV.getGet(_frame._key)) ) return false;
          } catch( NullPointerException | ClassCastException ex ) {
            return false;       // Handle all kinds of broken racey key updates
          }
          return true;
        }
      }).keys();

      _model_metrics = new ModelMetrics[modelMetricsKeys.length];
      for (int i = 0; i < modelMetricsKeys.length; i++)
        _model_metrics[i] = DKV.getGet(modelMetricsKeys[i]);
      return this;              // Flow coding
    }

    // Delete the metrics that match model and/or frame
    ModelMetricsList delete() {
      ModelMetricsList matches = fetch();

      for (ModelMetrics mm : matches._model_metrics)
        DKV.remove(mm._key);

      return matches;
    }

    /** Return all the models matching the model&frame filters */
    public Schema list(int version, ModelMetricsList m) {
      return this.schema(version).fillFromImpl(m.fetch());
    }

    // TODO: almost identical to ModelsHandler; refactor
    public static ModelMetrics getFromDKV(String mm_key) { return getFromDKV(mm_key); }

    protected ModelMetricsListSchemaV3 schema(int version) {
      switch (version) {
      case 3:   return new ModelMetricsListSchemaV3();
      default:  throw H2O.fail("Bad version for ModelMetrics schema: " + version);
      }
    }
  } // class ModelMetricsList

  /** Schema for a list of ModelMetricsBase.
   *  This should be common across all versions of ModelMetrics schemas, so it lives here.   */
  public static final class ModelMetricsListSchemaV3 extends Schema<ModelMetricsList, ModelMetricsListSchemaV3> {
    // Input fields
    @API(help = "Key of Model of interest (optional)", json = true)
    public KeyV3.ModelKeyV3 model;

    @API(help = "Key of Frame of interest (optional)", json = true)
    public KeyV3.FrameKeyV3 frame;

    @API(help = "Key of predictions frame, if predictions are requested (optional)", json = true, required = false, direction = API.Direction.INOUT)
    public KeyV3.FrameKeyV3 predictions_frame;

    @API(help = "Compute reconstruction error (optional, only for Deep Learning AutoEncoder models)", json = false, required = false)
    public boolean reconstruction_error;

    @API(help = "Extract Deep Features for given hidden layer (optional, only for Deep Learning models)", json = false, required = false)
    public int deep_features_hidden_layer;

    // Output fields
    @API(help = "ModelMetrics", direction = API.Direction.OUTPUT)
    public ModelMetricsBase[] model_metrics;

    @Override public ModelMetricsHandler.ModelMetricsList fillImpl(ModelMetricsList mml) {
      // TODO: check for type!
      mml._model = (null == this.model || null == this.model.key() ? null : this.model.key().get());
      mml._frame = (null == this.frame || null == this.frame.key() ? null : this.frame.key().get());
      mml._predictions_name = (null == this.predictions_frame || null == this.predictions_frame.key() ? null : this.predictions_frame.key().toString());
      mml._reconstruction_error = this.reconstruction_error;
      mml._deep_features_hidden_layer = this.deep_features_hidden_layer;

      if (null != model_metrics) {
        mml._model_metrics = new ModelMetrics[model_metrics.length];
        for( int i=0; i<model_metrics.length; i++ )
          mml._model_metrics[i++] = (ModelMetrics)model_metrics[i].createImpl();
      }
      return mml;
    }

    @Override public ModelMetricsListSchemaV3 fillFromImpl(ModelMetricsList mml) {
      // TODO: this is failing in PojoUtils with an IllegalAccessException.  Why?  Different class loaders?
      // PojoUtils.copyProperties(this, m, PojoUtils.FieldNaming.CONSISTENT);

      // Shouldn't need to do this manually. . .
      this.model = (mml._model == null ? null : new KeyV3.ModelKeyV3(mml._model._key));
      this.frame = (mml._frame == null ? null : new KeyV3.FrameKeyV3(mml._frame._key));
      this.predictions_frame = (mml._predictions_name == null ? null : new KeyV3.FrameKeyV3(Key.make(mml._predictions_name)));
      this.reconstruction_error = mml._reconstruction_error;
      this.deep_features_hidden_layer = mml._deep_features_hidden_layer;

      if (null != mml._model_metrics) {
        this.model_metrics = new ModelMetricsBase[mml._model_metrics.length];
        for( int i=0; i<model_metrics.length; i++ ) {
          ModelMetrics mm = mml._model_metrics[i];
          try {
            this.model_metrics[i] = (ModelMetricsBase)Schema.schema(3, mm.getClass()).fillFromImpl(mm);
          }
          catch (H2ONotFoundArgumentException e) {
            this.model_metrics[i] = (ModelMetricsBase)Schema.schema(Schema.getExperimentalVersion(), mm.getClass()).fillFromImpl(mm);
          }
        }
      } else {
        this.model_metrics = new ModelMetricsBase[0];
      }
      return this;
    }
  } // ModelMetricsListSchemaV3

  // TODO: almost identical to ModelsHandler; refactor
  public static ModelMetrics getFromDKV(Key key) {
    if (null == key)
      throw new IllegalArgumentException("Got null key.");

    Value v = DKV.get(key);
    if (null == v)
      throw new IllegalArgumentException("Did not find key: " + key.toString());

    Iced ice = v.get();
    if (! (ice instanceof ModelMetrics))
      throw new IllegalArgumentException("Expected a Model for key: " + key.toString() + "; got a: " + ice.getClass());

    return (ModelMetrics)ice;
  }

  /** Return a single ModelMetrics. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ModelMetricsListSchemaV3 fetch(int version, ModelMetricsListSchemaV3 s) {
    ModelMetricsList m = s.createAndFillImpl();
    s.fillFromImpl(m.fetch());
    return s;
  }

  /** Delete one or more ModelMetrics. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ModelMetricsListSchemaV3 delete(int version, ModelMetricsListSchemaV3 s) {
    ModelMetricsList m = s.createAndFillImpl();
    s.fillFromImpl(m.delete());
    return s;
  }

  /**
   * Score a frame with the given model and return just the metrics.
   * <p>
   * NOTE: ModelMetrics are now always being created by model.score. . .
   */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ModelMetricsListSchemaV3 score(int version, ModelMetricsListSchemaV3 s) {
    // parameters checking:
    if (null == s.model) throw new H2OIllegalArgumentException("model", "predict", s.model);
    if (null == DKV.get(s.model.name)) throw new H2OKeyNotFoundArgumentException("model", "predict", s.model.name);

    if (null == s.frame) throw new H2OIllegalArgumentException("frame", "predict", s.frame);
    if (null == DKV.get(s.frame.name)) throw new H2OKeyNotFoundArgumentException("frame", "predict", s.frame.name);

    ModelMetricsList parms = s.createAndFillImpl();
    parms._model.score(parms._frame, parms._predictions_name).remove(); // throw away predictions, keep metrics as a side-effect
    ModelMetricsListSchemaV3 mm = this.fetch(version, s);

    // TODO: for now only binary predictors write an MM object.
    // For the others cons one up here to return the predictions frame.
    if (null == mm)
      mm = new ModelMetricsListSchemaV3();

    if (null == mm.model_metrics || 0 == mm.model_metrics.length) {
      Log.warn("Score() did not return a ModelMetrics for model: " + s.model + " on frame: " + s.frame);
    }

    return mm;
  }

  /**
   * Score a frame with the given model and return the metrics AND the prediction frame.
   */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ModelMetricsListSchemaV3 predict(int version, ModelMetricsListSchemaV3 s) {
    // parameters checking:
    if (null == s.model) throw new H2OIllegalArgumentException("model", "predict", s.model);
    if (null == DKV.get(s.model.name)) throw new H2OKeyNotFoundArgumentException("model", "predict", s.model.name);

    if (null == s.frame) throw new H2OIllegalArgumentException("frame", "predict", s.frame);
    if (null == DKV.get(s.frame.name)) throw new H2OKeyNotFoundArgumentException("frame", "predict", s.frame.name);

    ModelMetricsList parms = s.createAndFillImpl();

    Frame predictions;
    if (!s.reconstruction_error && s.deep_features_hidden_layer < 0 ) {
      if (null == parms._predictions_name)
        parms._predictions_name = "predictions" + Key.make().toString().substring(0,5) + "_" + parms._model._key.toString() + "_on_" + parms._frame._key.toString();
      predictions = parms._model.score(parms._frame, parms._predictions_name);
    } else {
      if (Model.DeepFeatures.class.isAssignableFrom(parms._model.getClass())) {
        if (s.reconstruction_error) {
          if (s.deep_features_hidden_layer >= 0)
            throw new H2OIllegalArgumentException("Can only compute either reconstruction error OR deep features.", "");
          if (null == parms._predictions_name)
            parms._predictions_name = "reconstruction_error" + Key.make().toString().substring(0,5) + "_" + parms._model._key.toString() + "_on_" + parms._frame._key.toString();
          predictions = ((Model.DeepFeatures) parms._model).scoreAutoEncoder(parms._frame, Key.make(parms._predictions_name));
        } else {
          if (s.deep_features_hidden_layer < 0)
            throw new H2OIllegalArgumentException("Deep features hidden layer index must be >= 0.", "");
          if (null == parms._predictions_name)
            parms._predictions_name = "deep_features" + Key.make().toString().substring(0,5) + "_" + parms._model._key.toString() + "_on_" + parms._frame._key.toString();
          predictions = ((Model.DeepFeatures) parms._model).scoreDeepFeatures(parms._frame, s.deep_features_hidden_layer);
        }
        predictions = new Frame(Key.make(parms._predictions_name), predictions.names(), predictions.vecs());
        DKV.put(predictions._key, predictions);
      }
      else throw new H2OIllegalArgumentException("Requires a Deep Learning model.", "Model must implement specific methods.");
    }

    ModelMetricsListSchemaV3 mm = this.fetch(version, s);

    // TODO: for now only binary predictors write an MM object.
    // For the others cons one up here to return the predictions frame.
    if (null == mm)
      mm = new ModelMetricsListSchemaV3();

    mm.predictions_frame = new KeyV3.FrameKeyV3(predictions._key);

    if (null == mm.model_metrics || 0 == mm.model_metrics.length) {
      // There was no response in the test set -> cannot make a model_metrics object
    } else {
      mm.model_metrics[0].predictions = new FrameV3(predictions, 0, 100); // TODO: Should call schema(version)
    }
    return mm;
  }

  /*
  NOTE: copy-pasted from Models, not yet munged for ModelMetrics:

  // Remove an unlocked model.  Fails if model is in-use
  public Schema delete(int version, Models models) {
    Model model = getFromDKV(models.key);
    if (null == model)
      throw new IllegalArgumentException("Model key not found: " + models.key);
    model.delete();             // lock & remove
    // TODO: Hm, which Schema should we use here?  Surely not a hardwired InspectV1. . .
    InspectV1 s = new InspectV1();
    s.key = models.key;
    return s;
  }

  // Remove ALL an unlocked models.  Throws IAE for all deletes that failed
  // (perhaps because the Models were locked & in-use).
  public Schema deleteAll(int version, Models models) {
    final Key[] modelKeys = KeySnapshot.globalSnapshot().filter(new KeySnapshot.KVFilter() {
        @Override public boolean filter(KeySnapshot.KeyInfo k) {
          return Value.isSubclassOf(k._type, Model.class);
        }
      }).keys();

    String err=null;
    Futures fs = new Futures();
    for( int i = 0; i < modelKeys.length; i++ ) {
      try {
        getFromDKV(modelKeys[i]).delete(null,fs);
      } catch( IllegalArgumentException iae ) {
        err += iae.getMessage();
      }
    }
    fs.blockForPending();
    if( err != null ) throw new IllegalArgumentException(err);

    // TODO: Hm, which Schema should we use here?  Surely not a hardwired InspectV1. . .
    InspectV1 s = new InspectV1();
    return s;
  }
  */

}
