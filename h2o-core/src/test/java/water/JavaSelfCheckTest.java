package water;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.*;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class JavaSelfCheckTest {

    @Test
    public void checkCompatibility() {
        assertTrue(JavaSelfCheck.checkCompatibility());
    }

    @Test
    public void checkCompatibility_dynamic() throws Exception {
        assertTrue(H2O.dynamicallyInvokeJavaSelfCheck());
    }

}
