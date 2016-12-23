package ai.h2o.cascade.core;

import ai.h2o.cascade.Cascade;
import ai.h2o.cascade.CascadeSession;
import org.junit.Before;
import org.junit.Test;
import water.TestUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

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
  }


  //--------------------------------------------------------------------------------------------------------------------
  // Helpers
  //--------------------------------------------------------------------------------------------------------------------

  private SliceList getSliceList(String expr) {
    return Cascade.parse(expr).exec(scope).getSlice();
  }
  private void assertIter(String expr, long[] expected) {
    assertIter(expr, getSliceList(expr), expected);
  }
  private void assertIter(String expr, SliceList sl, long[] expected) {
    SliceList.Iterator iter = sl.iter();
    for (int i = 0; i < expected.length; i++) {
      if (iter.hasNext()) {
        long nextValue = iter.nextPrim();
        assertEquals("Wrong element at index " + i + " for slice " + expr, expected[i], nextValue);
      } else {
        fail("Iterator stopped at index " + i + " for slice " + expr);
      }
    }
    assertFalse("Iterator still wants to produce values for slice " + expr, iter.hasNext());
  }
}
