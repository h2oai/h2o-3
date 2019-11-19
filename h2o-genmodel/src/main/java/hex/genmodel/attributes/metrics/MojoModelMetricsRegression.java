package hex.genmodel.attributes.metrics;

import hex.genmodel.attributes.SerializedName;

public class MojoModelMetricsRegression extends MojoModelMetricsSupervised {
  public double _mean_residual_deviance;
  @SerializedName("rmsle")
  public double _root_mean_squared_log_error;
}
