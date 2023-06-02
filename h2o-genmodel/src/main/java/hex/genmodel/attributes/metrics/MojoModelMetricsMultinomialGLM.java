package hex.genmodel.attributes.metrics;


import hex.genmodel.attributes.SerializedName;

public class MojoModelMetricsMultinomialGLM extends MojoModelMetricsMultinomial {
  
  @SerializedName("null_degrees_of_freedom")
  public long _nullDegreesOfFreedom;
  @SerializedName("residual_degrees_of_freedom")
  public long _residualDegreesOfFreedom;
  @SerializedName("residual_deviance")
  public double _resDev;
  @SerializedName("null_deviance")
  public double _nullDev;
  public double _AIC;
  public double _loglikelihood;
}
