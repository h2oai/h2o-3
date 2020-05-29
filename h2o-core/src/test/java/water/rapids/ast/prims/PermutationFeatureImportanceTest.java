package water.rapids.ast.prims;
import hex.Model;
import hex.ModelMetrics;
import hex.ScoringInfo;
import hex.createframe.CreateFramePostprocessStep;
import hex.createframe.postprocess.ShuffleOneColumnCfps;
import hex.tree.gbm.GBMModel;
import hex.tree.gbm.GBM;
import org.junit.Assert;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.*;
import water.rapids.vals.ValFrame;
import water.util.*;
import org.junit.BeforeClass;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static hex.genmodel.utils.DistributionFamily.gaussian;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


public class PermutationFeatureImportanceTest extends TestUtil {

    @BeforeClass
    public static void stall() {
        stall_till_cloudsize(1);
    }
    
    @Test
    public void shuffleSmallDoubleVec(){
        Frame t = null;
        try {
            Scope.enter();
            t = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_NUM)
                    .withColNames("first", "second")
                    .withDataForCol(0, ard(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0))
                    .withDataForCol(1, ard(1.1, 2.1, 3.1, 4.1, 5.1, 6.1, 7.1, 8.1))
                    .build();
            Scope.track(t);
            Vec OG_vec = t.vec("first").doCopy();
//            VecUtils.shuffleVec new_vv = new VecUtils.shuffleVec(t.vec(0)).doAll(t.vec("first"));
            Vec new_v = Vec.makeVec(new VecUtils.shuffleVecVol2().doAll(t.vec("first"))._result, Vec.newKey());

//            Vec new_v = new_vv._vec;
            double rnd_coe = randomnessCoeefic(OG_vec, new_v);

            Assert.assertNotEquals(0, rnd_coe);
            
        } finally {
            Scope.exit();
        }
        
    }

    // compares values of vec and returns the randomness change. 0 no change, 1.0 all rows shuffled
    double randomnessCoeefic(Vec og, Vec nw){
        int changed_places = 0;
        for (int i = 0; i < nw.length() ; ++i){
            if(og.at(i) != nw.at(i))
                changed_places++;
        }
        return changed_places * 1.0/nw.length();
    }
    
    public double [] randomnessCoef(Frame fr, double [] res){
        // copy vecs to check on permuated ones
        Vec OG_vec[] = new Vec[fr.numCols()];
        for (int i = 0 ; i <  fr.numCols(); i ++)
            OG_vec[i] = fr.vec(i).makeCopy();

        String [] features = fr.names();
        ShuffleVec shf = new ShuffleVec(fr);
        Vec shf_v;
        for (int i = 0 ; i <  fr.numCols(); i ++){
            String col_name = features[i];
            shf_v = ShuffleVec.ShuffleTask.shuffle(fr.vec(col_name));
            res[i] = randomnessCoeefic(OG_vec[i] , shf_v);
        }
        return res;
    }
    
    @Test
    public void shuffleVecBiggerData(){
        Frame fr = null;
        try {
            Scope.enter(); 
            fr = parse_test_file("./smalldata/airlines/AirlinesTrainWgt.csv"); // has 24422; should be enough to test speed 
            Scope.track(fr);
//            long timeElapsed_1 = endTime_using_set - startTime_using_set;
            double res [] = new double [fr.numCols()];
            res = randomnessCoef(fr, res);
//            long timeElapsed_2 = endTime_copying_array - startTime_copying_array;
            for (int i = 0 ; i < fr.numCols() ; ++i){
                Assert.assertNotEquals(0, res[i]);
                // TODO check if low random coeficent and try if shuffling again, if that doesnt work FIXME
                // Its fine because the Vec can have the only a few values like Column[1] has only f10 and f1 
            }
            
//            Assert.assertNotEquals(0, rnd_coe);
        } finally {
            Scope.exit();
        }

    }
   
    
    @Test
    public void Regression() {
        GBMModel gbm = null;
        Frame fr = null, fr2 = null;
        try {
            Scope.enter();
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

            PermutationFeatureImportance Fi = new PermutationFeatureImportance(gbm, fr, fr2);
            Fi.getFeatureImportance();
            
        } finally {
            if( fr  != null ) fr .remove();
            if( fr2 != null ) fr2.remove();
            if( gbm != null ) gbm.remove();
            Scope.exit();
        }        
        
    }

    //testing if model is getting the GBMmodel on my class (PermutationFeatureImportanceM.java)
    @Test
    public void Classification() throws Exception {
        try {
            Scope.enter();
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

            Model.Parameters.CategoricalEncodingScheme[] supportedSchemes = {
                    Model.Parameters.CategoricalEncodingScheme.OneHotExplicit,
                    Model.Parameters.CategoricalEncodingScheme.SortByResponse,
                    Model.Parameters.CategoricalEncodingScheme.EnumLimited,
                    Model.Parameters.CategoricalEncodingScheme.Enum,
                    Model.Parameters.CategoricalEncodingScheme.Binary,
                    Model.Parameters.CategoricalEncodingScheme.LabelEncoder,
                    Model.Parameters.CategoricalEncodingScheme.Eigen
            };

            for (Model.Parameters.CategoricalEncodingScheme scheme : supportedSchemes) {

                GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
                parms._train = fr._key;
                parms._response_column = response;
                parms._ntrees = 5;
                parms._categorical_encoding = scheme;
                if (scheme == Model.Parameters.CategoricalEncodingScheme.EnumLimited) {
                    parms._max_categorical_levels = 3;
                }

                GBM job = new GBM(parms);
                GBMModel gbm = job.trainModel().get();
                Scope.track_generic(gbm);
                
                                
                // Done building model; produce a score column with predictions
                Frame scored = Scope.track(gbm.score(fr));

                // Build a POJO & MOJO, validate same results
                Assert.assertTrue(gbm.testJavaScoring(fr, scored, 1e-15));

                PermutationFeatureImportance Fi = new PermutationFeatureImportance(gbm, fr, scored);
                Fi.getFeatureImportance();
                System.out.print(Fi.getTable());
            
            }
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

            
            Val resVal = Rapids.exec("(Perm_Feature_importance " + frameName + " gbm" + ")", sess); // FIXME: find how to pass the model
            Assert.assertTrue(resVal instanceof ValFrame);
            Frame resFrame = resVal.getFrame();
            

        }   finally {
            Scope.exit();
        }
    }
}
