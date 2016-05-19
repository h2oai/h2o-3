package water.codegen.java;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import hex.genmodel.GenModel;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import water.Key;
import water.codegen.driver.DirectOutputDriver;
import water.fvec.Frame;
import static water.codegen.java.CodeGenTestUtil.getPojoModel;
import static water.codegen.java.POJOCodeGenFactory.generator;

/**
 * Created by michal on 3/21/16.
 */
public class DRFModelPOJOCodeGenTest extends water.TestUtil {

  static final String IRIS_PATH = "smalldata/iris/iris_wheader.csv";

  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test public void simpleModel() throws IOException {
    Frame frame = null;
    Frame prediction = null;
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

      GenModel genModel = getPojoModel(generator(model));
      // Verify that prediction of runtime model matches prediction of generated model
      prediction = model.score(frame);
      // Compare model prediction with validation data
      Assert.assertTrue(model.testJavaScoring(genModel, frame, prediction, 1e-15));
    } finally {
      if (frame != null) frame.delete();
      if (model != null) model.delete();
      if (prediction != null) prediction.delete();
    }
  }
}
