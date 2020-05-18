package water.rapids.ast.prims;

import hex.Model;
import hex.createframe.CreateFramePostprocessStep;
import hex.createframe.postprocess.ShuffleOneColumnCfps;
import hex.tree.gbm.GBMModel;
import hex.tree.gbm.GBM;
import org.junit.Assert;
import org.junit.Test;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.FeatureImportance4BBM;
import water.util.ArrayUtils;
import org.junit.BeforeClass;
import water.util.FrameUtils;
import water.util.MathUtils;
import water.util.VecUtils;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static hex.genmodel.utils.DistributionFamily.gaussian;
import static org.junit.Assert.assertEquals;


public class FeatureImportance4BBMTest extends TestUtil {

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
    @Test
    public void shuffleVecBiggerData(){
        Frame fr = null;
        try {
            Scope.enter(); 
            fr = parse_test_file("./smalldata/airlines/AirlinesTrainWgt.csv"); // has 24422; should be enough to test speed 
            Scope.track(fr);
            
            Vec OG_vec = fr.vec(10).makeCopy();

            /*
            long startTime_using_set = System.nanoTime();
            VecUtils.shuffleVec new_vv = new VecUtils.shuffleVec(fr.vec(10)).doAll(fr.vec("Weight"));
            long endTime_using_set = System.nanoTime();
            */
            long startTime_copying_array = System.nanoTime();
            Vec new_v = Vec.makeVec(new VecUtils.shuffleVecVol2().doAll(fr.vec("Weight"))._result, Vec.newKey());
            long endTime_copying_array = System.nanoTime();

//            long timeElapsed_1 = endTime_using_set - startTime_using_set;
//            long timeElapsed_2 = endTime_copying_array - startTime_copying_array;

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
    
    @Test
    public void initiate() {
        GBMModel gbm = null;
        Frame fr = null, fr2 = null;
        try {
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
            double sq_err = new MathUtils.SquareError().doAll(fr.vecs()[1],fr2.vecs()[0])._sum;
            double mse = sq_err/fr2.numRows();
            assertEquals(79152.12337641386,mse,0.1);
            assertEquals(79152.12337641386,gbm._output._scored_train[1]._mse,0.1);
            assertEquals(79152.12337641386,gbm._output._scored_train[1]._mean_residual_deviance,0.1);

            FeatureImportance4BBM Fi = new FeatureImportance4BBM(gbm, fr, fr2);
            Fi.getFeatureImportance();

        } finally {
            if( fr  != null ) fr .remove();
            if( fr2 != null ) fr2.remove();
            if( gbm != null ) gbm.remove();
        }        
        
    }

    //testing if model is getting the GBMmodel on my class (FeatureImportance4BBM.java)
/*   
    @Test
    public void testPassingModel() throws Exception {
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

                FeatureImportance4BBM Fi = new FeatureImportance4BBM(gbm);

            }

        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testFoo2() {
        GBMModel gbm = null;
        Frame fr = null, fr2 = null;
        try {
//            String tmp = System.getProperty("user.dir");
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
            String[] xcols = parms._ignored_columns = new String[fr.numCols() - 2];
            xcols[0] = fr._names[0];
            System.arraycopy(fr._names, 3, xcols, 1, fr.numCols() - 3);
            parms._learn_rate = 1.0f;
            parms._score_each_iteration = true;

            GBM job = new GBM(parms);
            gbm = job.trainModel().get();
            Assert.assertTrue(job.isStopped()); //HEX-1817

            // Done building model; produce a score column with predictions
            fr2 = gbm.score(fr);
            //job.response() can be used in place of fr.vecs()[1] but it has been rebalanced
            double sq_err = new MathUtils.SquareError().doAll(fr.vecs()[1], fr2.vecs()[0])._sum;
            double mse = sq_err / fr2.numRows();
            assertEquals(79152.12337641386, mse, 0.1);
            assertEquals(79152.12337641386, gbm._output._scored_train[1]._mse, 0.1);
            assertEquals(79152.12337641386, gbm._output._scored_train[1]._mean_residual_deviance, 0.1);
        } finally {
            if (fr != null) fr.remove();
            if (fr2 != null) fr2.remove();
            if (gbm != null) gbm.remove();
            //           FeatureImportance4BBM Fi = new FeatureImportance4BBM(gbm);
        }
 
    }
 */
}
