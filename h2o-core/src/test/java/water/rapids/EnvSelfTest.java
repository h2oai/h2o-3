package water.rapids;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import water.TestUtil;

public class EnvSelfTest {

    @Test // meta-meta test - makes sure that on CI we run with Rapids object consistency check
    public void checkObjectConsistencyCheckEnabled() {
        Assume.assumeTrue(TestUtil.isCI());
        Assert.assertTrue(Env.DEV_CHECK_OBJECT_CONSISTENCY);
    }

}
