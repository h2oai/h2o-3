package water.api;

import hex.ModelMetricsAutoencoder;

public class ModelMetricsAutoencoderV3 extends ModelMetricsBase<ModelMetricsAutoencoder, ModelMetricsAutoencoderV3> {
  @Override public ModelMetricsAutoencoder createImpl() {
    ModelMetricsAutoencoder m = new ModelMetricsAutoencoder(this.model.createImpl().get(), this.frame.createImpl().get());
    return (ModelMetricsAutoencoder) m;
  }
}
