package hex.tree.xgboost;

import hex.SplitFrame;
import hex.genmodel.utils.DistributionFamily;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;

import java.util.*;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class XGBoostVarImpComparisonTest extends TestUtil {

    @BeforeClass
    public static void setup() {
        stall_till_cloudsize(1);
    }

    @Parameterized.Parameters(name = "XGBoost(booster={0},distribution={1},response={2}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {"gbtree", "AUTO", "AGE"},
            {"gbtree", "bernoulli", "CAPSULE"},
            {"gbtree", "multinomial", "CAPSULE"},
            {"gbtree", "gaussian", "AGE"},
            {"gbtree", "gamma", "AGE"},
            {"gbtree", "poisson", "AGE"},
            {"gbtree", "tweedie", "AGE"},
            {"dart", "AUTO", "AGE"},
            {"dart", "bernoulli", "CAPSULE"},
            {"dart", "multinomial", "CAPSULE"},
            {"dart", "gaussian", "AGE"},
            {"dart", "gamma", "AGE"},
            {"dart", "poisson", "AGE"},
            {"dart", "tweedie", "AGE"},
            {"gblinear", "AUTO", "AGE"},
            {"gblinear", "bernoulli", "CAPSULE"},
            {"gblinear", "multinomial", "CAPSULE"},
            {"gblinear", "gaussian", "AGE"},
            {"gblinear", "gamma", "AGE"},
            {"gblinear", "poisson", "AGE"},
            {"gblinear", "tweedie", "AGE"}
        });
    }

    @Parameterized.Parameter
    public String booster;

    @Parameterized.Parameter(1)
    public String distribution;

    @Parameterized.Parameter(2)
    public String response;

    @Test
    public void testImportancesAreSame() {
        Scope.enter();
        try {
            Frame tfr = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));
            // define special columns
            Scope.track(tfr.replace(1, tfr.vecs()[1].toCategoricalVec()));   // Convert CAPSULE to categorical
            Scope.track(tfr.replace(3, tfr.vecs()[3].toCategoricalVec()));   // Convert RACE to categorical
            DKV.put(tfr);

            // split into train/test
            SplitFrame sf = new SplitFrame(tfr, new double[]{0.7, 0.3}, null);
            sf.exec().get();
            Key[] splits = sf._destination_frames;
            Frame trainFrame = Scope.track((Frame) splits[0].get());
            Frame testFrame = Scope.track((Frame) splits[1].get());

            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._booster = XGBoostModel.XGBoostParameters.Booster.valueOf(booster);
            parms._distribution = DistributionFamily.valueOf(distribution);
            parms._ntrees = 10;
            parms._max_depth = 5;
            parms._train = trainFrame._key;
            parms._valid = testFrame._key;
            parms._response_column = response;
            XGBoostModel.XGBoostParameters parmsClone = (XGBoostModel.XGBoostParameters) parms.clone();

            System.setProperty("sys.ai.h2o.xgboost.predict.native.enable", "true");
            XGBoostModel nativeModel = new XGBoost(parms).trainModel().get();
            Scope.track_generic(nativeModel);

            parms = parmsClone;
            System.setProperty("sys.ai.h2o.xgboost.predict.native.enable", "false");
            XGBoostModel javaModel = new XGBoost(parms).trainModel().get();
            Scope.track_generic(javaModel);

            if (nativeModel._output._varimp != javaModel._output._varimp) {
                // one would think that we could simple compare the arrays here, but on GPU the order of rows is different
                assertVarimpEquals(nativeModel._output._varimp, javaModel._output._varimp);
            } else {
                assertNull(nativeModel._output._varimp);
            }
        } finally {
            System.clearProperty("sys.ai.h2o.xgboost.predict.native.enable");
            Scope.exit();
        }
    }
    
    private static void assertVarimpEquals(XgbVarImp expected, XgbVarImp actual) {
        List<String> expectedNames = Arrays.asList(expected._names);
        List<String> actualNames = Arrays.asList(actual._names);
            assertEquals(
            "Names array is different", 
            new HashSet<>(expectedNames), new HashSet<>(actualNames)
        );
        for (int expectedIndex = 0; expectedIndex < expected._names.length; expectedIndex++) {
            int actualIndex = actualNames.indexOf(expectedNames.get(expectedIndex));
            assertTrue("Name " + expected._names[expectedIndex] + " not found in actuals", actualIndex >= 0);
            assertEquals(expected._varimp[expectedIndex], actual._varimp[actualIndex], 1e-2f);
            assertEquals(expected._covers[expectedIndex], actual._covers[actualIndex], 1e-2f);
            assertEquals(expected._freqs[expectedIndex], actual._freqs[actualIndex]);
        }
    }

}
