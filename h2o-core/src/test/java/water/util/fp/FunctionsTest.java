package water.util.fp;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import static water.util.fp.Functions.*;

import static org.junit.Assert.*;

/**
 * Tests for Functions.
 * 
 * Created by vpatryshev on 12/13/16.
 */
public class FunctionsTest {

  @Test
  public void testCompose() throws Exception {
    Function<Long, String> g = onList(Arrays.asList("null", "eins", "zwei", "drei"));
    Function<Integer, Long> f = new Function<Integer, Long>() {
      @Override public Long apply(Integer i) { return i-1L; }
    };
    Function<Integer, Long> f1 = new Function<Integer, Long>() {
      @Override public Long apply(Integer i) { return i-2L; }
    };
    
    Function<Integer, String> h = compose(g, f);
    Function<Integer, String> h1 = compose(g, f1);
    
    assertFalse(h.equals(h1));
    assertTrue(h.equals(compose(g, f)));

    assertEquals("zwei", h1.apply(4));
  }

  @Test
  public void testIdentity() throws Exception {
    Function<Byte, Byte> b1 = identity();
    assertEquals((byte)47, b1.apply((byte)47).byteValue());
  }

  @Test
  public void testOnList() throws Exception {
    Function<Long, String> f = onList(Arrays.asList("null", "eins", "zwei", "drei"));
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
    
    assertFalse(map(Collections.<Integer>emptyList(), f).iterator().hasNext());
    assertEquals(Arrays.asList("<<2>>","<<3>>","<<5>>","<<7>>"), map(Arrays.asList(2,3,5,7), f));
  }

  @Test
  public void testConstant() throws Exception {
    Function<String, Integer> c = Functions.<String, Integer>constant(1001590);
    assertEquals(1001590, c.apply("not my number").intValue());
  }

  @Test
  public void testSplitBy() throws Exception {
    assertEquals(Arrays.asList(""), splitBy(":").apply(""));
    assertTrue(splitBy(":").apply(":").isEmpty());
    assertEquals(Arrays.asList(" "), splitBy(":").apply(" :"));
    assertEquals(Arrays.asList("", " "), splitBy(":").apply(": "));
  }
  
  @Test
  public void testAUC() throws Exception {
    Function<Integer, Double> c = constant(5.);
    Function<Integer, Double> x = new Function<Integer, Double>() {
      public Double apply(Integer i) { return i * 0.1; }
    };
    assertEquals(1.0, integrate(x, c, 0, 2), 0.01);
    assertEquals(10.0, integrate(x, c, 0, 20), 0.01);
    assertEquals(2.0, integrate(x, x, 0, 20), 0.01);

    Function<Integer, Double> sin = new Function<Integer, Double>() {
      public Double apply(Integer i) { return Math.sin((314-i) * 0.01); }
    };
    Function<Integer, Double> cos = new Function<Integer, Double>() {
      public Double apply(Integer i) { return Math.cos((314-i) * 0.01); }
    };
    final double actual = integrate(cos, sin, 0, 314);
    assertEquals(Math.PI * 0.5, actual, 0.02);
    assertEquals(Math.PI, integrate(cos, sin, 0, 628), 0.01);
  }
}