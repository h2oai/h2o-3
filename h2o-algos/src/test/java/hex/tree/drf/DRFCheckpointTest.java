package hex.tree.drf;

import hex.tree.CompressedTree;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.TestUtil;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;

import static water.serial.ModelSerializationTest.assertTreeEquals;
import static water.serial.ModelSerializationTest.getTrees;

public class DRFCheckpointTest extends TestUtil {

  @BeforeClass public static void stall() { stall_till_cloudsize(1); }

  /** Test if reconstructed initial frame match the last iteration
   * of DRF model builder.
   *
   * <p>This test verify multinominal model.</p>
   */
  @Test
  public void testCheckpointReconstruction4Multinomial() {
    testCheckPointReconstruction("smalldata/iris/iris.csv", 4, true, 5, 3);
  }

  /** Test if reconstructed initial frame match the last iteration
   * of DRF model builder.
   *
   * <p>This test verify binominal model.</p>
   */
  @Test
  public void testCheckpointReconstruction4Binomial() {
    testCheckPointReconstruction("smalldata/logreg/prostate.csv", 1, true, 5, 3);
  }

  /** Test throwing the right exception if non-modifiable parameter is specified.
   */
  @Test(expected = H2OIllegalArgumentException.class)
  public void testCheckpointWrongParams() {
    testCheckPointReconstruction("smalldata/iris/iris.csv", 4, true, 5, 3, 0.2f, 0.67f);
  }


  /** Test if reconstructed initial frame match the last iteration
   * of DRF model builder.
   *
   * <p>This test verify regression model.</p>
   */
  @Test
  public void testCheckpointReconstruction4Regression() {
    testCheckPointReconstruction("smalldata/logreg/prostate.csv", 8, false, 5, 3);
  }

  private void testCheckPointReconstruction(String dataset,
                                            int responseIdx,
                                            boolean classification,
                                            int ntreesInPriorModel, int ntreesInNewModel) {
    testCheckPointReconstruction(dataset, responseIdx, classification, ntreesInPriorModel, ntreesInNewModel, 0.632f, 0.632f);
  }

  private void testCheckPointReconstruction(String dataset,
                                            int responseIdx,
                                            boolean classification,
                                            int ntreesInPriorModel, int ntreesInNewModel,
                                            float sampleRateInPriorModel, float sampleRateInNewModel) {
    Frame f = parse_test_file(dataset);
    // If classification turn response into categorical
    if (classification) {
      Vec respVec = f.vec(responseIdx);
      f.replace(responseIdx, respVec.toCategoricalVec()).remove();
      DKV.put(f._key, f);
    }
    DRFModel model = null;
    DRFModel modelFromCheckpoint = null;
    DRFModel modelFinal = null;
    try {
      DRFModel.DRFParameters drfParams = new DRFModel.DRFParameters();
      drfParams._model_id = Key.make("Initial model");
      drfParams._train = f._key;
      drfParams._response_column = f.name(responseIdx);
      drfParams._ntrees = ntreesInPriorModel;
      drfParams._seed = 42;
      drfParams._max_depth = 10;
      drfParams._score_each_iteration = true;
      drfParams._sample_rate = sampleRateInPriorModel;
      model = new DRF(drfParams).trainModel().get();

      DRFModel.DRFParameters drfFromCheckpointParams = new DRFModel.DRFParameters();
      drfFromCheckpointParams._model_id = Key.make("Model from checkpoint");
      drfFromCheckpointParams._train = f._key;
      drfFromCheckpointParams._response_column = f.name(responseIdx);
      drfFromCheckpointParams._ntrees = ntreesInPriorModel + ntreesInNewModel;
      drfFromCheckpointParams._seed = 42;
      drfFromCheckpointParams._checkpoint = model._key;
      drfFromCheckpointParams._score_each_iteration = true;
      drfFromCheckpointParams._max_depth = 10;
      drfFromCheckpointParams._sample_rate = sampleRateInNewModel;
      modelFromCheckpoint = new DRF(drfFromCheckpointParams).trainModel().get();

      // Compute a separated model containing the same number of trees as a model built from checkpoint
      DRFModel.DRFParameters drfFinalParams = new DRFModel.DRFParameters();
      drfFinalParams._model_id = Key.make("Validation model");
      drfFinalParams._train = f._key;
      drfFinalParams._response_column = f.name(responseIdx);
      drfFinalParams._ntrees = ntreesInPriorModel + ntreesInNewModel;
      drfFinalParams._seed = 42;
      drfFinalParams._score_each_iteration = true;
      drfFinalParams._max_depth = 10;
      modelFinal = new DRF(drfFinalParams).trainModel().get();

      CompressedTree[][] treesFromCheckpoint = getTrees(modelFromCheckpoint);
      CompressedTree[][] treesFromFinalModel = getTrees(modelFinal);
      assertTreeEquals("The model created from checkpoint and corresponding model created from scratch should have the same trees!",
              treesFromCheckpoint, treesFromFinalModel, true);

      // Make sure we are not re-using trees
      for (int tree = 0; tree < treesFromCheckpoint.length; tree++) {
        for (int clazz = 0; clazz < treesFromCheckpoint[tree].length; clazz++) {
          if (treesFromCheckpoint[tree][clazz] !=null) { // We already verify equality of models
            CompressedTree a = treesFromCheckpoint[tree][clazz];
            CompressedTree b = treesFromFinalModel[tree][clazz];
            Assert.assertNotEquals(a._key, b._key);
          }
        }
      }
    } finally {
      if (f!=null) f.delete();
      if (model!=null) model.delete();
      if (modelFromCheckpoint!=null) modelFromCheckpoint.delete();
      if (modelFinal!=null) modelFinal.delete();
    }
  }
}
