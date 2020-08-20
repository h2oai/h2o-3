package water.api.schemas3;

import hex.ModelMetricsRegressionCoxPH;
import water.api.API;
import water.api.API.Direction;

public class ModelMetricsRegressionCoxPHV3 extends ModelMetricsRegressionV3<ModelMetricsRegressionCoxPH, ModelMetricsRegressionCoxPHV3> {
  @API(help="concordance",direction = Direction.OUTPUT)
  public double concordance;

  @API(help = "concordant", direction = Direction.OUTPUT)
  public long concordant;

  @API(help = "discordant", direction = Direction.OUTPUT)
  public long discordant;

  @API(help = "tiedY", direction = Direction.OUTPUT)
  public long tiedY;

  @Override
  public ModelMetricsRegressionCoxPHV3 fillFromImpl(ModelMetricsRegressionCoxPH modelMetrics) {
    super.fillFromImpl(modelMetrics);
    this.concordance = modelMetrics.concordance();
    this.concordant = modelMetrics.concordant();
    this.discordant = modelMetrics.discordant();
    this.tiedY = modelMetrics.tiedY();
    return this;
  }

}
