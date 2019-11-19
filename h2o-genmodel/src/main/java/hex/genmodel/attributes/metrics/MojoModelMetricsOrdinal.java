package hex.genmodel.attributes.metrics;

import hex.genmodel.attributes.SerializedName;
import hex.genmodel.attributes.Table;

public class MojoModelMetricsOrdinal extends MojoModelMetricsSupervised {
  public float[] _hit_ratios;
  @SerializedName(value = "cm", insideElementPath = "table")
  public Table _cm;
  public Table _hit_ratio_table;
  public double _logloss;
  public double _mean_per_class_error;
  public String[] _domain;
  public double _sigma;

}
