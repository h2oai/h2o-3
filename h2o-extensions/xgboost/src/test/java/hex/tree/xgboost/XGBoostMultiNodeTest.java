package hex.tree.xgboost;

import hex.KeyValue;
import hex.SplitFrame;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.H2O;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class XGBoostMultiNodeTest extends TestUtil  {

    private Frame[] loadData() {
        Frame df = Scope.track(parseTestFile("smalldata/demos/bank-additional-full.csv"));
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
        parms._seed = 0xCAFEBABE;
        return parms;
    }
    
    @Test 
    public void shouldBuildExactOnSingleNode() {
        Assume.assumeTrue(H2O.getCloudSize() == 1);
        Scope.enter();
        try {
            XGBoostModel.XGBoostParameters parms = makeParms();
            parms._tree_method = XGBoostModel.XGBoostParameters.TreeMethod.exact;
            parms._build_tree_one_node = true;
            XGBoostModel model1 = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
            Scope.track_generic(model1);
            int treeMethodIdx = 0;
            while (!model1._output._native_parameters.get(treeMethodIdx, 0).equals("tree_method")) treeMethodIdx++;
            assertEquals("exact", model1._output._native_parameters.get(treeMethodIdx, 1));
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void shouldFailWithExact() {
        Assume.assumeTrue(H2O.getCloudSize() > 1);
        Scope.enter();
        try {
            XGBoostModel.XGBoostParameters parms = makeParms();
            parms._tree_method = XGBoostModel.XGBoostParameters.TreeMethod.exact;
            Exception thrown = null;
            try {
                XGBoostModel model1 = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
                Scope.track_generic(model1); // should not happen
            } catch (Exception e) {
                thrown = e;
            }
            assertNotNull("Expected exception, but none was thrown", thrown);
            assertTrue("Unexpected exception" + thrown.getMessage(), thrown.getMessage().contains("exact is not supported in distributed environment"));
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void shouldFailWithMonotoneApprox() {
        Assume.assumeTrue(H2O.getCloudSize() > 1);
        Scope.enter();
        try {
            XGBoostModel.XGBoostParameters parms = makeParms();
            parms._monotone_constraints = new KeyValue[] {
                new KeyValue("duration", -1), new KeyValue("age", -1)
            };
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
    public void shouldUseHistWithMonotoneAuto() {
        Assume.assumeTrue(H2O.getCloudSize() > 1);
        Scope.enter();
        try {
            XGBoostModel.XGBoostParameters parms = makeParms();
            parms._monotone_constraints = new KeyValue[] {
                new KeyValue("duration", -1), new KeyValue("age", -1)
            };
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
