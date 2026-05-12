package water.util;

import Jama.Matrix;
import org.junit.Assert;
import org.junit.Test;
import water.TestUtil;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.Assert.*;
import static water.TestUtil.genRandomArray;
import static water.TestUtil.genRandomMatrix;
import static water.util.ArrayUtils.*;

/**
 * Test FrameUtils interface.
 */
public class ArrayUtilsTest {
  @Test
  public void testAppendBytes() {
    byte[] sut = {1, 2, 3};
    byte[] sut2 = {3, 4};
    byte[] expected = {1, 2, 3, 3, 4};
    byte[] empty = {};
    assertArrayEquals(null, append((byte[]) null, null));
    assertArrayEquals(sut, append(null, sut));
    assertArrayEquals(sut, append(sut, null));
    assertArrayEquals(empty, append(null, empty));
    assertArrayEquals(empty, append(empty, null));
    assertArrayEquals(sut, append(empty, sut));
    assertArrayEquals(sut, append(sut, empty));
    assertArrayEquals(expected, append(sut, sut2));
  }

  @Test
  public void testAppendInts() {
    int[] sut = {1, 2, 3};
    int[] sut2 = {3, 4};
    int[] expected = {1, 2, 3, 3, 4};
    int[] empty = {};
    assertArrayEquals(null, append((int[]) null, null));
    assertArrayEquals(sut, append(null, sut));
    assertArrayEquals(sut, append(sut, null));
    assertArrayEquals(empty, append(null, empty));
    assertArrayEquals(empty, append(empty, null));
    assertArrayEquals(sut, append(empty, sut));
    assertArrayEquals(sut, append(sut, empty));
    assertArrayEquals(expected, append(sut, sut2));
  }
  
  @Test
  public void testAppendDouble() {
    double[] sut = {1.0, 2.0, 3.0};
    double[] expected = {1.0, 2.0, 3.0, 3.0};
    double[] empty = {};
    assertArrayEquals(expected, append(sut, 3.0), 0.0);
    assertArrayEquals(new double[]{3.0}, append(empty, 3.0), 0.0);
    assertArrayEquals(new double[]{3.0}, append(null, 3.0), 0.0);
  }

  @Test
  public void testAppendLongs() {
    long[] sut = {1, 2, 3};
    long[] sut2 = {3, 4};
    long[] expected = {1, 2, 3, 3, 4};
    long[] empty = {};
    assertArrayEquals(null, append((int[]) null, null));
    assertArrayEquals(sut, append(null, sut));
    assertArrayEquals(sut, append(sut, null));
    assertArrayEquals(empty, append(null, empty));
    assertArrayEquals(empty, append(empty, null));
    assertArrayEquals(sut, append(empty, sut));
    assertArrayEquals(sut, append(sut, empty));
    assertArrayEquals(expected, append(sut, sut2));
  }

  @Test
  public void testRemoveOneObject() {
    Integer[] sut = {1, 2, 3};
    Integer[] sutWithout1 = {2, 3};
    Integer[] sutWithout2 = {1, 3};
    Integer[] sutWithout3 = {1, 2};
    assertArrayEquals("Should have not deleted",    sut,         remove(sut, Integer.MIN_VALUE));
    assertArrayEquals("Should not have deleted ",   sut, remove(sut, -1));
    assertArrayEquals("Should have deleted first",  sutWithout1, remove(sut, 0));
    assertArrayEquals("Should have deleted second", sutWithout2, remove(sut, 1));
    assertArrayEquals("Should have deleted third",  sutWithout3, remove(sut, 2));
    assertArrayEquals("Should have not deleted",    sut,         remove(sut, 3));
    assertArrayEquals("Should have not deleted",    sut,         remove(sut, Integer.MAX_VALUE));
  }

  @Test
  public void testRemoveOneObjectFromSingleton() {
    Integer[] sut = {1};
    Integer[] sutWithout1 = {};
    assertArrayEquals("Should have not deleted",    sut,         remove(sut, Integer.MIN_VALUE));
    assertArrayEquals("Should not have deleted ",   sut, remove(sut, -1));
    assertArrayEquals("Should have deleted first",  sutWithout1, remove(sut, 0));
    assertArrayEquals("Should have not deleted",    sut,         remove(sut, 1));
    assertArrayEquals("Should have not deleted",    sut,         remove(sut, Integer.MAX_VALUE));
  }

  @Test
  public void testRemoveOneObjectFromEmpty() {
    Integer[] sut = {};
    assertArrayEquals("Nothing to remove",    sut, remove(sut, -1));
    assertArrayEquals("Nothing to remove",    sut, remove(sut, 0));
    assertArrayEquals("Nothing to remove",    sut, remove(sut, 1));
  }

