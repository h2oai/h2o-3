package water.api;

import hex.*;
import hex.genmodel.utils.DistributionFamily;
import org.apache.commons.lang.ArrayUtils;
import water.*;
import water.api.schemas3.*;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OKeyNotFoundArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.udf.CFuncRef;
import water.util.Log;

class ModelMetricsHandler extends Handler {
  /** Class which contains the internal representation of the ModelMetrics list and params. */
  public static final class ModelMetricsList extends Iced {
    public Model _model;
    public Frame _frame;
    public ModelMetrics[] _model_metrics;
    public String _predictions_name;
    public String _deviances_name;
    public boolean _deviances;
    public boolean _reconstruction_error;
    public boolean _reconstruction_error_per_feature;
    public int _deep_features_hidden_layer = -1;
    public String _deep_features_hidden_layer_name = null;
    public boolean _reconstruct_train;
    public boolean _project_archetypes;
    public boolean _reverse_transform;
    public boolean _leaf_node_assignment;
    public int _exemplar_index = -1;
    public String _custom_metric_func;
    public String _auc_type;
    public int _top_n;
    public int _bottom_n;
    public boolean _compare_abs;

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


    protected ModelMetricsListSchemaV3 schema(int version) {
      switch (version) {
      case 3:   return new ModelMetricsListSchemaV3();
      default:  throw H2O.fail("Bad version for ModelMetrics schema: " + version);
      }
    }
  } // class ModelMetricsList

  /** Schema for a list of ModelMetricsBaseV3.
   *  This should be common across all versions of ModelMetrics schemas, so it lives here.
   *  TODO: move to water.api.schemas3
   *  */
  public static final class ModelMetricsListSchemaV3 extends RequestSchemaV3<ModelMetricsList, ModelMetricsListSchemaV3> {
    // Input fields
    @API(help = "Key of Model of interest (optional)")
    public KeyV3.ModelKeyV3<Model> model;

    @API(help = "Key of Frame of interest (optional)")
    public KeyV3.FrameKeyV3 frame;

    @API(help = "Key of predictions frame, if predictions are requested (optional)", direction = API.Direction.INOUT)
    public KeyV3.FrameKeyV3 predictions_frame;

    @API(help = "Key for the frame containing per-observation deviances (optional)", direction = API.Direction.INOUT)
    public KeyV3.FrameKeyV3 deviances_frame;

    @API(help = "Compute reconstruction error (optional, only for Deep Learning AutoEncoder models)", json = false)
    public boolean reconstruction_error;

    @API(help = "Compute reconstruction error per feature (optional, only for Deep Learning AutoEncoder models)", json = false)
    public boolean reconstruction_error_per_feature;

    @API(help = "Extract Deep Features for given hidden layer (optional, only for Deep Learning models)", json = false)
    public int deep_features_hidden_layer;

    @API(help = "Extract Deep Features for given hidden layer by name (optional, only for Deep Water models)", json = false)
    public String deep_features_hidden_layer_name;

    @API(help = "Reconstruct original training frame (optional, only for GLRM models)", json = false)
    public boolean reconstruct_train;

    @API(help = "Project GLRM archetypes back into original feature space (optional, only for GLRM models)", json = false)
    public boolean project_archetypes;

    @API(help = "Reverse transformation applied during training to model output (optional, only for GLRM models)", json = false)
    public boolean reverse_transform;

    @API(help = "Return the leaf node assignment (optional, only for DRF/GBM models)", json = false)
    public boolean leaf_node_assignment;

    @API(help = "Type of the leaf node assignment (optional, only for DRF/GBM models)", values = {"Path", "Node_ID"}, json = false)
    public Model.LeafNodeAssignment.LeafNodeAssignmentType leaf_node_assignment_type;

    @API(help = "Predict the class probabilities at each stage (optional, only for GBM models)", json = false)
    public boolean predict_staged_proba;

    @API(help = "Predict the feature contributions - Shapley values (optional, only for DRF, GBM and XGBoost models)", json = false)
    public boolean predict_contributions;

    @API(help = "Specify how to output feature contributions in XGBoost - XGBoost by default outputs contributions for 1-hot encoded features, " +
            "specifying a Compact output format will produce a per-feature contribution", values = {"Original", "Compact"}, json = false)
    public Model.Contributions.ContributionsOutputFormat predict_contributions_output_format;

