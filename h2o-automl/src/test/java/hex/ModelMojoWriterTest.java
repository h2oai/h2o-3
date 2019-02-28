package hex;

import ai.h2o.automl.targetencoding.BlendingParams;
import ai.h2o.automl.targetencoding.TargetEncoder;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;

/**
 * This test should be moved to h2o-core module once we move all TargetEncoding related classes there as well. 
 */
public class ModelMojoWriterTest extends TestUtil implements ModelTestCommons {

  @BeforeClass public static void stall() { stall_till_cloudsize(1); }
  Frame trainFrame = parse_test_file("./smalldata/gbm_test/titanic.csv");
  String fileName = "test_mojo_te.zip";
  
  private Map<String, Frame> getTEMap() {
    
    String responseColumnName = "survived";
    asFactor(trainFrame, responseColumnName);
    
    BlendingParams params = new BlendingParams(3, 1);
    String[] teColumns = {"home.dest", "embarked"};
    TargetEncoder targetEncoder = new TargetEncoder(teColumns, params);
    Map<String, Frame> testEncodingMap = targetEncoder.prepareEncodingMap(trainFrame, responseColumnName, null);
    return testEncodingMap;
  }

  @Test public void writeModelToZipFile() {
    TestModel.TestParam p = new TestModel.TestParam();
    TestModel.TestOutput o = new TestModel.TestOutput();
    o.setNames(trainFrame.names());
    o._domains = trainFrame.domains();
    
    TestModel testModel = new TestModel(Key.make(),p,o);
    Map<String, Frame> teMap = getTEMap();
    testModel.addTargetEncodingMap(teMap);

    String fileName = "test_mojo_te.zip";

    try {
      FileOutputStream modelOutput = new FileOutputStream(fileName);
      testModel.getMojo().writeTo(modelOutput);
      modelOutput.close();

      Assert.assertTrue(new File(fileName).exists());

    } catch (Exception ex) {
      System.out.println(ex.getMessage());
    } finally {
      File file = new File(fileName);
      if (file.exists()) {
        file.delete();
      }
    }
    //TODO leaked keys
  }

}
