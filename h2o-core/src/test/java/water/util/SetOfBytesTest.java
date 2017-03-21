package water.util;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for SetOfBytes
 * 
 * Created by vpatryshev on 1/13/17.
 */
public class SetOfBytesTest {

  @Test
  public void testContains() throws Exception {
    SetOfBytes sut = new SetOfBytes(new byte[]{0, 2, (byte)0x80, (byte)-1});
    assertTrue(sut.contains(0));
    assertTrue(sut.contains(2));
    assertTrue(sut.contains(0x80));
    assertTrue(sut.contains(0xff));
    assertFalse(sut.contains(-2));
    assertFalse(sut.contains(1));
    assertFalse(sut.contains(3));
    assertFalse(sut.contains(256));
    assertFalse(sut.contains(0xffff));
    assertFalse(sut.contains(-129));
    assertFalse(sut.contains(Integer.MIN_VALUE));
    assertFalse(sut.contains(Integer.MAX_VALUE));
    
    for (int i = 0; i < 256; i++) assertFalse(new SetOfBytes("").contains(i));
    
    SetOfBytes sut1 = new SetOfBytes("Hello World!");
    assertTrue(sut1.contains('!'));
    assertTrue(sut1.contains(' '));
    assertTrue(sut1.contains('o'));
    assertFalse(sut1.contains('O'));
    assertFalse(sut1.contains('0'));
    assertFalse(sut1.contains('h'));
  }

  @Test
  public void testEquals() throws Exception {
    SetOfBytes sut1 = new SetOfBytes("Hi");
    SetOfBytes sut2 = new SetOfBytes("High");
    assertTrue(sut1.equals(new SetOfBytes("iH")));
    assertFalse(sut1.equals(sut2));
    assertFalse(sut2.equals(sut1));
  }

  @Test
  public void testAsBytes() throws Exception {
    SetOfBytes sut = new SetOfBytes("Hello World!");
    assertEquals(sut, new SetOfBytes(sut.getBytes()));
    assertArrayEquals(" !HWdelor".getBytes(), sut.getBytes());
  }
}