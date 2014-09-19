package water.api;

import hex.*;
import water.*;
import water.fvec.Frame;
import water.util.Log;

class ModelMetricsHandler extends Handler<ModelMetricsHandler.ModelMetricsList, ModelMetricsHandler.ModelMetricsListSchema> {
  @Override protected int min_ver() { return 3; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  /** Class which contains the internal representation of the ModelMetrics list and params. */
  public static final class ModelMetricsList extends Iced {
    public UniqueId model;
    public UniqueId frame;
    public ModelMetrics[] model_metrics;

    public ModelMetrics[] fetch() {
      final Key[] modelMetricsKeys = KeySnapshot.globalSnapshot().filter(new KeySnapshot.KVFilter() {
        @Override
        public boolean filter(KeySnapshot.KeyInfo k) {
          if (!Value.isSubclassOf(k._type, ModelMetrics.class))
            return false;
          if (null == model && null == frame)
            return true;

          Value mmv = DKV.get(k._key);
          if (null == mmv)
            throw H2O.fail("Failed to find ModelMetrics object for which we have a key: " + k._key.toString());
          if ("water.ModelMetrics".equals(mmv.className()))
            throw H2O.fail("ModelMetrics key points to a non-ModelMetrics object in the DKV: " + k._key.toString() + " has class: " + mmv.className());

          ModelMetrics mm = mmv.get();
          if (null == mm)
            throw H2O.fail("Failed to find ModelMetrics object for which we have a key: " + k._key.toString());

          // If we're filtering by model filter by Model.  :-)
          if (model != null) {
            // TODO: support old model versions
            Value v = DKV.get(model.getKey());
            if (null == v)
              return false; // Warn that the model is gone?  TODO: allow fetch of metrics for deleted Frames and Models.

            if (!v.isModel() || !mm.isForModel((Model) v.get())) return false;
          }

          // If we're filtering by frame filter by Frame.  :-)
          if (frame != null) {
            // TODO: support old frame versions
            Value v = DKV.get(frame.getKey());
            if (null == v)
              return false; // Warn that the frame is gone?  TODO: allow fetch of metrics for deleted Frames and Models.

            if (!v.isFrame() || !mm.isForFrame((Frame) v.get())) return false;
          }

          return true;
        }
      }).keys();

      ModelMetrics[] model_metrics_list = new ModelMetrics[modelMetricsKeys.length];
      for (int i = 0; i < modelMetricsKeys.length; i++) {
        Key key = modelMetricsKeys[i];
        Value v = DKV.get(key);
        if (null == v) {
          Log.warn("ModelMetrics key not found in DKV: " + key.toString());
          continue;
        }
        if (!"water.ModelMetrics".equals(v.className())) {
          Log.warn("ModelMetrics key: " + key.toString() + " points to a value of some other class: " + v.className());
          continue;
        }

        ModelMetrics model_metrics = v.get();
        model_metrics_list[i] = model_metrics;
      }

      return model_metrics_list;
    }

    /**
     * Return all the models.
     */
    public Schema list(int version, ModelMetricsList m) {
      m.model_metrics = m.fetch();
      return this.schema(version).fillFromImpl(m);
    }

    // TODO: almost identical to ModelsHandler; refactor
    public static ModelMetrics getFromDKV(String mm_key) {
      return getFromDKV(mm_key);
    }

    /*
    public static ModelMetrics getFromDKV(String model_key, String frame_key) {
      return getFromDKV(ModelMetrics.buildKey(model_key, frame_key));
    }
    */

    protected ModelMetricsListSchema schema(int version) {
      switch (version) {
        case 3:
          return new ModelMetricsListSchema();
        default:
          throw H2O.fail("Bad version for ModelMetrics schema: " + version);
      }
    }
  } // class ModelMetricsList

  /**
   * Schema for a list of ModelMetricsBase.
   * This should be common across all versions of ModelMetrics schemas, so it lives here.
   */
  public static final class ModelMetricsListSchema extends Schema<ModelMetricsList, ModelMetricsListSchema> {
    // Input fields
    @API(help = "Key of Model of interest (optional)", json = false)
    public UniqueIdBase model;

    @API(help = "Key of Frame of interest (optional)", json = false)
    public UniqueIdBase frame;

    // Output fields
    @API(help = "ModelMetrics", direction = API.Direction.OUTPUT)
    public ModelMetricsBase[] model_metrics;

    @Override public ModelMetricsHandler.ModelMetricsList createImpl() {
      ModelMetricsList mml = new ModelMetricsList();
      mml.model = this.model.createImpl();
      mml.frame = this.frame.createImpl();

      if (null != model_metrics) {
        mml.model_metrics = new ModelMetrics[model_metrics.length];

        int i = 0;
        for (ModelMetricsBase mmb : this.model_metrics) {
          mml.model_metrics[i++] = mmb.createImpl();
        }
      }
      return mml;
    }

    @Override public ModelMetricsListSchema fillFromImpl(ModelMetricsList mml) {
      // TODO: this is failing in PojoUtils with an IllegalAccessException.  Why?  Different class loaders?
      // PojoUtils.copyProperties(this, m, PojoUtils.FieldNaming.CONSISTENT);

      // Shouldn't need to do this manually. . .
      this.model = new UniqueIdV3().fillFromImpl(mml.model); // TODO: shouldn't have a hardwired version
      this.frame = new UniqueIdV3().fillFromImpl(mml.frame);

      if (null != mml.model_metrics) {
        this.model_metrics = new ModelMetricsBase[mml.model_metrics.length];

        int i = 0;
        for (ModelMetrics mm : mml.model_metrics) {
          this.model_metrics[i++] = mm.schema().fillFromImpl(mm);
        }
      }
      return this;
    }
  } // ModelMetricsListSchema

  // TODO: almost identical to ModelsHandler; refactor
  public static ModelMetrics getFromDKV(Key key) {
    if (null == key)
      throw new IllegalArgumentException("Got null key.");

    Value v = DKV.get(key);
    if (null == v)
      throw new IllegalArgumentException("Did not find key: " + key.toString());

    Iced ice = v.get();
    if (! (ice instanceof Model))
      throw new IllegalArgumentException("Expected a Model for key: " + key.toString() + "; got a: " + ice.getClass());

    return (ModelMetrics)ice;
  }

  /** Return a single ModelMetrics. */
  public Schema fetch(int version, ModelMetricsList m) {
    ModelMetrics[] mm = m.fetch();
    ModelMetricsListSchema schema = this.schema(version).fillFromImpl(m);
    return schema;
  }

  /** Return all ModelMetrics. */
  public Schema list(int version, ModelMetricsList ignore) {
    ModelMetricsList mm = new ModelMetricsList();
    mm.model = null;
    mm.frame = null;
    return fetch(version, mm);
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


  @Override protected ModelMetricsListSchema schema(int version) {
    switch (version) {
    case 3:   return new ModelMetricsListSchema();
    default:  throw H2O.fail("Bad version for ModelMetrics schema: " + version);
    }
  }

  // Need to stub this because it's required by H2OCountedCompleter:
  @Override public void compute2() { throw H2O.fail(); }

}
