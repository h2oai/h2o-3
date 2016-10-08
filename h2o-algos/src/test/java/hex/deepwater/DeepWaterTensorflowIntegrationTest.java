package hex.deepwater;

import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;

import static hex.genmodel.algos.DeepWaterMojo.createDeepWaterBackend;

@Ignore
public class DeepWaterTensorflowIntegrationTest extends DeepWaterAbstractIntegrationTest {

    @Before
    public void setUp() throws Exception {
        backend = createDeepWaterBackend(DeepWaterParameters.Backend.tensorflow.toString());
    }
    @BeforeClass
    static public void _preconditionDeepWater() { // NOTE: the `_` force execution of this check after setup
        Assume.assumeTrue(createDeepWaterBackend(DeepWaterParameters.Backend.tensorflow.toString())!=null);
    }
}
