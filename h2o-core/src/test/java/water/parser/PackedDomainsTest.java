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
    assertArrayEquals(new byte[]{
            5, 0, 0, 0, 0, 0, 0, 0, 3, 0, 0, 0, 97, 98, 99, 3, 0, 0, 0, -30, -120, -98, 0, 0, 0, 0, 1, 0, 0, 0, 88},
            packed);
  }

  @Test
  public void testPack1() throws Exception {
    BufferedString bs = new BufferedString("efabc");
    bs.addBuff("def".getBytes());
    bs.setOff(2);
    bs.setLen(3);
    byte[] packed = PackedDomains.pack(new BufferedString[] {
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
    byte[] packed = PackedDomains.pack(new BufferedString[]{bs});
    String[] unpacked = unpackToStrings(packed);
    assertArrayEquals(new String[]{"Middle"}, unpacked);
  }

  private String[] empty = new String[0];
  private String[] first = new String[]{"", "ANNIHILATION", "Zoo"};
  private String[] second = new String[]{"aardvark", "absolute", "neo", "x", "xyzzy"};
  private String[] third = new String[]{"", "abacus", "neolution", "x", "zambezi"};
  private String[] allWords = new String[] {
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
    assertArrayEquals(pack(empty), merge(empty, empty));
    assertArrayEquals(pack(second), merge(empty, second));
    assertArrayEquals(pack(second), merge(second, empty));
  }

  @Test
  public void testCalcMergedSize12() throws Exception {
    int size = calcMergedSize(first, second);
    assertEquals(pack("", "ANNIHILATION", "Zoo", "aardvark", "absolute", "neo", "x", "xyzzy").length, size);
  }

  @Test
  public void testMerge12() throws Exception {
    final byte[] merged = merge(first, second);
    assertArrayEquals(new String[] {"", "ANNIHILATION", "Zoo", "aardvark", "absolute", "neo", "x", "xyzzy"}, unpackToStrings(merged));
  }

  @Test
  public void testCalcMergedSize23() throws Exception {
    int size = calcMergedSize(second, third);
    assertEquals(pack("", "aardvark", "abacus", "absolute", "neo", "neolution", "x", "xyzzy", "zambezi").length, size);
  }

  @Test
  public void testMerge23() throws Exception {
    final byte[] merged = merge(second, third);
    assertArrayEquals(new String[] {"", "aardvark", "abacus", "absolute", "neo", "neolution", "x", "xyzzy", "zambezi"}, unpackToStrings(merged));
  }

  @Test
  public void testMerge3() throws Exception {
    assertArrayEquals(allWords, unpackToStrings(PackedDomains.merge(pack(third), merge(first, second))));
    assertArrayEquals(allWords, unpackToStrings(PackedDomains.merge(pack(first), merge(second, third))));
  }

  @Test
  public void testMergeIdempotent() throws Exception {
    assertArrayEquals(pack(first), merge(first, first));
    assertArrayEquals(pack(second), merge(second, second));
    assertArrayEquals(pack(third), merge(third, third));
  }

  private static byte[] merge(String[] s1, String[] s2) {
    return PackedDomains.merge(pack(s1), pack(s2));
  }

  private static int calcMergedSize(String[] s1, String[] s2) {
    return PackedDomains.calcMergedSize(pack(s1), pack(s2));
  }

  private static byte[] pack(String... source) {
    BufferedString[] bss = new BufferedString[source.length];
    for (int i = 0; i < source.length; i++) {
      bss[i] = new BufferedString(source[i]);
    }
    return PackedDomains.pack(bss);
  }

}