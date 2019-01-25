package ai.h2o.automl.targetencoding.integration;

import ai.h2o.automl.AutoML;
import ai.h2o.automl.AutoMLBuildSpec;
import ai.h2o.automl.targetencoding.BlendingParams;
import ai.h2o.automl.targetencoding.TargetEncoder;
import ai.h2o.automl.targetencoding.TargetEncoderFrameHelper;
import ai.h2o.automl.targetencoding.TargetEncodingParams;
import ai.h2o.automl.targetencoding.strategy.*;
import hex.Model;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import java.util.Map;

import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.encodingMapCleanUp;
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
    Frame trainingFrame = null;
    String teColumnName = "ColA";
    String responseColumnName = "ColC";
    String foldColumnName = "fold";
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      //NOTE: We can't use parse_test_file method because behaviour of the Frame would be different comparing to TestFrameBuilder's frames
      //fr = parse_test_file("./smalldata/gbm_test/titanic.csv"); 
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames(teColumnName, "ColB", responseColumnName, foldColumnName)
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "b", "b", "a"))
              .withDataForCol(1, ard(1, 1, 4, 7, 4))
              .withDataForCol(2, ar("2", "6", "6", "6", "6"))
              .withDataForCol(3, ar(1, 2, 2, 3, 2))
              .build();
      
      TargetEncoderFrameHelper.factorColumn(fr, responseColumnName);
      
      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.fold_column = foldColumnName;
      autoMLBuildSpec.input_spec.response_column = responseColumnName;

      TargetEncodingParams targetEncodingParams = new TargetEncodingParams(new BlendingParams(5, 1), TargetEncoder.DataLeakageHandlingStrategy.KFold, 0.01);
      TEParamsSelectionStrategy fixedTEParamsStrategy =  new FixedTEParamsStrategy(targetEncodingParams);

      Vec responseColumn = fr.vec(responseColumnName);
      TEApplicationStrategy teApplicationStrategy = new AllCategoricalTEApplicationStrategy(fr, responseColumn); //TODO we can introduce ENUM and create factory based on it
      
      autoMLBuildSpec.te_spec.application_strategy = teApplicationStrategy;
      autoMLBuildSpec.te_spec.params_selection_strategy = fixedTEParamsStrategy;

      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
      autoMLBuildSpec.build_control.keep_cross_validation_models = false;
      autoMLBuildSpec.build_control.keep_cross_validation_predictions = false;

      aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();

      leader = aml.leader();

      trainingFrame = aml.getTrainingFrame();
      
      assertNotEquals(" Two frames should be different.", fr, trainingFrame);
      assertEquals(teApplicationStrategy.getColumnsToEncode().length + fr.numCols(), trainingFrame.numCols());
      
      printOutFrameAsTable(trainingFrame, false, 100);

    } finally {
      if(leader!=null) leader.delete();
      if(aml!=null) aml.delete();
      if(trainingFrame != null)  trainingFrame.delete();
      if(fr != null) fr.delete();
    }
  }

  @Test public void defaultStrategiesTest() {
    //TODO check that if we don't specify stratagies in AutoMLBuildSpec then default ones will be applied
  }
  
  
  @Test public void KFoldFixedParamsAllCategoricalTest() {
    AutoML aml=null;
    Frame fr=null;
    Model leader = null;
    String teColumnName = "ColA";
    String responseColumnName = "ColC";
    String foldColumnName = "fold";
    try {
      // TODO refactor out buildspec fixture.
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames(teColumnName, "ColB", responseColumnName, foldColumnName)
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "b", "b", "a"))
              .withDataForCol(1, ard(1, 1, 4, 7, 4))
              .withDataForCol(2, ar("2", "6", "6", "6", "6"))
              .withDataForCol(3, ar(1, 2, 2, 3, 2))
              .build();

      Frame copyOfTheTrainingFrame = fr.deepCopy("copy_of_the_training_frame");
      DKV.put(copyOfTheTrainingFrame);
      
      TargetEncoderFrameHelper.factorColumn(fr, responseColumnName);

      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.fold_column = foldColumnName;
      autoMLBuildSpec.input_spec.response_column = responseColumnName;

      TargetEncodingParams targetEncodingParams = new TargetEncodingParams(new BlendingParams(5, 1), TargetEncoder.DataLeakageHandlingStrategy.KFold, 0.01);
      TEParamsSelectionStrategy fixedTEParamsStrategy =  new FixedTEParamsStrategy(targetEncodingParams);

      TEApplicationStrategy teApplicationStrategy = new AllCategoricalTEApplicationStrategy(fr, fr.vec(responseColumnName));

      autoMLBuildSpec.te_spec.application_strategy = teApplicationStrategy;
      autoMLBuildSpec.te_spec.params_selection_strategy = fixedTEParamsStrategy;
      autoMLBuildSpec.te_spec.seed = 2345;

      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
      autoMLBuildSpec.build_control.keep_cross_validation_models = false;
      autoMLBuildSpec.build_control.keep_cross_validation_predictions = false;

      aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();

      leader = aml.leader();

      Frame trainingEncodingsFromAutoML = aml.getTrainingFrame();

      // Preparing expected values with just TargetEncoding class to compare against encodings from AutoML process 
      String[] teColumns = teApplicationStrategy.getColumnsToEncode();
      TargetEncoder tec = new TargetEncoder(teColumns, targetEncodingParams.getBlendingParams());

      Map<String, Frame> encodingMap = tec.prepareEncodingMap(copyOfTheTrainingFrame, responseColumnName, foldColumnName, targetEncodingParams.isImputeNAsWithNewCategory());
      
      Frame expectedTrainingEncodedFrameFromTE = tec.applyTargetEncoding(copyOfTheTrainingFrame, responseColumnName, encodingMap, targetEncodingParams.getHoldoutType(),
              foldColumnName, targetEncodingParams.isWithBlendedAvg(), targetEncodingParams.isImputeNAsWithNewCategory(), autoMLBuildSpec.te_spec.seed);

      // We are ready to assert encodings from AutoML process and encodings directly from TargetEncoder
      assertTrue(isBitIdentical(trainingEncodingsFromAutoML, expectedTrainingEncodedFrameFromTE));

      encodingMapCleanUp(encodingMap);
      trainingEncodingsFromAutoML.delete();
      copyOfTheTrainingFrame.delete();
      expectedTrainingEncodedFrameFromTE.delete();

    } finally {
      if(leader!=null) leader.delete();
      if(aml!=null) aml.delete();
      if(fr != null) fr.delete();
    }
  }

  @Test public void ThresholdApplicationStrategyTest() {
    AutoML aml=null;
    Frame fr=null;
    Model leader = null;
    Frame trainingFrame = null;
    String teColumnName = "ColA";
    String responseColumnName = "ColC";
    String foldColumnName = "fold";
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      
      String columnThatIsSupposedToBeEncoded = "ColB";
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames(teColumnName, columnThatIsSupposedToBeEncoded, responseColumnName, foldColumnName)
              .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "b", "b", "a", "a"))
              .withDataForCol(1, ar("yellow", "blue", "green", "red", "purple", "orange"))
              .withDataForCol(2, ar("2", "6", "6", "6", "6", "2"))
              .withDataForCol(3, ar(1, 2, 2, 3, 2, 1))
              .build();

      TargetEncoderFrameHelper.factorColumn(fr, responseColumnName);

      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.fold_column = foldColumnName;
      autoMLBuildSpec.input_spec.response_column = responseColumnName;

      TargetEncodingParams targetEncodingParams = new TargetEncodingParams(new BlendingParams(5, 1), TargetEncoder.DataLeakageHandlingStrategy.KFold, 0.01);
      TEParamsSelectionStrategy fixedTEParamsStrategy =  new FixedTEParamsStrategy(targetEncodingParams);

      Vec responseColumn = fr.vec(responseColumnName);
      TEApplicationStrategy teApplicationStrategy = new ThresholdTEApplicationStrategy(fr, responseColumn, 5);

      autoMLBuildSpec.te_spec.application_strategy = teApplicationStrategy;
      autoMLBuildSpec.te_spec.params_selection_strategy = fixedTEParamsStrategy;

      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
      autoMLBuildSpec.build_control.keep_cross_validation_models = false;
      autoMLBuildSpec.build_control.keep_cross_validation_predictions = false;

      aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();

      leader = aml.leader();

      trainingFrame = aml.getTrainingFrame();

      assertNotEquals(" Two frames should be different.", fr, trainingFrame);
      assertEquals(1, teApplicationStrategy.getColumnsToEncode().length);
      assertEquals(teApplicationStrategy.getColumnsToEncode().length + fr.numCols(), trainingFrame.numCols());

    } finally {
      if(leader!=null) leader.delete();
      if(aml!=null) aml.delete();
      if(trainingFrame != null)  trainingFrame.delete();
      if(fr != null) fr.delete();
    }
  }
}
