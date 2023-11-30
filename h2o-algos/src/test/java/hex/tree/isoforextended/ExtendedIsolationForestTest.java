package hex.tree.isoforextended;

import hex.Model;
import hex.tree.isofor.ModelMetricsAnomaly;
import hex.tree.isoforextended.isolationtree.CompressedIsolationTree;
import org.apache.log4j.Logger;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.MRTask;
import water.Scope;
import water.TestUtil;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static hex.genmodel.algos.isoforextended.ExtendedIsolationForestMojoModel.averagePathLengthOfUnsuccessfulSearch;
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
            assertNotNull(model._output._model_summary);
            assertNull("No scoring history by default", model._output._scoring_history);
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
            Scope.track(out);
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
            assertNotNull(model);
            Scope.track_generic(model);

            Frame out = model.score(train);
            Scope.track(out);
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
            Scope.track(out);
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

    @Test
    public void testFinalScoringIsCorrect() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/anomaly/single_blob.csv"));
            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._extension_level = train.numCols() - 1;
            p._disable_training_metrics = false;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            ExtendedIsolationForestModel model = eif.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);

            assertTrue(model._output._training_metrics.isForFrame(train));
            assertTrue(model._output._training_metrics instanceof ModelMetricsAnomaly);
            ModelMetricsAnomaly modelMetricsAnomaly = (ModelMetricsAnomaly) model._output._training_metrics;

            Frame out = model.score(train);
            Scope.track(out);

            MeanScoreTask meanScoreTask = new MeanScoreTask().doAll(out);
            double meanAnomalyScore = meanScoreTask.totalAnomalyScore / out.numRows();
            double meanMeanLength = meanScoreTask.totalMeanLength / out.numRows();

            assertEquals("Unexpected final mean anomaly score",
                    meanAnomalyScore, modelMetricsAnomaly._mean_normalized_score, 1e-3);
            assertEquals("Unexpected final mean length",
                    meanMeanLength, modelMetricsAnomaly._mean_score, 1e-3);
        } finally {
            Scope.exit();
        }
    }

    private class MeanScoreTask extends MRTask<MeanScoreTask> {
        private double totalAnomalyScore = 0;
        private double totalMeanLength = 0;

        @Override
        public void map(Chunk[] cs) {
            for (int row = 0; row < cs[0]._len; row++) {
                totalAnomalyScore += cs[0].atd(row);
                totalMeanLength += cs[1].atd(row);
            }
        }

        @Override
        public void reduce(MeanScoreTask other) {
            totalAnomalyScore += other.totalAnomalyScore;
            totalMeanLength += other.totalMeanLength;
        }
    }

    @Test
    public void testScoreEachIteration() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/anomaly/single_blob.csv"));
            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 5;
            p._score_each_iteration = true;
            p._extension_level = train.numCols() - 1;
            p._disable_training_metrics = false;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            ExtendedIsolationForestModel model = eif.trainModel().get();
            assertNotNull(model);
            Scope.track_generic(model);

            LOG.info(model._output._scoring_history);

            assertEquals("Number of rows is not correct", 6, model._output._scoring_history.getRowDim());
            for (int treeNum = 0; treeNum <= p._ntrees; treeNum++) {
                assertEquals("Tree number is not correct", treeNum, model._output._scoring_history.get(treeNum, 2));
            }
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testScoreTreeIntervalSmoke() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/anomaly/single_blob.csv"));
            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 5;
            p._score_tree_interval = 2;
            p._extension_level = train.numCols() - 1;
            p._disable_training_metrics = false;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            ExtendedIsolationForestModel model = eif.trainModel().get();
            assertNotNull(model);
            Scope.track_generic(model);

            LOG.info(model._output._scoring_history);

            assertEquals("Number of rows is not correct", 4, model._output._scoring_history.getRowDim());
            assertEquals("Tree number is not correct", 0, model._output._scoring_history.get(0, 2));
            assertEquals("Tree number is not correct", 2, model._output._scoring_history.get(1, 2));
            assertEquals("Tree number is not correct", 4, model._output._scoring_history.get(2, 2));
            assertEquals("Tree number is not correct", 5, model._output._scoring_history.get(3, 2));
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testScoreTreeInterval() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/anomaly/single_blob.csv"));
            ExtendedIsolationForestModel.ExtendedIsolationForestParameters p =
                    new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p._score_tree_interval = 30;
            p._extension_level = train.numCols() - 1;
            p._disable_training_metrics = false;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            ExtendedIsolationForestModel model = eif.trainModel().get();
            assertNotNull(model);
            Scope.track_generic(model);

            LOG.info(model._output._scoring_history);

            p._ntrees = 30;
            p._score_tree_interval = 0;
            eif = new ExtendedIsolationForest(p);
            ExtendedIsolationForestModel model30 = eif.trainModel().get();
            assertNotNull(model30);
            Scope.track_generic(model30);
            ModelMetricsAnomaly modelMetricsAnomaly30 = (ModelMetricsAnomaly) model30._output._training_metrics;
            p._ntrees = 60;
            eif = new ExtendedIsolationForest(p);
            ExtendedIsolationForestModel model60 = eif.trainModel().get();
            assertNotNull(model60);
            Scope.track_generic(model60);
            ModelMetricsAnomaly modelMetricsAnomaly60 = (ModelMetricsAnomaly) model60._output._training_metrics;
            p._ntrees = 90;
            eif = new ExtendedIsolationForest(p);
            ExtendedIsolationForestModel model90 = eif.trainModel().get();
            assertNotNull(model90);
            Scope.track_generic(model90);
            ModelMetricsAnomaly modelMetricsAnomaly90 = (ModelMetricsAnomaly) model90._output._training_metrics;

            assertEquals("Partial score is not correct", modelMetricsAnomaly30._mean_score, model._output._scoring_history.get(1, 3));
            assertEquals("Partial score is not correct", modelMetricsAnomaly30._mean_normalized_score, model._output._scoring_history.get(1, 4));
            assertEquals("Partial score is not correct", modelMetricsAnomaly60._mean_score, model._output._scoring_history.get(2, 3));
            assertEquals("Partial score is not correct", modelMetricsAnomaly60._mean_normalized_score, model._output._scoring_history.get(2, 4));
            assertEquals("Partial score is not correct", modelMetricsAnomaly90._mean_score, model._output._scoring_history.get(3, 3));
            assertEquals("Partial score is not correct", modelMetricsAnomaly90._mean_normalized_score, model._output._scoring_history.get(3, 4));
        } finally {
            Scope.exit();
        }
    }
}
