package ai.h2o.automl;

import hex.Model;
import hex.splitframe.ShuffleSplitFrame;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.rapids.StratificationAssistant;

public class AutoMLBenchmarkingHelper extends TestUtil {


  static public Frame[] getRandomSplitsFromDataframe(Frame fr, double[] ratios, long splitSeed) {
    Key<Frame>[] keys = aro(Key.<Frame>make(), Key.<Frame>make(), Key.<Frame>make());
    Frame[] splits = null;
    splits = ShuffleSplitFrame.shuffleSplitFrame(fr, keys, ratios, splitSeed);

    return splits;
  }

  static public Frame[] split2ByRatio(Frame frame, double first, double second, long seed) {
    double[] ratios = new double[]{first, second};
    Key<Frame>[] keys = new Key[] {Key.make(), Key.make()};
    Frame[] splits = null;
    splits = ShuffleSplitFrame.shuffleSplitFrame(frame, keys, ratios, seed);
    return splits;
  }
  
  static public Frame[] getStratifiedSplits(Frame fr, String responseColumnName, double trainRatio, long splitSeed) {

    Frame[] splitsTrainOther = StratificationAssistant.split(fr, responseColumnName, trainRatio, splitSeed);
    Frame train = splitsTrainOther[0];
    Frame[] splitsValidLeader = StratificationAssistant.split(splitsTrainOther[1], responseColumnName, 0.5, splitSeed);
    return new Frame[]{train, splitsValidLeader[0], splitsValidLeader[1]};
  }


  public static double getScoreBasedOn(Frame fr, Model model) {
    model.score(fr);
    hex.ModelMetricsBinomial mmWithoutTE = hex.ModelMetricsBinomial.getFromDKV(model, fr);
    return mmWithoutTE.auc();
  }

  public static double getCumulativeLeaderboardScore(Frame split, Leaderboard leaderboardWithTE) {
    double cumulative = 0.0;
    for( Model model : leaderboardWithTE.getModels()) {
      cumulative += getScoreBasedOn(split, model);
    }
    return cumulative;
  }
}
