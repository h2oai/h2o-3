package water;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static water.ExternalFrameUtils.EXPECTED_BOOL;
import static water.ExternalFrameUtils.EXPECTED_CHAR;
import static water.ExternalFrameUtils.EXPECTED_DOUBLE;
import static water.ExternalFrameUtils.EXPECTED_FLOAT;
import static water.ExternalFrameUtils.EXPECTED_LONG;
import static water.ExternalFrameUtils.EXPECTED_SHORT;
import static water.ExternalFrameUtils.EXPECTED_STRING;
import static water.ExternalFrameUtils.EXPECTED_TIMESTAMP;
import static water.TestUtil.ari;
import static water.TestUtil.ar;
import static water.ExternalFrameUtils.EXPECTED_BYTE;
import static water.ExternalFrameUtils.EXPECTED_INT;
import static water.ExternalFrameUtils.EXPECTED_VECTOR;
import static water.fvec.Vec.T_NUM;

import static water.ExternalFrameUtils.getElemSizes;
import static water.ExternalFrameUtils.vecTypesFromExpectedTypes;
import static water.fvec.Vec.T_STR;
import static water.fvec.Vec.T_TIME;

public class ExternalFrameUtilsTest extends TestBase {

  static final int[] EMPTY_ARI = new int[0];

  @Test
  public void testGetElemSizes() {
    assertArrayEquals("Size of single primitive element is [1]",
                      ari(1), getElemSizes(ar(EXPECTED_BYTE), EMPTY_ARI));
    assertArrayEquals("Size of two single primitive elements is [1,1]",
                      ari(1,1), getElemSizes(ar(EXPECTED_INT, EXPECTED_BYTE), EMPTY_ARI));
    assertArrayEquals("Size of a single vec is size of specified length",
                      ari(1234), getElemSizes(ar(EXPECTED_VECTOR), ari(1234)));
    assertArrayEquals("Size of a two vec should match passed vector lenghts ",
                      ari(1234, 4567), getElemSizes(ar(EXPECTED_VECTOR, EXPECTED_VECTOR), ari(1234, 4567)));
    assertArrayEquals("Size of a two vec and primitive element should match passed vector lenghts and 1",
                      ari(1234, 1, 4567), getElemSizes(ar(EXPECTED_VECTOR, EXPECTED_BYTE, EXPECTED_VECTOR), ari(1234, 4567)));
    
  }

  @Test
  public void testVecTypesFromExpectedTypes() {
    assertNumericTypes(EXPECTED_BYTE, EXPECTED_CHAR, EXPECTED_SHORT,
                       EXPECTED_INT, EXPECTED_LONG,
                       EXPECTED_FLOAT, EXPECTED_DOUBLE);

    assertArrayEquals("Boolean type is mapped to vector numeric type",
                      ar(T_NUM),
                      vecTypesFromExpectedTypes(ar(EXPECTED_BOOL), EMPTY_ARI));
    
    assertArrayEquals("String type is mapped to vector string type",
                      ar(T_STR),
                      vecTypesFromExpectedTypes(ar(EXPECTED_STRING), EMPTY_ARI));

    assertArrayEquals("String type is mapped to vector string type",
                      ar(T_TIME),
                      vecTypesFromExpectedTypes(ar(EXPECTED_TIMESTAMP), EMPTY_ARI));

    assertArrayEquals("Two primitive types are mapped to vector numeric types",
                      ar(T_NUM, T_STR),
                      vecTypesFromExpectedTypes(ar(EXPECTED_BYTE, EXPECTED_STRING), EMPTY_ARI));

    assertArrayEquals("Vector type is mapped to array of vector numeric types",
                      ar(T_NUM, T_NUM, T_NUM),
                      vecTypesFromExpectedTypes(ar(EXPECTED_VECTOR), ari(3)));
    
    assertArrayEquals("Two vector types are mapped to array of vector numeric types",
                      ar(T_NUM, T_NUM, T_NUM, T_NUM, T_NUM),
                      vecTypesFromExpectedTypes(ar(EXPECTED_VECTOR, EXPECTED_VECTOR), ari(3, 2)));

    assertArrayEquals("Mixed types are mapped properly",
                      ar(T_NUM, T_NUM, T_NUM, T_STR, T_NUM, T_NUM),
                      vecTypesFromExpectedTypes(ar(EXPECTED_VECTOR, EXPECTED_STRING, EXPECTED_VECTOR), ari(3, 2)));

  }

  static void assertNumericTypes(byte ...type) {
    for (byte t : type) {
      assertArrayEquals("Numeric type is mapped to vector numeric type",
                        ar(T_NUM),
                        vecTypesFromExpectedTypes(ar(t), EMPTY_ARI));
    }
  }
}
