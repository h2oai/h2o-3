package water.fvec;

import org.junit.*;

import java.util.Arrays;
import hex.Model;
import water.TestUtil;

public class TransfVecTest extends TestUtil {

  // Need to move test files over
  @Test @Ignore
  public void testAdaptTo() {
    Frame v1 = null, v2 = null;
    try {
      v1 = parse_test_file("smalldata/test/cm/v1.csv");
      v2 = parse_test_file("smalldata/test/cm/v4.csv");
      //vv = v1.vecs()[0].adaptTo(v2.vecs()[0], true);
    } finally {
      if (v1 != null) v1.delete();
      if (v2 != null) v2.delete();
    }
  }

  /** Verifies that {@link Model#getDomainMapping(String[], String[], boolean)} returns
   *  correct values. */
  @Test public void testModelMappingCall() {
    testModelMapping(ar("A", "B", "C"), ar("A", "B", "C"), ar( ari(0,1,2), ari(0,1,2) ));
    testModelMapping(ar("A", "B", "C"), ar(     "B", "C"), ar( ari(0,1),   ari(1,2)   ));
    testModelMapping(ar("A", "B", "C"), ar(     "B"     ), ar( ari(0),     ari(1)     ));

    testModelMapping(ar("A", "B", "C"), ar("A", "B", "C", "D"), ar( ari(0,1,2), ari(0,1,2) ));
    testModelMapping(ar("A", "B", "C"), ar(     "B", "C", "D"), ar( ari(0,1),   ari(1,2)   ));
    testModelMapping(ar("A", "B", "C"), ar(     "B",      "D"), ar( ari(0),     ari(1)     ));
  }

  private static void testModelMapping(String[] modelDomain, String[] colDomain, int[][] expectedMapping) {
    int[][] mapping = Model.getDomainMapping(modelDomain, colDomain, false);
    Assert.assertEquals("getDomainMapping should return int[2][]", 2, mapping.length);
    Assert.assertEquals("getDomainMapping should return two arrays of the same length", mapping[0].length, mapping[1].length);
    Assert.assertArrayEquals("Values array differs",  expectedMapping[0], mapping[0]);
    Assert.assertArrayEquals("Indexes array differs", expectedMapping[1], mapping[1]);
    // Sanity check if we pass correct indexes
    int[] indexes = mapping[0];
    for (int i=0; i<indexes.length; i++)
      if (indexes[i] < 0 || indexes[i]>=modelDomain.length)
        Assert.assertTrue("Returned index mapping at " + i + "does not index correctly model domain " + Arrays.toString(modelDomain), false);
  }

  @Test public void testMappingComposition() {
    assertEqualMapping( ar( ari(1), ari(0)),   // expecting composed mapping
     TransfVec.compose( ar( ari(-1,1), null),     // <- 1st mapping
                        ar( ari( 1,2), ari(0,1))) // <- 2nd mapping
                        );
    assertEqualMapping( ar( ari(1,2,3,4,5,6), ari(0,1,2,3,4,5)), // expecting composed mapping
     TransfVec.compose( ar( ari(-1,1,2,3,4,5,6), null), // <- 1st mapping
                        ar( ari(1,2,3,4,5,6),    ari(0,1,2,3,4,5)))  // <- 2nd mapping
                        );
  }

  private static void assertEqualMapping(int[][] expectedMapping, int[][] actualMapping) {
    Assert.assertEquals("Mapping should be composed of two arrays", 2, actualMapping.length);
    Assert.assertEquals("Mapping should be composed of two arrays of equal length", actualMapping[0].length, actualMapping[1].length);
  }
}
