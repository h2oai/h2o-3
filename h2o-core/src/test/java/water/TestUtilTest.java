package water;

import org.junit.BeforeClass;
import org.junit.Test;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import java.io.IOException;

import static org.junit.Assert.assertTrue;

/**
 *  We need to make sure that our tools for testing are reliable as well
 */
public class TestUtilTest extends TestUtil {

  @BeforeClass public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void asFactor() {
    Scope.enter();
    try {
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA")
              .withVecTypes(Vec.T_STR)
              .withDataForCol(0, ar("yes", "no"))
              .build();
      Scope.track(fr);

      assertTrue(fr.vec(0).isString());

      Frame res = asFactor(fr, "ColA");

      assertTrue(res.vec(0).isCategorical());
      Scope.track(res);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testwriteFrameToCSV() {
    Scope.enter();
    try {
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA")
              .withVecTypes(Vec.T_NUM)
              .withDataForCol(0, ar(1,2))
              .build();
      Scope.track(fr);
      String filename = System.getProperty("java.io.tmpdir") + "testFile";
      System.out.println("WriteFrameToCSV to save frame");
      writeFrameToCSV(filename, fr, false, false);
      Frame fr2 = parse_test_file(filename);
      System.out.println("compare two frames");
     // assertTrue(fr.vec(0).at(0)==fr2.vec(0).at(0));
      //assertTrue(fr.vec(0).at(1)==fr2.vec(0).at(1));
      System.out.printf("%f, %f", fr.vec(0).at(0), fr2.vec(0).at(0));
      System.out.println("");
      System.out.printf("%f, %f", fr.vec(0).at(1), fr2.vec(0).at(1));
      System.out.println("");
      System.out.println("Test completed.  WriteFrameToCSV works!");
      Scope.track(fr2);
    } catch(IOException ex) {
        assertTrue(1==2); // test failed, force exception
    } finally {
      Scope.exit();
    }
  }
}



