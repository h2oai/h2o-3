package water;

import hex.ConfusionMatrix;
import static hex.ConfusionMatrix.buildCM;
import static hex.Model.adaptTestForTrain;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.fvec.Frame;
import water.fvec.Vec;
import static water.util.FrameUtils.parseFrame;

import java.io.IOException;
import java.util.Arrays;

public class ConfusionMatrixTest extends TestUtil {
  @BeforeClass
  public static void stall() { stall_till_cloudsize(1); }

  final boolean debug = false;

  @Test
  public void testIdenticalVectors() {
    simpleCMTest(
            "smalldata/junit/cm/v1.csv",
            "smalldata/junit/cm/v1.csv",
            ar("A", "B", "C"),
            ar("A", "B", "C"),
            ar("A", "B", "C"),
            ar( ar(2L, 0L, 0L),
                    ar(0L, 2L, 0L),
                    ar(0L, 0L, 1L)),
            debug, false);
  }

  @Test
  public void testVectorAlignment() {
    simpleCMTest(
            "smalldata/junit/cm/v1.csv",
            "smalldata/junit/cm/v2.csv",
            ar("A", "B", "C"),
            ar("A", "B", "C"),
            ar("A", "B", "C"),
            ar( ar(1L, 1L, 0L),
                    ar(0L, 1L, 1L),
                    ar(0L, 0L, 1L)
            ),
            debug, false);
  }

  /** Negative test testing expected exception if two vectors
   * of different lengths are provided.
   */
  @Test(expected = IllegalArgumentException.class)
  public void testDifferentLengthVectors() {
    simpleCMTest(
            "smalldata/junit/cm/v1.csv",
            "smalldata/junit/cm/v3.csv",
            ar("A", "B", "C"),
            ar("A", "B", "C"),
            ar("A", "B", "C"),
            ar( ar(1L, 1L, 0L),
                    ar(0L, 1L, 1L),
                    ar(0L, 0L, 1L)
            ),
            debug, false);
  }

  @Test
  public void testDifferentDomains() {

    simpleCMTest(
            "smalldata/junit/cm/v1.csv",
            "smalldata/junit/cm/v4.csv",
            ar("A", "B", "C"),
            ar("B", "C"),
            ar("A", "B", "C"),
            ar( ar(0L, 2L, 0L),
                    ar(0L, 0L, 2L),
                    ar(0L, 0L, 1L)
            ),
            debug, false);

    simpleCMTest(
            "smalldata/junit/cm/v2.csv",
            "smalldata/junit/cm/v4.csv",
            ar("A", "B", "C"),
            ar("B", "C"),
            ar("A", "B", "C"),
            ar( ar(0L, 1L, 0L),
                    ar(0L, 1L, 1L),
                    ar(0L, 0L, 2L)
            ),
            debug, false);
  }

  @Test
  public void testSimpleNumericVectors() {
    simpleCMTest(
            "smalldata/junit/cm/v1n.csv",
            "smalldata/junit/cm/v1n.csv",
            ar("0", "1", "2"),
            ar("0", "1", "2"),
            ar("0", "1", "2"),
            ar( ar(2L, 0L, 0L),
                    ar(0L, 2L, 0L),
                    ar(0L, 0L, 1L)
            ),
            debug, true);

    simpleCMTest(
            "smalldata/junit/cm/v1n.csv",
            "smalldata/junit/cm/v2n.csv",
            ar("0", "1", "2"),
            ar("0", "1", "2"),
            ar("0", "1", "2"),
            ar( ar(1L, 1L, 0L),
                    ar(0L, 1L, 1L),
                    ar(0L, 0L, 1L)
            ),
            debug, true);
  }

  @Test
  public void testDifferentDomainsNumericVectors() {

    simpleCMTest(
            "smalldata/junit/cm/v1n.csv",
            "smalldata/junit/cm/v4n.csv",
            ar("0", "1", "2"),
            ar("1", "2"),
            ar("0", "1", "2"),
            ar( ar(0L, 2L, 0L),
                    ar(0L, 0L, 2L),
                    ar(0L, 0L, 1L)
            ),
            debug, true);

    simpleCMTest(
            "smalldata/junit/cm/v2n.csv",
            "smalldata/junit/cm/v4n.csv",
            ar("0", "1", "2"),
            ar("1", "2"),
            ar("0", "1", "2"),
            ar( ar(0L, 1L, 0L),
                    ar(0L, 1L, 1L),
                    ar(0L, 0L, 2L)
            ),
            debug, true);

  }