  @Test
  public void testRemoveOneByte() {
    byte[] sut = {1, 2, 3};
    byte[] sutWithout1 = {2, 3};
    byte[] sutWithout2 = {1, 3};
    byte[] sutWithout3 = {1, 2};
    assertArrayEquals("Should have not deleted",    sut,         remove(sut, Integer.MIN_VALUE));
    assertArrayEquals("Should not have deleted ",   sut, remove(sut, -1));
    assertArrayEquals("Should have deleted first",  sutWithout1, remove(sut, 0));
    assertArrayEquals("Should have deleted second", sutWithout2, remove(sut, 1));
    assertArrayEquals("Should have deleted third",  sutWithout3, remove(sut, 2));
    assertArrayEquals("Should have not deleted",    sut,         remove(sut, 3));
    assertArrayEquals("Should have not deleted",    sut,         remove(sut, Integer.MAX_VALUE));
  }

  @Test
  public void testRemoveOneByteFromSingleton() {
    byte[] sut = {1};
    byte[] sutWithout1 = {};
    assertArrayEquals("Should have not deleted",    sut,         remove(sut, Integer.MIN_VALUE));
    assertArrayEquals("Should not have deleted ",   sut, remove(sut, -1));
    assertArrayEquals("Should have deleted first",  sutWithout1, remove(sut, 0));
    assertArrayEquals("Should have not deleted",    sut,         remove(sut, 1));
    assertArrayEquals("Should have not deleted",    sut,         remove(sut, Integer.MAX_VALUE));
  }

  @Test
  public void testRemoveOneByteFromEmpty() {
    byte[] sut = {};
    assertArrayEquals("Nothing to remove",    sut, remove(sut, -1));
    assertArrayEquals("Nothing to remove",    sut, remove(sut, 0));
    assertArrayEquals("Nothing to remove",    sut, remove(sut, 1));
  }

  @Test
  public void testRemoveOneInt() {
    int[] sut = {1, 2, 3};
    int[] sutWithout1 = {2, 3};
    int[] sutWithout2 = {1, 3};
    int[] sutWithout3 = {1, 2};
    assertArrayEquals("Should have not deleted",    sut,         remove(sut, Integer.MIN_VALUE));
    assertArrayEquals("Should not have deleted ",   sut, remove(sut, -1));
    assertArrayEquals("Should have deleted first",  sutWithout1, remove(sut, 0));
    assertArrayEquals("Should have deleted second", sutWithout2, remove(sut, 1));
    assertArrayEquals("Should have deleted third",  sutWithout3, remove(sut, 2));
    assertArrayEquals("Should have not deleted",    sut,         remove(sut, 3));
    assertArrayEquals("Should have not deleted",    sut,         remove(sut, Integer.MAX_VALUE));
  }

  @Test
  public void testRemoveOneIntFromSingleton() {
    int[] sut = {1};
    int[] sutWithout1 = {};
    assertArrayEquals("Should have not deleted",    sut,         remove(sut, Integer.MIN_VALUE));
    assertArrayEquals("Should not have deleted ",   sut, remove(sut, -1));
    assertArrayEquals("Should have deleted first",  sutWithout1, remove(sut, 0));
    assertArrayEquals("Should have not deleted",    sut,         remove(sut, 1));
    assertArrayEquals("Should have not deleted",    sut,         remove(sut, Integer.MAX_VALUE));
  }

  @Test
  public void testRemoveOneIntFromEmpty() {
    int[] sut = {};
    assertArrayEquals("Nothing to remove",    sut, remove(sut, -1));
    assertArrayEquals("Nothing to remove",    sut, remove(sut, 0));
    assertArrayEquals("Nothing to remove",    sut, remove(sut, 1));
  }

  @Test
  public void testCountNonZeroes() {
    double[] empty = {};
    assertEquals(0, countNonzeros(empty));
    double[] singlenz = {1.0};
    assertEquals(1, countNonzeros(singlenz));
    double[] threeZeroes = {0.0, 0.0, 0.0};
    assertEquals(0, countNonzeros(threeZeroes));
    double[] somenz = {-1.0, Double.MIN_VALUE, 0.0, Double.MAX_VALUE, 0.001, 0.0, 42.0};
    assertEquals(5, countNonzeros(somenz));
  }

  @Test
  public void testSortIndicesCutoffIsStable() {
    int arrayLen = 100;
    int[] indices = ArrayUtils.range(0, arrayLen - 1);
    double[] values = new double[arrayLen]; // intentionally only zeros
    double[] valuesInput = Arrays.copyOf(values, values.length);

    sort(indices, valuesInput, 500, 1);
    assertArrayEquals("Not correctly sorted or the same values were replaced",
            ArrayUtils.range(0, arrayLen - 1), indices);
    assertArrayEquals("Values array is changed", values, valuesInput, 0);

    sort(indices, valuesInput, 500, -1);
    assertArrayEquals("Not correctly sorted or the same values were replaced",
            ArrayUtils.range(0, arrayLen - 1), indices);
    assertArrayEquals("Values array is changed", values, valuesInput, 0);
  }

