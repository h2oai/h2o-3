package water.rapids;

<<<<<<< HEAD
=======
import hex.Model;
import hex.ModelMetricsBinomialGLM;
>>>>>>> Selecting the loss function metric via parameter
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
<<<<<<< HEAD
import water.fvec.FVecFactory;
import water.fvec.Frame;
import water.parser.ParseDataset;
import water.util.ArrayUtils;
import water.util.TwoDimTable;

import java.util.*;

import static hex.genmodel.utils.DistributionFamily.gaussian;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
=======
import water.fvec.*;
import water.parser.ParseDataset;
import water.rapids.vals.ValFrame;
import water.util.ArrayUtils;
import water.util.TwoDimTable;

import java.util.Arrays;
import java.util.Map;
import java.util.Random;

import static hex.genmodel.utils.DistributionFamily.gaussian;
import static org.junit.Assert.*;
import static water.util.RandomUtils.getRNG;
>>>>>>> Selecting the loss function metric via parameter


public class PermutationVarImpTest extends TestUtil {

    @BeforeClass
    public static void stall() {
        stall_till_cloudsize(1);
    }
<<<<<<< HEAD
    
    /**
     * Test setting the metric which Permutation Variable Importance will be calculated.
     * GLM binomial model
     */
    @Test public void testMetricBinomial() {
        GLMModel model = null;
        Frame fr = parse_test_file("smalldata/glm_test/glm_test2.csv");
        Frame score = null;
        try {
            Scope.enter();
            GLMModel.GLMParameters params = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.binomial);
            params._response_column = "response";
            params._ignored_columns = new String[]{"ID"};
            params._train = fr._key;
            params._lambda = new double[]{0};
            params._standardize = false;
            params._max_iterations = 20;

            GLM glm = new GLM( params);
            model = glm.trainModel().get();
            score = model.score(fr);

            PermutationVarImp pvi = new PermutationVarImp(model, fr);

            // when no metric specified "MSE
            TwoDimTable pviTable = pvi.getPermutationVarImp();
            assertEquals("mse", pvi._varImpMetric.mMetric);

            TwoDimTable pviTableRmse = pvi.getPermutationVarImp("rmse");
            assertEquals("rmse", pvi._varImpMetric.mMetric);
            
            TwoDimTable pviTableMse = pvi.getPermutationVarImp("mse");
            TwoDimTable pviTableR2 = pvi.getPermutationVarImp("r2");
            
            // Since model is binomial we can also calculate auc and logloss
            TwoDimTable pviTableAuc = pvi.getPermutationVarImp("auc");
            TwoDimTable pviTableLogloss = pvi.getPermutationVarImp("logloss");

            // the first column contains the relative (Permutation Variable) importance
            for (int row = 0 ; row < pviTableAuc.getRowDim() ; row++){
                String [] colTypes = pviTableRmse.getColTypes();
                Assert.assertTrue( colTypes[0].equals("double"));
                Assert.assertTrue( colTypes[1].equals("double"));
                Assert.assertTrue( colTypes[2].equals("double"));

                colTypes = pviTableMse.getColTypes();
                Assert.assertTrue( colTypes[0].equals("double"));

                colTypes = pviTableR2.getColTypes();
                Assert.assertTrue( colTypes[0].equals("double"));

                colTypes = pviTableAuc.getColTypes();
                Assert.assertTrue( colTypes[0].equals("double"));

                colTypes = pviTableLogloss.getColTypes();
                Assert.assertTrue( colTypes[0].equals("double"));
            }
        } finally {
            fr.remove();
            if (model != null) model.delete();
            if (score != null) score.delete();
            Scope.exit();
        }
    }

    /**
     * Test the TwoDimTable of Permutation Variable Importance and One At a Time
     * GBM gaussian model
     */
