package hex.deepwater;

import org.junit.Before;
import org.junit.Ignore;

@Ignore
public class DeepWaterTensorflowIntegrationTest extends DeepWaterAbstractIntegrationTest {

    @Before
    public void setUp() throws Exception {
        backend = DeepWaterParameters.Backend.tensorflow;
    }

}
