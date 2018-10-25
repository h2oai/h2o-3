package hex.genmodel.algos.pipeline;

import hex.genmodel.MojoModel;

import java.io.Serializable;

public class MojoPipeline extends MojoModel {

  MojoModel _mainModel;
  int[] _sourceRowIndices;
  int[] _targetMainModelRowIndices;
  int _generatedColumnCount;

  PipelineSubModel[] _models;

  public MojoPipeline(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    double[] mainModelRow = new double[_targetMainModelRowIndices.length + _generatedColumnCount];
    for (int i = 0; i < _targetMainModelRowIndices.length; i++) {
      mainModelRow[_targetMainModelRowIndices[i]] = row[_sourceRowIndices[i]];
    }

    // score sub-models and populate generated fields of the main-model input row
    for (PipelineSubModel psm : _models) {
      double[] subModelRow = new double[psm._inputMapping.length];
      for (int i = 0; i < psm._inputMapping.length; i++) {
        subModelRow[i] = row[psm._inputMapping[i]];
      }
      double[] subModelPreds = new double[psm._predsSize];
      subModelPreds = psm._mojoModel.score0(subModelRow, subModelPreds);
      for (int j = 0; j < psm._sourcePredsIndices.length; j++) {
        mainModelRow[psm._targetRowIndices[j]] = subModelPreds[psm._sourcePredsIndices[j]];
      }
    }

    return _mainModel.score0(mainModelRow, preds);
  }

  static class PipelineSubModel implements Serializable {
    int[] _inputMapping;
    int _predsSize;
    int[] _sourcePredsIndices;
    int[] _targetRowIndices;
    MojoModel _mojoModel;
  }

}
