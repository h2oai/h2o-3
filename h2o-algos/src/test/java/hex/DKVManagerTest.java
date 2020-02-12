package hex;

import hex.naivebayes.NaiveBayes;
import hex.naivebayes.NaiveBayesModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import water.*;
import water.fvec.*;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.ArrayList;
import java.util.List;

import static hex.genmodel.utils.DistributionFamily.AUTO;
import static org.junit.Assert.*;
import static water.TestUtil.ar;
import static water.TestUtil.parse_test_file;
import static water.fvec.VecHelper.*;


@RunWith(H2ORunner.class)
@CloudSize(1)
public class DKVManagerTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none(); 

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
            testRetainModel(model);
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
            testRetainModel(model);
            testModelDeletion(model);
        } finally {
            if (trainingFrame != null) trainingFrame.remove();
            if (preds != null) preds.remove();
            if (model != null) model.remove();
        }
    }

    /**
     * @author Michal Kurka
     */
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
          DKVManager.retain(new Key[]{f1._key});

            // Frame `f1` shouldn't lose any data 
          assertNotNull(f1._key.get());
          assertNotNull(f1.vec(1).chunkForChunkIdx(0));
          assertNull(f2._key.get());
        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void testRetain_badKey() {
      try {
        Scope.enter();
        Frame f1 = new TestFrameBuilder()
                .withVecTypes(Vec.T_NUM, Vec.T_NUM)
                .withDataForCol(0, ar(0, 1))
                .withDataForCol(1, ar(2, 3))
                .build();
        
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Please provide only Model and Frame keys.");
        // delete everything except for Frame `f1`
        DKVManager.retain(new Key[]{f1.vec(0)._key});
      } finally {
        Scope.exit();
      }
    }

    /**
     * Train a model by using a single input data frame. Retain the model. The frame should be retained (remain in DKV),
     * as the model links to it as a training frame.
     */
    @Test
    public void testRetainModels_sharedFrames() {
        try {
            Scope.enter();
            Frame trainingFrame = parse_test_file("./smalldata/iris/iris_wheader.csv");
            Scope.track(trainingFrame);

            // A little integration test
            NaiveBayesModel.NaiveBayesParameters parms = new NaiveBayesModel.NaiveBayesParameters();
            parms._train = trainingFrame._key;
            parms._valid = trainingFrame._key;
            parms._laplace = 0;
            parms._response_column = trainingFrame._names[4];
            parms._compute_metrics = false;
                    
            final Model model = new NaiveBayes(parms).trainModel().get();
            Scope.track_generic(model);

          DKVManager.retain(new Key[]{model._key});

            // Training frame of the model should be retained as well
            final Value value = DKV.get(trainingFrame._key);
            assertNotNull(value);
            assertTrue(value.isFrame());
            assertFalse(value.isDeleted());

            assertNotNull(value.get()); // The training frame should be there

        } finally {
            Scope.exit();
        }
        
    }


  /**
   * If a model has been trained using a Frame with shared vecs and the model is retained, the training frame should stay
   * intact.
   */
  @Test
  public void testRetainModels_sharedVecs() {
    try {
      Scope.enter();
      Frame trainingFrame = parse_test_file("./smalldata/iris/iris_wheader.csv");
      Scope.track(trainingFrame);

      Frame sharedFrame = new Frame(Key.<Frame>make("sharedFrame"));
      sharedFrame.add("borrowedCol", trainingFrame.vec(0));
      DKV.put(sharedFrame);
      Scope.track(sharedFrame);

      NaiveBayesModel.NaiveBayesParameters parms = new NaiveBayesModel.NaiveBayesParameters();
      parms._train = trainingFrame._key;
      parms._valid = trainingFrame._key;
      parms._laplace = 0;
      parms._response_column = trainingFrame._names[4];
      parms._compute_metrics = false;

      final Model model = new NaiveBayes(parms).trainModel().get();
      Scope.track_generic(model);

      DKVManager.retain(new Key[]{model._key});

      // The very frame with shared vecs should not exist
      final Value sharedFrameVal = DKV.get(sharedFrame._key);
      assertNull(sharedFrameVal);

      // Training frame of the model should be retained as well
      final Value trainVal = DKV.get(trainingFrame._key);
      assertNotNull(trainVal);
      assertTrue(trainVal.isFrame());
      assertFalse(trainVal.isDeleted());
      assertNotNull(trainVal.get()); // The training frame should be there

      // No shared vecs were deleted from the training frame as the sharedFrame was deleted.
      for (Key k : trainingFrame.keys()) {
        assertNotNull(DKV.get(k));
      }


    } finally {
      Scope.exit();
    }

  }

    /**
     * Train a model from a frame. Retain the frame only and let the model be deleted. The frame should remain in DKV.
     */
    @Test
    public void testdeleteModel_retainFrame() {
        try {
            Scope.enter();
            Frame trainingFrame = parse_test_file("./smalldata/iris/iris_wheader.csv");
            Scope.track(trainingFrame);

          NaiveBayesModel.NaiveBayesParameters parms = new NaiveBayesModel.NaiveBayesParameters();
            parms._train = trainingFrame._key;
            parms._laplace = 0;
            parms._response_column = trainingFrame._names[4];
            parms._compute_metrics = false;

            final Model model = new NaiveBayes(parms).trainModel().get();
            Scope.track_generic(model);

          DKVManager.retain(new Key[]{trainingFrame._key});

          // Training frame of the model should be retained as well
            final Value value = DKV.get(trainingFrame._key);
            assertNotNull(value);
            assertTrue(value.isFrame());
            assertFalse(value.isDeleted());

            assertNotNull(value.get()); // The training frame should be there
            
            assertNull(DKV.get(model._key)); // The model should be deleted

        } finally {
            Scope.exit();
        }

    }

  // Retaining models & integration test with models is in h2o-algos subproject.
  @Test
  public void testRetainFrame() {
    Frame frame = null;

    try {
      frame = parse_test_file("./smalldata/testng/airlines_train.csv");
      DKVManager.retain(new Key[]{frame._key});
      assertNotNull(DKV.get(frame._key));

      for (Vec vec : frame.vecs()) {
        assertNotNull(vec._key);

        for (int i = 0; i < vec.nChunks(); i++) {
          assertNotNull(DKV.get(vec.chunkKey(i)));
        }
      }

    } finally {
      if (frame != null) frame.delete();
    }
  }

  @Test
  public void testRetainNothing() throws InterruptedException {
    Frame frame = null;

    try {
      frame = parse_test_file("smalldata/testng/airlines_train.csv");
      DKVManager.retain(new Key[]{});
      assertNull(DKV.get(frame._key));

    } finally {
      if (frame != null) frame.delete();
    }
  }

    private static void testRetainFrame(Frame trainingFrame) {
      DKVManager.retain(new Key[]{trainingFrame._key});
      assertNotNull(DKV.get(trainingFrame._key));

        for (Vec vec : trainingFrame.vecs()) {
            assertNotNull(DKV.get(vec._key));

            for (int i = 0; i < vec.nChunks(); i++) {
              assertNotNull(vecChunkIdx(vec, i));
            }
        }
    }
    
    private static void testRetainModel(Model model){
      assertNotNull(DKV.get(model._key));
      DKVManager.retain(new Key[]{model._key});
      assertNotNull(DKV.get(model._key));
        
    }
    
    private static void testModelDeletion(final Model model){
      DKVManager.retain(new Key[]{});
      assertNull(DKV.get(model._key));
    }
}
