package water.udf.fp;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for Functions.
 * 
 * Created by vpatryshev on 12/13/16.
 */
public class FunctionsTest {

  @Test
  public void testCompose() throws Exception {
    Function<Long, String> g = Functions.onList(Arrays.asList("null", "eins", "zwei", "drei"));
    Function<Integer, Long> f = new Function<Integer, Long>() {
      @Override public Long apply(Integer i) { return i-1L; }
    };
    Function<Integer, Long> f1 = new Function<Integer, Long>() {
      @Override public Long apply(Integer i) { return i-2L; }
    };
    
    Function<Integer, String> h = Functions.compose(g, f);
    Function<Integer, String> h1 = Functions.compose(g, f1);
    
    assertFalse(h.equals(h1));
    assertTrue(h.equals(Functions.compose(g, f)));

    assertEquals("zwei", h1.apply(4));
  }

  @Test
  public void testIdentity() throws Exception {
    Function<Byte, Byte> b1 = Functions.identity();
    assertEquals((byte)47, b1.apply((byte)47).byteValue());
  }

  @Test
  public void testOnList() throws Exception {
    Function<Long, String> f = Functions.onList(Arrays.asList("null", "eins", "zwei", "drei"));
    assertEquals("null", f.apply(0L));
    assertEquals("eins", f.apply(1L));
    assertEquals("zwei", f.apply(2L));
    assertEquals("drei", f.apply(3L));
  }

  @Test
  public void testMap() throws Exception {
    Function<Integer, String> f = new Function<Integer, String>() {
      @Override public String apply(Integer i) { return "<<" + i + ">>"; }
    };
    
    assertFalse(Functions.map(Collections.<Integer>emptyList(), f).iterator().hasNext());
    assertEquals(Arrays.asList("<<2>>","<<3>>","<<5>>","<<7>>"), Functions.map(Arrays.asList(2,3,5,7), f));
  }

  @Test
  public void testConstant() throws Exception {
    Function<String, Integer> c = Functions.<String, Integer>constant(1001590);
    assertEquals(1001590, c.apply("not my number").intValue());
  }

  @Test
  public void testSplitBy() throws Exception {
    assertEquals(Arrays.asList(""), Functions.splitBy(":").apply(""));
    assertTrue(Functions.splitBy(":").apply(":").isEmpty());
    assertEquals(Arrays.asList(" "), Functions.splitBy(":").apply(" :"));
    assertEquals(Arrays.asList("", " "), Functions.splitBy(":").apply(": "));
  }

  @Test
  public void testHashCode() throws Exception {
    assertEquals(0, Functions.hashCode(null));
    assertEquals("querty".hashCode(), Functions.hashCode("querty"));
  }

  @Test
  public void testEqual() throws Exception {
    assertTrue(Functions.equal(null, null));
    assertFalse(Functions.equal(42, null));
    assertFalse(Functions.equal(null, 42));
    Double x = Math.sin(7);
    assertTrue(Functions.equal("ab" + x, 'a' + "b" + x));
  }
}