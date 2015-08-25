package water.currents;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.Keyed;
import water.TestUtil;
import water.fvec.Frame;

public class GroupByTest extends TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  @Test public void testBasic() {
    Frame fr = null;
    String tree = "(GB a.hex [1] [] mean 2 \"all\")"; // Group-By on col 1 (not 0), no order-by, mean of col 2
    try {
      fr = checkTree(tree);
      Assert.assertEquals( 2,fr.numCols());
      Assert.assertEquals(23,fr.numRows());

      Assert.assertEquals(2.0, fr.vec(0).at( 0), Math.ulp(1)); // Group 2.0, mean is 3.5
      Assert.assertEquals(3.5, fr.vec(1).at( 0), Math.ulp(1));
      Assert.assertEquals(2.2, fr.vec(0).at( 1), Math.ulp(1)); // Group 2.2, mean is 4.5
      Assert.assertEquals(4.5, fr.vec(1).at( 1), Math.ulp(1));

      Assert.assertEquals(2.8, fr.vec(0).at( 7), Math.ulp(1)); // Group 2.8, mean is 5.043, largest group
      Assert.assertEquals(5.042857142857143, fr.vec(1).at(7), Math.ulp(1));

      Assert.assertEquals(4.4, fr.vec(0).at(22), Math.ulp(1)); // Group 4.4, mean is 1.5, last group
      Assert.assertEquals(1.5, fr.vec(1).at(22), Math.ulp(1));

    } finally {
      if( fr != null ) fr.delete();
      Keyed.remove(Key.make("a.hex"));
    }
  }


  @Test public void testCatGroup() {
    Frame fr = null;
    String tree = "(GB a.hex [4] [] nrow 0 \"all\" mean 2 \"all\")"; // Group-By on col 4, no order-by, nrow and mean of col 2
    try {
      fr = checkTree(tree);
      Assert.assertEquals( 3,fr.numCols());
      Assert.assertEquals( 3,fr.numRows());
      String[] flowers = fr.vec(0).domain();

      Assert.assertEquals("Iris-setosa", flowers[(int)fr.vec(0).at8(0)]); // Group setosa, mean is 1.464
      Assert.assertEquals( 50  , fr.vec(1).at8(0));
      Assert.assertEquals(1.464, fr.vec(2).at (0), Math.ulp(1));
      Assert.assertEquals("Iris-versicolor", flowers[(int)fr.vec(0).at8(1)]); // Group versicolor, mean is 4.26
      Assert.assertEquals( 50  , fr.vec(1).at8(1));
      Assert.assertEquals(4.26 , fr.vec(2).at (1), Math.ulp(1));
      Assert.assertEquals("Iris-virginica", flowers[(int)fr.vec(0).at8(2)]); // Group virginica, mean is 5.552
      Assert.assertEquals( 50  , fr.vec(1).at8(2));
      Assert.assertEquals(5.552, fr.vec(2).at (2), Math.ulp(1));

    } finally {
      if( fr != null ) fr.delete();
      Keyed.remove(Key.make("a.hex"));
    }
  }

  private Frame checkTree(String tree) { return checkTree(tree,false); }
  private Frame checkTree(String tree, boolean expectThrow) {
    Frame fr = parse_test_file(Key.make("a.hex"),"smalldata/iris/iris_wheader.csv");
    try {
      Val val = Exec.exec(tree);
      Assert.assertFalse(expectThrow);
      System.out.println(val.toString());
      if( val instanceof ValFrame )
        return ((ValFrame)val)._fr;
      throw new IllegalArgumentException("exepcted a frame return");
    } catch( IllegalArgumentException iae ) {
      if( !expectThrow ) throw iae; // If not expecting a throw, then throw which fails the junit
      fr.delete();                  // If expecting, then cleanup
      return null;
    }
  }
}
