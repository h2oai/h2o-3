package hex;

import water.fvec.Frame;

public class ModelMetricsAutoEncoder extends ModelMetricsUnsupervised {
  public final double _mse;
  public ModelMetricsAutoEncoder(Model model, Frame frame) {
    super(model, frame);
    _mse = Double.NaN;
  }
  public ModelMetricsAutoEncoder(Model model, Frame frame, double mse) {
    super(model, frame);
    _mse = mse;
  }

  public static class MetricBuilderAutoEncoder extends MetricBuilderUnsupervised {
    public MetricBuilderAutoEncoder(int dims) {
      _work = new float[dims];
    }

    @Override public float[] perRow( float ds[], float yact[] ) {
      return null; //FIXME
    }

    // Having computed a MetricBuilder, this method fills in a ModelMetrics
    public ModelMetrics makeModelMetrics( Model m, Frame f, double sigma) {
      return null; //FIXME
    }
  }
}
