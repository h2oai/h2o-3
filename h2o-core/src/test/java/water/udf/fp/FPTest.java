package water.udf.fp;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;
import static water.udf.fp.FP.*;

/**
 * Tests set for FP library
 */
public class FPTest {

  @Test public void testEquals() {
    assertTrue(equal(null, null));
    assertFalse(equal(null, 1));
    assertFalse(equal("", null));
    assertFalse(equal("1", 1));
    assertTrue(equal(1234567, Integer.parseInt("1234567")));
  }

  @Test public void testHashCode() {
    assertEquals(0, FP.hashCode(null));
    assertEquals(1, FP.hashCode(1));
    final List<String> sut = new ArrayList<String>();
    sut.add(null);
    int hc = sut.hashCode();
    final int hashCode = FP.hashCode(sut);
    assertEquals(31, hashCode);
  }
  
  @Test
  public void testSome() throws Exception {
    Some<String> sut = new Some<>("Hello Junit");
    assertFalse(sut.isEmpty());
    assertEquals("Hello Junit", sut.get());
    assertEquals("Hello Junit", sut.get());
    Iterator<String> sui1 = sut.iterator();
    Iterator<String> sui2 = sut.iterator();
    assertTrue(sui1.hasNext());
    assertTrue(sui1.hasNext());
    assertTrue(sui2.hasNext());
    assertTrue(sui2.hasNext());
    assertEquals("Hello Junit", sui1.next());
    assertFalse(sui1.hasNext());
    assertFalse(sui1.hasNext());
    assertTrue(sui2.hasNext());
    assertTrue(sui2.hasNext());
    assertFalse(sut.isEmpty());
    assertTrue(sut.nonEmpty());
    assertEquals("Some(3.141592653589793)", Some(Math.PI).toString());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testOption() throws Exception {
    Option<String> sut1 = Some("Hello Junit");
    
//  should not compile
//  sut.get();

    Iterator<String> sui1 = sut1.iterator();
    assertTrue(sui1.hasNext());
    assertEquals("Hello Junit", sui1.next());
    assertFalse(sui1.hasNext());
    assertFalse(sut1.isEmpty());
    assertTrue(sut1.nonEmpty());
    
    assertTrue(None.isEmpty());
    assertFalse(None.nonEmpty());
    assertFalse(None.iterator().hasNext());
    assertEquals("None", None.toString());
    assertEquals(-1, None.hashCode());
    Option<String> sut2 = Option("Hello Junit");
    assertEquals(sut1, sut2);
    assertNotEquals(Option("Hello JuniT"), sut1);
    
    Option<String> sut3 = Option(null);
    assertEquals(None, sut3);

    Option<Integer> sut4 = sut1.flatMap(
        new Function<String, Option<Integer>>() { 
          public Option<Integer> apply(String s) { 
            return Option(s.length() - 1); } });
    
    assertEquals(Option(10), sut4);
    
    Option<Integer> sutem = ((Option<Object>)None).flatMap(
        new Function<Object, Option<Integer>>() {
          public Option<Integer> apply(Object x) {
            return Option(x.toString().length() - 1); } });
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testFlatten() throws Exception {
    Option<String> sut1 = Some("Hello Junit");
    Option<Option<String>> sutopt = Option(sut1);
    assertEquals(sut1, flatten(sutopt));
    
    assertEquals(None, flatten(Option(None)));
    assertEquals(None, flatten((Option<Option<Object>>)None));
  }

  @Test
  public void testHeadOption() throws Exception {
    assertEquals(None, headOption(Collections.emptyList()));
    final Option<Double> expected = Some(Math.PI);
    final Option<Double> expected2 = Some(Math.PI);
    assertTrue(expected.equals(expected2));
    assertEquals(expected, expected2);
    assertEquals(expected, headOption(Collections.nCopies(7, Math.PI)));
    assertEquals(expected, headOption(Collections.nCopies(1, Math.PI)));
    assertEquals(None, headOption(Collections.nCopies(0, Math.PI)));
  }

  @Test
  public void testHeadOption1() throws Exception {
    assertEquals(Some(Math.PI), headOption(Collections.nCopies(7, Math.PI).iterator()));
    assertEquals(Some(Math.PI), headOption(Collections.nCopies(1, Math.PI).iterator()));
    assertEquals(None, headOption(Collections.nCopies(0, Math.PI).iterator()));
  }
}