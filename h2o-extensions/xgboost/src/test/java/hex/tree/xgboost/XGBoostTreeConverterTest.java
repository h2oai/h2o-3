package hex.tree.xgboost;

import biz.k11i.xgboost.Predictor;
import biz.k11i.xgboost.gbm.GBTree;
import biz.k11i.xgboost.tree.RegTree;
import biz.k11i.xgboost.tree.RegTreeNode;
import hex.genmodel.algos.tree.SharedTreeGraph;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.ExtensionManager;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;

import java.io.ByteArrayInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class XGBoostTreeConverterTest extends TestUtil {

    @BeforeClass
    public static void stall() {
        stall_till_cloudsize(1);

        // we need to check for XGBoost backend availability after H2O is initialized, since we
        // XGBoost is a core extension and they are registered as part of the H2O's class main method
        Assume.assumeTrue("XGBoost was not loaded!\n"
                        + "H2O XGBoost needs binary compatible environment;"
                        + "Make sure that you have correct libraries installed"
                        + "and correctly configured LD_LIBRARY_PATH, especially"
                        + "make sure that CUDA libraries are available if you are running on GPU!",
                ExtensionManager.getInstance().isCoreExtensionsEnabled(XGBoostExtension.NAME));
    }

    @Test
    public void convertXGBoostTree_weather() throws Exception{
        Frame tfr = null;
        XGBoostModel model = null;
        Scope.enter();
        try {
            // Parse frame into H2O
            tfr = parse_test_file("./smalldata/junit/weather.csv");
            String response = "PressureChange";
            Scope.track(tfr.replace(tfr.find(response), tfr.vecs()[tfr.find(response)].toCategoricalVec()));
            DKV.put(tfr);

            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._ntrees = 1;
            parms._max_depth = 3;
            parms._train = tfr._key;
            parms._response_column = response;

            model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();

            final GBTree booster = (GBTree) new Predictor(new ByteArrayInputStream(model.model_info()._boosterBytes)).getBooster();
            final RegTree tree = booster.getGroupedTrees()[0][0];
            final RegTreeNode[] nodes = tree.getNodes();

            final SharedTreeGraph sharedTreeGraph = model.convert(0, "down");
            final String s = XGBoostUtils.makeFeatureMap(tfr, model.model_info()._dataInfoKey.get());
            assertNotNull(sharedTreeGraph);
            assertEquals(parms._ntrees, sharedTreeGraph.subgraphArray.size());
            final SharedTreeSubgraph sharedTreeSubgraph = sharedTreeGraph.subgraphArray.get(0);
            assertEquals(parms._max_depth, sharedTreeSubgraph.nodesArray.get(sharedTreeSubgraph.nodesArray.size() - 1).getDepth());
        } finally {
            Scope.exit();
            if (tfr!=null) tfr.remove();
            if (model!=null) model.delete();
        }
    }

    @Test
    public void convertXGBoostTree_airlines() throws Exception{
        Frame tfr = null;
        XGBoostModel model = null;
        Scope.enter();
        try {
            // Parse frame into H2O
            tfr = parse_test_file("./smalldata/testng/airlines_train.csv");
            String response = "IsDepDelayed";
            Scope.track(tfr.replace(tfr.find(response), tfr.vecs()[tfr.find(response)].toCategoricalVec()));
            DKV.put(tfr);

            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._ntrees = 1;
            parms._max_depth = 5;
            parms._ignored_columns = new String[]{"fYear","fMonth","fDayofMonth","fDayOfWeek","UniqueCarrier", "Dest"};
            parms._train = tfr._key;
            parms._response_column = response;

            model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();

            final GBTree booster = (GBTree) new Predictor(new ByteArrayInputStream(model.model_info()._boosterBytes)).getBooster();
            final RegTree tree = booster.getGroupedTrees()[0][0];
            final RegTreeNode[] nodes = tree.getNodes();

            final SharedTreeGraph sharedTreeGraph = model.convert(0, "NO");
            assertNotNull(sharedTreeGraph);
            assertEquals(parms._ntrees, sharedTreeGraph.subgraphArray.size());
            final SharedTreeSubgraph sharedTreeSubgraph = sharedTreeGraph.subgraphArray.get(0);
            assertEquals(parms._max_depth, sharedTreeSubgraph.nodesArray.get(sharedTreeSubgraph.nodesArray.size() - 1).getDepth());

        } finally {
            Scope.exit();
            if (tfr!=null) tfr.remove();
            if (model!=null) model.delete();
        }
    }

}