  /** Test for PUB-216:
   * The case when vector domain is set to a value (0~A, 1~B, 2~C), but actual values stored in
   * vector references only a subset of domain (1~B, 2~C). The TransfVec was using minimum from
   * vector (i.e., value 1) to compute transformation but minimum was wrong since it should be 0. */
  @Test public void testBadModelPrect() {

    simpleCMTest(
            frame("v1", vec(ar("A","B","C"), ari(0,0,1,1,2) )),
            frame("v1", vec(ar("A","B","C"), ari(1,1,2,2,2) )),
            ar("A","B","C"),
            ar("A","B","C"),
            ar("A","B","C"),
            ar( ar(0L, 2L, 0L),
                    ar(0L, 0L, 2L),
                    ar(0L, 0L, 1L)
            ),
            debug, false);

  }

  @Test public void testBadModelPrect2() {
    simpleCMTest(
            frame("v1", vec(ar("-1", "0", "1"), ari(0, 0, 1, 1, 2))),
            frame("v1", vec(ar("0", "1"), ari(0, 0, 1, 1, 1))),
            ar("-1", "0", "1"),
            ar("0", "1"),
            ar("-1", "0", "1"),
            ar(ar(0L, 2L, 0L),
                    ar(0L, 0L, 2L),
                    ar(0L, 0L, 1L)
            ),
            debug, false);

  }

  private void simpleCMTest(String f1, String f2, String[] expectedActualDomain, String[] expectedPredictDomain, String[] expectedDomain, long[][] expectedCM, boolean debug, boolean toEnum) {
    try {
      Frame v1 = parseFrame(Key.make("v1.hex"), find_test_file(f1));
      Frame v2 = parseFrame(Key.make("v2.hex"), find_test_file(f2));
      simpleCMTest(v1, v2, expectedActualDomain, expectedPredictDomain, expectedDomain, expectedCM, debug, toEnum);
      // now v1/v2 might be changed (enums or train/test adaptation), so the simpleCMTest must do the cleanup as well
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /** Delete v1, v2 after potential modifying operations during processing: enums and/or train/test adaptation. */
  private void simpleCMTest(Frame v1, Frame v2, String[] actualDomain, String[] predictedDomain, String[] expectedDomain, long[][] expectedCM, boolean debug, boolean toEnum) {
    Scope.enter();
    try {
      ConfusionMatrix cm = buildCM(v1.vecs()[0].toEnum(), v2.vecs()[0].toEnum());

      // -- DEBUG --
      if (debug) {
        System.err.println("actual            : " + Arrays.toString(actualDomain));
        System.err.println("predicted         : " + Arrays.toString(predictedDomain));
        System.err.println("CM domain         : " + Arrays.toString(cm._domain));
        System.err.println("expected CM domain: " + Arrays.toString(expectedDomain) + "\n");
        for (int i=0; i<cm._arr.length; i++)
          System.err.println(Arrays.toString(cm._arr[i]));
        System.err.println("");
        System.err.println(cm.toASCII());
      }
      // -- -- --
      assertCMEqual(expectedDomain, expectedCM, cm);
    } finally {
      if (v1 != null) v1.delete();
      if (v2 != null) v2.delete();
      Scope.exit();
    }
  }

  private void assertCMEqual(String[] expectedDomain, long[][] expectedCM, ConfusionMatrix actualCM) {
    Assert.assertArrayEquals("Expected domain differs",     expectedDomain,        actualCM._domain);
    long[][] acm = actualCM._arr;
    Assert.assertEquals("CM dimension differs", expectedCM.length, acm.length);
    for (int i=0; i < acm.length; i++) Assert.assertArrayEquals("CM row " +i+" differs!", expectedCM[i], acm[i]);
  }

}
