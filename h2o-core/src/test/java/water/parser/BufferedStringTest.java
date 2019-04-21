package water.parser;

import org.junit.Test;
import water.AutoBuffer;

import java.util.Arrays;

import static org.junit.Assert.*;

public class BufferedStringTest {

  @Test
  public void testWrite_impl() throws Exception {
    final String source = "this is not a string";
    BufferedString sut = new BufferedString(source);
    assertArrayEquals(source.getBytes(), sut.getBuffer());
    AutoBuffer ab = new AutoBuffer();
    sut.write_impl(ab);
    final byte[] expected = ("\u0015" + source).getBytes();
    final byte[] actual = ab.buf();
    assertArrayEquals(expected, actual);
  }

  @Test
  public void testRead_impl() throws Exception {
    final String source = "this is not a string";
    BufferedString sut1 = new BufferedString(source);
    AutoBuffer ab = new AutoBuffer();
    sut1.write_impl(ab);
    ab.flipForReading();
    BufferedString sut2 = new BufferedString();
    sut2.read_impl(ab);
    assertEquals(sut1, sut2);
  }

  @Test
  public void testCompareTo() throws Exception {
    final String source = "this is not a string";
    BufferedString sut1 = new BufferedString(source);
    assertEquals(0, sut1.compareTo(new BufferedString(source)));
    assertEquals(2, sut1.compareTo(new BufferedString("this is not a stri")));
  }

  @Test
  public void testAddChar() throws Exception {
    final String source = "abc";
    BufferedString sut1 = new BufferedString(source.getBytes(), 0, 2);
    assertEquals(2, sut1.length());
    sut1.addChar();
    assertEquals(3, sut1.length());
    String actual = sut1.toString();
    assertEquals(source, actual);
    byte[] bytes = sut1.getBuffer();
    assertArrayEquals(source.getBytes(), bytes);
  }

  @SuppressWarnings("EqualsBetweenInconvertibleTypes")
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
  public void testEqualsAsciiString() throws Exception {
    BufferedString sut1 = new BufferedString("abc");
    assertFalse(sut1.equalsAsciiString(null));
    assertTrue(sut1.equalsAsciiString("abc"));
    assertFalse(sut1.equalsAsciiString("ab"));
    assertFalse(sut1.equalsAsciiString("abd"));
    assertFalse(sut1.equalsAsciiString("abcd"));
    assertFalse(sut1.equalsAsciiString("abC"));
    assertFalse(sut1.equalsAsciiString("ab\u0441")); // this is Russian 'c' here
    assertFalse(sut1.equalsAsciiString("ab"));
    BufferedString sut2 = new BufferedString("");
    assertTrue(sut2.equalsAsciiString(""));
    assertFalse(sut1.equalsAsciiString(null));
    assertFalse(sut2.equalsAsciiString("a"));
    BufferedString sut3 = new BufferedString("a\u0100b");
    assertFalse(sut3.equalsAsciiString("a\u0100b"));
  }

  @Test
  public void testGetBuffer() throws Exception {
    final String source = "not a string\u00f0";
    BufferedString sut = new BufferedString(source);
    final byte[] expected = source.getBytes("UTF8");
    final byte[] actual = sut.getBuffer();
    assertArrayEquals("Failed. expected " + Arrays.toString(expected) + 
                      ", got " + Arrays.toString(actual), 
        expected, actual);
  }

}