package hex.Infogram;

import hex.SplitFrame;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
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
            Frame relCmiTrain = DKV.getGet(infogramModel._output._relevance_cmi_key);
            Scope.track(relCmiTrain);
            Frame relCmiTrainV = DKV.getGet(infogramModelV._output._relevance_cmi_key);
            Scope.track(relCmiTrainV);
            Frame relCmiTrainVCV = DKV.getGet(infogramModelVCV._output._relevance_cmi_key);
            Scope.track(relCmiTrainVCV);
            Frame relCmiTrainCV = DKV.getGet(infogramModelCV._output._relevance_cmi_key);
            Scope.track(relCmiTrainCV);
            TestUtil.assertFrameEquals(relCmiTrain, relCmiTrainV, 1e-6);
            TestUtil.assertFrameEquals(relCmiTrainVCV, relCmiTrainCV, 1e-6);
            TestUtil.assertFrameEquals(relCmiTrain, relCmiTrainVCV, 1e-6);
            
            // check two models have same validation relcmi frame;
            Frame relCmiValidV = DKV.getGet(infogramModelV._output._relevance_cmi_key_valid);
            Scope.track(relCmiValidV);
            Frame relCmiValidVCV = DKV.getGet(infogramModelVCV._output._relevance_cmi_key_valid);
            Scope.track(relCmiValidVCV);
            TestUtil.assertFrameEquals(relCmiValidV, relCmiValidVCV, 1e-6);
            
            // check two models with same cv relcmi frame;
            Frame relCmiCV = DKV.getGet(infogramModelCV._output._relevance_cmi_key_xval);
            Scope.track(relCmiCV);
            Frame relCmiVCV = DKV.getGet(infogramModelVCV._output._relevance_cmi_key_xval);
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
            params._seed = 12345;

            InfogramModel infogramModel = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModel);
            assertTrue(infogramModel._output._relevance_cmi_key_valid == null);
            
            params._valid = testFrame._key;
            InfogramModel infogramModelV = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModelV);
            assertTrue(infogramModelV._output._relevance_cmi_key_valid != null);
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
            params._algorithm = InfogramModel.InfogramParameters.Algorithm.gbm;
            params._top_n_features = 50;
            params._seed = 12345;

            // check and make sure infogram model can be run multiple times
            InfogramModel infogramModel = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModel);
            InfogramModel infogramModel2 = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModel2);
            Frame relCmiFrame = DKV.getGet(infogramModel._output._relevance_cmi_key);
            Scope.track(relCmiFrame);
            Frame relCmiFrame2 = DKV.getGet(infogramModel2._output._relevance_cmi_key);
            Scope.track(relCmiFrame2);            
            TestUtil.assertFrameEquals(relCmiFrame, relCmiFrame2, 1e-6);
            
            params._valid = testFrame._key;
            InfogramModel infogramModelV = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModelV);
            Frame relCmiFrameV = DKV.getGet(infogramModelV._output._relevance_cmi_key);
            Scope.track(relCmiFrameV);
            InfogramModel infogramModelV2 = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModelV2);
            Frame relCmiFrameV2 = DKV.getGet(infogramModelV2._output._relevance_cmi_key);
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
            params._top_n_features = 50;
            params._seed = 12345;
            params._nfolds = 3;
            params._fold_assignment = Modulo;

            InfogramModel infogramModel = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModel);
            assertCorrectCVCore(infogramModel);
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
            params._seed = 12345;
            params._nfolds = 3;
            params._fold_assignment = Modulo;

            InfogramModel infogramModel = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModel);
            InfogramModel infogramModel2 = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModel2);

            Frame relCmiFrameTrain = DKV.getGet(infogramModel._output._relevance_cmi_key);
            Scope.track(relCmiFrameTrain);
            Frame relCmiFrameTrain2 = DKV.getGet(infogramModel2._output._relevance_cmi_key);
            Scope.track(relCmiFrameTrain2);
            TestUtil.assertFrameEquals(relCmiFrameTrain, relCmiFrameTrain2, 1e-6);

            Frame relCmiFrameCV = DKV.getGet(infogramModel._output._relevance_cmi_key_xval);
            Scope.track(relCmiFrameCV);
            Frame relCmiFrameCV2 = DKV.getGet(infogramModel2._output._relevance_cmi_key_xval);
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
            Frame relCmiTrain = DKV.getGet(infogramModel._output._relevance_cmi_key);
            Scope.track(relCmiTrain);
            Frame relCmiTrainV = DKV.getGet(infogramModelV._output._relevance_cmi_key);
            Scope.track(relCmiTrainV);
            Frame relCmiTrainVCV = DKV.getGet(infogramModelVCV._output._relevance_cmi_key);
            Scope.track(relCmiTrainVCV);
            Frame relCmiTrainCV = DKV.getGet(infogramModelCV._output._relevance_cmi_key);
            Scope.track(relCmiTrainCV);
            TestUtil.assertFrameEquals(relCmiTrain, relCmiTrainV, 1e-6);
            TestUtil.assertFrameEquals(relCmiTrainVCV, relCmiTrainCV, 1e-6);
            TestUtil.assertFrameEquals(relCmiTrain, relCmiTrainVCV, 1e-6);

            // check two models have same validation relcmi frame;
            Frame relCmiValidV = DKV.getGet(infogramModelV._output._relevance_cmi_key_valid);
            Scope.track(relCmiValidV);
            Frame relCmiValidVCV = DKV.getGet(infogramModelVCV._output._relevance_cmi_key_valid);
            Scope.track(relCmiValidVCV);
            TestUtil.assertFrameEquals(relCmiValidV, relCmiValidVCV, 1e-6);

            // check two models with same cv relcmi frame;
            Frame relCmiCV = DKV.getGet(infogramModelCV._output._relevance_cmi_key_xval);
            Scope.track(relCmiCV);
            Frame relCmiVCV = DKV.getGet(infogramModelVCV._output._relevance_cmi_key_xval);
            Scope.track(relCmiVCV);
            TestUtil.assertFrameEquals(relCmiVCV, relCmiCV, 1e-6);

        } finally {
            Scope.exit();
        }
    }
    
    public static void assertCorrectCVCore(InfogramModel infogramModel) {
        long[] validNonZeroRows = new long[]{189, 190, 190};
        double[][] cmiRaw = new double[][]{{0.08169581771332357, 0.0, 0.03425625202438276, 0.008709814751866496, 
                0.02223534475430533, 0.017992393818269115, 0.0, 0.011487991245001439, 0.009665105118885187, 
                0.010230352938320308, 0.0, 0.007535040728417819, 0.007333681119888524, 0.0018593870004597335, 0.0, 0.0, 
                0.0, 4.805351900269983E-4, 0.0, 0.0, 0.0, 0.0, 0.0, 1.4858949061213877E-4, 0.0, 0.0, 0.0, 0.0, 0.0, 
                0.0}, {0.1414522916051233, 0.12553639426362206, 0.09820559210956681, 0.06337621394604387, 
                0.053230489048432084, 0.04410121054728933, 0.04333737327471621, 0.042078836523647745, 
                0.03592426584259201, 0.024528848281659243, 0.02433495028041932, 0.021951752848415218, 
                0.021742682136737912, 0.018167135927545708, 0.015904480642073615, 0.013632191507119806, 
                0.011538736943777828, 0.0, 0.0, 0.0, 0.003287932354896661, 0.0, 9.3448875400437E-4, 0.001244023574750841, 
                6.90450091490824E-4, 0.0, 0.0, 0.0, 0.0, 0.0}, {0.10992489551457041, 0.13260979375398474, 
                0.12638873978194365, 0.10456709563248978, 0.10171742595787592, 0.08983745180316571, 0.08823522369900783, 
                0.08218907928568608, 0.07915372669875698, 0.06966478885983651, 0.016696319717939723, 
                0.055213988611593656, 0.05303174283796519, 0.05120589063028813, 0.04968882046229339, 0.04090258142153935, 
                0.03833172324109668, 0.03153265030738428, 0.03114401424116675, 0.028193403336733702, 
                0.013747524723368265, 0.009557942432972588, 0.00882479405476655, 0.007893030913280086, 0.0, 0.0,
                0.0021635071805015116, 0.0, 0.0, 3.1179824929061795E-5}};
        String[][] validColNames = new String[][]{{"concave_points_worst", "compactness_worst", "texture_mean",
                "smoothness_mean", "perimeter_worst", "fractal_dimension_worst", "fractal_dimension_mean",
                "concave_points_mean", "symmetry_worst", "area_worst", "compactness_se", "concavity_se",
                "radius_worst", "area_mean", "concavity_mean", "symmetry_mean", "smoothness_worst", "perimeter_se",
                "area_se", "radius_mean", "texture_worst", "concavity_worst", "radius_se", "fractal_dimension_se",
                "texture_se", "symmetry_se", "concave_points_se", "compactness_mean", "perimeter_mean",
                "smoothness_se"}, {"area_se", "area_worst", "perimeter_mean", "radius_worst", "smoothness_worst",
                "compactness_se", "perimeter_worst", "concavity_worst", "fractal_dimension_worst", "texture_mean",
                "concave_points_worst", "texture_worst", "concave_points_mean", "symmetry_mean", "concavity_mean",
                "symmetry_worst", "symmetry_se", "radius_se", "area_mean", "compactness_mean", "texture_se",
                "perimeter_se", "fractal_dimension_se", "concave_points_se", "radius_mean", "smoothness_se",
                "compactness_worst", "fractal_dimension_mean", "smoothness_mean", "concavity_se"},
                {"concave_points_worst", "concave_points_mean", "perimeter_worst", "compactness_mean", "area_se",
                        "smoothness_se", "fractal_dimension_worst", "compactness_worst", "concavity_worst",
                        "compactness_se", "concave_points_se", "radius_se", "smoothness_worst",
                        "fractal_dimension_mean", "symmetry_se", "texture_se", "radius_worst", "texture_mean",
                        "area_worst", "symmetry_worst", "area_mean", "fractal_dimension_se", "texture_worst",
                        "perimeter_mean", "perimeter_se", "concavity_se", "smoothness_mean", "concavity_mean",
                        "radius_mean","symmetry_mean"}};
        double[] mainRelevance = new double[]{1.0, 0.08948953104223797, 0.02620743317990611, 0.0019399743316518242, 
                0.6187801783576264, 0.2808010415664416, 0.008630371321150698, 0.003244459766548256, 0.3404628948036715,
                0.09744553147933943, 0.3302352774810101, 0.003331706365888622, 0.0021346433329991698, 
                0.006881260287776389, 0.008212149084919634, 0.02578193462145112, 0.02601985020312607, 
                0.008178699708125306, 0.004138451650723794, 0.004100210323871828, 0.004047798948386242, 
                0.0037914745065436226, 0.0036801151185893786, 0.003628001817751223, 0.0026430896642307927, 
                0.001607777138844435, 0.001456217683506916, 6.185385268403536E-4, 5.372569213253213E-4, 
                2.943118926158916E-4};
        String[] mainColNames = new String[]{"radius_worst", "texture_worst", "area_se", "fractal_dimension_worst", 
                "perimeter_worst", "concave_points_mean", "perimeter_mean", "radius_se", "concave_points_worst", 
                "texture_mean", "area_worst", "smoothness_se", "smoothness_worst", "compactness_se", 
                "concave_points_se", "concavity_mean", "concavity_worst", "fractal_dimension_se", "symmetry_worst", 
                "area_mean", "radius_mean", "smoothness_mean", "compactness_mean", "fractal_dimension_mean", 
                "perimeter_se", "compactness_worst", "symmetry_se", "concavity_se", "symmetry_mean","texture_se"};
        Frame relCmiKeyCV = DKV.getGet(infogramModel._output._relevance_cmi_key_xval);
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
        int numPred = calculatedCmiRaw.length;
        double totalNObs = 1.0/sum(nObs);
        double[] cmiRawManual = new double[numPred];
        for (int fIndex=0; fIndex < numFold; fIndex++) {    // compute across each fold
            ArrayList<String> colNameList = new ArrayList<>(Arrays.asList(colNames[fIndex]));
            for (int pIndex=0; pIndex < numPred; pIndex++) {    // same column name as calculatedCMI frame
                String currPredName = calculatedColumns.get(pIndex);
                int colIndex = colNameList.indexOf(currPredName); // corresponding index from frame
                cmiRawManual[pIndex] += cmiRaw[fIndex][colIndex]*totalNObs*nObs[fIndex];
            }
        }
        assertArrayEquals(calculatedCmiRaw, cmiRawManual, 1e-6);    // compare cmi_raw calculations
        // generate cmi and check that is is correct
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
        for (int pIndex=0; pIndex < numPred; pIndex++) {
            String colName = calculatedColumns.get(pIndex);
            int mainIndex = mainColumns.indexOf(colName);
            manualRelevance[pIndex] = relevance[mainIndex];
            double temp = (1-manualRelevance[pIndex])*(1-manualRelevance[pIndex])+(1-manualCmi[pIndex])*(1-manualCmi[pIndex]);
            manualAdmissibleIndex[pIndex] = Math.sqrt(temp);
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

            InfogramModel infogramModel = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModel);
            assertTrue(infogramModel._output._relevance_cmi_key_valid==null);
            
            params._valid = testFrame._key;
            InfogramModel infogramModelV = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModelV);
            assertTrue(infogramModelV._output._relevance_cmi_key_valid!=null);
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
            params._protected_columns = new String[]{"sex","age","race"};
            params._algorithm = InfogramModel.InfogramParameters.Algorithm.gbm;
            params._ignored_columns = new String[]{"id"};
            params._top_n_features = 50;
            params._seed = 12345;

            InfogramModel infogramModel = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModel);
            InfogramModel infogramModel2 = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModel2);
            Frame relCmiTrain = DKV.getGet(infogramModel._output._relevance_cmi_key);
            Scope.track(relCmiTrain);
            Frame relCmiTrain2 = DKV.getGet(infogramModel2._output._relevance_cmi_key);
            Scope.track(relCmiTrain2);
            TestUtil.assertFrameEquals(relCmiTrain, relCmiTrain2, 1e-6);
            
            params._valid = testFrame._key;
            InfogramModel infogramModelV = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModelV);
            InfogramModel infogramModelV2 = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModelV2);

            Frame relCmiVTr = DKV.getGet(infogramModelV._output._relevance_cmi_key);
            Scope.track(relCmiVTr);
            Frame relCmiV2Tr = DKV.getGet(infogramModelV2._output._relevance_cmi_key);
            Scope.track(relCmiV2Tr);
            TestUtil.assertFrameEquals(relCmiV2Tr, relCmiVTr, 1e-6);
            
            Frame relCmiV = DKV.getGet(infogramModelV._output._relevance_cmi_key_valid);
            Scope.track(relCmiV);
            Frame relCmiV2 = DKV.getGet(infogramModelV2._output._relevance_cmi_key_valid);
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

            InfogramModel infogramModel = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModel);
            InfogramModel infogramModel2 = new Infogram(params).trainModel().get();
            Scope.track_generic(infogramModel2);
            // both runs should return same training relCMIframe contents
            Frame relCmiFrameTrain = DKV.getGet(infogramModel._output._relevance_cmi_key);
            Scope.track(relCmiFrameTrain);
            Frame relCmiFrameTrain2 = DKV.getGet(infogramModel2._output._relevance_cmi_key);
            Scope.track(relCmiFrameTrain2);
            TestUtil.assertFrameEquals(relCmiFrameTrain, relCmiFrameTrain2, 1e-6);
            // both runs should return same cross-validation relCMIframe contents
            Frame relCmiFrameValid = DKV.getGet(infogramModel._output._relevance_cmi_key_xval);
            Scope.track(relCmiFrameValid);
            Frame relCmiFrameTrainValid2 = DKV.getGet(infogramModel2._output._relevance_cmi_key_xval);
            Scope.track(relCmiFrameTrainValid2);
            TestUtil.assertFrameEquals(relCmiFrameValid, relCmiFrameTrainValid2, 1e-6);
        } finally {
            Scope.exit();
        }
    }


    public static void assertCorrectCVSafe(InfogramModel infogramModel) {
        long[] validNonZeroRows = new long[]{3437, 3470};
        double[][] cmiRaw = new double[][]{{0.01042522722364736, 0.0, 0.0, 8.172994825386137E-4, 0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                0.0, 0.0}};
        String[][] validColNames = new String[][]{{"days_b_screening_arrest", "end", "event", "juv_fel_count", 
                "decile_score", "start", "v_decile_score", "priors_count", "juv_other_count", "v_score_text", 
                "c_charge_degree", "juv_misd_count", "score_text", "priors_count.1", "decile_score.1"}, {"end", 
                "event", "decile_score", "priors_count", "start", "juv_fel_count", "juv_misd_count", 
                "days_b_screening_arrest", "c_charge_degree", "v_decile_score", "v_score_text", "juv_other_count", 
                "score_text", "decile_score.1", "priors_count.1"}};
        double[] mainRelevance = new double[]{1.0, 0.22232788946634713, 0.010488736091117714, 0.0, 
                0.016795647759374355, 0.0, 0.0018857970710325917, 0.00401633133386085, 0.01452655439713657, 
                2.574920854711118E-4, 0.004113058086770913, 0.0012933419572252322, 0.0014052131829135543, 
                0.0022520089083028056, 0.0013904296902873732};
        String[] mainColNames = new String[]{"end", "event", "priors_count", "priors_count.1", "decile_score", 
                "decile_score.1", "score_text", "v_decile_score", "start", "v_score_text", "days_b_screening_arrest", 
                "c_charge_degree", "juv_other_count", "juv_misd_count", "juv_fel_count"};
        Frame relCmiKeyCV = DKV.getGet(infogramModel._output._relevance_cmi_key_xval);
        Scope.track(relCmiKeyCV);
        // check correct validation infogram averaging
        assertCorrectInfoGramMetrics(relCmiKeyCV, validNonZeroRows, cmiRaw, mainRelevance, mainColNames, validColNames);
    }
    
    public void assertCorrectRuns(InfogramModel modelWOValid, InfogramModel modelWValid) {
        // compare results of training dataset info with and without validation dataset.  Should be the same
        Frame relCmiNoValid = DKV.getGet(modelWOValid._output._relevance_cmi_key);
        Scope.track(relCmiNoValid);
        Frame relCmiValid = DKV.getGet(modelWValid._output._relevance_cmi_key);
        Scope.track(relCmiValid);
        TestUtil.assertIdenticalUpToRelTolerance(relCmiNoValid, relCmiValid, 1e-6);
        // check to make sure relevance cmi frame has admissible index sorted in increasing order
        Frame relCmi_valid = DKV.getGet(modelWValid._output._relevance_cmi_key_valid);
        Scope.track(relCmi_valid);
        Vec admissibleIndex = Scope.track(relCmi_valid.vec("admissible_index"));
        long numRow = relCmi_valid.numRows();
        for (int rowIndex = 1; rowIndex < numRow; rowIndex++)
            assert admissibleIndex.at(rowIndex-1) >= admissibleIndex.at(rowIndex);
    }
}
