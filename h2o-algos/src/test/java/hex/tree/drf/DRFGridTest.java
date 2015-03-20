package hex.tree.drf;

import hex.Grid;
import hex.Model;
import java.util.Arrays;
import java.util.HashMap;
import org.junit.*;
import water.DKV;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;

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
      Grid.GridSearch gs = drfg.startGridSearch(hyperParms);
      Grid g2 = (Grid)gs.get();
      assert g2==drfg;

      // Print out the models from this grid search
      Model[] ms = gs.models();
      for( Model m : ms ) {
        DRFModel drf = (DRFModel)m;
        System.out.println(drf._output._mse_train[drf._output._ntrees] + " " + Arrays.toString(g2.getHypers(drf._parms)));
      }

    } finally {
      if( old != null ) old.remove();
      if( fr != null ) fr.remove();
      if( drfg != null ) drfg.remove();
    }
  }

}
