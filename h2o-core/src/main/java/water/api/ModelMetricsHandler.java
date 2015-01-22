package water.api;

import hex.Model;
import hex.ModelMetrics;
import water.*;
import water.fvec.Frame;
import water.util.Log;

class ModelMetricsHandler extends Handler {
  /** Class which contains the internal representation of the ModelMetrics list and params. */
  public static final class ModelMetricsList extends Iced {
    public Model _model;
    public Frame _frame;
    public ModelMetrics[] _model_metrics;

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
    @API(help = "Key of Model of interest (optional)", json = false)
    public String model;

    @API(help = "Key of Frame of interest (optional)", json = false)
    public String frame;

    // Output fields
    @API(help = "ModelMetrics", direction = API.Direction.OUTPUT)
    public ModelMetricsBase[] model_metrics;

    @Override public ModelMetricsHandler.ModelMetricsList fillImpl(ModelMetricsList mml) {
      mml._model = DKV.getGet(this.model);
      mml._frame = DKV.getGet(this.frame);
      if (null != model_metrics) {
        mml._model_metrics = new ModelMetrics[model_metrics.length];
        for( int i=0; i<model_metrics.length; i++ )
          mml._model_metrics[i++] = model_metrics[i].createImpl();
      }
      return mml;
    }

    @Override public ModelMetricsListSchemaV3 fillFromImpl(ModelMetricsList mml) {
      // TODO: this is failing in PojoUtils with an IllegalAccessException.  Why?  Different class loaders?
      // PojoUtils.copyProperties(this, m, PojoUtils.FieldNaming.CONSISTENT);

      // Shouldn't need to do this manually. . .
      this.model = (null == mml._model ? null : mml._model._key.toString());
      this.frame = (null == mml._frame ? null : mml._frame._key.toString());

      if (null != mml._model_metrics) {
        this.model_metrics = new ModelMetricsBase[mml._model_metrics.length];
        for( int i=0; i<model_metrics.length; i++ ) {
          ModelMetrics mm = mml._model_metrics[i];
          this.model_metrics[i] = mm.schema().fillFromImpl(mm);
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

  /**
   * Score a frame with the given model and return just the metrics.
   */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ModelMetricsListSchemaV3 score(int version, ModelMetricsListSchemaV3 s) {
    // NOTE: ModelMetrics are now always being created by model.score. . .
    ModelMetricsList parms = s.createAndFillImpl();
    ModelMetrics metrics = ModelMetrics.getFromDKV(parms._model, parms._frame);

    if (null != metrics) {
      Log.debug("using ModelMetrics from the cache. . .");
      return this.fetch(version, s);
    }
    Log.debug("Cache miss: computing ModelMetrics. . .");
    parms._model.score(parms._frame); // throw away predictions
    ModelMetricsListSchemaV3 mm = this.fetch(version, s);

    // TODO: for now only binary predictors write an MM object.
    // For the others cons one up here to return the predictions frame.
    if (null == mm)
      mm = new ModelMetricsListSchemaV3();

    if (null == mm.model_metrics || 0 == mm.model_metrics.length) {
      mm.model_metrics = new ModelMetricsV3[1];
      mm.model_metrics[0] = new ModelMetricsV3();
    }

    return mm;
  }

  /**
   * Score a frame with the given model and return the metrics AND the prediction frame.
   */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ModelMetricsListSchemaV3 predict(int version, ModelMetricsListSchemaV3 s) {
    // No caching for predict()
    ModelMetricsList parms = s.createAndFillImpl();
    Frame predictions = parms._model.score(parms._frame);
    ModelMetricsListSchemaV3 mm = this.fetch(version, s);

    // TODO: for now only binary predictors write an MM object.
    // For the others cons one up here to return the predictions frame.
    if (null == mm)
      mm = new ModelMetricsListSchemaV3();

    if (null == mm.model_metrics || 0 == mm.model_metrics.length) {
      mm.model_metrics = new ModelMetricsV3[1];
      mm.model_metrics[0] = new ModelMetricsV3();
    }

    Frame persisted = new Frame(Key.make("predictions_" + Key.rand()), predictions.names(), predictions.vecs());
    DKV.put(persisted);
    mm.model_metrics[0].predictions = new FrameV2(persisted, 0, 100); // TODO: Should call schema(version)
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
