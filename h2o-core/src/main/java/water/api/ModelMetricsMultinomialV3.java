package water.api;

import hex.ModelMetricsMultinomial;

public class ModelMetricsMultinomialV3 extends ModelMetricsBase<ModelMetricsMultinomial, ModelMetricsMultinomialV3> {
  @API(help="The Mean Squared Error of the prediction for this scoring run.", direction=API.Direction.OUTPUT)
  public double mse;

  @API(help="The ConfusionMatrix object for this scoring run.", direction=API.Direction.OUTPUT)
  public ConfusionMatrixBase cm;

  @API(help="The HitRatio object for this scoring run.", direction=API.Direction.OUTPUT)
  public HitRatioBase hr;

  @Override public ModelMetricsMultinomial createImpl() {
    return new ModelMetricsMultinomial(this.model.createImpl().get(), this.frame.createImpl().get());
  }
}
