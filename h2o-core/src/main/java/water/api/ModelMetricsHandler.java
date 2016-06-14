package water.api;

import hex.Model;
import hex.ModelMetrics;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OKeyNotFoundArgumentException;
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
    public boolean _reconstruction_error_per_feature;
    public int _deep_features_hidden_layer = -1;
    public boolean _reconstruct_train;
    public boolean _project_archetypes;
    public boolean _reverse_transform;
    public boolean _leaf_node_assignment;
    public int _exemplar_index = -1;

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
  public static final class ModelMetricsListSchemaV3 extends SchemaV3<ModelMetricsList, ModelMetricsListSchemaV3> {
    // Input fields
    @API(help = "Key of Model of interest (optional)", json = true)
    public KeyV3.ModelKeyV3 model;

    @API(help = "Key of Frame of interest (optional)", json = true)
    public KeyV3.FrameKeyV3 frame;

    @API(help = "Key of predictions frame, if predictions are requested (optional)", json = true, required = false, direction = API.Direction.INOUT)
    public KeyV3.FrameKeyV3 predictions_frame;

    @API(help = "Compute reconstruction error (optional, only for Deep Learning AutoEncoder models)", json = false, required = false)
    public boolean reconstruction_error;

    @API(help = "Compute reconstruction error per feature (optional, only for Deep Learning AutoEncoder models)", json = false, required = false)
    public boolean reconstruction_error_per_feature;

    @API(help = "Extract Deep Features for given hidden layer (optional, only for Deep Learning models)", json = false, required = false)
    public int deep_features_hidden_layer;

    @API(help = "Reconstruct original training frame (optional, only for GLRM models)", json = false, required = false)
    public boolean reconstruct_train;

    @API(help = "Project GLRM archetypes back into original feature space (optional, only for GLRM models)", json = false, required = false)
    public boolean project_archetypes;

    @API(help = "Reverse transformation applied during training to model output (optional, only for GLRM models)", json = false, required = false)
    public boolean reverse_transform;

    @API(help = "Return the leaf node assignment (optional, only for DRF/GBM models)", json = false, required = false)
    public boolean leaf_node_assignment;

    @API(help = "Retrieve all members for a given exemplar (optional, only for Aggregator models)", json = false, required = false)
    public int exemplar_index;

    // Output fields
    @API(help = "ModelMetrics", direction = API.Direction.OUTPUT)
    public ModelMetricsBase[] model_metrics;

    @Override public ModelMetricsHandler.ModelMetricsList fillImpl(ModelMetricsList mml) {
      // TODO: check for type!
      mml._model = (null == this.model || null == this.model.key() ? null : this.model.key().get());
      mml._frame = (null == this.frame || null == this.frame.key() ? null : this.frame.key().get());
      mml._predictions_name = (null == this.predictions_frame || null == this.predictions_frame.key() ? null : this.predictions_frame.key().toString());
      mml._reconstruction_error = this.reconstruction_error;
      mml._reconstruction_error_per_feature = this.reconstruction_error_per_feature;
      mml._deep_features_hidden_layer = this.deep_features_hidden_layer;
      mml._reconstruct_train = this.reconstruct_train;
      mml._project_archetypes = this.project_archetypes;
      mml._reverse_transform = this.reverse_transform;
      mml._leaf_node_assignment = this.leaf_node_assignment;
      mml._exemplar_index = this.exemplar_index;

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
      this.predictions_frame = (mml._predictions_name == null ? null : new KeyV3.FrameKeyV3(Key.<Frame>make(mml._predictions_name)));
      this.reconstruction_error = mml._reconstruction_error;
      this.reconstruction_error_per_feature = mml._reconstruction_error_per_feature;
      this.deep_features_hidden_layer = mml._deep_features_hidden_layer;
      this.reconstruct_train = mml._reconstruct_train;
      this.project_archetypes = mml._project_archetypes;
      this.reverse_transform = mml._reverse_transform;
      this.leaf_node_assignment = mml._leaf_node_assignment;
      this.exemplar_index = mml._exemplar_index;

      if (null != mml._model_metrics) {
        this.model_metrics = new ModelMetricsBase[mml._model_metrics.length];
        for( int i=0; i<model_metrics.length; i++ ) {
          ModelMetrics mm = mml._model_metrics[i];
          this.model_metrics[i] = (ModelMetricsBase) Schema.schema(3, mm.getClass()).fillFromImpl(mm);
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
  public JobV3 predictAsync(int version, final ModelMetricsListSchemaV3 s) {
    // parameters checking:
    if (null == s.model) throw new H2OIllegalArgumentException("model", "predict", s.model);
    if (null == DKV.get(s.model.name)) throw new H2OKeyNotFoundArgumentException("model", "predict", s.model.name);

    if (null == s.frame) throw new H2OIllegalArgumentException("frame", "predict", s.frame);
    if (null == DKV.get(s.frame.name)) throw new H2OKeyNotFoundArgumentException("frame", "predict", s.frame.name);

    final ModelMetricsList parms = s.createAndFillImpl();
    
    //predict2 does not return modelmetrics, so cannot handle deeplearning: reconstruction_error (anomaly) or GLRM: reconstruct and archetypes
    //predict2 can handle deeplearning: deepfeatures and predict 
    
    if (s.deep_features_hidden_layer > 0) {
      if (null == parms._predictions_name)
        parms._predictions_name = "deep_features" + Key.make().toString().substring(0, 5) + "_" +
                parms._model._key.toString() + "_on_" + parms._frame._key.toString();
    } else if (null == parms._predictions_name) {
      if (parms._exemplar_index >= 0) {
        parms._predictions_name = "members_" + parms._model._key.toString() + "_for_exemplar_" + parms._exemplar_index;
      } else {
        parms._predictions_name = "predictions" + Key.make().toString().substring(0, 5) + "_" + parms._model._key.toString() + "_on_" + parms._frame._key.toString();
      }
    }

    final Job<Frame> j = new Job(Key.make(parms._predictions_name), Frame.class.getName(), "prediction");


    H2O.H2OCountedCompleter work = new H2O.H2OCountedCompleter() {
      @Override
      public void compute2() {
        if (s.deep_features_hidden_layer < 0) {
          parms._model.score(parms._frame, parms._predictions_name, j);
        } else {
          Frame predictions = ((Model.DeepFeatures) parms._model).scoreDeepFeatures(parms._frame, s.deep_features_hidden_layer, j);
          predictions = new Frame(Key.make(parms._predictions_name), predictions.names(), predictions.vecs());
          DKV.put(predictions._key, predictions);
        }
        tryComplete(); 
      }
    };
    j.start(work, parms._frame.anyVec().nChunks());
    return new JobV3().fillFromImpl(j);
  }

  /**
   * Score a frame with the given model and return the metrics AND the prediction frame.
   */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ModelMetricsListSchemaV3 predict(int version, ModelMetricsListSchemaV3 s) {
    // parameters checking:
    if (null == s.model) throw new H2OIllegalArgumentException("model", "predict", s.model);
    if (null == DKV.get(s.model.name)) throw new H2OKeyNotFoundArgumentException("model", "predict", s.model.name);

    // Aggregator doesn't need a Frame to 'predict'
    if (s.exemplar_index<0) {
      if (null == s.frame) throw new H2OIllegalArgumentException("frame", "predict", s.frame);
      if (null == DKV.get(s.frame.name)) throw new H2OKeyNotFoundArgumentException("frame", "predict", s.frame.name);
    }

    ModelMetricsList parms = s.createAndFillImpl();

    Frame predictions;
    if (!s.reconstruction_error && !s.reconstruction_error_per_feature && s.deep_features_hidden_layer < 0 &&
        !s.project_archetypes && !s.reconstruct_train && !s.leaf_node_assignment && s.exemplar_index < 0) {
      if (null == parms._predictions_name)
        parms._predictions_name = "predictions" + Key.make().toString().substring(0,5) + "_" + parms._model._key.toString() + "_on_" + parms._frame._key.toString();
      predictions = parms._model.score(parms._frame, parms._predictions_name);
    } else {
      if (Model.DeepFeatures.class.isAssignableFrom(parms._model.getClass())) {
        if (s.reconstruction_error || s.reconstruction_error_per_feature) {
          if (s.deep_features_hidden_layer >= 0)
            throw new H2OIllegalArgumentException("Can only compute either reconstruction error OR deep features.", "");
          if (null == parms._predictions_name)
            parms._predictions_name = "reconstruction_error" + Key.make().toString().substring(0,5) + "_" + parms._model._key.toString() + "_on_" + parms._frame._key.toString();
          predictions = ((Model.DeepFeatures) parms._model).scoreAutoEncoder(parms._frame, Key.make(parms._predictions_name), parms._reconstruction_error_per_feature);
        } else {
          if (s.deep_features_hidden_layer < 0)
            throw new H2OIllegalArgumentException("Deep features hidden layer index must be >= 0.", "");
          if (null == parms._predictions_name)
            parms._predictions_name = "deep_features" + Key.make().toString().substring(0,5) + "_" + parms._model._key.toString() + "_on_" + parms._frame._key.toString();
          predictions = ((Model.DeepFeatures) parms._model).scoreDeepFeatures(parms._frame, s.deep_features_hidden_layer);
        }
        predictions = new Frame(Key.make(parms._predictions_name), predictions.names(), predictions.vecs());
        DKV.put(predictions._key, predictions);
      } else if(Model.GLRMArchetypes.class.isAssignableFrom(parms._model.getClass())) {
        if(s.project_archetypes) {
          if (null == parms._predictions_name)
            parms._predictions_name = "reconstructed_archetypes_" + Key.make().toString().substring(0, 5) + "_" + parms._model._key.toString() + "_of_" + parms._frame._key.toString();
          predictions = ((Model.GLRMArchetypes) parms._model).scoreArchetypes(parms._frame, Key.make(parms._predictions_name), s.reverse_transform);
        } else {
          assert s.reconstruct_train;
          if (null == parms._predictions_name)
            parms._predictions_name = "reconstruction_" + Key.make().toString().substring(0, 5) + "_" + parms._model._key.toString() + "_of_" + parms._frame._key.toString();
          predictions = ((Model.GLRMArchetypes) parms._model).scoreReconstruction(parms._frame, Key.make(parms._predictions_name), s.reverse_transform);
        }
      } else if(s.leaf_node_assignment) {
        assert(Model.LeafNodeAssignment.class.isAssignableFrom(parms._model.getClass()));
        if (null == parms._predictions_name)
          parms._predictions_name = "leaf_node_assignment" + Key.make().toString().substring(0, 5) + "_" + parms._model._key.toString() + "_on_" + parms._frame._key.toString();
        predictions = ((Model.LeafNodeAssignment) parms._model).scoreLeafNodeAssignment(parms._frame, Key.make(parms._predictions_name));
      } else if(s.exemplar_index >= 0) {
        assert(Model.ExemplarMembers.class.isAssignableFrom(parms._model.getClass()));
        if (null == parms._predictions_name)
          parms._predictions_name = "members_" + parms._model._key.toString() + "_for_exemplar_" + parms._exemplar_index;
        predictions = ((Model.ExemplarMembers) parms._model).scoreExemplarMembers(Key.make(parms._predictions_name), parms._exemplar_index);
      }
      else throw new H2OIllegalArgumentException("Requires a Deep Learning, GLRM, DRF or GBM model.", "Model must implement specific methods.");
    }

    ModelMetricsListSchemaV3 mm = this.fetch(version, s);

    // TODO: for now only binary predictors write an MM object.
    // For the others cons one up here to return the predictions frame.
    if (null == mm)
      mm = new ModelMetricsListSchemaV3();

    mm.predictions_frame = new KeyV3.FrameKeyV3(predictions._key);
    if (parms._leaf_node_assignment) //don't show metrics in leaf node assignments are made
      mm.model_metrics = null;

    if (null == mm.model_metrics || 0 == mm.model_metrics.length) {
      // There was no response in the test set -> cannot make a model_metrics object
    } else {
      mm.model_metrics[0].predictions = new FrameV3(predictions, 0, 100); // TODO: Should call schema(version)
    }
    return mm;
  }
}
