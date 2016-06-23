package water.rapids;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.Keyed;
import water.TestUtil;
import water.fvec.Frame;

public class TableTest extends TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  @Test public void testBasic() {
    Frame fr = null;
    String tree = "(table (cols_py hex [\"AGE\" \"RACE\"]) FALSE)";
    try {
      fr = chkTree(tree,"smalldata/prostate/prostate.csv");


    } finally {
      if( fr != null ) fr.delete();
      Keyed.remove(Key.make("hex"));
    }
  }
  

  @Test public void testBasicDdply() {
    Frame fr = null;
    String tree = "(table (cols_py hex [\"VOL\"]) FALSE)";
    try {
      fr = chkTree(tree,"smalldata/prostate/prostate.csv");


    } finally {
      if( fr != null ) fr.delete();
      Keyed.remove(Key.make("hex"));
    }
  }


  private void chkDim( Frame fr, int col, int row ) {
    Assert.assertEquals(col,fr.numCols());
    Assert.assertEquals(row,fr.numRows());
  }
  private void chkFr( Frame fr, int col, int row, double exp ) { chkFr(fr,col,row,exp,Math.ulp(1)); }
  private void chkFr( Frame fr, int col, int row, double exp, double tol ) {
    if( Double.isNaN(exp) ) Assert.assertTrue(fr.vec(col).isNA(row));
    else                    Assert.assertEquals(exp, fr.vec(col).at(row),tol);
  }
  private void chkFr( Frame fr, int col, int row, String exp ) {
    String[] dom = fr.vec(col).domain();
    Assert.assertEquals(exp, dom[(int)fr.vec(col).at8(row)]);
  }

  private Frame chkTree(String tree, String fname) { return chkTree(tree,fname,false); }
  private Frame chkTree(String tree, String fname, boolean expectThrow) {
    Frame fr = parse_test_file(Key.make("hex"),fname);
    try {
      Val val = Rapids.exec(tree);
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
