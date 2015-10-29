package hex.tree.drf;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import hex.Model;
import hex.grid.Grid;
import hex.grid.GridSearch;
import water.DKV;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.test.util.GridTestUtils;
import water.util.ArrayUtils;

import static hex.grid.ModelFactories.DRF_MODEL_FACTORY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static water.util.ArrayUtils.interval;

public class DRFGridTest extends TestUtil {

  @BeforeClass()
  public static void setup() {
    stall_till_cloudsize(5);
  }

  @Test
  public void testCarsGrid() {
    Grid<DRFModel.DRFParameters> grid = null;
    Frame fr = null;
    Vec old = null;
    try {
      fr = parse_test_file("smalldata/junit/cars.csv");
      fr.remove("name").remove(); // Remove unique id
      old = fr.remove("cylinders");
      fr.add("cylinders", old.toCategoricalVec()); // response to last column
      DKV.put(fr);

      // Setup hyperparameter search space
      HashMap<String, Object[]> hyperParms = new HashMap<>();
      hyperParms.put("_ntrees", new Integer[]{2, 4});
      hyperParms.put("_max_depth", new Integer[]{10, 20});
      hyperParms.put("_mtries", new Integer[]{-1, 4});
      Float[] legalSampleRateOpts = new Float[]{0.5f};
      Float[] illegalSampleRateOpts = new Float[]{1f};
      hyperParms.put("_sample_rate", ArrayUtils.join(legalSampleRateOpts, illegalSampleRateOpts));
      // Name of used hyper parameters
      String[] hyperParamNames = hyperParms.keySet().toArray(new String[hyperParms.size()]);
      Arrays.sort(hyperParamNames);
      int hyperSpaceSize = ArrayUtils.crossProductSize(hyperParms);

      // Fire off a grid search
      DRFModel.DRFParameters params = new DRFModel.DRFParameters();
      params._train = fr._key;
      params._response_column = "cylinders";
      // Get the Grid for this modeling class and frame
      GridSearch gs = GridSearch.startGridSearch(params, hyperParms, DRF_MODEL_FACTORY);
      grid = (Grid<DRFModel.DRFParameters>) gs.get();
      // Make sure number of produced models match size of specified hyper space
      Assert.assertEquals("Size of grid should match to size of hyper space", hyperSpaceSize,
                          grid.getModelCount() + grid.getFailureCount());
      //
      // Make sure that names of used parameters match
      //
      String[] gridHyperNames = grid.getHyperNames();
      Arrays.sort(gridHyperNames);
      Assert.assertArrayEquals("Hyper parameters names should match!", hyperParamNames,
                               gridHyperNames);

      //
      // Make sure that values of used parameters match as well to the specified values
      //
      Model[] ms = grid.getModels();
      Map<String, Set<Object>> usedModelParams = GridTestUtils.initMap(hyperParamNames);
      for (Model m : ms) {
        DRFModel drf = (DRFModel) m;
        System.out.println(
            drf._output._scored_train[drf._output._ntrees]._mse + " " + Arrays.deepToString(
                ArrayUtils.zip(grid.getHyperNames(), grid.getHyperValues(drf._parms))));
        GridTestUtils.extractParams(usedModelParams, drf._parms, hyperParamNames);
      }
      hyperParms.put("_sample_rate", legalSampleRateOpts);
      GridTestUtils.assertParamsEqual("Grid models parameters have to cover specified hyper space",
                                      hyperParms,
                                      usedModelParams);
      // Verify model failure
      Map<String, Set<Object>> failedHyperParams = GridTestUtils.initMap(hyperParamNames);
      for (Model.Parameters failedParams : grid.getFailedParameters()) {
        GridTestUtils.extractParams(failedHyperParams, failedParams, hyperParamNames);
      }
      hyperParms.put("_sample_rate", illegalSampleRateOpts);
      GridTestUtils
          .assertParamsEqual("Failed model parameters have to correspond to specified hyper space",
                             hyperParms,
                             failedHyperParams);

    } finally {
      if (old != null) {
        old.remove();
      }
      if (fr != null) {
        fr.remove();
      }
      if (grid != null) {
        grid.remove();
      }
    }
  }

  //@Ignore("PUBDEV-1643")
  @Test
  public void testDuplicatesCarsGrid() {
    Grid grid = null;
    Frame fr = null;
    Vec old = null;
    try {
      fr = parse_test_file("smalldata/junit/cars_20mpg.csv");
      fr.remove("name").remove(); // Remove unique id
      old = fr.remove("economy");
      fr.add("economy", old); // response to last column
      DKV.put(fr);

      // Setup random hyperparameter search space
      HashMap<String, Object[]> hyperParms = new HashMap<>();
      hyperParms.put("_ntrees", new Integer[]{5, 5});
      hyperParms.put("_max_depth", new Integer[]{2, 2});
      hyperParms.put("_mtries", new Integer[]{-1, -1});
      hyperParms.put("_sample_rate", new Float[]{.1f, .1f});

      // Fire off a grid search
      DRFModel.DRFParameters params = new DRFModel.DRFParameters();
      params._train = fr._key;
      params._response_column = "economy";

      // Get the Grid for this modeling class and frame
      GridSearch gs = GridSearch.startGridSearch(params, hyperParms, DRF_MODEL_FACTORY);
      grid = (Grid) gs.get();

      // Check that duplicate model have not been constructed
      Model[] models = grid.getModels();
      assertTrue("Number of returned models has to be > 0", models.length > 0);
      // But all off them should be same
      Key<Model> modelKey = models[0]._key;
      for (Model m : models) {
        assertTrue("Number of constructed models has to be equal to 1", modelKey == m._key);
      }
    } finally {
      if (old != null) {
        old.remove();
      }
      if (fr != null) {
        fr.remove();
      }
      if (grid != null) {
        grid.remove();
      }
    }
  }

