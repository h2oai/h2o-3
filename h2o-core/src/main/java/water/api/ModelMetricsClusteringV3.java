package water.api;

import hex.ModelMetricsClustering;
import water.util.TwoDimTable;

public class ModelMetricsClusteringV3 extends ModelMetricsBase<ModelMetricsClustering, ModelMetricsClusteringV3> {
  @API(help="Within Cluster Sum of Square Error")
  public double tot_withinss;       // Total within-cluster sum-of-square error

  @API(help="Total Sum of Square Error to Grand Mean")
  public double totss;    // Total sum-of-square error to grand mean centroid

  @API(help="Between Cluster Sum of Square Error")
  public double betweenss;

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
