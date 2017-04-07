package water.util.fp;

import org.junit.Test;

import java.util.Collections;
import java.util.Iterator;

import static org.junit.Assert.*;
import static water.util.fp.FP.*;

/**
 * Tests set for FP library
 */
public class FPTest {

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
    
    Option<?> sute = Option(None);
  }

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