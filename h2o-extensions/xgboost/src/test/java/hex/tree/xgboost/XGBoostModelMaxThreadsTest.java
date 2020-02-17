package hex.tree.xgboost;

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ClearSystemProperties;
import water.H2O;

import static org.junit.Assert.assertEquals;

public class XGBoostModelMaxThreadsTest {

    @Rule
    public final ClearSystemProperties clearPropertyRule = new ClearSystemProperties("sys.ai.h2o.xgboost.nthreadMax");

    @Test
    public void shouldGetCorrectMaxThread() {
        int cloudSize = Math.max(1, H2O.CLOUD.size());
        assertEquals(Runtime.getRuntime().availableProcessors() / cloudSize, XGBoostModel.getMaxNThread());
        System.setProperty("sys.ai.h2o.xgboost.nthreadMax", "2");
        assertEquals(2, XGBoostModel.getMaxNThread());
    }

}
