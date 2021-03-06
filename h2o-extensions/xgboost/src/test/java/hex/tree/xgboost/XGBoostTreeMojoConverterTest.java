package hex.tree.xgboost;

import biz.k11i.xgboost.Predictor;
import biz.k11i.xgboost.gbm.GBTree;
import biz.k11i.xgboost.tree.RegTree;
import biz.k11i.xgboost.tree.RegTreeNode;
import hex.genmodel.algos.tree.SharedTreeGraph;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.genmodel.algos.xgboost.XGBoostJavaMojoModel;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.*;
import water.fvec.Frame;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(Parameterized.class)
public class XGBoostTreeMojoConverterTest extends TestUtil {

    @Parameterized.Parameters(name = "{index}: gbm({0})")
    public static Iterable<?> data() { 
        return Arrays.asList(
                0,  // first tree 
                -1  // all trees
        );
    }

    @Parameterized.Parameter
    public int _treeToPrint;

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
            XGBoostJavaMojoModel mojo = (XGBoostJavaMojoModel) model.toMojo();

            final GBTree booster = (GBTree) new Predictor(new ByteArrayInputStream(model.model_info()._boosterBytes)).getBooster();
            final RegTree tree = booster.getGroupedTrees()[0][0];
            final RegTreeNode[] nodes = tree.getNodes();
            assertNotNull(nodes);

            final SharedTreeGraph sharedTreeGraph = mojo.convert(_treeToPrint, "down");
            assertNotNull(sharedTreeGraph);
            assertEquals(parms._ntrees * tfr.vec(response).domain().length, sharedTreeGraph.subgraphArray.size());
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
            XGBoostJavaMojoModel mojo = (XGBoostJavaMojoModel) model.toMojo();

            final GBTree booster = (GBTree) new Predictor(new ByteArrayInputStream(model.model_info()._boosterBytes)).getBooster();
            final RegTree tree = booster.getGroupedTrees()[0][0];
            final RegTreeNode[] nodes = tree.getNodes();
            assertNotNull(nodes);

            final SharedTreeGraph sharedTreeGraph = mojo.convert(_treeToPrint, "NO");
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
    public void testXGBoostBinomialClass_treeClassIsIgnored() throws IOException {
        Scope.enter();
        try {
            final Frame frame = parseTestFile("./smalldata/testng/airlines_train.csv");
            Scope.track(frame);
            String response = "IsDepDelayed";

            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._ntrees = 1;
            parms._max_depth = 3;
            parms._ignored_columns = new String[]{"fYear", "fMonth", "fDayofMonth", "fDayOfWeek", "UniqueCarrier"};
            parms._train = frame._key;
            parms._response_column = response;
            parms._reg_lambda = 0;

            final XGBoostModel model = new hex.tree.xgboost.XGBoost(parms)
                    .trainModel()
                    .get();
            Scope.track_generic(model);
            XGBoostJavaMojoModel mojo = (XGBoostJavaMojoModel) model.toMojo();

            final SharedTreeGraph sharedTreeGraph = mojo.convert(_treeToPrint, "ANYTHING");
            assertNotNull(sharedTreeGraph);
            assertEquals(parms._ntrees, sharedTreeGraph.subgraphArray.size());
            final SharedTreeSubgraph sharedTreeSubgraph = sharedTreeGraph.subgraphArray.get(0);
            assertEquals(parms._max_depth, sharedTreeSubgraph.nodesArray.get(sharedTreeSubgraph.nodesArray.size() - 1).getDepth());
        } finally {
            Scope.exit();
        }
    }

}
