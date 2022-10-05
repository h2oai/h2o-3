package ai.h2o.automl.leaderboard;

import ai.h2o.automl.Algo;
import ai.h2o.automl.AutoML;
import ai.h2o.automl.ModelingStep;
import ai.h2o.automl.dummy.DummyStepsProvider;
import ai.h2o.automl.events.EventLog;
import ai.h2o.automl.events.EventLogEntry;
import hex.Model;
import hex.leaderboard.*;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Key;
import water.Keyed;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.Log;
import water.util.TwoDimTable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static water.TestUtil.parseTestFile;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class LeaderboardTest {

  private static final Key<AutoML> dummy = Key.make();
  
  @Test
  public void test_toTwoDimTable_with_empty_models_and_without_sort_metric() {
    Leaderboard lb = null;
    EventLog eventLog = EventLog.getOrMake(dummy);
    try {
      lb = Leaderboard.getOrMake("dummy_lb_no_sort_metric", eventLog.asLogger(EventLogEntry.Stage.ModelTraining),  new Frame(), null);

      TwoDimTable table = lb.toTwoDimTable();
      assertNotNull("empty leaderboard should also produce a TwoDimTable", table);
      assertEquals("no models in this leaderboard", table.getTableDescription());
    } finally {
      if (lb != null) lb.remove();
      eventLog.remove();
    }
  }

  @Test
  public void test_toTwoDimTable_with_empty_models_and_with_sort_metric() {
    Leaderboard lb = null;
    EventLog eventLog = EventLog.getOrMake(dummy);
    try {
      lb = Leaderboard.getOrMake("dummy_lb_logloss_sort_metric", eventLog.asLogger(EventLogEntry.Stage.ModelTraining),  new Frame(), "logloss");

      TwoDimTable table = lb.toTwoDimTable();
      assertNotNull("empty leaderboard should also produce a TwoDimTable", table);
      assertEquals("no models in this leaderboard", table.getTableDescription());
    } finally {
      if (lb != null) lb.remove();
      eventLog.remove();
    }
  }


  @Test
  public void test_rank_tsv() {
    Leaderboard lb = null;
    EventLog eventLog = EventLog.getOrMake(dummy);
    GBMModel model = null;
    Frame fr  = null;
    try {
      fr = parseTestFile("./smalldata/logreg/prostate_train.csv");
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = fr._key;
      parms._nfolds = 2;
      parms._seed = 1;
      parms._response_column = "CAPSULE";
      GBM job = new GBM(parms);
      model = job.trainModel().get();
      
      lb = Leaderboard.getOrMake("dummy_rank_tsv", eventLog.asLogger(EventLogEntry.Stage.ModelTraining),  null, "mae");
      lb.addModel(model._key);
      Log.info(lb.rankTsv());
      assertEquals("Error\n[0.3448260574357465, 0.44675855535636816, 0.19959320678410908, 0.31468498072970547, 0.19959320678410908]\n", lb.rankTsv());
    } finally {
      if (lb != null){
        lb.remove();
      }
      eventLog.remove();
      if (model != null) {
        model.deleteCrossValidationModels();
        model.delete();
      }
      if( fr != null) {
        fr.delete();
      }
    }
  }

  @Test
  public void test_leaderboard_table_with_extensions() {
    List<Keyed> removables = new ArrayList<>();
    try {
      String target = "CAPSULE";
      final Frame fr = parseTestFile("./smalldata/logreg/prostate_train.csv").toCategoricalCol(target);  removables.add(fr);
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = fr._key;
      parms._nfolds = 3;
      parms._seed = 1;
      parms._response_column = "CAPSULE";
      GBM job = new GBM(parms);
      Model model = job.trainModel().get(); removables.add(model);
      ModelingStep step = new DummyStepsProvider.DummyModelStep(Algo.GBM, "my_gbm", null);

      EventLog eventLog = EventLog.getOrMake(dummy); removables.add(eventLog);
      Leaderboard lb = Leaderboard.getOrMake("leaderboard_with_ext", eventLog.asLogger(EventLogEntry.Stage.ModelTraining),null,null);
      removables.add(lb);
      lb.setExtensionsProvider(new LeaderboardExtensionsProvider() {
        @Override
        public LeaderboardCell[] createExtensions(Model model) {
          return new LeaderboardCell[] {
                  new TrainingTime(model),
                  new ScoringTimePerRow(model, fr),
                  new AlgoName(model),
                  new ModelProvider(model, step),
                  new ModelStep(model, step),
                  new ModelGroup(model, step),
          };
        }
      });
      lb.addModel(model._key);
      TwoDimTable lb_table = lb.toTwoDimTable();
      assertEquals(1, lb_table.getRowDim()); // one model, one row
      assertEquals(7, lb_table.getColDim()); // model_id + 6 binomial metrics
      assertEquals("Leaderboard for project leaderboard_with_ext", lb_table.getTableHeader());
      assertEquals("models sorted in order of auc, best first", lb_table.getTableDescription());
      assertArrayEquals(new String[] {"model_id", "auc", "logloss", "aucpr", "mean_per_class_error", "rmse", "mse"},
              lb_table.getColHeaders());
      assertArrayEquals(new String[] {"string", "double", "double", "double", "double", "double", "double"},
              lb_table.getColTypes());
      assertArrayEquals(new String[] {"%s", "%.6f", "%.6f", "%.6f", "%.6f", "%.6f", "%.6f"},
              lb_table.getColFormats());

      TwoDimTable lb_table_ext = lb.toTwoDimTable(LeaderboardExtensionsProvider.ALL);
      assertEquals(7+3, lb_table_ext.getColDim());
      assertArrayEquals(new String[] {"model_id", "auc", "logloss", "aucpr", "mean_per_class_error", "rmse", "mse", "training_time_ms", "predict_time_per_row_ms", "algo"},
              lb_table_ext.getColHeaders());
      assertArrayEquals(new String[] {"string", "double", "double", "double", "double", "double", "double", "long", "double", "string"},
              lb_table_ext.getColTypes());
      assertArrayEquals(new String[] {"%s", "%.6f", "%.6f", "%.6f", "%.6f", "%.6f", "%.6f", "%s", "%.6f", "%s"},
              lb_table_ext.getColFormats());
      assertTrue(lb_table_ext.get(0, 7 /*training_time_ms*/) instanceof Long);
      assertTrue(lb_table_ext.get(0, 8 /*predict_time_per_row_ms*/) instanceof Double);
      assertTrue(lb_table_ext.get(0, 9 /*algo*/) instanceof String);
      assertTrue((Long)lb_table_ext.get(0, 7) > 0);
      assertTrue((Double)lb_table_ext.get(0, 8) > 0);
      assertEquals("GBM", lb_table_ext.get(0, 9));

      TwoDimTable lb_table_custom_ext = lb.toTwoDimTable("training_time_ms");
      assertEquals(7+1, lb_table_custom_ext.getColDim());
      assertArrayEquals(new String[] {"model_id", "auc", "logloss", "aucpr", "mean_per_class_error", "rmse", "mse", "training_time_ms"},
              lb_table_custom_ext.getColHeaders());

      TwoDimTable lb_table_custom_unknown = lb.toTwoDimTable("unknown", "training_time_ms");
      assertEquals(7+1, lb_table_custom_unknown.getColDim());
      assertArrayEquals(new String[] {"model_id", "auc", "logloss", "aucpr", "mean_per_class_error", "rmse", "mse", "training_time_ms"},
              lb_table_custom_unknown.getColHeaders());

      TwoDimTable lb_table_custom_hidden = lb.toTwoDimTable("provider", "step", "group");
      assertEquals(7+3, lb_table_custom_hidden.getColDim());
      assertArrayEquals(new String[] {"model_id", "auc", "logloss", "aucpr", "mean_per_class_error", "rmse", "mse", "provider", "step", "group"},
              lb_table_custom_hidden.getColHeaders());
      assertArrayEquals(new String[] {"string", "double", "double", "double", "double", "double", "double", "string", "string", "int"},
              lb_table_custom_hidden.getColTypes());
      assertEquals(step.getProvider(), lb_table_custom_hidden.get(0, 7 /*provider*/));
      assertEquals(step.getId(), lb_table_custom_hidden.get(0, 8 /*step*/));
      assertEquals(step.getPriorityGroup(), lb_table_custom_hidden.get(0, 9 /*group*/));

    } finally {
      for (Keyed item : removables) item.remove(true);
    }
  }
}
