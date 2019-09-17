package hex.genmodel.attributes.metrics;


public class MojoModelMetricsOrdinalGLM extends MojoModelMetricsOrdinal {
  public long _nullDegressOfFreedom;
  public long _residualDegressOfFreedom;
  @SerializedName("residual_deviance")
  public double _resDev;
  @SerializedName("null_deviance")
  public double _nullDev;
  public double _AIC;
  
}
