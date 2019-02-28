package ai.h2o.automl;

import ai.h2o.automl.targetencoding.strategy.*;
import hex.Model;
import hex.splitframe.ShuffleSplitFrame;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.fvec.Vec;

import static org.junit.Assert.*;

public class TEGridSearchIntegrationWithAutoMLCVTest extends water.TestUtil {

  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  private Frame getPreparedTitanicFrame(String responseColumnName) {
    Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
    fr.remove(new String[]{"name", "ticket", "boat", "body"});
    asFactor(fr, responseColumnName);
    return fr;
  }

  @Test public void autoAssignedFoldsCVScenarioTest() {
    AutoML aml=null;
    Frame fr=null;
    Frame frForWithoutTE=null;
    Model leader = null;
    Frame trainingFrame = null;
    String responseColumnName = "survived";
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      
      fr = getPreparedTitanicFrame(responseColumnName);

      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.build_control.nfolds = 5;
      autoMLBuildSpec.input_spec.response_column = responseColumnName;

      Vec responseColumn = fr.vec(responseColumnName);
      TEApplicationStrategy thresholdTEApplicationStrategy = new ThresholdTEApplicationStrategy(fr, responseColumn, 5);
      
      int numberOfIterations = 100;
      long seed = autoMLBuildSpec.te_spec.seed;
      TEParamsSelectionStrategy gridSearchTEParamsSelectionStrategy =  
              new GridSearchTEParamsSelectionStrategy(fr, numberOfIterations, responseColumnName, thresholdTEApplicationStrategy.getColumnsToEncode(), true, seed);;

      autoMLBuildSpec.te_spec.application_strategy = thresholdTEApplicationStrategy;
      autoMLBuildSpec.te_spec.params_selection_strategy = gridSearchTEParamsSelectionStrategy;

      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
      autoMLBuildSpec.build_control.keep_cross_validation_models = false;
      autoMLBuildSpec.build_control.keep_cross_validation_predictions = false;

      aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();

      leader = aml.leader();

      double aucWithTE = leader.auc();

      trainingFrame = aml.getTrainingFrame();

      frForWithoutTE = getPreparedTitanicFrame(responseColumnName);
      Leaderboard leaderboardWithoutTE = trainAutoMLWithoutTE( frForWithoutTE,responseColumnName, seed);
      double aucWithoutTE = leaderboardWithoutTE.getLeader().auc();
      
      System.out.println("Performance with TE: AUC = " + aucWithTE);
      System.out.println("Performance without TE: AUC = " + aucWithoutTE);
      
      assertTrue(aucWithTE > aucWithoutTE);

      assertNotEquals(" Two frames should be different.", fr, trainingFrame);

    } finally {
      if(leader!=null) leader.delete();
      if(aml!=null) aml.delete();
      if(trainingFrame != null)  trainingFrame.delete();
      if(fr != null) fr.delete();
      if(frForWithoutTE != null) frForWithoutTE.delete();
    }
  }

  @Test public void customFoldColumnCVScenarioTest() {
  }

  @Test public void gridSearchEndedUpWithKFoldStrategyTest() {
  }
  
  @Test public void testThatDifferentTESearchSpacesAreBeingChosenDependingOnValidationModeOfAutoMLTest() {
    
  }

    //With nfolds being set we will use CV early stopping for AutoML search and therefore only DataLeakageHandlingStrategy.None target encoding strategy is going to be used. 
    //Otherwise we will overfit to dataleakage that target encoding introduces with KFold and LOO strategies.
  @Test public void gridSearchStrategyWithFinalBenchmarkOnHoldoutTest() {
    AutoML aml=null;
    Frame fr=null;
    Model leader = null;
    Frame trainingFrame = null;
    String responseColumnName = "survived";
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();

      fr = getPreparedTitanicFrame(responseColumnName);

      double[] ratios = ard(0.8, 0.2);
      Key<Frame>[] keys = aro(Key.<Frame>make("test.hex"), Key.<Frame>make("train.hex"));
      Frame[] splits = null;
      splits = ShuffleSplitFrame.shuffleSplitFrame(fr, keys, ratios, 42);

      autoMLBuildSpec.input_spec.training_frame = splits[0]._key;
      autoMLBuildSpec.build_control.nfolds = 5;
      autoMLBuildSpec.input_spec.response_column = responseColumnName;

      Vec responseColumn = fr.vec(responseColumnName);
      TEApplicationStrategy thresholdTEApplicationStrategy = new ThresholdTEApplicationStrategy(fr, responseColumn, 5);

      int numberOfIterations = 100; // TODO Need to introduce early stopping.
      long seed = autoMLBuildSpec.te_spec.seed;
      TEParamsSelectionStrategy gridSearchTEParamsSelectionStrategy =
              new GridSearchTEParamsSelectionStrategy(fr, numberOfIterations, responseColumnName, thresholdTEApplicationStrategy.getColumnsToEncode(), true, seed);;

      autoMLBuildSpec.te_spec.application_strategy = thresholdTEApplicationStrategy;
      autoMLBuildSpec.te_spec.params_selection_strategy = gridSearchTEParamsSelectionStrategy;

      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
      autoMLBuildSpec.build_control.keep_cross_validation_models = false;
      autoMLBuildSpec.build_control.keep_cross_validation_predictions = false;

      aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();

      leader = aml.leader();
      leader.score(splits[1]);
      hex.ModelMetricsBinomial mmWithTE = hex.ModelMetricsBinomial.getFromDKV(leader, splits[1]);


      double aucWithTE = mmWithTE.auc();

      trainingFrame = aml.getTrainingFrame();

      Leaderboard leaderboardWithoutTE = trainAutoMLWithoutTE(splits[0], responseColumnName, seed);
      Model withoutTELeader = leaderboardWithoutTE.getLeader();
      withoutTELeader.score(splits[1]);
      hex.ModelMetricsBinomial mmWithoutTE = hex.ModelMetricsBinomial.getFromDKV(withoutTELeader, splits[1]);
      double aucWithoutTE = mmWithoutTE.auc();

      System.out.println("Performance on holdout split with TE: AUC = " + aucWithTE);
      System.out.println("Performance on holdout split without TE: AUC = " + aucWithoutTE);

      assertTrue(aucWithTE > aucWithoutTE);

      assertNotEquals(" Two frames should be different.", fr, trainingFrame);

    } finally {
      if(leader!=null) leader.delete();
      if(aml!=null) aml.delete();
      if(trainingFrame != null)  trainingFrame.delete();
      if(fr != null) fr.delete();
    }
  }

  private Leaderboard trainAutoMLWithoutTE(Frame trainingSplit, String responseColumnName, long seed) {
    Leaderboard leaderboard=null;
    Scope.enter();
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();

      autoMLBuildSpec.input_spec.training_frame = trainingSplit._key;
      autoMLBuildSpec.build_control.nfolds = 5;
      autoMLBuildSpec.input_spec.response_column = responseColumnName;

      autoMLBuildSpec.te_spec.enabled = false;

      autoMLBuildSpec.build_control.project_name = "without_te";
      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
      autoMLBuildSpec.build_control.stopping_criteria.set_seed(7890);
      autoMLBuildSpec.build_control.keep_cross_validation_models = false;
      autoMLBuildSpec.build_control.keep_cross_validation_predictions = false;

      AutoML aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();
      leaderboard = aml.leaderboard();
    } finally {
      Scope.exit();

    }

    return leaderboard;
  }

}
