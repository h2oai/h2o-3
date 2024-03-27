package hex.Infogram;

import hex.SplitFrame;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;

import static hex.Infogram.InfogramUtils.strVec2array;
import static hex.Infogram.InfogramUtils.vec2array;
import static hex.Model.Parameters.FoldAssignmentScheme.Modulo;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static water.util.ArrayUtils.sum;

@RunWith(H2ORunner.class)
@CloudSize(1)
/***
 * Tests in this file is for making sure validation dataset and cross-validation actually work.
 */
public class InfogramCVValidTest  extends TestUtil {

    /**
     * Test to check that both CV and validation dataset can be used
     */
    @Test
    public void testCVValidationCore() {
        try {
            Scope.enter();
            String[] colNames = new String[]{"diagnosis", "radius_mean", "texture_mean", "perimeter_mean", "area_mean",
                    "smoothness_mean", "compactness_mean", "concavity_mean", "concave_points_mean", "symmetry_mean",
                    "fractal_dimension_mean", "radius_se", "texture_se", "perimeter_se", "area_se", "smoothness_se",
                    "compactness_se", "concavity_se", "concave_points_se", "symmetry_se", "fractal_dimension_se",
                    "radius_worst", "texture_worst", "perimeter_worst", "area_worst", "smoothness_worst", "compactness_worst",
                    "concavity_worst", "concave_points_worst", "symmetry_worst", "fractal_dimension_worst"};
            Frame trainF = Scope.track(parseTestFile("smalldata/admissibleml_test/wdbc_changed.csv"));
            trainF.setNames(colNames);
            trainF.replace(0, trainF.vec("diagnosis").toCategoricalVec()).remove();
            DKV.put(trainF);

            // split into train/test
            SplitFrame sf = new SplitFrame(trainF, new double[] {0.7, 0.3}, null);
            sf.exec().get();
            Key[] splits = sf._destination_frames;
            Frame trainFrame = Scope.track((Frame) splits[0].get());
            Frame testFrame = Scope.track((Frame) splits[1].get());

            InfogramModel.InfogramParameters params = new InfogramModel.InfogramParameters();
            params._response_column = "diagnosis";
            params._train = trainFrame._key;
            params._algorithm = InfogramModel.InfogramParameters.Algorithm.gbm;
            params._top_n_features = 50;
            params._algorithm_params = "{\"ntrees\": [8]}";
            params._seed = 12345;

            InfogramModel infogramModel = new Infogram(params).trainModel().get(); // model with training dataset only
            Scope.track_generic(infogramModel);

            params._valid = testFrame._key; // model with training, validation datasets
            InfogramModel infogramModelV = new Infogram(params).trainModel().get(); 
            Scope.track_generic(infogramModelV);
            
            params._nfolds = 3; // model with training, validation datasets and cross-validation
            params._fold_assignment = Modulo;
            InfogramModel infogramModelVCV = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModelVCV);
            
            params._valid = null; // model with training and cross-validation
            InfogramModel infogramModelCV = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModelCV);
            

            // check all four models have same training relcmi frame;
            Frame relCmiTrain = DKV.getGet(infogramModel._output._admissible_score_key);
            Scope.track(relCmiTrain);
            Frame relCmiTrainV = DKV.getGet(infogramModelV._output._admissible_score_key);
            Scope.track(relCmiTrainV);
            Frame relCmiTrainVCV = DKV.getGet(infogramModelVCV._output._admissible_score_key);
            Scope.track(relCmiTrainVCV);
            Frame relCmiTrainCV = DKV.getGet(infogramModelCV._output._admissible_score_key);
            Scope.track(relCmiTrainCV);
            TestUtil.assertFrameEquals(relCmiTrain, relCmiTrainV, 1e-6);
            TestUtil.assertFrameEquals(relCmiTrainVCV, relCmiTrainCV, 1e-6);
            TestUtil.assertFrameEquals(relCmiTrain, relCmiTrainVCV, 1e-6);
            
            // check two models have same validation relcmi frame;
            Frame relCmiValidV = DKV.getGet(infogramModelV._output._admissible_score_key_valid);
            Scope.track(relCmiValidV);
            Frame relCmiValidVCV = DKV.getGet(infogramModelVCV._output._admissible_score_key_valid);
            Scope.track(relCmiValidVCV);
            TestUtil.assertFrameEquals(relCmiValidV, relCmiValidVCV, 1e-6);
            
