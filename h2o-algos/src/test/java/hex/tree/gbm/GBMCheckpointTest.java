package hex.tree.gbm;

import hex.tree.CompressedTree;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.TestUtil;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;

import static water.serial.ModelSerializationTest.assertTreeEquals;
import static water.serial.ModelSerializationTest.getTrees;

public class GBMCheckpointTest extends TestUtil {

  @BeforeClass public static void stall() { stall_till_cloudsize(1); }

  @Test
  public void testCheckpointReconstruction4Multinomial() {
    testCheckPointReconstruction("smalldata/iris/iris.csv", 4, true, 5, 3);
  }

  @Test
  public void testCheckpointReconstruction4Binomial() {
    testCheckPointReconstruction("smalldata/logreg/prostate.csv", 1, true, 5, 3);
  }

  /** Test throwing the right exception if non-modifiable parameter is specified.
   */
  @Test(expected = H2OIllegalArgumentException.class)
  @Ignore
  public void testCheckpointWrongParams() {
    testCheckPointReconstruction("smalldata/iris/iris.csv", 4, true, 5, 3, 0.2f, 0.67f);
  }

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
    // If classification turn response into enum
    if (classification) {
      Vec respVec = f.vec(responseIdx);
      f.replace(responseIdx, respVec.toEnum()).remove();
      DKV.put(f._key, f);
    }
    GBMModel model = null;
    GBMModel modelFromCheckpoint = null;
    GBMModel modelFinal = null;
    try {
      GBMModel.GBMParameters gbmParams = new GBMModel.GBMParameters();
      gbmParams._model_id = Key.make("Initial model");
      gbmParams._train = f._key;
      gbmParams._response_column = f.name(responseIdx);
      gbmParams._ntrees = ntreesInPriorModel;
      gbmParams._seed = 42;
      gbmParams._max_depth = 10;
      gbmParams._score_each_iteration = true;
      model = new GBM(gbmParams).trainModel().get();

      GBMModel.GBMParameters gbmFromCheckpointParams = new GBMModel.GBMParameters();
      gbmFromCheckpointParams._model_id = Key.make("Model from checkpoint");
      gbmFromCheckpointParams._train = f._key;
      gbmFromCheckpointParams._response_column = f.name(responseIdx);
      gbmFromCheckpointParams._ntrees = ntreesInPriorModel + ntreesInNewModel;
      gbmFromCheckpointParams._seed = 42;
      gbmFromCheckpointParams._checkpoint = model._key;
      gbmFromCheckpointParams._score_each_iteration = true;
      gbmFromCheckpointParams._max_depth = 10;
      modelFromCheckpoint = new GBM(gbmFromCheckpointParams).trainModel().get();

      // Compute a separated model containing the same numnber of trees as a model built from checkpoint
      GBMModel.GBMParameters gbmFinalParams = new GBMModel.GBMParameters();
      gbmFinalParams._model_id = Key.make("Validation model");
      gbmFinalParams._train = f._key;
      gbmFinalParams._response_column = f.name(responseIdx);
      gbmFinalParams._ntrees = ntreesInPriorModel + ntreesInNewModel;
      gbmFinalParams._seed = 42;
      gbmFinalParams._score_each_iteration = true;
      gbmFinalParams._max_depth = 10;
      modelFinal = new GBM(gbmFinalParams).trainModel().get();

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
