package hex.deepwater;

import hex.genmodel.GenModel;
import org.junit.Before;
import org.junit.Ignore;

@Ignore
public class DeepWaterTensorflowIntegrationTest extends DeepWaterAbstractIntegrationTest {

    @Before
    public void setUp() throws Exception {
        backend = GenModel.createDeepWaterBackend(DeepWaterParameters.Backend.tensorflow.toString());
    }

}
