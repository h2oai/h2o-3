package ai.h2o.targetencoding;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TargetEncoderMojoWriterTest extends TestUtil {

  @BeforeClass
  public static void stall() { stall_till_cloudsize(1); }

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void writeModelToZipFile() throws Exception{

    String fileNameForMojo = "test_mojo_te.zip";
    try {
      Scope.enter();
      Frame trainFrame = parse_test_file("./smalldata/gbm_test/titanic.csv");
      Scope.track(trainFrame);
      TargetEncoderModel.TargetEncoderParameters p = new TargetEncoderModel.TargetEncoderParameters();
      String responseColumnName = "survived";

      asFactor(trainFrame, responseColumnName);

      p._blending = false;
      p._response_column = responseColumnName;
      p._ignored_columns = ignoredColumns(trainFrame, "home.dest", "embarked", p._response_column);
      p.setTrain(trainFrame._key);

      TargetEncoder builder = new TargetEncoder(p);

      TargetEncoderModel targetEncoderModel = builder.trainModel().get(); // Waiting for training to be finished
      Scope.track_generic(targetEncoderModel);
      File mojoFile = folder.newFile(fileNameForMojo);
      
      try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)){
        assertEquals(0, mojoFile.length());

        targetEncoderModel.getMojo().writeTo(modelOutput);
        assertTrue(mojoFile.length() > 0);
      }
    } finally {
      Scope.exit();
    }
  }

}
