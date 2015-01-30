package hex;

import water.fvec.Frame;

public class ModelMetricsClustering extends ModelMetricsUnsupervised {
  public final double[] _within_mse; //TODO: add Clustering specific members
  public final double _mse; //TODO: add Clustering specific members
  public ModelMetricsClustering(Model model, Frame frame) {
    super(model, frame);
    _mse = Double.NaN;
    _within_mse = null;
  }
  public ModelMetricsClustering(Model model, Frame frame, double mse, double[] within_mse) {
    super(model, frame);
    _mse = mse;
    _within_mse = within_mse;
  }

  public static class MetricBuilderClustering extends MetricBuilderUnsupervised {
    public MetricBuilderClustering(int dims) {
      _work = new float[dims];
    }

    // Compare row against centroid it was assigned to - compute MSE
    @Override public float[] perRow( float ds[], float[] yact ) {
      assert(ds.length == yact.length);
      // Compute error
      for (int i=0; i<ds.length; ++i) {
        float err = yact[i] - ds[i]; // Error: distance from the actual
        _sumsqe += err*err;       // Squared error
      }
      assert !Double.isNaN(_sumsqe);
      _count++;
      return ds;                // Flow coding
    }

    // Having computed a MetricBuilder, this method fills in a ModelMetrics
    public ModelMetrics makeModelMetrics( Model m, Frame f, double sigma) {
      double mse = _sumsqe / _count;
      double[] within_mse = null; //TODO
      return m._output.addModelMetrics(new ModelMetricsClustering( m, f, mse, within_mse));
    }
  }
}
