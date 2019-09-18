package hex.genmodel.attributes.metrics;


public class MojoModelMetricsMultinomialGLM extends MojoModelMetricsMultinomial {
  
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
