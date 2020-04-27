package hex.genmodel.easy;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

public class LabelEncoderTest {
  
  @Test
  public void encodeCatValue() {
    LabelEncoder labelEncoder = new LabelEncoder( 3, new String[]{"a", "b", "c", "d", "e", "f", "g", "h"});
    double[] row = new double[8];
    Arrays.fill(row, Double.NaN);

    assertTrue(labelEncoder.encodeCatValue("a", row));
    assertArrayEquals(new double[]{Double.NaN, Double.NaN, Double.NaN, 0.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN}, row, 0);

    assertTrue(labelEncoder.encodeCatValue("b", row));
    assertArrayEquals(new double[]{Double.NaN, Double.NaN, Double.NaN, 1.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN}, row, 0);

    assertTrue(labelEncoder.encodeCatValue("c", row));
    assertArrayEquals(new double[]{Double.NaN, Double.NaN, Double.NaN, 2.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN}, row, 0);

    assertTrue(labelEncoder.encodeCatValue("d", row));
    assertArrayEquals(new double[]{Double.NaN, Double.NaN, Double.NaN, 3.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN}, row, 0);

    assertTrue(labelEncoder.encodeCatValue("e", row));
    assertArrayEquals(new double[]{Double.NaN, Double.NaN, Double.NaN, 4.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN}, row, 0);

    assertTrue(labelEncoder.encodeCatValue("f", row));
    assertArrayEquals(new double[]{Double.NaN, Double.NaN, Double.NaN, 5.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN}, row, 0);

    assertTrue(labelEncoder.encodeCatValue("g", row));
    assertArrayEquals(new double[]{Double.NaN, Double.NaN, Double.NaN, 6.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN}, row, 0);

    assertTrue(labelEncoder.encodeCatValue("h", row));
    assertArrayEquals(new double[]{Double.NaN, Double.NaN, Double.NaN, 7.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN}, row, 0);
  }

  @Test
  public void encodeNA() {
    LabelEncoder encoder = new LabelEncoder( 1, new String[]{"a", "b", "c", "d"});
  
    double[] row = new double[4];
    Arrays.fill(row, Double.NaN);

    encoder.encodeNA(row);
    assertArrayEquals(new double[]{Double.NaN, Double.NaN, Double.NaN, Double.NaN}, row, 0);
  }

}
