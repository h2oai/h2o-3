package hex.tree.isoforfaircut;

import hex.tree.isoforfaircut.isolationtree.CompressedIsolationTree;
import hex.tree.isoforfaircut.isolationtree.IsolationTree;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.FrameUtils;

import java.util.Arrays;

import static org.junit.Assert.*;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class IsolationTreeTest extends TestUtil {
    private static final Logger LOG = Logger.getLogger(IsolationTreeTest.class);

    @Test
    public void testIsolationTreeSmoke() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/anomaly/single_blob.csv"));

            long start = System.currentTimeMillis();
            IsolationTree isolationTree = new IsolationTree(9, 1, 1);
            CompressedIsolationTree compressedIsolationTree = isolationTree.buildTree(FrameUtils.asDoubles(train), 0xBEEF, 0);
            long end = System.currentTimeMillis();
            isolationTree.logNodesNumRows(Level.INFO);

            long time = end - start;
            if (time > 200) {
                LOG.info("Tree building took a longer than it should.");
            }

            double pathLength = compressedIsolationTree.computePathLength(new double[]{0.0, 0.0}); // Normal Point
            assertTrue("Path length should be longer. Normal point should not be isolated close to root but is pathLength = " + pathLength, pathLength >= 7);
            pathLength = compressedIsolationTree.computePathLength(new double[]{-10.0, -10.0}); //Anomaly
            assertTrue("Path length should be close to 0 (Root) but is pathLength = " + pathLength, pathLength <= 7);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testFairCutTreeSplit() {
        double[][] data = new double[][]{{2.0, 1.0, -1.0}, {5.0, 6.0, -6.0}, {6.0, 0.0, -8.0}};
        double[] normalVector = new double[]{1.0, 4.0, -1.0};
        
        // no standardization
        double[] zeros = new double[]{0, 0, 0};
        double[] ones = new double[]{1, 1, 1};

        double[] z = new double[3];

        // project data
        for (int j = 0; j < data.length; j++) {
            for (int i = 0; i < 3; i++) {
                z[j] += data[i][j] * normalVector[i];
            }
        }

        IsolationTree.SplitCriteria splitCriteria =
                new IsolationTree.SplitCriteria(normalVector, 0, zeros, ones, 1, z);

        IsolationTree.FilteredData split = IsolationTree.split(data, splitCriteria);

        assertArrayEquals("Result is not correct", new double[]{-1.0}, split.getLeft()[0], 1e-3);
        assertArrayEquals("Result is not correct", new double[]{2.0, 1.0}, split.getRight()[0], 1e-3);
    }
    
    @Test
    public void testIsolationTreeStatsSmokeTreeLimitEqualToDataSize() {
        double[][] data = new double[][]{
                {5, 0.25159, 0.98433, 0.96306, -0.99260, -0.26453, -0.20910, 1.61677, -0.83930, -0.63632, 0.63247, 0.42071, -0.06717, -0.23723, -0.19529, 0.92324},
                {3, 1.49243, 1.24084, -0.93344, 1.54171, 0.32838, -1.12037, -0.46590, 0.74632, 1.13758, 0.93562, 1.17489, 0.42124, 1.42660, -0.11774, 0.46041}
        };
        IsolationTree isolationTree = new IsolationTree(4, 1, 1);
        isolationTree.buildTree(data, 0xF00D, 0);
        isolationTree.logNodesNumRows(Level.INFO);
        assertEquals("Check if isolation tree splitting correctly and adjust the number in case of inner splitting change", 6, isolationTree.getIsolatedPoints());
        assertEquals("Check if isolation tree splitting correctly and adjust the number in case of inner splitting change", 10, isolationTree.getNotIsolatedPoints());
        assertEquals("Check if isolation tree splitting correctly and adjust the number in case of inner splitting change", 0, isolationTree.getZeroSplits());
        assertEquals("Check if isolation tree splitting correctly and adjust the number in case of inner splitting change", 9, isolationTree.getLeaves());
        assertEquals("Depth should almost always be equal to height limit", 4, isolationTree.getDepth());

        assertEquals("Some point are lost in the process: ", data[0].length, isolationTree.getIsolatedPoints() + isolationTree.getNotIsolatedPoints());
    }

    @Test
    public void testIsolationTreeStatsSmokeTreeLimitGreaterThanDataSize() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/anomaly/single_blob.csv"));
            IsolationTree isolationTree = new IsolationTree(9, 1, 1);
            isolationTree.buildTree(FrameUtils.asDoubles(train), 0xF00D, 0);
            isolationTree.logNodesNumRows(Level.INFO);
            assertEquals("Check if isolation tree splitting correctly and adjust the number in case of inner splitting change", 169, isolationTree.getIsolatedPoints());
            assertEquals("Check if isolation tree splitting correctly and adjust the number in case of inner splitting change", 331, isolationTree.getNotIsolatedPoints());
            assertEquals("Check if isolation tree splitting correctly and adjust the number in case of inner splitting change", 0, isolationTree.getZeroSplits());
            assertEquals("Check if isolation tree splitting correctly and adjust the number in case of inner splitting change", 301, isolationTree.getLeaves());
            assertEquals("Depth should almost always be equal to height limit", 9, isolationTree.getDepth());

            assertEquals("Some points are lost in the process: ", train.numRows(), isolationTree.getIsolatedPoints() + isolationTree.getNotIsolatedPoints());
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testIsolationTreeStatsSmokeTreeLimitLessThanDataSize() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/anomaly/single_blob.csv"));
            IsolationTree isolationTree = new IsolationTree(7, 1, 1);
            isolationTree.buildTree(FrameUtils.asDoubles(train), 0xF00D, 0);
            isolationTree.logNodesNumRows(Level.INFO);
            assertEquals("Check if isolation tree splitting correctly and adjust the number in case of inner splitting change", 26, isolationTree.getIsolatedPoints());
            assertEquals("Check if isolation tree splitting correctly and adjust the number in case of inner splitting change", 474, isolationTree.getNotIsolatedPoints());
            assertEquals("Check if isolation tree splitting correctly and adjust the number in case of inner splitting change", 0, isolationTree.getZeroSplits());
            assertEquals("Check if isolation tree splitting correctly and adjust the number in case of inner splitting change", 119, isolationTree.getLeaves());
            assertEquals("Depth should almost always be equal to height limit", 7, isolationTree.getDepth());

            assertEquals("Some points are lost in the process: ", train.numRows(), isolationTree.getIsolatedPoints() + isolationTree.getNotIsolatedPoints());
        } finally {
            Scope.exit();
        }
    }
}
