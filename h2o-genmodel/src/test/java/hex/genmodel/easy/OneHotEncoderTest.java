package hex.genmodel.easy;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class OneHotEncoderTest {
  
  @Test
  public void encodeCatValue() {
    OneHotEncoder encoder = new OneHotEncoder("col1", 3, new String[]{"a", "b", "c"});
    double[] row = new double[8];
    Arrays.fill(row, Double.NaN);

    assertTrue(encoder.encodeCatValue("a", row));
    assertArrayEquals(new double[]{Double.NaN, Double.NaN, Double.NaN, 1.0, 0.0, 0.0, 0.0, Double.NaN}, row, 0);

    assertTrue(encoder.encodeCatValue("b", row));
    assertArrayEquals(new double[]{Double.NaN, Double.NaN, Double.NaN, 0.0, 1.0, 0.0, 0.0, Double.NaN}, row, 0);

    assertTrue(encoder.encodeCatValue("c", row));
    assertArrayEquals(new double[]{Double.NaN, Double.NaN, Double.NaN, 0.0, 0.0, 1.0, 0.0, Double.NaN}, row, 0);

    assertFalse(encoder.encodeCatValue("invalid", row));
    assertArrayEquals(new double[]{Double.NaN, Double.NaN, Double.NaN, 0.0, 0.0, 1.0, 0.0, Double.NaN}, row, 0);
  }

  @Test
  public void encodeNA() {
    OneHotEncoder encoder = new OneHotEncoder("col1", 1, new String[]{"a", "b"});
    double[] row = new double[5];
    Arrays.fill(row, Double.NaN);

    encoder.encodeNA(row);
    assertArrayEquals(new double[]{Double.NaN, 0.0, 0.0, 1.0, Double.NaN}, row, 0);
  }

}
