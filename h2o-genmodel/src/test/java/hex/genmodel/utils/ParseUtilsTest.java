package hex.genmodel.utils;

import org.junit.Before;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ParseUtilsTest {
  Random _random;

  @Before
  public void setUp(){
    _random = new Random();
  }
  @Test
  public void testReadInts() throws Exception {
    int oneValue = _random.nextInt();
    String valStr = Integer.toString(oneValue);
    assertEquals(ParseUtils.tryParse(valStr, null), oneValue);

    int arraySize = _random.nextInt(10)+1;
    int arrySizeM1 = arraySize-1;
    int[] valArray = new int[arraySize];
    StringBuilder sb = new StringBuilder(arraySize);
    sb.append("[");
    for (int index=0; index < arraySize; index++) {
      valArray[index] = _random.nextInt();
      sb.append(Integer.toString(valArray[index]));
      if (index < (arrySizeM1))
        sb.append(',');
    }
    sb.append("]");
    int[] readback = (int[]) ParseUtils.tryParse(sb.toString(), null);
    assertArrayEquals(readback, valArray);
  }

  @Test
  public void testReadDoubles() throws Exception {
    double oneValue = _random.nextDouble();
    String valStr = Double.toString(oneValue);
    assertEquals(ParseUtils.tryParse(valStr, null), oneValue);

    int arraySize = _random.nextInt(10)+1;
    int arrySizeM1 = arraySize-1;
    double[] valArray = new double[arraySize];
    StringBuilder sb = new StringBuilder(arraySize);
    sb.append("[");
    for (int index=0; index < arraySize; index++) {
      valArray[index] = _random.nextDouble();
      sb.append(Double.toString(valArray[index]));
      if (index < (arrySizeM1))
        sb.append(',');
    }
    sb.append("]");
    double[] readback = (double[]) ParseUtils.tryParse(sb.toString(), null);
    assertArrayEquals(readback, valArray, 1e-10);
  }

  @Test
  public void testReadLongs() throws Exception {
    long oneValue = _random.nextLong();
    String valStr = Long.toString(oneValue);
    assertEquals(ParseUtils.tryParse(valStr, null), oneValue);

    int arraySize = _random.nextInt(10)+1;
    int arrySizeM1 = arraySize-1;
    long[] valArray = new long[arraySize];
    StringBuilder sb = new StringBuilder(arraySize);
    sb.append("[");
    for (int index=0; index < arraySize; index++) {
      valArray[index] = _random.nextLong();
      sb.append(Long.toString(valArray[index]));
      if (index < (arrySizeM1))
        sb.append(',');
    }
    sb.append("]");
    long[] readback = (long[]) ParseUtils.tryParse(sb.toString(), null);
    assertArrayEquals(readback, valArray);
  }
}
