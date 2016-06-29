package water.api;

import hex.glrm.ModelMetricsGLRM;
import water.api.schemas3.ModelMetricsBaseV3;

public class ModelMetricsGLRMV99 extends ModelMetricsBaseV3<ModelMetricsGLRM, ModelMetricsGLRMV99> {
  @API(help="Sum of Squared Error (Numeric Cols)")
  public double numerr;

  @API(help="Misclassification Error (Categorical Cols)")
  public double caterr;

  @API(help="Number of Non-Missing Numeric Values")
  public long numcnt;

  @API(help="Number of Non-Missing Categorical Values")
  public long catcnt;
}
