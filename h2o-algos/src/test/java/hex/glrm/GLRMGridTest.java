package hex.glrm;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import hex.DataInfo;
import hex.Model;
import hex.grid.Grid;
import hex.grid.GridSearch;
import water.Job;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.util.ArrayUtils;

public class GLRMGridTest extends TestUtil {

  @BeforeClass()
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void testMultipleGridInvocation() {
    Grid<GLRMModel.GLRMParameters> grid = null;
    Frame fr = null;
    try {
      fr = parse_test_file("smalldata/iris/iris_wheader.csv");

      // Hyper-space
      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_k", new Integer[] {2, 4});
        // Search over this range of the init enum
        put("_transform", new DataInfo.TransformType[] {
            DataInfo.TransformType.NONE,
            DataInfo.TransformType.DEMEAN
        });
      }};

      // Name of used hyper parameters
      String[] hyperParamNames = hyperParms.keySet().toArray(new String[hyperParms.size()]);
      Arrays.sort(hyperParamNames);
      int hyperSpaceSize = ArrayUtils.crossProductSize(hyperParms);

      // Create default parameters
      GLRMModel.GLRMParameters params = new GLRMModel.GLRMParameters();
      params._train = fr._key;
      params._seed = 4224L;
      params._loss = GlrmLoss.Absolute;
      params._init = GLRM.Initialization.SVD;

      //
      // Fire off a grid search multiple times with same key and make sure
      // that results are same
      //
      final int ITER_CNT = 2;
      Key<Model>[][] modelKeys = new Key[ITER_CNT][];
      Key<Grid> gridKey = Key.make("GLRM_grid_iris" + Key.rand());
      for (int i = 0; i < ITER_CNT; i++) {
        Job<Grid> gs = GridSearch.startGridSearch(gridKey, params, hyperParms);
        grid = (Grid<GLRMModel.GLRMParameters>) gs.get();
        modelKeys[i] = grid.getModelKeys();
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
      }
      Assert.assertArrayEquals("The model keys should be same between two iterations!",
                               modelKeys[0], modelKeys[1]);
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
  public void testGridAppend() {
    Grid<GLRMModel.GLRMParameters> grid = null;
    Frame fr = null;
    try {
      fr = parse_test_file("smalldata/iris/iris_wheader.csv");

      // Hyper-space
      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_k", new Integer[] {2, 4});
        // Search over this range of the init enum
        put("_transform", new DataInfo.TransformType[] {
            DataInfo.TransformType.NONE,
            DataInfo.TransformType.DEMEAN
        });
      }};

      // Name of used hyper parameters
      final String[] hyperParamNames1 = hyperParms.keySet().toArray(new String[hyperParms.size()]);
      Arrays.sort(hyperParamNames1);
      final int hyperSpaceSize1 = ArrayUtils.crossProductSize(hyperParms);

      // Create default parameters
      GLRMModel.GLRMParameters params = new GLRMModel.GLRMParameters();
      params._train = fr._key;
      params._seed = 4224L;
      params._loss = GlrmLoss.Absolute;
      params._init = GLRM.Initialization.SVD;

      //
      // Fire off a grid two  times with same key and make sure
      // that final grid contains all models from both runs.
      //
      Key<Grid> gridKey = Key.make("GLRM_grid_iris" + Key.rand());

      // 1st iteration
      final Job<Grid> gs1 = GridSearch.startGridSearch(gridKey, params, hyperParms);
      grid = (Grid<GLRMModel.GLRMParameters>) gs1.get();
      // Make sure number of produced models match size of specified hyper space
      Assert.assertEquals("Size of grid should match to size of hyper space", hyperSpaceSize1,
                          grid.getModelCount() + grid.getFailureCount());
      // Make sure that names of used parameters match
      String[] gridHyperNames1 = grid.getHyperNames();
      Arrays.sort(gridHyperNames1);
      Assert.assertArrayEquals("Hyper parameters names should match!", hyperParamNames1,
                               gridHyperNames1);

      // 2nd iteration
      hyperParms.put("_k", new Integer[] { 3 });
      final String[] hyperParamNames2 = hyperParms.keySet().toArray(new String[hyperParms.size()]);
      Arrays.sort(hyperParamNames2);
      final int hyperSpaceSize2 = ArrayUtils.crossProductSize(hyperParms);
      Assert.assertArrayEquals("Names of hyperspaces should be same!", hyperParamNames1, hyperParamNames2);
      final Job<Grid> gs2 = GridSearch.startGridSearch(gridKey, params, hyperParms);
      grid = (Grid<GLRMModel.GLRMParameters>) gs2.get();
      // Make sure number of produced models match size of specified hyper space
      Assert.assertEquals("Size of grid should match to size of hyper space",
                          hyperSpaceSize1 + hyperSpaceSize2,
                          grid.getModelCount() + grid.getFailureCount());
      // Make sure that names of used parameters match
      String[] gridHyperNames2 = grid.getHyperNames();
      Arrays.sort(gridHyperNames2);
      Assert.assertArrayEquals("Hyper parameters names should match!", hyperParamNames2,
                               gridHyperNames2);

      // Verify PUBDEV-2633 - unique names of models
      Set<String> modelNames = new HashSet<>(grid.getModelCount());
      for (Key<Model> modelKey : grid.getModelKeys()) {
        modelNames.add(modelKey.toString());
      }
      Assert.assertEquals("Model names should be unique!",
                          grid.getModelCount(),
                          modelNames.size());
    } finally {
      if (fr != null) {
        fr.remove();
      }
      if (grid != null) {
        grid.remove();
      }
    }
  }

}
