package hex.tree.xgboost.predict;

import biz.k11i.xgboost.Predictor;
import biz.k11i.xgboost.gbm.GBTree;
import biz.k11i.xgboost.tree.NodeHelper;
import biz.k11i.xgboost.tree.RegTree;
import biz.k11i.xgboost.tree.RegTreeImpl;
import biz.k11i.xgboost.tree.RegTreeNode;
import hex.tree.xgboost.XGBoostModel;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.H2ORunner;

import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
public class PredictorTest extends TestUtil {

    @Test
    public void testScoringTreesHaveCorrectNodesInfo() {
        Assume.assumeTrue(PredictorFactory.unsafeTreesSupported());
        Scope.enter();
        try {
            final String response = "PressureChange";
            Frame frame = parseTestFile("./smalldata/junit/weather.csv")
                    .toCategoricalCol(response);
            Scope.track(frame);

            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._ntrees = 10;
            parms._max_depth = 5;
            parms._train = frame._key;
            parms._response_column = response;

            XGBoostModel model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
            assertNotNull(model);
            Scope.track_generic(model);

            byte[] boosterBytes = model.model_info()._boosterBytes;

            Predictor fullPredictor = PredictorFactory.makePredictor(boosterBytes, null, false);
            assertTrue(fullPredictor.getBooster() instanceof GBTree);
            GBTree fullBooster = (GBTree) fullPredictor.getBooster();

            Predictor scoringPredictor = PredictorFactory.makePredictor(boosterBytes, null, true);
            assertTrue(scoringPredictor.getBooster() instanceof GBTree);
            GBTree scoringBooster = (GBTree) scoringPredictor.getBooster();

            assertEquals(fullBooster.getGroupedTrees().length, scoringBooster.getGroupedTrees().length);
            for (int i = 0; i < fullBooster.getGroupedTrees().length; i++) {
                RegTree[] fullTrees = fullBooster.getGroupedTrees()[i];
                RegTree[] scoringTrees = scoringBooster.getGroupedTrees()[i];
                assertEquals(fullTrees.length, scoringTrees.length);
                for (int j = 0; j < fullTrees.length; j++) {
                    assertTrue(fullTrees[j] instanceof RegTreeImpl);
                    assertTrue(scoringTrees[j] instanceof XGBoostRegTree);
                    RegTreeNode[] fullNodes = fullTrees[j].getNodes();
                    RegTreeNode[] scoringNodes = scoringTrees[j].getNodes();
                    assertNotSame(fullNodes, scoringNodes);
                    assertNodesEqual(fullNodes, scoringNodes);
                }
            }
        } finally {
            Scope.exit();
        }
    }

    static void assertNodesEqual(RegTreeNode[] expected, RegTreeNode[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertTrue(NodeHelper.isEqual(expected[i], actual[i]));
        }
    }

}
