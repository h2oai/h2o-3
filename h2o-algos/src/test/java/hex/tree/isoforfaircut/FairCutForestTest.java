package hex.tree.isoforfaircut;

import hex.Model;
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

import static hex.genmodel.algos.isoforfaircut.FairCutForestMojoModel.averagePathLengthOfUnsuccessfulSearch;
import static org.junit.Assert.*;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class FairCutForestTest extends TestUtil {
    private static final Logger LOG = Logger.getLogger(FairCutForestTest.class);

    @Test
    public void testBasicTrain() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/anomaly/single_blob.csv"));
            FairCutForestModel.FairCutForestParameters p =
                    new FairCutForestModel.FairCutForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._extension_level = train.numCols() - 1;
            p._k_planes =  1;

            FairCutForest fcf = new FairCutForest(p);
            FairCutForestModel model = fcf.trainModel().get();
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
            FairCutForestModel.FairCutForestParameters p =
                    new FairCutForestModel.FairCutForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._extension_level = train.numCols() - 1;
            p._k_planes =  1;

            FairCutForest fcf = new FairCutForest(p);
            FairCutForestModel model = fcf.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);

            Frame out = model.score(train);
            Scope.track_generic(out);
            assertArrayEquals(new String[]{"anomaly_score", "mean_length"}, out.names());
            assertEquals(train.numRows(), out.numRows());

            model.testJavaScoring(train, out, 1e-3);
        } finally {
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testBasicTrainErrorTrain0Trees() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/anomaly/single_blob.csv"));
            FairCutForestModel.FairCutForestParameters p =
                    new FairCutForestModel.FairCutForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 0;
            p._sample_size = 10;
            p._extension_level = 0;
            p._k_planes =  1;

            FairCutForest fcf = new FairCutForest(p);
            fcf.trainModel().get();
        } finally {
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testBasicTrainErrorOnlyRootsDoesNotMakeSense() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/anomaly/single_blob.csv"));
            FairCutForestModel.FairCutForestParameters p =
                    new FairCutForestModel.FairCutForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 1;
            p._sample_size = 1;
            p._extension_level = -1;
            p._k_planes =  1;

            FairCutForest fcf = new FairCutForest(p);
            fcf.trainModel().get();
        } finally {
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testBasicTrainErrorNegativeExtensionLevel() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/anomaly/single_blob.csv"));
            FairCutForestModel.FairCutForestParameters p =
                    new FairCutForestModel.FairCutForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 1;
            p._sample_size = 2;
            p._extension_level = -1;
            p._k_planes =  1;

            FairCutForest fcf = new FairCutForest(p);
            fcf.trainModel().get();
        } finally {
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testBasicTrainErrorTooHighExtensionLevel() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/anomaly/single_blob.csv"));
            FairCutForestModel.FairCutForestParameters p =
                    new FairCutForestModel.FairCutForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 1;
            p._sample_size = 2;
            p._extension_level = 2;
            p._k_planes =  1;

            FairCutForest fcf = new FairCutForest(p);
            fcf.trainModel().get();
        } finally {
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testBasicTrainErrorTooFewHyperplanes() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/anomaly/single_blob.csv"));
            FairCutForestModel.FairCutForestParameters p =
                    new FairCutForestModel.FairCutForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 1;
            p._sample_size = 2;
            p._extension_level = 2;
            p._k_planes =  0;

            FairCutForest fcf = new FairCutForest(p);
            fcf.trainModel().get();
        } finally {
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testBasicTrainErrorTooManyHyperplanes() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/anomaly/single_blob.csv"));
            FairCutForestModel.FairCutForestParameters p =
                    new FairCutForestModel.FairCutForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 1;
            p._sample_size = 2;
            p._extension_level = 2;
            p._k_planes =  101;

            FairCutForest fcf = new FairCutForest(p);
            fcf.trainModel().get();
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

            FairCutForestModel.FairCutForestParameters p =
                    new FairCutForestModel.FairCutForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._sample_size = 30_000;
            p._extension_level = train.numCols() - 1;
            p._k_planes =  1;

            FairCutForest fcf = new FairCutForest(p);
            FairCutForestModel model = fcf.trainModel().get();
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

            FairCutForestModel.FairCutForestParameters p =
                    new FairCutForestModel.FairCutForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._extension_level = train.numCols() - 1;
            p._k_planes =  1;

            FairCutForest fcf = new FairCutForest(p);
            FairCutForestModel model = fcf.trainModel().get();
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

            FairCutForestModel.FairCutForestParameters p =
                    new FairCutForestModel.FairCutForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._extension_level = train.numCols() - 1;
            p._k_planes =  1;

            FairCutForest fcf = new FairCutForest(p);
            FairCutForestModel model = fcf.trainModel().get();
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

            FairCutForestModel.FairCutForestParameters p =
                    new FairCutForestModel.FairCutForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._sample_size = 3;
            p._extension_level = 3;
            p._k_planes =  1;
            p._categorical_encoding = Model.Parameters.CategoricalEncodingScheme.OneHotExplicit;

            FairCutForest fcf = new FairCutForest(p);
            FairCutForestModel model = fcf.trainModel().get();
            assertNotNull(model);
            Scope.track_generic(model);

            Frame out = model.score(train);
            Scope.track_generic(out);
            assertArrayEquals(new String[]{"anomaly_score", "mean_length"}, out.names());
            assertEquals(train.numRows(), out.numRows());
            model.testJavaScoring(train, out, 1e-3);
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

            FairCutForestModel.FairCutForestParameters p =
                    new FairCutForestModel.FairCutForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._sample_size = 2;
            p._extension_level = 2; // Maximum is 2 because String column will be removed
            p._k_planes =  1;

            FairCutForest fcf = new FairCutForest(p);
            FairCutForestModel model = fcf.trainModel().get();
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
                averagePathLengthOfUnsuccessfulSearch(256), 1e-5);
        assertEquals(11.583643521303037,
                averagePathLengthOfUnsuccessfulSearch(500), 1e-5);
        assertEquals(1, averagePathLengthOfUnsuccessfulSearch(2), 0);
        assertEquals(0, averagePathLengthOfUnsuccessfulSearch(1), 0);
        assertEquals(0, averagePathLengthOfUnsuccessfulSearch(0), 0);
        assertEquals(0, averagePathLengthOfUnsuccessfulSearch(-1), 0);
    }
}
