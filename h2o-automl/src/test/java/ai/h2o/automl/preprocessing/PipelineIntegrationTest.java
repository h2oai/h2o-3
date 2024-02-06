package ai.h2o.automl.preprocessing;

import ai.h2o.automl.Algo;
import ai.h2o.automl.AutoML;
import ai.h2o.automl.AutoMLBuildSpec;
import ai.h2o.automl.StepDefinition;
import hex.Model;
import hex.SplitFrame;
import hex.ensemble.StackedEnsembleModel;
import hex.pipeline.PipelineModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.*;
import static water.TestUtil.parseTestFile;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class PipelineIntegrationTest {
  
  
  @Test
  public void test_automl_run_with_cv_enabling_pipelines() {
    try {
      Scope.enter();
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      Frame fr = Scope.track(parseTestFile("./smalldata/titanic/titanic_expanded.csv"));
      SplitFrame sf = new SplitFrame(fr, new double[] { 0.7, 0.3 }, new Key[]{Key.make("titanic_train"), Key.make("titanic_test")});
      sf.exec().get();
      Frame train = Scope.track(sf._destination_frames[0].get());
      Frame test = Scope.track(sf._destination_frames[1].get());
      TestUtil.printOutFrameAsTable(test);

      autoMLBuildSpec.input_spec.training_frame = train._key;
      autoMLBuildSpec.input_spec.response_column = "survived";
      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(20); // sth big enough to test all algos+grids with TE
      autoMLBuildSpec.build_control.stopping_criteria.set_seed(42);
      autoMLBuildSpec.build_control.nfolds = 3;
      autoMLBuildSpec.build_models.preprocessing = new PipelineStepDefinition[] {
              new PipelineStepDefinition(PipelineStepDefinition.Type.TargetEncoding)
      };

      AutoML aml = AutoML.startAutoML(autoMLBuildSpec); Scope.track_generic(aml);
      aml.get();
      System.out.println(aml.leaderboard().toTwoDimTable());
      for (Model m : aml.leaderboard().getModels()) {
        if (m instanceof StackedEnsembleModel) {
          assertFalse(m.haveMojo()); // all SEs should have at least one Pipeline model as a base model which doesn't support MOJO
          assertFalse(m.havePojo());
        } else {
          assertTrue(m instanceof PipelineModel);
        }
      }
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_automl_run_with_cv_enabling_pipelines_scored_by_leaderboard_frame() {
    try {
      Scope.enter();
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      Frame fr = Scope.track(parseTestFile("./smalldata/titanic/titanic_expanded.csv"));
      SplitFrame sf = new SplitFrame(fr, new double[] { 0.7, 0.3 }, new Key[]{Key.make("titanic_train"), Key.make("titanic_test")});
      sf.exec().get();
      Frame train = Scope.track(sf._destination_frames[0].get());
      Frame test = Scope.track(sf._destination_frames[1].get());
      TestUtil.printOutFrameAsTable(test);

      autoMLBuildSpec.input_spec.training_frame = train._key;
//      autoMLBuildSpec.input_spec.validation_frame = test._key;
      autoMLBuildSpec.input_spec.leaderboard_frame = test._key;
      autoMLBuildSpec.input_spec.response_column = "survived";
      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(12); // sth big enough to have some grid
      autoMLBuildSpec.build_control.stopping_criteria.set_seed(42);
      autoMLBuildSpec.build_control.nfolds = 3;
      autoMLBuildSpec.build_models.preprocessing = new PipelineStepDefinition[] {
              new PipelineStepDefinition(PipelineStepDefinition.Type.TargetEncoding)
      };
      autoMLBuildSpec.build_models.modeling_plan = new StepDefinition[] {
              new StepDefinition(Algo.GLM.name()),
              new StepDefinition(Algo.XGBoost.name(), StepDefinition.Alias.grids),
              new StepDefinition(Algo.GBM.name(), StepDefinition.Alias.grids),
              new StepDefinition(Algo.StackedEnsemble.name(), StepDefinition.Alias.defaults),
      };

      AutoML aml = AutoML.startAutoML(autoMLBuildSpec); Scope.track_generic(aml);
      aml.get();
      System.out.println(aml.leaderboard().toTwoDimTable());
      for (Model m : aml.leaderboard().getModels()) {
        if (m instanceof StackedEnsembleModel) {
          assertFalse(m.haveMojo()); // all SEs should have at least one Pipeline model as a base model which doesn't support MOJO
          assertFalse(m.havePojo());
        } else {
          assertTrue(m instanceof PipelineModel);
        }
      }
    } finally {
      Scope.exit();
    }
  }
  
  @Test
  public void test_automl_run_without_cv_enabling_pipelines_scored_by_leaderboard_frame() {
    try {
      Scope.enter();
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      Frame fr = Scope.track(parseTestFile("./smalldata/titanic/titanic_expanded.csv"));
      SplitFrame sf = new SplitFrame(fr, new double[] { 0.7, 0.3 }, new Key[]{Key.make("titanic_train"), Key.make("titanic_test")});
      sf.exec().get();
      Frame train = Scope.track(sf._destination_frames[0].get());
      Frame test = Scope.track(sf._destination_frames[1].get());
      TestUtil.printOutFrameAsTable(test);

      autoMLBuildSpec.input_spec.training_frame = train._key;
      autoMLBuildSpec.input_spec.validation_frame = test._key;
      autoMLBuildSpec.input_spec.response_column = "survived";
      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(12); // sth big enough to have some grid
      autoMLBuildSpec.build_control.stopping_criteria.set_seed(42);
      autoMLBuildSpec.build_control.nfolds = 0;
      autoMLBuildSpec.build_models.preprocessing = new PipelineStepDefinition[] {
              new PipelineStepDefinition(PipelineStepDefinition.Type.TargetEncoding)
      };
      autoMLBuildSpec.build_models.modeling_plan = new StepDefinition[] {
              new StepDefinition(Algo.GLM.name()),
              new StepDefinition(Algo.XGBoost.name(), StepDefinition.Alias.grids),
              new StepDefinition(Algo.GBM.name(), StepDefinition.Alias.grids),
              new StepDefinition(Algo.StackedEnsemble.name(), StepDefinition.Alias.defaults),
      };

     AutoML aml = AutoML.startAutoML(autoMLBuildSpec); Scope.track_generic(aml);
      aml.get();
      System.out.println(aml.leaderboard().toTwoDimTable());
      for (Model m : aml.leaderboard().getModels()) {
        if (m instanceof StackedEnsembleModel) {
          assertFalse(m.haveMojo()); // all SEs should have at least one Pipeline model as a base model which doesn't support MOJO
          assertFalse(m.havePojo());
        } else {
          assertTrue(m instanceof PipelineModel);
        }
      }
    } finally {
      Scope.exit();
    }
  }
  
  
}
