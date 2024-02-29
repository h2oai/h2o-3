package hex.pipeline;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelCategory;
import hex.ModelMetrics;
import hex.pipeline.DataTransformer.FrameType;
import hex.pipeline.trackers.CompositeFrameTracker;
import hex.pipeline.TransformerChain.UnaryCompleter;
import hex.pipeline.trackers.ConsistentKeyTracker;
import hex.pipeline.trackers.ScopeTracker;
import org.apache.commons.lang.StringUtils;
import water.*;
import water.KeyGen.PatternKeyGen;
import water.fvec.Frame;
import water.udf.CFuncRef;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A {@link PipelineModel} encapsulates in a single model a collection of transformations applied to train/validation
 * before training a final delegate model (here called `estimator` to avoid confusion).
 * For scoring, the same transformations are first applied to the test data and the result is used by the estimator model to provide the final score.
 */
public class PipelineModel extends Model<PipelineModel, PipelineModel.PipelineParameters, PipelineModel.PipelineOutput> {

  public static final String ESTIMATOR_PARAM = "estimator";
  private static final KeyGen TRANSFORM_KEY_GEN = new PatternKeyGen("{0}_trf_by_{1}");

  public PipelineModel(Key<PipelineModel> selfKey, PipelineParameters parms, PipelineOutput output) {
    super(selfKey, parms, output);
  }

  @Override
  public boolean havePojo() {
    return false;
  }

