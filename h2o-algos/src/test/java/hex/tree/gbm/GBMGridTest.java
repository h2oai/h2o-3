package hex.tree.gbm;

import hex.Grid;
import hex.Model;
import hex.tree.gbm.GBMModel.GBMParameters.Family;
import java.util.*;
import org.junit.*;
import water.DKV;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GBMGridTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test public void testCarsGrid() {
    GBMGrid gbmg = null;
    Frame fr = null;
    Vec old = null;
    try {
      fr = parse_test_file("smalldata/junit/cars.csv");
      fr.remove("name").remove(); // Remove unique id
      old = fr.remove("cylinders");
      fr.add("cylinders",old.toEnum()); // response to last column
      DKV.put(fr);

      // Get the Grid for this modeling class and frame
      gbmg = GBMGrid.get(fr);

      // Setup hyperparameter search space
      HashMap<String,Object[]> hyperParms = new HashMap<>();
      hyperParms.put("_ntrees",new Integer[]{5,10});
      hyperParms.put("_distribution",new Family[] {Family.multinomial});
      hyperParms.put("_max_depth",new Integer[]{1,2,5});
      hyperParms.put("_learn_rate",new Float[]{0.01f,0.1f,0.3f});

      // Fire off a grid search
      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = fr._key;
      params._response_column = "cylinders";
      Grid.GridSearch gs = gbmg.startGridSearch(params, hyperParms);
      Grid g2 = (Grid)gs.get();
      assert g2==gbmg;

      // Print out the models from this grid search
      Model[] ms = gs.models();
      for( Model m : ms ) {
        GBMModel gbm = (GBMModel)m;
        System.out.println(gbm._output._scored_train[gbm._output._ntrees]._mse + " " + Arrays.toString(g2.getHypers(gbm._parms)));
      }

    } finally {
      if( old != null ) old.remove();
      if( fr != null ) fr.remove();
      if( gbmg != null ) gbmg.remove();
    }
  }

  @Ignore("PUBDEV-1643")
  public void testDuplicatesCarsGrid() {
    GBMGrid gbmg = null;
    Frame fr = null;
    Vec old = null;
    try {
      fr = parse_test_file("smalldata/junit/cars_20mpg.csv");
      fr.remove("name").remove(); // Remove unique id
      old = fr.remove("economy");
      fr.add("economy", old); // response to last column
      DKV.put(fr);

      // Get the Grid for this modeling class and frame
      gbmg = GBMGrid.get(fr);

      // Setup random hyperparameter search space
      HashMap<String, Object[]> hyperParms = new HashMap<>();
      hyperParms.put("_distribution", new Family[]{Family.gaussian});
      hyperParms.put("_ntrees", new Integer[]{5, 5});
      hyperParms.put("_max_depth", new Integer[]{2, 2});
      hyperParms.put("_learn_rate", new Float[]{.1f, .1f});

      // Fire off a grid search
      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = fr._key;
      params._response_column = "economy";
      Grid.GridSearch gs = gbmg.startGridSearch(params, hyperParms);
      Grid g2 = (Grid) gs.get();
      assert g2 == gbmg;

      // Check that duplicate model have not been constructed
      Integer numModels = gs.models().length;
      System.out.println("Grid consists of " + numModels + " models");
      assertTrue(numModels==1);

    } finally {
      if (old != null) old.remove();
      if (fr != null) fr.remove();
      if (gbmg != null) gbmg.remove();
    }
  }

  @Ignore("PUBDEV-1648")
  public void testRandomCarsGrid() {
    GBMGrid gbmg = null;
    GBMModel gbmRebuilt = null;
    Frame fr = null;
    Vec old = null;
    try {
      fr = parse_test_file("smalldata/junit/cars_20mpg.csv");
      fr.remove("name").remove(); // Remove unique id
      old = fr.remove("economy_20mpg");
      fr.add("economy_20mpg", old.toEnum()); // response to last column
      DKV.put(fr);

      // Get the Grid for this modeling class and frame
      gbmg = GBMGrid.get(fr);

      // Setup random hyperparameter search space
      HashMap<String, Object[]> hyperParms = new HashMap<>();
      hyperParms.put("_distribution", new Family[]{Family.bernoulli});

      // Construct random grid search space
      Random rng = new Random();

      Integer ntreesDim = rng.nextInt(3)+1;
      Integer maxDepthDim = rng.nextInt(3)+1;
      Integer learnRateDim = rng.nextInt(3)+1;

      ArrayList<Integer> ntreesList = new ArrayList<Integer>() {{ add(1); add(2); add(3); add(4); add(5); add(6);
        add(7); add(8); add(9); add(10);}};
      Collections.shuffle(ntreesList);
      Integer[] ntreesSpace = new Integer[ntreesDim];
      for(int i=0; i<ntreesDim; i++){ ntreesSpace[i] = ntreesList.get(i); }

      ArrayList<Integer> maxDepthList = new ArrayList<Integer>() {{ add(1); add(2); add(3); add(4); add(5); }};
      Collections.shuffle(maxDepthList);
      Integer[] maxDepthSpace = new Integer[maxDepthDim];
      for(int i=0; i<maxDepthDim; i++){ maxDepthSpace[i] = maxDepthList.get(i); }

      ArrayList<Float> learnRateList = new ArrayList<Float>() {{ add(0.1f); add(0.2f); add(0.3f); add(0.4f);
        add(0.5f); add(0.6f); add(0.7f); add(0.8f); add(0.9f); add(1.0f);}};
      Collections.shuffle(learnRateList);
      Float[] learnRateSpace = new Float[learnRateDim];
      for(int i=0; i<learnRateDim; i++){ learnRateSpace[i] = learnRateList.get(i); }

      hyperParms.put("_ntrees", ntreesSpace);
      hyperParms.put("_max_depth", maxDepthSpace);
      hyperParms.put("_learn_rate", learnRateSpace);

      // Fire off a grid search
      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = fr._key;
      params._response_column = "economy_20mpg";
      Grid.GridSearch gs = gbmg.startGridSearch(params, hyperParms);
      Grid g2 = (Grid) gs.get();
      assert g2 == gbmg;

      System.out.println("ntrees search space: " + Arrays.toString(ntreesSpace));
      System.out.println("max_depth search space: " + Arrays.toString(maxDepthSpace));
      System.out.println("learn_rate search space: " + Arrays.toString(learnRateSpace));

      // Check that cardinality of grid
      Model[] ms = gs.models();
      Integer numModels = ms.length;
      System.out.println("Grid consists of " + numModels + " models");
      assertTrue(numModels == ntreesDim * maxDepthDim * learnRateDim);

      // Pick a random model from the grid
      HashMap<String, Object[]> randomHyperParms = new HashMap<>();
      randomHyperParms.put("_distribution", new Family[]{Family.bernoulli});

      Integer ntreeVal = ntreesSpace[rng.nextInt(ntreesSpace.length)];
      randomHyperParms.put("_ntrees", new Integer[]{ntreeVal});

      Integer maxDepthVal = maxDepthSpace[rng.nextInt(maxDepthSpace.length)];
      randomHyperParms.put("_max_depth", maxDepthSpace);

      Float learnRateVal = learnRateSpace[rng.nextInt(learnRateSpace.length)];
      randomHyperParms.put("_learn_rate", learnRateSpace);

      //TODO: GBMModel gbmFromGrid = (GBMModel) g2.model(randomHyperParms).get();

      // Rebuild it with it's parameters
      params._distribution = Family.bernoulli;
      params._ntrees = ntreeVal;
      params._max_depth = maxDepthVal;
      params._learn_rate = learnRateVal;
      GBM job = null;
      try {
        job = new GBM(params);
        gbmRebuilt = job.trainModel().get();
      } finally {
        if (job != null) job.remove();
      }
      assertTrue(job._state == water.Job.JobState.DONE);

      // Make sure the AUC metrics match
      //double fromGridAUC = gbmFromGrid._output._scored_train[gbmFromGrid._output._ntrees]._AUC;
      double rebuiltAUC = gbmRebuilt._output._scored_train[gbmRebuilt._output._ntrees]._AUC;
      //System.out.println("The random grid model's AUC: " + fromGridAUC);
      System.out.println("The rebuilt model's AUC: " + rebuiltAUC);
      //assertEquals(fromGridAUC, rebuiltAUC);

    } finally {
      if (old != null) old.remove();
      if (fr != null) fr.remove();
      if (gbmg != null) gbmg.remove();
      if (gbmRebuilt != null) gbmRebuilt.remove();
    }
  }
}
