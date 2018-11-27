package ai.h2o.automl;

import hex.Model;
import hex.SplitFrame;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Lockable;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.*;

public class AutoMLTest extends water.TestUtil {

  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  @Test public void test_basic_automl_behaviour_using_cv() {
    AutoML aml=null;
    Frame fr=null;
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      fr = parse_test_file("./smalldata/logreg/prostate_train.csv");
      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.response_column = "CAPSULE";

      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(3);
      autoMLBuildSpec.build_control.keep_cross_validation_models = false; //Prevent leaked keys from CV models
      autoMLBuildSpec.build_control.keep_cross_validation_predictions = false; //Prevent leaked keys from CV predictions

      aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();

      Key[] modelKeys = aml.leaderboard().getModelKeys();
      int count_se = 0, count_non_se = 0;
      for (Key k : modelKeys) if (k.toString().startsWith("StackedEnsemble")) count_se++; else count_non_se++;

      assertEquals("wrong amount of standard models", 3, count_non_se);
      assertEquals("wrong amount of SE models", 2, count_se);
      assertEquals(3+2, aml.leaderboard().getModelCount());
    } finally {
      // Cleanup
      if(aml!=null) aml.deleteWithChildren();
      if(fr != null) fr.delete();
    }
  }

  //important test: the basic execution path is very different when CV is disabled
  // being for model training but also default leaderboard scoring
  // also allows us to keep an eye on memory leaks.
  @Test public void test_automl_with_cv_disabled() {
    AutoML aml=null;
    Frame fr=null;
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      fr = parse_test_file("./smalldata/logreg/prostate_train.csv");
      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.response_column = "CAPSULE";

      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(3);
      autoMLBuildSpec.build_control.nfolds = 0;

      aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();

      Key[] modelKeys = aml.leaderboard().getModelKeys();
      int count_se = 0, count_non_se = 0;
      for (Key k : modelKeys) if (k.toString().startsWith("StackedEnsemble")) count_se++; else count_non_se++;

      assertEquals("wrong amount of standard models", 3, count_non_se);
      assertEquals("no Stacked Ensemble expected if cross-validation is disabled", 0, count_se);
      assertEquals(3, aml.leaderboard().getModelCount());
    } finally {
      // Cleanup
      if(aml!=null) aml.deleteWithChildren();
      if(fr != null) fr.delete();
    }
  }
  
  @Test public void test_stacked_ensembles_trained_with_blending_frame_if_provided() {
    List<Lockable> deletables = new ArrayList<>();
    try {
      final int seed = 62832;
      final Frame fr = parse_test_file("./smalldata/logreg/prostate_train.csv"); deletables.add(fr);
      final Frame test = parse_test_file("./smalldata/logreg/prostate_test.csv"); deletables.add(test);
      
      String target = "CAPSULE";
      int tidx = fr.find(target);
      fr.replace(tidx, fr.vec(tidx).toCategoricalVec()).remove(); DKV.put(fr); deletables.add(fr);
      test.replace(tidx, test.vec(tidx).toCategoricalVec()).remove(); DKV.put(test); deletables.add(test);
      
      SplitFrame sf = new SplitFrame(fr, new double[] { 0.7, 0.3 }, null);
      sf.exec().get();
      Key<Frame>[] ksplits = sf._destination_frames;
      final Frame train = ksplits[0].get(); deletables.add(train);
      final Frame blending = ksplits[1].get(); deletables.add(blending);

      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      autoMLBuildSpec.input_spec.training_frame = train._key;
      autoMLBuildSpec.input_spec.blending_frame = blending._key;
      autoMLBuildSpec.input_spec.leaderboard_frame = test._key;
      autoMLBuildSpec.input_spec.response_column = target;

      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(3);
      autoMLBuildSpec.build_control.nfolds = 0;
      autoMLBuildSpec.build_control.stopping_criteria.set_seed(seed);

      AutoML aml = AutoML.startAutoML(autoMLBuildSpec); deletables.add(aml);
      aml.get();
      
      Key[] modelKeys = aml.leaderboard().getModelKeys();
      int count_se = 0, count_non_se = 0;
      for (Key k : modelKeys) if (k.toString().startsWith("StackedEnsemble")) count_se++; else count_non_se++;

      assertEquals("wrong amount of standard models", 3, count_non_se);
      assertEquals("wrong amount of SE models", 2, count_se);
      assertEquals(5, aml.leaderboard().getModelCount());
    } finally {
      // Cleanup
      for (Lockable l: deletables) {
        if (l instanceof AutoML) ((AutoML)l).deleteWithChildren();
        l.delete();
      }
    }
  }


  // timeout can cause interruption of steps at various levels, for example from top to bottom:
  //  - interruption after an AutoML model has been trained, preventing addition of more models
  //  - interruption when building the main model (if CV enabled)
  //  - interruption when building a CV model (for example right after building a tree)
  // we want to leave the memory in a clean state after any of those interruptions.
  // this test uses a slightly random timeout to ensure it will interrupt the training at various steps
  @Test public void test_automl_basic_behaviour_on_timeout() {
    AutoML aml=null;
    Frame fr=null;
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      fr = parse_test_file("./smalldata/logreg/prostate_train.csv");
      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.response_column = "CAPSULE";

      autoMLBuildSpec.build_control.stopping_criteria.set_max_runtime_secs(new Random().nextInt(30));
      autoMLBuildSpec.build_control.keep_cross_validation_models = false; //Prevent leaked keys from CV models
      autoMLBuildSpec.build_control.keep_cross_validation_predictions = false; //Prevent leaked keys from CV predictions

      aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();

      // no assertion, we just want to check leaked keys
    } finally {
      // Cleanup
      if(aml!=null) aml.deleteWithChildren();
      if(fr != null) fr.delete();
    }
  }

  @Test public void test_automl_basic_behaviour_on_grid_timeout() {
    AutoML aml=null;
    Frame fr=null;
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      fr = parse_test_file("./smalldata/logreg/prostate_train.csv");
      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.response_column = "CAPSULE";
      autoMLBuildSpec.build_models.exclude_algos = new Algo[] {Algo.DeepLearning, Algo.DRF, Algo.GLM};

      autoMLBuildSpec.build_control.stopping_criteria.set_max_runtime_secs(8);
//      autoMLBuildSpec.build_control.stopping_criteria.set_max_runtime_secs(new Random().nextInt(30));
      autoMLBuildSpec.build_control.keep_cross_validation_models = false; //Prevent leaked keys from CV models
      autoMLBuildSpec.build_control.keep_cross_validation_predictions = false; //Prevent leaked keys from CV predictions

      aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();

      // no assertion, we just want to check leaked keys
    } finally {
      // Cleanup
      if(aml!=null) aml.deleteWithChildren();
      if(fr != null) fr.delete();
    }
  }


  @Test public void test_individual_model_max_runtime() {
    AutoML aml=null;
    Frame fr=null;
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
//      fr = parse_test_file("./smalldata/prostate/prostate_complete.csv"); //using slightly larger dataset to make this test useful
//      autoMLBuildSpec.input_spec.response_column = "CAPSULE";
      fr = parse_test_file("./smalldata/diabetes/diabetes_text_train.csv"); //using slightly larger dataset to make this test useful
      autoMLBuildSpec.input_spec.response_column = "diabetesMed";
      autoMLBuildSpec.input_spec.training_frame = fr._key;

      int max_model_runtime_secs = 10;
      autoMLBuildSpec.build_models.exclude_algos = new Algo[] {Algo.DRF};
      autoMLBuildSpec.build_control.stopping_criteria.set_seed(1);
      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(3);
      autoMLBuildSpec.build_control.stopping_criteria.set_max_model_runtime_secs(max_model_runtime_secs);
      autoMLBuildSpec.build_control.keep_cross_validation_models = true; //Prevent leaked keys from CV models
      autoMLBuildSpec.build_control.keep_cross_validation_predictions = false; //Prevent leaked keys from CV predictions

      aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();

      int tolerance = (autoMLBuildSpec.build_control.nfolds + 1) * 2; //generously adding 2s tolerance for each model
      for (Key<Model> key : aml.leaderboard().getModelKeys()) {
        Model model = key.get();
        double duration = model._output._run_time;
        Log.info(key + " build duration (s): "+ duration / 1e3);
        if (model._output._cross_validation_models != null) {
          for (Key<Model> kcv : model._output._cross_validation_models) {
            Model cv = kcv.get();
            long cv_duration = cv._output._run_time;
            Log.info(kcv + " build duration (s): "+ cv_duration / 1e3);
            duration += cv_duration;
          }
          Log.info(key + " added build duration (s): "+ duration / 1e3);
        }
        duration = model._output._total_run_time / 1e3;
        Log.info(key + " model total build duration (s): "+ duration);
        assertTrue(key + " took longer than required: "+ duration,
            duration - max_model_runtime_secs < tolerance);
        model.deleteCrossValidationModels();
      }

      // no assertion, we just want to check leaked keys
    } finally {
      // Cleanup
      if(aml!=null) aml.deleteWithChildren();
      if(fr != null) fr.delete();
    }
  }

  @Test public void KeepCrossValidationFoldAssignmentEnabledTest() {
    AutoML aml = null;
    Frame fr = null;
    Model leader = null;
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      fr = parse_test_file("./smalldata/logreg/prostate_train.csv");
      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.response_column = "CAPSULE";
      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
      autoMLBuildSpec.build_control.stopping_criteria.set_max_runtime_secs(30);
      autoMLBuildSpec.build_control.keep_cross_validation_fold_assignment = true;

      aml = AutoML.makeAutoML(Key.<AutoML>make(), new Date(), autoMLBuildSpec);
      AutoML.startAutoML(aml);
      aml.get();

      leader = aml.leader();

      assertTrue(leader !=null && leader._parms._keep_cross_validation_fold_assignment);
      assertNotNull(leader._output._cross_validation_fold_assignment_frame_id);

    } finally {
      if(aml!=null) aml.deleteWithChildren();
      if(fr != null) fr.remove();
      if(leader!=null) {
        Frame cvFoldAssignmentFrame = DKV.getGet(leader._output._cross_validation_fold_assignment_frame_id);
        cvFoldAssignmentFrame.delete();
      }
    }
  }

  @Test public void KeepCrossValidationFoldAssignmentDisabledTest() {
    AutoML aml = null;
    Frame fr = null;
    Model leader = null;
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      fr = parse_test_file("./smalldata/airlines/allyears2k_headers.zip");
      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.response_column = "IsDepDelayed";
      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
      autoMLBuildSpec.build_control.stopping_criteria.set_max_runtime_secs(30);
      autoMLBuildSpec.build_control.keep_cross_validation_fold_assignment = false;

      aml = AutoML.makeAutoML(Key.<AutoML>make(), new Date(), autoMLBuildSpec);
      AutoML.startAutoML(aml);
      aml.get();

      leader = aml.leader();

      assertTrue(leader !=null && !leader._parms._keep_cross_validation_fold_assignment);
      assertNull(leader._output._cross_validation_fold_assignment_frame_id);

    } finally {
      if(aml!=null) aml.deleteWithChildren();
      if(fr != null) fr.delete();
    }
  }

  @Test public void testWorkPlan() {
    AutoML aml = null;
    Frame fr=null;
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      fr = parse_test_file("./smalldata/airlines/allyears2k_headers.zip");
      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.response_column = "IsDepDelayed";
      aml = new AutoML(Key.<AutoML>make(), new Date(), autoMLBuildSpec);

      AutoML.WorkAllocations workPlan = aml.planWork();

      int max_total_work = 1*10+3*20     //DL
                         + 2*10          //DRF
                         + 5*10+1*60     //GBM
                         + 1*20          //GLM
                         + 3*10+1*100    //XGBoost
                         + 2*15;         //SE
      assertEquals(workPlan.remainingWork(), max_total_work);

      autoMLBuildSpec.build_models.exclude_algos = new Algo[] {Algo.DeepLearning, Algo.XGBoost, };
      workPlan = aml.planWork();

      assertEquals(workPlan.remainingWork(), max_total_work - (/*DL*/ 1*10+3*20 + /*XGB*/ 3*10+1*100));

    } finally {
      if (aml != null) aml.deleteWithChildren();
      if (fr != null) fr.remove();
    }
  }

  @Test public void test_training_frame_partition_when_cv_disabled_and_validation_frame_missing() {
    AutoML aml = null;
    Frame fr = null, test = null;
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      fr = parse_test_file("./smalldata/logreg/prostate_train.csv");
      test = parse_test_file("./smalldata/logreg/prostate_test.csv");
      autoMLBuildSpec.input_spec.response_column = "CAPSULE";
      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.validation_frame = null;
      autoMLBuildSpec.input_spec.leaderboard_frame = test._key;
      autoMLBuildSpec.build_control.nfolds = 0;
      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
      autoMLBuildSpec.build_control.stopping_criteria.set_seed(1);
      aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();
      double tolerance = 1e-2;
      assertEquals(0.9, (double)aml.getTrainingFrame().numRows() / fr.numRows(), tolerance);
      assertEquals(0.1, (double)aml.getValidationFrame().numRows() / fr.numRows(), tolerance);
      assertEquals(test.numRows(), aml.getLeaderboardFrame().numRows());
    } finally {
      if (aml != null) aml.deleteWithChildren();
      if (fr != null) fr.remove();
      if (test != null) test.remove();
    }
  }

  @Test public void  test_training_frame_partition_when_cv_disabled_and_leaderboard_frame_missing() {
    AutoML aml = null;
    Frame fr = null, test = null;
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      fr = parse_test_file("./smalldata/logreg/prostate_train.csv");
      test = parse_test_file("./smalldata/logreg/prostate_test.csv");
      autoMLBuildSpec.input_spec.response_column = "CAPSULE";
      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.validation_frame = test._key;
      autoMLBuildSpec.input_spec.leaderboard_frame = null;
      autoMLBuildSpec.build_control.nfolds = 0;
      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
      autoMLBuildSpec.build_control.stopping_criteria.set_seed(1);
      aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();
      double tolerance = 1e-2;
      assertEquals(0.9, (double)aml.getTrainingFrame().numRows() / fr.numRows(), tolerance);
      assertEquals(test.numRows(), aml.getValidationFrame().numRows());
      assertEquals(0.1, (double)aml.getLeaderboardFrame().numRows() / fr.numRows(), tolerance);
    } finally {
      if (aml != null) aml.deleteWithChildren();
      if (fr != null) fr.remove();
      if (test != null) test.remove();
    }
  }

  @Test public void test_training_frame_partition_when_cv_disabled_and_both_validation_and_leaderboard_frames_missing() {
    AutoML aml = null;
    Frame fr = null;
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      fr = parse_test_file("./smalldata/logreg/prostate_train.csv");
      autoMLBuildSpec.input_spec.response_column = "CAPSULE";
      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.validation_frame = null;
      autoMLBuildSpec.input_spec.leaderboard_frame = null;
      autoMLBuildSpec.build_control.nfolds = 0;
      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
      autoMLBuildSpec.build_control.stopping_criteria.set_seed(1);
      aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();
      double tolerance = 1e-2;
      assertEquals(0.8, (double)aml.getTrainingFrame().numRows() / fr.numRows(), tolerance);
      assertEquals(0.1, (double)aml.getValidationFrame().numRows() / fr.numRows(), tolerance);
      assertEquals(0.1, (double)aml.getLeaderboardFrame().numRows() / fr.numRows(), tolerance);
    } finally {
      if (aml != null) aml.deleteWithChildren();
      if (fr != null) fr.remove();
    }
  }

  @Test public void test_training_frame_not_partitioned_when_cv_enabled() {
    AutoML aml = null;
    Frame fr = null;
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      fr = parse_test_file("./smalldata/logreg/prostate_train.csv");
      autoMLBuildSpec.input_spec.response_column = "CAPSULE";
      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.validation_frame = null;
      autoMLBuildSpec.input_spec.leaderboard_frame = null;
      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
      autoMLBuildSpec.build_control.stopping_criteria.set_seed(1);
      aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();
      assertEquals(fr.numRows(), aml.getTrainingFrame().numRows());
      assertNull(aml.getValidationFrame());
      assertNull(aml.getLeaderboardFrame());
    } finally {
      if (aml != null) aml.deleteWithChildren();
      if (fr != null) fr.remove();
    }
  }

  @Test public void testExcludeAlgos() {
    AutoML aml = null;
    Frame fr=null;
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      fr = parse_test_file("./smalldata/airlines/allyears2k_headers.zip");
      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.response_column = "IsDepDelayed";
      autoMLBuildSpec.build_models.exclude_algos = new Algo[] {Algo.DeepLearning, Algo.XGBoost, };
      aml = new AutoML(Key.<AutoML>make(), new Date(), autoMLBuildSpec);
      AutoML.WorkAllocations workPlan = aml.planWork();
      for (Algo algo : autoMLBuildSpec.build_models.exclude_algos) {
        assertNull(workPlan.getAllocation(algo, AutoML.JobType.ModelBuild));
        assertNull(workPlan.getAllocation(algo, AutoML.JobType.HyperparamSearch));
      }
      for (Algo algo : Algo.values()) {
        if (!ArrayUtils.contains(autoMLBuildSpec.build_models.exclude_algos, algo)) {
          assertFalse(
              workPlan.getAllocation(algo, AutoML.JobType.ModelBuild) == null
                  && workPlan.getAllocation(algo, AutoML.JobType.HyperparamSearch) == null
          );
        }
      }
    } finally {
      if (aml != null) aml.deleteWithChildren();
      if (fr != null) fr.remove();
    }
  }

  @Test public void testIncludeAlgos() {
    AutoML aml = null;
    Frame fr=null;
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      fr = parse_test_file("./smalldata/airlines/allyears2k_headers.zip");
      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.response_column = "IsDepDelayed";
      autoMLBuildSpec.build_models.include_algos = new Algo[] {Algo.DeepLearning, Algo.XGBoost, };
      aml = new AutoML(Key.<AutoML>make(), new Date(), autoMLBuildSpec);
      AutoML.WorkAllocations workPlan = aml.planWork();
      for (Algo algo : autoMLBuildSpec.build_models.include_algos) {
        assertFalse(
            workPlan.getAllocation(algo, AutoML.JobType.ModelBuild) == null
            && workPlan.getAllocation(algo, AutoML.JobType.HyperparamSearch) == null
        );
      }
      for (Algo algo : Algo.values()) {
        if (!ArrayUtils.contains(autoMLBuildSpec.build_models.include_algos, algo)) {
          assertNull(workPlan.getAllocation(algo, AutoML.JobType.ModelBuild));
          assertNull(workPlan.getAllocation(algo, AutoML.JobType.HyperparamSearch));
        }
      }
    } finally {
      if (aml != null) aml.deleteWithChildren();
      if (fr != null) fr.remove();
    }
  }

  @Test public void testExcludeIncludeAlgos() {
    AutoML aml = null;
    Frame fr=null;
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      fr = parse_test_file("./smalldata/airlines/allyears2k_headers.zip");
      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.response_column = "IsDepDelayed";
      autoMLBuildSpec.build_models.exclude_algos = new Algo[] {Algo.GBM, Algo.GLM, };
      autoMLBuildSpec.build_models.include_algos = new Algo[] {Algo.DeepLearning, Algo.XGBoost, };
      try {
        aml = new AutoML(Key.<AutoML>make(), new Date(), autoMLBuildSpec);
        fail("Should have thrown an H2OIllegalArgumentException for providing both include_algos and exclude_algos");
      } catch (H2OIllegalArgumentException e) {
        assertTrue(e.getMessage().startsWith("Parameters `exclude_algos` and `include_algos` are mutually exclusive"));
      }
    } finally {
      if (aml != null) aml.deleteWithChildren();
      if (fr != null) fr.remove();
    }
  }
}