    @API(help = "Only for predict_contributions function - sort Shapley values and return top_n highest (optional)", json = false)
    public int top_n;

    @API(help = "Only for predict_contributions function - sort Shapley values and return bottom_n lowest (optional)", json = false)
    public int bottom_n;

    @API(help = "Only for predict_contributions function - sort absolute Shapley values (optional)", json = false)
    public boolean compare_abs;

    @API(help = "Retrieve the feature frequencies on paths in trees in tree-based models (optional, only for GBM, DRF and Isolation Forest)", json = false)
    public boolean feature_frequencies;

    @API(help = "Retrieve all members for a given exemplar (optional, only for Aggregator models)", json = false)
    public int exemplar_index;

    @API(help = "Compute the deviances per row (optional, only for classification or regression models)", json = false)
    public boolean deviances;

    @API(help = "Reference to custom evaluation function, format: `language:keyName=funcName`", json=false)
    public String custom_metric_func;

    @API(help = "Set default multinomial AUC type. Must be one of: \"AUTO\", \"NONE\", \"MACRO_OVR\", \"WEIGHTED_OVR\", \"MACRO_OVO\", \"WEIGHTED_OVO\". Default is \"NONE\" (optional, only for multinomial classification).", json=false, direction = API.Direction.INPUT)
    public String auc_type;

    // Output fields
    @API(help = "ModelMetrics", direction = API.Direction.OUTPUT)
    public ModelMetricsBaseV3[] model_metrics;

