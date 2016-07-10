package water.fvec;

import org.junit.BeforeClass;
import org.junit.Test;
import water.H2O;
import water.Key;
import water.MRTask;
import water.TestUtil;
import water.parser.BufferedString;
import water.parser.ParseDataset;
import water.parser.ParseSetup;
import water.util.VecUtils;

import java.util.ArrayList;
import java.util.Arrays;


public class CategoricalTest extends TestUtil{
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test public void testCancelSparseCategoricals() {
    Frame frame = null;
    Vec stringVec = null;
    Vec catVec = null;
    try {
      frame = parse_test_file("smalldata/junit/iris.csv");
      Vec vec = frame.lastVec();
      assert vec.naCnt() == 0;
      stringVec = vec.toStringVec();
      assert stringVec.naCnt() == 0;
      catVec = stringVec.toCategoricalVec();
      assert catVec.naCnt() == 0;
    } finally {
      if (frame != null) frame.delete();
      if (stringVec != null) stringVec.remove();
      if (catVec != null) catVec.remove();
    }
  }
}
