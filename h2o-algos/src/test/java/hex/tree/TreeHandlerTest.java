package hex.tree;

import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.schemas.TreeV3;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;
import water.*;
import water.api.schemas3.KeyV3;
import water.fvec.Frame;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TreeHandlerTest extends TestUtil {

    @BeforeClass
    public static void stall() {
        stall_till_cloudsize(1);
    }

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

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

            final int[] leftChildren = treeProperties.getLeftChildren();
            final int[] rightChildren = treeProperties.getRightChildren();
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

            final String[] nodeDescriptions = treeProperties.getDescriptions();
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
    public void testSharedTreeSubgraphConversion_argumentValidation() {

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

            final TreeHandler treeHandler = new TreeHandler();
            TreeV3 args = new TreeV3();
            args.model = new KeyV3.ModelKeyV3(Key.make());

            //Unexisting key

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
            args.tree_class = 0;
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
            args.tree_class = 10;

            try {
                treeHandler.getTree(3, args);
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("There is no such tree class."));
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
            exceptionThrown = false;

            //Tree class < 0
            args.tree_number = 0;
            args.tree_class = -1;
            try {
                treeHandler.getTree(3, args);
            } catch (IllegalArgumentException e){
                assertTrue(e.getMessage().contains("Tree class must be greater than 0."));
                exceptionThrown = true;
            }
            assertTrue(exceptionThrown);
            exceptionThrown = false;

        } finally {
            if (tfr != null) tfr.remove();
            if (model != null) model.remove();
        }
    }

    @Test
    public void testArtificialTreeConversion(){
        final SharedTreeSubgraph mock = mock(SharedTreeSubgraph.class);
    }



}