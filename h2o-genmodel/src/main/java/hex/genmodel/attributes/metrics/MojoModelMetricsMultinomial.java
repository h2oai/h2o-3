package hex.genmodel.attributes.metrics;

import hex.genmodel.attributes.Table;

public class MojoModelMetricsMultinomial extends MojoModelMetrics {

  @SerializedName(value = "cm", insideElementPath = "table")
  public Table _confusion_matrix;
  @SerializedName("hit_ratio_table")
  public Table _hit_ratios;
  public double _logloss;
  public double _mean_per_class_error;
}
