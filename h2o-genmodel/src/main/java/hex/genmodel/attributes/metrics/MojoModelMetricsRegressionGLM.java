package hex.genmodel.attributes.metrics;

import hex.genmodel.attributes.SerializedName;

public class MojoModelMetricsRegressionGLM extends MojoModelMetricsRegression {
  @SerializedName("null_degrees_of_freedom")
  public long _nullDegressOfFreedom;  
  @SerializedName("residual_degrees_of_freedom")
  public long _residualDegressOfFreedom;
  @SerializedName("residual_deviance")
  public double _resDev;
  @SerializedName("null_deviance")
  public double _nullDev;
  public double _AIC;
}
