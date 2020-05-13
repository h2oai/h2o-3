package hex.tree.isoforextended;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ExtendedIsolationForestTest extends TestUtil {

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

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            ExtendedIsolationForestModel model = eif.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);

            Frame test = Scope.track(parse_test_file("smalldata/anomaly/single_blob.csv"));
            Frame out = model.score(test);
            Scope.track_generic(out);
        } finally {
            Scope.exit();
        }
    }

    /**
     * Big data equals 65536 x 128
     */
    @Test
    @Ignore("Expensive")
    public void testBasicBigData() {
        try {
            Scope.enter();
            Frame train = Scope.track(generate_real_only(128, 65536, 0, 0xCAFFE));

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
        } finally {
            Scope.exit();
        }
    }

    @Test
    @Ignore("Not working yet")
    public void testBasicWithCategoricalData() {
        try {
            Scope.enter();
            Frame train = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_CAT)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .withDataForCol(1, ar("B", "C", "D", "E", "B", "C", "D", "E", "A", "B"))
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
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void avgPathLengTest() {
        assertEquals(10.244770920116851,
                ExtendedIsolationForest.IsolationTree.averagePathLengthOfUnsuccesfullSearch(256), 1e-5);
        assertEquals(11.583643521303037,
                ExtendedIsolationForest.IsolationTree.averagePathLengthOfUnsuccesfullSearch(500), 1e-5);
        assertEquals(1, ExtendedIsolationForest.IsolationTree.averagePathLengthOfUnsuccesfullSearch(2), 1e-5);
        assertEquals(0, ExtendedIsolationForest.IsolationTree.averagePathLengthOfUnsuccesfullSearch(1), 1e-5);
        assertEquals(0, ExtendedIsolationForest.IsolationTree.averagePathLengthOfUnsuccesfullSearch(0), 1e-5);
        assertEquals(0, ExtendedIsolationForest.IsolationTree.averagePathLengthOfUnsuccesfullSearch(-1), 1e-5);
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

            Frame resVec = new FilterLtTask(v, 0).doAll(m.types(), m).outputFrame(Key.make(), null, null);
            assertEquals(1, resVec.numRows());
            assertEquals(4, resVec.numCols());
            DKV.remove(resVec._key);
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

            Frame resVec = new FilterLtTask(v, 0).doAll(m.types(), m).outputFrame(Key.make(), null, null);
            assertEquals(3, resVec.numRows());
            assertEquals(3, resVec.numCols());
            DKV.remove(resVec._key);
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

            Frame resVec = new FilterGteTask(v, 0).doAll(m.types(), m).outputFrame(Key.make(), null, null);
            assertEquals(1, resVec.numRows());
            assertEquals(4, resVec.numCols());
            DKV.remove(resVec._key);
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

            Frame resVec = new FilterGteTask(v, 0).doAll(m.types(), m).outputFrame(Key.make(), null, null);
            assertEquals(6, resVec.numRows());
            assertEquals(3, resVec.numCols());
            DKV.remove(resVec._key);
        } finally {
            Scope.exit();
        }
    }
}
