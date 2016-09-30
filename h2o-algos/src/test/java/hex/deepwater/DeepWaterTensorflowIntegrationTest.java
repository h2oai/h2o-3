package hex.deepwater;

import hex.deepwater.backends.BackendFactory;
import org.junit.Before;
import org.junit.Ignore;

@Ignore
public class DeepWaterTensorflowIntegrationTest extends DeepWaterAbstractIntegrationTest {

    @Before
    public void setUp() throws Exception {
        backend = BackendFactory.create(DeepWaterParameters.Backend.mxnet);
    }

}