  @Override
  public boolean haveMojo() {
    return false;
  }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    throw new UnsupportedOperationException("PipelineModel.makeMetricBuilder should never be called!");
  }

  @Override
  protected double[] score0(double[] data, double[] preds) {
    throw H2O.unimpl("Pipeline can not score on raw data");
  }

  /*
  @Override
  protected PipelinePredictScoreResult predictScoreImpl(Frame fr, Frame adaptFrm, String destination_key, Job j, boolean computeMetrics, CFuncRef customMetricFunc) {
    Frame preds = doScore(adaptFrm, destination_key, j, computeMetrics, customMetricFunc);
    ModelMetrics mm = null;
    Model finalModel = _output.getFinalModel();
    if (computeMetrics && finalModel != null) {
      // obtaining the model metrics from the final model
      Key<ModelMetrics>[] mms = finalModel._output.getModelMetrics();
      ModelMetrics lastComputedMetric = mms[mms.length - 1].get();
      mm = lastComputedMetric.deepCloneWithDifferentModelAndFrame(this, adaptFrm);
      this.addModelMetrics(mm);
      //now that we have the metric set on the pipeline model, removing the one we just computed on the delegate model (otherwise it leaks in client mode)
      for (Key<ModelMetrics> kmm : finalModel._output.clearModelMetrics(true)) {
        DKV.remove(kmm);
      }
    }
    String[] names = makeScoringNames();
    String[][] domains = makeScoringDomains(adaptFrm, computeMetrics, names);
    ModelMetrics.MetricBuilder mb = makeMetricBuilder(domains[0]);
    return new PipelinePredictScoreResult(mb, preds, mm);
  }
   */

  @Override
  public Frame score(Frame fr, String destination_key, Job j, boolean computeMetrics, CFuncRef customMetricFunc) throws IllegalArgumentException {
    return doScore(fr, destination_key, j, computeMetrics, customMetricFunc);
  }

  private Frame doScore(Frame fr, String destination_key, Job j, boolean computeMetrics, CFuncRef customMetricFunc) throws IllegalArgumentException {
    if (fr == null) return null;
    try (Scope.Safe s = Scope.safe(fr)) {
      PipelineContext context = newContext(fr, j);
      Frame result = newChain().transform(fr, FrameType.Test, context, new UnaryCompleter<Frame>() {
        @Override
        public Frame apply(Frame frame, PipelineContext context) {
          if (_output._estimator == null) {
            return new Frame(Key.make(destination_key), frame.names(), frame.vecs());
          }
          Frame result = _output._estimator.get().score(frame, destination_key, j, computeMetrics, customMetricFunc);
          if (computeMetrics) {
            ModelMetrics mm = ModelMetrics.getFromDKV(_output._estimator.get(), frame);
            if (mm != null) addModelMetrics(mm.deepCloneWithDifferentModelAndFrame(PipelineModel.this, fr));
          }
          return result;
        }
      });
      Scope.untrack(result);
      DKV.put(result);
      return result;
    }
  }

  /**
   * applies all the pipeline transformers to the input frame.
   * @param fr
   * @return the transformed frame, as it would be passed to the estimator model if fr was used for predict/scoring.
   */
  @Override
  public Frame transform(Frame fr) {
    if (fr == null) return null;
    try (Scope.Safe s = Scope.safe(fr)) {
      PipelineContext context = newContext(fr, null);
      Frame result = newChain().transform(fr, FrameType.Test, context, new UnaryCompleter<Frame>() {
        @Override
        public Frame apply(Frame frame, PipelineContext context) {
          return new Frame(TRANSFORM_KEY_GEN.make(fr.getKey(), getKey()), frame.names(), frame.vecs()); 
        }
      });
      Scope.untrack(result);
      DKV.put(result);
      return result;
    }
  }

  private TransformerChain newChain() {
    //no need to call `prepare` on this chain as we're using the output transformers, which have been prepared during training.
    return new TransformerChain(_output._transformers);
  }
  
  private PipelineContext newContext(Frame fr, Job job) {
    return new PipelineContext(
            _parms, 
            new CompositeFrameTracker(
              new ConsistentKeyTracker(fr),
              new ScopeTracker()
            ),
            job);
  }

  @Override
  protected Futures remove_impl(Futures fs, boolean cascade) {
    if (cascade) {
      if (_output._transformers != null) {
        for (DataTransformer dt : _output.getTransformers()) {
          if (dt != null) dt.cleanup(fs);
        }
      }
      if (_output._estimator != null) {
        Keyed.remove(_output._estimator, fs, cascade);
      }
    }
    return super.remove_impl(fs, cascade);
  }
  
  void syncOutput() {
    PipelineModel.PipelineOutput pmo = this._output;
    if (pmo.getEstimatorModel() == null) return;
    Model.Output mo = pmo.getEstimatorModel()._output;
    if (mo._training_metrics != null) pmo._training_metrics = addModelMetrics(mo._training_metrics.deepCloneWithDifferentModelAndFrame(this, this._parms.train()));
    if (mo._validation_metrics != null) pmo._validation_metrics = addModelMetrics(mo._validation_metrics.deepCloneWithDifferentModelAndFrame(this, this._parms.valid()));
    if (mo._cross_validation_metrics != null) pmo._cross_validation_metrics = addModelMetrics(mo._cross_validation_metrics.deepCloneWithDifferentModelAndFrame(this, this._parms.train()));
    pmo._cross_validation_metrics_summary = mo._cross_validation_metrics_summary;
    pmo._cross_validation_fold_assignment_frame_id = mo._cross_validation_fold_assignment_frame_id;
    pmo._cross_validation_holdout_predictions_frame_id = mo._cross_validation_holdout_predictions_frame_id;
    pmo._cross_validation_predictions = mo._cross_validation_predictions;
    pmo._cross_validation_models = mo._cross_validation_models; // FIXME: ideally, should be PipelineModels (build pipeline output pointing at CV model, use it for new pipeline model, etc.)
    //...???
  }

  
  /*
  public class PipelinePredictScoreResult extends PredictScoreResult {

    private final ModelMetrics _modelMetrics;
    public PipelinePredictScoreResult(ModelMetrics.MetricBuilder metricBuilder, Frame preds, ModelMetrics modelMetrics) {
      super(metricBuilder, preds, preds);
      _modelMetrics = modelMetrics;
    }

    @Override
    public ModelMetrics makeModelMetrics(Frame fr, Frame adaptFr) {
      return _modelMetrics;
    }
  }
   */


  public static class PipelineParameters extends Model.Parameters {
    
    static String ALGO = "Pipeline";
    
    // think about Grids: we should be able to slightly modify grids to set nested hyperparams, for example "_transformers[1]._my_param", "_estimator._my_param"
    // this doesn't have to work for all type of transformers, but for example for those wrapping a model (see ModelAsFeatureTransformer) and for the final estimator.
    // as soon as we can do this, then we will be able to train pipelines in grids like any other model.
    
    public Key<DataTransformer>[] _transformers;
    public Model.Parameters _estimatorParams;
    public KeyGen _estimatorKeyGen = new PatternKeyGen("{0}_estimator"); 

    @Override
    public String algoName() {
      return ALGO; 
    }

    @Override
    public String fullName() {
      return ALGO;
    }

    @Override
    public String javaName() {
      return PipelineModel.class.getName();
    }

    @Override
    public long progressUnits() {
      return 0;
    }

    @Override
    public Object getParameter(String name) {
      String[] tokens = parseParameterName(name);
      if (tokens.length > 1) {
        String tok0 = tokens[0];
        if (ESTIMATOR_PARAM.equals(tok0)) return _estimatorParams == null ? null : _estimatorParams.getParameter(tokens[1]);
        DataTransformer dt = getTransformer(tok0);
        return dt == null ? null : dt.getParameter(tokens[1]);
      }
      return super.getParameter(name);
    }

    @Override
    public void setParameter(String name, Object value) {
      String[] tokens = parseParameterName(name);
      if (tokens.length > 1) {
        String tok0 = tokens[0];
        if (ESTIMATOR_PARAM.equals(tok0)) {
          _estimatorParams.setParameter(tokens[1], value);
          return;
        }
        DataTransformer dt = getTransformer(tok0);
        if (dt != null) dt.setParameter(tokens[1], value);
        return;
      }
      super.setParameter(name, value);
    }

    @Override
    public boolean isParameterAssignable(String name) {
      String[] tokens = parseParameterName(name);
      if (tokens.length > 1) {
        String tok0 = tokens[0];
        if (ESTIMATOR_PARAM.equals(tok0)) return _estimatorParams == null ? null : _estimatorParams.isParameterAssignable(tokens[1]);
        DataTransformer dt = getTransformer(tok0);
        // for now allow transformers hyper params on non-defaults
        return dt != null && dt.hasParameter(tokens[1]); 
//        return dt != null && dt.isValidHyperParameter(tokens[1]);
      }
      return super.isParameterAssignable(name);
    }

    private static final Pattern TRANSFORMER_PAT = Pattern.compile("transformers\\[(\\w+)]");
    private String[] parseParameterName(String name) {
      String[] tokens = name.split("\\.", 2);
      if (tokens.length == 1) return tokens;
      String tok0 = StringUtils.stripStart(tokens[0], "_");
      if (ESTIMATOR_PARAM.equals(tok0) || getTransformer(tok0) != null) {
        return new String[]{tok0, tokens[1]} ;
      } else {
        Matcher m = TRANSFORMER_PAT.matcher(tok0);
        if (m.matches()) {
          String id = m.group(1);
          try {
            int idx = Integer.parseInt(id);
            assert idx >=0 && idx < _transformers.length;
            assert _transformers[idx].get() != null;
            return new String[]{_transformers[idx].get().name(), tokens[1]};
          } catch(NumberFormatException nfe) {
            if (getTransformer(id) != null) return new String[] {id, tokens[1]};
            throw new IllegalArgumentException("Unknown pipeline transformer: "+tok0);
          }
        } else {
          throw new IllegalArgumentException("Unknown pipeline parameter: "+name);
        }
      }
    }
    
    public DataTransformer[] getTransformers() {
      if (_transformers == null) return null;
      for (Key<DataTransformer> key : _transformers) {
        DKV.prefetch(key);
      }
      return Arrays.stream(_transformers).map(Key::get).toArray(DataTransformer[]::new);
    }
    
    public void setTransformers(DataTransformer... transformers) {
      if (transformers == null) {
        _transformers = null;
        return;
      }
      _transformers = Arrays.stream(transformers)
              .map(DataTransformer::init)
              .map(DataTransformer::getKey)
              .toArray(Key[]::new);
    }
    
    private DataTransformer getTransformer(String id) {
      if (_transformers == null) return null;
      return Stream.of(getTransformers()).filter(t -> t.name().equals(id)).findFirst().orElse(null);
    }

    @Override
    public PipelineParameters freshCopy() {
      PipelineParameters copy = (PipelineParameters) super.freshCopy();
      DataTransformer[] dts = getTransformers();
      copy._transformers = dts == null 
              ? null 
              : Arrays.stream(dts)
                      .map(DataTransformer::freshCopy)
                      .map(DataTransformer::getKey)
                      .toArray(Key[]::new);
      return copy;
    }
    
    @Override
    protected Parameters cloneImpl() throws CloneNotSupportedException {
      PipelineParameters clone = (PipelineParameters) super.cloneImpl();
      clone._transformers = _transformers == null ? null : _transformers.clone();
      clone._estimatorParams = _estimatorParams == null ? null : _estimatorParams.clone();
      return clone;
    }

    @Override
    public long checksum(Set<String> ignoredFields) {
      Set<String> ignored = ignoredFields == null ? new HashSet<>() : new HashSet<>(ignoredFields);
      ignored.add("_estimatorKeyGen");
      long xs = super.checksum(ignored);
      return xs;
    }
  }
  
  public static class PipelineOutput extends Model.Output {
    
    public Key<DataTransformer>[] _transformers;
    public Key<Model> _estimator;

    public PipelineOutput(ModelBuilder b) {
      super(b);
    }
    
    public DataTransformer[] getTransformers() {
      if (_transformers == null) return null;
      for (Key<DataTransformer> key : _transformers) {
        DKV.prefetch(key);
      }
      return Arrays.stream(_transformers).map(Key::get).toArray(DataTransformer[]::new);
    }
    
    public Model getEstimatorModel() {
      return _estimator == null ? null : _estimator.get();
    }

    @Override
    public ModelCategory getModelCategory() {
      Model em = getEstimatorModel();
      return em == null ? super.getModelCategory() : em._output.getModelCategory();
    }
  }
  
}
