package ai.h2o.automl.targetencoder;


import ai.h2o.automl.leaderboard.Leaderboard;
import hex.Model;
import hex.splitframe.ShuffleSplitFrame;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;

public class AutoMLBenchmarkHelper extends TestUtil {

  public static Frame getPreparedTitanicFrame(String responseColumnName) {
    Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
    fr.remove("name").remove();
    fr.remove("ticket").remove();
    fr.remove("boat").remove();
    fr.remove("body").remove();
    asFactor(fr, responseColumnName);
    return fr;
  }

  static public Frame[] getRandomSplitsFromDataframe(Frame fr, double[] ratios, long splitSeed) {
    Key<Frame>[] keys = new Key[ratios.length];
    for (int i = 0; i < ratios.length; i++) {
      keys[i] = Key.make();
    }
    Frame[] splits = null;
    splits = ShuffleSplitFrame.shuffleSplitFrame(fr, keys, ratios, splitSeed);
    return splits;
  }

  public static double getScoreBasedOn(Frame fr, Model model) {
    model.score(fr).delete();
    hex.ModelMetricsBinomial mmb = hex.ModelMetricsBinomial.getFromDKV(model, fr);
    return mmb.auc();
  }

  public static double getCumulativeLeaderboardScore(Frame split, Leaderboard leaderboard) {
    double cumulative = 0.0;
    for (Model model : leaderboard.getModels()) {
      cumulative += getScoreBasedOn(split, model);
    }
    return cumulative;
  }

  public static double getCumulativeAUCScore(Leaderboard leaderboard) {
    double cumulative = 0.0;
    for (Model model : leaderboard.getModels()) {
      cumulative += model.auc();
    }
    return cumulative;
  }
}
