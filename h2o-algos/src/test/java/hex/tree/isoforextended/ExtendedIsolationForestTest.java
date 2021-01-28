package hex.tree.isoforextended;

import org.apache.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.*;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.util.FrameUtils;

import java.util.Arrays;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

public class ExtendedIsolationForestTest extends TestUtil {
    private static final Logger LOG = Logger.getLogger(ExtendedIsolationForestTest.class);

    @BeforeClass()
    public static void setup() {
        stall_till_cloudsize(1);
    }

    @Test
    public void testBasic() {
        try {
            Scope.enter();
            Frame train = Scope.track(parse_test_file("smalldata/anomaly/single_blob.csv"));
            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p.extension_level = train.numCols() - 1;
            
            DKV.put(train);

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
    public void testBasicBigData() {
        try {
            Scope.enter();
            Frame train = Scope.track(generate_real_only(128, 100_000, 0, 0xCAFFE));

            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._sample_size = 20_000;
            p.extension_level = train.numCols() - 1;

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
            Frame train = Scope.track(generate_real_only(2, 65536, 0, 0xCAFFE));

            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p.extension_level = train.numCols() - 1;

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
            Frame train = Scope.track(generate_real_only(128, 500, 0, 0xCAFFE));

            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p.extension_level = train.numCols() - 1;

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
            p.extension_level = train.numCols() - 1;

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
                IsolationTree.averagePathLengthOfUnsuccessfulSearch(256), 1e-5);
        assertEquals(11.583643521303037,
                IsolationTree.averagePathLengthOfUnsuccessfulSearch(500), 1e-5);
        assertEquals(1, IsolationTree.averagePathLengthOfUnsuccessfulSearch(2), 1e-5);
        assertEquals(0, IsolationTree.averagePathLengthOfUnsuccessfulSearch(1), 1e-5);
        assertEquals(0, IsolationTree.averagePathLengthOfUnsuccessfulSearch(0), 1e-5);
        assertEquals(0, IsolationTree.averagePathLengthOfUnsuccessfulSearch(-1), 1e-5);
    }

    @Test
    public void testFilterLtTask() {
        try {
            Scope.enter();
            Frame m = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
                    .withDataForCol(0, ard(1.0, 1.0))
                    .withDataForCol(1, ard(1.0, 1.0))
                    .withDataForCol(2, ard(1.0, 1.0))
                    .withDataForCol(3, ard(1.0, 1.0))
                    .build();
            Scope.track(m);
            Vec v = Vec.makeVec(ard(2.0, -1), Vec.newKey());
            Scope.track(v);

            Frame res = new FilterLtTask(v, 0).doAll(m.types(), m).outputFrame(Key.make(), m._names, m.domains());

            assertEquals("Not correctly filtered",1, res.numRows());
            assertEquals("Column number is wrong",4, res.numCols());
            DKV.remove(res._key);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testFilterLtTaskCategoricalData() {
        try {
            Scope.enter();
            Frame m = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .withDataForCol(1, ar("B", "C", "D", "E", "B", "C", "D", "E", "A", "B"))
                    .withDataForCol(2, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .build();
            Scope.track(m);
            Vec v = Vec.makeVec(ard(2.0, 2.0, 0.0, -1.2, 5.5, -5.5, 6.5, 5.5, -5.5, 6.5), Vec.newKey());
            Scope.track(v);

            Frame res = new FilterLtTask(v, 0).doAll(m.types(), m).outputFrame(Key.make(), m._names, m.domains());

            assertTrue("Column is not categorical", res.vec(1).isCategorical());;
            assertEquals("Not correctly filtered", 3, res.numRows());
            assertEquals("Column number is wrong",3, res.numCols());
            DKV.remove(res._key);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testFilterGteTask() {
        try {
            Scope.enter();
            Frame m = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
                    .withDataForCol(0, ard(1.0, 1.0))
                    .withDataForCol(1, ard(1.0, 1.0))
                    .withDataForCol(2, ard(1.0, 1.0))
                    .withDataForCol(3, ard(1.0, 1.0))
                    .build();
            Scope.track(m);
            Vec v = Vec.makeVec(ard(2.0, -1.0), Vec.newKey());
            Scope.track(v);

            Frame res = new FilterGteTask(v, 0).doAll(m.types(), m).outputFrame(Key.make(), m._names, m.domains());

            assertEquals("Not correctly filtered",1, res.numRows());
            assertEquals("Column number is wrong",4, res.numCols());
            DKV.remove(res._key);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testFilterGteTaskCategoricalData() {
        try {
            Scope.enter();
            Frame m = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .withDataForCol(1, ar("B", "C", "D", "E", "B", "C", "D", "E", "A", "B"))
                    .withDataForCol(2, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .build();
            Scope.track(m);
            Vec v = Vec.makeVec(ard(2.0, 2.0, 0.0, -1.2, 5.5, -5.5, 6.5, 5.5, -5.5, -7.5), Vec.newKey());
            Scope.track(v);
            Frame res = new FilterGteTask(v, 0).doAll(m.types(), m).outputFrame(Key.make(), m._names, m.domains());

            assertTrue("Column is not categorical", res.vec(1).isCategorical());;
            assertEquals("Not correctly filtered",6, res.numRows());
            assertEquals("Column number is wrong",3, res.numCols());
            DKV.remove(res._key);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testIsolationTreeSmoke() {
        try {
            Scope.enter();
            Frame train = Scope.track(parse_test_file("smalldata/anomaly/single_blob.csv"));

            long start = System.currentTimeMillis();
            IsolationTree isolationTree = new IsolationTree(FrameUtils.asDoubles(train), 9, 0xBEEF, 1, 0);
            isolationTree.buildTree();
            long end = System.currentTimeMillis();
            isolationTree.logNodesNumRows();

            long time = end - start;
            if (time > 200) {
                LOG.info("Tree building took a longer than it should.");
            }

            double pathLength = isolationTree.computePathLength(new double[]{0.0, 0.0}); // Normal Point
            assertTrue("Path length should be longer. Normal point should not be isolated close to root but is pathLength = " + pathLength, pathLength >= 4);

            pathLength = isolationTree.computePathLength(new double[]{5.0, 5.0}); //Anomaly
            assertTrue("Path length should be close to 0 (Root) but is pathLength = " + pathLength, pathLength <= 4);

        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testIsolationTreeLarge() {
        try {
            Scope.enter();
            Frame train = Scope.track(generate_real_only(32, 32768, 0, 0xBEEF));
            double[] normalPoint = toNumericRow(train, 0);

            long start = System.currentTimeMillis();
            IsolationTree isolationTree = new IsolationTree(FrameUtils.asDoubles(train), 16, 0xBEEF, 127, 0);
            isolationTree.buildTree();
            long end = System.currentTimeMillis();
            isolationTree.logNodesNumRows();

            long time = end - start;
            if (time > 1000) {
                LOG.info("Tree building took a longer than it should: " + time + "ms.");
            }

            double pathLength = isolationTree.computePathLength(normalPoint);
            assertTrue("Path length should be longer. Normal point should not be isolated close to root but is pathLength = " + pathLength, pathLength >= 8);

            double[] anomaly = new double[32];
            Arrays.fill(anomaly, 10000.0);
            pathLength = isolationTree.computePathLength(anomaly); //Anomaly
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

        // Result of (data - p) * n
        // assertArrayEquals("Result is not correct", new double[]{1.5, 0.25, -1.25}, ret.getRes(), 1e-3);

        assertArrayEquals("Result is not correct", new double[]{-1.0}, split.getLeft()[0], 1e-3);
        assertArrayEquals("Result is not correct", new double[]{2.0, 1.0}, split.getRight()[0], 1e-3);
    }
}
