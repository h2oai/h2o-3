package ai.h2o.cascade.core;

import ai.h2o.cascade.Cascade;
import ai.h2o.cascade.CascadeSession;
import org.junit.Before;
import org.junit.Test;
import water.TestUtil;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 */
public class SliceListTest extends TestUtil {
  private Scope scope;

  @Before
  public void setUp() throws Exception {
    stall_till_cloudsize(1);
    scope = new CascadeSession("test").globalScope();
  }


  @Test
  public void testBasic() {
    // No operations are expected to succeed on a slicelist made from no-arg constructor
    new SliceList();

    SliceList sl1 = new SliceList(33);
    assertTrue(sl1.isSlice());
    assertTrue(sl1.isDense());
    assertTrue(sl1.isSorted());
    assertEquals(Val.Type.SLICE, sl1.type());
    assertEquals(1, sl1.size());
    assertEquals(33, sl1.first());
    assertArrayEquals(ari(33), sl1.expand4());
    assertArrayEquals(ar(33), sl1.expand8());
    assertEquals("<33>", sl1.toString());

    SliceList sl2 = new SliceList(6, 17);
    assertTrue(sl2.isDense());
    assertTrue(sl2.isSorted());
    assertEquals(11, sl2.size());
    assertEquals(6, sl2.first());
    assertArrayEquals(ar(6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16), sl2.expand8());
    assertEquals("<6:11>", sl2.toString());

    SliceList sl3 = new SliceList(ar(-4, -3, -2, -1, 1100));
    assertFalse(sl3.isDense());
    assertTrue(sl3.isSorted());
    assertEquals(5, sl3.size());
    assertEquals(-4, sl3.first());
    assertArrayEquals(ar(-4, -3, -2, -1, 1100), sl3.expand8());
    assertEquals("<-4 -3 -2 -1 1100>", sl3.toString());

    @SuppressWarnings("ExternalizableWithoutPublicNoArgConstructor")
    SliceList sl4 = new SliceList(ar("foo", "!!!"), makeTestFrame());
    assertFalse(sl4.isDense());
    assertTrue(sl4.isSorted());
    assertArrayEquals(ar(1, 4), sl4.expand8());
    assertEquals("<1 4>", sl4.toString());

    SliceList sl5 = new SliceList(Arrays.asList(3L, 5L, 7L), Arrays.asList(2L, 2L, 2L), Arrays.asList(1L, 1L, 1L));
    assertTrue(sl5.isDense());
    assertTrue(sl5.isSorted());
    assertEquals(6, sl5.size());
    assertEquals("<3:2 5:2 7:2>", sl5.toString());

    SliceList sl6 = new SliceList(Arrays.asList(0L, 5L), Arrays.asList(2L, 3L), Arrays.asList(2L, -1L));
    assertFalse(sl6.isDense());
    assertFalse(sl6.isSorted());
    assertEquals(5, sl6.size());
    assertArrayEquals(ar(0, 2, 5, 4, 3), sl6.expand8());
    assertEquals("<0:2:2 5:3:-1>", sl6.toString());
  }


  @Test
  public void testIter() throws Exception {
    assertIter("<0:5>", ar(0, 1, 2, 3, 4));
    assertIter("<3:3:3>", ar(3, 6, 9));
    assertIter("<3:3:-1>", ar(3, 2, 1));
    assertIter("<3:3:-3>", ar(3, 0, -3));
    assertIter("<9:4:0>", ar(9, 9, 9, 9));
    assertIter("<100:1>", ar(100));
    assertIter("<100:2>", ar(100, 101));
    assertIter("<100:3:-10>", ar(100, 90, 80));
    assertIter("<1 2 3 15>", ar(1, 2, 3, 15));
    assertIter("<1 2:3:0 3:6:-1, 17, 4>", ar(1, 2, 2, 2, 3, 2, 1, 0, -1, -2, 17, 4));
    assertIter("<0:5 7:3:0 4:2:-2 0:2:0 8>", ar(0, 1, 2, 3, 4, 7, 7, 7, 4, 2, 0, 0, 8));

    assertIter("new SliceList(5)", new SliceList(5), ar(5));
    assertIter("new SliceList(3, 7)", new SliceList(3, 7), ar(3, 4, 5, 6));
    assertIter("new SliceList({5, 1, -3, 2, 7})", new SliceList(ar(5, 1, -3, 2, 7)), ar(5, 1, -3, 2, 7));

    SliceList sl1 = new SliceList(1, 6);
    SliceList.Iterator it1 = sl1.iter();
    it1.remove();  // should be noop
    it1.remove();
    long sum = 0;
    while (it1.hasNext()) {
      sum += it1.next();
    }
    assertEquals(1 + 2 + 3 + 4 + 5, sum);
    it1.reset();
    while (it1.hasNext()) {
      sum += it1.next();
    }
    assertEquals((1 + 2 + 3 + 4 + 5) * 2, sum);

    SliceList sl2 = new SliceList(ar(1, 2, 3, 4));
    SliceList.Iterator it2 = sl2.iter();
    it2.remove();  // should be noop
    it2.remove();
    sum = 0;
    while (it2.hasNext()) sum += it2.next();
    assertEquals(10, sum);
    it2.reset();
    while (it2.hasNext()) sum += it2.next();
    assertEquals(20, sum);
  }


