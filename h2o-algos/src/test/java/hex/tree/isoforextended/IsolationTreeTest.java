package hex.tree.isoforextended;

import hex.tree.isoforextended.isolationtree.CompressedIsolationTree;
import hex.tree.isoforextended.isolationtree.IsolationTree;
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
            IsolationTree isolationTree = new IsolationTree(9, 1);
            CompressedIsolationTree compressedIsolationTree = isolationTree.buildTree(FrameUtils.asDoubles(train), 0xBEEF, 0);
            long end = System.currentTimeMillis();
            isolationTree.logNodesNumRows();

            long time = end - start;
            if (time > 200) {
                LOG.info("Tree building took a longer than it should.");
            }

            double pathLength = compressedIsolationTree.computePathLength(new double[]{0.0, 0.0}); // Normal Point
            assertTrue("Path length should be longer. Normal point should not be isolated close to root but is pathLength = " + pathLength, pathLength >= 4);

            pathLength = compressedIsolationTree.computePathLength(new double[]{5.0, 5.0}); //Anomaly
            assertTrue("Path length should be close to 0 (Root) but is pathLength = " + pathLength, pathLength <= 4);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testIsolationTreeLarge() {
        try {
            Scope.enter();
            Frame train = Scope.track(generateRealOnly(32, 32768, 0, 0xBEEF));
            double[] normalPoint = toNumericRow(train, 0);

            long start = System.currentTimeMillis();
            IsolationTree isolationTree = new IsolationTree(16, 31);
            CompressedIsolationTree compressedIsolationTree = isolationTree.buildTree(FrameUtils.asDoubles(train), 0xBEEF, 0);
            long end = System.currentTimeMillis();
            isolationTree.logNodesNumRows();

            long time = end - start;
            if (time > 1000) {
                LOG.info("Tree building took a longer than it should: " + time + "ms.");
            }

            double pathLength = compressedIsolationTree.computePathLength(normalPoint);
            assertTrue("Path length should be longer. Normal point should not be isolated close to root but is pathLength = " + pathLength, pathLength >= 8);

            double[] anomaly = new double[32];
            Arrays.fill(anomaly, 10000.0);
            pathLength = compressedIsolationTree.computePathLength(anomaly); //Anomaly
            assertTrue("Path length should be close to 0 (Root) but is pathLength = " + pathLength, pathLength <= 8);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testExtendedIsolationTreeSplit() {
        double[][] data = new double[][]{{2.0, 1.0, -1.0}, {5.0, 6.0, -6.0}, {6.0, 0.0, -8.0}};
        double[] p = new double[]{1.0, 4.0, -1.0};
        double[] n = new double[]{-0.25, 0.0, 0.25};

        IsolationTree.FilteredData split = IsolationTree.extendedIsolationForestSplit(data, p, n);

        // Result of (data - p) * n = (1.5, 0.25, -1.25)^T

        assertArrayEquals("Result is not correct", new double[]{-1.0}, split.getLeft()[0], 1e-3);
        assertArrayEquals("Result is not correct", new double[]{2.0, 1.0}, split.getRight()[0], 1e-3);
    }

    @Test
    public void testGaussianVector() {
        double[] a = IsolationTree.gaussianVector(5,  2, 0xCAFFE);
        int numOfZeros = 0;
        for (double v : a) {
            if (v == 0) {
                numOfZeros++;
            }
        }
        assertEquals("Array should contain two zeros: " + Arrays.toString(a),2, numOfZeros);
        assertArrayEquals("Arrays are different: " + Arrays.toString(a), new double[]{0.866, 0.0, 1.657, -0.166, 0.0}, a, 1e-3);
    }
}
