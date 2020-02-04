package ai.h2o.automl.targetencoder;

import ai.h2o.automl.Algo;
import ai.h2o.automl.AutoML;
import ai.h2o.automl.AutoMLBuildSpec;
import ai.h2o.automl.StepDefinition;
import ai.h2o.targetencoding.strategy.AllCategoricalTEApplicationStrategy;
import ai.h2o.targetencoding.strategy.TEApplicationStrategy;
import ai.h2o.targetencoding.strategy.ThresholdTEApplicationStrategy;
import hex.Model;
import hex.ScoreKeeper;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import static ai.h2o.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;
import static org.junit.Assert.assertTrue;

// This test suite also checks how we apply preprocessing at ModellingStep and then revert all the changes in the data afterwards
public class TEIntegrationWithAutoMLTest extends water.TestUtil {

  @BeforeClass public static void setup() { stall_till_cloudsize(1); }


  public static Frame getPreparedTitanicFrame(String responseColumnName) {
    Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
    fr.remove("name").remove();
    fr.remove("ticket").remove();
    fr.remove("boat").remove();
    fr.remove("body").remove();
    asFactor(fr, responseColumnName);
    return fr;
  }

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

  @Test
  public void automaticTE_is_being_applied_with_cv_automl_case() {
    AutoML aml=null;
    Scope.enter();
    Model leader = null;
    String foldColumnName = "fold";
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();

      String responseColumnName = "survived";
      Frame fr = getPreparedTitanicFrame(responseColumnName);
      Scope.track(fr);

      int nfolds = 5;
      addKFoldColumn(fr, foldColumnName, nfolds, 3456);

      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.fold_column = foldColumnName;
      autoMLBuildSpec.input_spec.response_column = responseColumnName;

      autoMLBuildSpec.te_spec.application_strategy = new AllCategoricalTEApplicationStrategy(fr, new String[]{responseColumnName});
      autoMLBuildSpec.te_spec.max_models = 2;
      autoMLBuildSpec.te_spec.seed = 2345;

      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
      autoMLBuildSpec.build_control.stopping_criteria.set_stopping_metric(ScoreKeeper.StoppingMetric.AUC);
      autoMLBuildSpec.build_control.keep_cross_validation_models = true;
      autoMLBuildSpec.build_control.keep_cross_validation_predictions = true;
      autoMLBuildSpec.build_models.modeling_plan = new StepDefinition[] {
              new StepDefinition(Algo.GBM.name(), new String[]{ "def_1" })
      };

      aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();

      leader = aml.leader();

      Frame trainingFrame = aml.getTrainingFrame();
      Scope.track(trainingFrame);

      //Check that trainingFrame was not affected by TE data preprocessing
      String[] encodedColumns = {"sex_te", "cabin_te", "home.dest_te", "embarked_te"};
      assertIdenticalUpToRelTolerance(fr, trainingFrame, 0, true, "Two frames should be identical.");

      leader.score(fr).delete();

      hex.ModelMetricsBinomial mmb = hex.ModelMetricsBinomial.getFromDKV(leader, fr);
      assertTrue(mmb.auc() > /*score without preprocessing for a given seed*/ 0.0); //TODO

    } finally {
      if(leader!=null) leader.delete();
      if(aml!=null) aml.delete();
    }
  }

  @Test // test does not leak keys. Is it expected?
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

  @Test
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