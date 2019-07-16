package ai.h2o.automl.targetencoding;

import hex.ModelStubs;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.*;

public class TargetEncoderMojoWriterTest extends TestUtil implements ModelStubs {

  @BeforeClass
  public static void stall() { stall_till_cloudsize(1); }

  Frame trainFrame = parse_test_file("./smalldata/gbm_test/titanic.csv");

  @Test
  public void writeModelToZipFile() throws Exception{
    Scope.enter();
    try {
      TestModel.TestParam p = new TestModel.TestParam();
      String[] teColumns = {"home.dest", "embarked"};
      String responseColumnName = "survived";

      asFactor(trainFrame, responseColumnName);

      p._withBlending = false;
      p._columnNamesToEncode = teColumns;
      p.setTrain(trainFrame._key);
      p._response_column = responseColumnName;

      TargetEncoderBuilder builder = new TargetEncoderBuilder(p);

      builder.trainModel().get(); // Waiting for training to be finished
      TargetEncoderModel targetEncoderModel = builder.getTargetEncoderModel(); // TODO change the way of how we getting model after PUBDEV-6670. We should be able to get it from DKV with .trainModel().get()

      String fileName = "test_mojo_te.zip";

      try {
        FileOutputStream modelOutput = new FileOutputStream(fileName);
        targetEncoderModel.getMojo().writeTo(modelOutput);
        modelOutput.close();

        assertTrue(new File(fileName).exists());

      } finally {
        File file = new File(fileName);
        if (file.exists()) {
          file.delete();
        }
        trainFrame.delete();
        TargetEncoderFrameHelper.encodingMapCleanUp(targetEncoderModel._output._target_encoding_map);
      }
    } finally {
      Scope.exit();
    }
  }

}
