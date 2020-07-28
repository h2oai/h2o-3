package ai.h2o.targetencoding;

import org.apache.commons.lang.ArrayUtils;
import org.junit.Before;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;

import static org.junit.Assert.*;

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
      parameters._data_leakage_handling = TargetEncoder.DataLeakageHandlingStrategy.None;
      parameters._inflection_point = 0.3;
      parameters._smoothing = 0.7;
      parameters._blending = true;
      parameters._response_column = "IsDepDelayed";
      parameters._ignored_columns = ignoredColumns(trainingFrame, "Origin", parameters._response_column);
      parameters._train = trainingFrame._key;
      parameters._seed = 0XFEED;
      

      TargetEncoderBuilder job = new TargetEncoderBuilder(parameters);
      final TargetEncoderModel targetEncoderModel = job.trainModel().get();
      Scope.track_generic(targetEncoderModel);
      assertNotNull(targetEncoderModel);
      final Frame transformedFrame = targetEncoderModel.score(testFrame);
      Scope.track(transformedFrame);
      
      assertNotNull(transformedFrame);
      assertEquals(trainingFrame.numCols() + 1, transformedFrame.numCols());
      final int encodedColumnIndex = ArrayUtils.indexOf(transformedFrame.names(), "Origin_te");
      assertNotEquals(-1, encodedColumnIndex);
      assertTrue(transformedFrame.vec(encodedColumnIndex).isNumeric());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testTargetEncoderModel_noBlendingParameters() {
    try {
      Scope.enter();
      Frame trainingFrame = parse_test_file("./smalldata/testng/airlines_train.csv");
      Scope.track(trainingFrame);
      Frame testFrame = parse_test_file("./smalldata/testng/airlines_test.csv");
      Scope.track(testFrame);

      TargetEncoderModel.TargetEncoderParameters parameters = new TargetEncoderModel.TargetEncoderParameters();
      parameters._data_leakage_handling = TargetEncoder.DataLeakageHandlingStrategy.None;
      parameters._blending = true;
      parameters._response_column = "IsDepDelayed";
      parameters._ignored_columns = ignoredColumns(trainingFrame, "Origin", parameters._response_column);
      parameters._train = trainingFrame._key;
      parameters._seed = 0XFEED;


      TargetEncoderBuilder job = new TargetEncoderBuilder(parameters);
      final TargetEncoderModel targetEncoderModel = job.trainModel().get();
      Scope.track_generic(targetEncoderModel);
      assertNotNull(targetEncoderModel);
      final Frame transformedFrame = targetEncoderModel.score(testFrame);
      Scope.track(transformedFrame);

      assertNotNull(transformedFrame);
      assertEquals(trainingFrame.numCols() + (trainingFrame.numCols() - parameters._ignored_columns.length - 1), transformedFrame.numCols());
      final int encodedColumnIndex = ArrayUtils.indexOf(transformedFrame.names(), "Origin_te");
      assertNotEquals(-1, encodedColumnIndex);
      assertTrue(transformedFrame.vec(encodedColumnIndex).isNumeric());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testTargetEncoderModel_dropNonCategoricalCols() {
    try {
      Scope.enter();
      Frame trainingFrame = parse_test_file("./smalldata/testng/airlines_train.csv");
      Scope.track(trainingFrame);

      TargetEncoderModel.TargetEncoderParameters parameters = new TargetEncoderModel.TargetEncoderParameters();
      parameters._data_leakage_handling = TargetEncoder.DataLeakageHandlingStrategy.None;
      parameters._response_column = "IsDepDelayed";
      parameters._ignored_columns = null;
      parameters._train = trainingFrame._key;
      parameters._seed = 0XFEED;


      TargetEncoderBuilder job = new TargetEncoderBuilder(parameters);
      final TargetEncoderModel targetEncoderModel = job.trainModel().get();
      Scope.track_generic(targetEncoderModel);
      // Check categorical colums for not being removed
      assertArrayEquals(new String[]{"fYear", "fMonth", "fDayofMonth", "fDayOfWeek", "UniqueCarrier",
              "Origin", "Dest", "IsDepDelayed"}, targetEncoderModel._output._names);
    } finally {
      Scope.exit();
    }
  }
}
