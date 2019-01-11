package ai.h2o.automl.targetencoding;

import ai.h2o.automl.AutoML;
import ai.h2o.automl.AutoMLBuildSpec;
import ai.h2o.automl.targetencoding.strategy.AllCategoricalTEApplicationStrategy;
import ai.h2o.automl.targetencoding.strategy.FixedTEParamsStrategy;
import ai.h2o.automl.targetencoding.strategy.TEApplicationStrategy;
import ai.h2o.automl.targetencoding.strategy.TEParamsSelectionStrategy;
import hex.Model;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.fvec.Frame;

import java.util.Map;

import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;
import static org.junit.Assert.*;

public class TargetEncodingIntegrationWithAutoMLTest extends water.TestUtil {

  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  @Test public void TEIsNotBeingAppliedIfWeDontHaveCategoricalColumns() {
    AutoML aml=null;
    Frame fr=null;
    Model leader = null;
    Frame originalCopyOfTrainingFrame = null;
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      fr = parse_test_file("./smalldata/logreg/prostate_train.csv");
      originalCopyOfTrainingFrame = fr.deepCopy(Key.make().toString());
      DKV.put(originalCopyOfTrainingFrame);
      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.response_column = "CAPSULE";

      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
      autoMLBuildSpec.build_control.keep_cross_validation_models = false; 
      autoMLBuildSpec.build_control.keep_cross_validation_predictions = false; 

      aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();

      leader = aml.leader();

      // Expect that if we don't have categorical vectors in training frame then we will return it unchanged 
      assert isBitIdentical(aml.getTrainingFrame(), originalCopyOfTrainingFrame):"The two frames are not the same.";

    } finally {
      // Cleanup
      if(leader!=null) leader.delete();
      if(aml!=null) aml.delete();
      if(fr != null) fr.delete();
      if(originalCopyOfTrainingFrame != null) originalCopyOfTrainingFrame.delete();
    }
  }

  @Test public void KFoldSmokeTest() {
    AutoML aml=null;
    Frame fr=null;
    Model leader = null;
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      
      String foldColumnName = "fold";
      addKFoldColumn(fr, foldColumnName, 5, 1234L);
      String responceColumnName = "survived";
      TargetEncoderFrameHelper.factorColumn(fr, responceColumnName);
      
      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.fold_column = foldColumnName;
      autoMLBuildSpec.input_spec.response_column = responceColumnName;

      TargetEncodingParams targetEncodingParams = new TargetEncodingParams(new BlendingParams(5, 1), TargetEncoder.DataLeakageHandlingStrategy.KFold);
      TEParamsSelectionStrategy fixedTEParamsStrategy =  new FixedTEParamsStrategy(targetEncodingParams);

      TEApplicationStrategy teApplicationStrategy = new AllCategoricalTEApplicationStrategy(fr, fr.vec(responceColumnName)); //TODO we can introduce ENUM and create factory based on it
      
      autoMLBuildSpec.te_spec.application_strategy = teApplicationStrategy;
      autoMLBuildSpec.te_spec.params_selection_strategy = fixedTEParamsStrategy;

      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
      autoMLBuildSpec.build_control.keep_cross_validation_models = false;
      autoMLBuildSpec.build_control.keep_cross_validation_predictions = false;

      aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();

      leader = aml.leader();

      Frame trainingFrame = aml.getTrainingFrame();
      assertNotEquals(" Two frames should be different.", fr, trainingFrame);
      assertEquals(teApplicationStrategy.getColumnsToEncode().length + fr.numCols(), trainingFrame.numCols());
      
      printOutFrameAsTable(trainingFrame, false, 100);

    } finally {
      if(leader!=null) leader.delete();
      if(aml!=null) aml.delete();
      if(fr != null) fr.delete();
    }
  }

  @Test public void KFoldFixedParamsAllCategoricalTest() {
    AutoML aml=null;
    Frame fr=null;
    Model leader = null;
    try {
      // TODO refactor out buildspec fixture.
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      fr = parse_test_file("./smalldata/gbm_test/titanic.csv");

      String foldColumnName = "fold";
      addKFoldColumn(fr, foldColumnName, 5, 1234L);
      String responceColumnName = "survived";
      TargetEncoderFrameHelper.factorColumn(fr, responceColumnName);

      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.fold_column = foldColumnName;
      autoMLBuildSpec.input_spec.response_column = responceColumnName;

      TargetEncodingParams targetEncodingParams = new TargetEncodingParams(new BlendingParams(5, 1), TargetEncoder.DataLeakageHandlingStrategy.KFold);
      TEParamsSelectionStrategy fixedTEParamsStrategy =  new FixedTEParamsStrategy(targetEncodingParams);

      TEApplicationStrategy teApplicationStrategy = new AllCategoricalTEApplicationStrategy(fr, fr.vec(responceColumnName));

      autoMLBuildSpec.te_spec.application_strategy = teApplicationStrategy;
      autoMLBuildSpec.te_spec.params_selection_strategy = fixedTEParamsStrategy;
      autoMLBuildSpec.te_spec.seed = 2345;

      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
      autoMLBuildSpec.build_control.keep_cross_validation_models = false;
      autoMLBuildSpec.build_control.keep_cross_validation_predictions = false;

      aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();

      leader = aml.leader();

      Frame trainingFrameAutoMLWasTrainingOn = aml.getTrainingFrame();
      Frame copyOfTheTrainingFrame = fr.deepCopy("copy_of_the_training_frame");
      DKV.put(copyOfTheTrainingFrame);

      // Preparing expected values with just TargetEncoding class
      String[] teColumns = teApplicationStrategy.getColumnsToEncode();
      TargetEncoder tec = new TargetEncoder(teColumns, targetEncodingParams.getBlendingParams());

      Map<String, Frame> encodingMap = tec.prepareEncodingMap(copyOfTheTrainingFrame, responceColumnName, foldColumnName, targetEncodingParams.isImputeNAsWithNewCategory());
      
      Frame expectedTrainingEncodedFrame = tec.applyTargetEncoding(copyOfTheTrainingFrame, responceColumnName, encodingMap, targetEncodingParams.getHoldoutType(),
              foldColumnName, targetEncodingParams.isWithBlendedAvg(), targetEncodingParams.isImputeNAsWithNewCategory(), autoMLBuildSpec.te_spec.seed);

      //We need to find a way of how to sort both frames so that we can compare them. Use MRTast to add id column.
      printOutFrameAsTable(expectedTrainingEncodedFrame.sort(new int[]{7}), false, 13);
      printOutFrameAsTable(trainingFrameAutoMLWasTrainingOn.sort(new int[]{2}), false, 13);
//      printOutFrameAsTable(expectedTrainingEncodedFrame.subframe(teColumns).sort(new int[]{3}), false, 1309);
//      printOutFrameAsTable(trainingFrameAutoMLWasTrainingOn.subframe(teColumns).sort(new int[]{3}), false, 1309);

      // We are ready to assert encodings from AutoML process and encodings directly from TargetEncoder
//      assertTrue(isBitIdentical(expectedTrainingEncodedFrame.subframe(teColumns).sort(new int[]{3}), trainingFrameAutoMLWasTrainingOn.subframe(teColumns).sort(new int[]{3})));

    } finally {
      if(leader!=null) leader.delete();
      if(aml!=null) aml.delete();
      if(fr != null) fr.delete();
    }
  }
}
