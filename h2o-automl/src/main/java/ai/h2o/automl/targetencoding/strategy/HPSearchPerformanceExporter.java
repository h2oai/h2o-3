package ai.h2o.automl.targetencoding.strategy;

import water.Scope;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.ArrayList;

public class HPSearchPerformanceExporter  {
  private ArrayList<Double> surrogatePredictions = new ArrayList<>(); //TODO add support for surrogatePredictions export
  private ArrayList<Double> scores = new ArrayList<>();

  public HPSearchPerformanceExporter() {
  }

  public void exportToCSV(String modelName) {
    Scope.enter();
    double[] scoresAsDouble = new double[scores.size()];
    for (int i = 0; i < scores.size(); i++) {
      scoresAsDouble[i] = (double) scores.toArray()[i];
    }
    Vec predVec = Vec.makeVec(scoresAsDouble, Vec.newKey());
    Frame fr = new Frame(new String[]{"score"}, new Vec[]{predVec});
    Frame.export(fr,   modelName + "-" + System.currentTimeMillis() / 1000 + ".csv", "frame_name", true, 1).get();
    Scope.exit();
  };

  public void update(double prediction, double score) {
    surrogatePredictions.add(prediction);
    scores.add(score);
  };
}
