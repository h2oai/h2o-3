package hex.kmeans;

import java.util.Arrays;
import java.util.HashMap;
import hex.Grid;
import hex.Model;
import org.junit.*;
import water.TestUtil;
import water.fvec.Frame;


public class KMeansGridTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test public void testIrisGrid() {
    KMeansGrid kmg = null;
    Frame fr = null;
    try {
      fr = parse_test_file("smalldata/iris/iris_wheader.csv");

      // Get the Grid for this modeling class and frame
      kmg = KMeansGrid.get(fr);

      // 4-dimensional hyperparameter search
      HashMap<String,Object[]> hyperParms = new HashMap<>();

      // Search over this range of K's
      hyperParms.put("_k",new Integer[]{1,2,3,4,5,6}); // Note that k==0 is illegal, and k==1 is trivial

      // Search over this range of the init enum
      hyperParms.put("_init",new KMeans.Initialization[] {
          KMeans.Initialization.Random,
          KMeans.Initialization.PlusPlus,
          KMeans.Initialization.Furthest});

      // Search over this range of the init enum
      hyperParms.put("_seed",new Long[]{0L,1L,123456789L,987654321L});

      // Fire off a grid search
      Grid.GridSearch gs = kmg.startGridSearch(hyperParms);
      Grid g2 = (Grid)gs.get();
      assert g2==kmg;

      // Print out the models from this grid search
      Model[] ms = gs.models();
      for( Model m : ms ) {
        KMeansModel kmm = (KMeansModel)m;
        System.out.println(kmm._output._tot_withinss + " " + Arrays.toString(g2.getHypers(kmm._parms)));
      }

    } finally {
      if( fr  != null ) fr .remove();
      if( kmg != null ) kmg.remove();
    }
  }

}
