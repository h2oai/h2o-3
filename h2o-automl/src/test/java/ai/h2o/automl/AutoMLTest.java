package ai.h2o.automl;

import ai.h2o.automl.StepDefinition.Step;
import ai.h2o.automl.leaderboard.*;
import hex.Model;
import hex.ScoreKeeper;
import hex.SplitFrame;
import hex.deeplearning.DeepLearningModel;
import hex.ensemble.StackedEnsembleModel;
import hex.glm.GLMModel;
import hex.leaderboard.LeaderboardCell;
import hex.tree.SharedTreeModel.SharedTreeParameters;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBMModel;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostModel.XGBoostParameters;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.*;
import water.exceptions.H2OAutoMLException;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.junit.rules.DKVIsolation;
import water.junit.rules.ScopeTracker;
import water.logging.LoggingLevel;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.ArrayUtils;
import water.util.Log;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static ai.h2o.automl.ModelingStep.ModelStep.DEFAULT_MODEL_GROUP;
import static ai.h2o.automl.ModelingStep.ModelStep.DEFAULT_MODEL_TRAINING_WEIGHT;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.*;
import static water.TestUtil.aro;
import static water.TestUtil.parseTestFile;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class AutoMLTest {
  
  @Rule
  public ScopeTracker scope = new ScopeTracker();
  
  @Rule
  public DKVIsolation isolation = new DKVIsolation();
  
  @Test public void test_basic_automl_behaviour_using_cv() {
    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
    Frame fr = parseTestFile("./smalldata/logreg/prostate_train.csv");
    String target = "CAPSULE";
    int tidx = fr.find(target);
    fr.replace(tidx, fr.vec(tidx).toCategoricalVec()).remove(); DKV.put(fr);
    scope.track(fr);
    autoMLBuildSpec.input_spec.training_frame = fr._key;
    autoMLBuildSpec.input_spec.response_column = target;
    autoMLBuildSpec.input_spec.sort_metric = "AUCPR";
//      autoMLBuildSpec.build_models.exclude_algos = new Algo[] {Algo.XGBoost};
    int maxModels = 5;

//      autoMLBuildSpec.build_models.exploitation_ratio = 1;
    autoMLBuildSpec.build_control.stopping_criteria.set_seed(1);
    autoMLBuildSpec.build_control.stopping_criteria.set_max_models(maxModels);
    autoMLBuildSpec.build_control.keep_cross_validation_models = false; //Prevent leaked keys from CV models
    autoMLBuildSpec.build_control.keep_cross_validation_predictions = false; //Prevent leaked keys from CV predictions
    autoMLBuildSpec.build_models.modeling_plan = ModelingPlans.TWO_LAYERED;

    AutoML aml = scope.track(AutoML.startAutoML(autoMLBuildSpec));
    aml.get();
    Log.info(aml.leaderboard().toLogString());

    Key[] modelKeys = aml.leaderboard().getModelKeys();
    int count_se = 0, count_non_se = 0;
    for (Key k : modelKeys) if (k.toString().startsWith("StackedEnsemble")) count_se++; else count_non_se++;

    assertEquals("wrong amount of standard models", maxModels, count_non_se);
    assertEquals("wrong amount of SE models", 2, count_se);
    assertEquals(maxModels+2, aml.leaderboard().getModelCount());
  }

  //important test: the basic execution path is very different when CV is disabled
  // being for model training but also default leaderboard scoring
  // also allows us to keep an eye on memory leaks.
  @Test public void test_automl_with_cv_disabled() {
    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
    Frame fr = scope.track(parseTestFile("./smalldata/logreg/prostate_train.csv"));
    autoMLBuildSpec.input_spec.training_frame = fr._key;
    autoMLBuildSpec.input_spec.response_column = "CAPSULE";

    autoMLBuildSpec.build_control.stopping_criteria.set_max_models(3);
    autoMLBuildSpec.build_control.nfolds = 0;
    autoMLBuildSpec.build_models.modeling_plan = ModelingPlans.TWO_LAYERED;

    AutoML aml = scope.track(AutoML.startAutoML(autoMLBuildSpec));
    aml.get();

    Key[] modelKeys = aml.leaderboard().getModelKeys();
    int count_se = 0, count_non_se = 0;
    for (Key k : modelKeys) if (k.toString().startsWith("StackedEnsemble")) count_se++; else count_non_se++;

    assertEquals("wrong amount of standard models", 3, count_non_se);
    assertEquals("no Stacked Ensemble expected if cross-validation is disabled", 0, count_se);
    assertEquals(3, aml.leaderboard().getModelCount());
  }
  
  @Test public void test_stacked_ensembles_trained_with_blending_frame_if_provided() {
    final int seed = 62832;
    final Frame fr = parseTestFile("./smalldata/logreg/prostate_train.csv");
    final Frame test = parseTestFile("./smalldata/logreg/prostate_test.csv");
      
    String target = "CAPSULE";
    int tidx = fr.find(target);
    fr.replace(tidx, fr.vec(tidx).toCategoricalVec()).remove(); DKV.put(fr); scope.track(fr);
    test.replace(tidx, test.vec(tidx).toCategoricalVec()).remove(); DKV.put(test); scope.track(test);
      
    SplitFrame sf = new SplitFrame(fr, new double[] { 0.7, 0.3 }, null);
    sf.exec().get();
    Key<Frame>[] ksplits = sf._destination_frames;
    final Frame train = ksplits[0].get(); scope.track(train);
    final Frame blending = ksplits[1].get(); scope.track(blending);

    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
    autoMLBuildSpec.input_spec.training_frame = train._key;
    autoMLBuildSpec.input_spec.blending_frame = blending._key;
    autoMLBuildSpec.input_spec.leaderboard_frame = test._key;
    autoMLBuildSpec.input_spec.response_column = target;

    autoMLBuildSpec.build_control.stopping_criteria.set_max_models(5);
    autoMLBuildSpec.build_control.nfolds = 0;
    autoMLBuildSpec.build_control.stopping_criteria.set_seed(seed);
    autoMLBuildSpec.build_models.modeling_plan = ModelingPlans.TWO_LAYERED;

    AutoML aml = scope.track(AutoML.startAutoML(autoMLBuildSpec));
    aml.get();
      
    Key[] modelKeys = aml.leaderboard().getModelKeys();
    int count_se = 0, count_non_se = 0;
    for (Key k : modelKeys) if (k.toString().startsWith("StackedEnsemble")) count_se++; else count_non_se++;

    assertEquals("wrong amount of standard models", 5, count_non_se);
    assertEquals("wrong amount of SE models", 2, count_se);
    assertEquals(7, aml.leaderboard().getModelCount());
  }


  @Test public void test_no_stacked_ensemble_trained_if_only_one_algo() {
    final int seed = 62832;
    final Frame train = parseTestFile("./smalldata/logreg/prostate_train.csv");
    String target = "CAPSULE";
    int tidx = train.find(target);
    train.replace(tidx, train.vec(tidx).toCategoricalVec()).remove(); DKV.put(train); scope.track(train);

    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
    autoMLBuildSpec.input_spec.training_frame = train._key;
    autoMLBuildSpec.input_spec.response_column = target;

    autoMLBuildSpec.build_control.stopping_criteria.set_max_models(3);
    autoMLBuildSpec.build_control.stopping_criteria.set_seed(seed);
    autoMLBuildSpec.build_models.include_algos = aro(Algo.GBM);
    autoMLBuildSpec.build_models.modeling_plan = ModelingPlans.TWO_LAYERED;

    AutoML aml = scope.track(AutoML.startAutoML(autoMLBuildSpec));
    aml.get();

    Key[] modelKeys = aml.leaderboard().getModelKeys();
    int count_se = 0, count_non_se = 0;
    for (Key k : modelKeys) if (k.toString().startsWith("StackedEnsemble")) count_se++; else count_non_se++;

    assertEquals("wrong amount of standard models", 3, count_non_se);
    assertEquals("wrong amount of SE models", 0, count_se);
    assertEquals(3, aml.leaderboard().getModelCount());
  }



  // timeout can cause interruption of steps at various levels, for example from top to bottom:
  //  - interruption after an AutoML model has been trained, preventing addition of more models
  //  - interruption when building the main model (if CV enabled)
  //  - interruption when building a CV model (for example right after building a tree)
  // we want to leave the memory in a clean state after any of those interruptions.
  // this test uses a slightly random timeout to ensure it will interrupt the training at various steps
  @Test public void test_automl_basic_behaviour_on_timeout() {
    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
    Frame fr = scope.track(parseTestFile("./smalldata/logreg/prostate_train.csv"));
    autoMLBuildSpec.input_spec.training_frame = fr._key;
    autoMLBuildSpec.input_spec.response_column = "CAPSULE";

    autoMLBuildSpec.build_control.stopping_criteria.set_max_runtime_secs(1+new Random().nextInt(30));
    autoMLBuildSpec.build_control.keep_cross_validation_models = false; //Prevent leaked keys from CV models
    autoMLBuildSpec.build_control.keep_cross_validation_predictions = false; //Prevent leaked keys from CV predictions
    autoMLBuildSpec.build_models.modeling_plan = ModelingPlans.TWO_LAYERED;

    AutoML aml = scope.track(AutoML.startAutoML(autoMLBuildSpec));
    aml.get();
    // no assertion, we just want to check leaked keys
  }

  @Test public void test_automl_basic_behaviour_on_grid_timeout() {
    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
    Frame fr = scope.track(parseTestFile("./smalldata/logreg/prostate_train.csv"));
    autoMLBuildSpec.input_spec.training_frame = fr._key;
    autoMLBuildSpec.input_spec.response_column = "CAPSULE";
    autoMLBuildSpec.build_models.exclude_algos = new Algo[] {Algo.XGBoost, Algo.DeepLearning, Algo.DRF, Algo.GLM};

    autoMLBuildSpec.build_control.stopping_criteria.set_max_runtime_secs(15);
    autoMLBuildSpec.build_control.keep_cross_validation_models = false; //Prevent leaked keys from CV models
    autoMLBuildSpec.build_control.keep_cross_validation_predictions = false; //Prevent leaked keys from CV predictions
    autoMLBuildSpec.build_models.modeling_plan = ModelingPlans.TWO_LAYERED;

    AutoML aml = scope.track(AutoML.startAutoML(autoMLBuildSpec));
    aml.get();
    // no assertion, we just want to check leaked keys
  }
  
  @Test public void test_automl_behaviour_when_using_both_max_models_and_max_runtime_secs() {
    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
    Frame fr = scope.track(parseTestFile("./smalldata/logreg/prostate_train.csv"));
    autoMLBuildSpec.input_spec.training_frame = fr._key;
    autoMLBuildSpec.input_spec.response_column = "CAPSULE";
//      autoMLBuildSpec.build_models.exclude_algos = new Algo[] {Algo.XGBoost, Algo.DeepLearning, Algo.DRF, Algo.GLM};
    autoMLBuildSpec.build_control.stopping_criteria.set_max_runtime_secs(10);
    autoMLBuildSpec.build_control.stopping_criteria.set_max_models(100);
    autoMLBuildSpec.build_control.keep_cross_validation_models = true;  // to inspect CV models below
    autoMLBuildSpec.build_control.stopping_criteria.set_seed(42);

    AutoML aml = scope.track(AutoML.startAutoML(autoMLBuildSpec, true));
    aml.get();

    System.out.println(aml.leaderboard().toTwoDimTable(ModelProvider.COLUMN.getName(), ModelStep.COLUMN.getName()).toString());
    //as max_models is provided, no time budget is assigned to the models by default, 
    // even when user also provides max_runtime_secs: in this case, the latter only acts as a global limit
    // and cancels the last training step.
    StepResultState[] steps = aml._stepsResults;
    assertTrue("shouldn't have managed to train all max_models", steps.length < autoMLBuildSpec.build_control.stopping_criteria.max_models());
    StepResultState lastStep = steps[steps.length - 1];
    Log.info("last step = "+lastStep);
    assertTrue("last model training should have been cancelled", 
            lastStep.is(StepResultState.ResultStatus.cancelled)      // if timeout during model training
                    || lastStep.is(StepResultState.ResultStatus.success));   // if timeout between models
    if (lastStep.is(StepResultState.ResultStatus.success)) {
      String[] tokens = lastStep.id().split(":");
      String provider = tokens[0];
      String stepId = tokens[1];
      // verify that the last trained model was actually trained without any time limit, 
      //  including all the associated CV models.
      Map<Key<Model>, LeaderboardCell[]> extensions = aml.leaderboard().getExtensionsAsMap();
      Key<Model> model_id = extensions.entrySet().stream()
              .filter(e -> provider.equals(Arrays.stream(e.getValue())
                      .filter(c -> c.getColumn() == ModelProvider.COLUMN)
                      .findFirst().get().getValue()) 
                      && stepId.equals(Arrays.stream(e.getValue())
                      .filter(c -> c.getColumn() == ModelStep.COLUMN)
                      .findFirst().get().getValue())
              ).map(Map.Entry::getKey)
              .findFirst().orElse(null);
      assertNotNull(model_id);
      Model model = DKV.getGet(model_id);
      Key<Model>[] cvModels =  model._output._cross_validation_models;
      for (Key<Model> key : cvModels) {
        Model cvModel = DKV.getGet(key);
        assertEquals(0.0, cvModel._parms._max_runtime_secs, 0.0);
      }
    }
    Log.info(Scope.current());
  }


  @Ignore
  @Test public void test_individual_model_max_runtime() {
    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
//      fr = parseTestFile("./smalldata/prostate/prostate_complete.csv"); //using slightly larger dataset to make this test useful
//      autoMLBuildSpec.input_spec.response_column = "CAPSULE";
    Frame fr = scope.track(parseTestFile("./smalldata/diabetes/diabetes_text_train.csv")); //using slightly larger dataset to make this test useful
    autoMLBuildSpec.input_spec.response_column = "diabetesMed";
    autoMLBuildSpec.input_spec.training_frame = fr._key;

    int max_runtime_secs_per_model = 10;
    autoMLBuildSpec.build_models.exclude_algos = aro(Algo.GLM, Algo.DeepLearning); // GLM still tends to take a bit more time than it should: nothing dramatic, but enough to fail UTs.
    autoMLBuildSpec.build_control.stopping_criteria.set_seed(1);
    autoMLBuildSpec.build_control.stopping_criteria.set_max_models(10);
    autoMLBuildSpec.build_control.stopping_criteria.set_max_runtime_secs_per_model(max_runtime_secs_per_model);
    autoMLBuildSpec.build_control.keep_cross_validation_models = false; //Prevent leaked keys from CV models
    autoMLBuildSpec.build_control.keep_cross_validation_predictions = false; //Prevent leaked keys from CV predictions
    autoMLBuildSpec.build_models.modeling_plan = ModelingPlans.TWO_LAYERED;

    AutoML aml = scope.track(AutoML.startAutoML(autoMLBuildSpec));
    aml.get();

    int tolerance = (autoMLBuildSpec.build_control.nfolds + 1) * max_runtime_secs_per_model / 3; //generously adding 33% tolerance for each cv model + final model
    for (Key<Model> key : aml.leaderboard().getModelKeys()) {
      Model model = key.get();
      double duration = model._output._total_run_time / 1e3;
      assertTrue(key + " took longer than required: "+ duration,
              duration - max_runtime_secs_per_model < tolerance);
    }
  }

  @Test public void test_keep_cross_validation_enabled() {
    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
    Frame fr = scope.track(parseTestFile("./smalldata/logreg/prostate_train.csv"));
    autoMLBuildSpec.input_spec.training_frame = fr._key;
    autoMLBuildSpec.input_spec.response_column = "CAPSULE";
    autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
    autoMLBuildSpec.build_control.keep_cross_validation_fold_assignment = true;
    autoMLBuildSpec.build_models.modeling_plan = ModelingPlans.TWO_LAYERED;

    AutoML aml = scope.track(AutoML.startAutoML(autoMLBuildSpec));
    aml.get();

    Model leader = scope.<Model>track(aml.leader());

    assertTrue(leader !=null && leader._parms._keep_cross_validation_fold_assignment);
    assertNotNull(leader._output._cross_validation_fold_assignment_frame_id);
  }

  @Test public void test_keep_cross_validation_fold_assignment_disabled() {
    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
    Frame fr = scope.track(parseTestFile("./smalldata/airlines/AirlinesTrain.csv"));
    autoMLBuildSpec.input_spec.training_frame = fr._key;
    autoMLBuildSpec.input_spec.response_column = "IsDepDelayed";
    autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
    autoMLBuildSpec.build_control.keep_cross_validation_fold_assignment = false;
    autoMLBuildSpec.build_models.modeling_plan = ModelingPlans.TWO_LAYERED;

    AutoML aml = scope.track(AutoML.startAutoML(autoMLBuildSpec));
    aml.get();

    Model leader = scope.<Model>track(aml.leader());

    assertTrue(leader !=null && !leader._parms._keep_cross_validation_fold_assignment);
    assertNull(leader._output._cross_validation_fold_assignment_frame_id);
  }

  @Test public void test_work_plan_without_exploitation() {
    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
    Frame fr = scope.track(parseTestFile("./smalldata/airlines/allyears2k_headers.zip"));
    autoMLBuildSpec.input_spec.training_frame = fr._key;
    autoMLBuildSpec.input_spec.response_column = "IsDepDelayed";
    autoMLBuildSpec.build_models.exploitation_ratio = 0;
    autoMLBuildSpec.build_models.modeling_plan = ModelingPlans.ONE_LAYERED;
    AutoML aml = new AutoML(autoMLBuildSpec);
    DKV.put(scope.track(aml)); // adding manually for easy tracking as we're just calling `planWork`
    Map<Algo, Integer> defaultAllocs = new HashMap<Algo, Integer>(){{
      put(Algo.DeepLearning, 1*10+3*15); // models+grids
      put(Algo.DRF, 2*10);
      put(Algo.GBM, 5*10+1*60); // models+grids
      put(Algo.GLM, 1*10);
      put(Algo.XGBoost, 3*10+1*90); // models+grids
      put(Algo.StackedEnsemble, 2*10);
    }};
    int maxTotalWork = 0;
    for (Map.Entry<Algo, Integer> entry : defaultAllocs.entrySet()) {
      if (entry.getKey().enabled()) {
        maxTotalWork += entry.getValue();
      }
    }
    aml.planWork();
    assertEquals(maxTotalWork, aml._workAllocations.remainingWork());

    autoMLBuildSpec.build_models.exclude_algos = aro(Algo.DeepLearning, Algo.DRF);
    aml.planWork();

    assertEquals(maxTotalWork - defaultAllocs.get(Algo.DeepLearning) - defaultAllocs.get(Algo.DRF), aml._workAllocations.remainingWork());
  }

  @Test public void test_work_plan_with_exploitation() {
    double exploitationRatio = 0.2;
    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
    Frame fr = scope.track(parseTestFile("./smalldata/airlines/allyears2k_headers.zip"));
    autoMLBuildSpec.input_spec.training_frame = fr._key;
    autoMLBuildSpec.input_spec.response_column = "IsDepDelayed";
    autoMLBuildSpec.build_models.exploitation_ratio = exploitationRatio;
    autoMLBuildSpec.build_models.modeling_plan = ModelingPlans.ONE_LAYERED;
    AutoML aml = new AutoML(autoMLBuildSpec);
    DKV.put(scope.track(aml)); // adding manually for easy tracking as we're just calling `planWork`
    aml.planWork();

    Map<Algo, Integer> explorationAllocs = new HashMap<Algo, Integer>(){{
      put(Algo.DeepLearning, 1*10+3*15); // models+grids
      put(Algo.DRF, 2*10);
      put(Algo.GBM, 5*10+1*60); // models+grids
      put(Algo.GLM, 1*10);
      put(Algo.XGBoost, 3*10+1*90); // models+grids
      put(Algo.StackedEnsemble, 2*10);
    }};
    Map<Algo, Integer> exploitationAllocs = new HashMap<Algo, Integer>(){{
      put(Algo.GBM, 1*10);
      put(Algo.XGBoost, 1*30);
    }};
    int expectedExplorationWork = explorationAllocs.entrySet().stream().filter(algo -> algo.getKey().enabled()).mapToInt(Map.Entry::getValue).sum();

    Function<AutoML, Double> computeExploitationRatio = automl -> {
      int explorationWork = automl._workAllocations.remainingWork(ModelingStep.isExplorationWork);
      int exploitationWork = automl._workAllocations.remainingWork(ModelingStep.isExploitationWork);
      return (double)exploitationWork/(explorationWork+exploitationWork);
    };

    assertEquals(expectedExplorationWork, aml._workAllocations.remainingWork(ModelingStep.isExplorationWork));
    assertEquals(expectedExplorationWork, aml._workAllocations.remainingWork() * (1 - exploitationRatio), 1);
    assertEquals(exploitationRatio, computeExploitationRatio.apply(aml), 0.1);

    autoMLBuildSpec.build_models.exclude_algos = aro(Algo.DeepLearning, Algo.DRF);
    aml.planWork();
    expectedExplorationWork = expectedExplorationWork - explorationAllocs.get(Algo.DeepLearning) - explorationAllocs.get(Algo.DRF);
    assertEquals(expectedExplorationWork, aml._workAllocations.remainingWork(ModelingStep.isExplorationWork));
    assertEquals(expectedExplorationWork, aml._workAllocations.remainingWork() * (1 - exploitationRatio), 1);
    assertEquals(exploitationRatio, computeExploitationRatio.apply(aml), 0.01);

    int totalExploitationWork = exploitationAllocs.entrySet().stream().filter(algo -> algo.getKey().enabled()).mapToInt(Map.Entry::getValue).sum();
    double expectedGBMExploitationRatio = (double)exploitationAllocs.get(Algo.GBM) / totalExploitationWork;
    double computedGBMExploitationRatio = (double)aml._workAllocations.remainingWork(ModelingStep.isExploitationWork.and(w -> w._algo == Algo.GBM)) / aml._workAllocations.remainingWork(ModelingStep.isExploitationWork);
    assertEquals(expectedGBMExploitationRatio, computedGBMExploitationRatio, 0.01);
  }

  @Test public void test_training_plan() {
    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
    Frame fr = scope.track(parseTestFile("./smalldata/logreg/prostate_train.csv"));
    autoMLBuildSpec.input_spec.training_frame = fr._key;
    autoMLBuildSpec.input_spec.response_column = "CAPSULE";
    autoMLBuildSpec.build_models.modeling_plan = new StepDefinition[] {
            new StepDefinition(Algo.GBM.name(), "def_1"),                             // 1 model
            new StepDefinition(Algo.GLM.name(), StepDefinition.Alias.all),            // 1 model
            new StepDefinition(Algo.DRF.name(), new Step("XRT", 2, 20)),  // 1 model
            new StepDefinition(Algo.XGBoost.name(), StepDefinition.Alias.grids),      // 1 grid
            new StepDefinition(Algo.DeepLearning.name(), StepDefinition.Alias.grids), // 1 grid
            new StepDefinition(Algo.StackedEnsemble.name(), StepDefinition.Alias.defaults)   // 2 groups = 2 models (all SEs are redundant and ignored)
    };
    autoMLBuildSpec.build_models.exclude_algos = new Algo[] {Algo.XGBoost, Algo.DeepLearning};
    AutoML aml = scope.track(AutoML.startAutoML(autoMLBuildSpec));
    aml.get();
    System.out.println(aml.leaderboard().toTwoDimTable("step", "group").toString());

    assertEquals(5, aml.leaderboard().getModelCount());
    assertEquals(1, Stream.of(aml.leaderboard().getModels()).filter(GBMModel.class::isInstance).count());
    assertEquals(1, Stream.of(aml.leaderboard().getModels()).filter(GLMModel.class::isInstance).count());
    assertEquals(1, Stream.of(aml.leaderboard().getModels()).filter(DRFModel.class::isInstance).count());
    assertEquals(0, Stream.of(aml.leaderboard().getModels()).filter(XGBoostModel.class::isInstance).count());
    assertEquals(0, Stream.of(aml.leaderboard().getModels()).filter(DeepLearningModel.class::isInstance).count());
    assertEquals(2, Stream.of(aml.leaderboard().getModels()).filter(StackedEnsembleModel.class::isInstance).count()); //one for each group

    assertNotNull(aml._actualModelingSteps);
    Log.info(Arrays.toString(aml._actualModelingSteps));
    assertArrayEquals(new StepDefinition[] {
            new StepDefinition(Algo.GBM.name(), new Step("def_1", DEFAULT_MODEL_GROUP, DEFAULT_MODEL_TRAINING_WEIGHT)),
            new StepDefinition(Algo.GLM.name(), new Step("def_1", DEFAULT_MODEL_GROUP, DEFAULT_MODEL_TRAINING_WEIGHT)),
            new StepDefinition(Algo.StackedEnsemble.name(), new Step("best_of_family_1", 1, DEFAULT_MODEL_TRAINING_WEIGHT)),
            new StepDefinition(Algo.DRF.name(), new Step("XRT", 2, 20)),
            new StepDefinition(Algo.StackedEnsemble.name(), new Step("best_of_family_2", 2, DEFAULT_MODEL_TRAINING_WEIGHT)),
    }, aml._actualModelingSteps);
  }

  @Test public void test_training_frame_partition_when_cv_disabled_and_validation_frame_missing() {
    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
    Frame fr = scope.track(parseTestFile("./smalldata/logreg/prostate_train.csv"));
    Frame test = scope.track(parseTestFile("./smalldata/logreg/prostate_test.csv"));
    autoMLBuildSpec.input_spec.response_column = "CAPSULE";
    autoMLBuildSpec.input_spec.training_frame = fr._key;
    autoMLBuildSpec.input_spec.validation_frame = null;
    autoMLBuildSpec.input_spec.leaderboard_frame = test._key;
    autoMLBuildSpec.build_control.nfolds = 0;
    autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
    autoMLBuildSpec.build_control.stopping_criteria.set_seed(1);
    autoMLBuildSpec.build_models.modeling_plan = ModelingPlans.TWO_LAYERED;
    AutoML aml = scope.track(AutoML.startAutoML(autoMLBuildSpec));
    aml.get();
    double tolerance = 1e-2;
    assertEquals(0.9, (double)aml.getTrainingFrame().numRows() / fr.numRows(), tolerance);
    assertEquals(0.1, (double)aml.getValidationFrame().numRows() / fr.numRows(), tolerance);
    assertEquals(test.numRows(), aml.getLeaderboardFrame().numRows());
  }

  @Test public void  test_training_frame_partition_when_cv_disabled_and_leaderboard_frame_missing() {
    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
    Frame fr = scope.track(parseTestFile("./smalldata/logreg/prostate_train.csv"));
    Frame test = scope.track(parseTestFile("./smalldata/logreg/prostate_test.csv"));
    autoMLBuildSpec.input_spec.response_column = "CAPSULE";
    autoMLBuildSpec.input_spec.training_frame = fr._key;
    autoMLBuildSpec.input_spec.validation_frame = test._key;
    autoMLBuildSpec.input_spec.leaderboard_frame = null;
    autoMLBuildSpec.build_control.nfolds = 0;
    autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
    autoMLBuildSpec.build_control.stopping_criteria.set_seed(1);
    autoMLBuildSpec.build_models.modeling_plan = ModelingPlans.TWO_LAYERED;
    AutoML aml = scope.track(AutoML.startAutoML(autoMLBuildSpec));
    aml.get();
    double tolerance = 1e-2;
    assertEquals(1, (double)aml.getTrainingFrame().numRows() / fr.numRows(), tolerance);
    assertEquals(test.numRows(), aml.getValidationFrame().numRows());
    assertEquals(aml.getValidationFrame().numRows(), aml.getLeaderboardFrame().numRows());
  }

  @Test public void test_training_frame_partition_when_cv_disabled_and_both_validation_and_leaderboard_frames_missing() {
    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
    Frame fr = scope.track(parseTestFile("./smalldata/logreg/prostate_train.csv"));
    autoMLBuildSpec.input_spec.response_column = "CAPSULE";
    autoMLBuildSpec.input_spec.training_frame = fr._key;
    autoMLBuildSpec.input_spec.validation_frame = null;
    autoMLBuildSpec.input_spec.leaderboard_frame = null;
    autoMLBuildSpec.build_control.nfolds = 0;
    autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
    autoMLBuildSpec.build_control.stopping_criteria.set_seed(1);
    autoMLBuildSpec.build_models.modeling_plan = ModelingPlans.TWO_LAYERED;
    AutoML aml = scope.track(AutoML.startAutoML(autoMLBuildSpec));
    aml.get();
    double tolerance = 1e-2;
    assertEquals(0.9, (double)aml.getTrainingFrame().numRows() / fr.numRows(), tolerance);
    assertEquals(0.1, (double)aml.getValidationFrame().numRows() / fr.numRows(), tolerance);
    assertEquals(0.1, (double)aml.getLeaderboardFrame().numRows() / fr.numRows(), tolerance);
  }

  @Test public void test_training_frame_not_partitioned_when_cv_enabled() {
    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
    Frame fr = scope.track(parseTestFile("./smalldata/logreg/prostate_train.csv"));
    autoMLBuildSpec.input_spec.response_column = "CAPSULE";
    autoMLBuildSpec.input_spec.training_frame = fr._key;
    autoMLBuildSpec.input_spec.validation_frame = null;
    autoMLBuildSpec.input_spec.leaderboard_frame = null;
    autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
    autoMLBuildSpec.build_control.stopping_criteria.set_seed(1);
    autoMLBuildSpec.build_models.modeling_plan = ModelingPlans.TWO_LAYERED;
    AutoML aml = scope.track(AutoML.startAutoML(autoMLBuildSpec));
    aml.get();
    assertEquals(fr.numRows(), aml.getTrainingFrame().numRows());
    assertNull(aml.getValidationFrame());
    assertNull(aml.getLeaderboardFrame());
  }

  @Test public void test_exclude_algos() {
    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
    Frame fr = scope.track(parseTestFile("./smalldata/airlines/allyears2k_headers.zip"));
    autoMLBuildSpec.input_spec.training_frame = fr._key;
    autoMLBuildSpec.input_spec.response_column = "IsDepDelayed";
    autoMLBuildSpec.build_models.exclude_algos = new Algo[] {Algo.DeepLearning, Algo.XGBoost, };
    autoMLBuildSpec.build_models.modeling_plan = ModelingPlans.TWO_LAYERED;
    AutoML aml = scope.track(new AutoML(autoMLBuildSpec));
    DKV.put(scope.track(aml)); // adding manually for easy tracking as we're just calling `planWork`
    aml.planWork();
    for (IAlgo algo : autoMLBuildSpec.build_models.exclude_algos) {
      assertEquals(0, aml._workAllocations.getAllocations(w -> w._algo == algo).length);
    }
    for (Algo algo : Algo.values()) {
      if (!ArrayUtils.contains(autoMLBuildSpec.build_models.exclude_algos, algo)) {
        assertNotEquals(0, aml._workAllocations.getAllocations(w -> w._algo == algo).length);
      }
    }
  }

  @Test public void test_include_algos() {
    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
    Frame fr = scope.track(parseTestFile("./smalldata/airlines/allyears2k_headers.zip"));
    autoMLBuildSpec.input_spec.training_frame = fr._key;
    autoMLBuildSpec.input_spec.response_column = "IsDepDelayed";
    autoMLBuildSpec.build_models.include_algos = new Algo[] {Algo.DeepLearning, Algo.XGBoost, };
    autoMLBuildSpec.build_models.modeling_plan = ModelingPlans.TWO_LAYERED;
    AutoML aml = new AutoML(autoMLBuildSpec); 
    DKV.put(scope.track(aml)); // adding manually for easy tracking as we're just calling `planWork`
    aml.planWork();
    for (IAlgo algo : autoMLBuildSpec.build_models.include_algos) {
      if (algo.enabled()) {
        assertNotEquals(0, aml._workAllocations.getAllocations(w -> w._algo == algo).length);
      } else {
        assertEquals(0, aml._workAllocations.getAllocations(w -> w._algo == algo).length);
      }
    }
    for (Algo algo : Algo.values()) {
      if (!ArrayUtils.contains(autoMLBuildSpec.build_models.include_algos, algo)) {
        assertEquals(0, aml._workAllocations.getAllocations(w -> w._algo == algo).length);
      }
    }
  }

  @Test public void test_exclude_include_algos() {
    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
    Frame fr = scope.track(parseTestFile("./smalldata/airlines/allyears2k_headers.zip"));
    autoMLBuildSpec.input_spec.training_frame = fr._key;
    autoMLBuildSpec.input_spec.response_column = "IsDepDelayed";
    autoMLBuildSpec.build_models.exclude_algos = new Algo[] {Algo.GBM, Algo.GLM, };
    autoMLBuildSpec.build_models.include_algos = new Algo[] {Algo.DeepLearning, Algo.XGBoost, };
    autoMLBuildSpec.build_models.modeling_plan = ModelingPlans.TWO_LAYERED;
    try {
      new AutoML(autoMLBuildSpec);
      fail("Should have thrown an H2OIllegalArgumentException for providing both include_algos and exclude_algos");
    } catch (H2OIllegalArgumentException e) {
      assertTrue(e.getMessage().startsWith("Parameters `exclude_algos` and `include_algos` are mutually exclusive"));
    }
  }


  @Test public void test_algos_have_default_parameters_enforcing_reproducibility() {
    int maxModels = 20; // generating enough models so that we can check every algo x every mode (single model + grid models)
    int seed = 0;
    int nfolds = 0;  //this test currently fails if CV is enabled due to PUBDEV-6385 (the final model gets its `stopping_rounds` param reset to 0)
    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
    Frame fr = scope.track(parseTestFile("./smalldata/logreg/prostate.csv"));
    autoMLBuildSpec.input_spec.training_frame = fr._key;
    autoMLBuildSpec.input_spec.response_column = "CAPSULE";

    autoMLBuildSpec.build_control.stopping_criteria.set_max_models(maxModels);
    autoMLBuildSpec.build_control.nfolds = nfolds;
    autoMLBuildSpec.build_control.stopping_criteria.set_seed(seed);
    autoMLBuildSpec.build_models.modeling_plan = ModelingPlans.TWO_LAYERED;

    AutoML aml = scope.track(AutoML.startAutoML(autoMLBuildSpec));
    aml.get();
    assertEquals(maxModels+(nfolds > 0 ? 2 : 0), aml.leaderboard().getModelCount());

    Key[] modelKeys = aml.leaderboard().getModelKeys();
    Map<Algo, List<Key<Model>>> keysByAlgo = new HashMap<>();
    for (Algo algo : Algo.values()) keysByAlgo.put(algo, new ArrayList<>());
    for (Key k : modelKeys) {
      if (k.toString().startsWith("XRT")) {
        keysByAlgo.get(Algo.DRF).add(k);
      } else for (Algo algo: Algo.values()) {
        if (k.toString().startsWith(algo.name())) {
          keysByAlgo.get(algo).add(k);
          break;
        }
      }
    }

    // verify that all keys were categorized
    int count = 0; for (List<Key<Model>> keys : keysByAlgo.values()) count += keys.size();
    assertEquals(aml.leaderboard().getModelCount(), count);

    // check parameters constraints that should be set for all models
    for (Algo algo: Algo.values()) {
      Set<Long> collectedSeeds = new HashSet<>(); // according to AutoML logic, no model for same algo should have the same seed.
      List<Key<Model>> keys = keysByAlgo.get(algo);
      for (Key<Model> key : keys) {
        Model.Parameters parameters = key.get()._parms;
        assertTrue(parameters._seed != -1);
        assertTrue(key+":"+parameters._seed, Math.abs(parameters._seed - seed) < maxModels);
        collectedSeeds.add(parameters._seed);
        assertTrue(key+" has `stopping_rounds` param set to "+parameters._stopping_rounds,
                parameters._stopping_rounds == 3 || algo == Algo.GLM);  // stopping criteria only disabled for GLM (using lambda search)
      }
      assertTrue(collectedSeeds.size() > 1 || keys.size() < 2);  // we should have built enough models to guarantee this
    }

    //check model specific constraints
    for (Algo algo : Arrays.asList(Algo.XGBoost)) {
      List<Key<Model>> keys = keysByAlgo.get(algo);
      for (Key<Model> key : keys) {
        XGBoostParameters parameters = (XGBoostParameters)key.get()._parms;
        assertEquals(5, parameters._score_tree_interval);
        assertEquals(3, parameters._stopping_rounds); //should probably not be left enforced/hardcoded for XGB?
      }
    }

    for (Algo algo : Arrays.asList(Algo.DRF, Algo.GBM)) {
      List<Key<Model>> keys = keysByAlgo.get(algo);
      for (Key<Model> key : keys) {
        SharedTreeParameters parameters = (SharedTreeParameters)key.get()._parms;
        assertEquals(5, parameters._score_tree_interval);
      }
    }
  }
  
  @Test(expected = H2OAutoMLException.class)
  public void test_run_fails_after_multiple_consecutive_model_failures() {
    AutoML aml = null;
    try {
      int seed = 0;
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      Frame fr = Scope.track(parseTestFile("./smalldata/extdata/australia.csv")); //regression task
      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.response_column = "runoffnew";
      // no model limit
      autoMLBuildSpec.build_models.exclude_algos = new Algo[] {Algo.GLM}; // our GLM ignores stopping metric, probably due to lambda search, ad therefore doesn't fail, making the test logic more complex
      autoMLBuildSpec.build_control.stopping_criteria.set_seed(seed);
      autoMLBuildSpec.build_control.stopping_criteria.set_stopping_metric(ScoreKeeper.StoppingMetric.lift_top_group);  // stopping metric incompatible with regression
      autoMLBuildSpec.build_models.modeling_plan = ModelingPlans.TWO_LAYERED;

      aml = scope.track(AutoML.startAutoML(autoMLBuildSpec));
      Scope.track_generic(aml);
      aml.get();
    } catch (Exception e) {
      long count = Arrays.stream(aml.eventLog()._events).filter(ev -> ev.getLevel() == LoggingLevel.ERROR).count();
      assertEquals(aml._maxConsecutiveModelFailures, count);
      assertEquals(0, aml.leaderboard().getModelCount());
      throw e;
    }
  }
}
