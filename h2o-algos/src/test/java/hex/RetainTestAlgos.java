package hex;

import hex.naivebayes.NaiveBayes;
import hex.naivebayes.NaiveBayesModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.H2O;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.ArrayList;
import java.util.List;

import static hex.genmodel.utils.DistributionFamily.gaussian;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class RetainTestAlgos extends TestUtil {

    @BeforeClass()
    public static void setup() {
        stall_till_cloudsize(1);
    }

    @Test
    public void testNaiveBayesModel() {
        NaiveBayesModel model = null;
        Frame trainingFrame = null, preds = null;
        try {
            trainingFrame = parse_test_file("smalldata/iris/iris_wheader.csv");
            List<Key> retainedKeys = new ArrayList<>();
            retainedKeys.add(trainingFrame._key);
            // Test the training frame has not been deleted
            testRetainFrame(trainingFrame);

            // A little integration test
            NaiveBayesModel.NaiveBayesParameters parms = new NaiveBayesModel.NaiveBayesParameters();
            parms._train = trainingFrame._key;
            parms._laplace = 0;
            parms._response_column = trainingFrame._names[4];
            parms._compute_metrics = false;

            model = new NaiveBayes(parms).trainModel().get();
            assertNotNull(model);
            // Done building model; produce a score column with class assignments
            preds = model.score(trainingFrame);
            assertNotNull(preds);
            
            // Test model retainment
            testRetainModel(model, trainingFrame);
            testModelDeletion(model);

        } finally {
            if (trainingFrame != null) trainingFrame.delete();
            if (preds != null) preds.delete();
            if (model != null) model.delete();
        }
    }


    @Test
    public void testGBM() {
        GBMModel model = null;
        Frame trainingFrame = null, preds = null;
        try {
            trainingFrame = parse_test_file("./smalldata/gbm_test/Mfgdata_gaussian_GBM_testing.csv");

            // Test the training frame has not been deleted
            testRetainFrame(trainingFrame);
            
            // A little integration test
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = trainingFrame._key;
            parms._distribution = gaussian;
            parms._response_column = trainingFrame._names[1];
            parms._ntrees = 1;
            parms._max_depth = 1;
            parms._min_rows = 1;
            parms._nbins = 20;
            parms._learn_rate = 1.0f;
            parms._score_each_iteration = true;

            GBM job = new GBM(parms);
            model = job.trainModel().get();
            assertNotNull(model);

            preds = model.score(trainingFrame);
            assertNotNull(preds);

            // Test model retainment
            testRetainModel(model, trainingFrame);
            testModelDeletion(model);
        } finally {
            if (trainingFrame != null) trainingFrame.remove();
            if (preds != null) preds.remove();
            if (model != null) model.remove();
        }
    }

    private static void testRetainFrame(Frame trainingFrame) {
        List<Key> retainedKeys = new ArrayList<>();
        retainedKeys.add(trainingFrame._key);

        H2O.retain(retainedKeys);
        assertTrue(H2O.STORE.containsKey(trainingFrame._key));
        assertNotNull(DKV.get(trainingFrame._key));

        for (Vec vec : trainingFrame.vecs()) {
            assertTrue(H2O.STORE.containsKey(vec._key));

            for (int i = 0; i < vec.nChunks(); i++) {
                assertTrue(H2O.STORE.containsKey(vec.chunkKey(i)));
            }
        }
    }
    
    private static void testRetainModel(Model model, Frame trainingFrame){
        assertNotNull(DKV.get(model._key));
        List<Key> retainedKeys = new ArrayList<>();
        retainedKeys.add(model._key);
        H2O.retain(retainedKeys);
        assertNotNull(DKV.get(model._key));
        
        assertNull(DKV.get(trainingFrame._key));
    }
    
    private static void testModelDeletion(Model model){
        H2O.retain(new ArrayList<Key>());
        assertNull(DKV.get(model._key));
    }
}
