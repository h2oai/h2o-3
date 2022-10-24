package hex.pipeline;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelMetrics;
import hex.pipeline.DataTransformer.FrameType;
import hex.pipeline.PipelineContext.CompositeFrameTracker;
import hex.pipeline.TransformerChain.UnaryCompleter;
import water.*;
import water.fvec.Frame;
import water.udf.CFuncRef;
import water.util.ReflectionUtils;

import java.util.stream.Stream;

public class PipelineModel extends Model<PipelineModel, PipelineModel.PipelineParameters, PipelineModel.PipelineOutput> {


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
//    assert _output.getFinalModel() != null;
//    return _output.getFinalModel().makeMetricBuilder(domain);
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
      PipelineContext context = newContext(fr);
      Frame result = newChain().transform(fr, FrameType.Scoring, context, new UnaryCompleter<Frame>() {
        @Override
        public Frame apply(Frame frame, PipelineContext context) {
          if (_output._model == null) {
            return new Frame(Key.make(destination_key), frame.names(), frame.vecs());
          }
          Frame result = _output._model.get().score(frame, destination_key, j, computeMetrics, customMetricFunc);
          if (computeMetrics) {
            ModelMetrics mm = ModelMetrics.getFromDKV(_output._model.get(), frame);
            if (mm != null) addModelMetrics(mm.deepCloneWithDifferentModelAndFrame(PipelineModel.this, fr));
          }
          return result;
        }
      });
      Scope.untrack(result);
      return result;
    }
  }
  
  private TransformerChain newChain() {
    //no need to call `prepare` on this chain as we're using the output transformers, which have been prepared during training.
    return new TransformerChain(_output._transformers);
  }
  
  private PipelineContext newContext(Frame fr) {
    return new PipelineContext(_parms, new CompositeFrameTracker(
            new PipelineContext.ConsistentKeyTracker(fr),
            new PipelineContext.ScopeTracker()
    ));
  }

  @Override
  protected Futures remove_impl(Futures fs, boolean cascade) {
    if (cascade) {
      if (_output._transformers != null) {
        for (DataTransformer dt : _output._transformers) {
          dt.cleanup(fs);
        }
      }
      if (_output._model != null) {
        Keyed.remove(_output._model, fs, cascade);
      }
    }
    return super.remove_impl(fs, cascade);
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
    
    public DataTransformer[] _transformers;
    public Model.Parameters _estimatorParams;
    public Key<Model> _estimatorResult;

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
  }
  
  public static class PipelineOutput extends Model.Output {
    
    DataTransformer[] _transformers;
    Key<Model> _model;

    public PipelineOutput(ModelBuilder b) {
      super(b);
    }
    
    public DataTransformer[] getTransformers() {
      return _transformers == null ? null : _transformers.clone();
    }
    
    public Model getModel() {
      return _model == null ? null : _model.get();
    }
    
    void sync() {
      Model m = getModel();
      if (m == null) return;
      _training_metrics = m._output._training_metrics;
      _validation_metrics = m._output._validation_metrics;
      _cross_validation_metrics = m._output._cross_validation_metrics;
      _cross_validation_metrics_summary = m._output._cross_validation_metrics_summary;
      _cross_validation_fold_assignment_frame_id = m._output._cross_validation_fold_assignment_frame_id;
      _cross_validation_holdout_predictions_frame_id = m._output._cross_validation_holdout_predictions_frame_id;
      _cross_validation_predictions = m._output._cross_validation_predictions;
      _cross_validation_models = m._output._cross_validation_models; // FIXME: ideally, should be PipelineModels (build pipeline output pointing at Cv model, use it for new pipeline model, etc.)
      //...???
    }

  }
  
}
