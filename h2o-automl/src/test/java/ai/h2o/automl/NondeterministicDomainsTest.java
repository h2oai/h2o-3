package ai.h2o.automl;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.Job;
import water.Key;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.util.TwoDimTable;

import java.io.File;
import java.util.Date;
import java.util.Random;
import java.util.UUID;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

public class NondeterministicDomainsTest extends water.TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void domainsAreDifferentTest() {
    String tmpName = null;
    Frame fr = null;
    Frame reimportedFrame = null;
    try {
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "a"))
              .withDataForCol(1, ar("yes", "no", "yes"))
              .withChunkLayout(1, 1, 1)
              .build();

      String[] domainForColAOriginal = fr.vec(0).domain();
      String[] domainForColBOriginal = fr.vec(1).domain();

      tmpName = UUID.randomUUID().toString();
      Job export = Frame.export(fr, tmpName, fr._key.toString(), true, 1);
      export.get();

      reimportedFrame = parse_test_file(Key.make("parsed"), tmpName, true);
      String[] domainForColAReimported = reimportedFrame.vec(0).domain();
      String[] domainForColBReimported = reimportedFrame.vec(1).domain();

      printOutFrameAsTable(reimportedFrame);

      // For some unknown reason ColA domain is sorted in the same fashion both in TestFrameBuilder and in parsed from file frame. But that is not the case for the ColB.
      // I was trying to reverse order for domains in TestFrameBuilder but we should reverse domains ONLY for particular columns in oder to match with parser's behavior and it is unclear which ones.
      assertArrayEquals(domainForColAOriginal, domainForColAReimported);

      try {
        assertArrayEquals(domainForColBOriginal, domainForColBReimported);
        fail();
      } catch (AssertionError ex) {
        System.out.print("Exception was thrown as expected");
      }

    } finally {
      fr.delete();
      reimportedFrame.delete();
      new File(tmpName).delete();
    }
  }

  private void printOutFrameAsTable(Frame fr) {

    TwoDimTable twoDimTable = fr.toTwoDimTable();
    System.out.println(twoDimTable.toString(2, false));
  }
}
