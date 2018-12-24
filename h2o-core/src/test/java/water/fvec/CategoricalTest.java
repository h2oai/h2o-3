package water.fvec;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.H2O;
import water.Key;
import water.MRTask;
import water.TestUtil;
import water.parser.BufferedString;
import water.parser.ParseDataset;
import water.parser.ParseSetup;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.VecUtils;

import java.util.ArrayList;
import java.util.Arrays;

import static water.TestUtil.parse_test_file;


@RunWith(H2ORunner.class)
@CloudSize(1)
public class CategoricalTest{

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
