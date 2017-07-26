package water.parser;

import org.junit.Test;

import static org.junit.Assert.*;
import static water.parser.PackedDomains.*;
/**
 * Test suite for PackedDomains
 * 
 * Created by vpatryshev on 4/12/17.
 */
public class PackedDomainsTest {

  @Test
  public void testSizeOf() throws Exception {
    assertEquals(12345, sizeOf(new byte[]{57,48,0,0,5}));
  }

  @Test
  public void testAsArrayOfStrings() throws Exception {
    final byte[] packed = pack("", "abc", "∞", "", "X");
    final String[] actuals = unpackToStrings(packed);
    assertArrayEquals(new String[]{"", "abc", "∞", "", "X"},
        actuals);
  }

  @Test
  public void testPack() throws Exception {
    byte[] packed = pack("", "abc", "∞", "", "X");
    assertArrayEquals(new byte[]{5,0,0,0,0,97,98,99,0,(byte)0xe2,(byte)0x88,(byte)0x9e,0,0,88,0}, packed);
  }

  @Test
  public void testPack1() throws Exception {
    BufferedString bs = new BufferedString("efabcxy".getBytes(), 2, 3);
    assertEquals("abc", bs.toString());
    byte[] packed = pack(new BufferedString[] {
        new BufferedString(""),
        bs,
        new BufferedString("∞"),
        new BufferedString(""),
        new BufferedString("X")});
    
    assertArrayEquals(pack("", "abc", "∞", "", "X"), packed);
  }

  @Test
  public void testPackStringWithOffset() {
    BufferedString bs = new BufferedString("LeftMiddleRight".getBytes(), "Left".length(), "Middle".length());
    byte[] packed = pack(new BufferedString[]{bs});
    String[] unpacked = unpackToStrings(packed);
    assertArrayEquals(new String[]{"Middle"}, unpacked);
  }

  byte[] empty = pack();
  byte[] first = pack("", "ANNIHILATION", "Zoo");
  byte[] second = pack("aardvark", "absolute", "neo", "x", "xyzzy");
  byte[] third = pack("", "abacus", "neolution", "x", "zambezi");
  String[] allWords = new String[] {
      "",
      "ANNIHILATION",
      "Zoo",
      "aardvark",
      "abacus",
      "absolute",
      "neo",
      "neolution",
      "x",
      "xyzzy",
      "zambezi"
  };

  @Test
  public void testMergeEmpties() throws Exception {
    assertArrayEquals(empty, merge(empty, empty));
    assertArrayEquals(second, merge(empty, second));
    assertArrayEquals(second, merge(second, empty));
  }

  @Test
  public void testMerge12() throws Exception {
    final byte[] merged = merge(first, second);
    assertArrayEquals(new String[] {"", "ANNIHILATION", "Zoo", "aardvark", "absolute", "neo", "x", "xyzzy"}, unpackToStrings(merged));
  }

  @Test
  public void testMerge23() throws Exception {
    final byte[] merged = merge(second, third);
    assertArrayEquals(new String[] {"", "aardvark", "abacus", "absolute", "neo", "neolution", "x", "xyzzy", "zambezi"}, unpackToStrings(merged));
  }

  @Test
  public void testMerge3() throws Exception {
    assertArrayEquals(allWords, unpackToStrings(merge(third, merge(first, second))));
    assertArrayEquals(allWords, unpackToStrings(merge(first, merge(second, third))));
  }

  @Test
  public void testMergeIdempotent() throws Exception {
    assertArrayEquals(first, merge(first, first));
    assertArrayEquals(second, merge(second, second));
    assertArrayEquals(third, merge(third, third));
  }
}