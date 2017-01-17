package water.parser;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * This is mostly a skeleton of the tests, feel free to implement the cases
 * Created by vpatryshev on 1/17/17.
 */
public class BufferedStringTest {

  @Test
  public void testWrite_impl() throws Exception {

  }

  @Test
  public void testRead_impl() throws Exception {

  }

  @Test
  public void testCompareTo() throws Exception {

  }

  @Test
  public void testHashCode() throws Exception {

  }

  @Test
  public void testAddChar() throws Exception {

  }

  @Test
  public void testAddBuff() throws Exception {

  }

  @Test
  public void testToString() throws Exception {

  }

  @Test
  public void testBytesToString() throws Exception {

  }

  @Test
  public void testToString1() throws Exception {

  }

  @Test
  public void testToBufferedString() throws Exception {

  }

  @Test
  public void testSet() throws Exception {

  }

  @Test
  public void testSet1() throws Exception {

  }

  @Test
  public void testSet2() throws Exception {

  }

  @Test
  public void testSetOff() throws Exception {

  }

  @Test
  public void testEquals() throws Exception {
    BufferedString sut = new BufferedString("abc");
    assertEquals(sut, sut);
    assertEquals(sut, new BufferedString("abc"));
    assertFalse(sut.equals("abc"));
    assertFalse(sut.equals(new BufferedString("abcd")));
    assertFalse(sut.equals(new BufferedString("ABCD")));
    assertFalse(sut.equals(new BufferedString(" abc")));
    assertFalse(sut.equals(new BufferedString("abc ")));
    assertFalse(sut.equals(new BufferedString("abc\0")));
    assertFalse(sut.equals(new BufferedString("ab")));
    assertFalse(sut.equals(new BufferedString("")));
    assertFalse(new BufferedString("").equals(sut));
  }

  @Test
  public void testGetBuffer() throws Exception {

  }

  @Test
  public void testGetOffset() throws Exception {

  }

  @Test
  public void testLength() throws Exception {

  }

  @Test
  public void testGetNumericType() throws Exception {

  }
}