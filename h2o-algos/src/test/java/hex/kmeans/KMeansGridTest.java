package hex.kmeans;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
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
import water.test.util.GridTestUtils;
import water.util.ArrayUtils;

import static hex.grid.ModelFactories.KMEANS_MODEL_FACTORY;
import static org.junit.Assert.assertTrue;


public class KMeansGridTest extends TestUtil {

  @BeforeClass()
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void testIrisGrid() {
    Grid grid = null;
    Frame fr = null;
    try {
      fr = parse_test_file("smalldata/iris/iris_wheader.csv");

      // 4-dimensional hyperparameter search
      HashMap<String, Object[]> hyperParms = new HashMap<>();

      // Search over this range of K's
      hyperParms.put("_k", new Integer[]{1, 2, 3, 4, 5,
                                         6}); // Note that k==0 is illegal, and k==1 is trivial

      // Search over this range of the init enum
      hyperParms.put("_init", new KMeans.Initialization[]{
          KMeans.Initialization.Random,
          KMeans.Initialization.PlusPlus,
          KMeans.Initialization.Furthest});

      // Search over this range of the init enum
      hyperParms.put("_seed", new Long[]{/* 0L, */ 1L, 123456789L, 987654321L});
      // Name of used hyper parameters
      String[] hyperParamNames = hyperParms.keySet().toArray(new String[hyperParms.size()]);
      Arrays.sort(hyperParamNames);
      int hyperSpaceSize = ArrayUtils.crossProductSize(hyperParms);

      // Create default parameters
      KMeansModel.KMeansParameters params = new KMeansModel.KMeansParameters();
      params._train = fr._key;
      // Fire off a grid search and get result
      GridSearch gs = GridSearch.startGridSearch(params, hyperParms, KMEANS_MODEL_FACTORY);
      grid = (Grid) gs.get();
      // Make sure number of produced models match size of specified hyper space
      Assert.assertEquals("Size of grid should match to size of hyper space", hyperSpaceSize,
                          grid.getModelKeys().length);
      //
      // Make sure that names of used parameters match
      //
      String [] gridHyperNames = grid.getHyperNames();
      Arrays.sort(gridHyperNames);
      Assert.assertArrayEquals("Hyper parameters names should match!", hyperParamNames,
                               gridHyperNames);


      //
      // Make sure that values of used parameters match as well to the specified values
      //
      Map<String, Set<Object>> usedModelParams = GridTestUtils.initMap(hyperParamNames);
      Model[] ms = grid.getModels();
      for (Model m : ms) {
        KMeansModel kmm = (KMeansModel) m;
        System.out.println(kmm._output._tot_withinss + " " + Arrays.deepToString(
            ArrayUtils.zip(grid.getHyperNames(), grid.getHyperValues(kmm._parms))));
        GridTestUtils.extractParams(usedModelParams, kmm._parms, hyperParamNames);
      }
      GridTestUtils.assertParamsEqual("Grid models parameters have to cover specified hyper space",
                                      hyperParms,
                                      usedModelParams);
    } finally {
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
    try {
      fr = parse_test_file("smalldata/iris/iris_wheader.csv");
      fr.remove("class").remove();
      DKV.put(fr);

      // Setup hyperparameter search space
      HashMap<String, Object[]> hyperParms = new HashMap<>();
      hyperParms.put("_k", new Integer[]{3, 3, 3});
      hyperParms.put("_init", new KMeans.Initialization[]{
          KMeans.Initialization.Random,
          KMeans.Initialization.Random,
          KMeans.Initialization.Random});
      hyperParms.put("_seed", new Long[]{123456789L, 123456789L, 123456789L});

      // Fire off a grid search
      KMeansModel.KMeansParameters params = new KMeansModel.KMeansParameters();
      params._train = fr._key;
      // Get the Grid for this modeling class and frame
      GridSearch gs = GridSearch.startGridSearch(params, hyperParms, KMEANS_MODEL_FACTORY);
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
      if (fr != null) {
        fr.remove();
      }
      if (grid != null) {
        grid.remove();
      }
    }
  }

  @Test
  public void testUserPointsCarsGrid() {
    Grid grid = null;
    Frame fr = null;
    Frame init = frame(ard(ard(5.0, 3.4, 1.5, 0.2),
                           ard(7.0, 3.2, 4.7, 1.4),
                           ard(6.5, 3.0, 5.8, 2.2)));
    try {
      fr = parse_test_file("smalldata/iris/iris_wheader.csv");
      fr.remove("class").remove();
      DKV.put(fr);

      // Setup hyperparameter search space
      HashMap<String, Object[]> hyperParms = new HashMap<>();
      hyperParms.put("_k", new Integer[]{3});
      hyperParms.put("_init", new KMeans.Initialization[]{
          KMeans.Initialization.Random,
          KMeans.Initialization.PlusPlus,
          KMeans.Initialization.User,
          KMeans.Initialization.Furthest});
      hyperParms.put("_seed", new Long[]{123456789L});

      // Fire off a grid search
      KMeansModel.KMeansParameters params = new KMeansModel.KMeansParameters();
      params._train = fr._key;
      params._user_points = init._key;
      // Get the Grid for this modeling class and frame
      GridSearch gs = GridSearch.startGridSearch(params, hyperParms, KMEANS_MODEL_FACTORY);
      grid = (Grid) gs.get();

      // Check that duplicate model have not been constructed
      Integer numModels = grid.getModels().length;
      System.out.println("Grid consists of " + numModels + " models");
      assertTrue(numModels == 4);
    } finally {
      if (fr != null) {
        fr.remove();
      }
      if (init != null) {
        init.remove();
      }
      if (grid != null) {
        grid.remove();
      }
    }
  }

  @Ignore("PUBDEV-1675")
  public void testRandomCarsGrid() {
    Grid grid = null;
    KMeansModel kmRebuilt = null;
    Frame fr = null;
    Frame init = frame(ard(ard(5.0, 3.4, 1.5, 0.2),
                           ard(7.0, 3.2, 4.7, 1.4),
                           ard(6.5, 3.0, 5.8, 2.2)));
    try {
      fr = parse_test_file("smalldata/iris/iris_wheader.csv");
      fr.remove("class").remove();
      DKV.put(fr);

      // Setup random hyperparameter search space
      HashMap<String, Object[]> hyperParms = new HashMap<>();

      // Construct random grid search space
      Random rng = new Random();

      Integer kDim = rng.nextInt(4) + 1;
      Integer initDim = rng.nextInt(4) + 1;
      Integer seedDim = rng.nextInt(4) + 1;
      Integer standardizeDim = rng.nextInt(2) + 1;

      Integer[]
          kArr =
          new Integer[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21,
                        22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
                        41, 42, 43, 44, 45, 46, 47,
                        48, 49, 50};
      ArrayList<Integer> kList = new ArrayList<Integer>(Arrays.asList(kArr));
      Collections.shuffle(kList);
      Integer[] kSpace = new Integer[kDim];
      for (int i = 0; i < kDim; i++) {
        kSpace[i] = kList.get(i);
      }

      KMeans.Initialization[] initArr = new KMeans.Initialization[]{KMeans.Initialization.Random,
                                                                    KMeans.Initialization.User,
                                                                    KMeans.Initialization.PlusPlus,
                                                                    KMeans.Initialization.Furthest};
      ArrayList<KMeans.Initialization>
          initList =
          new ArrayList<KMeans.Initialization>(Arrays.asList(initArr));
      Collections.shuffle(initList);
      KMeans.Initialization[] initSpace = new KMeans.Initialization[initDim];
      for (int i = 0; i < initDim; i++) {
        initSpace[i] = initList.get(i);
      }

      Long[] seedArr = new Long[]{0L, 1L, 123456789L, 987654321L};
      ArrayList<Long> seedList = new ArrayList<Long>(Arrays.asList(seedArr));
      Collections.shuffle(seedList);
      Long[] seedSpace = new Long[seedDim];
      for (int i = 0; i < seedDim; i++) {
        seedSpace[i] = seedList.get(i);
      }

      Integer[] standardizeArr = new Integer[]{1, 0};
      ArrayList<Integer> standardizeList = new ArrayList<Integer>(Arrays.asList(standardizeArr));
      Collections.shuffle(standardizeList);
      Integer[] standardizeSpace = new Integer[standardizeDim];
      for (int i = 0; i < standardizeDim; i++) {
        standardizeSpace[i] = standardizeList.get(i);
      }

      hyperParms.put("_k", kSpace);
      hyperParms.put("_init", initSpace);
      hyperParms.put("_seed", seedSpace);
      hyperParms.put("_standardize", standardizeSpace);

      System.out.println("k search space: " + Arrays.toString(kSpace));
      System.out.println("max_depth search space: " + Arrays.toString(initSpace));
      System.out.println("seed search space: " + Arrays.toString(seedSpace));
      System.out.println("sample_rate search space: " + Arrays.toString(standardizeSpace));

      // Fire off a grid search
      KMeansModel.KMeansParameters params = new KMeansModel.KMeansParameters();
      params._train = fr._key;
      if (Arrays.asList(initSpace).contains(KMeans.Initialization.User)) {
        params._user_points = init._key;
      }
      // Get the Grid for this modeling class and frame
      GridSearch gs = GridSearch.startGridSearch(params, hyperParms, KMEANS_MODEL_FACTORY);
      grid = (Grid) gs.get();

      // Check that cardinality of grid
      Model[] ms = grid.getModels();
      Integer numModels = ms.length;
      System.out.println("Grid consists of " + numModels + " models");
      assertTrue(numModels == kDim * initDim * standardizeDim * seedDim);

      // Pick a random model from the grid
      HashMap<String, Object[]> randomHyperParms = new HashMap<>();

      Integer kVal = kSpace[rng.nextInt(kSpace.length)];
      randomHyperParms.put("_k", new Integer[]{kVal});

      KMeans.Initialization initVal = initSpace[rng.nextInt(initSpace.length)];
      randomHyperParms.put("_init", initSpace);

      Long seedVal = seedSpace[rng.nextInt(seedSpace.length)];
      randomHyperParms.put("_seed", seedSpace);

      Integer standardizeVal = standardizeSpace[rng.nextInt(standardizeSpace.length)];
      randomHyperParms.put("_standardize", standardizeSpace);

      //TODO: KMeansModel kmFromGrid = (KMeansModel) g2.model(randomHyperParms).get();

      // Rebuild it with it's parameters
      params._k = kVal;
      params._init = initVal;
      params._seed = seedVal;
      params._standardize = standardizeVal == 1;
      KMeans job = null;
      try {
        job = new KMeans(params);
        kmRebuilt = job.trainModel().get();
      } finally {
        if (job != null) {
          job.remove();
        }
      }
      assertTrue(job._state == water.Job.JobState.DONE);

      // Make sure the betweenss metrics match
      //double fromGridBetweenss = kmFromGrid._output._betweenss;
      double rebuiltBetweenss = kmRebuilt._output._betweenss;
      //System.out.println("The random grid model's betweenss: " + fromGridBetweenss);
      System.out.println("The rebuilt model's betweenss: " + rebuiltBetweenss);
      //assertEquals(fromGridBetweenss, rebuiltBetweenss);

    } finally {
      if (fr != null) {
        fr.remove();
      }
      if (grid != null) {
        grid.remove();
      }
      if (kmRebuilt != null) {
        kmRebuilt.remove();
      }
      if (init != null) {
        init.remove();
      }
    }
  }
}
