package water.api.schemas3;

import hex.ModelMetricsRegressionCoxPH;
import water.api.API;
import water.api.API.Direction;

public class ModelMetricsRegressionCoxPHV3 extends ModelMetricsRegressionV3<ModelMetricsRegressionCoxPH, ModelMetricsRegressionCoxPHV3> {
  @API(help="concordance index",direction = Direction.OUTPUT)
  public double concordance;

  @API(help = "number of concordant pairs", direction = Direction.OUTPUT)
  public long concordant;

  @API(help = "number of discordant pairs", direction = Direction.OUTPUT)
  public long discordant;

  @API(help = "number of pairs tied in Y value", direction = Direction.OUTPUT)
  public long tied_y;

  @Override
  public ModelMetricsRegressionCoxPHV3 fillFromImpl(ModelMetricsRegressionCoxPH modelMetrics) {
    super.fillFromImpl(modelMetrics);
    this.concordance = modelMetrics.concordance();
    this.concordant = modelMetrics.concordant();
    this.discordant = modelMetrics.discordant();
    this.tied_y = modelMetrics.tiedY();
    return this;
  }

}
