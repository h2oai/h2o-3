package water.util;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test for BytesStats class
 * 
 * Created by vpatryshev on 3/27/17.
 */
public class BytesStatsTest {
  @Test public void testOnEmpty() {
    BytesStats sut = new BytesStats(StringUtils.bytesOf(""));
    assertEquals(-1, sut.averageWidth());
    assertEquals(0, sut.numChars);
    assertEquals(-1, sut.maxWidth);
    assertEquals(0, sut.numLines);
  }

  @Test public void testOneLine() {
    BytesStats sut = new BytesStats(StringUtils.bytesOf("hello world"));
    assertEquals(-1, sut.averageWidth());
    assertEquals(0, sut.numChars);
    assertEquals(-1, sut.maxWidth);
    assertEquals(0, sut.numLines);
  }

  @Test public void testOneLineNL() {
    BytesStats sut = new BytesStats(StringUtils.bytesOf("hello world\n"));
    assertEquals(11, sut.numChars);
    assertEquals(11, sut.maxWidth);
    assertEquals(1, sut.numLines);
    assertEquals(11, sut.averageWidth());
  }

  @Test public void testGeneric() {
    BytesStats sut = new BytesStats(StringUtils.bytesOf("hello world\n\nHere I come\n:)"));
    assertEquals(22, sut.numChars);
    assertEquals(11, sut.maxWidth);
    assertEquals(3, sut.numLines);
    assertEquals(7, sut.averageWidth());
  }

  @Test public void testNlInTheEnd() {
    BytesStats sut = new BytesStats(StringUtils.bytesOf("hello world\n\nHere I come\n:)\n"));
    assertEquals(24, sut.numChars);
    assertEquals(11, sut.maxWidth);
    assertEquals(4, sut.numLines);
    assertEquals(6, sut.averageWidth());
  }
}