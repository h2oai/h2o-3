package water.codegen.java;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import water.Key;
import water.codegen.driver.DirectOutputDriver;
import water.fvec.Frame;
import water.util.FileUtils;

/**
 * Created by michal on 3/21/16.
 */
public class DRFModelJCodeGenTest extends water.TestUtil {

  static final String IRIS_PATH = "smalldata/iris/iris_wheader.csv";

  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test public void simpleModel() throws IOException {
    Frame frame = null;
    DRFModel model = null;
    try {
      final Frame f = frame = parse_test_file(Key.make("iris.hex"), IRIS_PATH);
      DRFModel.DRFParameters params = new DRFModel.DRFParameters() {{
        _train = f._key;
        _response_column = "class";
        _ntrees = 1;
        _max_depth = 50; // Unlimited
      }};
      model = new DRF(params, Key.<DRFModel>make("iris.model")).trainModel().get();

      // FIXME: calling new XXX().build() is bad pattern here, since you can
      // forget call of build and result will have no output.
      new DirectOutputDriver().codegen(new DRFModelJCodeGen(model).build(), System.err);
      model.toJava(System.err, false, true);

    } finally {
      if (frame != null) frame.delete();
      if (model != null) model.delete();
    }
  }
}