=======


    @Test /*(expected = MetricNotFoundExeption.class)*/
    public void passMetric() throws MetricNotFoundExeption{

        Scope.enter();
        Key parsed = Key.make("cars_parsed");
        Frame fr = null;
        GLMModel model = null;
        Frame score = null;
        try {
            fr = parse_test_file(parsed, "smalldata/junit/cars.csv");
            GLMModel.GLMParameters params = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.poisson, GLMModel.GLMParameters.Family.poisson.defaultLink, new double[]{0}, new double[]{0}, 0, 0);
            params._response_column = "power (hp)";
            // params._response = fr.find(params._response_column);
            params._ignored_columns = new String[]{"name"};
            params._train = parsed;
            params._lambda = new double[]{0};
            params._alpha = new double[]{0};
            params._missing_values_handling = GLMModel.GLMParameters.MissingValuesHandling.Skip;

            model = new GLM(params).trainModel().get();

            /* ==== storing the metric accordingly ==== */
            String metric = "rmse";
            PermutationVarImp pvi = new PermutationVarImp(model, fr);
            TwoDimTable pvi_t = pvi.getPermutationVarImp(metric);
            assertEquals(metric, pvi._variImpMetric.m_selectedMetric);

            metric = "RMSE";
            pvi_t = pvi.getPermutationVarImp(metric);
            assertEquals(pvi._variImpMetric.m_selectedMetric, "rmse");

            /* ==== end ==== */
            
            /* === getting the metric from the model or ModelMetrics === */
            pvi_t = pvi.getPermutationVarImp("mse");
            for (int i = 0 ; i < pvi._pVarImp.length ; i++) {
                assertTrue(pvi._pVarImp[i] > 0 || pvi._pVarImp[i] < 0);
            }
            
/*           throws exception, as expected 
            pvi_t = pvi.getPermutationVarImp("logloss");
            for (int i = 0 ; i < pvi._pVarImp.length ; i++) {
                assertTrue(pvi._pVarImp[i] > 0 || pvi._pVarImp[i] < 0);
            }
*/  
            /* ==== end ==== */

            
        }finally {
            if (fr != null) fr.delete();
            if (score != null) score.delete();
            if (model != null) model.delete();
            Scope.exit();
        }
    }
    
    @Test
    public void shuffleSmallDoubleVec(){
        Frame t = null;
        try {
            Scope.enter();
            t = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_STR)
                    .withColNames("first", "second")
                    .withDataForCol(0, ard(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0))
                    .withDataForCol(1, ar("a", "b", "c", "d", "e", "f","g", "h", "i", "j", "k"))
                    .build();
            Scope.track(t);

            assertEquals(t.vec(1).get_type(), Vec.T_STR);
//            VecUtils.shuffleVec new_vv = new VecUtils.shuffleVec(t.vec(0)).doAll(t.vec("first"));
//            Vec new_v = Vec.makeVec(new VecUtils.shuffleVecVol2().doAll(t.vec("first"))._result, Vec.newKey());

//            Vec new_v = new_vv._vec;
            for (int i = 0 ; i < 4 ; i++) {
                Vec shuffled_feature = ShuffleVec.ShuffleTask.shuffle(t.vec(1));
                double rnd_coe = randomnessCoeefic(t.vec(1), shuffled_feature);
                System.out.println("COEF is  = " + rnd_coe);
            }
//            Assert.assertNotEquals(0, rnd_coe);
            
        } finally {
            Scope.exit();
        }
        
    }

    // compares values of vec and returns the randomness change. 0 no change, 1.0 all rows shuffled
    double randomnessCoeefic(Vec og, Vec nw){
        int changed_places = 0;
        switch (og.get_type()) {
            case Vec.T_STR:
                for (int i = 0; i < nw.length(); ++i) {
                    if (!og.stringAt(i).equals(nw.stringAt(i)))
                        changed_places++;
                }
                break;
            case Vec.T_NUM:    
            for (int i = 0; i < nw.length(); ++i) {
                if (og.at(i) != nw.at(i))
                    changed_places++;
            }
                break;
            default:
                throw new IllegalArgumentException("type not supported, FIX!");
            
        }
        return changed_places * 1.0/nw.length();
    }
    
    public double [] randomnessCoef(Frame fr, double [] res){
        // copy vecs to check on permuated ones
        Vec OG_vec[] = new Vec[fr.numCols()];
        for (int i = 0 ; i <  fr.numCols(); i ++)
            OG_vec[i] = fr.vec(i).makeCopy();

        String [] features = fr.names();
        Vec shf_v;
        for (int i = 0 ; i <  fr.numCols(); i ++){
            String col_name = features[i];
            shf_v = ShuffleVec.ShuffleTask.shuffle(fr.vec(col_name));
            res[i] = randomnessCoeefic(OG_vec[i] , shf_v);
        }
        return res;
    }
    
    @Test
    public void shuffleBigVec(){
        Frame fr = null;
        try {
            Scope.enter();
            fr = parse_test_file("./smalldata/jira/shuffle_test.csv"); // has 1000000 unique values;  
            Scope.track(fr);
//            long timeElapsed_1 = endTime_using_set - startTime_using_set;

            Vec vec1 = ShuffleVec.ShuffleTask.shuffle(fr.vec("first"));
            Vec vec2 = ShuffleVec.ShuffleTask.shuffle(fr.vec("first"));

            for (int i = 0; i < vec1.length() ; ++i){
                double l = vec1.at(i);
                double r = vec2.at(i);
                boolean ast = true;
            }

            for (int i = 0 ; i < 12 ; i++) {
                Vec shuffled_feature = ShuffleVec.ShuffleTask.shuffle(fr.vec("first"));
                double rnd_coe = randomnessCoeefic(fr.vec("first"), shuffled_feature);
                System.out.println("COEF is  = " + rnd_coe);
            }
//            Assert.assertNotEquals(0, rnd_coe);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void shuffleBigVec_2(){
        Frame fr = null;
        try {
            Scope.enter();
            fr = parse_test_file("./smalldata/jira/shuffle_test_2.csv"); // has 10000000 unique values;  
            Scope.track(fr);
//            long timeElapsed_1 = endTime_using_set - startTime_using_set;

            Vec shuffled_feature = ShuffleVec.ShuffleTask.shuffle(fr.vec("second"));
            
//            Vec _shuffled_feature = VecUtils.shuffle(fr.vec("second"));
            
            double rnd_coe = randomnessCoeefic(fr.vec("second"), shuffled_feature);
            System.out.println("COEF is  = " + rnd_coe);

//            Assert.assertNotEquals(0, rnd_coe);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void shuffleString(){
        Frame fr_small_solo = null;
        Frame fr_bigger = null;
        try {
            Scope.enter();
//            fr_small_solo = parse_test_file("./smalldata/jira/string_vec.csv"); // has 3000 unique values;  
//            long timeElapsed_1 = endTime_using_set - startTime_using_set;
            fr_bigger = parse_test_file("./smalldata/gbm_test/30k_cattest.csv"); 
            Scope.track(fr_bigger);

            Vec shuffled_feature = ShuffleVec.ShuffleTask.shuffle(fr_bigger.vec(0));
            double rnd_coe = randomnessCoeefic(fr_bigger.vec(0), shuffled_feature);
            System.out.println("COEF is  = " + rnd_coe);

//            Assert.assertNotEquals(0, rnd_coe);
        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void shuffleVecBiggerData(){
        Frame fr = null;
        try {
            Scope.enter(); 
            fr = parse_test_file("./smalldata/airlines/AirlinesTrainWgt.csv"); // has 24422;  
            Scope.track(fr);
//            long timeElapsed_1 = endTime_using_set - startTime_using_set;
            double res [] = new double [fr.numCols()];
            res = randomnessCoef(fr, res);
//            long timeElapsed_2 = endTime_copying_array - startTime_copying_array;
            for (int i = 0 ; i < fr.numCols() ; ++i){
                Assert.assertNotEquals(0, res[i]);
                // Its fine because the Vec can have the only a few values like Column[1] has only f10 and f1 
            }
            for (int i = 0 ; i < 4 ; i++) {
                Vec shuffled_feature = ShuffleVec.ShuffleTask.shuffle(fr.vec(4));
                double rnd_coe = randomnessCoeefic(fr.vec(0), shuffled_feature);
                System.out.println("COEF is  = " + rnd_coe);
            }
            
//            Assert.assertNotEquals(0, rnd_coe);
        } finally {
            Scope.exit();
        }

    }

    @Test
    public void ShuffleAlgo(){
        Random rand = new Random();
        long rseed = rand.nextLong();
        Random rng = getRNG(rseed);
        int [] arr = new int[]{1,2,3,4,5,6,7,8,9,10};
        int [] _arr = new int[]{0,0,0,0,0,0,0,0,0,0};
        for (int row = 0; row < arr.length; row++) {
            System.out.print(Arrays.toString(_arr));
            int j = rng.nextInt(row + 1); // inclusive upper bound <0,row>
            if (j != row) _arr[row] = _arr[j];
            _arr[j] = arr[row];
            System.out.println(" | " + Arrays.toString(_arr));
        }
    }
    

    @Test
    public void removalOfResponseCol(){
        Frame _canCarTrain = null;
        Vec _merit, _class;
        GLMModel model = null;
        try{
            Scope.enter();

            Key outputKey = Key.make("prostate_cat_train.hex");
            NFSFileVec nfs = makeNfsFileVec("smalldata/glm_test/cancar_logIn.csv");

            _canCarTrain = ParseDataset.parse(outputKey, nfs._key);
            _canCarTrain.add("Merit", (_merit = _canCarTrain.remove("Merit")).toCategoricalVec());
            _canCarTrain.add("Class", (_class = _canCarTrain.remove("Class")).toCategoricalVec());

            DKV.put(_canCarTrain._key, _canCarTrain);
            
//            fr = parse_test_file("./smalldata/gbm_test/Mfgdata_gaussian_GBM_testing.csv");
//            Scope.track(fr);
//            DKV.put(fr);
            GLMModel.GLMParameters parms = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.poisson);
            parms._train = _canCarTrain._key;
            parms._ignored_columns = new String[]{"Insured", "Premium", "Cost"};
            // "response_column":"Claims","offset_column":"logInsured"
            parms._response_column = "Claims";
            parms._offset_column = "logInsured";
            parms._standardize = false;
            parms._lambda = new double[]{0};
            parms._alpha = new double[]{0};
            parms._objective_epsilon = 0;
            parms._beta_epsilon = 1e-6;
            parms._gradient_epsilon = 1e-10;
            parms._max_iterations = 1000;
            
            model = new GLM(parms).trainModel().get();
            Frame scoreTrain = model.score(_canCarTrain);
            
            
            PermutationVarImp pfi = new PermutationVarImp(model, scoreTrain);
//            pfi.removeResCol();
            String [] features_without_res = pfi.get_features_wo_res_test();
//            Assert.assertFalse(Arrays.asList(features_without_res).contains("Claims"));
            
            TwoDimTable res = pfi.oat();
            
            System.out.println(res);
            
        } finally {
            if( _canCarTrain  != null ) _canCarTrain.delete();
            if( model != null ) model.delete();
            Scope.exit();
        }
    }


>>>>>>> Selecting the loss function metric via parameter
    @Test
    public void Regression() {
        GBMModel gbm = null;
        Frame fr = null, fr2 = null;
        try {
            Scope.enter();
<<<<<<< HEAD
            fr = parse_test_file("./smalldata/junit/cars.csv");
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            
            parms._train = fr._key;
            parms._distribution = gaussian;
            parms._response_column = "economy (mpg)";
            parms._ntrees = 5;
            parms._max_depth = 4;
            parms._min_rows = 1;
            parms._nbins = 50;
            parms._learn_rate = .2f;
            parms._score_each_iteration = true;
            
            GBM job = new GBM(parms);
            gbm = job.trainModel().get();

            // Done building model; produce a score column with predictions
            fr2 = gbm.score(fr);
            PermutationVarImp pvi =  new PermutationVarImp(gbm, fr);

            TwoDimTable pvi_table = pvi.getPermutationVarImp();
            
            String [] colTypes = pvi_table.getColTypes();

            Assert.assertTrue( colTypes[0].equals("double"));
            Assert.assertTrue( colTypes[1].equals("double"));
            Assert.assertTrue( colTypes[2].equals("double"));
            
            TwoDimTable pvi_oat = pvi.oat();
            colTypes = pvi_oat.getColTypes();

            Assert.assertTrue( colTypes[0].equals("double"));
            Assert.assertTrue( colTypes[1].equals("double"));
            
            
        } finally {
            if (fr != null) fr.remove();
            if (fr2 != null) fr2.remove();
            if (gbm != null) gbm.remove();
=======
            fr = parse_test_file("./smalldata/gbm_test/Mfgdata_gaussian_GBM_testing.csv");
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = fr._key;
            parms._distribution = gaussian;
            parms._response_column = fr._names[1]; // Row in col 0, dependent in col 1, predictor in col 2
            parms._ntrees = 1;
            parms._max_depth = 1;
            parms._min_rows = 1;
            parms._nbins = 20;
            // Drop ColV2 0 (row), keep 1 (response), keep col 2 (only predictor), drop remaining cols
            String[] xcols = parms._ignored_columns = new String[fr.numCols()-2];
            xcols[0] = fr._names[0];
            System.arraycopy(fr._names,3,xcols,1,fr.numCols()-3);
            parms._learn_rate = 1.0f;
            parms._score_each_iteration=true;

            GBM job = new GBM(parms);
            gbm = job.trainModel().get();
            Assert.assertTrue(job.isStopped()); //HEX-1817

            // Done building model; produce a score column with predictions
            fr2 = gbm.score(fr);
            //job.response() can be used in place of fr.vecs()[1] but it has been rebalanced

            System.out.println(new PermutationVarImp(gbm, fr).oat());

        } finally {
            if( fr  != null ) fr .remove();
            if( fr2 != null ) fr2.remove();
            if( gbm != null ) gbm.remove();
>>>>>>> Selecting the loss function metric via parameter
            Scope.exit();
        }        
        
    }

<<<<<<< HEAD
    /**
     * Testing Permutation Variable Importance values on a classification GBM model
     */
    @Test
    public void Classification() {
        Frame fr = null;
=======
    //testing if model is getting the GBMmodel on my class (PermutationFeatureImportanceM.java)
    @Test
    public void Classification() {
>>>>>>> Selecting the loss function metric via parameter
        try {
            Scope.enter();
            final String response = "CAPSULE";
            final String testFile = "./smalldata/logreg/prostate.csv";
<<<<<<< HEAD
            fr = parse_test_file(testFile)
=======
            Frame fr = parse_test_file(testFile)
>>>>>>> Selecting the loss function metric via parameter
                    .toCategoricalCol("RACE")
                    .toCategoricalCol("GLEASON")
                    .toCategoricalCol(response);
            fr.remove("ID").remove();
            fr.vec("RACE").setDomain(ArrayUtils.append(fr.vec("RACE").domain(), "3"));
            Scope.track(fr);
            DKV.put(fr);

<<<<<<< HEAD
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = fr._key;
            parms._response_column = response;
            parms._ntrees = 5;

            GBM job = new GBM(parms);
            GBMModel gbm = job.trainModel().get();
            Scope.track_generic(gbm);
                            
            // Done building model; produce a score column with predictions
            Frame scored = Scope.track(gbm.score(fr));

            PermutationVarImp Fi = new PermutationVarImp(gbm, fr);
            TwoDimTable pvi = Fi.getPermutationVarImp();

            String [] colTypes = pvi.getColTypes();

            Assert.assertTrue( colTypes[0].equals("double"));
            Assert.assertTrue( colTypes[1].equals("double"));
            Assert.assertTrue( colTypes[2].equals("double"));

            
        } finally {
            if (fr != null) fr.remove();
            Scope.exit();
        }
    }


    /**
     * Testing Permutation Variable importance on GLM model
     */
=======
            Model.Parameters.CategoricalEncodingScheme[] supportedSchemes = {
                    Model.Parameters.CategoricalEncodingScheme.OneHotExplicit,
//                    Model.Parameters.CategoricalEncodingScheme.SortByResponse,
//                    Model.Parameters.CategoricalEncodingScheme.EnumLimited,
//                    Model.Parameters.CategoricalEncodingScheme.Enum,
//                    Model.Parameters.CategoricalEncodingScheme.Binary,
//                    Model.Parameters.CategoricalEncodingScheme.LabelEncoder,
//                    Model.Parameters.CategoricalEncodingScheme.Eigen
            };

//            for (Model.Parameters.CategoricalEncodingScheme scheme : supportedSchemes) {

                GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
                parms._train = fr._key;
                parms._response_column = response;
                parms._ntrees = 5;
//                parms._categorical_encoding = scheme;
//                if (scheme == Model.Parameters.CategoricalEncodingScheme.EnumLimited) {
//                    parms._max_categorical_levels = 3;
//                }

                GBM job = new GBM(parms);
                GBMModel gbm = job.trainModel().get();
                Scope.track_generic(gbm);
                
                                
                // Done building model; produce a score column with predictions
                Frame scored = Scope.track(gbm.score(fr));

                // Build a POJO & MOJO, validate same results
                Assert.assertTrue(gbm.testJavaScoring(fr, scored, 1e-15));

                PermutationVarImp Fi = new PermutationVarImp(gbm, fr);
                Fi.getPermutationVarImp();
                
            
//            }
        } finally {
            Scope.exit();
        }
    }

    // Test will crash as to not passing the model correctly
    @Test public void testFI4SmallDataAst(){
        Frame frame = null;
        Frame fi_frame = null;
        try {
            Scope.enter();
            Session sess = new Session();
            final String response = "CAPSULE";
            final String testFile = "./smalldata/logreg/prostate.csv";
            Frame fr = parse_test_file(testFile)
                    .toCategoricalCol("RACE")
                    .toCategoricalCol("GLEASON")
                    .toCategoricalCol(response);
            fr.remove("ID").remove();
            fr.vec("RACE").setDomain(ArrayUtils.append(fr.vec("RACE").domain(), "3"));
            
            Scope.track(fr);
            DKV.put(fr);

                GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
                parms._train = fr._key;
                parms._response_column = response;
                parms._ntrees = 5;
                parms._categorical_encoding = Model.Parameters.CategoricalEncodingScheme.OneHotExplicit;

                GBM job = new GBM(parms);
                GBMModel gbm = job.trainModel().get();
                Scope.track_generic(gbm);


                // Done building model; produce a score column with predictions
                Frame scored = gbm.score(fr);
                Scope.track(scored);

                // Build a POJO & MOJO, validate same results
                Assert.assertTrue(gbm.testJavaScoring(fr, scored, 1e-15));
                
            DKV.put(scored);
            
            Val val = Rapids.exec("(Perm_Feature_importance fr scored)", sess);
            fi_frame = val.getFrame();
            assertNotNull(fi_frame);
            assertEquals(Vec.T_NUM, fi_frame.vec(0).get_type());
            assertEquals(fi_frame.numRows(),fr.numRows() - 1);
            


        } finally {
            Scope.exit();
            if (frame != null) frame.remove();
            if (fi_frame != null) fi_frame.remove();
        }

    }
    // similar as the previous test this one crashes
    @Test
    public void SimpleGenDataSet(){
        Scope.enter();
        try {
            Session sess = new Session();
            String frameName = "testFrame";
            
            String [] mouse_gender = new String [] {"M", "F", "M", "M", "F" , "M", "F", "M", "F"};

            // small mice data set
            Frame inputFrame = new TestFrameBuilder()
                    .withName(frameName, sess)
                    .withColNames("Weight", "Height", "Age", "Gender", "Tail_len")
                    .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_STR, Vec.T_NUM)
                    .withDataForCol(0, ard(11, 11.5, 19.7, 20.6, 27, 8.5, 14, 18, 16))
                    .withDataForCol(1, ard (16 ,17 , 25.4, 25, 29.2, 14, 20, 21.2, 16.9) )
                    .withDataForCol(2, ard(16, 15.4, 22, 19.3, 22.5, 10, 16.2, 20, 23 ))
                    .withDataForCol(3, mouse_gender)
                    .withDataForCol(4, ard(1.6, 1.7, 2.5, 2.6, 2.75, 1.4, 2.0, 2.1, 1.6))
                    .build();

            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = inputFrame._key;
            parms._distribution = gaussian;
            parms._response_column = inputFrame._names[0]; // Row in col 0, dependent in col 1, predictor in col 2
            parms._ntrees = 1;
            parms._max_depth = 1;
            parms._min_rows = 1;
            parms._nbins = 20;
            parms._learn_rate = 1.0f;
            parms._score_each_iteration=true;


            GBM job = new GBM(parms);
            GBMModel gbm = job.trainModel().get();
            Scope.track_generic(gbm);
            DKV.put(gbm);


            // Done building model; produce a score column with predictions
            Frame scored = gbm.score(inputFrame);
            Scope.track(scored);
            
            Scope.track(inputFrame);
//            Scope.track(expectedOutputFrame);

            
            Val resVal = Rapids.exec("(Perm_Feature_importance " + frameName + " gbm" + ")", sess); 
            Assert.assertTrue(resVal instanceof ValFrame);
            Frame resFrame = resVal.getFrame();
            

        }   finally {
            Scope.exit();
        }
    }
    
>>>>>>> Selecting the loss function metric via parameter
    @Test
    public void testPermVarIm_output(){
        GLMModel model = null;

        Key parsed = Key.make("prostate_parsed");
        Key modelKey = Key.make("prostate_model");

        Frame fr = parse_test_file(parsed, "smalldata/logreg/prostate.csv");
        Key betaConsKey = Key.make("beta_constraints");

<<<<<<< HEAD
=======
        String[] cfs1 = new String[]{"AGE", "RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON", "Intercept"};
        double[] vals = new double[]{-0.006502588, -0.500000000, 0.500000000, 0.400000000, 0.034826559, -0.011661747, 0.500000000, -4.564024};

//    [AGE, RACE, DPROS, DCAPS, PSA, VOL, GLEASON, Intercept]
>>>>>>> Selecting the loss function metric via parameter
        FVecFactory.makeByteVec(betaConsKey, "names, lower_bounds, upper_bounds\n AGE, -.5, .5\n RACE, -.5, .5\n DCAPS, -.4, .4\n DPROS, -.5, .5 \nPSA, -.5, .5\n VOL, -.5, .5\nGLEASON, -.5, .5");
        Frame betaConstraints = ParseDataset.parse(Key.make("beta_constraints.hex"), betaConsKey);

        try {
            // H2O differs on intercept and race, same residual deviance though
            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._standardize = true;
            params._family = GLMModel.GLMParameters.Family.binomial;
            params._beta_constraints = betaConstraints._key;
            params._response_column = "CAPSULE";
            params._ignored_columns = new String[]{"ID"};
            params._train = fr._key;
            params._objective_epsilon = 0;
            params._alpha = new double[]{1};
            params._lambda = new double[]{0.001607};
            params._obj_reg = 1.0 / 380;
            GLM glm = new GLM(params, modelKey);
            model = glm.trainModel().get();
            assertTrue(glm.isStopped());
<<<<<<< HEAD
=======
            ModelMetricsBinomialGLM val = (ModelMetricsBinomialGLM) model._output._training_metrics;
>>>>>>> Selecting the loss function metric via parameter

            PermutationVarImp PermVarImp = new PermutationVarImp(model, fr);
            TwoDimTable table = PermVarImp.getPermutationVarImp();

            String ts = table.toString();
            assertTrue(ts.length() > 0);
<<<<<<< HEAD
            
            String [] colTypes = table.getColTypes();

            Assert.assertTrue( colTypes[0].equals("double"));
            Assert.assertTrue( colTypes[1].equals("double"));
            Assert.assertTrue( colTypes[2].equals("double"));
=======

            System.out.println(table);
            double varImp = 0;
            for (int i = 0; i < table.getRowDim(); i ++){
                for (int j = 0; j < table.getColDim() ; j++) { // relative, scaled, percentage 
                    assertTrue(!table.get(i, j).equals(0.0));
                    if (j == 1) {
                        varImp = (Double) table.get(i, j);
                        assertTrue(varImp <= 1.0);
                    }
                }
            }
            
>>>>>>> Selecting the loss function metric via parameter


        } finally {
            fr.delete();
            betaConstraints.delete();
            if (model != null) model.delete();
        }
        
    }
<<<<<<< HEAD

    /**
     * Sorting a map by value.
     */
    public static class MapUtil {
        public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
            List<Map.Entry<K, V>> list = new ArrayList<>(map.entrySet());
            list.sort(Collections.reverseOrder(Map.Entry.comparingByValue()));
            
            Map<K, V> result = new LinkedHashMap<>();
            for (Map.Entry<K, V> entry : list) {
                result.put(entry.getKey(), entry.getValue());
            }

            return result;
        }
    }

    /**
     * Test Permutation Variable Importance together with GLM model and 
     * check whether the PVI values ordered are similar to the 
     * coefficients from the GLM. Allows +-3 elements to be in different oreder
     */
    @Test
    public void testPermVarImpGLM(){
=======
    
    @Test
    public void testPermVarImp_GLM(){
>>>>>>> Selecting the loss function metric via parameter
        Scope.enter();
        Key parsed = Key.make("cars_parsed");
        Frame fr = null;
        GLMModel model = null;
        try {
            fr = parse_test_file(parsed, "smalldata/junit/cars.csv");
            GLMModel.GLMParameters params = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.poisson, GLMModel.GLMParameters.Family.poisson.defaultLink, new double[]{0}, new double[]{0},0,0);
            params._response_column = "power (hp)";
            // params._response = fr.find(params._response_column);
            params._ignored_columns = new String[]{"name"};
            params._train = parsed;
            params._lambda = new double[]{0};
            params._alpha = new double[]{0};
            params._missing_values_handling = GLMModel.GLMParameters.MissingValuesHandling.Skip;

            model = new GLM(params).trainModel().get();
            
            // calculate permutation feature importance
            PermutationVarImp PermVarImp = new PermutationVarImp(model, fr);
<<<<<<< HEAD
            TwoDimTable permVarImpTable = PermVarImp.getPermutationVarImp("r2");

            assert model._parms._standardize;

            // Variable -> Relative Importance 
            Map<String, Double> perVarImp = PermVarImp._varImpMap;
            Map<String, Double> coefficients = model.coefficients();
            
            // Sort the maps
            Map<String, Double> sCoefficients = MapUtil.sortByValue(coefficients);
            Map<String, Double> sPvi = MapUtil.sortByValue(perVarImp);
            
            // Instead of comparing values compare positions (rank)
            String [] coeff = new String[sCoefficients.size() - 1];
            String [] pvi = new String[perVarImp.size()];

            int id = 0;
            for (String name : sCoefficients.keySet()) {
                if (name == "Intercept") continue; // ignore intercept 
                coeff[id++] = name;
            }
            id = 0;
            for (String name : sPvi.keySet()) {
                pvi[id++] = name;
            }
            // same leangth of variables
            assertEquals(coeff.length, pvi.length);
            
            int size = perVarImp.size();
            
            // Compares the rank where the feature is for +- 2 positions
            
            // *      ...       *        ...        *     (variable)
            // |\\    ...   / / | \ \    ...      //|     (compared)
            // * **   ...  *  * * *  *   ...    * * *     (variable)
            for (int i = 0 ; i < size; i++){
                 if (i == 0){
                     assertTrue(pvi[i].equals(coeff[i]) || 
                             pvi[i].equals(coeff[i + 1]) ||
                             pvi[i].equals(coeff[i + 2]));
                         
                } else if (i == 1){
                     assertTrue(pvi[i].equals(coeff[i]) ||
                             pvi[i].equals(coeff[i - 1]) ||
                             pvi[i].equals(coeff[i + 1]) ||
                             pvi[i].equals(coeff[i + 2]));

                } else if (i >= 2 && i < size - 2){
                     assertTrue(pvi[i].equals(coeff[i]) ||
                             pvi[i].equals(coeff[i - 1]) ||
                             pvi[i].equals(coeff[i - 2]) ||
                             pvi[i].equals(coeff[i + 1]) ||
                             pvi[i].equals(coeff[i + 2]));
                    
                } else if (i == size - 2){
                     assertTrue(pvi[i].equals(coeff[i]) ||
                             pvi[i].equals(coeff[i - 1]) ||
                             pvi[i].equals(coeff[i - 2]) ||
                             pvi[i].equals(coeff[i + 1]));

                } else if (i == size - 1) {
                     assertTrue(pvi[i].equals(coeff[i]) ||
                             pvi[i].equals(coeff[i - 1]) ||
                             pvi[i].equals(coeff[i - 2]));
                } else{
                    System.out.println("You messed it up!");
                }
            }
=======
            TwoDimTable permVarImp = PermVarImp.getPermutationVarImp();
            
            
            Map<String, Double> perVarImp = PermVarImp.toMapScaled();
            Map<String, Double> glmVarImp = model.coefficients();

            System.out.println("GLM metrics:" + glmVarImp);
            System.out.println("PVI metrics:" + perVarImp);
            System.out.println("size GLM: " + perVarImp.size() +" | " + "size PVI: " + perVarImp.size());

            /*
            // Instead of comparing values compare positions of (rank) 
            for (String name : perVarImp.keySet()){
                double pvi = perVarImp.get(name);
                double vi = (double) b_varImp.get(name); // VarImp stores floats, typecast needed
                Assert.assertEquals(pvi, vi, 0.2);
            }
            */
            // test scoring
>>>>>>> Selecting the loss function metric via parameter
        } finally {
            if (fr != null) fr.delete();
            if (model != null) model.delete();
            Scope.exit();
        }
    }
<<<<<<< HEAD
=======
    
    /* Testing PermutationVarImp against existing VarImp on GBM model */
>>>>>>> Selecting the loss function metric via parameter
}
