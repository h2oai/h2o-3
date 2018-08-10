package hex.tree;

import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.genmodel.utils.DistributionFamily;
import hex.schemas.TreeV3;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import water.*;
import water.api.schemas3.KeyV3;
import water.fvec.Frame;

import java.util.*;

import static org.junit.Assert.*;

public class TreeHandlerTest extends TestUtil {

    @BeforeClass
    public static void stall() {
        stall_till_cloudsize(1);
    }

    @Test
    public void testSharedTreeSubgraphConversion() {
        Frame tfr = null;
        GBMModel model = null;

        Scope.enter();
        try {
            tfr = parse_test_file("./smalldata/airlines/allyears2k_headers.zip");
            DKV.put(tfr);
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = tfr._key;
            parms._response_column = "Dest";
            parms._ntrees = 1;
            parms._seed = 0;

            model = new GBM(parms).trainModel().get();
            final SharedTreeModel.SharedTreeOutput sharedTreeOutput = model._output;
            assertEquals(parms._ntrees, sharedTreeOutput._ntrees);
            assertEquals(parms._ntrees, sharedTreeOutput._treeKeys.length);
            assertEquals(parms._ntrees, sharedTreeOutput._treeKeysAux.length);

            final int treeIndex = 0; //Not a tree number, numbered from 0
            final int treeClass = 0;
            final CompressedTree auxCompressedTree = sharedTreeOutput._treeKeysAux[treeIndex][treeClass].get();
            final SharedTreeSubgraph sharedTreeSubgraph = sharedTreeOutput._treeKeys[treeIndex][treeClass].get()
                    .toSharedTreeSubgraph(auxCompressedTree, sharedTreeOutput._names, sharedTreeOutput._domains);

            assertNotNull(sharedTreeSubgraph);

            final TreeHandler.TreeProperties treeProperties = TreeHandler.convertSharedTreeSubgraph(sharedTreeSubgraph);
            assertNotNull(treeProperties);
            assertEquals(sharedTreeSubgraph.nodesArray.size(), treeProperties._descriptions.length);
            assertEquals(sharedTreeSubgraph.nodesArray.size(), treeProperties._thresholds.length);
            assertEquals(sharedTreeSubgraph.nodesArray.size(), treeProperties._features.length);
            assertEquals(sharedTreeSubgraph.nodesArray.size(), treeProperties._nas.length);

            final int[] leftChildren = treeProperties._leftChildren;
            final int[] rightChildren = treeProperties._rightChildren;
            assertEquals(leftChildren.length, rightChildren.length);

            final SharedTreeNode rootNode = sharedTreeSubgraph.rootNode;
            final Deque<SharedTreeNode> discoverednodes = new ArrayDeque<>();
            discoverednodes.push(rootNode);
            int nonRootNodesFound = 0;
            for (int i = 0; i < leftChildren.length; i++) {
                final SharedTreeNode sharedTreeNode = discoverednodes.pollLast();
                final SharedTreeNode leftChild = sharedTreeNode.getLeftChild();
                final SharedTreeNode rightChild = sharedTreeNode.getRightChild();

                if (leftChildren[i] != -1) {
                    assertEquals(leftChildren[i], leftChild.getNodeNumber());
                    discoverednodes.push(sharedTreeNode.getLeftChild());
                    nonRootNodesFound++;
                }
                if (rightChildren[i] != -1) {
                    assertEquals(rightChildren[i], rightChild.getNodeNumber());
                    discoverednodes.push(sharedTreeNode.getRightChild());
                    nonRootNodesFound++;
                }
            }

            assertEquals(sharedTreeSubgraph.nodesArray.size(), nonRootNodesFound + 1); // +1 for the root node that is not represented explicitely


        } finally {
            Scope.exit();
            if (tfr != null) tfr.remove();
            if (model != null) model.remove();
        }
    }

    @Test
    public void testSharedTreeSubgraphConversion_inclusiveLevelsIris() {

        Frame tfr = null;
        GBMModel model = null;

        Scope.enter();
        try {
            tfr = parse_test_file("./smalldata/iris/iris2.csv");
            DKV.put(tfr);
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = tfr._key;
            parms._response_column = "response";
            parms._ntrees = 1;
            parms._seed = 0;

            model = new GBM(parms).trainModel().get();
            final SharedTreeModel.SharedTreeOutput sharedTreeOutput = model._output;
            assertEquals(parms._ntrees, sharedTreeOutput._ntrees);
            assertEquals(parms._ntrees, sharedTreeOutput._treeKeys.length);
            assertEquals(parms._ntrees, sharedTreeOutput._treeKeysAux.length);

            final int treeNumber = 0;
            final int treeClass = 0;
            final CompressedTree auxCompressedTree = sharedTreeOutput._treeKeysAux[treeNumber][treeClass].get();
            final SharedTreeSubgraph sharedTreeSubgraph = sharedTreeOutput._treeKeys[treeNumber][treeClass].get()
                    .toSharedTreeSubgraph(auxCompressedTree, sharedTreeOutput._names, sharedTreeOutput._domains);

            assertNotNull(sharedTreeSubgraph);

            final TreeHandler.TreeProperties treeProperties = TreeHandler.convertSharedTreeSubgraph(sharedTreeSubgraph);
            assertNotNull(treeProperties);

            final String[] nodeDescriptions = treeProperties._descriptions;
            assertEquals(sharedTreeSubgraph.nodesArray.size(), nodeDescriptions.length);

            for (String nodeDescription : nodeDescriptions) {
                assertFalse(nodeDescription.isEmpty());
            }

        } finally {
            if (tfr != null) tfr.remove();
            if (model != null) model.remove();
        }

    }

