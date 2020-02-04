package hex.tree.xgboost;

import hex.KeyValue;
import hex.SplitFrame;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import water.H2O;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class XGBoostMonotoneTest  extends TestUtil  {

    @BeforeClass
    public static void stall() {
        stall_till_cloudsize(1);
    }
    
    private Frame[] loadData() {
        Frame df = Scope.track(parse_test_file("smalldata/demos/bank-additional-full.csv"));
        SplitFrame splits = new SplitFrame(df, new double[]{0.7, 0.3}, null);
        splits.exec().get();
        return new Frame[] {
            Scope.track(splits._destination_frames[0].get()),
            Scope.track(splits._destination_frames[1].get())
        };
    }
    
    private XGBoostModel.XGBoostParameters makeParms() {
        Frame[] frames = loadData();
        XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
        parms._train = frames[0]._key;
        parms._valid = frames[1]._key;
        parms._response_column = "y";
        parms._ntrees = 100;
        parms._max_depth = 3;
        parms._monotone_constraints = new KeyValue[] {
            new KeyValue("duration", -1), new KeyValue("age", -1)
        };
        parms._seed = 0xCAFEBABE;
        return parms;
    }

    @Test
    public void shouldFailWithApprox() {
        Assume.assumeTrue(H2O.getCloudSize() > 1);
        Scope.enter();
        try {
            XGBoostModel.XGBoostParameters parms = makeParms();
            parms._tree_method = XGBoostModel.XGBoostParameters.TreeMethod.approx;
            Exception thrown = null;
            try {
                XGBoostModel model1 = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
                Scope.track_generic(model1); // should not happen
            } catch (Exception e) {
                thrown = e;
            }
            assertNotNull("Expected exception, but none was thrown", thrown);
            assertTrue("Unexpected exception" + thrown.getMessage(), thrown.getMessage().contains("approx is not supported with _monotone_constraints"));
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void shouldUseHistWithAuto() {
        Assume.assumeTrue(H2O.getCloudSize() > 1);
        Scope.enter();
        try {
            XGBoostModel.XGBoostParameters parms = makeParms();
            parms._tree_method = XGBoostModel.XGBoostParameters.TreeMethod.auto;
            XGBoostModel model1 = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
            Scope.track_generic(model1); // should not happen
            int treeMethodIdx = 0;
            while (!model1._output._native_parameters.get(treeMethodIdx, 0).equals("tree_method")) treeMethodIdx++;
            assertEquals("hist", model1._output._native_parameters.get(treeMethodIdx, 1));
        } finally {
            Scope.exit();
        }
    }
}
