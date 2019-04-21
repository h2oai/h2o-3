package water.api.schemas3;

import hex.ModelMetricsOrdinal;
import water.api.API;
import water.api.SchemaServer;
import water.util.TwoDimTable;

import static hex.ModelMetricsMultinomial.getHitRatioTable;

public class ModelMetricsOrdinalV3<I extends ModelMetricsOrdinal, S extends ModelMetricsOrdinalV3<I, S>>
    extends ModelMetricsBaseV3<I, S> {
  @API(help="The R^2 for this scoring run.", direction=API.Direction.OUTPUT)
  public double r2;

  @API(help="The hit ratio table for this scoring run.", direction=API.Direction.OUTPUT, level= API.Level.expert)
  public TwoDimTableV3 hit_ratio_table;

  @API(help="The ConfusionMatrix object for this scoring run.", direction=API.Direction.OUTPUT)
  public ConfusionMatrixV3 cm;

  @API(help="The logarithmic loss for this scoring run.", direction=API.Direction.OUTPUT)
  public double logloss;

  @API(help="The mean misclassification error per class.", direction=API.Direction.OUTPUT)
  public double mean_per_class_error;

  @Override
  public S fillFromImpl(I modelMetrics) {
    super.fillFromImpl(modelMetrics);
    logloss = modelMetrics._logloss;
    r2 = modelMetrics.r2();

    if (modelMetrics._hit_ratios != null) {
      TwoDimTable table = getHitRatioTable(modelMetrics._hit_ratios);
      hit_ratio_table = (TwoDimTableV3) SchemaServer.schema(this.getSchemaVersion(), table).fillFromImpl(table);
    }

    if (null != modelMetrics._cm) {
      modelMetrics._cm.table();  // Fill in lazy table, for icing
      cm = (ConfusionMatrixV3) SchemaServer.schema(this.getSchemaVersion(), modelMetrics._cm).fillFromImpl
          (modelMetrics._cm);
    }

    return (S)this;
  }
}
