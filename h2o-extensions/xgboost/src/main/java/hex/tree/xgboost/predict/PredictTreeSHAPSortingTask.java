package hex.tree.xgboost.predict;

import hex.DataInfo;
import hex.Model;
import hex.genmodel.algos.tree.ContributionComposer;
import hex.genmodel.algos.tree.TreeSHAPPredictor;
import hex.tree.xgboost.XGBoostModelInfo;
import hex.tree.xgboost.XGBoostOutput;
import water.MemoryManager;
import water.fvec.Chunk;
import water.fvec.NewChunk;

public class PredictTreeSHAPSortingTask extends PredictTreeSHAPTask {

  private final boolean _outputAggregated;
  private final int _topN;
  private final int _bottomN;
  private final boolean _compareAbs;

  public PredictTreeSHAPSortingTask(DataInfo di, XGBoostModelInfo modelInfo, XGBoostOutput output,
                                    Model.Contributions.ContributionsOptions options) {
    super(di,modelInfo,output, options);
    _outputAggregated = Model.Contributions.ContributionsOutputFormat.Compact.equals(options._outputFormat);
    _topN = options._topN;
    _bottomN = options._bottomN;
    _compareAbs = options._compareAbs;
  }

  protected void fillInput(Chunk[] chks, int row, double[] input, float[] contribs, int[] contribNameIds) {
    super.fillInput(chks, row, input, contribs);
    for (int i = 0; i < contribNameIds.length; i++) {
      contribNameIds[i] = i;
    }
  }

  @Override
  public void map(Chunk[] chks, NewChunk[] nc) {
    MutableOneHotEncoderFVec rowFVec = new MutableOneHotEncoderFVec(_di, _output._sparse);

    double[] input = MemoryManager.malloc8d(chks.length);
    float[] contribs = MemoryManager.malloc4f(_di.fullN() + 1);
    float[] output = _outputAggregated ? MemoryManager.malloc4f(chks.length) : contribs;
    int[] contribNameIds = MemoryManager.malloc4(output.length);

    TreeSHAPPredictor.Workspace workspace = _mojo.makeContributionsWorkspace();

    for (int row = 0; row < chks[0]._len; row++) {
      fillInput(chks, row, input, contribs, contribNameIds);
      rowFVec.setInput(input);

      // calculate Shapley values
      _mojo.calculateContributions(rowFVec, contribs, workspace);

      handleOutputFormat(rowFVec, contribs, output);

      ContributionComposer contributionComposer = new ContributionComposer();
      int[] contribNameIdsSorted = contributionComposer.composeContributions(
              contribNameIds, output, _topN, _bottomN, _compareAbs);

      addContribToNewChunk(contribs, contribNameIdsSorted, nc);
    }
  }

  protected void addContribToNewChunk(float[] contribs, int[] contribNamesSorted, NewChunk[] nc) {
    for (int i = 0, inputPointer = 0; i < nc.length-1; i+=2, inputPointer++) {
      nc[i].addNum(contribNamesSorted[inputPointer]);
      nc[i+1].addNum(contribs[contribNamesSorted[inputPointer]]);
    }
    nc[nc.length-1].addNum(contribs[contribs.length-1]); // bias
  }

}
