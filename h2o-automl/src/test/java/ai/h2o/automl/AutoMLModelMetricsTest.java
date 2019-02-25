package ai.h2o.automl;

import hex.Model;
import hex.splitframe.ShuffleSplitFrame;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.fvec.Frame;

import static org.junit.Assert.assertEquals;

public class AutoMLModelMetricsTest extends water.TestUtil {

  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  private Frame getPreparedTitanicFrame(String responseColumnName) {
    Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
    fr.remove(new String[]{"name", "ticket", "boat", "body"});
    asFactor(fr, responseColumnName);
    return fr;
  }
  
  private Frame[] getSplitsFromTitanicDataset(String responseColumnName) {
    Frame fr = getPreparedTitanicFrame(responseColumnName);

    double[] ratios = ard(0.7, 0.15, 0.15);
    Key<Frame>[] keys = aro(Key.<Frame>make(), Key.<Frame>make(), Key.<Frame>make());
    Frame[] splits = null;
    splits = ShuffleSplitFrame.shuffleSplitFrame(fr, keys, ratios, 42);

    return splits;
  }
  
  private double getScoreBasedOn(Frame fr, Model model) {
    model.score(fr);
    hex.ModelMetricsBinomial mmWithoutTE = hex.ModelMetricsBinomial.getFromDKV(model, fr);
    return mmWithoutTE.auc();
  }

  @Test public void scoresOnLeaderBoardFrameAndTheSameFrameAfterwardsShouldBeEqualTest() {

    String responseColumnName = "survived";

    Frame[] splits = getSplitsFromTitanicDataset(responseColumnName);

    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
    autoMLBuildSpec.input_spec.training_frame = splits[0]._key;
    autoMLBuildSpec.input_spec.validation_frame = splits[1]._key;
    autoMLBuildSpec.input_spec.leaderboard_frame = splits[2]._key;
    autoMLBuildSpec.build_control.nfolds = 0;
    autoMLBuildSpec.input_spec.response_column = responseColumnName;

    autoMLBuildSpec.build_control.project_name = "first";
    autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
    autoMLBuildSpec.build_control.stopping_criteria.set_seed(7890);
    autoMLBuildSpec.build_control.keep_cross_validation_models = false;
    autoMLBuildSpec.build_control.keep_cross_validation_predictions = false;

    AutoML aml = AutoML.startAutoML(autoMLBuildSpec);
    aml.get();
    Leaderboard leaderboard = aml.leaderboard();

    // It is expected that auc() called on leader will return value estimated on `leaderboard_frame` but it is not the case.
    // We are getting auc() from `validation_frame` as notion of `leaderboard_frame` is outside of the model. 
    // So we need a proxy around model that will be returned from getLeader() and provide us metrics that were used to make this model a leader.
    double auc = leaderboard.getLeader().auc();
    
    printOutFrameAsTable(splits[2], false, 20);

    double aucSeparatelyCalculated = getScoreBasedOn(splits[2], leaderboard.getLeader());
    
    assertEquals(auc, aucSeparatelyCalculated, 1e-5);
    
  }

}
