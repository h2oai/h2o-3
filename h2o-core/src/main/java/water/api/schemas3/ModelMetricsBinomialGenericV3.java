package water.api.schemas3;

import hex.ModelMetricsBinomialGeneric;
import water.api.API;

public class ModelMetricsBinomialGenericV3<I extends ModelMetricsBinomialGeneric, S extends ModelMetricsBinomialGenericV3<I, S>>
        extends ModelMetricsBinomialV3<I, S> {

  @API(help = "The Metrics for various thresholds.", direction = API.Direction.OUTPUT, level = API.Level.expert)
  public TwoDimTableV3 thresholds_and_metric_scores;

  @API(help = "The Metrics for various criteria.", direction = API.Direction.OUTPUT, level = API.Level.secondary)
  public TwoDimTableV3 max_criteria_and_metric_scores;

  @API(help = "Gains and Lift table.", direction = API.Direction.OUTPUT, level = API.Level.secondary)
  public TwoDimTableV3 gains_lift_table;

  @Override
  public S fillFromImpl(ModelMetricsBinomialGeneric modelMetrics) {
    super.fillFromImpl(modelMetrics);
    r2 = modelMetrics.r2();
    logloss = modelMetrics._logloss;

    if (modelMetrics != null && modelMetrics._confusion_matrix != null) {
      final ConfusionMatrixV3 convertedConfusionMatrix = new ConfusionMatrixV3();
      convertedConfusionMatrix.table = new TwoDimTableV3().fillFromImpl(modelMetrics._confusion_matrix);
      this.cm = convertedConfusionMatrix;
    }

    if (modelMetrics._thresholds_and_metric_scores != null) {
        this.thresholds_and_metric_scores = new TwoDimTableV3().fillFromImpl(modelMetrics._thresholds_and_metric_scores);
      }

    if (modelMetrics._max_criteria_and_metric_scores != null) {
        max_criteria_and_metric_scores = new TwoDimTableV3().fillFromImpl(modelMetrics._max_criteria_and_metric_scores);
    }

    if (modelMetrics._gainsLiftTable != null) {
      this.gains_lift_table = new TwoDimTableV3().fillFromImpl(modelMetrics._gainsLiftTable);
    }

    return (S) this;
  }
}
