package water.util;

import hex.CreateFrame;
import hex.ToEigenVec;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.hamcrest.CoreMatchers;
import water.DKV;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import static org.junit.Assert.*;
import static water.util.ArrayUtils.*;

/**
 * Test FrameUtils interface.
 */
public class ArrayUtilsTest extends TestUtil {
  @BeforeClass
  static public void setup() {  stall_till_cloudsize(1); }

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
}
