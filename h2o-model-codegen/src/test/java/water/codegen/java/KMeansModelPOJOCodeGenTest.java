package water.codegen.java;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import hex.genmodel.GenModel;
import hex.kmeans.KMeans;
import hex.kmeans.KMeansModel;
import water.fvec.Frame;

import static water.codegen.java.CodeGenTestUtil.getPojoModel;

/**
 * KMeans model POJO generator.
 */
public class KMeansModelPOJOCodeGenTest extends water.TestUtil {

  static final String IRIS_PATH = "smalldata/iris/iris_wheader.csv";

  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test public void simpleModel() throws IOException {
    Frame fr = null;
    Frame prediction = null;
    KMeansModel model = null;
    try {
      final Frame f = fr = parse_test_file(IRIS_PATH);

      KMeansModel.KMeansParameters params = new KMeansModel.KMeansParameters() {{
        _train = f._key;
        _ignored_columns = new String[] {"class"};
        _k = 3;
        _standardize = true;
        _max_iterations = 10;
        _init = KMeans.Initialization.Random;
      }};

      model = new KMeans(params).trainModel().get();
      // Generate POJO based model
      POJOModelCodeGenerator pojoCodeGen = new KMeansModelPOJOCodeGen(model).build();
      GenModel genModel = getPojoModel(pojoCodeGen);
      // Verify that prediction of runtime model matches prediction of generated model
      prediction = model.score(fr);
      // Compare model prediction with validation data
      Assert.assertTrue(model.testJavaScoring(genModel, fr, prediction, 1e-15));
    } finally {
      if (fr != null) fr.delete();
      if (prediction != null) prediction.delete();
      if (model != null) model.delete();
    }
  }
}