package hex.genmodel.algos.isoforextended;

import hex.genmodel.MojoModel;
import hex.genmodel.algos.tree.*;
import hex.genmodel.utils.ArrayUtils;
import hex.genmodel.utils.ByteBufferWrapper;
import hex.genmodel.utils.MathUtils;

import java.util.Arrays;

public final class ExtendedIsolationForestMojoModel extends MojoModel {

  int _ntrees;

  long _sample_size;

  byte[][] _compressedTrees;

  private ScoreIsolationTree _scoreIsolationTree;

  public ExtendedIsolationForestMojoModel(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
  }

  protected void postInit() {
    _scoreIsolationTree = new ScoreIsolationTree0();
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    return score0(row, 0.0, preds);
  }

  @Override
  public double[] score0(double[] row, double offset, double[] preds) {
    return row;
  }

  @Override
  public int getPredsSize() {
    return 2;
  }

  @Override
  public String[] getOutputNames() {
      return new String[]{"anomaly_score", "mean_length"};
  }

  public static double scoreTree0(byte[] isolationTree, double[] row) {
    return -1;
  }

}
