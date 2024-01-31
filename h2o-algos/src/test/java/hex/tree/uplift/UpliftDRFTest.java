package hex.tree.uplift;

import hex.Model;
import hex.ScoreKeeper;
import hex.genmodel.MojoModel;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.prediction.UpliftBinomialModelPrediction;
import hex.genmodel.utils.DistributionFamily;
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

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.*;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class UpliftDRFTest extends TestUtil {

    @Test
    public void testBasicTrain() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/uplift/criteo_uplift_13k.csv"));
            train.toCategoricalCol("treatment");
            train.toCategoricalCol("conversion");
            UpliftDRFModel.UpliftDRFParameters p = new UpliftDRFModel.UpliftDRFParameters();
            p._train = train._key;
            p._ignored_columns = new String[]{"visit", "exposure"};
            p._treatment_column = "treatment";
            p._response_column = "conversion";
            p._seed = 0xDECAF;
            p._ntrees = 3;

            UpliftDRF udrf = new UpliftDRF(p);
            UpliftDRFModel model = udrf.trainModel().get();
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
            Frame train = Scope.track(parseTestFile("smalldata/uplift/criteo_uplift_13k.csv"));
            train.toCategoricalCol("treatment");
            train.toCategoricalCol("conversion");
            UpliftDRFModel.UpliftDRFParameters p = new UpliftDRFModel.UpliftDRFParameters();
            p._train = train._key;
            p._ignored_columns = new String[]{"visit", "exposure"};
            p._treatment_column = "treatment";
            p._response_column = "conversion";
            p._seed = 0xDECAF;
            p._nbins = 10;
            p._ntrees = 3;

            UpliftDRF udrf = new UpliftDRF(p);
            UpliftDRFModel model = udrf.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);

            Frame out = model.score(train);
            Scope.track(out);
            assertArrayEquals(new String[]{"uplift_predict", "p_y1_with_treatment", "p_y1_without_treatment"}, out.names());
            assertEquals(train.numRows(), out.numRows());

            assertNull(model._output._varimp); // not supported yet
        } finally {
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testBasicTrainErrorMissingTreatmentColumn() {
        try {
            Scope.enter();
            Frame train = generateFrame();
            train.remove("treatment");
            train.toCategoricalCol("conversion");
            UpliftDRFModel.UpliftDRFParameters p = new UpliftDRFModel.UpliftDRFParameters();
            p._train = train._key;
            p._treatment_column = "treatment";
            p._response_column = "conversion";

            UpliftDRF udrf = new UpliftDRF(p);
            udrf.trainModel().get();
        } finally {
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testBasicTrainErrorDoNotSupportMultipleTreatment() {
        try {
            Scope.enter();
            Frame train = generateFrame();
            UpliftDRFModel.UpliftDRFParameters p = new UpliftDRFModel.UpliftDRFParameters();
            p._train = train._key;
            p._treatment_column = "treatment2";
            p._response_column = "conversion";

            UpliftDRF udrf = new UpliftDRF(p);
            udrf.trainModel().get();
        } finally {
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testBasicTrainErrorTreatmentMustBeCategorical() {
        try {
            Scope.enter();
            Frame train = generateFrame();
            UpliftDRFModel.UpliftDRFParameters p = new UpliftDRFModel.UpliftDRFParameters();
            p._train = train._key;
            p._treatment_column = "C0";
            p._response_column = "conversion";

            UpliftDRF udrf = new UpliftDRF(p);
            udrf.trainModel().get();
        } finally {
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testBasicTrainErrorDoNotSupportMultinomialResponseColumn() {
        try {
            Scope.enter();
            Frame train = generateFrame();
            UpliftDRFModel.UpliftDRFParameters p = new UpliftDRFModel.UpliftDRFParameters();
            p._train = train._key;
            p._treatment_column = "treatment";
            p._response_column = "C1";

            UpliftDRF udrf = new UpliftDRF(p);
            udrf.trainModel().get();
        } finally {
            Scope.exit();
        }
    }


    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testBasicTrainErrorDoNotSupportNfolds() {
        try {
            Scope.enter();
            Frame train = generateFrame();
            UpliftDRFModel.UpliftDRFParameters p = new UpliftDRFModel.UpliftDRFParameters();
            p._train = train._key;
            p._treatment_column = "treatment";
            p._response_column = "conversion";
            p._nfolds = 10;

            UpliftDRF udrf = new UpliftDRF(p);
            udrf.trainModel().get();
        } finally {
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testBasicTrainErrorDoNotSupportFoldColumn() {
        try {
            Scope.enter();
            Frame train = generateFrame();
            UpliftDRFModel.UpliftDRFParameters p = new UpliftDRFModel.UpliftDRFParameters();
            p._train = train._key;
            p._treatment_column = "treatment";
            p._response_column = "conversion";
            p._fold_column = "C0";

            UpliftDRF udrf = new UpliftDRF(p);
            udrf.trainModel().get();
        } finally {
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testBasicTrainErrorDoNotSupportOffset() {
        try {
            Scope.enter();
            Frame train = generateFrame();
            UpliftDRFModel.UpliftDRFParameters p = new UpliftDRFModel.UpliftDRFParameters();
            p._train = train._key;
            p._treatment_column = "treatment";
            p._response_column = "conversion";
            p._offset_column = "C1";

            UpliftDRF udrf = new UpliftDRF(p);
            udrf.trainModel().get();
        } finally {
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testBasicTrainErrorDoNotSupportDistribution() {
        try {
            Scope.enter();
            Frame train = generateFrame();
            UpliftDRFModel.UpliftDRFParameters p = new UpliftDRFModel.UpliftDRFParameters();
            p._train = train._key;
            p._treatment_column = "treatment";
            p._response_column = "conversion";
            p._distribution = DistributionFamily.multinomial;

            UpliftDRF udrf = new UpliftDRF(p);
            udrf.trainModel().get();
        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void testBasicTrainSupportEarlyStoppingAUUC() {
        try {
            Scope.enter();
            Frame train = generateFrame();
            int ntrees = 42;
            UpliftDRFModel.UpliftDRFParameters p = new UpliftDRFModel.UpliftDRFParameters();
            p._train = train._key;
            p._treatment_column = "treatment";
            p._response_column = "conversion";
            p._stopping_metric = ScoreKeeper.StoppingMetric.AUUC;
            p._stopping_rounds = 2;
            p._ntrees = ntrees;
            p._score_each_iteration = true;

            UpliftDRF udrf = new UpliftDRF(p);
            UpliftDRFModel model = udrf.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);
            assertTrue(model._output._treeStats._num_trees < ntrees);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBasicTrainSupportEarlyStoppingATE() {
        try {
            Scope.enter();
            Frame train = generateFrame();
            int ntrees = 42;
            UpliftDRFModel.UpliftDRFParameters p = new UpliftDRFModel.UpliftDRFParameters();
            p._train = train._key;
            p._treatment_column = "treatment";
            p._response_column = "conversion";
            p._stopping_metric = ScoreKeeper.StoppingMetric.ATE;
            p._stopping_rounds = 2;
            p._ntrees = ntrees;
            p._score_each_iteration = true;

            UpliftDRF udrf = new UpliftDRF(p);
            UpliftDRFModel model = udrf.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);
            assertTrue(model._output._treeStats._num_trees < ntrees);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBasicTrainSupportEarlyStoppingATT() {
        try {
            Scope.enter();
            Frame train = generateFrame();
            int ntrees = 42;
            UpliftDRFModel.UpliftDRFParameters p = new UpliftDRFModel.UpliftDRFParameters();
            p._train = train._key;
            p._treatment_column = "treatment";
            p._response_column = "conversion";
            p._stopping_metric = ScoreKeeper.StoppingMetric.ATT;
            p._stopping_rounds = 2;
            p._ntrees = ntrees;
            p._score_each_iteration = true;

            UpliftDRF udrf = new UpliftDRF(p);
            UpliftDRFModel model = udrf.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);
            assertTrue(model._output._treeStats._num_trees < ntrees);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBasicTrainSupportEarlyStoppingATC() {
        try {
            Scope.enter();
            Frame train = generateFrame();
            int ntrees = 42;
            UpliftDRFModel.UpliftDRFParameters p = new UpliftDRFModel.UpliftDRFParameters();
            p._train = train._key;
            p._treatment_column = "treatment";
            p._response_column = "conversion";
            p._stopping_metric = ScoreKeeper.StoppingMetric.ATC;
            p._stopping_rounds = 2;
            p._ntrees = ntrees;
            p._score_each_iteration = true;

            UpliftDRF udrf = new UpliftDRF(p);
            UpliftDRFModel model = udrf.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);
            assertTrue(model._output._treeStats._num_trees < ntrees);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBasicTrainSupportEarlyStoppingQini() {
        try {
            Scope.enter();
            Frame train = generateFrame();
            int ntrees = 42;
            UpliftDRFModel.UpliftDRFParameters p = new UpliftDRFModel.UpliftDRFParameters();
            p._train = train._key;
            p._treatment_column = "treatment";
            p._response_column = "conversion";
            p._stopping_metric = ScoreKeeper.StoppingMetric.qini;
            p._stopping_rounds = 2;
            p._ntrees = ntrees;
            p._score_each_iteration = true;

            UpliftDRF udrf = new UpliftDRF(p);
            UpliftDRFModel model = udrf.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);
            assertTrue(model._output._treeStats._num_trees < ntrees);
        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void testMaxDepthZero() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/uplift/criteo_uplift_13k.csv"));
            train.toCategoricalCol("treatment");
            train.toCategoricalCol("conversion");
            UpliftDRFModel.UpliftDRFParameters p = new UpliftDRFModel.UpliftDRFParameters();
            p._train = train._key;
            p._ignored_columns = new String[]{"visit", "exposure"};
            p._treatment_column = "treatment";
            p._response_column = "conversion";
            p._seed = 0xDECAF;
            p._max_depth = 0;
            p._score_each_iteration = true;
            p._ntrees = 3;

            UpliftDRF udrf = new UpliftDRF(p);
            UpliftDRFModel model = udrf.trainModel().get();
            Scope.track_generic(model);
            assertNotNull(model);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testPredictCorrectOutput() {
        try {
            Scope.enter();
            Frame train = generateFrame();
            train.toCategoricalCol("treatment");
            train.toCategoricalCol("conversion");
            UpliftDRFModel.UpliftDRFParameters p = new UpliftDRFModel.UpliftDRFParameters();
            p._train = train._key;
            p._response_column = "conversion";
            p._treatment_column = "treatment";
            p._ntrees = 2;

            UpliftDRF udrf = new UpliftDRF(p);
            UpliftDRFModel model = udrf.trainModel().get();
            Scope.track_generic(model);
            Frame preds = model.score(train);
            Scope.track(preds);
            assertArrayEquals("Prediction frame column names are incorrect.",
                    preds.names(), new String[]{"uplift_predict", "p_y1_with_treatment", "p_y1_without_treatment"});
        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void testMojo() {
        try {
            Scope.enter();
            Frame train = generateFrame();
            train.toCategoricalCol("treatment");
            train.toCategoricalCol("conversion");
            UpliftDRFModel.UpliftDRFParameters p = new UpliftDRFModel.UpliftDRFParameters();
            p._train = train._key;
            p._response_column = "conversion";
            p._treatment_column = "treatment";
            p._ntrees = 4;

            UpliftDRF udrf = new UpliftDRF(p);
            UpliftDRFModel model = udrf.trainModel().get();
            Scope.track_generic(model);
            Frame preds = model.score(train);
            Scope.track(preds);

            Model.JavaScoringOptions options = new Model.JavaScoringOptions();
            options._disable_pojo = true;
            assertTrue(model.testJavaScoring(train, preds,1e-15, options));
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testEasyPredictMojo() throws Exception {
        try {
            Scope.enter();
            Frame train = generateFrame();
            train.toCategoricalCol("treatment");
            train.toCategoricalCol("conversion");
            Scope.track_generic(train);
            UpliftDRFModel.UpliftDRFParameters p = new UpliftDRFModel.UpliftDRFParameters();
            p._train = train._key;
            p._response_column = "conversion";
            p._treatment_column = "treatment";
            p._ntrees = 4;

            UpliftDRF udrf = new UpliftDRF(p);
            UpliftDRFModel model = udrf.trainModel().get();
            Scope.track_generic(model);
            MojoModel mojo = model.toMojo();
            EasyPredictModelWrapper wrapper = new EasyPredictModelWrapper(
                    new EasyPredictModelWrapper.Config()
                            .setModel(mojo)
                            .setEnableContributions(false)
            );
            Frame featureFr = train.subframe(mojo.features());
            Scope.track_generic(featureFr);
            for (int i = 0; i < featureFr.numRows(); i++) {
                RowData row = new RowData();
                for (String feat : featureFr.names()) {
                    if (featureFr.vec(feat).isCategorical()){
                        String value = featureFr.vec(feat).stringAt(i);
                        row.put(feat, value);
                    } else if (!featureFr.vec(feat).isNA(i)) {
                        double value = featureFr.vec(feat).at(i);
                        row.put(feat, value);
                    }
                }
                UpliftBinomialModelPrediction pred = wrapper.predictUpliftBinomial(row);
                assertEquals(pred.predictions.length,3);
                assertEquals(pred.predictions[0], pred.predictions[1]-pred.predictions[2], 0);
            }
        } finally {
            Scope.exit();
        }
    }
    
    private Frame generateFrame(){
        return new TestFrameBuilder()
                .withColNames("C0", "C1", "treatment", "treatment2", "conversion")
                .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_CAT, Vec.T_CAT, Vec.T_CAT)
                .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                .withDataForCol(1, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                .withDataForCol(2, ar("T", "C", "T", "T", "T", "C", "C", "C", "C", "C"))
                .withDataForCol(3, ar("T", "C2", "T", "T", "T", "C2", "C", "C", "C", "C2"))
                .withDataForCol(4, ar("1", "0", "1", "0", "1", "0", "1", "0", "1", "1"))
                .build();
    }
}