  @Test
  public void testSortIndicesCutoffBranch() {
    int arrayLen = 10;
    int[] indices = ArrayUtils.range(0, arrayLen - 1);
    double[] values = new double[]{-12, -5, 1, 255, 1.25, -1, 0, 2, -26, 16};
    double[] valuesInput = Arrays.copyOf(values, values.length);

    sort(indices, valuesInput, 500, 1);
    assertArrayEquals("Not correctly sorted", new int[]{8, 0, 1, 5, 6, 2, 4, 7, 9, 3}, indices);
    assertArrayEquals("Values array is changed", values, valuesInput, 0);
    for (int index = 1; index < arrayLen; index++)
      Assert.assertTrue(values[indices[index-1]]+" should be <= "+values[indices[index]],
              values[indices[index-1]] <= values[indices[index]]);

    sort(indices, valuesInput, 500, -1);
    assertArrayEquals("Not correctly sorted", new int[]{3, 9, 7, 4, 2, 6, 5, 1, 0, 8}, indices);
    assertArrayEquals("Values array is changed", values, valuesInput, 0);
    for (int index = 1; index < arrayLen; index++)
      Assert.assertTrue(values[indices[index-1]]+" should be >= "+values[indices[index]],
              values[indices[index-1]] >= values[indices[index]]);
  }

  @Test
  public void testSortIndicesJavaSortBranch() {
    int arrayLen = 10;
    int[] indices = ArrayUtils.range(0, arrayLen - 1);
    double[] values = new double[]{-12, -5, 1, 255, 1.25, -1, 0, 2, -26, 16};
    double[] valuesInput = Arrays.copyOf(values, values.length);

    sort(indices, valuesInput, -1, 1);
    assertArrayEquals("Not correctly sorted", new int[]{8, 0, 1, 5, 6, 2, 4, 7, 9, 3}, indices);
    assertArrayEquals("Values array is changed", values, valuesInput, 0);
    for (int index = 1; index < arrayLen; index++)
      Assert.assertTrue(values[indices[index-1]]+" should be <= "+values[indices[index]],
              values[indices[index-1]] <= values[indices[index]]);

    sort(indices, valuesInput, -1, -1);
    assertArrayEquals("Not correctly sorted", new int[]{3, 9, 7,4, 2, 6, 5, 1, 0, 8}, indices);
    assertArrayEquals("Values array is changed", values, valuesInput, 0);
    for (int index = 1; index < arrayLen; index++)
      Assert.assertTrue(values[indices[index-1]]+" should be >= "+values[indices[index]],
              values[indices[index-1]] >= values[indices[index]]);
  }
  
  @Test
  public void testSortIndicesRandomAttackJavaSortBranch() {
    Random randObj = new Random(12345);
    int arrayLen = 100;
    int[] indices = new int[arrayLen];
    double[] values = new double[arrayLen];
    for (int index = 0; index < arrayLen; index++) {// generate data array
      values[index] = randObj.nextDouble();
      indices[index] = index;
    }

    sort(indices, values, -1, 1); // sorting in ascending order
    for (int index = 1; index < arrayLen; index++)  // check correct sorting in ascending order
      Assert.assertTrue(values[indices[index-1]]+" should be <= "+values[indices[index]], 
              values[indices[index-1]] <= values[indices[index]]); 
    
    sort(indices, values, -1, -1);  // sorting in descending order
    for (int index = 1; index < arrayLen; index++)  // check correct sorting in descending order
      Assert.assertTrue(values[indices[index-1]]+" should be >= "+values[indices[index]],
              values[indices[index-1]] >= values[indices[index]]);  
  }

  @Test
  public void testSortIndicesRandomAttackCutoffBranch() {
    Random randObj = new Random(12345);
    int arrayLen = 100;
    int[] indices = new int[arrayLen];
    double[] values = new double[arrayLen];
    for (int index = 0; index < arrayLen; index++) {// generate data array
      values[index] = randObj.nextDouble();
      indices[index] = index;
    }

    sort(indices, values, 500, 1); // sorting in ascending order
    for (int index = 1; index < arrayLen; index++)  // check correct sorting in ascending order
      Assert.assertTrue(values[indices[index-1]]+" should be <= "+values[indices[index]],
              values[indices[index-1]] <= values[indices[index]]);

    sort(indices, values, 500, -1);  // sorting in descending order
    for (int index = 1; index < arrayLen; index++)  // check correct sorting in descending order
      Assert.assertTrue(values[indices[index-1]]+" should be >= "+values[indices[index]],
              values[indices[index-1]] >= values[indices[index]]);
  }

  @Test
  public void testAddWithCoefficients() {
    float[] a = {1.0f, 2.0f, 3.0f};
    float[] b = {100.0f, 200.0f, 300.0f};

    float[] result = ArrayUtils.add(10.0f, a, 2.0f, b);
    assertTrue(result == a);
    assertArrayEquals(new float[]{210.0f, 420.0f, 630.0f}, a, 0.001f);
  }

  @Test public void test_encodeAsInt() {
    byte[]bs = new byte[]{0,0,0,0,1};
    assertEquals(0, encodeAsInt(bs, 0));
    assertEquals(0x1000000, encodeAsInt(bs, 1));
    try {
      encodeAsInt(bs, 2);
      fail("Should not work");
    } catch (Throwable ignore) {}
    
    bs[0] = (byte)0xfe;
    assertEquals(0xfe, encodeAsInt(bs, 0));
    bs[1] = (byte)0xca;
    assertEquals(0xcafe, encodeAsInt(bs, 0));
    bs[2] = (byte)0xde;
    assertEquals(0xdecafe, encodeAsInt(bs, 0));
    bs[3] = (byte)0x0a;
    assertEquals(0xadecafe, encodeAsInt(bs, 0));
    assertEquals(0x10adeca, encodeAsInt(bs, 1));
  }

