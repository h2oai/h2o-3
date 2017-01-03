package hex.deepwater;

public class DeepWaterCaffeIntegrationTest extends DeepWaterAbstractIntegrationTest {

  @Override
  DeepWaterParameters.Backend getBackend() { return DeepWaterParameters.Backend.caffe; }
}
