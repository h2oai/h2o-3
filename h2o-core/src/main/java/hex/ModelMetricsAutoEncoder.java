package hex;

import hex.genmodel.algos.deeplearning.DeeplearningMojoModel;
import water.H2O;
import water.fvec.Frame;

public class ModelMetricsAutoEncoder extends ModelMetricsUnsupervised {
  public ModelMetricsAutoEncoder(Model model, Frame frame, CustomMetric customMetric) {
    super(model, frame, 0, Double.NaN, customMetric);
  }
  public ModelMetricsAutoEncoder(Model model, Frame frame, long nobs, double mse, CustomMetric customMetric) {
    super(model, frame, nobs, mse, customMetric);
  }

  public static class MetricBuilderAutoEncoder extends MetricBuilderUnsupervised<MetricBuilderAutoEncoder> {
    public MetricBuilderAutoEncoder(int dims) {
      _work = new double[dims];
    }

    @Override public double[] perRow(double ds[], float yact[], Model m) {
      throw H2O.unimpl();
    }

    // Having computed a MetricBuilder, this method fills in a ModelMetrics
    public ModelMetrics makeModelMetrics(Model m, Frame f) {
      return m.addModelMetrics(new ModelMetricsAutoEncoder(m, f, _customMetric));
    }
  }

  public static class IndependentAutoEncoderMetricBuilder
          extends IndependentMetricBuilderUnsupervised<IndependentAutoEncoderMetricBuilder> {
    private double _recError = 0.0d;
    transient private DeeplearningMojoModel _mojoModel = null;

    public IndependentAutoEncoderMetricBuilder() {}
    
    public IndependentAutoEncoderMetricBuilder(DeeplearningMojoModel mojoModel) {
      _mojoModel = mojoModel;
    }

    @Override public double[] perRow(double[] prediction, double[] original) {
        _recError += _mojoModel.calculateReconstructionErrorPerRowData(original, prediction);
        _count += 1;
        return prediction;
    }

    @Override public double[] perRow(double[] prediction, float[] original) {
        return perRow(prediction, float2double(original));
    }

    @Override public void reduce(IndependentAutoEncoderMetricBuilder mb) {
        super.reduce(mb);
        _recError += mb._recError;
    }

    @Override public ModelMetrics makeModelMetrics() {
        return new ModelMetricsAutoEncoder(null, null, _count, _recError / _count, _customMetric);
    }
  }
}
