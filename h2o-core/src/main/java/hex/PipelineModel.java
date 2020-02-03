package hex;

import water.*;
import water.fvec.Frame;
import water.udf.CFuncRef;

import java.util.List;

public class PipelineModel extends Model<PipelineModel, PipelineModel.PipelineModelParameters, PipelineModel.PipelineModelOutput> {

  protected static final String ALGO_NAME = "ModelPipeline";

  private final Key<Model>[] _preprocessingModels;
  private final Model _scoringModel;

  public PipelineModel(Key<PipelineModel> selfKey,
                       PipelineModelParameters parms,
                       PipelineModelOutput output,
                       Model scoringModel,
                       List<Key<Model>> preprocessingModels) {
    super(selfKey, parms, output);
    _scoringModel = scoringModel;
    _preprocessingModels = preprocessingModels.toArray(new Key[0]);
  }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    switch(_output.getModelCategory()) {
      case Binomial:
        return new ModelMetricsBinomial.MetricBuilderBinomial(domain);
      case Multinomial:
        return new ModelMetricsMultinomial.MetricBuilderMultinomial(_output.nclasses(), domain);
      case Ordinal:
        return new ModelMetricsOrdinal.MetricBuilderOrdinal(_output.nclasses(), domain);
      case Regression:
        return new ModelMetricsRegression.MetricBuilderRegression();
      default:
      case Unknown:
        throw new IllegalStateException("Model category is unknown");
    }
  }

  public static class PipelineModelParameters extends Model.Parameters {

    @Override
    public String algoName() {
      return ALGO_NAME;
    }

    @Override
    public String fullName() {
      return "ModelPipeline";
    }

    @Override
    public String javaName() {
      return PipelineModel.class.getName();
    }

    @Override
    public long progressUnits() {
      return 1;
    }

  }

  public static class PipelineModelOutput extends Model.Output {

    public PipelineModelParameters _parms;
    public Model _scoringModel;

    public PipelineModelOutput(PipelineModelBuilder b, Model scoringModel) {
      super(b);
      _parms = b._parms;
      _scoringModel = scoringModel;
      _cross_validation_metrics = _scoringModel._output._cross_validation_metrics;
    }

    @Override public ModelCategory getModelCategory() {
      return _scoringModel._output.getModelCategory();
    }
  }

  @Override
  protected long checksum_impl() {
    return _scoringModel.checksum_impl();
  }

  @Override
  protected double[] score0(double data[], double preds[]){
    throw new UnsupportedOperationException("TargetEncoderModel doesn't support scoring on raw data. Use score() instead.");
  }

  @Override
  public Frame score(Frame fr, String destination_key, Job j, boolean computeMetrics, CFuncRef customMetricFunc) throws IllegalArgumentException {

    Frame preprocessedFrame = fr;

    for(Key<Model> preprocessingModelKey :_preprocessingModels) {
      Model preprocessingModel = DKV.getGet(preprocessingModelKey);
      preprocessedFrame = preprocessingModel.score(fr);
    }
    Frame frameWithScores = _scoringModel.score(preprocessedFrame);
    ModelMetrics training_metrics = _scoringModel._output._training_metrics;
    ModelMetrics cv_metrics = _scoringModel._output._cross_validation_metrics;
    return frameWithScores;
  }
  

  @Override
  public ModelMojoWriter getMojo() { // return specific type
    throw  new UnsupportedOperationException("Unsupported yet");
  }

  @Override
  protected Futures remove_impl(Futures fs, boolean cascade) {
//    TargetEncoderFrameHelper.encodingMapCleanUp(_output._target_encoding_map);
    return super.remove_impl(fs, cascade);
  }
}
