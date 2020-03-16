package hex.genmodel.easy;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class EnumLimitedEncoderTest {
  
  @Test
  public void encodeCatValue() {
    EnumLimitedEncoder enumLimitedEncoder = new EnumLimitedEncoder("col1", 3, new String[]{"a", "b", "c", "d", "e", "f", "g", "h"}, new String[]{"a", "b", "c", "other"} );
    double[] row = new double[8];
    Arrays.fill(row, Double.NaN);

    assertTrue(enumLimitedEncoder.encodeCatValue("a", row));
    assertArrayEquals(new double[]{Double.NaN, Double.NaN, Double.NaN, 0.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN}, row, 0);

    assertTrue(enumLimitedEncoder.encodeCatValue("b", row));
    assertArrayEquals(new double[]{Double.NaN, Double.NaN, Double.NaN, 1.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN}, row, 0);

    assertTrue(enumLimitedEncoder.encodeCatValue("c", row));
    assertArrayEquals(new double[]{Double.NaN, Double.NaN, Double.NaN, 2.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN}, row, 0);

    assertTrue(enumLimitedEncoder.encodeCatValue("d", row));
    assertArrayEquals(new double[]{Double.NaN, Double.NaN, Double.NaN, 3.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN}, row, 0);

    assertTrue(enumLimitedEncoder.encodeCatValue("e", row));
    assertArrayEquals(new double[]{Double.NaN, Double.NaN, Double.NaN, 3.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN}, row, 0);

    assertTrue(enumLimitedEncoder.encodeCatValue("f", row));
    assertArrayEquals(new double[]{Double.NaN, Double.NaN, Double.NaN, 3.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN}, row, 0);

    assertTrue(enumLimitedEncoder.encodeCatValue("g", row));
    assertArrayEquals(new double[]{Double.NaN, Double.NaN, Double.NaN, 3.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN}, row, 0);

    assertTrue(enumLimitedEncoder.encodeCatValue("h", row));
    assertArrayEquals(new double[]{Double.NaN, Double.NaN, Double.NaN, 3.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN}, row, 0);

    enumLimitedEncoder = new EnumLimitedEncoder("col1", 2, new String[]{"a", "b", "c"}, new String[]{"a", "b", "c"} );
    EnumEncoder enumEncoder = new EnumEncoder("col1", 2, new String[]{"a", "b", "c"});

    double[] rowEnumLimited = new double[3];
    double[] rowEnum = new double[3];
    Arrays.fill(rowEnumLimited, Double.NaN);
    Arrays.fill(rowEnum, Double.NaN);

    assertTrue(enumLimitedEncoder.encodeCatValue("a", rowEnumLimited));
    assertTrue(enumEncoder.encodeCatValue("a", rowEnum));
    assertArrayEquals(new double[]{Double.NaN, Double.NaN, 0.0}, rowEnumLimited, 0);
    assertArrayEquals(rowEnum, rowEnumLimited, 0);
  }

  @Test
  public void encodeNA() {
    EnumLimitedEncoder encoder = new EnumLimitedEncoder("col1", 1, new String[]{"a", "b", "c", "d"}, new String[]{"a","b","other"});
  
    double[] row = new double[4];
    Arrays.fill(row, Double.NaN);

    encoder.encodeNA(row);
    assertArrayEquals(new double[]{Double.NaN, Double.NaN, Double.NaN, Double.NaN}, row, 0);
  }

}
