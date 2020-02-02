package ai.h2o.automl.targetencoder;

import ai.h2o.automl.AutoML;
import ai.h2o.automl.AutoMLBuildSpec;
import ai.h2o.targetencoding.TargetEncoderFrameHelper;
import ai.h2o.targetencoding.strategy.AllCategoricalTEApplicationStrategy;
import ai.h2o.targetencoding.strategy.TEApplicationStrategy;
import ai.h2o.targetencoding.strategy.ThresholdTEApplicationStrategy;
import hex.Model;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import java.util.Arrays;

import static org.junit.Assert.*;

// This test suite also checks how we apply preprocessing at ModellingStep and then revert all the changes in the data afterwards
public class TEIntegrationWithAutoMLTest extends water.TestUtil {

  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  @Test public void testWhenGridSizeIsOne() {
    //TODO
  }


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

      TEApplicationStrategy teApplicationStrategy = new AllCategoricalTEApplicationStrategy(fr, new String[]{autoMLBuildSpec.input_spec.response_column});
      autoMLBuildSpec.te_spec.application_strategy = teApplicationStrategy;

      aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();

      leader = aml.leader();

      // Expect that if we don't have categorical vectors in training frame then we will return it unchanged
      assertBitIdentical(aml.getTrainingFrame(), originalCopyOfTrainingFrame);

    } finally {
      // Cleanup
      if(leader!=null) leader.delete();
      if(aml!=null) aml.delete();
      if(fr != null) fr.delete();
      if(originalCopyOfTrainingFrame != null) originalCopyOfTrainingFrame.delete();
    }
  }

  public void all_categoricals_with_KFold_TE_strategy() {
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
              .withName("trainingFrame")
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

      autoMLBuildSpec.te_spec.application_strategy = new AllCategoricalTEApplicationStrategy(fr, new String[]{responseColumnName});
      autoMLBuildSpec.te_spec.seed = 2345;

      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
      autoMLBuildSpec.build_control.keep_cross_validation_models = false;
      autoMLBuildSpec.build_control.keep_cross_validation_predictions = false;

      aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();

      leader = aml.leader();

      trainingFrame = aml.getTrainingFrame();

      assertIdenticalUpToRelTolerance(fr, trainingFrame, 0, false, "Two frames should be different.");
      assertTrue(leader!= null && Arrays.asList(leader._output._names).contains(teColumnName + "_te"));

      printOutFrameAsTable(trainingFrame, false, 100);

    } finally {
      if(leader!=null) leader.delete();
      if(aml!=null) aml.delete();
      if(trainingFrame != null)  trainingFrame.delete();
      if(fr != null) fr.delete();
    }
  }

  @Test
  public void scopeUntrack() {
    Scope.enter();
    Frame fr = null;
    try{
      fr = parse_test_file("./smalldata/logreg/prostate_train.csv");
      Scope.track(fr);
      Scope.untrack(fr.keys());
    } finally {
//      Scope.exit(fr._key);
      Scope.exit();
    }
  }

  @Ignore
  public void we_can_score_with_model_from_the_leadearboard() {
    AutoML aml=null;
    AutoML amlWithoutTE=null;
    Frame fr=null;
    Model leader = null;
    Frame validationFrame = null;
    Frame copyOfValidFrame = null;
    Frame leaderboardFrame = null;
    String teColumnName = "ColA";
    String responseColumnName = "ColC";
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();

      String columnThatIsSupposedToBeEncoded = "ColB";
      fr = new TestFrameBuilder()
              .withName("trainingFrame")
              .withColNames(teColumnName, columnThatIsSupposedToBeEncoded, responseColumnName)
              .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "b", "b", "a", "a", "b", "a"))
              .withDataForCol(1, ar("yellow", "blue", "green", "red", "purple", "orange", "purple", "orange"))
              .withDataForCol(2, ar("2", "6", "6", "6", "6", "2", "6", "2"))
              .build();

      validationFrame = new TestFrameBuilder()
              .withName("validationFrame")
              .withColNames(teColumnName, columnThatIsSupposedToBeEncoded, responseColumnName)
              .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "b", "b", "a", "a"))
              .withDataForCol(1, ar("yellow", "blue", "green", "red", "purple", "orange"))
              .withDataForCol(2, ar("2", "6", "6", "6", "6", "2"))
              .build();
      copyOfValidFrame = validationFrame.deepCopy(Key.make().toString());
      DKV.put(copyOfValidFrame);

      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.validation_frame = validationFrame._key;
      autoMLBuildSpec.input_spec.response_column = responseColumnName;

      autoMLBuildSpec.te_spec.application_strategy = new ThresholdTEApplicationStrategy(fr, 5, new String[]{responseColumnName});
      autoMLBuildSpec.te_spec.max_models = 6;
//      autoMLBuildSpec.te_spec.seed = 1234; // None
      autoMLBuildSpec.te_spec.seed = 2345;

      autoMLBuildSpec.build_control.project_name = "with_te";
      autoMLBuildSpec.build_control.nfolds = 0;   // For validation frame to be used we need to satisfy nfolds == 0.

      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
      autoMLBuildSpec.build_control.keep_cross_validation_models = false;
      autoMLBuildSpec.build_control.keep_cross_validation_predictions = false;

      aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();

      leader = aml.leader();

      double auc = leader.auc();

      //TODO fix this test when we are able to store data preprocessing steps used during training

    } finally {
      if(aml!=null) aml.delete();
      if(amlWithoutTE!=null) amlWithoutTE.delete();
      if(leader!=null) leader.delete();
      if(validationFrame != null)  validationFrame.delete();
      if(leaderboardFrame != null)  leaderboardFrame.delete();
      if(copyOfValidFrame != null)  copyOfValidFrame.delete();
      if(fr != null) fr.delete();
    }
  }

  @Test
  public void when_none_of_TE_parameters_improved_performance_we_fallback_to_original_model() {
    //TODO
  }

  @Test
  public void checkThatAllModelsInTheLeaderboardGotIgnoredColumnsTest() {
    // it is expected that we will have original columns for encodings listed in the `_ignored_columns` array.
//    assertArrayEquals(leaderWithTE._parms._ignored_columns, teApplicationStrategy.getColumnsToEncode());
  }
}