  @Test
  public void testNormalizeR() {
    SliceList sl1 = new SliceList(7).normalizeR(10);
    assertArrayEquals(ar(7), sl1.expand8());

    SliceList sl2 = getSliceList("<2:3:2 4:5:-1>").normalizeR(10);
    assertArrayEquals(ar(2, 4, 6, 4, 3, 2, 1, 0), sl2.expand8());

    SliceList sl3 = new SliceList(-1).normalizeR(5);
    assertEquals("<1:4>", sl3.toString());
    assertArrayEquals(ar(1, 2, 3, 4), sl3.expand8());

    SliceList sl4 = new SliceList(-3).normalizeR(6);
    assertEquals("<0:2 3:3>", sl4.toString());
    assertArrayEquals(ar(0, 1, 3, 4, 5), sl4.expand8());

    SliceList sl5 = new SliceList(-5).normalizeR(5);
    assertEquals("<0:4>", sl5.toString());
    assertArrayEquals(ar(0, 1, 2, 3), sl5.expand8());

    SliceList sl6 = new SliceList(ar(-1, -3, -5)).normalizeR(5);
    assertEquals("<1 3>", sl6.toString());
    assertArrayEquals(ar(1, 3), sl6.expand8());
  }


  @Test
  public void testExceptions() {
    try {
      new SliceList(0, 10000000).expand8();
      fail("Expansion of huge list should not be allowed");
    } catch (IllegalArgumentException ignored) {}

    try {
      new SliceList(0, 10000000).expand4();
      fail("Expansion of huge list should not be allowed");
    } catch (IllegalArgumentException ignored) {}

    try {
      new SliceList(ar("foo", "bar"), makeTestFrame());
      fail("Column 'bar' should not have been found");
    } catch (IllegalArgumentException ignored) {}

    try {
      getSliceList("<2:3:2 4:6:-1>").normalizeR(10);
      fail("Mixed list should not be normalizable");
    } catch (IllegalArgumentException ignored) {}

    try {
      getSliceList("<-3 5>").normalizeR(10);
      fail("Mixed list should not be normalizable");
    } catch (IllegalArgumentException ignored) {}

    try {
      getSliceList("<10>").normalizeR(10);
      fail("Index out of range");
    } catch (IllegalArgumentException ignored) {}

    try {
      getSliceList("<-11>").normalizeR(10);
      fail("Index out of range");
    } catch (IllegalArgumentException ignored) {}
  }



  //--------------------------------------------------------------------------------------------------------------------
  // Helpers
  //--------------------------------------------------------------------------------------------------------------------

  /** Convert Cascade expression into a {@link SliceList} */
  private SliceList getSliceList(String expr) {
    return Cascade.parse(expr).exec(scope).getSlice();
  }

  /**
   * Assert that iterating over the {@code SliceList} generated from the
   * expression {@code expr} yields the {@code expected} sequence of values.
   * <br>
   * This also tests the {@code .expand4()} and {@code .expand8()} methods.
   */
  private void assertIter(String expr, long[] expected) {
    assertIter(expr, getSliceList(expr), expected);
  }

  /**
   * Assert that iterating over the slice list {@code sl}, corresponding to
   * the expression {@code expr}, yields the {@code expected} sequence. The
   * difference from {@link #assertIter(String, long[])} is that here the
   * {@code expr} may be any description of the actual slice list used.
   * <br>
   * This also tests the {@code .expand4()} and {@code .expand8()} methods.
   */
  private void assertIter(String expr, SliceList sl, long[] expected) {
    int[] expected4 = new int[expected.length];
    SliceList.Iterator iter = sl.iter();
    for (int i = 0; i < expected.length; i++) {
      expected4[i] = (int) expected[i];
      if (iter.hasNext()) {
        long nextValue = iter.nextPrim();
        assertEquals("Wrong element at index " + i + " for slice " + expr, expected[i], nextValue);
      } else {
        fail("Iterator stopped at index " + i + " for slice " + expr);
      }
    }
    assertFalse("Iterator still wants to produce values for slice " + expr, iter.hasNext());

    int[] expanded4 = sl.expand4();
    long[] expanded8 = sl.expand8();
    assertArrayEquals("Wrong expand4() form for slice " + expr, expected4, expanded4);
    assertArrayEquals("Wrong expand8() form for slice " + expr, expected, expanded8);
  }


  @SuppressWarnings("ExternalizableWithoutPublicNoArgConstructor")
  private GhostFrame makeTestFrame() {
    return new GhostFrame1() {
      private String[] cols = {"a", "foo", "a0", " ", "!!!"};
      @Override public int numCols() { return 5; }
      @Override public byte type(int i) { return 0; }
      @Override public String name(int i) { return cols[i]; }
    };
  }

}
