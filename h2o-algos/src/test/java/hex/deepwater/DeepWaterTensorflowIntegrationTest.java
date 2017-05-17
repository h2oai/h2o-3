package hex.deepwater;

import org.junit.Assume;
import org.junit.BeforeClass;

public class DeepWaterTensorflowIntegrationTest extends DeepWaterAbstractIntegrationTest {
    @Override
    DeepWaterParameters.Backend getBackend() { return DeepWaterParameters.Backend.tensorflow; }

    @BeforeClass
    public static void checkBackend() { Assume.assumeTrue(DeepWater.haveBackend(DeepWaterParameters.Backend.tensorflow)); }
}