  //@Ignore("PUBDEV-1648")
  @Test
  public void testRandomCarsGrid() {
    Grid grid = null;
    DRFModel drfRebuilt = null;
    Frame fr = null;
    try {
      fr = parse_test_file("smalldata/junit/cars.csv");
      fr.remove("name").remove();
      Vec old = fr.remove("economy (mpg)");
      fr.add("economy (mpg)", old); // response to last column
      DKV.put(fr);

      // Setup random hyperparameter search space
      HashMap<String, Object[]> hyperParms = new HashMap<>();

      // Construct random grid search space
      long seed = System.nanoTime();
      Random rng = new Random(seed);

      // Limit to 1-3 randomly, 4 times.  Average total number of models is
      // 2^4, or 16.  Max is 81 models.
      Integer ntreesDim = rng.nextInt(3) + 1;
      Integer maxDepthDim = rng.nextInt(3) + 1;
      Integer mtriesDim = rng.nextInt(3) + 1;
      Integer sampleRateDim = rng.nextInt(3) + 1;

      Integer[] ntreesArr = interval(1, 15);
      ArrayList<Integer> ntreesList = new ArrayList<>(Arrays.asList(ntreesArr));
      Collections.shuffle(ntreesList);
      Integer[] ntreesSpace = new Integer[ntreesDim];
      for (int i = 0; i < ntreesDim; i++) {
        ntreesSpace[i] = ntreesList.get(i);
      }

      Integer[] maxDepthArr = interval(1, 10);
      ArrayList<Integer> maxDepthList = new ArrayList<>(Arrays.asList(maxDepthArr));
      Collections.shuffle(maxDepthList);
      Integer[] maxDepthSpace = new Integer[maxDepthDim];
      for (int i = 0; i < maxDepthDim; i++) {
        maxDepthSpace[i] = maxDepthList.get(i);
      }

      Integer[] mtriesArr = interval(1, 5);
      ArrayList<Integer> mtriesList = new ArrayList<>(Arrays.asList(mtriesArr));
      Collections.shuffle(mtriesList);
      Integer[] mtriesSpace = new Integer[mtriesDim];
      for (int i = 0; i < mtriesDim; i++) {
        mtriesSpace[i] = mtriesList.get(i);
      }

      Float[] sampleRateArr = interval(0.01f, 0.99f, 0.01f);
      ArrayList<Float> sampleRateList = new ArrayList<>(Arrays.asList(sampleRateArr));
      Collections.shuffle(sampleRateList);
      Float[] sampleRateSpace = new Float[sampleRateDim];
      for (int i = 0; i < sampleRateDim; i++) {
        sampleRateSpace[i] = sampleRateList.get(i);
      }

      hyperParms.put("_ntrees", ntreesSpace);
      hyperParms.put("_max_depth", maxDepthSpace);
      hyperParms.put("_mtries", mtriesSpace);
      hyperParms.put("_sample_rate", sampleRateSpace);

      // Fire off a grid search
      DRFModel.DRFParameters params = new DRFModel.DRFParameters();
      params._train = fr._key;
      params._response_column = "economy (mpg)";
      // Get the Grid for this modeling class and frame
      GridSearch gs = GridSearch.startGridSearch(params, hyperParms, DRF_MODEL_FACTORY);
      grid = (Grid) gs.get();

      System.out.println("Test seed: " + seed);
      System.out.println("ntrees search space: " + Arrays.toString(ntreesSpace));
      System.out.println("max_depth search space: " + Arrays.toString(maxDepthSpace));
      System.out.println("mtries search space: " + Arrays.toString(mtriesSpace));
      System.out.println("sample_rate search space: " + Arrays.toString(sampleRateSpace));

      // Check that cardinality of grid
      Model[] ms = grid.getModels();
      int numModels = ms.length;
      System.out.println("Grid consists of " + numModels + " models");
      assertEquals("Number of models should match hyper space size", numModels,
                   ntreesDim * maxDepthDim * sampleRateDim * mtriesDim + grid.getFailureCount());

      // Pick a random model from the grid
      HashMap<String, Object[]> randomHyperParms = new HashMap<>();

      Integer ntreeVal = ntreesSpace[rng.nextInt(ntreesSpace.length)];
      randomHyperParms.put("_ntrees", new Integer[]{ntreeVal});

      Integer maxDepthVal = maxDepthSpace[rng.nextInt(maxDepthSpace.length)];
      randomHyperParms.put("_max_depth", maxDepthSpace);

      Integer mtriesVal = mtriesSpace[rng.nextInt(mtriesSpace.length)];
      randomHyperParms.put("_max_depth", mtriesSpace);

      Float sampleRateVal = sampleRateSpace[rng.nextInt(sampleRateSpace.length)];
      randomHyperParms.put("_sample_rate", sampleRateSpace);

      //TODO: DRFModel drfFromGrid = (DRFModel) g2.model(randomHyperParms).get();

      // Rebuild it with it's parameters
      params._ntrees = ntreeVal;
      params._max_depth = maxDepthVal;
      params._mtries = mtriesVal;
      DRF job = null;
      try {
        job = new DRF(params);
        drfRebuilt = job.trainModel().get();
      } finally {
        if (job != null) {
          job.remove();
        }
      }
      assertTrue(job._state == water.Job.JobState.DONE);

      // Make sure the MSE metrics match
      //double fromGridMSE = drfFromGrid._output._scored_train[drfFromGrid._output._ntrees]._mse;
      double rebuiltMSE = drfRebuilt._output._scored_train[drfRebuilt._output._ntrees]._mse;
      //System.out.println("The random grid model's MSE: " + fromGridMSE);
      System.out.println("The rebuilt model's MSE: " + rebuiltMSE);
      //assertEquals(fromGridMSE, rebuiltMSE);

    } finally {
      if (fr != null) {
        fr.remove();
      }
      if (grid != null) {
        grid.remove();
      }
      if (drfRebuilt != null) {
        drfRebuilt.remove();
      }
    }
  }

