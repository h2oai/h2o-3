package hex.deepwater;

import org.junit.Ignore;

@Ignore
public class DeepWaterTensorflowIntegrationTest extends DeepWaterAbstractIntegrationTest {
    @Override
    DeepWaterParameters.Backend getBackend() { return DeepWaterParameters.Backend.tensorflow; }
}
