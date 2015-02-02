package water.api;

import hex.ModelMetricsMultinomial;

public class ModelMetricsMultinomialV3 extends ModelMetricsBase<ModelMetricsMultinomial, ModelMetricsMultinomialV3> {
  @API(help="The Mean Squared Error of the prediction for this scoring run.", direction=API.Direction.OUTPUT)
  public double mse;

  @API(help="The ConfusionMatrix object for this scoring run.", direction=API.Direction.OUTPUT)
  public ConfusionMatrixBase cm;

  @API(help="The HitRatio object for this scoring run.", direction=API.Direction.OUTPUT)
  public HitRatioBase hr;

  @Override public ModelMetricsMultinomialV3 fillFromImpl(ModelMetricsMultinomial modelMetrics) {
    super.fillFromImpl(modelMetrics);
    this.mse = modelMetrics._mse;

    if (null != modelMetrics._hr)
      this.hr = (HitRatioBase)Schema.schema(this.getSchemaVersion(), modelMetrics._hr).fillFromImpl(modelMetrics._hr);

    if (null != modelMetrics._cm)
      this.cm = (ConfusionMatrixBase)Schema.schema(this.getSchemaVersion(), modelMetrics._cm).fillFromImpl(modelMetrics._cm);

    return this;
  }
}
