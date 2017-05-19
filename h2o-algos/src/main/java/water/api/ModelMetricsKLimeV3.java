package water.api;

import hex.klime.KLimeModel;
import water.api.schemas3.ModelMetricsRegressionV3;
import water.api.schemas3.TwoDimTableV3;
import water.util.TwoDimTable;

public class ModelMetricsKLimeV3<I extends KLimeModel.ModelMetricsKLime, S extends ModelMetricsKLimeV3<I, S>> extends ModelMetricsRegressionV3<I, S> {

  @API(help="Number of clusters.", direction=API.Direction.OUTPUT)
  public int k;

  @API(help="Local metrics calculated for each cluster.", direction=API.Direction.OUTPUT)
  public TwoDimTableV3 cluster_metrics;

  @Override
  public S fillFromImpl(I modelMetrics) {
    super.fillFromImpl(modelMetrics);
    k = modelMetrics._clusterMetrics.length;
    String[] clusterNames = new String[k];
    for (int i = 0; i < k; i++)
      clusterNames[i] = Integer.toString(i);
    TwoDimTable cm = new TwoDimTable("Cluster Metrics", "Metrics calculated from cluster-local observations",
            clusterNames,
            new String[]{"Uses Global Model", "r2", "MSE", "RMSE", "nobs"},
            new String[]{"string", "double", "double", "double", "int"},
            null,
            "Cluster");
    for (int i = 0; i < k; i++) {
      cm.set(i, 0, Boolean.toString(modelMetrics._usesGlobalModel[i]));
      cm.set(i, 1, modelMetrics._clusterMetrics[i].r2());
      cm.set(i, 2, modelMetrics._clusterMetrics[i].mse());
      cm.set(i, 3, modelMetrics._clusterMetrics[i].rmse());
      cm.set(i, 4, modelMetrics._clusterMetrics[i]._nobs);
    }
    cluster_metrics = new TwoDimTableV3().fillFromImpl(cm);
    return (S) this;
  }
}
