package water.api;

import hex.ModelMetricsAutoEncoder;

public class ModelMetricsAutoEncoderV3 extends ModelMetricsBase<ModelMetricsAutoEncoder, ModelMetricsAutoEncoderV3> {
  @API(help="The Mean Squared Error of the reconstruction.", direction=API.Direction.OUTPUT)
  public double mse;
}