  @Test
  public void testCollisionOfDRFParamsChecksum() {
    Frame fr = null;
    try {
      fr = parse_test_file("smalldata/junit/cars.csv");
      fr.remove("name").remove();
      Vec old = fr.remove("economy (mpg)");
      fr.add("economy (mpg)", old); // response to last column
      DKV.put(fr);

      // {"_model_id":null,"_train":{"name":"_83da9e0754c5eb9f6b812fe17e7945e5","type":"Key"},"_valid":null,"_nfolds":0,"_keep_cross_validation_predictions":false,"_fold_assignment":"AUTO","_distribution":"AUTO","_tweedie_power":1.5,"_ignored_columns":null,"_ignore_const_cols":true,"_weights_column":null,"_offset_column":null,"_fold_column":null,"_score_each_iteration":false,"_response_column":"economy (mpg)","_balance_classes":false,"_max_after_balance_size":5.0,"_class_sampling_factors":null,"_max_hit_ratio_k":10,"_max_confusion_matrix_size":20,"_checkpoint":null,"_ntrees":9,"_max_depth":15,"_min_rows":1.0,"_nbins":20,"_nbins_cats":1024,"_r2_stopping":0.999999,"_seed":-4522296119273841674,"_nbins_top_level":1024,"_build_tree_one_node":false,"_initial_score_interval":4000,"_score_interval":4000,"_mtries":3,"_sample_rate":0.6499997,"_binomial_double_trees":false}
      DRFModel.DRFParameters params1 = new DRFModel.DRFParameters();
      params1._train = fr._key;
      params1._response_column = "economy (mpg)";
      params1._seed = -4522296119273841674L;
      params1._mtries = 3;
      params1._max_depth = 15;
      params1._ntrees = 9;
      params1._sample_rate = 0.6499997f;

      // {"_model_id":null,"_train":{"name":"_83da9e0754c5eb9f6b812fe17e7945e5","type":"Key"},"_valid":null,"_nfolds":0,"_keep_cross_validation_predictions":false,"_fold_assignment":"AUTO","_distribution":"AUTO","_tweedie_power":1.5,"_ignored_columns":null,"_ignore_const_cols":true,"_weights_column":null,"_offset_column":null,"_fold_column":null,"_score_each_iteration":false,"_response_column":"economy (mpg)","_balance_classes":false,"_max_after_balance_size":5.0,"_class_sampling_factors":null,"_max_hit_ratio_k":10,"_max_confusion_matrix_size":20,"_checkpoint":null,"_ntrees":13,"_max_depth":1,"_min_rows":1.0,"_nbins":20,"_nbins_cats":1024,"_r2_stopping":0.999999,"_seed":-4522296119273841674,"_nbins_top_level":1024,"_build_tree_one_node":false,"_initial_score_interval":4000,"_score_interval":4000,"_mtries":1,"_sample_rate":0.6499997,"_binomial_double_trees":false}
      DRFModel.DRFParameters params2 = new DRFModel.DRFParameters();
      params2._train = fr._key;
      params2._response_column = "economy (mpg)";
      params2._seed = -4522296119273841674L;
      params2._mtries = 1;
      params2._max_depth = 1;
      params2._ntrees = 13;
      params2._sample_rate = 0.6499997f;
      long csum1 = params1.checksum();
      long csum2 = params2.checksum();
      Assert.assertNotEquals("Checksums shoudl be different", csum1, csum2);
    } finally {
      if (fr != null) {
        fr.remove();
      }
    }
  }
}
