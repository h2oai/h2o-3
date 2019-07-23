package hex.genmodel.attributes.metrics;

import hex.genmodel.attributes.Table;

public class MojoModelMetricsOrdinal extends MojoModelMetrics {
  public float[] _hit_ratios;
  @SerializedName(value = "cm", insideElementPath = "table")
  public Table _cm;
  public double _logloss;
  public double _mean_per_class_error;
  public String[] _domain;
  public double _sigma;

}
