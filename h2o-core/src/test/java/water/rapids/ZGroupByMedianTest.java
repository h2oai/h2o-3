package water.rapids;

import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.Keyed;
import water.TestUtil;
import water.fvec.Frame;
import water.rapids.vals.ValFrame;

import static org.junit.Assert.assertTrue;

public class ZGroupByMedianTest extends TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(5); }

  // Note that if this median test runs before the testSplitCats and/or testGroupbyTableSpeed,
  // we will encounter the leaked key errors. This has been captured in JIRA PUBDEV-PUBDEV-5090.
  @Test
  public void testGroupbyMedian() {
    Frame fr = null;
    String tree = "(GB hex [0] median 1 \"all\")"; // Group-By on col 0 median of col 1
    double[] correct_median = {0.49851096435701053, 0.50183187047352851, 0.50187234362560651, 0.50528965387515079,
            0.49887302541203787};  // order may not be correct
    try {
      //  fr = chkTree(tree, "smalldata/jira/pubdev_4727_median.csv");
      fr = chkTree(tree, "smalldata/jira/pubdev_4727_junit_data.csv");
      for (int index=0; index < fr.numRows(); index++) {  // compare with correct medians
        assertTrue(Math.abs(correct_median[(int)fr.vec(0).at(index)]-fr.vec(1).at(index))<1e-12);
      }
    } finally {
      if( fr != null ) fr.delete();
      Keyed.remove(Key.make("hex"));
    }
  }

  private Frame chkTree(String tree, String fname) { return chkTree(tree,fname,false); }
  private Frame chkTree(String tree, String fname, boolean expectThrow) {
    Frame fr = parseTestFile(Key.make("hex"),fname);
    try {
      Val val = Rapids.exec(tree);
      System.out.println(val.toString());
      if( val instanceof ValFrame )
        return val.getFrame();
      throw new IllegalArgumentException("expected a frame return");
    } catch( IllegalArgumentException iae ) {
      if( !expectThrow ) throw iae; // If not expecting a throw, then throw which fails the junit
      fr.delete();                  // If expecting, then cleanup
      return null;
    }
  }
}

