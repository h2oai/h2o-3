package hex.tree.gbm;

import hex.Grid;
import hex.Model;
import hex.tree.gbm.GBMModel.GBMParameters.Family;
import java.util.Arrays;
import java.util.HashMap;
import org.junit.*;
import water.DKV;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;

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
        System.out.println(gbm._output._mse_train[gbm._output._ntrees] + " " + Arrays.toString(g2.getHypers(gbm._parms)));
      }

    } finally {
      if( old != null ) old.remove();
      if( fr != null ) fr.remove();
      if( gbmg != null ) gbmg.remove();
    }
  }

}
