package hex.tree.xgboost;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ClearSystemProperties;

import static org.junit.Assert.assertEquals;

public class XGBoostModelMaxThreadsTest {

    @Rule
    public final ClearSystemProperties clearPropertyRule = new ClearSystemProperties("sys.ai.h2o.xgboost.nthreadMax");

    @Test
    public void shouldRespectPropertyOverride() {
        int original = XGBoostModel.getMaxNThread();
        Assume.assumeTrue(original >= 2);
        System.setProperty("sys.ai.h2o.xgboost.nthreadMax", String.valueOf(original / 2));
        assertEquals(original / 2, XGBoostModel.getMaxNThread());
    }

}
