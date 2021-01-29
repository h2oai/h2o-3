package water.util;

import org.junit.Assert;
import org.junit.Test;

import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.Assert.*;
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
  public void testSortIndices() {
    Random randObj = new Random(12345);
    int arrayLen = 100;
    int[] indices = new int[arrayLen];
    double[] values = new double[arrayLen];
    for (int index = 0; index < arrayLen; index++)  // generate data array
      values[index] = randObj.nextDouble();
    
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

}
