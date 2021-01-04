package hex.tree.xgboost;

import biz.k11i.xgboost.Predictor;
import biz.k11i.xgboost.gbm.GBTree;
import biz.k11i.xgboost.tree.RegTree;
import biz.k11i.xgboost.tree.RegTreeNode;
import hex.genmodel.algos.tree.SharedTreeGraph;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.schemas.TreeV3;
import hex.tree.TreeHandler;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import water.*;
import water.api.schemas3.KeyV3;
import water.fvec.Frame;

import java.io.ByteArrayInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class XGBoostTreeConverterTest extends TestUtil {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

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
            tfr = parseTestFile("./smalldata/junit/weather.csv");
            String response = "PressureChange";
            Scope.track(tfr.replace(tfr.find(response), tfr.vecs()[tfr.find(response)].toCategoricalVec()));
            DKV.put(tfr);

            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._ntrees = 1;
            parms._max_depth = 3;
            parms._train = tfr._key;
            parms._response_column = response;
            parms._reg_lambda = 0;

            model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();

            final GBTree booster = (GBTree) new Predictor(new ByteArrayInputStream(model.model_info()._boosterBytes)).getBooster();
            final RegTree tree = booster.getGroupedTrees()[0][0];
            final RegTreeNode[] nodes = tree.getNodes();
            assertNotNull(nodes);

            final SharedTreeGraph sharedTreeGraph = model.convert(0, "down");
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
            tfr = parseTestFile("./smalldata/testng/airlines_train.csv");
            String response = "IsDepDelayed";
            Scope.track(tfr.replace(tfr.find(response), tfr.vecs()[tfr.find(response)].toCategoricalVec()));
            DKV.put(tfr);

            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._ntrees = 1;
            parms._max_depth = 5;
            parms._ignored_columns = new String[]{"fYear","fMonth","fDayofMonth","fDayOfWeek","UniqueCarrier", "Dest"};
            parms._train = tfr._key;
            parms._response_column = response;
            parms._reg_lambda = 0;

            model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();

            final GBTree booster = (GBTree) new Predictor(new ByteArrayInputStream(model.model_info()._boosterBytes)).getBooster();
            final RegTree tree = booster.getGroupedTrees()[0][0];
            final RegTreeNode[] nodes = tree.getNodes();
            assertNotNull(nodes);

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

    @Test
    public void convertXGBoostTree_airlines_wrong_tree_class() throws Exception{
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("There should be no tree class specified for regression.");
        Frame tfr = null;
        XGBoostModel model = null;
        Scope.enter();
        try {
            // Parse frame into H2O
            tfr = parseTestFile("./smalldata/testng/airlines_train.csv", "NA", 1,
                    new byte[]{4,2,2,2,2,4,4,4,3});
            String response = "Distance";
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

            final SharedTreeGraph sharedTreeGraph = model.convert(0, "NO");
            assertNotNull(sharedTreeGraph);
            assertEquals(parms._ntrees, sharedTreeGraph.subgraphArray.size());
            final SharedTreeSubgraph sharedTreeSubgraph = sharedTreeGraph.subgraphArray.get(0);
            assertEquals(parms._max_depth, sharedTreeSubgraph.nodesArray.get(sharedTreeSubgraph.nodesArray.size() - 1).getDepth());

        } finally {
            Scope.exit();
            if (tfr != null) tfr.remove();
            if (model != null) model.delete();
        }
    }

    @Test
    public void testXGBoostBinomialClass_noTreeClassSpecified() {
        Scope.enter();
        try {
            final Frame frame = parseTestFile("./smalldata/testng/airlines_train.csv");
            Scope.track(frame);
            String response = "IsDepDelayed";

            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._ntrees = 1;
            parms._max_depth = 5;
            parms._ignored_columns = new String[]{"fYear", "fMonth", "fDayofMonth", "fDayOfWeek", "UniqueCarrier"};
            parms._train = frame._key;
            parms._response_column = response;
            parms._reg_lambda = 0;

            final XGBoostModel model = new hex.tree.xgboost.XGBoost(parms)
                    .trainModel()
                    .get();
            Scope.track_generic(model);

            // Do not specify tree class for binomial problem - the only tree class should be selected and no error
            // is expected to be thrown
            final TreeHandler treeHandler = new TreeHandler();
            final TreeV3 args = new TreeV3();
            args.model = new KeyV3.ModelKeyV3(model._key);
            final TreeV3 tree = treeHandler.getTree(3, args);
            assertNotNull(tree);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testXGBoostRegression_noTreeClassSpecified() {
        Scope.enter();
        try {
            final Frame frame = parseTestFile("./smalldata/testng/airlines_train.csv");
            Scope.track(frame);
            String response = "Distance";

            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._ntrees = 1;
            parms._max_depth = 5;
            parms._ignored_columns = new String[]{"fYear", "fMonth", "fDayofMonth", "fDayOfWeek", "UniqueCarrier"};
            parms._train = frame._key;
            parms._response_column = response;
            parms._reg_lambda = 0;

            final XGBoostModel model = new hex.tree.xgboost.XGBoost(parms)
                    .trainModel()
                    .get();
            Scope.track_generic(model);

            // Do not specify tree class for regression - the only tree class should be selected and no error
            // is expected to be thrown
            final TreeHandler treeHandler = new TreeHandler();
            final TreeV3 args = new TreeV3();
            args.model = new KeyV3.ModelKeyV3(model._key);
            final TreeV3 tree = treeHandler.getTree(3, args);
            assertNotNull(tree);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testXGBoostMultinomial_noTreeClassSpecified() {
        Scope.enter();
        try {
            final Frame frame = parseTestFile("./smalldata/testng/airlines_train.csv");
            Scope.track(frame);
            String response = "Origin";

            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._ntrees = 1;
            parms._max_depth = 5;
            parms._ignored_columns = new String[]{"fYear", "fMonth", "fDayofMonth", "fDayOfWeek", "UniqueCarrier"};
            parms._train = frame._key;
            parms._response_column = response;
            parms._reg_lambda = 0;

            final XGBoostModel model = new hex.tree.xgboost.XGBoost(parms)
                    .trainModel()
                    .get();
            Scope.track_generic(model);

            final TreeHandler treeHandler = new TreeHandler();
            final TreeV3 args = new TreeV3();
            args.model = new KeyV3.ModelKeyV3(model._key);
            
            // Tree class must be specified for multinomial.
            expectedException.expect(IllegalArgumentException.class);
            expectedException.expectMessage("Model category 'Multinomial' requires tree class to be specified.");
            final TreeV3 tree = treeHandler.getTree(3, args);
            assertNotNull(tree);
        } finally {
            Scope.exit();
        }
    }

}
