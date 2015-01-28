package water.api;

import hex.ModelMetricsAutoEncoder;

public class ModelMetricsAutoEncoderV3 extends ModelMetricsBase<ModelMetricsAutoEncoder, ModelMetricsAutoEncoderV3> {
  @API(help="The Mean Squared Error of the reconstruction.", direction=API.Direction.OUTPUT)
  public double mse;

  @Override public ModelMetricsAutoEncoder createImpl() {
    return new ModelMetricsAutoEncoder(this.model.createImpl().get(), this.frame.createImpl().get());
  }
}
