package ai.h2o.automl;

import ai.h2o.automl.targetencoding.BlendingParams;
import ai.h2o.automl.targetencoding.TargetEncoder;
import ai.h2o.automl.targetencoding.TargetEncoderFrameHelper;
import ai.h2o.automl.targetencoding.TargetEncodingParams;
import ai.h2o.automl.targetencoding.strategy.AllCategoricalTEApplicationStrategy;
import ai.h2o.automl.targetencoding.strategy.FixedTEParamsStrategy;
import ai.h2o.automl.targetencoding.strategy.TEApplicationStrategy;
import ai.h2o.automl.targetencoding.strategy.TEParamsSelectionStrategy;
import hex.Model;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;

import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;
import static org.junit.Assert.assertArrayEquals;

/**
 * Have to keep test class here since most of the methods in AutoML, LeaderBoard are package private.
 */
public class AutoMLWithTETitanicBenchmark extends TestUtil {


  @BeforeClass public static void setup() {
    stall_till_cloudsize(1);
  }

  int numberOfComparedModels = 2;
  Algo[] excludeAlgos = {Algo.DeepLearning, Algo.DRF/*, Algo.GLM*/, Algo.XGBoost, Algo.GBM, Algo.StackedEnsemble};

  @Test
  public void checkThatWithTEWeAreGettingBetterPerformanceTest() {
    AutoML aml=null;
    Frame fr=null;
    Leaderboard leaderboardWithTE = null;
    String responseColumnName = "survived";
    String foldColumnName = "fold";

    Scope.enter();

    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      fr.remove(new String[]{"name", "ticket", "boat", "body"});
      
      long seed = 1235L;

      addKFoldColumn(fr, foldColumnName, 5, seed);
      TargetEncoderFrameHelper.factorColumn(fr, responseColumnName);

      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.fold_column = foldColumnName;
      autoMLBuildSpec.input_spec.response_column = responseColumnName;

      TargetEncodingParams targetEncodingParams = new TargetEncodingParams(new BlendingParams(3, 1), TargetEncoder.DataLeakageHandlingStrategy.KFold, 0.01);
      TEParamsSelectionStrategy fixedTEParamsStrategy =  new FixedTEParamsStrategy(targetEncodingParams);

      Vec responseColumn = fr.vec(responseColumnName);
      TEApplicationStrategy teApplicationStrategy = new AllCategoricalTEApplicationStrategy(fr, responseColumn); 

      autoMLBuildSpec.te_spec.application_strategy = teApplicationStrategy;
      autoMLBuildSpec.te_spec.params_selection_strategy = fixedTEParamsStrategy;
      autoMLBuildSpec.te_spec.seed = seed;

      autoMLBuildSpec.build_models.exclude_algos = excludeAlgos;

      autoMLBuildSpec.build_control.project_name = "with_te";
      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(numberOfComparedModels);
      autoMLBuildSpec.build_control.keep_cross_validation_models = false;
      autoMLBuildSpec.build_control.keep_cross_validation_predictions = false;

      aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();

      leaderboardWithTE = aml.leaderboard();

      Leaderboard leaderboardWithoutTE = trainAutoMLWithoutTE(responseColumnName, foldColumnName, seed);
      
      System.out.println("LeaderBoard withTE ");

      for (Model model : leaderboardWithTE.getModels()) {
        System.out.println(model._parms.fullName());
        // it is expected that we will have original columns for encodings listed in the `_ignored_columns` array.
        assertArrayEquals(model._parms._ignored_columns, teApplicationStrategy.getColumnsToEncode());
      }

      System.out.println("LeaderBoard withoutTE ");

      for(Model model : leaderboardWithoutTE.getModels()) {
        System.out.println(model._parms.fullName());
      }
      

      double aucWithoutTE = leaderboardWithoutTE.getLeader().auc();
      double aucWithTE = leaderboardWithTE.getLeader().auc();
      
      System.out.println("AUC without TE = " + aucWithoutTE);
      System.out.println("AUC with TE = " + aucWithTE);
      
      Assert.assertTrue(aucWithoutTE < aucWithTE);
      
      leaderboardWithoutTE.delete();
    } finally {
      Scope.exit();
    }
  }

  private Leaderboard trainAutoMLWithoutTE(String responseColumnName, String foldColumnName, long seed) {
    Leaderboard leader=null;
    Scope.enter();
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      Frame training = parse_test_file("./smalldata/gbm_test/titanic.csv");
      training.remove(new String[]{"name", "ticket", "boat", "body"});
      
      addKFoldColumn(training, foldColumnName, 5, seed);
  
      TargetEncoderFrameHelper.factorColumn(training, responseColumnName);
  
      autoMLBuildSpec.input_spec.training_frame = training._key;
      autoMLBuildSpec.input_spec.fold_column = foldColumnName;
      autoMLBuildSpec.input_spec.response_column = responseColumnName;
  
      autoMLBuildSpec.te_spec.enabled = false;

      autoMLBuildSpec.build_models.exclude_algos = excludeAlgos;

      autoMLBuildSpec.build_control.project_name = "without_te";
      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(numberOfComparedModels);
      autoMLBuildSpec.build_control.keep_cross_validation_models = false;
      autoMLBuildSpec.build_control.keep_cross_validation_predictions = false;
  
      AutoML aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();
      leader = aml.leaderboard();
    } finally {
      Scope.exit();

    }
    return leader;
  }
  
}
