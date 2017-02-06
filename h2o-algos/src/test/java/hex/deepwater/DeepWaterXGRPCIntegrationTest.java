package hex.deepwater;

import org.junit.Ignore;

@Ignore
public class DeepWaterXGRPCIntegrationTest extends DeepWaterAbstractIntegrationTest {
    @Override
    DeepWaterParameters.Backend getBackend() { return DeepWaterParameters.Backend.xgrpc; }
}
