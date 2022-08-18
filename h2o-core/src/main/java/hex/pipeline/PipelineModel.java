package hex.pipeline;

import hex.Model;
import hex.ModelMetrics;
import hex.pipeline.DataTransformer.FrameType;
import hex.pipeline.TransformerChain.UnaryCompleter;
import water.H2O;
import water.Job;
import water.Key;
import water.fvec.Frame;
import water.udf.CFuncRef;

public class PipelineModel extends Model<PipelineModel, PipelineModel.PipelineParameters, PipelineModel.PipelineOutput> {


  public PipelineModel(Key<PipelineModel> selfKey, PipelineParameters parms, PipelineOutput output) {
    super(selfKey, parms, output);
  }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    return null;
  }

  @Override
  protected double[] score0(double[] data, double[] preds) {
    throw H2O.unimpl("Pipeline can not score on raw data");
  }
  
  @Override
  public Frame score(Frame fr, String destination_key, Job j, boolean computeMetrics, CFuncRef customMetricFunc) throws IllegalArgumentException {
    if (fr == null) return null;
    PipelineContext context = new PipelineContext(_parms);
    return new TransformerChain(_output._transformers).transform(fr, FrameType.Scoring, context, new UnaryCompleter<Frame>() {
      @Override
      public Frame apply(Frame frame, PipelineContext context) {
        if (_output._estimator == null) {
          return new Frame(Key.make(destination_key), frame.names(), frame.vecs());
        }
        return _output._estimator.get().score(frame, destination_key, j, computeMetrics, customMetricFunc);
      }
    });
  }

  public static class PipelineParameters extends Model.Parameters {
    
    // think about Grids: we should be able to slightly modify grids to set nested hyperparams, for example "_transformers[1]._my_param", "_estimator._my_param"
    // this doesn't have to work for all type of transformers, but for example for those wrapping a model (see ModelAsFeatureTransformer) and for the final estimator.
    // as soon as we can do this, then we will be able to train pipelines in grids like any other model.
    
    DataTransformer[] _transformers;
    Model.Parameters _estimator;

    @Override
    public String algoName() {
      return "Pipeline";
    }

    @Override
    public String fullName() {
      return "Pipeline";
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
    Key<Model> _estimator;
    
  }
  
}
