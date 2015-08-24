package water.currents;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.H2O;
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
      throw H2O.unimpl();       // Need to do some asserts here!
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
