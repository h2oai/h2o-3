package water.api;

import hex.glrm.ModelMetricsGLRM;

public class ModelMetricsGLRMV99 extends ModelMetricsBase<ModelMetricsGLRM, ModelMetricsGLRMV99> {
  @API(help="Sum of Squared Error (Numeric Cols)")
  public double numerr;

  @API(help="Misclassification Error (Categorical Cols)")
  public double caterr;
}
