package hex.generic;

import hex.Model;
import hex.ModelCategory;
import hex.ModelMetrics;
import hex.ModelMetricsBinomial;
import hex.coxph.CoxPH;
import hex.coxph.CoxPHModel;
import hex.deeplearning.DeepLearning;
import hex.deeplearning.DeepLearningModel;
import hex.ensemble.Metalearner;
import hex.ensemble.StackedEnsemble;
import hex.ensemble.StackedEnsembleModel;
import hex.gam.GAM;
import hex.gam.GAMModel;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.rulefit.RuleFit;
import hex.rulefit.RuleFitModel;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import hex.tree.isofor.IsolationForest;
import hex.tree.isofor.IsolationForestModel;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.io.*;
import java.net.URI;
import java.util.ArrayList;

import static hex.genmodel.utils.DistributionFamily.AUTO;
import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class GenericModelTest extends TestUtil {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testJavaScoring_gbm_binomial() throws Exception {
        try {
            Scope.enter();
            // Create new GBM model
            final Frame trainingFrame = parseTestFile("./smalldata/testng/airlines_train.csv");
            Scope.track(trainingFrame);
            final Frame testFrame = parseTestFile("./smalldata/testng/airlines_test.csv");
            Scope.track(testFrame);
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = "IsDepDelayed";
            parms._ntrees = 1;

            GBM job = new GBM(parms);
            final GBMModel model = job.trainModel().get();
            Scope.track_generic(model);
            assertEquals(model._output.getModelCategory(), ModelCategory.Binomial);
            final ByteArrayOutputStream originalModelMojo = new ByteArrayOutputStream();
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            model.getMojo().writeTo(originalModelMojo);
            model.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            final Key mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            final GenericModel genericModel = trainAndCheck(generic);
            Scope.track_generic(genericModel);

            assertNotNull(genericModel._output._training_metrics);
            assertTrue(genericModel._output._training_metrics instanceof ModelMetricsBinomial);

            final Frame genericModelPredictions = genericModel.score(testFrame);
            Scope.track(genericModelPredictions);

            final boolean equallyScored = genericModel.testJavaScoring(testFrame, genericModelPredictions, 0);
            assertTrue(equallyScored);

            final Frame originalModelPredictions = model.score(testFrame);
            Scope.track(originalModelPredictions);
            assertTrue(TestUtil.compareFrames(genericModelPredictions, originalModelPredictions));

            checkScoreContributions(model, genericModel, testFrame);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testJavaScoring_drf_binomial() throws Exception {
        try {
            Scope.enter();
            final Frame trainingFrame = parseTestFile("./smalldata/testng/airlines_train.csv");
            Scope.track(trainingFrame);
            final Frame testFrame = parseTestFile("./smalldata/testng/airlines_test.csv");
            Scope.track(testFrame);
            DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = "IsDepDelayed";
            parms._ntrees = 1;

            DRF job = new DRF(parms);
            final DRFModel model = job.trainModel().get();
            Scope.track_generic(model);
            assertEquals(model._output.getModelCategory(), ModelCategory.Binomial);
            final ByteArrayOutputStream originalModelMojo = new ByteArrayOutputStream();
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            model.getMojo().writeTo(originalModelMojo);
            model.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            final Key mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            final GenericModel genericModel = trainAndCheck(generic);
            Scope.track_generic(genericModel);

            final Frame genericModelPredictions = genericModel.score(testFrame);
            Scope.track(genericModelPredictions);
            assertEquals(2691, genericModelPredictions.numRows());

            final boolean equallyScored = genericModel.testJavaScoring(testFrame, genericModelPredictions, 0);
            assertTrue(equallyScored);

            final Frame originalModelPredictions = model.score(testFrame);
            Scope.track(originalModelPredictions);
            assertTrue(TestUtil.compareFrames(genericModelPredictions, originalModelPredictions));

            checkScoreContributions(model, genericModel, testFrame);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testJavaScoring_irf_binomial() throws Exception {
        try {
            Scope.enter();
            final Frame trainingFrame = parseTestFile("./smalldata/testng/airlines_train.csv");
            Scope.track(trainingFrame);
            final Frame testFrame = parseTestFile("./smalldata/testng/airlines_test.csv");
            Scope.track(testFrame);
            IsolationForestModel.IsolationForestParameters parms = new IsolationForestModel.IsolationForestParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = "IsDepDelayed";
            parms._ntrees = 1;

            IsolationForest job = new IsolationForest(parms);
            final IsolationForestModel model = job.trainModel().get();
            Scope.track_generic(model);
            assertEquals(model._output.getModelCategory(), ModelCategory.AnomalyDetection);
            final ByteArrayOutputStream originalModelMojo = new ByteArrayOutputStream();
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            model.getMojo().writeTo(originalModelMojo);
            model.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            final Key mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            final GenericModel genericModel = trainAndCheck(generic);
            Scope.track_generic(genericModel);

            final Frame genericModelPredictions = genericModel.score(testFrame);
            Scope.track(genericModelPredictions);
            assertEquals(2691, genericModelPredictions.numRows());

            final boolean equallyScored = genericModel.testJavaScoring(testFrame, genericModelPredictions, 0);
            assertTrue(equallyScored);

            final Frame originalModelPredictions = model.score(testFrame);
            Scope.track(originalModelPredictions);
            assertTrue(TestUtil.compareFrames(genericModelPredictions, originalModelPredictions));
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testJavaScoring_gbm_regression() throws Exception {
        try {
            Scope.enter();
            final Frame trainingFrame = parseTestFile("./smalldata/testng/airlines_train.csv");
            Scope.track(trainingFrame);
            final Frame testFrame = parseTestFile("./smalldata/testng/airlines_test.csv");
            Scope.track(testFrame);
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = "Distance";
            parms._ntrees = 1;

            GBM job = new GBM(parms);
            final GBMModel model = job.trainModel().get();
            Scope.track_generic(model);
            assertEquals(model._output.getModelCategory(), ModelCategory.Regression);
            final ByteArrayOutputStream originalModelMojo = new ByteArrayOutputStream();
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            model.getMojo().writeTo(originalModelMojo);
            model.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            final Key mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            final GenericModel genericModel = trainAndCheck(generic);
            Scope.track_generic(genericModel);

            final Frame genericModelPredictions = genericModel.score(testFrame);
            Scope.track(genericModelPredictions);
            assertEquals(2691, genericModelPredictions.numRows());

            final boolean equallyScored = genericModel.testJavaScoring(testFrame, genericModelPredictions, 0);
            assertTrue(equallyScored);

            final Frame originalModelPredictions = model.score(testFrame);
            Scope.track(originalModelPredictions);
            assertTrue(TestUtil.compareFrames(genericModelPredictions, originalModelPredictions));

            checkScoreContributions(model, genericModel, testFrame);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testJavaScoring_gbm_regression_offset() throws Exception {
        try {
            Scope.enter();
            final Frame trainingFrame = parseTestFile("smalldata/junit/cars_20mpg.csv");
            Scope.track(trainingFrame);

            Vec offset = trainingFrame.anyVec().makeCon(0.5);
            trainingFrame.add("offset", offset);
            DKV.put(trainingFrame);

            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = "economy_20mpg";
            parms._ntrees = 1;
            parms._offset_column = "offset";

            final GBMModel model = new GBM(parms).trainModel().get();
            Scope.track_generic(model);

            File exportDir = temporaryFolder.newFolder("cars_offset_test");
            URI mojoURI = model.exportMojo(new File(exportDir, "cars.zip").getAbsolutePath(), false);

            final GenericModel genericModel = Generic.importMojoModel(mojoURI.getPath(), false);
            Scope.track_generic(genericModel);

            final Frame genericModelPredictions = genericModel.score(trainingFrame);
            Scope.track(genericModelPredictions);

            assertTrue(model.testJavaScoring(trainingFrame, genericModelPredictions, 1e-6));
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testJavaScoring_drf_regression() throws Exception {
        try {
            Scope.enter();
            final Frame trainingFrame = parseTestFile("./smalldata/testng/airlines_train.csv");
            Scope.track(trainingFrame);
            final Frame testFrame = parseTestFile("./smalldata/testng/airlines_test.csv");
            Scope.track(testFrame);
            DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = "Distance";
            parms._ntrees = 1;

            DRF job = new DRF(parms);
            final DRFModel model = job.trainModel().get();
            Scope.track_generic(model);
            assertEquals(model._output.getModelCategory(), ModelCategory.Regression);
            final ByteArrayOutputStream originalModelMojo = new ByteArrayOutputStream();
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            model.getMojo().writeTo(originalModelMojo);
            model.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            final Key mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            final GenericModel genericModel = trainAndCheck(generic);
            Scope.track_generic(genericModel);

            final Frame genericModelPredictions = genericModel.score(testFrame);
            Scope.track(genericModelPredictions);
            assertEquals(2691, genericModelPredictions.numRows());

            final boolean equallyScored = genericModel.testJavaScoring(testFrame, genericModelPredictions, 0);
            assertTrue(equallyScored);

            final Frame originalModelPredictions = model.score(testFrame);
            Scope.track(originalModelPredictions);
            assertTrue(TestUtil.compareFrames(genericModelPredictions, originalModelPredictions));

            checkScoreContributions(model, genericModel, testFrame);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testJavaScoring_irf_numerical() throws Exception {
        try {
            Scope.enter();
            final Frame trainingFrame = parseTestFile("./smalldata/testng/airlines_train.csv");
            Scope.track(trainingFrame);
            final Frame testFrame = parseTestFile("./smalldata/testng/airlines_test.csv");
            Scope.track(testFrame);
            IsolationForestModel.IsolationForestParameters parms = new IsolationForestModel.IsolationForestParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = "Distance";
            parms._ntrees = 1;

            IsolationForest job = new IsolationForest(parms);
            final IsolationForestModel model = job.trainModel().get();
            Scope.track_generic(model);
            assertEquals(model._output.getModelCategory(), ModelCategory.AnomalyDetection);
            final ByteArrayOutputStream originalModelMojo = new ByteArrayOutputStream();
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            model.getMojo().writeTo(originalModelMojo);
            model.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            final Key mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            final GenericModel genericModel = trainAndCheck(generic);
            Scope.track_generic(genericModel);

            final Frame genericModelPredictions = genericModel.score(testFrame);
            Scope.track(genericModelPredictions);
            assertEquals(2691, genericModelPredictions.numRows());

            final boolean equallyScored = genericModel.testJavaScoring(testFrame, genericModelPredictions, 0);
            assertTrue(equallyScored);

            final Frame originalModelPredictions = model.score(testFrame);
            Scope.track(originalModelPredictions);
            assertTrue(TestUtil.compareFrames(genericModelPredictions, originalModelPredictions));
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testJavaScoring_glm() throws Exception {
        try {
            Scope.enter();
            final Frame trainingFrame = parseTestFile("./smalldata/testng/airlines_train.csv");
            Scope.track(trainingFrame);
            final Frame testFrame = parseTestFile("./smalldata/testng/airlines_test.csv");
            Scope.track(testFrame);
            GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = "Distance";

            GLM job = new GLM(parms);
            final GLMModel model = job.trainModel().get();
            Scope.track_generic(model);
            assertEquals(model._output.getModelCategory(), ModelCategory.Regression);
            final ByteArrayOutputStream originalModelMojo = new ByteArrayOutputStream();
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            model.getMojo().writeTo(originalModelMojo);
            model.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            final Key mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            final GenericModel genericModel = trainAndCheck(generic);
            Scope.track_generic(genericModel);

            final Frame genericModelPredictions = genericModel.score(testFrame);
            Scope.track(genericModelPredictions);
            assertEquals(2691, genericModelPredictions.numRows());

            final boolean equallyScored = genericModel.testJavaScoring(testFrame, genericModelPredictions, 0);
            assertTrue(equallyScored);

            final Frame originalModelPredictions = model.score(testFrame);
            Scope.track(originalModelPredictions);
            assertTrue(TestUtil.compareFrames(genericModelPredictions, originalModelPredictions));
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testJavaScoring_gbm_multinomial() throws Exception {
        try {
            Scope.enter();
            final Frame trainingFrame = parseTestFile("./smalldata/testng/airlines_train.csv");
            Scope.track(trainingFrame);
            final Frame testFrame = parseTestFile("./smalldata/testng/airlines_test.csv");
            Scope.track(testFrame);
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = "Origin";
            parms._ntrees = 1;

            GBM job = new GBM(parms);
            final GBMModel model = job.trainModel().get();
            Scope.track_generic(model);
            assertEquals(model._output.getModelCategory(), ModelCategory.Multinomial);
            final ByteArrayOutputStream originalModelMojo = new ByteArrayOutputStream();
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            model.getMojo().writeTo(originalModelMojo);
            model.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            final Key mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            final GenericModel genericModel = trainAndCheck(generic);
            Scope.track_generic(genericModel);
            assertNotNull(genericModel._output._model_summary);
            assertNotNull(genericModel._output._variable_importances);

            final Frame genericModelPredictions = genericModel.score(testFrame);
            Scope.track(genericModelPredictions);
            assertEquals(2691, genericModelPredictions.numRows());

            final boolean equallyScored = genericModel.testJavaScoring(testFrame, genericModelPredictions, 0);
            assertTrue(equallyScored);

            final Frame originalModelPredictions = model.score(testFrame);
            Scope.track(originalModelPredictions);
            assertTrue(TestUtil.compareFrames(genericModelPredictions, originalModelPredictions));
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testJavaScoring_drf_multinomial() throws Exception {
        try {
            Scope.enter();
            final Frame trainingFrame = parseTestFile("./smalldata/testng/airlines_train.csv");
            Scope.track(trainingFrame);
            final Frame testFrame = parseTestFile("./smalldata/testng/airlines_test.csv");
            Scope.track(testFrame);
            DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = "Origin";
            parms._ntrees = 1;

            DRF job = new DRF(parms);
            final DRFModel model = job.trainModel().get();
            Scope.track_generic(model);
            assertEquals(model._output.getModelCategory(), ModelCategory.Multinomial);
            final ByteArrayOutputStream originalModelMojo = new ByteArrayOutputStream();
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            model.getMojo().writeTo(originalModelMojo);
            model.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            final Key mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            final GenericModel genericModel = trainAndCheck(generic);
            Scope.track_generic(genericModel);

            final Frame genericModelPredictions = genericModel.score(testFrame);
            Scope.track(genericModelPredictions);
            assertEquals(2691, genericModelPredictions.numRows());

            final boolean equallyScored = genericModel.testJavaScoring(testFrame, genericModelPredictions, 0);
            assertTrue(equallyScored);

            final Frame originalModelPredictions = model.score(testFrame);
            Scope.track(originalModelPredictions);
            assertTrue(TestUtil.compareFrames(genericModelPredictions, originalModelPredictions));
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testJavaScoring_irf_multinomial() throws Exception {
        try {
            Scope.enter();
            final Frame trainingFrame = parseTestFile("./smalldata/testng/airlines_train.csv");
            Scope.track(trainingFrame);
            final Frame testFrame = parseTestFile("./smalldata/testng/airlines_test.csv");
            Scope.track(testFrame);
            IsolationForestModel.IsolationForestParameters parms = new IsolationForestModel.IsolationForestParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = "Origin";
            parms._ntrees = 1;

            IsolationForest job = new IsolationForest(parms);
            final IsolationForestModel model = job.trainModel().get();
            Scope.track_generic(model);
            assertEquals(model._output.getModelCategory(), ModelCategory.AnomalyDetection);
            final ByteArrayOutputStream originalModelMojo = new ByteArrayOutputStream();
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            model.getMojo().writeTo(originalModelMojo);
            model.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            final Key mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            final GenericModel genericModel = trainAndCheck(generic);
            Scope.track_generic(genericModel);

            final Frame genericModelPredictions = genericModel.score(testFrame);
            Scope.track(genericModelPredictions);
            assertEquals(2691, genericModelPredictions.numRows());

            final boolean equallyScored = genericModel.testJavaScoring(testFrame, genericModelPredictions, 0);
            assertTrue(equallyScored);

            final Frame originalModelPredictions = model.score(testFrame);
            Scope.track(originalModelPredictions);
            assertTrue(TestUtil.compareFrames(genericModelPredictions, originalModelPredictions));
        } finally {
            Scope.exit();
        }
    }

    /**
     * Create a GBM model and writes a MOJO into a temporary zip file. Then, it creates a Generic model out of that
     * temporary zip file and re-downloads the underlying MOJO again. The byte arrays representing both MOJOs are tested
     * to be the same.
     *
     */
    @Test
    public void downloadable_mojo_gbm() throws IOException {
        try {
            Scope.enter();
            // Create new GBM model
            final Frame trainingFrame = parseTestFile("./smalldata/gbm_test/Mfgdata_gaussian_GBM_testing.csv");
            Scope.track(trainingFrame);
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._monotone_constraints = new hex.KeyValue[] {new hex.KeyValue("a", -1d)};
            parms._response_column = trainingFrame._names[1];
            parms._ntrees = 1;

            GBM job = new GBM(parms);
            final GBMModel gbm = job.trainModel().get();
            Scope.track_generic(gbm);
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            gbm.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            final Key mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            // Create Generic model from given imported MOJO
            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            final GenericModel genericModel = trainAndCheck(generic);
            Scope.track_generic(genericModel);

            // Compare the two MOJOs byte-wise
            final File genericModelMojoFile = File.createTempFile("mojo", "zip");
            genericModel.getMojo().writeTo(new FileOutputStream(genericModelMojoFile));
            assertArrayEquals(FileUtils.readFileToByteArray(originalModelMojoFile), FileUtils.readFileToByteArray(genericModelMojoFile));

        } finally {
            Scope.exit();
        }
    }

    /**
     * Create a DRF model and writes a MOJO into a temporary zip file. Then, it creates a Generic model out of that
     * temporary zip file and re-downloads the underlying MOJO again. The byte arrays representing both MOJOs are tested
     * to be the same.
     *
     */
    @Test
    public void downloadable_mojo_drf() throws IOException {
        try {
            Scope.enter();
            // Create new DRF model
            final Frame trainingFrame = parseTestFile("./smalldata/gbm_test/Mfgdata_gaussian_GBM_testing.csv");
            Scope.track(trainingFrame);
            DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = trainingFrame._names[1];
            parms._ntrees = 1;

            DRF job = new DRF(parms);
            final DRFModel originalModel = job.trainModel().get();
            Scope.track_generic(originalModel);
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            originalModel.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            final Key mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            // Create Generic model from given imported MOJO
            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            final GenericModel genericModel = trainAndCheck(generic);
            Scope.track_generic(genericModel);

            // Compare the two MOJOs byte-wise
            final File genericModelMojoFile = File.createTempFile("mojo", "zip");
            genericModel.getMojo().writeTo(new FileOutputStream(genericModelMojoFile));
            assertArrayEquals(FileUtils.readFileToByteArray(originalModelMojoFile), FileUtils.readFileToByteArray(genericModelMojoFile));

        } finally {
            Scope.exit();
        }
    }

    /**
     * Create an IRF model and writes a MOJO into a temporary zip file. Then, it creates a Generic model out of that
     * temporary zip file and re-downloads the underlying MOJO again. The byte arrays representing both MOJOs are tested
     * to be the same.
     *
     */
    @Test
    public void downloadable_mojo_irf() throws IOException {
        try {
            Scope.enter();
            // Create new IRF model
            final Frame trainingFrame = parseTestFile("./smalldata/gbm_test/Mfgdata_gaussian_GBM_testing.csv");
            Scope.track(trainingFrame);
            IsolationForestModel.IsolationForestParameters parms = new IsolationForestModel.IsolationForestParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = trainingFrame._names[1];
            parms._ntrees = 1;

            IsolationForest job = new IsolationForest(parms);
            final IsolationForestModel originalModel = job.trainModel().get();
            Scope.track_generic(originalModel);
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            originalModel.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            final Key mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            // Create Generic model from given imported MOJO
            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            final GenericModel genericModel = trainAndCheck(generic);
            Scope.track_generic(genericModel);

            // Compare the two MOJOs byte-wise
            final File genericModelMojoFile = File.createTempFile("mojo", "zip");
            genericModel.getMojo().writeTo(new FileOutputStream(genericModelMojoFile));
            assertArrayEquals(FileUtils.readFileToByteArray(originalModelMojoFile), FileUtils.readFileToByteArray(genericModelMojoFile));

        } finally {
            Scope.exit();
        }
    }

    /**
     * Create a GLM model and writes a MOJO into a temporary zip file. Then, it creates a Generic model out of that
     * temporary zip file and re-downloads the underlying MOJO again. The byte arrays representing both MOJOs are tested
     * to be the same.
     *
     */
    @Test
    public void downloadable_mojo_glm() throws IOException {
        try {
            Scope.enter();
            final Frame trainingFrame = parseTestFile("./smalldata/gbm_test/Mfgdata_gaussian_GBM_testing.csv");
            Scope.track(trainingFrame);
            GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = trainingFrame._names[1];

            GLM job = new GLM(parms);
            final GLMModel originalModel = job.trainModel().get();
            Scope.track_generic(originalModel);
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            originalModel.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            final Key mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            // Create Generic model from given imported MOJO
            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            final GenericModel genericModel = trainAndCheck(generic);
            Scope.track_generic(genericModel);

            // Compare the two MOJOs byte-wise
            final File genericModelMojoFile = File.createTempFile("mojo", "zip");
            genericModel.getMojo().writeTo(new FileOutputStream(genericModelMojoFile));
            assertArrayEquals(FileUtils.readFileToByteArray(originalModelMojoFile), FileUtils.readFileToByteArray(genericModelMojoFile));

        } finally {
            Scope.exit();
        }
    } 
    
    /**
     * Create a CoxPH model and writes a MOJO into a temporary zip file. Then, it creates a Generic model out of that
     * temporary zip file and re-downloads the underlying MOJO again. The byte arrays representing both MOJOs are tested
     * to be the same.
     *
     */
    @Test
    public void downloadable_mojo_cox_ph() throws IOException {
        try {
            Scope.enter();
            final Frame trainingFrame = parseTestFile("./smalldata/coxph_test/heart.csv");
            Scope.track(trainingFrame);
            CoxPHModel.CoxPHParameters parms = new CoxPHModel.CoxPHParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._start_column = "start";
            parms._stop_column = "stop";
            parms._response_column = "event";
            parms._ignored_columns = new String[] {"id"};

            hex.coxph.CoxPH job = new CoxPH(parms);
            final CoxPHModel originalModel = job.trainModel().get();
            Scope.track_generic(originalModel);
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            originalModel.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            final Key mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            // Create Generic model from given imported MOJO
            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            final GenericModel genericModel = trainAndCheck(generic);
            Scope.track_generic(genericModel);

            // Compare the two MOJOs byte-wise
            final File genericModelMojoFile = File.createTempFile("mojo", "zip");
            genericModel.getMojo().writeTo(new FileOutputStream(genericModelMojoFile));
            assertArrayEquals(FileUtils.readFileToByteArray(originalModelMojoFile), FileUtils.readFileToByteArray(genericModelMojoFile));

        } finally {
            Scope.exit();
        }
    } 
    
    @Test
    public void testJavaScoring_mojo_cox_ph() throws IOException {
        try {
            Scope.enter();
            final Frame trainingFrame = parseTestFile("./smalldata/coxph_test/heart.csv");
            Scope.track(trainingFrame);
            final Frame testFrame = parseTestFile("./smalldata/coxph_test/heart_test.csv");
            Scope.track(testFrame);
            testJavaScoringCoxPH(trainingFrame, testFrame, new String[0]);
        } finally {
            Scope.exit();
        }
    }
     @Test
    public void testJavaScoring_mojo_cox_ph_strata() throws IOException {
        try {
            Scope.enter();
            final Frame trainingFrame = parseTestFile("./smalldata/coxph_test/heart.csv").toCategoricalCol("transplant");
            Scope.track(trainingFrame);
            final Frame testFrame = parseTestFile("./smalldata/coxph_test/heart_test.csv").toCategoricalCol("transplant");
            Scope.track(testFrame);
            testJavaScoringCoxPH(trainingFrame, testFrame, new String[] {"transplant"});

        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void testJavaScoring_mojo_cox_ph_categorical() throws IOException {
        try {
            Scope.enter();
            final Frame trainingFrame = parseTestFile("./smalldata/coxph_test/heart.csv").toCategoricalCol("transplant");
            Scope.track(trainingFrame);
            final Frame testFrame = parseTestFile("./smalldata/coxph_test/heart_test.csv").toCategoricalCol("transplant");
            Scope.track(testFrame);
            testJavaScoringCoxPH(trainingFrame, testFrame, new String[0]);
        } finally {
            Scope.exit();
        }
    }
 
    @Test
    public void testJavaScoring_mojo_cox_ph_2_categoricals() throws IOException {
        try {
            Scope.enter();
            final Frame trainingFrame = parseTestFile("./smalldata/coxph_test/heart.csv")
                    .toCategoricalCol("transplant")
                    .toCategoricalCol("surgery");
            Scope.track(trainingFrame);
            final Frame testFrame = parseTestFile("./smalldata/coxph_test/heart_test.csv")
                    .toCategoricalCol("transplant")
                    .toCategoricalCol("surgery");
            Scope.track(testFrame);
            testJavaScoringCoxPH(trainingFrame, testFrame, new String[0]);
        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void testJavaScoring_mojo_cox_ph_2_stratify() throws IOException {
        try {
            Scope.enter();
            final Frame trainingFrame = parseTestFile("./smalldata/coxph_test/heart.csv")
                    .toCategoricalCol("transplant")
                    .toCategoricalCol("surgery");
            Scope.track(trainingFrame);
            final Frame testFrame = parseTestFile("./smalldata/coxph_test/heart_test.csv")
                    .toCategoricalCol("transplant")
                    .toCategoricalCol("surgery");
            Scope.track(testFrame);
            testJavaScoringCoxPH(trainingFrame, testFrame, new String[] {"transplant", "surgery"});
        } finally {
            Scope.exit();
        }
    }


    private void testJavaScoringCoxPH(Frame trainingFrame, Frame testFrame, String[] stratifyBy) throws IOException {
        CoxPHModel.CoxPHParameters parms = new CoxPHModel.CoxPHParameters();
        parms._train = trainingFrame._key;
        parms._distribution = AUTO;
        parms._start_column = "start";
        parms._stop_column = "stop";
        parms._response_column = "event";
        parms._ignored_columns = new String[]{"id"};
        parms._stratify_by = stratifyBy;
        parms._use_all_factor_levels = true;

        CoxPH job = new CoxPH(parms);
        final CoxPHModel originalModel = job.trainModel().get();
        Scope.track_generic(originalModel);

        // FIXME: for debugging issues on jenkins
        originalModel.exportBinaryModel(modelExportFile("binary", "bin").getAbsolutePath(), true);

        final File originalModelMojoFile = modelExportFile("mojo", "zip");
        originalModel
                .getMojo()
                .writeTo(new FileOutputStream(originalModelMojoFile));
        
        final Key mojoKey = importMojo(originalModelMojoFile.getAbsolutePath());

        // Create Generic model from given imported MOJO
        final GenericModelParameters genericModelParameters = new GenericModelParameters();
        genericModelParameters._model_key = mojoKey;
        final Generic generic = new Generic(genericModelParameters);
        final GenericModel genericModel = trainAndCheck(generic);
        Scope.track_generic(genericModel);

        final Frame genericModelPredictions = genericModel.score(testFrame);
        Scope.track(genericModelPredictions);
        assertEquals(testFrame.numRows(), genericModelPredictions.numRows());

        final boolean equallyScored = genericModel.testJavaScoring(testFrame, genericModelPredictions, 0);
        assertTrue(equallyScored);

        final Frame originalModelPredictions = originalModel.score(testFrame);
        Scope.track(originalModelPredictions);
        assertTrue(TestUtil.compareFrames(originalModelPredictions, genericModelPredictions, 0.000001, 0.00001));
    }
    
    private File modelExportFile(String prefix, String suffix) throws IOException {
        File sandboxDir = H2O.getCloudSize() > 1 ? new File("sandbox/multi") : new File("sandbox/single");
        if (sandboxDir.isDirectory()) {
            String name = "unknown";
            for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
                if (ste.getMethodName().startsWith("test") && ste.getClassName().equals(getClass().getCanonicalName())) {
                    name = ste.getMethodName();
                }
            }
            return new File(sandboxDir, prefix + name + "." + suffix);
        } else {
            return File.createTempFile("mojo", "zip");
        }
    }
    
    @Test
    public void downloadable_mojo_glm_binomial() throws IOException {
        try {
            Scope.enter();
            final Frame trainingFrame = parseTestFile("./smalldata/testng/airlines_train.csv");
            Scope.track(trainingFrame);
            GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._family = GLMModel.GLMParameters.Family.binomial;
            parms._response_column = "IsDepDelayed";

            GLM job = new GLM(parms);
            final GLMModel originalModel = job.trainModel().get();
            Scope.track_generic(originalModel);
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            originalModel.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            final Key mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            // Create Generic model from given imported MOJO
            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            final GenericModel genericModel = trainAndCheck(generic);
            Scope.track_generic(genericModel);

            // Compare the two MOJOs byte-wise
            final File genericModelMojoFile = File.createTempFile("mojo", "zip");
            genericModel.getMojo().writeTo(new FileOutputStream(genericModelMojoFile));
            assertArrayEquals(FileUtils.readFileToByteArray(originalModelMojoFile), FileUtils.readFileToByteArray(genericModelMojoFile));

        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testJavaScoring_deeplearning() throws Exception {
        try {
            Scope.enter();
            // Create new GBM model
            final Frame trainingFrame = parseTestFile("./smalldata/testng/airlines_train.csv");
            Scope.track(trainingFrame);
            final Frame testFrame = parseTestFile("./smalldata/testng/airlines_test.csv");
            Scope.track(testFrame);

            DeepLearningModel.DeepLearningParameters parms = new DeepLearningModel.DeepLearningParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._epochs = 1;
            parms._response_column = "IsDepDelayed";

            DeepLearning job = new DeepLearning(parms);
            final DeepLearningModel model = job.trainModel().get();
            Scope.track_generic(model);
            assertEquals(model._output.getModelCategory(), ModelCategory.Binomial);
            final ByteArrayOutputStream originalModelMojo = new ByteArrayOutputStream();
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            model.getMojo().writeTo(originalModelMojo);
            model.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            final Key mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            final GenericModel genericModel = trainAndCheck(generic);
            Scope.track_generic(genericModel);


            assertNotNull(genericModel._output._training_metrics);
            assertTrue(genericModel._output._training_metrics instanceof ModelMetricsBinomial);

            final Frame genericModelPredictions = genericModel.score(testFrame);
            Scope.track(genericModelPredictions);

            assertTrue(genericModel.testJavaScoring(testFrame, genericModelPredictions, 0));

            final Frame originalModelPredictions = model.score(testFrame);
            Scope.track(originalModelPredictions);
            assertTrue(TestUtil.compareFrames(genericModelPredictions, originalModelPredictions));

        } finally {
            Scope.exit();
        }
    }

    @Test
    public void downloadable_mojo_deeplearning() throws IOException {
        try {
            Scope.enter();
            final Frame trainingFrame = parseTestFile("./smalldata/testng/airlines_train.csv");
            Scope.track(trainingFrame);

            DeepLearningModel.DeepLearningParameters parms = new DeepLearningModel.DeepLearningParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._epochs = 1;
            parms._response_column = "IsDepDelayed";

            DeepLearning job = new DeepLearning(parms);
            final DeepLearningModel originalModel = job.trainModel().get();
            Scope.track_generic(originalModel);
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            originalModel.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            final Key<Frame> mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            // Create Generic model from given imported MOJO
            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            final GenericModel genericModel = trainAndCheck(generic);
            Scope.track_generic(genericModel);

            // Compare the two MOJOs byte-wise
            final File genericModelMojoFile = temporaryFolder.newFile();
            genericModelMojoFile.deleteOnExit();
            genericModel.getMojo().writeTo(new FileOutputStream(genericModelMojoFile));
            assertArrayEquals(FileUtils.readFileToByteArray(originalModelMojoFile), FileUtils.readFileToByteArray(genericModelMojoFile));

        } finally {
            Scope.exit();
        }
    }


    @Test
    public void stackedEnsembleMojoTest() throws IOException {
        try {
            Scope.enter();

            final Frame trainingFrame = parseTestFile("./smalldata/testng/airlines_train.csv");
            Scope.track(trainingFrame);

            // Create DeepLearning Model
            final DeepLearningModel.DeepLearningParameters deepLearningParameters = new DeepLearningModel.DeepLearningParameters();
            deepLearningParameters._train = trainingFrame._key;
            deepLearningParameters._distribution = AUTO;
            deepLearningParameters._epochs = 1;
            deepLearningParameters._response_column = "IsDepDelayed";
            deepLearningParameters._nfolds = 2;
            deepLearningParameters._keep_cross_validation_predictions = true;
            deepLearningParameters._seed = 0XFEED;

            final DeepLearning deepLearning = new DeepLearning(deepLearningParameters);
            final DeepLearningModel deepLearningModel = deepLearning.trainModel().get();
            Scope.track_generic(deepLearningModel);


            // Create GBM model
            final GBMModel.GBMParameters gbmParameters = new GBMModel.GBMParameters();
            gbmParameters._train = trainingFrame._key;
            gbmParameters._distribution = AUTO;
            gbmParameters._response_column = "IsDepDelayed";
            gbmParameters._ntrees = 1;
            gbmParameters._nfolds = 2;
            gbmParameters._keep_cross_validation_predictions = true;

            gbmParameters._seed = 0XFEED;

            final GBM gbm = new GBM(gbmParameters);
            final GBMModel gbmModel = gbm.trainModel().get();
            Scope.track_generic(gbmModel);

            final StackedEnsembleModel.StackedEnsembleParameters stackedEnsembleParameters = new StackedEnsembleModel.StackedEnsembleParameters();
            stackedEnsembleParameters._train = trainingFrame._key;
            stackedEnsembleParameters._response_column = "IsDepDelayed";
            stackedEnsembleParameters._metalearner_algorithm = Metalearner.Algorithm.AUTO;
            stackedEnsembleParameters._base_models = new Key[]{deepLearningModel._key, gbmModel._key};
            stackedEnsembleParameters._seed = 0xFEED;

            final StackedEnsemble stackedEnsemble = new StackedEnsemble(stackedEnsembleParameters);
            final StackedEnsembleModel stackedEnsembleModel = stackedEnsemble.trainModel().get();
            Scope.track_generic(stackedEnsembleModel);
            assertNotNull(stackedEnsembleModel);

            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            stackedEnsembleModel.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            final Key<Frame> mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            // Create Generic model from given imported MOJO
            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            final GenericModel genericModel = trainAndCheck(generic);
            Scope.track_generic(genericModel);

            // Compare the two MOJOs byte-wise
            final File genericModelMojoFile = temporaryFolder.newFile();
            genericModelMojoFile.deleteOnExit();
            genericModel.getMojo().writeTo(new FileOutputStream(genericModelMojoFile));
            assertArrayEquals(FileUtils.readFileToByteArray(originalModelMojoFile), FileUtils.readFileToByteArray(genericModelMojoFile));

            // Test scoring
            final Frame testFrame = parseTestFile("./smalldata/testng/airlines_test.csv");
            Scope.track(testFrame);
            final Frame predictions = genericModel.score(testFrame);
            Scope.track(predictions);

            final boolean equallyScored = genericModel.testJavaScoring(testFrame, predictions, 0);

            final Frame originalModelPredictions = stackedEnsembleModel.score(testFrame);
            Scope.track(originalModelPredictions);
            assertTrue(TestUtil.compareFrames(predictions, originalModelPredictions));

            assertTrue(equallyScored);
        } finally {
            Scope.exit();
        }
    }


    private GenericModel trainAndCheck(Generic builder) {
        GenericModel model = builder.trainModel().get();
        assertNotNull(model);
        assertFalse(model.needsPostProcess());
        assertNotNull(model.toString()); // make sure model can be printed
        return model;
    }

    private Key<Frame> importMojo(final String mojoAbsolutePath) {
        final ArrayList<String> keys = new ArrayList<>(1);
        H2O.getPM().importFiles(mojoAbsolutePath, "", new ArrayList<>(), keys, new ArrayList<>(),
                new ArrayList<>());
        assertEquals(1, keys.size());
        final Key<Frame> key = Key.make(keys.get(0));
        Scope.track_generic(key.get());
        return key;
    }


    @Test
    public void isAlgoNamePresent() throws IOException {
        try {
            Scope.enter();
            // Create new GBM model
            final Frame trainingFrame = parseTestFile("./smalldata/gbm_test/Mfgdata_gaussian_GBM_testing.csv");
            Scope.track(trainingFrame);
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = trainingFrame._names[1];
            parms._ntrees = 1;

            GBM job = new GBM(parms);
            final GBMModel gbm = job.trainModel().get();
            Scope.track_generic(gbm);
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            gbm.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            final Key mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            // Create Generic model from given imported MOJO
            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            final GenericModel genericModel = trainAndCheck(generic);
            Scope.track_generic(genericModel);

            assertEquals("gbm",genericModel._output._original_model_identifier);
            assertEquals("Gradient Boosting Machine", genericModel._output._original_model_full_name);
        } finally {
            Scope.exit();
        }
    }

    private void checkScoreContributions(Model.Contributions originalModel, GenericModel genericModel, Frame testFrame) {
        testFrame = ensureDistributed(testFrame);

        Key<Frame> dest = Key.make();

        final Frame originalModelContributions = originalModel.scoreContributions(testFrame, Key.make());
        Scope.track(originalModelContributions);
        final Frame genericModelContributions = genericModel.scoreContributions(testFrame, dest);
        Scope.track(genericModelContributions);

        assertInDKV(dest, genericModelContributions);
        assertFrameEquals(originalModelContributions, genericModelContributions, 0.0d);
    }



    @Test
    public void rulefitMojoTestBinomial() throws IOException {
        try {
            Scope.enter();

            final Frame trainingFrame = parseTestFile("./smalldata/testng/airlines_train.csv");
            Scope.track(trainingFrame);

            final RuleFitModel.RuleFitParameters ruleFitParameters = new RuleFitModel.RuleFitParameters();
            ruleFitParameters._train = trainingFrame._key;
            ruleFitParameters._distribution = AUTO;
            ruleFitParameters._response_column = "IsDepDelayed";
            ruleFitParameters._seed = 0XFEED;
            ruleFitParameters._model_type = RuleFitModel.ModelType.RULES_AND_LINEAR;
            ruleFitParameters._max_rule_length = 5;
            ruleFitParameters._min_rule_length = 1;
            ruleFitParameters._max_num_rules = 1000;

            final RuleFit ruleFit = new RuleFit(ruleFitParameters);
            final RuleFitModel ruleFitModel = ruleFit.trainModel().get();
            Scope.track_generic(ruleFitModel);
            assertNotNull(ruleFitModel);

            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            ruleFitModel.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            final Key<Frame> mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            // Create Generic model from given imported MOJO
            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            final GenericModel genericModel = trainAndCheck(generic);
            Scope.track_generic(genericModel);

            // Compare the two MOJOs byte-wise
            final File genericModelMojoFile = temporaryFolder.newFile();
            genericModelMojoFile.deleteOnExit();
            genericModel.getMojo().writeTo(new FileOutputStream(genericModelMojoFile));
            assertArrayEquals(FileUtils.readFileToByteArray(originalModelMojoFile), FileUtils.readFileToByteArray(genericModelMojoFile));

            // Test scoring
            final Frame testFrame = parseTestFile("./smalldata/testng/airlines_test.csv");
            Scope.track(testFrame);
            final Frame predictions = genericModel.score(testFrame);
            Scope.track(predictions);

            final boolean equallyScored = genericModel.testJavaScoring(testFrame, predictions, 0);

            final Frame originalModelPredictions = ruleFitModel.score(testFrame);
            Scope.track(originalModelPredictions);
            assertTrue(TestUtil.compareFrames(predictions, originalModelPredictions));

            assertTrue(equallyScored);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void rulefitMojoTestRegression() throws IOException {
        try {
            Scope.enter();

            final Frame trainingFrame = parseTestFile("./smalldata/testng/airlines_train.csv");
            Scope.track(trainingFrame);
            
            final RuleFitModel.RuleFitParameters ruleFitParameters = new RuleFitModel.RuleFitParameters();
            ruleFitParameters._train = trainingFrame._key;
            ruleFitParameters._distribution = AUTO;
            ruleFitParameters._response_column = "Distance";
            ruleFitParameters._seed = 0XFEED;
            ruleFitParameters._model_type = RuleFitModel.ModelType.RULES_AND_LINEAR;
            ruleFitParameters._max_rule_length = 5;
            ruleFitParameters._min_rule_length = 1;
            ruleFitParameters._max_num_rules = 1000;

            final RuleFit ruleFit = new RuleFit(ruleFitParameters);
            final RuleFitModel ruleFitModel = ruleFit.trainModel().get();
            Scope.track_generic(ruleFitModel);
            assertNotNull(ruleFitModel);

            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            ruleFitModel.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            final Key<Frame> mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            // Create Generic model from given imported MOJO
            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            final GenericModel genericModel = trainAndCheck(generic);
            Scope.track_generic(genericModel);

            // Compare the two MOJOs byte-wise
            final File genericModelMojoFile = temporaryFolder.newFile();
            genericModelMojoFile.deleteOnExit();
            genericModel.getMojo().writeTo(new FileOutputStream(genericModelMojoFile));
            assertArrayEquals(FileUtils.readFileToByteArray(originalModelMojoFile), FileUtils.readFileToByteArray(genericModelMojoFile));

            // Test scoring
            final Frame testFrame = parseTestFile("./smalldata/testng/airlines_test.csv");
            Scope.track(testFrame);
            final Frame predictions = genericModel.score(testFrame);
            Scope.track(predictions);

            final boolean equallyScored = genericModel.testJavaScoring(testFrame, predictions, 0);

            final Frame originalModelPredictions = ruleFitModel.score(testFrame);
            Scope.track(originalModelPredictions);
            assertTrue(TestUtil.compareFrames(predictions, originalModelPredictions));

            assertTrue(equallyScored);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testJavaScoring_gbm_binomial_pojo() throws Exception {
        try {
            Scope.enter();
            System.setProperty("sys.ai.h2o.pojo.import.enabled", "true");
            // Create new GBM model
            final Frame trainingFrame = parseTestFile("./smalldata/testng/airlines_train.csv");
            Scope.track(trainingFrame);
            final Frame testFrame = parseTestFile("./smalldata/testng/airlines_test.csv");
            Scope.track(testFrame);
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = "IsDepDelayed";
            parms._ntrees = 1;

            GBM job = new GBM(parms);
            final GBMModel model = job.trainModel().get();
            Scope.track_generic(model);
            assertEquals(model._output.getModelCategory(), ModelCategory.Binomial);

            String pojoCode = model.toJava(false, true);
            File pojoFile = temporaryFolder.newFile(model._key + ".java");
            try (FileWriter wr = new FileWriter(pojoFile)) {
                IOUtils.write(pojoCode, wr);
            }

            GenericModel generic = Generic.importMojoModel(pojoFile.getAbsolutePath(), true);
            Scope.track_generic(generic);

            Frame scoredOriginal = model.score(testFrame);
            Scope.track(scoredOriginal);
            Frame scoredGeneric = generic.score(testFrame);
            Scope.track(scoredGeneric);

            assertFrameEquals(scoredOriginal, scoredGeneric, 0);
        } finally {
            System.clearProperty("sys.ai.h2o.pojo.import.enabled");
            Scope.exit();
        }
    }

    @Test
    public void testGAM_binomial() throws IOException {
        final GAMModel.GAMParameters gamParameters = new GAMModel.GAMParameters();
        gamParameters._family = GLMModel.GLMParameters.Family.binomial;
        gamParameters._response_column = "CAPSULE";
        gamParameters._seed = 0XFEED;
        gamParameters._ignored_columns = new String[] {"ID"};
        gamParameters._alpha = new double[]{0.5};
        gamParameters._lambda_search = false;
        gamParameters._gam_columns = new String[][]{{"AGE"}};

        checkGAM(gamParameters, "./smalldata/prostate/prostate.csv");
    }

    @Test
    public void testGAM_gaussian() throws IOException {
        final GAMModel.GAMParameters gamParameters = new GAMModel.GAMParameters();
        gamParameters._family = GLMModel.GLMParameters.Family.gaussian;
        gamParameters._response_column = "C21";
        gamParameters._seed = 0XFEED;
        gamParameters._alpha = new double[]{0.0};
        gamParameters._lambda = new double[]{0.0};
        gamParameters._lambda_search = false;
        gamParameters._gam_columns = new String[][]{{"C11"}, {"C12"}, {"C13"}};
        gamParameters._max_iterations = 3;
        gamParameters._scale = new double[]{1.0, 1.0, 1.0};

        checkGAM(gamParameters, "./smalldata/glm_test/gaussian_20cols_10000Rows.csv", "C1", "C2");
    }

    @Test
    public void testGAM_multinomial() throws IOException {
        final GAMModel.GAMParameters gamParameters = new GAMModel.GAMParameters();
        gamParameters._family = GLMModel.GLMParameters.Family.multinomial;
        gamParameters._response_column = "C11";
        gamParameters._seed = 0XFEED;
        gamParameters._alpha = new double[]{0.0};
        gamParameters._lambda = new double[]{0.0};
        gamParameters._lambda_search = false;
        gamParameters._gam_columns = new String[][]{{"C6"}, {"C7"}, {"C8"}};
        gamParameters._max_iterations = 3;
        gamParameters._scale = new double[]{1.0, 1.0, 1.0};

        checkGAM(gamParameters, "./smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv", "C1", "C2");
    }

    private void checkGAM(GAMModel.GAMParameters gamParameters, String dataset, String... catCols) throws IOException {
        try {
            Scope.enter();

            final Frame trainingFrame = parseTestFile(dataset);
            if (gamParameters._family == GLMModel.GLMParameters.Family.binomial || 
                    gamParameters._family == GLMModel.GLMParameters.Family.multinomial) {
                trainingFrame.toCategoricalCol(gamParameters._response_column);
            }
            for (String catCol : catCols) {
                trainingFrame.toCategoricalCol(catCol);
            }
            Scope.track(trainingFrame);
            gamParameters._train = trainingFrame._key;

            // 0. Train a GAM model
            final GAM gam = new GAM(gamParameters);
            final GAMModel gamModel = gam.trainModel().get();
            assertNotNull(gamModel);
            Scope.track_generic(gamModel);

            final Frame originalModelPredictions = gamModel.score(trainingFrame);
            Scope.track(originalModelPredictions);

            // 1. Sanity check - make sure MOJO is actually consistent with in-H2O predictions before go further
            assertTrue(gamModel.testJavaScoring(trainingFrame, originalModelPredictions, 1e-6));

            // 2. Import MOJO into a Generic model
            final File mojoFile = File.createTempFile("mojo", "zip");
            gamModel.getMojo().writeTo(new FileOutputStream(mojoFile));
            GenericModel genericModel = Generic.importMojoModel(mojoFile.getAbsolutePath(), false);
            Scope.track_generic(genericModel);
            assertTrue(genericModel.hasBehavior(GenericModel.ModelBehavior.USE_MOJO_PREDICT));
            
            // 3. Score Generic model
            final Frame genericModelPredictions = genericModel.score(trainingFrame);
            Scope.track(genericModelPredictions);

            // Compare - predictions should be almost identical (up to the same tolerance as in-H2O and MOJO model predictions)
            assertTrue(TestUtil.compareFrames(genericModelPredictions, originalModelPredictions, 1e-6));

            // for now, we just produce regular metrics (not GAM specific metrics)
            Key<ModelMetrics>[] genericModelMetrics = genericModel._output.getModelMetrics();
            assertTrue(genericModelMetrics.length > 0);
            ModelMetrics mm = DKV.getGet(genericModelMetrics[0]);

            assertEquals(gamModel._output._training_metrics._MSE, mm._MSE, 1e-6);
            assertEquals(gamModel._output._training_metrics._nobs, mm._nobs, 1e-6);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testAutoEncoderWithParametrizedEncoding() throws IOException {
        try {
            Scope.enter();
            Frame train = TestUtil.parseAndTrackTestFile("smalldata/airlines/AirlinesTrain.csv.zip");

            DeepLearningModel.DeepLearningParameters p = new DeepLearningModel.DeepLearningParameters();
            p._train = train._key;
            p._autoencoder = true;
            p._seed = 0xDECAF;
            p._hidden = new int[]{10, 5, 3};
            p._adaptive_rate = true;
            p._l1 = 1e-4;
            p._activation = DeepLearningModel.DeepLearningParameters.Activation.Tanh;
            p._max_w2 = 10;
            p._train_samples_per_iteration = -1;
            p._loss = DeepLearningModel.DeepLearningParameters.Loss.Huber;
            p._epochs = 0.2;
            p._force_load_balance = true;
            p._score_training_samples = 0;
            p._score_validation_samples = 0;
            p._reproducible = true;
            p._categorical_encoding = Model.Parameters.CategoricalEncodingScheme.Eigen;

            DeepLearning dl = new DeepLearning(p);
            DeepLearningModel autoencoder = dl.trainModel().get();
            Assert.assertNotNull(autoencoder);
            Scope.track_generic(autoencoder);

            // first make sure MOJO predict works
            Frame reconstructed = autoencoder.score(train);
            Scope.track(reconstructed);
            Assert.assertTrue(autoencoder.testJavaScoring(train, reconstructed, 1e-6));

            File mojoFile = temporaryFolder.newFile();
            URI mojoURI = autoencoder.exportMojo(mojoFile.getAbsolutePath(), true);

            // now check that imported AE mojo produces the same results as original model
            GenericModel genericAE = Generic.importMojoModel(mojoURI);
            assertNotNull(genericAE);
            Scope.track_generic(genericAE);

            Frame reconstructedAE = genericAE.score(train);
            Scope.track(reconstructedAE);
            assertFrameEquals(reconstructed, reconstructedAE, 1e-6);
        } finally {
            Scope.exit();
        }
    }

}
