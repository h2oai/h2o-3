package hex.genmodel.attributes.metrics;

import hex.genmodel.attributes.SerializedName;

public class MojoModelMetricsRegressionCoxPH extends MojoModelMetricsRegression {
  @SerializedName("concordance")
  public double concordance;

  @SerializedName("concordant")
  public long concordant;

  @SerializedName("discordant")
  public long discordant;

  @SerializedName("tiedY")
  public long tiedY;
}
