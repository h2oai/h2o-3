package ai.h2o.targetencoding;

import ai.h2o.targetencoding.TargetEncoderModel.DataLeakageHandlingStrategy;
import ai.h2o.targetencoding.TargetEncoderModel.TargetEncoderParameters;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

/**
 * This test is checking for data leakage in case of exception during execution.
 */
@RunWith(H2ORunner.class)
@CloudSize(1)
public class TargetEncodingExceptionsHandlingTest extends TestUtil {

  @Test
  public void test_exception_handling_during_training() {
    try {
      Scope.enter();
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("categorical", "target", "foldc")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "b", "b"))
              .withDataForCol(1, ar("N", "Y", "Y", "Y"))
              .withDataForCol(2, ar(1, 2, 2, 3))
              .build(); //auto-track

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._train = fr._key;
      teParams._response_column = "target";
      teParams._fold_column = "foldc";
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.KFold;
      teParams._seed = 42;
      
      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoder teSpy = spy(te);
      doNothing().when(teSpy).init(true); // this won't initialize columns to encode and throw a NPE later during training
      
      try {
        Scope.track_generic(teSpy.trainModel().get());
        fail("should not be raised");
      } catch (NullPointerException ex) {
        assertEquals("prepareEncodingMap", ex.getStackTrace()[0].getMethodName());
      }
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_exception_handling_during_predictions() {
      try {
        Scope.enter();
        Frame fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("categorical", "target", "foldc")
                .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
                .withDataForCol(0, ar("a", "b", "b", "b"))
                .withDataForCol(1, ar("N", "Y", "Y", "Y"))
                .withDataForCol(2, ar(1, 2, 2, 3))
                .build(); //auto-track

        TargetEncoderParameters teParams = new TargetEncoderParameters();
        teParams._train = fr._key;
        teParams._response_column = "target";
        teParams._fold_column = "foldc";
        teParams._data_leakage_handling = DataLeakageHandlingStrategy.KFold;
        teParams._seed = 42;

        TargetEncoder te = new TargetEncoder(teParams);
        TargetEncoderModel teModel = te.trainModel().get();
        Scope.track_generic(teModel);
        
        // modifying encodings after training to cause an exception when applying the model.
        Frame encodings = teModel._output._target_encoding_map.get("categorical");
        Scope.track(encodings.remove(TargetEncoderHelper.NUMERATOR_COL));

        try {
          Scope.track(teModel.score(fr));
          fail("should not be raised");
        } catch (AssertionError ex) {
          assertEquals("groupEncodingsByCategory", ex.getStackTrace()[0].getMethodName());
        }
      } finally {
        Scope.exit();
      }
  }
  
}
