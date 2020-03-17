package water.api.schemas3;

import hex.ModelMetricsOrdinalGeneric;
import water.api.API;

public class ModelMetricsOrdinalGenericV3<I extends ModelMetricsOrdinalGeneric, S extends ModelMetricsOrdinalGenericV3<I, S>>
        extends ModelMetricsBaseV3<I, S> {
  @API(help = "The R^2 for this scoring run.", direction = API.Direction.OUTPUT)
  public double r2;

  @API(help = "The hit ratio table for this scoring run.", direction = API.Direction.OUTPUT, level = API.Level.expert)
  public TwoDimTableV3 hit_ratio_table;

  @API(help = "The ConfusionMatrix object for this scoring run.", direction = API.Direction.OUTPUT)
  public ConfusionMatrixV3 cm;

  @API(help = "The logarithmic loss for this scoring run.", direction = API.Direction.OUTPUT)
  public double logloss;

  @API(help = "The mean misclassification error per class.", direction = API.Direction.OUTPUT)
  public double mean_per_class_error;

  @Override
  public S fillFromImpl(I modelMetrics) {
    super.fillFromImpl(modelMetrics);

    if (null != modelMetrics._confusion_matrix) {
      final ConfusionMatrixV3 convertedConfusionMatrix = new ConfusionMatrixV3();
      convertedConfusionMatrix.table = new TwoDimTableV3().fillFromImpl(modelMetrics._confusion_matrix);
      cm = convertedConfusionMatrix;
    }
    
    mean_per_class_error = modelMetrics._mean_per_class_error;

    return (S) this;
  }
}
