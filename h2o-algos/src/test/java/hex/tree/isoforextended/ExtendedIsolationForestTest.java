package hex.tree.isoforextended;

import hex.Model;
import hex.tree.isoforextended.isolationtree.CompressedIsolationTree;
import hex.tree.isoforextended.isolationtree.IsolationTree;
import org.apache.log4j.Logger;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.FrameUtils;

import java.util.Arrays;

import static org.junit.Assert.*;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class ExtendedIsolationForestTest extends TestUtil {
    private static final Logger LOG = Logger.getLogger(ExtendedIsolationForestTest.class);

    @Test
    public void testBasicTrain() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/anomaly/single_blob.csv"));
            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._extension_level = train.numCols() - 1;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            ExtendedIsolationForestModel model = eif.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBasicTrainAndScore() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/anomaly/single_blob.csv"));
            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._extension_level = train.numCols() - 1;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            ExtendedIsolationForestModel model = eif.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);

            Frame out = model.score(train);
            Scope.track_generic(out);
            assertArrayEquals(new String[]{"anomaly_score", "mean_length"}, out.names());
            assertEquals(train.numRows(), out.numRows());
        } finally {
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testBasicTrainErrorTrain0Trees() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/anomaly/single_blob.csv"));
            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 0;
            p._sample_size = 10;
            p._extension_level = 0;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            eif.trainModel().get();
        } finally {
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testBasicTrainErrorOnlyRootsDoesNotMakeSense() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/anomaly/single_blob.csv"));
            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 1;
            p._sample_size = 1;
            p._extension_level = -1;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            eif.trainModel().get();
        } finally {
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testBasicTrainErrorNegativeExtensionLevel() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/anomaly/single_blob.csv"));
            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 1;
            p._sample_size = 2;
            p._extension_level = -1;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            eif.trainModel().get();
        } finally {
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testBasicTrainErrorTooHighExtensionLevel() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/anomaly/single_blob.csv"));
            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 1;
            p._sample_size = 2;
            p._extension_level = 2;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            eif.trainModel().get();
        } finally {
            Scope.exit();
        }
    }

    @Test
    @Ignore("Expensive")
    public void testBasicBigData() {
        try {
            Scope.enter();
            Frame train = Scope.track(generateRealOnly(128, 100_000, 0, 0xCAFFE));

            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._sample_size = 30_000;
            p._extension_level = train.numCols() - 1;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            ExtendedIsolationForestModel model = eif.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);

            Frame out = model.score(train);
            Scope.track_generic(out);
            assertArrayEquals(new String[]{"anomaly_score", "mean_length"}, out.names());
            assertEquals(train.numRows(), out.numRows());
        } finally {
            Scope.exit();
        }
    }

    @Test
    @Ignore("Expensive")
    public void testBasicBigDataRows() {
        try {
            Scope.enter();
            Frame train = Scope.track(generateRealOnly(2, 65536, 0, 0xCAFFE));

            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._extension_level = train.numCols() - 1;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            ExtendedIsolationForestModel model = eif.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);

            Frame out = model.score(train);
            Scope.track_generic(out);
            assertArrayEquals(new String[]{"anomaly_score", "mean_length"}, out.names());
            assertEquals(train.numRows(), out.numRows());
        } finally {
            Scope.exit();
        }
    }

    @Test
    @Ignore("Expensive")
    public void testBasicBigDataCols() {
        try {
            Scope.enter();
            Frame train = Scope.track(generateRealOnly(128, 500, 0, 0xCAFFE));

            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._extension_level = train.numCols() - 1;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            ExtendedIsolationForestModel model = eif.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);

            Frame out = model.score(train);
            Scope.track_generic(out);
            assertArrayEquals(new String[]{"anomaly_score", "mean_length"}, out.names());
            assertEquals(train.numRows(), out.numRows());
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBasicWithCategoricalData() {
        try {
            Scope.enter();
            Frame train = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .withDataForCol(1, ar("B", "C", "D", "E", "B", "C", "D", "E", "A", "B"))
                    .withDataForCol(2, ar("BB", "CC", "DD", "EE", "BB", "CC", "DD", "EV", "AW", "BW"))
                    .withDataForCol(3, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .build();
            Scope.track(train);

            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._sample_size = 2;
            p._extension_level = 2;
            p._categorical_encoding = Model.Parameters.CategoricalEncodingScheme.OneHotExplicit;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            ExtendedIsolationForestModel model = eif.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);
        } finally {
            Scope.exit();
        }
    }

    /**
     * String data will be ignored
     */
    @Test
    public void testBasicWithStringData() {
        try {
            Scope.enter();
            Frame train = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_STR, Vec.T_NUM)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .withDataForCol(1, ar("B", "C", "D", "E", "B", "C", "D", "E", "A", "B"))
                    .withDataForCol(2, ar("BB", "CC", "DD", "EEa", "BB", "CC", "DD", "EV", "AW", "BW"))
                    .withDataForCol(3, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .build();
            Scope.track(train);

            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._sample_size = 2;
            p._extension_level = 2; // Maximum is 2 because String column will be removed

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            ExtendedIsolationForestModel model = eif.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);

            Frame out = model.score(train);
            Scope.track_generic(out);
            assertArrayEquals(new String[]{"anomaly_score", "mean_length"}, out.names());
            assertEquals(train.numRows(), out.numRows());
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void avgPathLengTest() {
        assertEquals(10.244770920116851,
                CompressedIsolationTree.averagePathLengthOfUnsuccessfulSearch(256), 1e-5);
        assertEquals(11.583643521303037,
                CompressedIsolationTree.averagePathLengthOfUnsuccessfulSearch(500), 1e-5);
        assertEquals(1, CompressedIsolationTree.averagePathLengthOfUnsuccessfulSearch(2), 0);
        assertEquals(0, CompressedIsolationTree.averagePathLengthOfUnsuccessfulSearch(1), 0);
        assertEquals(0, CompressedIsolationTree.averagePathLengthOfUnsuccessfulSearch(0), 0);
        assertEquals(0, CompressedIsolationTree.averagePathLengthOfUnsuccessfulSearch(-1), 0);
    }

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
        assertArrayEquals(new double[]{0.866, 0.0, 1.657, -0.166, 0.0}, a, 1e-3);
    }
}
