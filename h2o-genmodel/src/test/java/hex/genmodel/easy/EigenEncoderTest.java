package hex.genmodel.easy;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class EigenEncoderTest {
  
  @Test
  public void encodeCatValue() {
    EigenEncoder encoder = new EigenEncoder("col1", 3, new String[]{"a", "b", "c"}, new double []{0.234, 1.203, 0});
    double[] row = new double[8];
    Arrays.fill(row, Double.NaN);

    assertTrue(encoder.encodeCatValue("a", row));
    assertArrayEquals(new double[]{Double.NaN, Double.NaN, Double.NaN, 0.234, Double.NaN, Double.NaN, Double.NaN, Double.NaN}, row, 1e-3);

    assertTrue(encoder.encodeCatValue("b", row));
    assertArrayEquals(new double[]{Double.NaN, Double.NaN, Double.NaN, 1.203, Double.NaN, Double.NaN, Double.NaN, Double.NaN}, row, 1e-3);

    assertTrue(encoder.encodeCatValue("c", row));
    assertArrayEquals(new double[]{Double.NaN, Double.NaN, Double.NaN, 0.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN}, row, 1e-3);

    assertFalse(encoder.encodeCatValue("invalid", row));
    assertArrayEquals(new double[]{Double.NaN, Double.NaN, Double.NaN, 0.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN}, row, 1e-3);
  }

  @Test
  public void encodeNA() {
    EigenEncoder encoder = new EigenEncoder("col1", 1, new String[]{"a", "b"}, new double[] {2.345, 3.456});
    double[] row = new double[5];
    Arrays.fill(row, Double.NaN);

    encoder.encodeNA(row);
    assertArrayEquals(new double[]{Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN}, row, 0);
  }

}
