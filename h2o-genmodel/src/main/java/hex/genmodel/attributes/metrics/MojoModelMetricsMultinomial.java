package hex.genmodel.attributes.metrics;

import hex.genmodel.attributes.SerializedName;
import hex.genmodel.attributes.Table;

public class MojoModelMetricsMultinomial extends MojoModelMetricsSupervised {

  @SerializedName(value = "cm", insideElementPath = "table")
  public Table _confusion_matrix;
  @SerializedName("hit_ratio_table")
  public Table _hit_ratios;
  @SerializedName("multinomial_auc_table")
  public Table _multinomial_auc;
  @SerializedName("multinomial_aucpr_table")
  public Table _multinomial_aucpr;
  public double _logloss;
  public double _mean_per_class_error;
}
