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
            "smalldata/junit/cm/v4.csv",
            "smalldata/junit/cm/v1.csv",
            ar("B", "C"),
            ar("A", "B", "C"),
            ar("B", "C", "A"),
            ar( ar(0L, 0L, 2L),
                    ar(2L, 1L, 0L),
                    ar(0L, 0L, 0L)
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

    simpleCMTest(
            "smalldata/junit/cm/v4n.csv",
            "smalldata/junit/cm/v1n.csv",
            ar("1", "2"),
            ar("0", "1", "2"),
            ar("1", "2", "0"),
            ar( ar(0L, 0L, 2L),
                    ar(2L, 1L, 0L),
                    ar(0L, 0L, 0L)
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

    simpleCMTest(
            frame("v1", vec(ar("B","C"), ari(0,0,1,1) )), // B B C C actual
            frame("v1", vec(ar("A","B"), ari(1,1,0,0) )), // B B A A predicted
            ar("B","C"),
            ar("A","B"),
            ar("B","C","A"),
            ar( ar(2L, 0L, 0L), // B
                    ar(0L, 0L, 2L), // C
                    ar(0L, 0L, 0L)  // A
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

    simpleCMTest(
            frame("v1", vec(ar("-1", "0"),ari(0,0,1,1) )),
            frame("v1", vec(ar("0", "1"), ari(1,1,0,0) )),
            ar("-1","0"),
            ar("0","1"),
            ar("-1","0","1"),
            ar( ar(0L, 0L, 2L),
                    ar(0L, 2L, 0L),
                    ar(0L, 0L, 0L)
            ),
            debug, false);

    // The case found by Nidhi on modified covtype dataset
    simpleCMTest(
            frame("v1", vec(ar(      "1","2","3","4","5","6","7"), ari( 0, 1, 2, 3, 4, 5, 6) )),
            frame("v1", vec(ar("-1", "1","2","3","4","5","6","7"), ari( 2, 3, 4, 5, 6, 7, 1) )),
            ar(      "1","2","3","4","5","6","7"),
            ar("-1", "1","2","3","4","5","6"),
            ar("1","2","3","4","5","6","7","-1"),
            ar( ar( 0L, 1L, 0L, 0L, 0L, 0L, 0L, 0L), // "1"
                    ar( 0L, 0L, 1L, 0L, 0L, 0L, 0L, 0L), // "2"
                    ar( 0L, 0L, 0L, 1L, 0L, 0L, 0L, 0L), // "3"
                    ar( 0L, 0L, 0L, 0L, 1L, 0L, 0L, 0L), // "4"
                    ar( 0L, 0L, 0L, 0L, 0L, 1L, 0L, 0L), // "5"
                    ar( 0L, 0L, 0L, 0L, 0L, 0L, 1L, 0L), // "6"
                    ar( 1L, 0L, 0L, 0L, 0L, 0L, 0L, 0L), // "7"
                    ar( 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L)  // "-1"
            ),
            debug, false);

    // Another case
    simpleCMTest(
            frame("v1", vec(ar("7","8", "9","10","11"),ari( 0, 1, 2, 3, 4) )),
            frame("v1", vec(ar("7","8","10","11","13"),ari( 0, 1, 4, 2, 3) )),
            ar("7","8", "9","10","11"),
            ar("7","8","10","11","13"),
            ar("7","8","9","10","11","13"),
            ar( ar( 1L, 0L, 0L, 0L, 0L, 0L), // "7"
                    ar( 0L, 1L, 0L, 0L, 0L, 0L), // "8"
                    ar( 0L, 0L, 0L, 0L, 0L, 1L), // "9"
                    ar( 0L, 0L, 0L, 1L, 0L, 0L), // "10"
                    ar( 0L, 0L, 0L, 0L, 1L, 0L), // "11"
                    ar( 0L, 0L, 0L, 0L, 0L, 0L)  // "13"
            ),
            debug, false);

    // Mixed case
    simpleCMTest(
            frame("v1", vec(ar("-1", "1", "A"), ari( 0, 1, 2) )),
            frame("v1", vec(ar( "0", "1", "B"), ari( 0, 1, 2) )),
            ar("-1", "1", "A"),
            ar( "0", "1", "B"),
            ar( "-1", "1", "A", "0", "B"),
            ar( ar( 0L, 0L, 0L, 1L, 0L), // "-1"
                    ar( 0L, 1L, 0L, 0L, 0L), // "1"
                    ar( 0L, 0L, 0L, 0L, 1L), // "A"
                    ar( 0L, 0L, 0L, 0L, 0L), // "0"
                    ar( 0L, 0L, 0L, 0L, 0L)  // "B"
            ),
            debug, false);

    // Mixed case with change of numeric ordering 1, 10, 9 -> 1,9,10
    simpleCMTest(
            frame("v1", vec(ar("-1", "1", "10", "9", "A"), ari( 0, 1, 2, 3, 4) )),
            frame("v1", vec(ar( "0", "2",  "8", "9", "B"), ari( 0, 1, 2, 3, 4) )),
            ar("-1", "1", "10", "9", "A"),
            ar( "0", "2",  "8", "9", "B"),
            ar( "-1", "1", "10", "9", "A", "0", "2", "8", "B"),
            ar( ar( 0L, 0L, 0L, 0L, 0L, 1L, 0L, 0L, 0L), // "-1"
                    ar( 0L, 0L, 0L, 0L, 0L, 0L, 1L, 0L, 0L), // "1"
                    ar( 0L, 0L, 0L, 0L, 0L, 0L, 0L, 1L, 0L), // "10"
                    ar( 0L, 0L, 0L, 1L, 0L, 0L, 0L, 0L, 0L), // "9"
                    ar( 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 1L), // "A"
                    ar( 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L), // "0"
                    ar( 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L), // "2"
                    ar( 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L), // "8"
                    ar( 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L)  // "B"
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
    Vec old1 = null;
    Vec old2 = null;
    Vec oldv2 = null;
    try {
      // Way to simulate labels coming out of predict - those are always enums.
      if (toEnum) {
        old1 = v1.replace(0, v1.vecs()[0].toEnum()); //keep reference for mem cleanup
        old2 = v2.replace(0, v2.vecs()[0].toEnum());
      }
      oldv2 = v2.vecs()[0]; // keep reference for mem cleanup - this vec might be used as MasterVec for EnumWrapped Vecs!

      // this call can modify v2 vecs during adaptation, that's why we need to keep the old oldv1/oldv2
      String[] ignoredwarnings = adaptTestForTrain(v1._names, v1.domains(), v2, Double.NaN, true);

      ConfusionMatrix cm = buildCM(v1.vecs()[0], v2.vecs()[0]);

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
      if (old1 != null) old1.remove();
      if (old2 != null) old2.remove();
      if (oldv2 != null) oldv2.remove();
    }
  }

  private void assertCMEqual(String[] expectedDomain, long[][] expectedCM, ConfusionMatrix actualCM) {
    Assert.assertArrayEquals("Expected domain differs",     expectedDomain,        actualCM._domain);
    long[][] acm = actualCM._arr;
    Assert.assertEquals("CM dimension differs", expectedCM.length, acm.length);
    for (int i=0; i < acm.length; i++) Assert.assertArrayEquals("CM row " +i+" differs!", expectedCM[i], acm[i]);
  }

}