    @Test
    public void testSharedTreeSubgraphConversion_argumentValidationMultinomial() {

        Frame tfr = null;
        GBMModel model = null;

        Scope.enter();
        try {
            tfr = parse_test_file("./smalldata/iris/iris2.csv");
            DKV.put(tfr);
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = tfr._key;
            parms._response_column = "response";
            parms._ntrees = 1;
            parms._seed = 0;

            model = new GBM(parms).trainModel().get();

            final TreeHandler treeHandler = new TreeHandler();
            final TreeV3 args = new TreeV3();
            args.model = new KeyV3.ModelKeyV3(Key.make());

            //Non-existing key

            boolean exceptionThrown = false;
            try {
                treeHandler.getTree(3, args);
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("Given model does not exist"));
                exceptionThrown = true;
            }
            assertTrue(exceptionThrown);
            exceptionThrown = false;

            // Invalid tree index
            args.tree_number = 1;
            args.tree_class = "Iris-setosa";
            args.model = new KeyV3.ModelKeyV3(model._key);
            try {
                treeHandler.getTree(3, args);
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("There is no such tree."));
                exceptionThrown = true;
            }
            assertTrue(exceptionThrown);
            exceptionThrown = false;

            // Invalid tree class
            args.tree_number = 0;
            args.tree_class = "NonExistingCategoricalLevel";

            try {
                treeHandler.getTree(3, args);
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("There is no such tree class. Given categorical level does not exist in response column: NonExistingCategoricalLevel"));
                exceptionThrown = true;
            }
            assertTrue(exceptionThrown);
            exceptionThrown = false;

            //  Tree number < 0
            args.tree_number = -1;

            try {
                treeHandler.getTree(3, args);
            } catch (IllegalArgumentException e){
                assertTrue(e.getMessage().contains("Tree number must be greater than 0."));
                exceptionThrown = true;
            }
            assertTrue(exceptionThrown);
        } finally {
            Scope.exit();
            if (tfr != null) tfr.remove();
            if (model != null) model.remove();
        }
    }

    @Test
    public void testSharedTreeSubgraphConversion_argumentValidationRegression() {


        Frame tfr = null;
        GBMModel regressionModel = null;

        Scope.enter();
        try {
            tfr = parse_test_file("./smalldata/iris/iris2.csv");
            DKV.put(tfr);
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = tfr._key;
            parms._ntrees = 1;
            parms._seed = 0;
            parms._response_column = "Sepal.Length";

            final TreeV3 args = new TreeV3();
            regressionModel = new GBM(parms).trainModel().get();
            args.model = new KeyV3.ModelKeyV3(regressionModel._key);
            args.tree_class = "NonExistingClass";


            final TreeHandler treeHandler = new TreeHandler();
            boolean exceptionThrown = false;
            try {
                treeHandler.getTree(3, args);
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("There are no tree classes for regression."));
                exceptionThrown = true;
            }
            assertTrue(exceptionThrown);


        } finally {
            if (tfr != null) tfr.remove();
            if (regressionModel != null) regressionModel.remove();
            Scope.exit();
        }
    }

    @Test
    public void testSharedTreeSubgraphConversion_argumentValidationBinomial() {


        Frame tfr = null;
        GBMModel model = null;

        Scope.enter();
        try {
            tfr = parse_test_file("./smalldata/testng/airlines_train.csv");
            DKV.put(tfr);
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = tfr._key;
            parms._ntrees = 1;
            parms._seed = 0;
            parms._response_column = "IsDepDelayed";

            // Test incorrect tree request
            final TreeV3 args = new TreeV3();
            model = new GBM(parms).trainModel().get();
            args.model = new KeyV3.ModelKeyV3(model._key);
            args.tree_class = "YES";

            // If the tree class name is specified, it must be the tree class built exactly
            final TreeHandler treeHandler = new TreeHandler();
            boolean exceptionThrown = false;
            try {
                treeHandler.getTree(3, args);
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("For binomial, only one tree class has been built per each iteration: NO"));
                exceptionThrown = true;
            }
            assertTrue(exceptionThrown);

            args.tree_class = "NO";
            final TreeV3 correctlySpecifiedClassTree = treeHandler.getTree(3, args);
            assertNotNull(correctlySpecifiedClassTree);

            args.tree_class = "";
            final TreeV3 noClassTree = treeHandler.getTree(3, args);
            assertNotNull(noClassTree);

        } finally {
            if (tfr != null) tfr.remove();
            if (model != null) model.remove();
            Scope.exit();
        }
    }


}