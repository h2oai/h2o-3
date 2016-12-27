package water.rapids.ast.prims.reducers;

import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.rapids.Rapids;
import water.rapids.Val;

import static org.junit.Assert.assertEquals;


/**
 * Test the AstNaCnt.java class
 */
public class AstNaRowCntTest extends TestUtil {

  @BeforeClass
  static public void setup() { stall_till_cloudsize(1); }


  //--------------------------------------------------------------------------------------------------------------------
  // Tests
  //--------------------------------------------------------------------------------------------------------------------

  @Test public void testAstRowNaNumEnum() {
    Frame f = parse_test_file(Key.make("prostate.hex"), "smalldata/prostate/prostate_cat.csv");
    try {
      // insert NAs in 0/2/3/4 columns which are enums
      f.vec(0).setNA(0);  // set 0th column 0th row to NA
      f.vec(2).setNA(10); // set 2th column 10th row to NA
      f.vec(3).setNA(10);   // set 3th column 10th row to NA.  Did not increase NA row count
      f.vec(4).setNA(100);  // set 4th column 100th row to NA.

      // insert NAs in 1/5/6/7 which are numerical columns
      f.vec(1).setNA(200);
      f.vec(5).setNA(20);
      f.vec(6).setNA(100);  // should not increase NA row count here
      f.vec(7).setNA(180);
      String x = String.format("(naRowCnt %s)", f._key);
      Val res = Rapids.exec(x);         // make the call to count number of NAs rows  in frame
      assertEquals((int) res.getNum(), 6);
    } finally {
      if (f != null) f.delete();
    }
  }

  @Test public void testAstRowNaCntString() {
    Frame f = parse_test_file(Key.make("prostate.hex"), "smalldata/iris/virginica.csv");
    try {
      // insert NAs in 4th column which is string
      f.vec(4).setNA(10);  // set 4th column 100th row to NA.
      f.vec(4).setNA(20);  // set 4th column 100th row to NA.

      // insert NAs in 0/1/2/3 which are numerical columns
      f.vec(0).setNA(20); // should not increase NA row count here
      f.vec(1).setNA(8);
      f.vec(2).setNA(7);
      f.vec(3).setNA(34);
      String x = String.format("(naRowCnt %s)", f._key);
      Val res = Rapids.exec(x);         // make the call to count number of NAs rows  in frame
      assertEquals((int) res.getNum(), 5);
    } finally {
      if (f != null) f.delete();
    }
  }

  @Test public void testAstRowNaCntUUIDs() {
    Frame f = parse_test_file(Key.make("prostate.hex"), "smalldata/jira/test_uuid_na.csv");
    try {
      String x = String.format("(naRowCnt %s)", f._key);
      Val res = Rapids.exec(x);         // frame contains 1 NA
      assertEquals((int) res.getNum(), 1);
    } finally {
      if (f != null) f.delete();
    }
  }

  @Test public void testAstRowNaCntDateTime() {
    Frame f = parse_test_file(Key.make("prostate.hex"), "smalldata/parser/orc/orc2csv/TestOrcFile.testDate1900.csv");
    try {
      // insert NAs into 0th column which is time stamp
      f.vec(0).setNA(1);
      f.vec(0).setNA(18);
      f.vec(0).setNA(28);
      // insert NAs to 1st column which is date
      f.vec(1).setNA(1);
      f.vec(1).setNA(20);
      f.vec(1).setNA(39);
      String x = String.format("(naRowCnt %s)", f._key);
      Val res = Rapids.exec(x);         // frame contains 5 NA
      assertEquals((int) res.getNum(), 5);
    } finally {
      if (f != null) f.delete();
    }
  }
}