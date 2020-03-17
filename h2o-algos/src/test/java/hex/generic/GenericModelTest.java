package hex.generic;

import hex.ModelCategory;
import hex.ModelMetricsBinomial;
import hex.deeplearning.DeepLearning;
import hex.deeplearning.DeepLearningModel;
import hex.ensemble.Metalearner;
import hex.ensemble.StackedEnsemble;
import hex.ensemble.StackedEnsembleModel;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import hex.tree.isofor.IsolationForest;
import hex.tree.isofor.IsolationForestModel;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import water.*;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.FrameUtilsTest;

import java.io.*;
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
        Key mojo = null;
        GBMModel model = null;
        GenericModel genericModel = null;
        Frame trainingFrame = null;
        Frame testFrame = null;
        Frame predictions = null;
        try {
            // Create new GBM model
            trainingFrame = parse_test_file("./smalldata/testng/airlines_train.csv");
            testFrame = parse_test_file("./smalldata/testng/airlines_test.csv");
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = "IsDepDelayed";
            parms._ntrees = 1;

            GBM job = new GBM(parms);
            model = job.trainModel().get();
            assertEquals(model._output.getModelCategory(), ModelCategory.Binomial);
            final ByteArrayOutputStream originalModelMojo = new ByteArrayOutputStream();
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            model.getMojo().writeTo(originalModelMojo);
            model.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            genericModel = trainAndCheck(generic);


            assertNotNull(genericModel._output._training_metrics);
            assertTrue(genericModel._output._training_metrics instanceof ModelMetricsBinomial);

            predictions = genericModel.score(testFrame);

            final boolean equallyScored = genericModel.testJavaScoring(testFrame, predictions, 0);
            assertTrue(equallyScored);
        } finally {
            if (model != null) model.remove();
            if (mojo != null) mojo.remove();
            if (genericModel != null) genericModel.remove();
            if (trainingFrame != null) trainingFrame.remove();
            if (testFrame != null) testFrame.remove();
            if (predictions != null) predictions.remove();
        }
    }

    @Test
    public void testJavaScoring_drf_binomial() throws Exception {
        Key mojo = null;
        DRFModel model = null;
        GenericModel genericModel = null;
        Frame trainingFrame = null;
        Frame testFrame = null;
        Frame predictions = null;
        try {
            trainingFrame = parse_test_file("./smalldata/testng/airlines_train.csv");
            testFrame = parse_test_file("./smalldata/testng/airlines_test.csv");
            DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = "IsDepDelayed";
            parms._ntrees = 1;

            DRF job = new DRF(parms);
            model = job.trainModel().get();
            assertEquals(model._output.getModelCategory(), ModelCategory.Binomial);
            final ByteArrayOutputStream originalModelMojo = new ByteArrayOutputStream();
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            model.getMojo().writeTo(originalModelMojo);
            model.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            genericModel = trainAndCheck(generic);

            predictions = genericModel.score(testFrame);
            assertEquals(2691, predictions.numRows());

            final boolean equallyScored = genericModel.testJavaScoring(testFrame, predictions, 0);
            assertTrue(equallyScored);
        } finally {
            if (model != null) model.remove();
            if (mojo != null) mojo.remove();
            if (genericModel != null) genericModel.remove();
            if (trainingFrame != null) trainingFrame.remove();
            if (testFrame != null) testFrame.remove();
            if (predictions != null) predictions.remove();
        }
    }

    @Test
    public void testJavaScoring_irf_binomial() throws Exception {
        Key mojo = null;
        IsolationForestModel model = null;
        GenericModel genericModel = null;
        Frame trainingFrame = null;
        Frame testFrame = null;
        Frame predictions = null;
        try {
            trainingFrame = parse_test_file("./smalldata/testng/airlines_train.csv");
            testFrame = parse_test_file("./smalldata/testng/airlines_test.csv");
            IsolationForestModel.IsolationForestParameters parms = new IsolationForestModel.IsolationForestParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = "IsDepDelayed";
            parms._ntrees = 1;

            IsolationForest job = new IsolationForest(parms);
            model = job.trainModel().get();
            assertEquals(model._output.getModelCategory(), ModelCategory.AnomalyDetection);
            final ByteArrayOutputStream originalModelMojo = new ByteArrayOutputStream();
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            model.getMojo().writeTo(originalModelMojo);
            model.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            genericModel = trainAndCheck(generic);

            predictions = genericModel.score(testFrame);
            assertEquals(2691, predictions.numRows());

            final boolean equallyScored = genericModel.testJavaScoring(testFrame, predictions, 0);
            assertTrue(equallyScored);
        } finally {
            if (model != null) model.remove();
            if (mojo != null) mojo.remove();
            if (genericModel != null) genericModel.remove();
            if (trainingFrame != null) trainingFrame.remove();
            if (testFrame != null) testFrame.remove();
            if (predictions != null) predictions.remove();
        }
    }

    @Test
    public void testJavaScoring_gbm_regression() throws Exception {
        Key mojo = null;
        GBMModel model = null;
        GenericModel genericModel = null;
        Frame trainingFrame = null;
        Frame testFrame = null;
        Frame predictions = null;
        try {
            trainingFrame = parse_test_file("./smalldata/testng/airlines_train.csv");
            testFrame = parse_test_file("./smalldata/testng/airlines_test.csv");
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = "Distance";
            parms._ntrees = 1;

            GBM job = new GBM(parms);
            model = job.trainModel().get();
            assertEquals(model._output.getModelCategory(), ModelCategory.Regression);
            final ByteArrayOutputStream originalModelMojo = new ByteArrayOutputStream();
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            model.getMojo().writeTo(originalModelMojo);
            model.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            genericModel = trainAndCheck(generic);

            predictions = genericModel.score(testFrame);
            assertEquals(2691, predictions.numRows());

            final boolean equallyScored = genericModel.testJavaScoring(testFrame, predictions, 0);
            assertTrue(equallyScored);
        } finally {
            if (model != null) model.remove();
            if (mojo != null) mojo.remove();
            if (genericModel != null) genericModel.remove();
            if (trainingFrame != null) trainingFrame.remove();
            if (testFrame != null) testFrame.remove();
            if (predictions != null) predictions.remove();
        }
    }

    @Test
    public void testJavaScoring_drf_regression() throws Exception {
        Key mojo = null;
        DRFModel model = null;
        GenericModel genericModel = null;
        Frame trainingFrame = null;
        Frame testFrame = null;
        Frame predictions = null;
        try {
            trainingFrame = parse_test_file("./smalldata/testng/airlines_train.csv");
            testFrame = parse_test_file("./smalldata/testng/airlines_test.csv");
            DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = "Distance";
            parms._ntrees = 1;

            DRF job = new DRF(parms);
            model = job.trainModel().get();
            assertEquals(model._output.getModelCategory(), ModelCategory.Regression);
            final ByteArrayOutputStream originalModelMojo = new ByteArrayOutputStream();
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            model.getMojo().writeTo(originalModelMojo);
            model.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            genericModel = trainAndCheck(generic);

            predictions = genericModel.score(testFrame);
            assertEquals(2691, predictions.numRows());

            final boolean equallyScored = genericModel.testJavaScoring(testFrame, predictions, 0);
            assertTrue(equallyScored);
        } finally {
            if (model != null) model.remove();
            if (mojo != null) mojo.remove();
            if (genericModel != null) genericModel.remove();
            if (trainingFrame != null) trainingFrame.remove();
            if (testFrame != null) testFrame.remove();
            if (predictions != null) predictions.remove();
        }
    }

    @Test
    public void testJavaScoring_irf_numerical() throws Exception {
        Key mojo = null;
        IsolationForestModel model = null;
        GenericModel genericModel = null;
        Frame trainingFrame = null;
        Frame testFrame = null;
        Frame predictions = null;
        try {
            trainingFrame = parse_test_file("./smalldata/testng/airlines_train.csv");
            testFrame = parse_test_file("./smalldata/testng/airlines_test.csv");
            IsolationForestModel.IsolationForestParameters parms = new IsolationForestModel.IsolationForestParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = "Distance";
            parms._ntrees = 1;

            IsolationForest job = new IsolationForest(parms);
            model = job.trainModel().get();
            assertEquals(model._output.getModelCategory(), ModelCategory.AnomalyDetection);
            final ByteArrayOutputStream originalModelMojo = new ByteArrayOutputStream();
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            model.getMojo().writeTo(originalModelMojo);
            model.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            genericModel = trainAndCheck(generic);

            predictions = genericModel.score(testFrame);
            assertEquals(2691, predictions.numRows());

            final boolean equallyScored = genericModel.testJavaScoring(testFrame, predictions, 0);
            assertTrue(equallyScored);
        } finally {
            if (model != null) model.remove();
            if (mojo != null) mojo.remove();
            if (genericModel != null) genericModel.remove();
            if (trainingFrame != null) trainingFrame.remove();
            if (testFrame != null) testFrame.remove();
            if (predictions != null) predictions.remove();
        }
    }

    @Test
    public void testJavaScoring_glm() throws Exception {
        Key mojo = null;
        GLMModel model = null;
        GenericModel genericModel = null;
        Frame trainingFrame = null;
        Frame testFrame = null;
        Frame predictions = null;
        try {
            trainingFrame = parse_test_file("./smalldata/testng/airlines_train.csv");
            testFrame = parse_test_file("./smalldata/testng/airlines_test.csv");
            GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = "Distance";

            GLM job = new GLM(parms);
            model = job.trainModel().get();
            assertEquals(model._output.getModelCategory(), ModelCategory.Regression);
            final ByteArrayOutputStream originalModelMojo = new ByteArrayOutputStream();
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            model.getMojo().writeTo(originalModelMojo);
            model.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            genericModel = trainAndCheck(generic);

            predictions = genericModel.score(testFrame);
            assertEquals(2691, predictions.numRows());

            final boolean equallyScored = genericModel.testJavaScoring(testFrame, predictions, 0);
            assertTrue(equallyScored);
        } finally {
            if (model != null) model.remove();
            if (mojo != null) mojo.remove();
            if (genericModel != null) genericModel.remove();
            if (trainingFrame != null) trainingFrame.remove();
            if (testFrame != null) testFrame.remove();
            if (predictions != null) predictions.remove();
        }
    }

    @Test
    public void testJavaScoring_gbm_multinomial() throws Exception {
        Key mojo = null;
        GBMModel model = null;
        GenericModel genericModel = null;
        Frame trainingFrame = null;
        Frame testFrame = null;
        Frame predictions = null;
        try {
            // Create new GBM model
            trainingFrame = parse_test_file("./smalldata/testng/airlines_train.csv");
            testFrame = parse_test_file("./smalldata/testng/airlines_test.csv");
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = "Origin";
            parms._ntrees = 1;

            GBM job = new GBM(parms);
            model = job.trainModel().get();
            assertEquals(model._output.getModelCategory(), ModelCategory.Multinomial);
            final ByteArrayOutputStream originalModelMojo = new ByteArrayOutputStream();
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            model.getMojo().writeTo(originalModelMojo);
            model.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            genericModel = trainAndCheck(generic);
            assertNotNull(genericModel._output._model_summary);
            assertNotNull(genericModel._output._variable_importances);

            predictions = genericModel.score(testFrame);
            assertEquals(2691, predictions.numRows());

            final boolean equallyScored = genericModel.testJavaScoring(testFrame, predictions, 0);
            assertTrue(equallyScored);
        } finally {
            if (model != null) model.remove();
            if (mojo != null) mojo.remove();
            if (genericModel != null) genericModel.remove();
            if (trainingFrame != null) trainingFrame.remove();
            if (testFrame != null) testFrame.remove();
            if (predictions != null) predictions.remove();
        }
    }

    @Test
    public void testJavaScoring_drf_multinomial() throws Exception {
        Key mojo = null;
        DRFModel model = null;
        GenericModel genericModel = null;
        Frame trainingFrame = null;
        Frame testFrame = null;
        Frame predictions = null;
        try {
            trainingFrame = parse_test_file("./smalldata/testng/airlines_train.csv");
            testFrame = parse_test_file("./smalldata/testng/airlines_test.csv");
            DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = "Origin";
            parms._ntrees = 1;

            DRF job = new DRF(parms);
            model = job.trainModel().get();
            assertEquals(model._output.getModelCategory(), ModelCategory.Multinomial);
            final ByteArrayOutputStream originalModelMojo = new ByteArrayOutputStream();
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            model.getMojo().writeTo(originalModelMojo);
            model.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            genericModel = trainAndCheck(generic);

            predictions = genericModel.score(testFrame);
            assertEquals(2691, predictions.numRows());

            final boolean equallyScored = genericModel.testJavaScoring(testFrame, predictions, 0);
            assertTrue(equallyScored);
        } finally {
            if (model != null) model.remove();
            if (mojo != null) mojo.remove();
            if (genericModel != null) genericModel.remove();
            if (trainingFrame != null) trainingFrame.remove();
            if (testFrame != null) testFrame.remove();
            if (predictions != null) predictions.remove();
        }
    }

    @Test
    public void testJavaScoring_irf_multinomial() throws Exception {
        Key mojo = null;
        IsolationForestModel model = null;
        GenericModel genericModel = null;
        Frame trainingFrame = null;
        Frame testFrame = null;
        Frame predictions = null;
        try {
            trainingFrame = parse_test_file("./smalldata/testng/airlines_train.csv");
            testFrame = parse_test_file("./smalldata/testng/airlines_test.csv");
            IsolationForestModel.IsolationForestParameters parms = new IsolationForestModel.IsolationForestParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = "Origin";
            parms._ntrees = 1;

            IsolationForest job = new IsolationForest(parms);
            model = job.trainModel().get();
            assertEquals(model._output.getModelCategory(), ModelCategory.AnomalyDetection);
            final ByteArrayOutputStream originalModelMojo = new ByteArrayOutputStream();
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            model.getMojo().writeTo(originalModelMojo);
            model.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            genericModel = trainAndCheck(generic);

            predictions = genericModel.score(testFrame);
            assertEquals(2691, predictions.numRows());

            final boolean equallyScored = genericModel.testJavaScoring(testFrame, predictions, 0);
            assertTrue(equallyScored);
        } finally {
            if (model != null) model.remove();
            if (mojo != null) mojo.remove();
            if (genericModel != null) genericModel.remove();
            if (trainingFrame != null) trainingFrame.remove();
            if (testFrame != null) testFrame.remove();
            if (predictions != null) predictions.remove();
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
        GBMModel gbm = null;
        Key mojo = null;
        GenericModel genericModel = null;
        Frame trainingFrame = null;
        try {
            // Create new GBM model
            trainingFrame = parse_test_file("./smalldata/gbm_test/Mfgdata_gaussian_GBM_testing.csv");
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = trainingFrame._names[1];
            parms._ntrees = 1;

            GBM job = new GBM(parms);
            gbm = job.trainModel().get();
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            gbm.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            // Create Generic model from given imported MOJO
            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            genericModel = trainAndCheck(generic);

            // Compare the two MOJOs byte-wise
            final File genericModelMojoFile = File.createTempFile("mojo", "zip");
            genericModel.getMojo().writeTo(new FileOutputStream(genericModelMojoFile));
            assertArrayEquals(FileUtils.readFileToByteArray(originalModelMojoFile), FileUtils.readFileToByteArray(genericModelMojoFile));
            
        } finally {
            if(gbm != null) gbm.remove();
            if (mojo != null) mojo.remove();
            if (genericModel != null) genericModel.remove();
            if (trainingFrame != null) trainingFrame.remove();
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
        DRFModel originalModel = null;
        Key mojo = null;
        GenericModel genericModel = null;
        Frame trainingFrame = null;
        try {
            // Create new DRF model
            trainingFrame = parse_test_file("./smalldata/gbm_test/Mfgdata_gaussian_GBM_testing.csv");
            DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = trainingFrame._names[1];
            parms._ntrees = 1;

            DRF job = new DRF(parms);
            originalModel = job.trainModel().get();
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            originalModel.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            // Create Generic model from given imported MOJO
            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            genericModel = trainAndCheck(generic);

            // Compare the two MOJOs byte-wise
            final File genericModelMojoFile = File.createTempFile("mojo", "zip");
            genericModel.getMojo().writeTo(new FileOutputStream(genericModelMojoFile));
            assertArrayEquals(FileUtils.readFileToByteArray(originalModelMojoFile), FileUtils.readFileToByteArray(genericModelMojoFile));

        } finally {
            if(originalModel != null) originalModel.remove();
            if (mojo != null) mojo.remove();
            if (genericModel != null) genericModel.remove();
            if (trainingFrame != null) trainingFrame.remove();
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
        IsolationForestModel originalModel = null;
        Key mojo = null;
        GenericModel genericModel = null;
        Frame trainingFrame = null;
        try {
            // Create new IRF model
            trainingFrame = parse_test_file("./smalldata/gbm_test/Mfgdata_gaussian_GBM_testing.csv");
            IsolationForestModel.IsolationForestParameters parms = new IsolationForestModel.IsolationForestParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = trainingFrame._names[1];
            parms._ntrees = 1;

            IsolationForest job = new IsolationForest(parms);
            originalModel = job.trainModel().get();
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            originalModel.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            // Create Generic model from given imported MOJO
            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            genericModel = trainAndCheck(generic);

            // Compare the two MOJOs byte-wise
            final File genericModelMojoFile = File.createTempFile("mojo", "zip");
            genericModel.getMojo().writeTo(new FileOutputStream(genericModelMojoFile));
            assertArrayEquals(FileUtils.readFileToByteArray(originalModelMojoFile), FileUtils.readFileToByteArray(genericModelMojoFile));

        } finally {
            if(originalModel != null) originalModel.remove();
            if (mojo != null) mojo.remove();
            if (genericModel != null) genericModel.remove();
            if (trainingFrame != null) trainingFrame.remove();
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
        GLMModel originalModel = null;
        Key mojo = null;
        GenericModel genericModel = null;
        Frame trainingFrame = null;
        try {
            trainingFrame = parse_test_file("./smalldata/gbm_test/Mfgdata_gaussian_GBM_testing.csv");
            GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = trainingFrame._names[1];

            GLM job = new GLM(parms);
            originalModel = job.trainModel().get();
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            originalModel.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            // Create Generic model from given imported MOJO
            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            genericModel = trainAndCheck(generic);

            // Compare the two MOJOs byte-wise
            final File genericModelMojoFile = File.createTempFile("mojo", "zip");
            genericModel.getMojo().writeTo(new FileOutputStream(genericModelMojoFile));
            assertArrayEquals(FileUtils.readFileToByteArray(originalModelMojoFile), FileUtils.readFileToByteArray(genericModelMojoFile));

        } finally {
            if(originalModel != null) originalModel.remove();
            if (mojo != null) mojo.remove();
            if (genericModel != null) genericModel.remove();
            if (trainingFrame != null) trainingFrame.remove();
        }
    }

    @Test
    public void downloadable_mojo_glm_binomial() throws IOException {
        GLMModel originalModel = null;
        Key mojo = null;
        GenericModel genericModel = null;
        Frame trainingFrame = null;
        try {
            trainingFrame = parse_test_file("./smalldata/testng/airlines_train.csv");
            GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._family = GLMModel.GLMParameters.Family.binomial;
            parms._response_column = "IsDepDelayed";

            GLM job = new GLM(parms);
            originalModel = job.trainModel().get();
            final File originalModelMojoFile = File.createTempFile("mojo", "zip");
            originalModel.getMojo().writeTo(new FileOutputStream(originalModelMojoFile));

            mojo = importMojo(originalModelMojoFile.getAbsolutePath());

            // Create Generic model from given imported MOJO
            final GenericModelParameters genericModelParameters = new GenericModelParameters();
            genericModelParameters._model_key = mojo;
            final Generic generic = new Generic(genericModelParameters);
            genericModel = trainAndCheck(generic);

            // Compare the two MOJOs byte-wise
            final File genericModelMojoFile = File.createTempFile("mojo", "zip");
            genericModel.getMojo().writeTo(new FileOutputStream(genericModelMojoFile));
            assertArrayEquals(FileUtils.readFileToByteArray(originalModelMojoFile), FileUtils.readFileToByteArray(genericModelMojoFile));

        } finally {
            if (originalModel != null) originalModel.remove();
            if (mojo != null) mojo.remove();
            if (genericModel != null) genericModel.remove();
            if (trainingFrame != null) trainingFrame.remove();
        }
    }

    @Test
    public void testJavaScoring_deeplearning() throws Exception {
        try {
            Scope.enter();
            // Create new GBM model
            final Frame trainingFrame = parse_test_file("./smalldata/testng/airlines_train.csv");
            Scope.track(trainingFrame);
            final Frame testFrame = parse_test_file("./smalldata/testng/airlines_test.csv");
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

            final Frame predictions = genericModel.score(testFrame);
            Scope.track(predictions);

            final boolean equallyScored = genericModel.testJavaScoring(testFrame, predictions, 0);
            assertTrue(equallyScored);

        } finally {
            Scope.exit();
        }
    }

    @Test
    public void downloadable_mojo_deeplearning() throws IOException {
        try {
            Scope.enter();
            final Frame trainingFrame = parse_test_file("./smalldata/testng/airlines_train.csv");
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

            final Frame trainingFrame = parse_test_file("./smalldata/testng/airlines_train.csv");
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
            final Frame testFrame = parse_test_file("./smalldata/testng/airlines_test.csv");
            Scope.track(testFrame);
            final Frame predictions = genericModel.score(testFrame);
            Scope.track(predictions);

            final boolean equallyScored = genericModel.testJavaScoring(testFrame, predictions, 0);

            final Frame originalModelPredictions = stackedEnsembleModel.score(testFrame);
            Scope.track(originalModelPredictions);
            assertTrue(FrameUtilsTest.compareFrames(predictions, originalModelPredictions));
            
            assertTrue(equallyScored);
        } finally {
            Scope.exit();
        }
    }


    private GenericModel trainAndCheck(Generic builder) {
        GenericModel model = builder.trainModel().get();
        assertNotNull(model);
        assertFalse(model.needsPostProcess());
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

}
