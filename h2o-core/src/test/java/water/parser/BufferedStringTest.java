package water.parser;

import org.junit.Ignore;
import org.junit.Test;
import water.AutoBuffer;
import water.Paxos;
import water.TestUtil;

import java.util.Arrays;

import static java.util.Arrays.*;

import static org.junit.Assert.*;

/**
 * This is mostly a skeleton of the tests, feel free to implement the cases
 * Created by vpatryshev on 1/17/17.
 */
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

  @Ignore("This test fails currently - bugs in AutoBuffer, probably")
  @Test
  public void testRead_impl() throws Exception {
    final String source = "this is not a string";
    BufferedString sut1 = new BufferedString(source);
    AutoBuffer ab = new AutoBuffer();
    sut1.write_impl(ab);
    ab.bufClose();
    BufferedString sut2 = new BufferedString("what?");
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

  @Test @Ignore // this is a stub
  public void testHashCode() throws Exception {

  }

  @Test
  public void testAddChar() throws Exception {
    final String source = "abc";
    BufferedString sut1 = new BufferedString(source);
    assertEquals(3, sut1.length());
    sut1.addChar();
    assertEquals(4, sut1.length());
// TODO(vlad): fix the crash in the next line    
//    String actual = sut1.bytesToString();
//    assertEquals(source, actual);
    // TODO(vlad): fix it; we don't need the cloud
//    Paxos._commonKnowledge = true; // this is totally stupid; thank you Cliff for the fun
// TODO(vlad): fix the crash in the next line    
//    byte[] bytes = sut1.asBytes();
//    assertArrayEquals(source.getBytes(), bytes);
  }

  @Test @Ignore // this is a stub
  public void testAddBuff() throws Exception {

  }

  @Test @Ignore // this is a stub
  public void testToString() throws Exception {

  }

  @Test @Ignore // this is a stub
  public void testBytesToString() throws Exception {

  }

  @Test @Ignore // this is a stub
  public void testToString1() throws Exception {

  }

  @Test @Ignore // this is a stub
  public void testToBufferedString() throws Exception {

  }

  @Test @Ignore // this is a stub
  public void testSet() throws Exception {

  }

  @Test @Ignore // this is a stub
  public void testSet1() throws Exception {

  }

  @Test @Ignore // this is a stub
  public void testSet2() throws Exception {

  }

  @Test @Ignore // this is a stub
  public void testSetOff() throws Exception {

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
  public void testSameString() throws Exception {
    BufferedString sut1 = new BufferedString("abc");
    assertFalse(sut1.sameString(null));
    assertTrue(sut1.sameString("abc"));
    assertFalse(sut1.sameString("ab"));
    assertFalse(sut1.sameString("abd"));
    assertFalse(sut1.sameString("abcd"));
    assertFalse(sut1.sameString("abC"));
    assertFalse(sut1.sameString("ab\u0441")); // this is Russian 'c' here
    assertFalse(sut1.sameString("ab"));
    BufferedString sut2 = new BufferedString("");
    assertTrue(sut2.sameString(""));
    assertFalse(sut1.sameString(null));
    assertFalse(sut2.sameString("a"));
    BufferedString sut3 = new BufferedString("a\u0100b");
    assertFalse(sut3.sameString("a\u0100b"));
  }

  @Ignore("This test is failing because the method is wrong and must be fixed, see PUBDEV-3957")
  @Test public void testSameStringUTF8() throws Exception {
    BufferedString sut4 = new BufferedString("a\u0088b");
    assertTrue(sut4.sameString("a\u0088b"));
  }

  @Test public void testIsOneOf() throws Exception {
    BufferedString sut = new BufferedString("abc");
    assertFalse(sut.isOneOf(null));
    assertFalse(sut.isOneOf(new String[]{}));
    assertFalse(sut.isOneOf(new String[]{"", "a", "b", "ab", "bc", "abcd", "xabc"}));
    assertTrue(sut.isOneOf(new String[]{"abc", "a", "b", "ab", "bc", "abcd"}));
    assertTrue(sut.isOneOf(new String[]{"a", "b", "ab", "bc", "abcd", "abc"}));
    assertTrue(sut.isOneOf(new String[]{"", "b", "ab", "bc", "abcd", "abc", "whateva"}));
    assertTrue(sut.isOneOf(new String[]{"", null, "ab", "bc", "abcd", "abc", "whateva"}));
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

  @Test @Ignore // this is a stub
  public void testGetOffset() throws Exception {

  }

  @Test @Ignore // this is a stub
  public void testLength() throws Exception {

  }

  @Test @Ignore // this is a stub
  public void testGetNumericType() throws Exception {

  }
}