package ai.h2o;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.H2O;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class AdmissibleMLTest {

    @Test
    public void testNothing() {
        assertTrue(H2O.getCloudSize() > 0);
    }
}
