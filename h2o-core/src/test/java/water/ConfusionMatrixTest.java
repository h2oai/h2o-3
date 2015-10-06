package water;

import hex.ConfusionMatrix;
import static hex.ConfusionMatrix.buildCM;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.fvec.Frame;
import water.util.ArrayUtils;

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
            ard( ard(2, 0, 0),
                    ard(0, 2, 0),
                    ard(0, 0, 1)),
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
            ard( ard(1, 1, 0),
                    ard(0, 1, 1),
                    ard(0, 0, 1)
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
            ard( ard(1, 1, 0),
                    ard(0, 1, 1),
                    ard(0, 0, 1)
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
            ard( ard(0, 2, 0),
                    ard(0, 0, 2),
                    ard(0, 0, 1)
            ),
            debug, false);

    simpleCMTest(
            "smalldata/junit/cm/v2.csv",
            "smalldata/junit/cm/v4.csv",
            ar("A", "B", "C"),
            ar("B", "C"),
            ar("A", "B", "C"),
            ard( ard(0, 1, 0),
                    ard(0, 1, 1),
                    ard(0, 0, 2)
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
            ard( ard(2, 0, 0),
                    ard(0, 2, 0),
                    ard(0, 0, 1)
            ),
            debug, true);

    simpleCMTest(
            "smalldata/junit/cm/v1n.csv",
            "smalldata/junit/cm/v2n.csv",
            ar("0", "1", "2"),
            ar("0", "1", "2"),
            ar("0", "1", "2"),
            ard( ard(1, 1, 0),
                    ard(0, 1, 1),
                    ard(0, 0, 1)
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
            ard( ard(0, 2, 0),
                    ard(0, 0, 2),
                    ard(0, 0, 1)
            ),
            debug, true);

    simpleCMTest(
            "smalldata/junit/cm/v2n.csv",
            "smalldata/junit/cm/v4n.csv",
            ar("0", "1", "2"),
            ar("1", "2"),
            ar("0", "1", "2"),
            ard( ard(0, 1, 0),
                    ard(0, 1, 1),
                    ard(0, 0, 2)
            ),
            debug, true);

  }

  /** Test for PUB-216:
   * The case when vector domain is set to a value (0~A, 1~B, 2~C), but actual values stored in
   * vector references only a subset of domain (1~B, 2~C). The TransfVec was using minimum from
   * vector (i.e., value 1) to compute transformation but minimum was wrong since it should be 0. */
  @Test public void testBadModelPrect() {

    simpleCMTest(
            ArrayUtils.frame("v1", vec(ar("A", "B", "C"), ari(0, 0, 1, 1, 2))),
            ArrayUtils.frame("v1", vec(ar("A", "B", "C"), ari(1, 1, 2, 2, 2))),
            ar("A","B","C"),
            ar("A","B","C"),
            ar("A","B","C"),
            ard( ard(0, 2, 0),
                    ard(0, 0, 2),
                    ard(0, 0, 1)
            ),
            debug, false);

  }

  @Test public void testBadModelPrect2() {
    simpleCMTest(
            ArrayUtils.frame("v1", vec(ar("-1", "0", "1"), ari(0, 0, 1, 1, 2))),
            ArrayUtils.frame("v1", vec(ar("0", "1"), ari(0, 0, 1, 1, 1))),
            ar("-1", "0", "1"),
            ar("0", "1"),
            ar("-1", "0", "1"),
            ard(ard(0, 2, 0),
                    ard(0, 0, 2),
                    ard(0, 0, 1)
            ),
            debug, false);

  }

  private void simpleCMTest(String f1, String f2, String[] expectedActualDomain, String[] expectedPredictDomain, String[] expectedDomain, double[][] expectedCM, boolean debug, boolean toCategorical) {
    try {
      Frame v1 = parseFrame(Key.make("v1.hex"), find_test_file(f1));
      Frame v2 = parseFrame(Key.make("v2.hex"), find_test_file(f2));
      v2 = v1.makeCompatible(v2);
      simpleCMTest(v1, v2, expectedActualDomain, expectedPredictDomain, expectedDomain, expectedCM, debug, toCategorical);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /** Delete v1, v2 after potential modifying operations during processing: categoricals and/or train/test adaptation. */
  private void simpleCMTest(Frame v1, Frame v2, String[] actualDomain, String[] predictedDomain, String[] expectedDomain, double[][] expectedCM, boolean debug, boolean toCategorical) {
    Scope.enter();
    try {
      ConfusionMatrix cm = buildCM(v1.vecs()[0].toCategoricalVec(), v2.vecs()[0].toCategoricalVec());

      // -- DEBUG --
      if (debug) {
        System.err.println("actual            : " + Arrays.toString(actualDomain));
        System.err.println("predicted         : " + Arrays.toString(predictedDomain));
        System.err.println("CM domain         : " + Arrays.toString(cm._domain));
        System.err.println("expected CM domain: " + Arrays.toString(expectedDomain) + "\n");
        for (int i=0; i<cm._cm.length; i++)
          System.err.println(Arrays.toString(cm._cm[i]));
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

  private void assertCMEqual(String[] expectedDomain, double[][] expectedCM, ConfusionMatrix actualCM) {
    Assert.assertArrayEquals("Expected domain differs",     expectedDomain,        actualCM._domain);
    double[][] acm = actualCM._cm;
    Assert.assertEquals("CM dimension differs", expectedCM.length, acm.length);
    for (int i=0; i < acm.length; i++) Assert.assertArrayEquals("CM row " +i+" differs!", expectedCM[i], acm[i],1e-10);
  }

}