            // check two models with same cv relcmi frame;
            Frame relCmiCV = DKV.getGet(infogramModelCV._output._admissible_score_key_xval);
            Scope.track(relCmiCV);
            Frame relCmiVCV = DKV.getGet(infogramModelVCV._output._admissible_score_key_xval);
            Scope.track(relCmiVCV);
            TestUtil.assertFrameEquals(relCmiVCV, relCmiCV, 1e-6);

        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void testValidationCore() {
        try {
            Scope.enter();
            String[] colNames = new String[]{"diagnosis", "radius_mean", "texture_mean", "perimeter_mean", "area_mean",
                    "smoothness_mean", "compactness_mean", "concavity_mean", "concave_points_mean", "symmetry_mean",
                    "fractal_dimension_mean", "radius_se", "texture_se", "perimeter_se", "area_se", "smoothness_se",
                    "compactness_se", "concavity_se", "concave_points_se", "symmetry_se", "fractal_dimension_se",
                    "radius_worst", "texture_worst", "perimeter_worst", "area_worst", "smoothness_worst", "compactness_worst",
                    "concavity_worst", "concave_points_worst", "symmetry_worst", "fractal_dimension_worst"};
            Frame trainF = Scope.track(parseTestFile("smalldata/admissibleml_test/wdbc_changed.csv"));
            trainF.setNames(colNames);
            trainF.replace(0, trainF.vec("diagnosis").toCategoricalVec()).remove();
            DKV.put(trainF);

            // split into train/test
            SplitFrame sf = new SplitFrame(trainF, new double[] {0.7, 0.3}, null);
            sf.exec().get();
            Key[] splits = sf._destination_frames;
            Frame trainFrame = Scope.track((Frame) splits[0].get());
            Frame testFrame = Scope.track((Frame) splits[1].get());
            
            InfogramModel.InfogramParameters params = new InfogramModel.InfogramParameters();
            params._response_column = "diagnosis";
            params._train = trainFrame._key;
            params._algorithm = InfogramModel.InfogramParameters.Algorithm.gbm;
            params._top_n_features = 50;
            params._algorithm_params = "{\"ntrees\": [8]}";
            params._seed = 12345;

            InfogramModel infogramModel = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModel);
            assertTrue(infogramModel._output._admissible_score_key_valid == null);
            
            params._valid = testFrame._key;
            InfogramModel infogramModelV = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModelV);
            assertTrue(infogramModelV._output._admissible_score_key_valid != null);
            assertCorrectRuns(infogramModel, infogramModelV);
        } finally {
            Scope.exit();
        }
    }

    /***
     * Test the case where we call infogram model building multiple times to make sure it works.
     */
    @Test
    public void testValidationCoreDuplicate() {
        try {
            Scope.enter();
            String[] colNames = new String[]{"diagnosis", "radius_mean", "texture_mean", "perimeter_mean", "area_mean",
                    "smoothness_mean", "compactness_mean", "concavity_mean", "concave_points_mean", "symmetry_mean",
                    "fractal_dimension_mean", "radius_se", "texture_se", "perimeter_se", "area_se", "smoothness_se",
                    "compactness_se", "concavity_se", "concave_points_se", "symmetry_se", "fractal_dimension_se",
                    "radius_worst", "texture_worst", "perimeter_worst", "area_worst", "smoothness_worst", "compactness_worst",
                    "concavity_worst", "concave_points_worst", "symmetry_worst", "fractal_dimension_worst"};
            Frame trainF = Scope.track(parseTestFile("smalldata/admissibleml_test/wdbc_changed.csv"));
            trainF.setNames(colNames);
            trainF.replace(0, trainF.vec("diagnosis").toCategoricalVec()).remove();
            DKV.put(trainF);

            // split into train/test
            SplitFrame sf = new SplitFrame(trainF, new double[] {0.7, 0.3}, null);
            sf.exec().get();
            Key[] splits = sf._destination_frames;
            Frame trainFrame = Scope.track((Frame) splits[0].get());
            Frame testFrame = Scope.track((Frame) splits[1].get());

            InfogramModel.InfogramParameters params = new InfogramModel.InfogramParameters();
            params._response_column = "diagnosis";
            params._train = trainFrame._key;
            params._algorithm_params = "{\"ntrees\": [8]}";
            params._algorithm = InfogramModel.InfogramParameters.Algorithm.gbm;
            params._top_n_features = 50;
            params._seed = 12345;

            // check and make sure infogram model can be run multiple times
            InfogramModel infogramModel = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModel);
            InfogramModel infogramModel2 = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModel2);
            Frame relCmiFrame = DKV.getGet(infogramModel._output._admissible_score_key);
            Scope.track(relCmiFrame);
            Frame relCmiFrame2 = DKV.getGet(infogramModel2._output._admissible_score_key);
            Scope.track(relCmiFrame2);            
            TestUtil.assertFrameEquals(relCmiFrame, relCmiFrame2, 1e-6);
            
            params._valid = testFrame._key;
            InfogramModel infogramModelV = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModelV);
            Frame relCmiFrameV = DKV.getGet(infogramModelV._output._admissible_score_key);
            Scope.track(relCmiFrameV);
            InfogramModel infogramModelV2 = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModelV2);
            Frame relCmiFrameV2 = DKV.getGet(infogramModelV2._output._admissible_score_key);
            Scope.track(relCmiFrameV2);
            TestUtil.assertFrameEquals(relCmiFrameV, relCmiFrameV2, 1e-6);
        } finally {
            Scope.exit();
        }
    }
    

    @Test
    public void testCVCore() {
        try {
            Scope.enter();

            String[] colNames = new String[]{"diagnosis", "radius_mean", "texture_mean", "perimeter_mean", "area_mean",
                    "smoothness_mean", "compactness_mean", "concavity_mean", "concave_points_mean", "symmetry_mean",
                    "fractal_dimension_mean", "radius_se", "texture_se", "perimeter_se", "area_se", "smoothness_se",
                    "compactness_se", "concavity_se", "concave_points_se", "symmetry_se", "fractal_dimension_se",
                    "radius_worst", "texture_worst", "perimeter_worst", "area_worst", "smoothness_worst", "compactness_worst",
                    "concavity_worst", "concave_points_worst", "symmetry_worst", "fractal_dimension_worst"};
            Frame trainF = Scope.track(parseTestFile("smalldata/admissibleml_test/wdbc_changed.csv"));
            trainF.setNames(colNames);
            trainF.replace(0, trainF.vec("diagnosis").toCategoricalVec()).remove();
            DKV.put(trainF);
            InfogramModel.InfogramParameters params = new InfogramModel.InfogramParameters();
            params._response_column = "diagnosis";
            params._train = trainF._key;
            params._algorithm = InfogramModel.InfogramParameters.Algorithm.gbm;
            params._top_n_features = 5;
            params._seed = 12345;
            params._nfolds = 2;
            params._fold_assignment = Modulo;

            InfogramModel infogramModel = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModel);
            assertCorrectCVCore(infogramModel, params, trainF);
        } finally {
            Scope.exit();
        }
    }

    /***
     * Run infogram CV multiple times and make sure it works.
     */
    @Test
    public void testCVCoreDuplicate() {
        try {
            Scope.enter();

            String[] colNames = new String[]{"diagnosis", "radius_mean", "texture_mean", "perimeter_mean", "area_mean",
                    "smoothness_mean", "compactness_mean", "concavity_mean", "concave_points_mean", "symmetry_mean",
                    "fractal_dimension_mean", "radius_se", "texture_se", "perimeter_se", "area_se", "smoothness_se",
                    "compactness_se", "concavity_se", "concave_points_se", "symmetry_se", "fractal_dimension_se",
                    "radius_worst", "texture_worst", "perimeter_worst", "area_worst", "smoothness_worst", "compactness_worst",
                    "concavity_worst", "concave_points_worst", "symmetry_worst", "fractal_dimension_worst"};
            Frame trainF = Scope.track(parseTestFile("smalldata/admissibleml_test/wdbc_changed.csv"));
            trainF.setNames(colNames);
            trainF.replace(0, trainF.vec("diagnosis").toCategoricalVec()).remove();
            DKV.put(trainF);

            InfogramModel.InfogramParameters params = new InfogramModel.InfogramParameters();
            params._response_column = "diagnosis";
            params._train = trainF._key;
            params._algorithm = InfogramModel.InfogramParameters.Algorithm.gbm;
            params._top_n_features = 50;
            params._algorithm_params = "{\"ntrees\": [8]}";
            params._seed = 12345;
            params._nfolds = 3;
            params._fold_assignment = Modulo;

            InfogramModel infogramModel = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModel);
            InfogramModel infogramModel2 = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModel2);

            Frame relCmiFrameTrain = DKV.getGet(infogramModel._output._admissible_score_key);
            Scope.track(relCmiFrameTrain);
            Frame relCmiFrameTrain2 = DKV.getGet(infogramModel2._output._admissible_score_key);
            Scope.track(relCmiFrameTrain2);
            TestUtil.assertFrameEquals(relCmiFrameTrain, relCmiFrameTrain2, 1e-6);

            Frame relCmiFrameCV = DKV.getGet(infogramModel._output._admissible_score_key_xval);
            Scope.track(relCmiFrameCV);
            Frame relCmiFrameCV2 = DKV.getGet(infogramModel2._output._admissible_score_key_xval);
            Scope.track(relCmiFrameCV2);
            TestUtil.assertFrameEquals(relCmiFrameCV, relCmiFrameCV2, 1e-6);
        } finally {
            Scope.exit();
        }
    }

    /**
     * Test to check that both CV and validation dataset can be used
     */
    @Test
    public void testCVValidationSafe() {
        try {
            Scope.enter();
            Frame trainF = Scope.track(parseTestFile("smalldata/admissibleml_test/compas_full.csv"));
            trainF.replace(trainF.numCols()-1, trainF.vec("two_year_recid").toCategoricalVec()).remove();
            DKV.put(trainF);

            // split into train/test
            SplitFrame sf = new SplitFrame(trainF, new double[] {0.7, 0.3}, null);
            sf.exec().get();
            Key[] splits = sf._destination_frames;
            Frame trainFrame = Scope.track((Frame) splits[0].get());
            Frame testFrame = Scope.track((Frame) splits[1].get());

            InfogramModel.InfogramParameters params = new InfogramModel.InfogramParameters();
            params._response_column = "two_year_recid";
            params._train = trainFrame._key;
            params._protected_columns = new String[]{"sex","age","race"};
            params._algorithm = InfogramModel.InfogramParameters.Algorithm.gbm;
            params._ignored_columns = new String[]{"id"};
            params._top_n_features = 50;
            params._algorithm_params = "{\"ntrees\": [8]}";
            params._seed = 12345;

            InfogramModel infogramModel = new Infogram(params).trainModel().get(); // model with training dataset only
            Scope.track_generic(infogramModel);

            params._valid = testFrame._key; // model with training, validation datasets
            InfogramModel infogramModelV = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModelV);

            params._nfolds = 2; // model with training, validation datasets and cross-validation
            InfogramModel infogramModelVCV = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModelVCV);

            params._valid = null; // model with training and cross-validation
            InfogramModel infogramModelCV = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModelCV);


            // check all four models have same training relcmi frame;
            Frame relCmiTrain = DKV.getGet(infogramModel._output._admissible_score_key);
            Scope.track(relCmiTrain);
            Frame relCmiTrainV = DKV.getGet(infogramModelV._output._admissible_score_key);
            Scope.track(relCmiTrainV);
            Frame relCmiTrainVCV = DKV.getGet(infogramModelVCV._output._admissible_score_key);
            Scope.track(relCmiTrainVCV);
            Frame relCmiTrainCV = DKV.getGet(infogramModelCV._output._admissible_score_key);
            Scope.track(relCmiTrainCV);
            TestUtil.assertFrameEquals(relCmiTrain, relCmiTrainV, 1e-6);
            TestUtil.assertFrameEquals(relCmiTrainVCV, relCmiTrainCV, 1e-6);
            TestUtil.assertFrameEquals(relCmiTrain, relCmiTrainVCV, 1e-6);

            // check two models have same validation relcmi frame;
            Frame relCmiValidV = DKV.getGet(infogramModelV._output._admissible_score_key_valid);
            Scope.track(relCmiValidV);
            Frame relCmiValidVCV = DKV.getGet(infogramModelVCV._output._admissible_score_key_valid);
            Scope.track(relCmiValidVCV);
            TestUtil.assertFrameEquals(relCmiValidV, relCmiValidVCV, 1e-6);

            // check two models with same cv relcmi frame;
            Frame relCmiCV = DKV.getGet(infogramModelCV._output._admissible_score_key_xval);
            Scope.track(relCmiCV);
            Frame relCmiVCV = DKV.getGet(infogramModelVCV._output._admissible_score_key_xval);
            Scope.track(relCmiVCV);
            TestUtil.assertFrameEquals(relCmiVCV, relCmiCV, 1e-6);

        } finally {
            Scope.exit();
        }
    }
    
    public static void assertCorrectCVCore(InfogramModel infogramModel, InfogramModel.InfogramParameters params, Frame trainF) {
        long[] validNonZeroRows = new long[]{285, 284};
        double[][] cmiRaw = new double [][]{{0.0740886597141513, 0.06680054529745522, 0.0, 0.010589279492509235, 0.00354537095549301},
                {0.07689334414132229, 0.0, 0.028545715203748073, 0.002795096913999734, 0.006658503429222945}};
        String[][] validColNames = new String[][]{{"concave_points_worst", "texture_worst", "radius_worst", "area_worst", "perimeter_worst"},
                {"texture_worst", "concave_points_mean", "area_worst", "concave_points_worst", "radius_worst"}};
        double[] mainRelevance = new double[]{0.34372355964840223, 1.0, 0.22668974589585472, 0.04829270617635511, 
                0.12967891871933077};
        String[] mainColNames = new String[]{"concave_points_worst", "radius_worst", "concave_points_mean", 
                "perimeter_worst", "area_worst"};
        
        Frame relCmiKeyCV = DKV.getGet(infogramModel._output._admissible_score_key_xval);
        Scope.track(relCmiKeyCV);
        // check correct validation infogram averaging
        assertCorrectInfoGramMetrics(relCmiKeyCV, validNonZeroRows, cmiRaw, mainRelevance, mainColNames, validColNames);
    }

    /***
     * Test to check that the calculation of cmi, cmi_raw, admissible, admissible_index are all correct from the 
     * multiple folds.
     * 
     * @param relCmiKeyCV
     * @param nObs
     * @param cmiRaw
     * @param relevance
     * @param mainColNames
     * @param colNames
     */
    public static void assertCorrectInfoGramMetrics(Frame relCmiKeyCV, long[] nObs, double[][] cmiRaw, 
                                                    double[] relevance, String[] mainColNames, String[][] colNames) {
        double[] calculatedCmiRaw = vec2array(relCmiKeyCV.vec(5));
        ArrayList<String> calculatedColumns = new ArrayList(Arrays.asList(strVec2array(relCmiKeyCV.vec("column"))));
        // manually calculate the cmi_raw from intermediate results collected manually
        int numFold = nObs.length;
        int numPred = relevance.length;
        double totalNObs = 1.0/sum(nObs);
        double[] cmiRawManual = new double[numPred];
        for (int fIndex=0; fIndex < numFold; fIndex++) {    // compute across each fold
            ArrayList<String> colNameList = new ArrayList<>(Arrays.asList(colNames[fIndex]));
            for (int pIndex=0; pIndex < numPred; pIndex++) {    // same column name as calculatedCMI frame
                String currPredName = calculatedColumns.get(pIndex);
                int colIndex = colNameList.indexOf(currPredName); // corresponding index from frame
                if (colIndex >= 0)
                    cmiRawManual[pIndex] += cmiRaw[fIndex][colIndex]*totalNObs*nObs[fIndex];
            }
        }
        assertArrayEquals(calculatedCmiRaw, cmiRawManual, 1e-6);    // compare cmi_raw calculations
        // generate cmi and check that is correct
        double[] calculatedCmi = vec2array(relCmiKeyCV.vec(4));
        double[] manualCmi = new double[numPred];
        double oneOvermaxCMI = 1.0/ArrayUtils.maxValue(cmiRawManual);
        for (int pIndex=0; pIndex < numPred; pIndex++) {
            manualCmi[pIndex] = cmiRawManual[pIndex]*oneOvermaxCMI;
        }
        assertArrayEquals(calculatedCmi, manualCmi, 1e-6);          // compare cmi calculations
        // generate admissible and admissible_index, cmi sequence same as order in provided frame
        ArrayList<String> mainColumns = new ArrayList(Arrays.asList(mainColNames));
        double[] calculateAdmissible = vec2array(relCmiKeyCV.vec("admissible"));
        double[] calculateAdmissibleIndex = vec2array(relCmiKeyCV.vec("admissible_index"));
        double[] calculatedRelevance = vec2array(relCmiKeyCV.vec(3));
        double[] manualAdmissible = new double[numPred];
        double[] manualAdmissibleIndex = new double[numPred];
        double[] manualRelevance = new double[numPred];
        double scale = 1.0/Math.sqrt(2.0);
        for (int pIndex=0; pIndex < numPred; pIndex++) {
            String colName = calculatedColumns.get(pIndex);
            int mainIndex = mainColumns.indexOf(colName);
            manualRelevance[pIndex] = relevance[mainIndex];
            double temp = (1-manualRelevance[pIndex])*(1-manualRelevance[pIndex])+(1-manualCmi[pIndex])*(1-manualCmi[pIndex]);
            manualAdmissibleIndex[pIndex] = Math.sqrt(temp)*scale;
            manualAdmissible[pIndex] = manualRelevance[pIndex]>=0.1 && manualCmi[pIndex]>=0.1?1:0;

        }
        assertArrayEquals(manualAdmissible, calculateAdmissible, 1e-6); // compare admissible
        assertArrayEquals(manualAdmissibleIndex, calculateAdmissibleIndex, 1e-6);   // compare admissible_index
        assertArrayEquals(manualRelevance, calculatedRelevance, 1e-6);  // compare relevance
    }

    @Test
    public void testValidationSafe() {
        try {
            Scope.enter();
            Frame trainF = Scope.track(parseTestFile("smalldata/admissibleml_test/compas_full.csv"));
            trainF.replace(trainF.numCols()-1, trainF.vec("two_year_recid").toCategoricalVec()).remove();
            DKV.put(trainF);
            SplitFrame sf = new SplitFrame(trainF, new double[] {0.7, 0.3}, null);
            sf.exec().get();
            Key[] splits = sf._destination_frames;
            Frame trainFrame = Scope.track((Frame) splits[0].get());
            Frame testFrame = Scope.track((Frame) splits[1].get());
            
            InfogramModel.InfogramParameters params = new InfogramModel.InfogramParameters();
            params._response_column = "two_year_recid";
            params._train = trainFrame._key;
            params._protected_columns = new String[]{"sex","age","race"};
            params._algorithm = InfogramModel.InfogramParameters.Algorithm.gbm;
            params._ignored_columns = new String[]{"id"};
            params._top_n_features = 50;
            params._seed = 12345;
            params._algorithm_params = "{\"ntrees\": [8]}";

            InfogramModel infogramModel = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModel);
            assertTrue(infogramModel._output._admissible_score_key_valid==null);
            
            params._valid = testFrame._key;
            InfogramModel infogramModelV = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModelV);
            assertTrue(infogramModelV._output._admissible_score_key_valid!=null);
            assertCorrectRuns(infogramModel, infogramModelV);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testValidationSafeDuplicate() {
        try {
            Scope.enter();
            Frame trainF = Scope.track(parseTestFile("smalldata/admissibleml_test/compas_full.csv"));
            trainF.replace(trainF.numCols()-1, trainF.vec("two_year_recid").toCategoricalVec()).remove();
            DKV.put(trainF);
            SplitFrame sf = new SplitFrame(trainF, new double[] {0.7, 0.3}, null);
            sf.exec().get();
            Key[] splits = sf._destination_frames;
            Frame trainFrame = Scope.track((Frame) splits[0].get());
            Frame testFrame = Scope.track((Frame) splits[1].get());

            InfogramModel.InfogramParameters params = new InfogramModel.InfogramParameters();
            params._response_column = "two_year_recid";
            params._train = trainFrame._key;

            params._algorithm_params = "{\"ntrees\": [8]}";
            params._protected_columns = new String[]{"sex","age","race"};
            params._algorithm = InfogramModel.InfogramParameters.Algorithm.gbm;
            params._ignored_columns = new String[]{"id"};
            params._top_n_features = 50;
            params._seed = 12345;

            InfogramModel infogramModel = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModel);
            InfogramModel infogramModel2 = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModel2);
            Frame relCmiTrain = DKV.getGet(infogramModel._output._admissible_score_key);
            Scope.track(relCmiTrain);
            Frame relCmiTrain2 = DKV.getGet(infogramModel2._output._admissible_score_key);
            Scope.track(relCmiTrain2);
            TestUtil.assertFrameEquals(relCmiTrain, relCmiTrain2, 1e-6);
            
            params._valid = testFrame._key;
            InfogramModel infogramModelV = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModelV);
            InfogramModel infogramModelV2 = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModelV2);

            Frame relCmiVTr = DKV.getGet(infogramModelV._output._admissible_score_key);
            Scope.track(relCmiVTr);
            Frame relCmiV2Tr = DKV.getGet(infogramModelV2._output._admissible_score_key);
            Scope.track(relCmiV2Tr);
            TestUtil.assertFrameEquals(relCmiV2Tr, relCmiVTr, 1e-6);
            
            Frame relCmiV = DKV.getGet(infogramModelV._output._admissible_score_key_valid);
            Scope.track(relCmiV);
            Frame relCmiV2 = DKV.getGet(infogramModelV2._output._admissible_score_key_valid);
            Scope.track(relCmiV2);
            TestUtil.assertFrameEquals(relCmiV2, relCmiV, 1e-6);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testCVSafe() {
        try {
            Scope.enter();
            Frame trainF = Scope.track(parseTestFile("smalldata/admissibleml_test/compas_full.csv"));
            trainF.replace(trainF.numCols()-1, trainF.vec("two_year_recid").toCategoricalVec()).remove();
            DKV.put(trainF);
            
            InfogramModel.InfogramParameters params = new InfogramModel.InfogramParameters();
            params._response_column = "two_year_recid";
            params._train = trainF._key;
            params._protected_columns = new String[]{"sex","age","race"};
            params._algorithm = InfogramModel.InfogramParameters.Algorithm.gbm;
            params._ignored_columns = new String[]{"id"};
            params._top_n_features = 50;
            params._seed = 12345;
            params._nfolds = 2;
            params._fold_assignment = Modulo;

            InfogramModel infogramModel = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModel);
            assertCorrectCVSafe(infogramModel);
        } finally {
            Scope.exit();
        }
    }

    /**
     * make sure multiple runs with CV finish.
     */
    @Test
    public void testCVSafeDuplicate() {
        try {
            Scope.enter();
            Frame trainF = Scope.track(parseTestFile("smalldata/admissibleml_test/compas_full.csv"));
            trainF.replace(trainF.numCols()-1, trainF.vec("two_year_recid").toCategoricalVec()).remove();
            DKV.put(trainF);

            InfogramModel.InfogramParameters params = new InfogramModel.InfogramParameters();
            params._response_column = "two_year_recid";
            params._train = trainF._key;
            params._protected_columns = new String[]{"sex","age","race"};
            params._algorithm = InfogramModel.InfogramParameters.Algorithm.gbm;
            params._ignored_columns = new String[]{"id"};
            params._top_n_features = 50;
            params._seed = 12345;
            params._nfolds = 2;
            params._algorithm_params = "{\"ntrees\": [8]}";

            InfogramModel infogramModel = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModel);
            InfogramModel infogramModel2 = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModel2);
            // both runs should return same training relCMIframe contents
            Frame relCmiFrameTrain = DKV.getGet(infogramModel._output._admissible_score_key);
            Scope.track(relCmiFrameTrain);
            Frame relCmiFrameTrain2 = DKV.getGet(infogramModel2._output._admissible_score_key);
            Scope.track(relCmiFrameTrain2);
            TestUtil.assertFrameEquals(relCmiFrameTrain, relCmiFrameTrain2, 1e-6);
            // both runs should return same cross-validation relCMIframe contents
            Frame relCmiFrameValid = DKV.getGet(infogramModel._output._admissible_score_key_xval);
            Scope.track(relCmiFrameValid);
            Frame relCmiFrameTrainValid2 = DKV.getGet(infogramModel2._output._admissible_score_key_xval);
            Scope.track(relCmiFrameTrainValid2);
            TestUtil.assertFrameEquals(relCmiFrameValid, relCmiFrameTrainValid2, 1e-6);
        } finally {
            Scope.exit();
        }
    }


    public static void assertCorrectCVSafe(InfogramModel infogramModel) {
        long[] validNonZeroRows = new long[]{3454,3453};
        double[][] cmiRaw = new double[][]{{0.5724240823304241, 0.49538631399928285, 0.08931514015582542, 0.08931514015582542, 0.07769862704423769, 0.07769862704423769, 0.06339896155155711, 0.04204048453203135, 0.025171185599880297, 0.017537162997962152, 0.009121361241773651, 0.011998999469123306, 0.010971434455554263, 0.008674291823460023, 0.0076452997045523},
                {0.5463757380373683, 0.5044681055598337, 0.09110353480455802, 0.09110353480455802, 0.06541477175441757, 0.06541477175441757, 0.05996846597569727, 0.03708853658710587, 0.027427326130934904, 0.016798382736979645, 0.013812236652105958, 0.01150201605853618, 0.00716852653446054, 0.00694939077877077, 0.005481070985486669}};
        String[][] validColNames = new String[][]{{"end", "event", "priors_count", "priors_count.1", "decile_score", "decile_score.1", "score_text", "v_decile_score", "v_score_text", "days_b_screening_arrest", "start", "c_charge_degree", "juv_other_count", "juv_fel_count", "juv_misd_count"},
                {"end", "event", "priors_count", "priors_count.1", "decile_score", "decile_score.1", "score_text", "v_decile_score", "v_score_text", "juv_other_count", "days_b_screening_arrest", "start", "juv_misd_count", "juv_fel_count", "c_charge_degree"}};
        double[] mainRelevance = new double[]{1.0,  0.22232788946634713, 0.010488736091117714, 0.0, 
                0.016795647759374355, 0.0, 0.0018857970710325917, 0.00401633133386085, 0.01452655439713657, 
                2.574920854711118E-4, 0.004113058086770913, 0.0012933419572252322, 0.0014052131829135543, 
                0.0022520089083028056, 0.0013904296902873732};
        String[] mainColNames = new String[]{"end", "event", "priors_count", "priors_count.1", "decile_score", 
                "decile_score.1", "score_text", "v_decile_score", "start", "v_score_text", "days_b_screening_arrest", 
                "c_charge_degree", "juv_other_count", "juv_misd_count", "juv_fel_count"};
        Frame relCmiKeyCV = DKV.getGet(infogramModel._output._admissible_score_key_xval);
        Scope.track(relCmiKeyCV);
        // check correct validation infogram averaging
        assertCorrectInfoGramMetrics(relCmiKeyCV, validNonZeroRows, cmiRaw, mainRelevance, mainColNames, validColNames);
    }
    
    public void assertCorrectRuns(InfogramModel modelWOValid, InfogramModel modelWValid) {
        // compare results of training dataset info with and without validation dataset.  Should be the same
        Frame relCmiNoValid = DKV.getGet(modelWOValid._output._admissible_score_key);
        Scope.track(relCmiNoValid);
        Frame relCmiValid = DKV.getGet(modelWValid._output._admissible_score_key);
        Scope.track(relCmiValid);
        TestUtil.assertIdenticalUpToRelTolerance(relCmiNoValid, relCmiValid, 1e-6);
        // check to make sure relevance cmi frame has admissible index sorted in increasing order
        Frame relCmi_valid = DKV.getGet(modelWValid._output._admissible_score_key_valid);
        Scope.track(relCmi_valid);
        Vec admissibleIndex = Scope.track(relCmi_valid.vec("admissible_index"));
        long numRow = relCmi_valid.numRows();
        for (int rowIndex = 1; rowIndex < numRow; rowIndex++)
            assert admissibleIndex.at(rowIndex-1) >= admissibleIndex.at(rowIndex);
    }
}
