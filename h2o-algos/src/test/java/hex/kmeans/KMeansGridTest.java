package hex.kmeans;

import static org.junit.Assert.assertArrayEquals;
import org.junit.BeforeClass;
import org.junit.Test;

//import java.io.File;
//import java.util.Arrays;
//import java.util.Comparator;
//import org.junit.*;
//import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.H2O.H2OFuture;
//import water.fvec.NFSFileVec;
//import water.parser.ParseDataset;
import hex.Grid;


public class KMeansGridTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test public void testIrisGrid() {
    KMeansGrid kmg = null;
    Frame fr = null;
    try {
      fr = parse_test_file("smalldata/iris/iris_wheader.csv");

      // Get the Grid for this modeling class and frame
      kmg = KMeansGrid.get(fr);

      // 4-dimensioanl hyperparameter search
      assert kmg.nHyperParms()==4;

      // Search over this range of K's
      assert kmg.hyperName(0).equals("k");
      double[] ks = new double[]{0,1,2,3,4,5,6}; // Note that k==0 is illegal, and k==1 is trivial

      // Search over this range of the standardize flag
      assert kmg.hyperName(1).equals("standardize");
      double[] stds = null;     // null means: take the default, no search

      // Search over this range of the init enum
      assert kmg.hyperName(2).equals("init");
      double[] inits = new double[]{KMeans.Initialization.Random.ordinal(),1,2}; // Random, PlusPlus,Furthest, not User

      // Search over this range of the init enum
      assert kmg.hyperName(3).equals("seed");
      double[] seeds = new double[]{0,1,123456789,987654321};

      // Fire off a grid search
      H2OFuture<Grid> fg = kmg.startGridSearch(new double[][]{ks,stds,inits,seeds});
      
      Grid g2 = fg.getResult();
      assert g2==kmg;

    } finally {
      if( fr  != null ) fr .remove();
      if( kmg != null ) kmg.remove();
    }
  }

}