    @Override public ModelMetricsHandler.ModelMetricsList fillImpl(ModelMetricsList mml) {
      // TODO: check for type!
      mml._model = (this.model == null || this.model.key() == null ? null : this.model.key().get());
      mml._frame = (this.frame == null || this.frame.key() == null ? null : this.frame.key().get());
      mml._predictions_name = (null == this.predictions_frame || null == this.predictions_frame.key() ? null : this.predictions_frame.key().toString());
      mml._reconstruction_error = this.reconstruction_error;
      mml._reconstruction_error_per_feature = this.reconstruction_error_per_feature;
      mml._deep_features_hidden_layer = this.deep_features_hidden_layer;
      mml._deep_features_hidden_layer_name = this.deep_features_hidden_layer_name;
      mml._reconstruct_train = this.reconstruct_train;
      mml._project_archetypes = this.project_archetypes;
      mml._reverse_transform = this.reverse_transform;
      mml._leaf_node_assignment = this.leaf_node_assignment;
      mml._exemplar_index = this.exemplar_index;
      mml._deviances = this.deviances;
      mml._auc_type = this.auc_type;
      mml._top_n = this.top_n;
      mml._bottom_n = this.bottom_n;
      mml._compare_abs = this.compare_abs;

      if (model_metrics != null) {
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
      this.deviances_frame = (mml._deviances_name == null ? null : new KeyV3.FrameKeyV3(Key.<Frame>make(mml._deviances_name)));
      this.reconstruction_error = mml._reconstruction_error;
      this.reconstruction_error_per_feature = mml._reconstruction_error_per_feature;
      this.deep_features_hidden_layer = mml._deep_features_hidden_layer;
      this.deep_features_hidden_layer_name = mml._deep_features_hidden_layer_name;
      this.reconstruct_train = mml._reconstruct_train;
      this.project_archetypes = mml._project_archetypes;
      this.reverse_transform = mml._reverse_transform;
      this.leaf_node_assignment = mml._leaf_node_assignment;
      this.exemplar_index = mml._exemplar_index;
      this.deviances = mml._deviances;
      this.auc_type = mml._auc_type;
      this.top_n = mml._top_n;
      this.bottom_n = mml._bottom_n;
      this.compare_abs = mml._compare_abs;

      if (null != mml._model_metrics) {
        this.model_metrics = new ModelMetricsBaseV3[mml._model_metrics.length];
        for( int i=0; i<model_metrics.length; i++ ) {
          ModelMetrics mm = mml._model_metrics[i];
          this.model_metrics[i] = (ModelMetricsBaseV3) SchemaServer.schema(3, mm.getClass()).fillFromImpl(mm);
        }
      } else {
        this.model_metrics = new ModelMetricsBaseV3[0];
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

    String customMetricFunc = s.custom_metric_func;
    if (customMetricFunc == null) {
      customMetricFunc = parms._model._parms._custom_metric_func;
    }
    // set user given auc type, used for scoring a testing data fe. from h2o.performance function
    MultinomialAucType at = parms._model._parms._auc_type;
    if(s.auc_type != null) {
      parms._model._parms._auc_type = MultinomialAucType.valueOf(s.auc_type.toUpperCase());
    }
    parms._model.score(parms._frame, parms._predictions_name, null, true, CFuncRef.from(customMetricFunc)).remove(); // throw away predictions, keep metrics as a side-effect
    ModelMetricsListSchemaV3 mm = this.fetch(version, s);

    // TODO: for now only binary predictors write an MM object.
    // For the others cons one up here to return the predictions frame.
    if (null == mm)
      mm = new ModelMetricsListSchemaV3();

    if (null == mm.model_metrics || 0 == mm.model_metrics.length) {
      Log.warn("Score() did not return a ModelMetrics for model: " + s.model + " on frame: " + s.frame);
    }
    // set original auc type back
    parms._model._parms._auc_type = at;
    return mm;
  }

  public static final class ModelMetricsMaker extends Iced {
    public String _predictions_frame;
    public String _actuals_frame;
    public String[] _domain;
    public DistributionFamily _distribution;
    public MultinomialAucType _auc_type;
    public ModelMetrics _model_metrics;
  }

  public static final class ModelMetricsMakerSchemaV3 extends SchemaV3<ModelMetricsMaker, ModelMetricsMakerSchemaV3> {
    @API(help="Predictions Frame.", direction=API.Direction.INOUT)
    public String predictions_frame;

    @API(help="Actuals Frame.", direction=API.Direction.INOUT)
    public String actuals_frame;

    @API(help="Weights Frame.", direction=API.Direction.INOUT)
    public String weights_frame;

    @API(help="Treatment Frame.", direction=API.Direction.INOUT)
    public String treatment_frame;

    @API(help="Domain (for classification).", direction=API.Direction.INOUT)
    public String[] domain;

    @API(help="Distribution (for regression).", direction=API.Direction.INOUT, values = { "gaussian", "poisson", "gamma", "laplace" })
    public DistributionFamily distribution;
    
    @API(help = "Default AUC type (for multinomial classification).", 
            valuesProvider = ModelParamsValuesProviders.MultinomialAucTypeSchemeValuesProvider.class,
            level = API.Level.secondary, direction = API.Direction.INOUT, gridable = true)
    public MultinomialAucType auc_type;


    @API(help="Model Metrics.", direction=API.Direction.OUTPUT)
    public ModelMetricsBaseV3 model_metrics;
  }

  /**
   * Make a model metrics object from actual and predicted values
   */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ModelMetricsMakerSchemaV3 make(int version, ModelMetricsMakerSchemaV3 s) {
    // parameters checking:
    if (null == s.predictions_frame) throw new H2OIllegalArgumentException("predictions_frame", "make", s.predictions_frame);
    Frame pred = DKV.getGet(s.predictions_frame);
    if (null == pred) throw new H2OKeyNotFoundArgumentException("predictions_frame", "make", s.predictions_frame);

    if (null == s.actuals_frame) throw new H2OIllegalArgumentException("actuals_frame", "make", s.actuals_frame);
    Frame act = DKV.getGet(s.actuals_frame);
    if (null == act) throw new H2OKeyNotFoundArgumentException("actuals_frame", "make", s.actuals_frame);

    Vec weights = null;
    if (null != s.weights_frame) {
      Frame weightsFrame = DKV.getGet(s.weights_frame);
      if (null == weightsFrame) throw new H2OKeyNotFoundArgumentException("weights_frame", "make", s.weights_frame);
      weights = weightsFrame.anyVec();
    }
    
    Vec treatment = null;
    if(null != s.treatment_frame){
      Frame treatmentFrame = DKV.getGet(s.treatment_frame);
      if (null == treatmentFrame) throw new H2OKeyNotFoundArgumentException("treatment_frame", "make", s.treatment_frame);
      treatment = treatmentFrame.anyVec();
    }

    if (s.domain ==null) {
      if (pred.numCols()!=1) {
        throw new H2OIllegalArgumentException("predictions_frame", "make", "For regression problems (domain=null), the predictions_frame must have exactly 1 column.");
      }
      ModelMetricsRegression mm = ModelMetricsRegression.make(pred.anyVec(), act.anyVec(), weights, s.distribution);
      s.model_metrics = new ModelMetricsRegressionV3().fillFromImpl(mm);
    } else if (s.domain.length==2) {
      if (pred.numCols()!=1) {
        throw new H2OIllegalArgumentException("predictions_frame", "make", "For domains with 2 class labels, the predictions_frame must have exactly one column containing the class-1 probabilities.");
      }
      if(treatment != null){
        ModelMetricsBinomialUplift mm = ModelMetricsBinomialUplift.make(pred.anyVec(), act.anyVec(), treatment, s.domain);
        s.model_metrics = new ModelMetricsBinomialUpliftV3().fillFromImpl(mm);
      }
      ModelMetricsBinomial mm = ModelMetricsBinomial.make(pred.anyVec(), act.anyVec(), weights, s.domain);
      s.model_metrics = new ModelMetricsBinomialV3().fillFromImpl(mm);
    } else if (s.domain.length>2){
      if (pred.numCols()!=s.domain.length) {
        throw new H2OIllegalArgumentException("predictions_frame", "make", "For domains with " + s.domain.length + " class labels, the predictions_frame must have exactly " + s.domain.length + " columns containing the class-probabilities.");
      }

      if (s.distribution == DistributionFamily.ordinal) {
        ModelMetricsOrdinal mm = ModelMetricsOrdinal.make(pred, act.anyVec(), s.domain);
        s.model_metrics = new ModelMetricsOrdinalV3().fillFromImpl(mm);
      } else {
        ModelMetricsMultinomial mm = ModelMetricsMultinomial.make(pred, act.anyVec(), weights, s.domain, s.auc_type);
        s.model_metrics = new ModelMetricsMultinomialV3().fillFromImpl(mm);
      }
    } else {
      throw H2O.unimpl();
    }
    return s;
  }

  /**
   * Score a frame with the given model and return a Job that output a frame with predictions.
   * Do *not* calculate ModelMetrics.
   */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public JobV3 predictAsync(int version, final ModelMetricsListSchemaV3 s) {
    // parameters checking:
    if (null == s.model) throw new H2OIllegalArgumentException("model", "predict", s.model);
    if (null == DKV.get(s.model.name)) throw new H2OKeyNotFoundArgumentException("model", "predict", s.model.name);

    if (null == s.frame) throw new H2OIllegalArgumentException("frame", "predict", s.frame);
    if (null == DKV.get(s.frame.name)) throw new H2OKeyNotFoundArgumentException("frame", "predict", s.frame.name);

    if (s.deviances || null != s.deviances_frame) 
      throw new H2OIllegalArgumentException("deviances", "not supported for async", s.deviances_frame);

    final ModelMetricsList parms = s.createAndFillImpl();
    
    long workAmount = parms._frame.anyVec().nChunks();
    if (s.predict_contributions) {
      workAmount = parms._frame.anyVec().length();
      if (null == parms._predictions_name)
        parms._predictions_name = "contributions_" + Key.make().toString().substring(0, 5) + "_" + parms._model._key.toString() + "_on_" + parms._frame._key.toString();
    } else if (s.deep_features_hidden_layer > 0 || s.deep_features_hidden_layer_name != null) {
      if (null == parms._predictions_name)
        parms._predictions_name = "deep_features" + Key.make().toString().substring(0, 5) + "_" +
                parms._model._key.toString() + "_on_" + parms._frame._key.toString();
    } else if (null == parms._predictions_name) {
      parms._predictions_name = "transformation" + Key.make().toString().substring(0, 5) + "_" + parms._model._key.toString() + "_on_" + parms._frame._key.toString();
    }

    final Job<Frame> j = new Job<>(Key.make(parms._predictions_name), Frame.class.getName(), "transformation");

    H2O.H2OCountedCompleter work = new H2O.H2OCountedCompleter() {
      @Override
      public void compute2() {
        if (s.predict_contributions) {
          if (! (parms._model instanceof Model.Contributions)) {
            throw new H2OIllegalArgumentException("Model type " + parms._model._parms.algoName() + " doesn't support calculating Feature Contributions.");
          }
          Model.Contributions mc = (Model.Contributions) parms._model;
          Model.Contributions.ContributionsOutputFormat outputFormat = null == s.predict_contributions_output_format ?
                  Model.Contributions.ContributionsOutputFormat.Original : s.predict_contributions_output_format;
          Model.Contributions.ContributionsOptions options = new Model.Contributions.ContributionsOptions();
          options.setOutputFormat(outputFormat)
                  .setTopN(parms._top_n)
                  .setBottomN(parms._bottom_n)
                  .setCompareAbs(parms._compare_abs);
          mc.scoreContributions(parms._frame, Key.make(parms._predictions_name), j, options);
        } else if (s.deep_features_hidden_layer < 0 && s.deep_features_hidden_layer_name == null) {
          parms._model.score(parms._frame, parms._predictions_name, j, false, CFuncRef.from(s.custom_metric_func));
        } else if (s.deep_features_hidden_layer_name != null){
          Frame predictions;
          try {
            predictions = ((Model.DeepFeatures) parms._model).scoreDeepFeatures(parms._frame, s.deep_features_hidden_layer_name, j);
          } catch(IllegalArgumentException e) {
            Log.warn(e.getMessage());
            throw e;
          }
          if (predictions!=null) {
            predictions = new Frame(Key.make(parms._predictions_name), predictions.names(), predictions.vecs());
            DKV.put(predictions._key, predictions);
          }
        } else {
          Frame predictions = ((Model.DeepFeatures) parms._model).scoreDeepFeatures(parms._frame, s.deep_features_hidden_layer, j);
          predictions = new Frame(Key.make(parms._predictions_name), predictions.names(), predictions.vecs());
          DKV.put(predictions._key, predictions);
        }
        if ((parms._model._warningsP != null) && (parms._model._warningsP.length > 0)) { // add prediction warning here only
          String[] allWarnings = (String[]) ArrayUtils.addAll(j.warns(), parms._model._warningsP); // copy both over
          j.setWarnings(allWarnings);
        }
        tryComplete();
      }
    };
    j.start(work, workAmount);
    return new JobV3().fillFromImpl(j);
  }

  /**
   * Score a frame with the given model and return the metrics AND the prediction frame.
   */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ModelMetricsListSchemaV3 predict(int version, ModelMetricsListSchemaV3 s) {
    // parameters checking:
    if (s.model == null) throw new H2OIllegalArgumentException("model", "predict", null);
    if (DKV.get(s.model.name) == null) throw new H2OKeyNotFoundArgumentException("model", "predict", s.model.name);

    // Aggregator doesn't need a Frame to 'predict'
    if (s.exemplar_index < 0) {
      if (s.frame == null) throw new H2OIllegalArgumentException("frame", "predict", null);
      if (DKV.get(s.frame.name) == null) throw new H2OKeyNotFoundArgumentException("frame", "predict", s.frame.name);
    }

    ModelMetricsList parms = s.createAndFillImpl();

    Frame predictions;
    Frame deviances = null;
    if (!s.reconstruction_error && !s.reconstruction_error_per_feature && s.deep_features_hidden_layer < 0 &&
        !s.project_archetypes && !s.reconstruct_train && !s.leaf_node_assignment && !s.predict_staged_proba && !s.predict_contributions && !s.feature_frequencies && s.exemplar_index < 0) {
      if (null == parms._predictions_name)
        parms._predictions_name = "predictions" + Key.make().toString().substring(0,5) + "_" + parms._model._key.toString() + "_on_" + parms._frame._key.toString();
      String customMetricFunc = s.custom_metric_func;
      if (customMetricFunc == null) {
        customMetricFunc = parms._model._parms._custom_metric_func;
      }
      predictions = parms._model.score(parms._frame, parms._predictions_name, null, true, CFuncRef.from(customMetricFunc));
      if (s.deviances) {
        if (!parms._model.isSupervised())
          throw new H2OIllegalArgumentException("Deviances can only be computed for supervised models.");
        if (null == parms._deviances_name)
          parms._deviances_name = "deviances" + Key.make().toString().substring(0, 5) + "_" + parms._model._key.toString() + "_on_" + parms._frame._key.toString();
        deviances = parms._model.computeDeviances(parms._frame, predictions, parms._deviances_name);
      }
    } else {
      if (s.deviances)
        throw new H2OIllegalArgumentException("Cannot compute deviances in combination with other special predictions.");
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
        predictions = new Frame(Key.<Frame>make(parms._predictions_name), predictions.names(), predictions.vecs());
        DKV.put(predictions._key, predictions);
      } else if(Model.GLRMArchetypes.class.isAssignableFrom(parms._model.getClass())) {
        if(s.project_archetypes) {
          if (parms._predictions_name == null)
            parms._predictions_name = "reconstructed_archetypes_" + Key.make().toString().substring(0, 5) + "_" + parms._model._key.toString() + "_of_" + parms._frame._key.toString();
          predictions = ((Model.GLRMArchetypes) parms._model).scoreArchetypes(parms._frame, Key.<Frame>make(parms._predictions_name), s.reverse_transform);
        } else {
          assert s.reconstruct_train;
          if (parms._predictions_name == null)
            parms._predictions_name = "reconstruction_" + Key.make().toString().substring(0, 5) + "_" + parms._model._key.toString() + "_of_" + parms._frame._key.toString();
          predictions = ((Model.GLRMArchetypes) parms._model).scoreReconstruction(parms._frame, Key.<Frame>make(parms._predictions_name), s.reverse_transform);
        }
      } else if(s.leaf_node_assignment) {
        assert(Model.LeafNodeAssignment.class.isAssignableFrom(parms._model.getClass()));
        if (null == parms._predictions_name)
          parms._predictions_name = "leaf_node_assignment" + Key.make().toString().substring(0, 5) + "_" + parms._model._key.toString() + "_on_" + parms._frame._key.toString();
        Model.LeafNodeAssignment.LeafNodeAssignmentType type = null == s.leaf_node_assignment_type ? Model.LeafNodeAssignment.LeafNodeAssignmentType.Path : s.leaf_node_assignment_type;
        predictions = ((Model.LeafNodeAssignment) parms._model).scoreLeafNodeAssignment(parms._frame, type, Key.<Frame>make(parms._predictions_name));
      } else if(s.feature_frequencies) {
        assert(Model.FeatureFrequencies.class.isAssignableFrom(parms._model.getClass()));
        if (null == parms._predictions_name)
          parms._predictions_name = "feature_frequencies" + Key.make().toString().substring(0, 5) + "_" + parms._model._key.toString() + "_on_" + parms._frame._key.toString();
        predictions = ((Model.FeatureFrequencies) parms._model).scoreFeatureFrequencies(parms._frame, Key.<Frame>make(parms._predictions_name));
      } else if(s.predict_staged_proba) {
        if (! (parms._model instanceof Model.StagedPredictions)) {
          throw new H2OIllegalArgumentException("Model type " + parms._model._parms.algoName() + " doesn't support Staged Predictions.");
        }
        if (null == parms._predictions_name)
          parms._predictions_name = "staged_proba_" + Key.make().toString().substring(0, 5) + "_" + parms._model._key.toString() + "_on_" + parms._frame._key.toString();
        predictions = ((Model.StagedPredictions) parms._model).scoreStagedPredictions(parms._frame, Key.<Frame>make(parms._predictions_name));
      } else if(s.predict_contributions) {
        if (! (parms._model instanceof Model.Contributions)) {
          throw new H2OIllegalArgumentException("Model type " + parms._model._parms.algoName() + " doesn't support calculating Feature Contributions.");
        }
        Model.Contributions mc = (Model.Contributions) parms._model;
        if (null == parms._predictions_name)
          parms._predictions_name = "contributions_" + Key.make().toString().substring(0, 5) + "_" + parms._model._key.toString() + "_on_" + parms._frame._key.toString();
        Model.Contributions.ContributionsOutputFormat outputFormat = null == s.predict_contributions_output_format ? 
                Model.Contributions.ContributionsOutputFormat.Original : s.predict_contributions_output_format;
        Model.Contributions.ContributionsOptions options = new Model.Contributions.ContributionsOptions().setOutputFormat(outputFormat);
        predictions = mc.scoreContributions(parms._frame, Key.make(parms._predictions_name), null, options);
      } else if(s.exemplar_index >= 0) {
        assert(Model.ExemplarMembers.class.isAssignableFrom(parms._model.getClass()));
        if (null == parms._predictions_name)
          parms._predictions_name = "members_" + parms._model._key.toString() + "_for_exemplar_" + parms._exemplar_index;
        predictions = ((Model.ExemplarMembers) parms._model).scoreExemplarMembers(Key.<Frame>make(parms._predictions_name), parms._exemplar_index);
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
    if (deviances !=null)
      mm.deviances_frame = new KeyV3.FrameKeyV3(deviances._key);

    if (null == mm.model_metrics || 0 == mm.model_metrics.length) {
      // There was no response in the test set -> cannot make a model_metrics object
    } else {
      mm.model_metrics[0].predictions = new FrameV3(predictions, 0, 100); // TODO: Should call schema(version)
    }
    return mm;
  }
}
