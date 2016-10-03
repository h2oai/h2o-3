package water.rapids.ast.prims.assign;

import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.vals.ValFrame;

import static org.junit.Assert.*;

public class AstRectangleAssignTest extends TestUtil {

  @BeforeClass
  static public void setup() { stall_till_cloudsize(1); }

  @Test
  public void testProstate_assign_frame_slice() throws Exception {
    final Frame data = parse_test_file(Key.make("data"), "smalldata/airlines/allyears2k_headers.zip");
    Frame output = null;
    try {
      String rapids = "(tmp= tst (:= data (rows (cols data [11.0] ) [10000.0:10000.0] ) [11.0] [0.0:10000.0] ) )";
      Val val = Rapids.exec(rapids);
      if (val instanceof ValFrame) {
        output = val.getFrame();
        double[] expected = vec2array(data.vec(11));
        System.arraycopy(expected, 10000, expected, 0, 10000);
        double[] actual = vec2array(output.vec(11));
        assertArrayEquals(expected, actual, 0.0001d);
      }
    } finally {
      data.delete();
      if (output != null) {
        output.delete();
      }
    }
  }

  private static double[] vec2array(Vec v) {
    Vec.Reader ovr = v.new Reader();
    assert ovr.length() < Integer.MAX_VALUE;
    final int len = (int) ovr.length();
    double[] array = new double[len];
    for (int i = 0; i < len; i++) array[i] = ovr.at(i);
    return array;
  }

}