  @Test public void test_decodeAsInt() {
    byte[]bs = new byte[]{1,2,3,4,5};
    assertArrayEquals(new byte[]{0,0,0,0,5}, decodeAsInt(0, bs, 0));
    try {
      decodeAsInt(1, bs, 3);
      fail("Should not work");
    } catch (Throwable ignore) {}
    
    try {
      decodeAsInt(256, bs, 4);
      fail("Should not work");
    } catch (Throwable ignore) {}

    assertArrayEquals(new byte[]{(byte)0xfe,0,0,0,5}, decodeAsInt(0xfe, bs, 0));
    assertArrayEquals(new byte[]{(byte)0xfe,(byte)0xca,0,0,5}, decodeAsInt(0xcafe, bs, 0));
    assertArrayEquals(new byte[]{(byte)0xfe,(byte)0xca,(byte)0xde,0,5}, decodeAsInt(0xdecafe, bs, 0));
    assertArrayEquals(new byte[]{(byte)0xfe,(byte)0xca,(byte)0xde,(byte)0x80,5}, decodeAsInt(0x80decafe, bs, 0));
    
  }

  @Test
  public void testFloatsToDouble() {
    assertNull(toDouble((float[]) null));
    assertArrayEquals(new double[]{1.0, 2.2}, toDouble(new float[]{1.0f, 2.2f}), 1e-7);
  }

  @Test
  public void testIntsToDouble() {
    assertNull(toDouble((int[]) null));
    assertArrayEquals(new double[]{1.0, 42.0}, toDouble(new int[]{1, 42}), 0);
  }

  @Test
  public void testOccurrenceCount() {
    byte[] arr = new byte[]{ 1, 2, 1, 1, 3, 4 };
    assertEquals("Occurrence count mismatch.", 3, ArrayUtils.occurrenceCount(arr, (byte) 1));
    assertEquals("Occurrence count mismatch.", 0, ArrayUtils.occurrenceCount(arr, (byte) 0));
  }

  @Test
  public void testOccurrenceCountEmptyArray() {
    byte[] arr = new byte[]{};
    assertEquals("Occurrence count mismatch.", 0, ArrayUtils.occurrenceCount(arr, (byte) 1));
  }
  
  @Test
  public void testByteArraySelect() {
    byte[] arr = new byte[]{ 1, 2, 3, 4, 5, 6 };
    int[] idxs = new int[]{ 3, 1, 5 };

    byte[] expectedSelectedElements = new byte[]{ 4, 2, 6 };

    assertArrayEquals("Selected array elements mismatch.", 
                      expectedSelectedElements, ArrayUtils.select(arr, idxs));
  }
  
  @Test
  public void testByteArrayEmptySelect() {
    byte[] arr = new byte[]{ 1, 2, 3, 4, 5, 6 };
    int[] idxs = new int[]{};

    byte[] expectedSelectedElements = new byte[]{};

    assertArrayEquals("Selected array elements mismatch.",
                      expectedSelectedElements, ArrayUtils.select(arr, idxs));
  }

