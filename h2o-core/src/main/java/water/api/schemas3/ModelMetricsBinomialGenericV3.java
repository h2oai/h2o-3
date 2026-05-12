package water.api.schemas3;

import hex.ModelMetricsBinomialGeneric;

public class ModelMetricsBinomialGenericV3<I extends ModelMetricsBinomialGeneric, S extends ModelMetricsBinomialGenericV3<I, S>>
        extends ModelMetricsBinomialV3<I, S> {

  @Override
  public S fillFromImpl(ModelMetricsBinomialGeneric modelMetrics) {
    super.fillFromImpl(modelMetrics);
    r2 = modelMetrics.r2();
    logloss = modelMetrics._logloss;
    loglikelihood = modelMetrics._loglikelihood;
    AIC = modelMetrics._aic;

    if (modelMetrics != null && modelMetrics._confusion_matrix != null) {
      final ConfusionMatrixV3 convertedConfusionMatrix = new ConfusionMatrixV3();
      convertedConfusionMatrix.table = new TwoDimTableV3().fillFromImpl(modelMetrics._confusion_matrix);
      this.cm = convertedConfusionMatrix;
    }
    
    if (modelMetrics._thresholds_and_metric_scores != null) { // Possibly overwrites whatever has been set in the ModelMetricsBinomialV3
        this.thresholds_and_metric_scores = new TwoDimTableV3().fillFromImpl(modelMetrics._thresholds_and_metric_scores);
      }

    if (modelMetrics._max_criteria_and_metric_scores != null) { // Possibly overwrites whatever has been set in the ModelMetricsBinomialV3
        max_criteria_and_metric_scores = new TwoDimTableV3().fillFromImpl(modelMetrics._max_criteria_and_metric_scores);
    }

    if (modelMetrics._gainsLiftTable != null) { // Possibly overwrites whatever has been set in the ModelMetricsBinomialV3
      this.gains_lift_table = new TwoDimTableV3().fillFromImpl(modelMetrics._gainsLiftTable);
    }

    return (S) this;
  }
}
