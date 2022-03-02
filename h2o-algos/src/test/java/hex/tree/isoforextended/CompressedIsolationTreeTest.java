package hex.tree.isoforextended;

import hex.genmodel.algos.isoforextended.ExtendedIsolationForestMojoModel;
import hex.tree.isoforextended.isolationtree.CompressedIsolationTree;
import hex.tree.isoforextended.isolationtree.IsolationTree;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.FrameUtils;
import water.util.RandomUtils;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertEquals;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class CompressedIsolationTreeTest extends TestUtil {
    private static final Logger LOG = Logger.getLogger(CompressedIsolationTreeTest.class);

    /**
     *                 10              
     *             /       \          
     *         2               8      
     *       /   \          /    \    
     *     1       1       7      1
     */
    private CompressedIsolationTree cmpIsolationTreeFull;

    /**
     *                 10              
     *             /       \          
     *         7               3      
     *       /   \          /    \    
     *     7       0       2        1
     */
    private CompressedIsolationTree cmpIsolationTreeOneZero;

    /**
     *                 10              
     *             /       \          
     *         7               3      
     *       /    \          /    \    
     *     7        0       2      1
     *    / \     /  \    /  \    /  \ 
     *   1   6   .   .   1    1  .   .     
     */
    private CompressedIsolationTree cmpIsolationTreeSparse;
    
    private void initIsolationTrees() {
        try {
            Scope.enter();
            Frame train = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_NUM)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .withDataForCol(1, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .build();
            Scope.track(train);

            IsolationTree isolationTree = new IsolationTree(2, 1);
            cmpIsolationTreeFull = isolationTree.buildTree(FrameUtils.asDoubles(train), 0xDECAF, 0);
            cmpIsolationTreeOneZero = isolationTree.buildTree(FrameUtils.asDoubles(train), 0xBEEF, 0);
            IsolationTree isolationTreeSparse = new IsolationTree(3, 1);
            cmpIsolationTreeSparse = isolationTreeSparse.buildTree(FrameUtils.asDoubles(train), 0xBEEF, 0);
            isolationTreeSparse.logNodesNumRows(Level.INFO);
        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void testIsolationTreeToBytesSmall() {
        try {
            Scope.enter();
            initIsolationTrees();
            byte[] cmpIsolationTreeFullBytes = cmpIsolationTreeFull.toBytes();
            byte[] cmpIsolationTreeOneZeroBytes = cmpIsolationTreeOneZero.toBytes();
            byte[] cmpIsolationTreeSparseBytes = cmpIsolationTreeSparse.toBytes();
            
            // Should isolate to each leaf in full isolation tree from left to right
            double[] row1 = new double[]{0.0, 0.0};
            double[] row2 = new double[]{1.0, 1.0};
            double[] row3 = new double[]{3.0, 3.0};
            double[] row4 = new double[]{2.0, 2.0};

            LOG.info(Arrays.toString(cmpIsolationTreeFullBytes));
            double pathLengthFromBytes = ExtendedIsolationForestMojoModel.scoreTree0(cmpIsolationTreeFullBytes, row1);
            assertEquals(cmpIsolationTreeFull.computePathLength(row1), pathLengthFromBytes, 0);
            pathLengthFromBytes = ExtendedIsolationForestMojoModel.scoreTree0(cmpIsolationTreeFullBytes, row2);
            assertEquals(cmpIsolationTreeFull.computePathLength(row2), pathLengthFromBytes, 0);
            pathLengthFromBytes = ExtendedIsolationForestMojoModel.scoreTree0(cmpIsolationTreeFullBytes, row3);
            assertEquals(cmpIsolationTreeFull.computePathLength(row3), pathLengthFromBytes, 0);
            pathLengthFromBytes = ExtendedIsolationForestMojoModel.scoreTree0(cmpIsolationTreeFullBytes, row4);
            assertEquals(cmpIsolationTreeFull.computePathLength(row4), pathLengthFromBytes, 0);

            // Should isolate to each leaf from left to right
            double[] row5 = new double[]{0.0, 0.0};
            double[] row6 = new double[]{7.0, 7.0};
            double[] row7 = new double[]{10.0, 10.0};
            double[] row8 = new double[]{6.5, 6.5};

            LOG.info(Arrays.toString(cmpIsolationTreeOneZeroBytes));
            pathLengthFromBytes = ExtendedIsolationForestMojoModel.scoreTree0(cmpIsolationTreeOneZeroBytes, row5);
            assertEquals(cmpIsolationTreeOneZero.computePathLength(row5), pathLengthFromBytes, 0);
            pathLengthFromBytes = ExtendedIsolationForestMojoModel.scoreTree0(cmpIsolationTreeOneZeroBytes, row6);
            assertEquals(cmpIsolationTreeOneZero.computePathLength(row6), pathLengthFromBytes, 0);
            pathLengthFromBytes = ExtendedIsolationForestMojoModel.scoreTree0(cmpIsolationTreeOneZeroBytes, row7);
            assertEquals(cmpIsolationTreeOneZero.computePathLength(row7), pathLengthFromBytes, 0);
            pathLengthFromBytes = ExtendedIsolationForestMojoModel.scoreTree0(cmpIsolationTreeOneZeroBytes, row8);
            assertEquals(cmpIsolationTreeOneZero.computePathLength(row8), pathLengthFromBytes, 0);

            // Should isolate to each leaf from left to right
            double[] row9 = new double[]{0.0, 0.0};
            double[] row10 = new double[]{1.0, 1.0};
            double[] row11 = new double[]{6.5, 6.5};
            double[] row12 = new double[]{8.0, 8.0};
            double[] row13 = new double[]{7.0, 7.0};
            double[] row14 = new double[]{10.0, 10.0};

            LOG.info(Arrays.toString(cmpIsolationTreeSparseBytes));
            pathLengthFromBytes = ExtendedIsolationForestMojoModel.scoreTree0(cmpIsolationTreeSparseBytes, row9);
            assertEquals(cmpIsolationTreeSparse.computePathLength(row9), pathLengthFromBytes, 0);
            pathLengthFromBytes = ExtendedIsolationForestMojoModel.scoreTree0(cmpIsolationTreeSparseBytes, row10);
            assertEquals(cmpIsolationTreeSparse.computePathLength(row10), pathLengthFromBytes, 0);
            pathLengthFromBytes = ExtendedIsolationForestMojoModel.scoreTree0(cmpIsolationTreeSparseBytes, row11);
            assertEquals(cmpIsolationTreeSparse.computePathLength(row11), pathLengthFromBytes, 0);
            pathLengthFromBytes = ExtendedIsolationForestMojoModel.scoreTree0(cmpIsolationTreeSparseBytes, row12);
            assertEquals(cmpIsolationTreeSparse.computePathLength(row12), pathLengthFromBytes, 0);
            pathLengthFromBytes = ExtendedIsolationForestMojoModel.scoreTree0(cmpIsolationTreeSparseBytes, row13);
            assertEquals(cmpIsolationTreeSparse.computePathLength(row13), pathLengthFromBytes, 0);
            pathLengthFromBytes = ExtendedIsolationForestMojoModel.scoreTree0(cmpIsolationTreeSparseBytes, row14);
            assertEquals(cmpIsolationTreeSparse.computePathLength(row14), pathLengthFromBytes, 0);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testIsolationTreeMediumRandomAttack() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/anomaly/single_blob.csv"));
            Scope.track(train);

            IsolationTree isolationTree = new IsolationTree(8, 1);
            CompressedIsolationTree compressedIsolationTree = isolationTree.buildTree(FrameUtils.asDoubles(train), 0xBEEF, 0);
            isolationTree.logNodesNumRows(Level.INFO);

            double[] row = new double[2];
            Random random = RandomUtils.getRNG(0xBEEF);
            for (int i = 0; i < 100; i++) {
                row[0] = random.nextDouble();
                row[1] = random.nextDouble();
                LOG.debug(Arrays.toString(row));
                double pathLengthFromBytes = ExtendedIsolationForestMojoModel.scoreTree0(compressedIsolationTree.toBytes(), row);
                double pathLengthClass = compressedIsolationTree.computePathLength(row);
                assertEquals(pathLengthClass, pathLengthFromBytes, 0);
                
            }
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testIsolationTreeLargeRandomAttack() {
        try {
            Scope.enter();
            Frame train = Scope.track(generateRealOnly(32, 32768, 0, 0xBEEF));
            Scope.track(train);

            IsolationTree isolationTree = new IsolationTree(16, train.numCols() - 1);
            CompressedIsolationTree compressedIsolationTree = isolationTree.buildTree(FrameUtils.asDoubles(train), 0xBEEF, 0);
            isolationTree.logNodesNumRows(Level.DEBUG);

            double[] row = new double[train.numCols() - 1];
            Random random = RandomUtils.getRNG(0xBEEF);
            for (int i = 0; i < 100; i++) {
                for (int j = 0; j < row.length; j++) {
                    row[j] = random.nextDouble();
                }
                LOG.debug(Arrays.toString(row));
                double pathLengthFromBytes = ExtendedIsolationForestMojoModel.scoreTree0(compressedIsolationTree.toBytes(), row);
                double pathLengthClass = compressedIsolationTree.computePathLength(row);
                assertEquals(pathLengthClass, pathLengthFromBytes, 0);
            }
        } finally {
            Scope.exit();
        }
    }    
}
