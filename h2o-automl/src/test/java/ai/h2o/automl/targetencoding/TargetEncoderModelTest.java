package ai.h2o.automl.targetencoding;

import org.apache.commons.lang.ArrayUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;

import static org.junit.Assert.*;
import static water.TestUtil.parse_test_file;

public class TargetEncoderModelTest extends TestUtil{

  @Before
  public void setUp() {
    TestUtil.stall_till_cloudsize(1);
  }

  @Test
  public void testTargetEncoderModel() {
    try {
      Scope.enter();
      Frame trainingFrame = parse_test_file("./smalldata/testng/airlines_train.csv");
      Scope.track(trainingFrame);
      Frame testFrame = parse_test_file("./smalldata/testng/airlines_test.csv");
      Scope.track(testFrame);

      TargetEncoderModel.TargetEncoderParameters parameters = new TargetEncoderModel.TargetEncoderParameters();
      parameters._leakageHandlingStrategy = TargetEncoder.DataLeakageHandlingStrategy.None.toString();
      parameters._blendingParams = new BlendingParams(0.3,0.7);
      parameters._columnNamesToEncode = new String[]{"Origin"};
      parameters._response_column = "IsDepDelayed";
      parameters._train = trainingFrame._key;
      parameters._seed = 0XFEED;
      

      TargetEncoderBuilder job = new TargetEncoderBuilder(parameters);
      final TargetEncoderModel targetEncoderModel = job.trainModel().get();
      Scope.track_generic(targetEncoderModel);
      assertNotNull(targetEncoderModel);
      final Frame transformedFrame = targetEncoderModel.score(testFrame);
      Scope.track(transformedFrame);
      
      assertNotNull(transformedFrame);
      assertEquals(trainingFrame.numCols() + parameters._columnNamesToEncode.length, transformedFrame.numCols());
      final int encodedColumnIndex = ArrayUtils.indexOf(transformedFrame.names(), parameters._columnNamesToEncode[0] + "_te");
      assertNotEquals(-1, encodedColumnIndex);
      assertTrue(transformedFrame.vec(encodedColumnIndex).isNumeric());
    } finally {
      Scope.exit();
    }
  }
}
