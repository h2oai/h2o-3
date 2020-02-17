package hex.tree.xgboost;

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ClearSystemProperties;

import static org.junit.Assert.assertEquals;

public class XGBoostModelMaxThreadsTest {

    @Rule
    public final ClearSystemProperties clearPropertyRule = new ClearSystemProperties("sys.ai.h2o.xgboost.nthreadMax");

    @Test
    public void shouldGetCorrectMaxThread() {
        assertEquals(Runtime.getRuntime().availableProcessors(), XGBoostModel.getMaxNThread());
        System.setProperty("sys.ai.h2o.xgboost.nthreadMax", "2");
        assertEquals(2, XGBoostModel.getMaxNThread());
    }

}
