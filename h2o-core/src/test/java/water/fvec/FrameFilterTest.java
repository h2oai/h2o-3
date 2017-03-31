package water.fvec;

import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.parser.BufferedString;
import water.udf.fp.Functions;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Test for FrameFilter
 * <p>
 * Created by vpatryshev on 2/21/17.
 */
public class FrameFilterTest extends TestUtil {

  static boolean isSquare(long i) {
    int sqrt = (int) Math.sqrt(i);
    for (int j = sqrt - 1; j <= sqrt + 1; j++) if (j * j == i) return true;
    return false;
  }

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(2);
  }

  @Test
  public void testSmallSet() throws Exception {
    Scope.enter();
    Vec v1 = vec(1, 2, 3, 4, 5);
    Vec v2 = svec("eins", "zwei", "drei", "vier", "fuenf");
    Vec v3 = svec("-1-", "-2-", "-3-", "-4-", "-5-");
    Frame f = new Frame(v2, v3, v1);
    FrameFilter sut1 = new FrameFilter(f, "C3") {
      @Override
      public boolean accept(Chunk c, int i) {
        return c.at8(i) % 2 == 1;
      }
    };
    Frame actual = sut1.eval();
//    assertArrayEquals(new String[]{"C2", "C3"}, actual.names());
    Vec va0 = actual.vec(0);
    Vec va1 = actual.vec(1);
    assertEquals(3, va0.length());
    assertEquals(3, va1.length());
    BufferedString stupidBuffer = new BufferedString();
    assertEquals("eins", String.valueOf(va0.atStr(stupidBuffer, 0)));
    assertEquals("-3-", String.valueOf(va1.atStr(stupidBuffer, 1)));

    Scope.exit();
  }

//  @Test
//  public void testLargeSet() throws Exception {
//    Scope.enter();
//    int size = 1000000;
//    Vec v1 = vec(size, Functions.<Integer>identity());
//    Vec v2 = vec(size, Functions.<Integer>identity());
//    Vec v3 = svec(size, Functions.format("<<%d>>"));
//    Frame f = new Frame(v1, v2, v3);
//
//    FrameFilter sut = new FrameFilter(f, "C1") {
//      @Override
//      public boolean accept(Chunk c, int i) {
//        return isSquare(c.at8(i));
//      }
//    };
//
//    Frame actual = sut.eval();
//    assertArrayEquals(new String[]{"C2", "C3"}, actual.names());
//    Vec va0 = actual.vec(0);
//    Vec va1 = actual.vec(1);
//    assertEquals(1000, va0.length());
//    assertEquals(1000, va1.length());
//    BufferedString stupidBuffer = new BufferedString();
//    for (int i = 0; i < 1000; i++) {
//      int i2 = i * i;
//      assertEquals(i2, va0.at8(i));
//      assertEquals("<<" + i2 + ">>", String.valueOf(va1.atStr(stupidBuffer, i)));
//    }
//    Scope.exit();
//  }
}