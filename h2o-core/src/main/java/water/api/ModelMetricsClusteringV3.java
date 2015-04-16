package water.api;

import hex.ModelMetricsClustering;
import water.util.TwoDimTable;

public class ModelMetricsClusteringV3 extends ModelMetricsBase<ModelMetricsClustering, ModelMetricsClusteringV3> {
  @API(help="Average within cluster Mean Square Error")
  public double avg_within_ss;       // Average within-cluster MSE, variance

  @API(help="Average Mean Square Error to grand mean")
  public double avg_ss;    // Total MSE to grand mean centroid

  @API(help="Average between cluster Mean Square Error")
  public double avg_between_ss;

  @API(help="Centroid Statistics")
  public TwoDimTableBase centroid_stats;

  @Override
  public ModelMetricsClusteringV3 fillFromImpl(ModelMetricsClustering impl) {
    ModelMetricsClusteringV3 mm = super.fillFromImpl(impl);
    TwoDimTable tdt = impl.createCentroidStatsTable();
    if (tdt != null)
      mm.centroid_stats = new TwoDimTableBase().fillFromImpl(tdt);
    return mm;
  }
}