  @Test
  public void testSubArrayByte() {
    byte[] a = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    byte[] subA = ArrayUtils.subarray(a, 0, 6);
    assertArrayEquals(new byte[]{0, 1, 2, 3, 4, 5}, subA);

    byte[] subA2 = ArrayUtils.subarray(a, 1, 6);
    assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6}, subA2);

    subA2[2] = 2;
    assertArrayEquals(subA2, new byte[]{1, 2, 2, 4, 5, 6});
    assertArrayEquals(new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, a);
  }

  @Test
  public void testSubArray2D() {
    Integer[][] a = new Integer[][]{{0, 1}, {2, 3, 4}, {5, 6, 7, 8, 9}, {5}, {5, 6}, {5, 6, 8}};
    Integer[][] subA = ArrayUtils.subarray2DLazy(a, 0, 2);
    assertArrayEquals("Wrong column subarray", new Integer[][]{{0, 1}, {2, 3, 4}}, subA);

    Integer[][] subA2 = ArrayUtils.subarray2DLazy(a, 1, 5);
    assertArrayEquals("Wrong column subarray", new Integer[][]{{2, 3, 4}, {5, 6, 7, 8, 9}, {5}, {5, 6}, {5, 6, 8}}, subA2);

    subA2[1][2] = 2;
    assertArrayEquals("Subarray not changed",
            new Integer[][]{{2, 3, 4}, {5, 6, 2, 8, 9}, {5}, {5, 6}, {5, 6, 8}}, subA2);
    assertArrayEquals("Original array not changed",
            new Integer[][]{{0, 1}, {2, 3, 4}, {5, 6, 2, 8, 9}, {5}, {5, 6}, {5, 6, 8}}, a);
  }

  @Test
  public void testGaussianVector() {
    double[] a = ArrayUtils.gaussianVector(5, 0xCAFFE);
    assertArrayEquals(new double[]{0.86685, 0.539654, 1.65799, -0.16698, 2.332985}, a, 1e-3);
  }

  @Test
  public void testInnerProductDouble() {
    double[] a = new double[]{1, 2.5, 2.25, -6.25, 4, 7};
    double[] b = new double[]{2, 2, 2, 2, 2, 2};
    double res = ArrayUtils.innerProduct(a, b);
    assertEquals(21, res, 0);
  }

  @Test
  public void testSubDouble() {
    double[] a = new double[]{1, 2.5, 2.25, -6.25, 4, 7};
    double[] b = new double[]{2, 2, 2, 2, 2, 2};
    double[] res = ArrayUtils.subtract(a, b);
    assertArrayEquals(new double[]{-1, 0.5, 0.25, -8.25, 2, 5}, res, 0);
  }
  
  @Test
  public void testToStringQuotedElements(){
    final Object[] names = new String[]{"", "T16384"};
    final String outputString = toStringQuotedElements(names);
    assertEquals("[\"\", \"T16384\"]", outputString);
  }

  @Test
  public void testToStringQuotedElementsNullInput(){
    final String outputString = toStringQuotedElements(null);
    assertEquals("null", outputString);
  }

  @Test
  public void testToStringQuotedElementsEmptyInput(){
    final Object[] emptyNames = new String[0];
    final String outputString = toStringQuotedElements(emptyNames);
    assertEquals("[]", outputString);
  }

  @Test
  public void testMinMax() {
    double[] array = new double[]{1.0, 4.0, -1.0};
    double[] res = minMaxValue(array);
    assertArrayEquals("Result is not correct", new double[]{-1.0, 4.0}, res, 0);
  }

  @Test
  public void testMinMaxNaN() {
    double[] array = new double[]{Double.NaN, 4.0, -1.0};
    double[] res = minMaxValue(array);
    assertArrayEquals("Result is not correct", new double[]{-1.0, 4.0}, res, 0);
  }

  @Test
  public void testMinMaxNaNs() {
    double[] array = new double[]{Double.NaN, Double.NaN, Double.NaN};
    double[] res = minMaxValue(array);
    assertArrayEquals("Result is not correct", new double[]{Double.MAX_VALUE, Double.MIN_VALUE}, res, 0);
  }

  @Test
  public void testSubAndMul() {
    double[] row = new double[]{2.0, 5.0, 6.0};
    double[] p = new double[]{1.0, 4.0, -1.0};
    double[] n = new double[]{-0.25, 0, 0.25};
    double res = subAndMul(row, p, n);

    assertEquals("Result is not correct", 1.5, res, 1e-3);

    double[] sub = ArrayUtils.subtract(row, p);
    double res2 = ArrayUtils.innerProduct(sub, n);

    assertEquals("Result is not correct", res, res2, 1e-3);
  }
  
  @Test
  public void testToStringQuotedElements_with_max_items() {
    final Object[] names = IntStream.range(1, 10).mapToObj(Integer::toString).toArray();
    final String outputString = toStringQuotedElements(names, 5);
    assertEquals("[\"1\", \"2\", \"3\", ...4 not listed..., \"8\", \"9\"]", outputString);
  }

  @Test
  public void testToStringQuotedElements_with_max_items_corner_cases() {
    final Object[] names = IntStream.range(1, 4).mapToObj(Integer::toString).toArray();
    assertEquals("[\"1\", \"2\", \"3\"]", toStringQuotedElements(names, -1));
    assertEquals("[\"1\", \"2\", \"3\"]", toStringQuotedElements(names, 0));
    assertEquals("[\"1\", ...2 not listed...]", toStringQuotedElements(names, 1));
    assertEquals("[\"1\", ...1 not listed..., \"3\"]", toStringQuotedElements(names, 2));
    assertEquals("[\"1\", \"2\", \"3\"]", toStringQuotedElements(names, 3));
    assertEquals("[\"1\", \"2\", \"3\"]", toStringQuotedElements(names, 4));
  }

  @Test
  public void rangeTest() {
    int[] range = ArrayUtils.range(0, 5);
    assertArrayEquals("It is not a valid range", new int[]{0, 1, 2, 3, 4, 5}, range);
  }

  @Test
  public void testUniformDistFromArray() {
    double[][] array = new double[][]{{1.0, 2.0, 3.0}, {-1.0, -2.0, -3.0}};
    double[] dist = uniformDistFromArray(array, 0xDECAF);
    assertArrayEquals("Not expected array of size", new double[]{2.763, -2.958}, dist, 10e-3);
  }

  @Test
  public void testInterpolateLinear(){
    double[] simple = new double[]{Double.NaN, 1, 2, Double.NaN, 4};
    double[] simpleExpected = new double[]{0.5, 1, 2, 3, 4};
    interpolateLinear(simple);
    assertArrayEquals("Interpolated array should be"+Arrays.toString(simpleExpected)+" but is"+Arrays.toString(simple), simpleExpected, simple, 0);

    double[] simple2 = new double[]{Double.NaN, Double.NaN, 3, 4, Double.NaN, 6};
    double[] simpleExpected2 = new double[]{1, 2, 3, 4, 5, 6};
    interpolateLinear(simple2);
    assertArrayEquals("Interpolated array should be"+Arrays.toString(simpleExpected2)+" but is"+ Arrays.toString(simple2), simpleExpected2, simple2, 0);

    double[] complex = new double[]{0, Double.NaN, 3, Double.NaN, 9};
    double[] complexExpected = new double[]{0, 1.5, 3, 6, 9};
    interpolateLinear(complex);
    assertArrayEquals("Interpolated array should be"+Arrays.toString(complexExpected)+" but is"+Arrays.toString(complex), complexExpected, complex, 0);
  }

  @Test
  public void testDistinctLongs() {
    assertArrayEquals(new long[0], ArrayUtils.distinctLongs(0, 100L, RandomUtils.getRNG(42)));
    assertArrayEquals(
            ArrayUtils.toString(ArrayUtils.seq(0, 33)), 
            ArrayUtils.toString(ArrayUtils.distinctLongs(33, 33L, RandomUtils.getRNG(42)))); // comparing strings to avoid int-long conversion ;)
    
    long bound = (long) Integer.MAX_VALUE + 1;
    long[] vals = ArrayUtils.distinctLongs(33, bound, RandomUtils.getRNG(42));
    assertEquals(33, vals.length);
    for (int i = 0; i < vals.length; i++) {
      assertTrue(vals[i] < bound);
      assertTrue(i == 0 || vals[i - 1] < vals[i]);
    }
    try {
      ArrayUtils.distinctLongs(11, 10, RandomUtils.getRNG(42));
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals(e.getMessage(), "argument bound (=10) needs to be lower or equal to n (=11)");
    }
    try {
      ArrayUtils.distinctLongs(11, 12, new Random());
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals(e.getMessage(), "Random implementation needs to be created by RandomUtils and inherit from RandomBase");
    }
  }

  @Test
  public void testInsert() {
    String[] ar1 = {"a", "b", "c", "d", "e"};
    String[] ar2 = {"1", "2", "3"};
    assertNull(ArrayUtils.insert(null, null, -1));
    assertSame(ar1, ArrayUtils.insert(ar1, null, -1));
    assertSame(ar2, ArrayUtils.insert(null, ar2, -1));
    assertArrayEquals(ArrayUtils.append(ar1, ar2), ArrayUtils.insert(ar1, ar2, ar1.length));
    assertArrayEquals(ArrayUtils.append(ar2, ar1), ArrayUtils.insert(ar1, ar2, 0));
    assertArrayEquals(new String[]{"a", "b", "1", "2", "3", "c", "d", "e"}, ArrayUtils.insert(ar1, ar2, 2));
  }
  
  @Test
  public void testMatrixVectorMultiplication() {
    testMatArrayMult(10, 10); // square matrix
    testMatArrayMult(10, 5);  // tall array
    testMatArrayMult(5, 10);  // wide array
  }
  
  public void testMatArrayMult(int mat1Row, int mat1Col) {
    double[][] mat1 = genRandomMatrix(mat1Row, mat1Col, 888);
    double[] arr = genRandomArray(mat1Col, 889);
    double[] result = new double[mat1Row];
    matrixVectorMult(result, mat1, arr);
    
    Matrix mat2 = new Matrix(mat1);
    Matrix vec = (new Matrix(new double[][]{arr})).transpose();
    Matrix resultM = mat2.times(vec);
    double[] matResult = resultM.transpose().getArray()[0];
    
    TestUtil.checkArrays(result, matResult, 1e-6);
  }
  
  @Test
  public void testOutputProductCum() {
    testOutProdCum(10, 10, 5, 8);
    testOutProdCum(20, 10, 30, 40);
    testOutProdCum(4, 20, 8, 9);
  }
  
  @Test
  public void testOuterProductSymCum() {
    testOutProdSymCum(20, 10, 5);
    testOutProdSymCum(15, 15, 10);
    testOutProdSymCum(25, 50, 20);
  }
  
  public void testOutProdCum(int arr1Len, int arr2Len, int numVec, int numLevel) {
    double[][][] arr1 = new double[numLevel][][];
    double[][][] arr2 = new double[numLevel][][];
    Matrix matResult = new Matrix(new double[arr1Len][arr2Len]);
    double[][] result = new double[arr1Len][arr2Len];
    
    for (int index = 0; index < numLevel; index++) {
      arr1[index] = genRandomMatrix(numVec, arr1Len, 888+index);
      arr2[index] = genRandomMatrix(numVec, arr2Len, 889+index);
      for (int index2 = 0; index2 < numVec; index2++)
        outerProductCum(result, arr1[index][index2], arr2[index][index2]);
      Matrix tempMatrix = (new Matrix(arr1[index])).transpose();
      Matrix tempMatrix2 = new Matrix(arr2[index]);
      Matrix outerMatrix = tempMatrix.times(tempMatrix2);
      matResult = matResult.plus(outerMatrix);
    }
    
    double[][] matResultT = matResult.getArray();
    TestUtil.checkDoubleArrays(result, matResultT, 1e-6);
  }
  
  public void testOutProdSymCum(int arr1Len, int numVec, int numLevel) {
    double[][][] arr1 = new double[numLevel][][];
    Matrix matResult = new Matrix(new double[arr1Len][arr1Len]);
    double[][] result = new double[arr1Len][arr1Len];
    
    for (int index = 0; index < numLevel; index++) {
      arr1[index] = genRandomMatrix(numVec, arr1Len, 888+index);
      for (int index2 = 0; index2 < numVec; index2++)
        outputProductSymCum(result, arr1[index][index2]);
      Matrix tempMatrix = (new Matrix(arr1[index])).transpose();
      Matrix outerMatrix = tempMatrix.times(tempMatrix.transpose());
      matResult = matResult.plus(outerMatrix);
    }
    
    double[][] matResultT = matResult.getArray();
    TestUtil.checkDoubleArrays(result, matResultT, 1e-6);
  }
  
  @Test
  public void testMatrixAddition() {
    testMatrixAdd(20, 20, 10); // square matrix
    testMatrixAdd(4, 20, 4);   // wide matrix
    testMatrixAdd(50, 8, 20);  // tall matrix
  }
  
  @Test
  public void testArraysAddition() {
    testArrayAdd(15, 10);
    testArrayAdd(8, 20);
    testArrayAdd(30, 10);
  }
  
  public void testArrayAdd(int arrLength, int numInstant) {
    double[][] arrayOfVec = genRandomMatrix(numInstant, arrLength, 888);
    double[] vecSum = new double[arrLength];
    Matrix matrixSum = new Matrix(new double[1][arrLength]);
    for (int index=0; index<numInstant; index++) {
      add(vecSum, arrayOfVec[index]);
      matrixSum = matrixSum.plus(new Matrix(new double[][]{arrayOfVec[index]}));
    }
    
    double[] matrixSumT = matrixSum.transpose().getArray()[0];
    TestUtil.checkArrays(vecSum, matrixSumT, 1e-6);
  }
  
  public void testMatrixAdd(int numRow, int numCol, int numInstant) {
    double[][][] matrix = new double[numInstant][][];
    Matrix[] matrixT = new Matrix[numInstant];
    Matrix matrixTSum = new Matrix(new double[numRow][numCol]);
    
    for (int index=0; index<numInstant; index++) {
      matrix[index] = genRandomMatrix(numRow, numCol, 888 + index);
      matrixT[index] = new Matrix(matrix[index]);
    }
    
    double[][] result = new double[numRow][numCol];
    for (int index=0; index<numInstant; index++) {
      add(result, matrix[index]);
      matrixTSum = matrixTSum.plus(matrixT[index]);
    }
    
    double[][] matrixSumT = matrixTSum.getArray();
    TestUtil.checkDoubleArrays(result, matrixSumT, 1e-6);
  }
  
  @Test
  public void testMatrixMultiplication() {
    testMatrixMult(10,10, 10);  // check square matrix
    testMatrixMult(10, 5, 20);  // wide matrix
    testMatrixMult(20, 10, 10); // skinny matrix
  }
  
  @Test
  public void testMatrixScalarMultiplication() {
    testMatScalarMult(10, 20);
    testMatScalarMult(8, 8);
    testMatScalarMult(25, 11);
  }
  
  @Test
  public void testMatrixMinus() {
    checkMatrixMinus(10, 10);
    checkMatrixMinus(15, 8);
    checkMatrixMinus(10, 20);
  }
  
  public void checkMatrixMinus(int numRow, int numCol) {
    double[][] mat1 = genRandomMatrix(numRow, numCol, 123);
    double[][] mat2 = genRandomMatrix(numRow, numCol, 321);
    Matrix matMat1 = new Matrix(mat1);
    double[][] matResult = matMat1.minus(new Matrix(mat2)).getArray();
    double[][] result = new double[numRow][numCol];
    minus(result, mat1, mat2);
    TestUtil.checkDoubleArrays(result, matResult, 1e-6);
  }
  
  @Test 
  public void testArrayMinus() {
    testArrMinus(10);
    testArrMinus(15);
    testArrMinus(20);
  }
  
  public void testArrMinus(int length) {
    double[] array1 = genRandomArray(length, 1234);
    double[] array2 = genRandomArray(length, 1235);
    double[] result = new double[length];
    minus(result, array1, array2);
    
    double[] matResult = new Matrix(new double[][]{array1}).minus(new Matrix(new double[][]{array2})).transpose().getArray()[0];
    TestUtil.checkArrays(result, matResult, 1e-6);
  }
  
  public void testMatScalarMult(int numRow, int numCol) {
    double[][] mat = genRandomMatrix(numRow, numCol, 123);
    double[][] result = new double[numRow][numCol];
    double scalar = genRandomMatrix(1, 1, 124)[0][0];
    mult(result, mat, scalar);
    double[][] matResult = new Matrix(mat).times(scalar).getArray();
    TestUtil.checkDoubleArrays(result, matResult, 1e-6);
  }

  public void testMatrixMult(int mat1Row, int mat1Col, int mat2Col) {
    double[][] mat1 = genRandomMatrix(mat1Row, mat1Col, 888);
    double[][] mat2 = genRandomMatrix(mat1Col, mat2Col, 889);
    double[][] result = new double[mat1Row][mat2Col];
    matrixMult(result, mat1, mat2);
    
    // generate result using Matrix toolbox;
    Matrix mat3 = new Matrix(mat1);
    Matrix mat4 = new Matrix(mat2);
    double[][] resultMatrix = mat3.times(mat4).getArray();
    
    // compare result
    TestUtil.checkDoubleArrays(result, resultMatrix, 1e-6);
  }
  
  @Test
  public void testTrace() {
    testMatrixTrace(1);
    testMatrixTrace(5);
    testMatrixTrace(10);
  }
  
  public void testMatrixTrace(int matLength) {
    double[][] mat = genRandomMatrix(matLength, matLength, 123);
    Matrix matMat = new Matrix(mat);
    double traceVal = trace(mat);
    double matTraceVal = matMat.trace();
    assertTrue(Math.abs(traceVal - matTraceVal) < 1e-6);
  }
  
  @Test
  public void testMaxMagArrayMatrix() {
    // check maximum magnitude for arrays
    checkCorrectMaxMag(10, 0);
    checkCorrectMaxMag(20, 19);
    checkCorrectMaxMag(1,0);
    // check maximum magnitude for matrices
    checkCorrectMaxMag(1,1,0,0);
    checkCorrectMaxMag(10, 1, 0, 0);
    checkCorrectMaxMag(10, 1, 9, 0);
    checkCorrectMaxMag(1, 15, 0, 0);
    checkCorrectMaxMag(1, 15, 0, 14);
    checkCorrectMaxMag(20, 15, 0, 0);
    checkCorrectMaxMag(20, 15, 19, 14);
    checkCorrectMaxMag(20, 15, 10, 7);
  }
  
  public void checkCorrectMaxMag(int numRow, int numCol, int maxValueRow, int maxValueCol) {
    double[][] mat = new double[numRow][];
    double maxValue = 0;
    double maxRowValue;
    for (int index=0; index<numRow; index++) {
      mat[index] = genRandomArray(numCol, 123+index);
      maxRowValue = maxMag(mat[index]);
      if (maxRowValue > maxValue)
        maxValue = maxRowValue;
    }
    maxValue += 20;
    mat[maxValueRow][maxValueCol] = maxValue;
    double maxValueFound = maxMag(mat);
    assertTrue(Math.abs(maxValue - maxValueFound) < 1e-12);
  }
  
  public void checkCorrectMaxMag(int arrayLength, int maxValueIndex) {
    double randValue = genRandomMatrix(1, 1, 123)[0][0];
    double maxValue = Math.abs(randValue) + 10;
    double[] arr = new double[arrayLength];
    Arrays.fill(arr, randValue);
    arr[maxValueIndex] = maxValue;
    double maxFound = maxMag(arr);
    assertTrue(Math.abs(maxValue-maxFound) < 1e-12);
  }
  
  @Test
  public void testFlattenArray() {
    checkFlattenArray(2, 1);
    checkFlattenArray(10, 20);
    checkFlattenArray(25, 3);
  }
  
  public void checkFlattenArray(int numLevel2, int numRandomCoeffs) {
    double[][] originalMat = genRandomMatrix(numLevel2, numRandomCoeffs, 123);
    double[] flattenArr = flattenArray(originalMat);
    int oneDArrayInd = 0;
    for (int level2Ind = 0; level2Ind < numLevel2; level2Ind++) {
      for (int coefInd = 0; coefInd < numRandomCoeffs; coefInd++) {
        assertEquals(originalMat[level2Ind][coefInd], flattenArr[oneDArrayInd], 1e-6);
        oneDArrayInd++;
      }
    }
  }
  
  @Test
  public void testExpandMat() {
    checkExpandMat(2, 1);
    checkExpandMat(10, 2);
    checkExpandMat(5, 20);
    checkExpandMat(13, 13);
  }
  
  public void checkExpandMat(int numLevel2, int numRandomCoeff) {
    double[][] tmat = genRandomMatrix(numRandomCoeff, numRandomCoeff, 123);
    double[][] tmatBig = expandMat(tmat, numLevel2);
    int bigRowInd;
    int bigColInd;
    int offset;
    for (int ind = 0; ind < numLevel2; ind++) {
      for (int ind2 = 0; ind2 < numRandomCoeff; ind2++) {
        offset = ind*numRandomCoeff;
        bigRowInd = offset + ind2;
        for (int index = 0; index < numRandomCoeff; index++) {
          bigColInd = offset+index;
          assertEquals(tmatBig[bigRowInd][bigColInd], tmat[ind2][index], 1e-6);
        }
      }
    }
  }
}
