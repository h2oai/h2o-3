package hex.deepwater;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Ignore;

@Ignore
public class DeepWaterXGRPCIntegrationTest extends DeepWaterAbstractIntegrationTest {
    @Override
    DeepWaterParameters.Backend getBackend() { return DeepWaterParameters.Backend.xgrpc; }

    @BeforeClass
    public static void checkBackend() { Assume.assumeTrue(DeepWater.haveBackend(DeepWaterParameters.Backend.xgrpc)); }
}
