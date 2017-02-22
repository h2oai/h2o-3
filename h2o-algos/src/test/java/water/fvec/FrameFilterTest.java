package water.fvec;

import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.parser.BufferedString;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Test for FrameFilter
 * 
 * Created by vpatryshev on 2/21/17.
 */
public class FrameFilterTest extends TestUtil {
  @BeforeClass
  public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void testWholeEnchilada() throws Exception {
    Scope.enter();
    Vec v1 = vec(1,2,3,4,5);
    Vec v2 = svec("eins", "zwei", "drei", "vier", "fuenf");
    Vec v3 = svec("-1-", "-2-", "-3-", "-4-", "-5-");
    Frame f = new Frame(v1, v2, v3);

    FrameFilter sut = new FrameFilter() {

      @Override
      boolean accept(Chunk c, int i) {
        return c.at8(i) % 2 == 1;
      }
    };

    Key<Frame> destKey = Key.make();
//    assertEquals("what", sut.manyMaps(v1, v2));
    Frame actual = Scope.track(sut.eval(f, "C1"));
    assertArrayEquals(new String[]{"C2", "C3"}, actual.names());
    Vec va0 = actual.vec(0);
    Vec va1 = actual.vec(1);
    assertEquals(3, va0.length());
    assertEquals(3, va1.length());
    BufferedString stupidBuffer = new BufferedString();
    assertEquals("eins", String.valueOf(va0.atStr(stupidBuffer, 0)));
    assertEquals("-3-", String.valueOf(va1.atStr(stupidBuffer, 1)));
    Scope.exit();
  }
}