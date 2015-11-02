package water.api;

import hex.ModelMetricsBinomial;
import hex.ModelMetricsMultinomial;
import static hex.ModelMetricsMultinomial.getHitRatioTable;
import water.util.TwoDimTable;

public class ModelMetricsMultinomialV3<I extends ModelMetricsMultinomial, S extends ModelMetricsMultinomialV3<I, S>> extends ModelMetricsBase<ModelMetricsMultinomial, ModelMetricsMultinomialV3<I,S>> {
  @API(help="The R^2 for this scoring run.", direction=API.Direction.OUTPUT)
  public double r2;

  @API(help="The hit ratio table for this scoring run.", direction=API.Direction.OUTPUT, level= API.Level.expert)
  public TwoDimTableBase hit_ratio_table;

  @API(help="The ConfusionMatrix object for this scoring run.", direction=API.Direction.OUTPUT)
  public ConfusionMatrixBase cm;

  @API(help="The logarithmic loss for this scoring run.", direction=API.Direction.OUTPUT)
  public double logloss;

  @Override public ModelMetricsMultinomialV3 fillFromImpl(ModelMetricsMultinomial modelMetrics) {
    super.fillFromImpl(modelMetrics);
    logloss = modelMetrics._logloss;
    r2 = modelMetrics.r2();

    if (modelMetrics._hit_ratios != null) {
      TwoDimTable table = getHitRatioTable(modelMetrics._hit_ratios);
      hit_ratio_table = (TwoDimTableBase)Schema.schema(this.getSchemaVersion(), table).fillFromImpl(table);
    }

    if (null != modelMetrics._cm) {
      modelMetrics._cm.table();  // Fill in lazy table, for icing
      cm = (ConfusionMatrixBase) Schema.schema(this.getSchemaVersion(), modelMetrics._cm).fillFromImpl(modelMetrics._cm);
    }

    return this;
  }
}
