package ai.h2o.targetencoding;

import ai.h2o.targetencoding.TargetEncoderModel.TargetEncoderParameters;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class TargetEncodingImmutabilityTest extends TestUtil {
  
  @Test
  public void deepCopyTest() {
    try {
      Scope.enter();
      Frame fr = new TestFrameBuilder()
              .withName("trainingFrame")
              .withColNames("colA")
              .withVecTypes(Vec.T_STR)
              .withDataForCol(0, ar("a", "b", "c"))
              .build();

      Frame trainCopy = fr.deepCopy(Key.make().toString());
      Scope.track(trainCopy);

      assertBitIdentical(fr, trainCopy);
      trainCopy.vec(0).set(0, "d");

      assertIdenticalUpToRelTolerance(fr, trainCopy, 0, false);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_original_frame_was_not_modified_during_training_and_scoring() {
    try {
      Scope.enter();
      Frame training = new TestFrameBuilder()
              .withName("trainingFrame")
              .withColNames("categorical", "target", "foldc")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "c", "d", "e", "b", "b"))
              .withDataForCol(1, ar("N", "Y", "Y", "Y", "Y", "N", "N"))
              .withDataForCol(2, ar(1, 2, 2, 3, 1, 2, 1))
              .build();

      Frame trainCopy = training.deepCopy(Key.make().toString());
      Scope.track(trainCopy);
      
      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._train = training._key;
      teParams._response_column = "target";
      teParams._fold_column = "foldc";
      teParams._data_leakage_handling = TargetEncoderModel.DataLeakageHandlingStrategy.KFold;
      teParams._seed = 42;
      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);
      
      Frame encoded = teModel.transformTraining(training);
      Scope.track(encoded);

      assertBitIdentical(training, trainCopy);
    } finally {
      Scope.exit();
    }
  }

}
