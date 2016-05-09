package water.codegen.java;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import hex.deeplearning.DeepLearning;
import hex.deeplearning.DeepLearningModel;
import hex.genmodel.GenModel;
import water.TestUtil;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.Log;

import static water.codegen.java.CodeGenTestUtil.getPojoModel;

/**
 * Created by michal on 5/6/16.
 */
public class DeepLearningModelPOJOCodeGenTest extends TestUtil {

  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void simpleModel() {
    Frame tfr = null;
    Frame prediction = null;
    DeepLearningModel model = null;

    try {
      tfr = parse_test_file("./smalldata/iris/iris.csv");
      DeepLearningModel.DeepLearningParameters parms = new DeepLearningModel.DeepLearningParameters();
      parms._train = tfr._key;
      parms._epochs = 100;
      parms._response_column = "C5";
      parms._reproducible = true;
      parms._classification_stop = 0.7;
      parms._score_duty_cycle = 1;
      parms._score_interval = 0;
      parms._hidden = new int[]{100,100};
      parms._seed = 0xdecaf;
      parms._variable_importances = true;

      // Build a first model; all remaining models should be equal
      model = new DeepLearning(parms).trainModel().get();

      // Generate POJO based model
      POJOModelCodeGenerator pojoCodeGen = new DeepLearningModelPOJOCodeGen(model).build();
      GenModel genModel = getPojoModel(pojoCodeGen);
      // Verify that prediction of runtime model matches prediction of generated model
      prediction = model.score(tfr);
      // Compare model prediction with validation data
      Assert.assertTrue(model.testJavaScoring(genModel, tfr, prediction, 1e-15));
    } finally {
      if (tfr != null) tfr.delete();
      if (prediction != null) prediction.delete();
      if (model != null) model.delete();
    }
  }

}
