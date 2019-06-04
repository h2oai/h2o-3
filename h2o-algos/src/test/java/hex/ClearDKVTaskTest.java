package hex;

import hex.naivebayes.NaiveBayes;
import hex.naivebayes.NaiveBayesModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import java.util.ArrayList;
import java.util.List;

import static hex.genmodel.utils.DistributionFamily.AUTO;
import static org.junit.Assert.*;

public class ClearDKVTaskTest extends TestUtil {

    @BeforeClass()
    public static void setup() {
        stall_till_cloudsize(1);
    }

    @Test
    public void testNaiveBayesModel() {
        NaiveBayesModel model = null;
        Frame trainingFrame = null, preds = null;
        try {
            trainingFrame = parse_test_file("./smalldata/iris/iris_wheader.csv");
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
            parms._distribution = AUTO;
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

    @Test
    public void testRetainSharedVecs() {
        try {
            Scope.enter();
            Frame f1 = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_NUM)
                    .withDataForCol(0, ar(0, 1))
                    .withDataForCol(1, ar(2, 3))
                    .build();
            Frame f2 = new Frame(Key.<Frame>make());
            f2.add("vec_shared_with_f1", f1.vec(1));
            DKV.put(f2);
            Scope.track(f2);

            // delete everything except for Frame `f1`
            new DKV.ClearDKVTask(new Key[]{f1._key})
                    .doAllNodes();

            // Frame `f1` shouldn't lose any data 
            assertNotNull(f1.vec(1).chunkForChunkIdx(0));
        } finally {
            Scope.exit();
        }
    }
    
    private static void testRetainFrame(Frame trainingFrame) {
        new DKV.ClearDKVTask(new Key[]{trainingFrame._key}).doAllNodes();
        assertTrue(H2O.STORE.containsKey(trainingFrame._key));
        assertNotNull(DKV.get(trainingFrame._key));

        for (Vec vec : trainingFrame.vecs()) {
            assertTrue(H2O.STORE.containsKey(vec._key));

            for (int i = 0; i < vec.nChunks(); i++) {
                assertTrue(H2O.STORE.containsValue(vec.chunkIdx(i)));
            }
        }
    }
    
    private static void testRetainModel(Model model, Frame trainingFrame){
        assertNotNull(DKV.get(model._key));
        new DKV.ClearDKVTask(new Key[]{model._key}).doAllNodes();
        assertNotNull(DKV.get(model._key));
        
    }
    
    private static void testModelDeletion(final Model model){
        new DKV.ClearDKVTask(new Key[]{}).doAllNodes();
        assertNull(DKV.get(model._key));
    }
}
