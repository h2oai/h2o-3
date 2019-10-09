package hex.genmodel.attributes.metrics;

import hex.genmodel.attributes.SerializedName;
import hex.genmodel.attributes.Table;

public class MojoModelMetricsBinomial extends MojoModelMetricsSupervised {
  
  @SerializedName("AUC")
  public double _auc;
  public double _pr_auc;
  @SerializedName("Gini")
  public double _gini;
  public double _mean_per_class_error;
  public double _logloss;
  public Table _gains_lift_table;
  public Table _thresholds_and_metric_scores;
  public Table _max_criteria_and_metric_scores;
  @SerializedName(value = "cm", insideElementPath = "table")
  public Table _confusion_matrix;
}
