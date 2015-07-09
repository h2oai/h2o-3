package hex.tree.drf;

import hex.Grid;
import hex.Model;

import java.util.*;
import org.junit.*;
import water.DKV;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;

import static org.junit.Assert.assertTrue;

public class DRFGridTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test public void testCarsGrid() {
    DRFGrid drfg = null;
    Frame fr = null;
    Vec old = null;
    try {
      fr = parse_test_file("smalldata/junit/cars.csv");
      fr.remove("name").remove(); // Remove unique id
      old = fr.remove("cylinders");
      fr.add("cylinders",old.toEnum()); // response to last column
      DKV.put(fr);

      // Get the Grid for this modeling class and frame
      drfg = DRFGrid.get(fr);

      // Setup hyperparameter search space
      HashMap<String,Object[]> hyperParms = new HashMap<>();
      hyperParms.put("_ntrees",new Integer[]{20,40});
      hyperParms.put("_max_depth",new Integer[]{10,20});
      hyperParms.put("_mtries",new Integer[]{-1,4,5});

      // Fire off a grid search
      DRFModel.DRFParameters params = new DRFModel.DRFParameters();
      params._train = fr._key;
      params._response_column = "cylinders";
      Grid.GridSearch gs = drfg.startGridSearch(params, hyperParms);
      Grid g2 = (Grid)gs.get();
      assert g2==drfg;

      // Print out the models from this grid search
      Model[] ms = gs.models();
      for( Model m : ms ) {
        DRFModel drf = (DRFModel)m;
        System.out.println(drf._output._scored_train[drf._output._ntrees]._mse + " " + Arrays.toString(g2.getHypers(drf._parms)));
      }

    } finally {
      if( old != null ) old.remove();
      if( fr != null ) fr.remove();
      if( drfg != null ) drfg.remove();
    }
  }

  @Ignore("PUBDEV-1643")
  public void testDuplicatesCarsGrid() {
    DRFGrid drfg = null;
    Frame fr = null;
    Vec old = null;
    try {
      fr = parse_test_file("smalldata/junit/cars_20mpg.csv");
      fr.remove("name").remove(); // Remove unique id
      old = fr.remove("economy");
      fr.add("economy", old); // response to last column
      DKV.put(fr);

      // Get the Grid for this modeling class and frame
      drfg = DRFGrid.get(fr);

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
      Grid.GridSearch gs = drfg.startGridSearch(params, hyperParms);
      Grid g2 = (Grid) gs.get();
      assert g2 == drfg;

      // Check that duplicate model have not been constructed
      Integer numModels = gs.models().length;
      System.out.println("Grid consists of " + numModels + " models");
      assertTrue(numModels==1);

    } finally {
      if (old != null) old.remove();
      if (fr != null) fr.remove();
      if (drfg != null) drfg.remove();
    }
  }

  @Ignore("PUBDEV-1648")
  public void testRandomCarsGrid() {
    DRFGrid drfg = null;
    DRFModel drfRebuilt = null;
    Frame fr = null;
    Vec old = null;
    try {
      fr = parse_test_file("smalldata/junit/cars.csv");
      fr.remove("name").remove();
      old = fr.remove("economy (mpg)");

      fr.add("economy (mpg)", old); // response to last column
      DKV.put(fr);

      // Get the Grid for this modeling class and frame
      drfg = DRFGrid.get(fr);

      // Setup random hyperparameter search space
      HashMap<String, Object[]> hyperParms = new HashMap<>();

      // Construct random grid search space
      Random rng = new Random();

      Integer ntreesDim = rng.nextInt(4)+1;
      Integer maxDepthDim = rng.nextInt(4)+1;
      Integer mtriesDim = rng.nextInt(4)+1;
      Integer sampleRateDim = rng.nextInt(4)+1;

      Integer[] ntreesArr = new Integer[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21,
              22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47,
              48, 49, 50};
      ArrayList<Integer> ntreesList = new ArrayList<Integer>(Arrays.asList(ntreesArr));
      Collections.shuffle(ntreesList);
      Integer[] ntreesSpace = new Integer[ntreesDim];
      for(int i=0; i<ntreesDim; i++){ ntreesSpace[i] = ntreesList.get(i); }

      Integer[] maxDepthArr = new Integer[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20};
      ArrayList<Integer> maxDepthList = new ArrayList<Integer>(Arrays.asList(maxDepthArr));
      Collections.shuffle(maxDepthList);
      Integer[] maxDepthSpace = new Integer[maxDepthDim];
      for(int i=0; i<maxDepthDim; i++){ maxDepthSpace[i] = maxDepthList.get(i); }

      Integer[] mtriesArr = new Integer[]{1, 2, 3, 4, 5, 6};
      ArrayList<Integer> mtriesList = new ArrayList<Integer>(Arrays.asList(mtriesArr));
      Collections.shuffle(mtriesList);
      Integer[] mtriesSpace = new Integer[mtriesDim];
      for(int i=0; i<mtriesDim; i++){ mtriesSpace[i] = mtriesList.get(i); }

      Float[] sampleRateArr = new Float[]{0.01f, 0.02f, 0.03f, 0.04f, 0.05f, 0.06f, 0.07f, 0.08f, 0.09f, 0.1f, 0.11f,
              0.12f, 0.13f, 0.14f, 0.15f, 0.16f, 0.17f, 0.18f, 0.19f, 0.2f, 0.21f, 0.22f, 0.23f, 0.24f, 0.25f, 0.26f,
              0.27f, 0.28f, 0.29f, 0.3f, 0.31f, 0.32f, 0.33f, 0.34f, 0.35f, 0.36f, 0.37f, 0.38f, 0.39f, 0.4f, 0.41f,
              0.42f, 0.43f, 0.44f, 0.45f, 0.46f, 0.47f, 0.48f, 0.49f, 0.5f, 0.51f, 0.52f, 0.53f, 0.54f, 0.55f, 0.56f,
              0.57f, 0.58f, 0.59f, 0.6f, 0.61f, 0.62f, 0.63f, 0.64f, 0.65f, 0.66f, 0.67f, 0.68f, 0.69f, 0.7f, 0.71f,
              0.72f, 0.73f, 0.74f, 0.75f, 0.76f, 0.77f, 0.78f, 0.79f, 0.8f, 0.81f, 0.82f, 0.83f, 0.84f, 0.85f, 0.86f,
              0.87f, 0.88f, 0.89f, 0.9f, 0.91f, 0.92f, 0.93f, 0.94f, 0.95f, 0.96f, 0.97f, 0.98f, 0.99f, 1.0f};
      ArrayList<Float> sampleRateList = new ArrayList<Float>(Arrays.asList(sampleRateArr));
      Collections.shuffle(sampleRateList);
      Float[] sampleRateSpace = new Float[sampleRateDim];
      for(int i=0; i<sampleRateDim; i++){ sampleRateSpace[i] = sampleRateList.get(i); }

      hyperParms.put("_ntrees", ntreesSpace);
      hyperParms.put("_max_depth", maxDepthSpace);
      hyperParms.put("_mtries", mtriesSpace);
      hyperParms.put("_sample_rate", sampleRateSpace);

      // Fire off a grid search
      DRFModel.DRFParameters params = new DRFModel.DRFParameters();
      params._train = fr._key;
      params._response_column = "economy (mpg)";
      Grid.GridSearch gs = drfg.startGridSearch(params, hyperParms);
      Grid g2 = (Grid) gs.get();
      assert g2 == drfg;

      System.out.println("ntrees search space: " + Arrays.toString(ntreesSpace));
      System.out.println("max_depth search space: " + Arrays.toString(maxDepthSpace));
      System.out.println("mtries search space: " + Arrays.toString(mtriesSpace));
      System.out.println("sample_rate search space: " + Arrays.toString(sampleRateSpace));

      // Check that cardinality of grid
      Model[] ms = gs.models();
      Integer numModels = ms.length;
      System.out.println("Grid consists of " + numModels + " models");
      assertTrue(numModels == ntreesDim * maxDepthDim * sampleRateDim * mtriesDim);

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
        if (job != null) job.remove();
      }
      assertTrue(job._state == water.Job.JobState.DONE);

      // Make sure the MSE metrics match
      //double fromGridMSE = drfFromGrid._output._scored_train[drfFromGrid._output._ntrees]._mse;
      double rebuiltMSE = drfRebuilt._output._scored_train[drfRebuilt._output._ntrees]._mse;
      //System.out.println("The random grid model's MSE: " + fromGridMSE);
      System.out.println("The rebuilt model's MSE: " + rebuiltMSE);
      //assertEquals(fromGridMSE, rebuiltMSE);

    } finally {
      if (old != null) old.remove();
      if (fr != null) fr.remove();
      if (drfg != null) drfg.remove();
      if (drfRebuilt != null) drfRebuilt.remove();
    }
  }
}
