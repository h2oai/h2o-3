package hex.tree;

import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.Frame;

import java.util.Arrays;

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

            final int treeNumber = 0;
            final int treeClass = 0;
            final CompressedTree auxCompressedTree = sharedTreeOutput._treeKeysAux[treeNumber][treeClass].get();
            final SharedTreeSubgraph sharedTreeSubgraph = sharedTreeOutput._treeKeys[treeNumber][treeClass].get()
                    .toSharedTreeSubgraph(auxCompressedTree, sharedTreeOutput._names, sharedTreeOutput._domains);

            assertNotNull(sharedTreeSubgraph);

            final TreeHandler.CompressedTreeFormat compressedTreeFormat = TreeHandler.convertSharedTreeSubgraph(sharedTreeSubgraph);
            assertNotNull(compressedTreeFormat);

            assertEquals(0, compressedTreeFormat.getRootNodeNumber());

            // Test against manually walked tree to verify algorithm's corectness
            final SharedTreeNode rootNode = sharedTreeSubgraph.rootNode;
            assertEquals(rootNode.getNodeNumber(), compressedTreeFormat.getRootNodeNumber());

            // Root node children
            final SharedTreeNode leftChild11 = rootNode.getLeftChild();
            final SharedTreeNode rightChild12 = rootNode.getRightChild();

            assertEquals(leftChild11.getNodeNumber(), compressedTreeFormat.getLeftChildren()[0]);
            assertEquals(rightChild12.getNodeNumber(), compressedTreeFormat.getRightChildren()[0]);

            // Second level (children of 1st level - 4 in total)
            final SharedTreeNode leftChild21 = leftChild11.getLeftChild();
            final SharedTreeNode rightChild22 = leftChild11.getRightChild();

            final SharedTreeNode leftChild23 = rightChild12.getLeftChild();
            final SharedTreeNode rightChild24 = rightChild12.getRightChild();

            assertEquals(leftChild21.getNodeNumber(), compressedTreeFormat.getLeftChildren()[1]);
            assertEquals(rightChild22.getNodeNumber(), compressedTreeFormat.getRightChildren()[1]);

            assertEquals(leftChild23.getNodeNumber(), compressedTreeFormat.getLeftChildren()[2]);
            assertEquals(rightChild24.getNodeNumber(), compressedTreeFormat.getRightChildren()[2]);

            // Third level (children of 2nd level - 6 in total, two are missing ( node 21 has no children))
            final SharedTreeNode leftChild31 = leftChild21.getLeftChild();
            assertNull(leftChild31);
            final SharedTreeNode rightChild32 = leftChild21.getRightChild();
            assertNull(rightChild32);

            final SharedTreeNode leftChild33 = rightChild22.getLeftChild();
            final SharedTreeNode rightChild34 = rightChild22.getRightChild();

            final SharedTreeNode leftChild35 = leftChild23.getLeftChild();
            final SharedTreeNode rightChild36 = leftChild23.getRightChild();

            final SharedTreeNode leftChild37 = rightChild24.getLeftChild();
            final SharedTreeNode rightChild38 = rightChild24.getRightChild();

            assertEquals(-1, compressedTreeFormat.getLeftChildren()[3]);
            assertEquals(-1, compressedTreeFormat.getRightChildren()[3]);

            assertEquals(leftChild33.getNodeNumber(), compressedTreeFormat.getLeftChildren()[4]);
            assertEquals(rightChild34.getNodeNumber(), compressedTreeFormat.getRightChildren()[4]);

            assertEquals(leftChild35.getNodeNumber(), compressedTreeFormat.getLeftChildren()[5]);
            assertEquals(rightChild36.getNodeNumber(), compressedTreeFormat.getRightChildren()[5]);

            assertEquals(leftChild37.getNodeNumber(), compressedTreeFormat.getLeftChildren()[6]);
            assertEquals(rightChild38.getNodeNumber(), compressedTreeFormat.getRightChildren()[6]);

            // Fourth level (children of 3rd level - 4 child nodes in total)

            final SharedTreeNode leftChild41 = leftChild33.getLeftChild();
            assertNull(leftChild41);
            final SharedTreeNode rightChild42 = leftChild33.getRightChild();
            assertNull(rightChild42);

            final SharedTreeNode leftChild43 = rightChild34.getLeftChild();
            final SharedTreeNode rightChild44 = rightChild34.getRightChild();

            final SharedTreeNode leftChild45 = leftChild35.getLeftChild();
            assertNull(leftChild45);
            final SharedTreeNode rightChild46 = leftChild35.getRightChild();
            assertNull(rightChild46);

            final SharedTreeNode leftChild46 = rightChild36.getLeftChild();
            assertNull(leftChild46);
            final SharedTreeNode rightChild47 = rightChild36.getRightChild();
            assertNull(rightChild47);

            final SharedTreeNode leftChild48 = leftChild37.getLeftChild();
            final SharedTreeNode rightChild49 = leftChild37.getRightChild();

            final SharedTreeNode leftChild410 = rightChild38.getLeftChild();
            assertNull(leftChild410);
            final SharedTreeNode rightChild411 = rightChild38.getRightChild();
            assertNull(rightChild411);


            assertEquals(-1, compressedTreeFormat.getLeftChildren()[7]);
            assertEquals(-1, compressedTreeFormat.getRightChildren()[7]);

            assertEquals(leftChild43.getNodeNumber(), compressedTreeFormat.getLeftChildren()[8]);
            assertEquals(rightChild44.getNodeNumber(), compressedTreeFormat.getRightChildren()[8]);

            assertEquals(-1, compressedTreeFormat.getLeftChildren()[9]);
            assertEquals(-1, compressedTreeFormat.getRightChildren()[9]);

            assertEquals(-1, compressedTreeFormat.getLeftChildren()[10]);
            assertEquals(-1, compressedTreeFormat.getRightChildren()[10]);

            assertEquals(leftChild48.getNodeNumber(), compressedTreeFormat.getLeftChildren()[11]);
            assertEquals(rightChild49.getNodeNumber(), compressedTreeFormat.getRightChildren()[11]);

            assertEquals(-1, compressedTreeFormat.getLeftChildren()[12]);
            assertEquals(-1, compressedTreeFormat.getRightChildren()[12]);

            // Fifth level (children of level 4, 2 child nodes)

            final SharedTreeNode leftChild51 = leftChild43.getLeftChild();
            assertNull(leftChild51);
            final SharedTreeNode rightChild52 = leftChild43.getRightChild();
            assertNull(rightChild52);

            final SharedTreeNode leftChild53 = rightChild44.getLeftChild();
            assertNull(leftChild53);
            final SharedTreeNode rightChild54 = rightChild44.getRightChild();
            assertNull(rightChild54);

            final SharedTreeNode leftChild55 = leftChild48.getLeftChild();
            final SharedTreeNode rightChild56 = leftChild48.getRightChild();

            final SharedTreeNode leftChild57 = rightChild49.getLeftChild();
            assertNull(leftChild57);
            final SharedTreeNode rightChild58 = rightChild49.getRightChild();
            assertNull(rightChild58);

            assertEquals(-1, compressedTreeFormat.getLeftChildren()[13]);
            assertEquals(-1, compressedTreeFormat.getRightChildren()[13]);

            assertEquals(-1, compressedTreeFormat.getLeftChildren()[14]);
            assertEquals(-1, compressedTreeFormat.getRightChildren()[14]);

            assertEquals(leftChild55.getNodeNumber(), compressedTreeFormat.getLeftChildren()[15]);
            assertEquals(rightChild56.getNodeNumber(), compressedTreeFormat.getRightChildren()[15]);

            assertEquals(-1, compressedTreeFormat.getLeftChildren()[16]);
            assertEquals(-1, compressedTreeFormat.getRightChildren()[16]);

            // Sixth level (children of level 5 - no child nodes, only terminal -1s)

            assertNull(leftChild55.getLeftChild());
            assertNull(leftChild55.getRightChild());

            assertEquals(-1, compressedTreeFormat.getLeftChildren()[17]);
            assertEquals(-1, compressedTreeFormat.getRightChildren()[17]);

            assertNull(rightChild56.getLeftChild());
            assertNull(rightChild56.getRightChild());

            assertEquals(-1, compressedTreeFormat.getLeftChildren()[18]);
            assertEquals(-1, compressedTreeFormat.getRightChildren()[18]);

            // There should be no more nodes
            assertEquals(19, sharedTreeSubgraph.nodesArray.size());
            assertEquals(sharedTreeSubgraph.nodesArray.size(), compressedTreeFormat.getRightChildren().length);
            assertEquals(sharedTreeSubgraph.nodesArray.size(), compressedTreeFormat.getLeftChildren().length);

            // Neither array with node numbers should contain zero. Zero is number of the root node in this case.
            // A zero in the array would mean the algorithm did not fill the whole array (0 is initial value).
            assertTrue(Arrays.binarySearch(compressedTreeFormat.getLeftChildren(), 0) < 0);
            assertTrue(Arrays.binarySearch(compressedTreeFormat.getRightChildren(), 0) < 0);

        } finally {
            Scope.exit();
            if (tfr != null) tfr.remove();
            if (model != null) model.remove();
        }

    }

}