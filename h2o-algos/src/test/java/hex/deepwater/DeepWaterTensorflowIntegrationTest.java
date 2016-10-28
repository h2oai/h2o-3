package hex.deepwater;

public class DeepWaterTensorflowIntegrationTest extends DeepWaterAbstractIntegrationTest {
    @Override
    DeepWaterParameters.Backend getBackend() { return DeepWaterParameters.Backend.tensorflow; }
}
