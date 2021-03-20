package hex.tree.xgboost.predict;

import hex.DataInfo;
import hex.Model;
import hex.genmodel.algos.tree.ContributionComposer;
import hex.genmodel.algos.xgboost.XGBoostJavaMojoModel;
import hex.genmodel.attributes.parameters.KeyValue;
import hex.tree.xgboost.XGBoostModelInfo;
import hex.tree.xgboost.XGBoostOutput;
import water.MRTask;
import water.MemoryManager;
import water.fvec.Chunk;
import water.fvec.NewChunk;

import java.util.Arrays;

public class PredictTreeSHAPSortingTask extends PredictTreeSHAPTask {

  private final transient String[] _contribNames;
  private final transient int _topN;
  private final transient int _topBottomN;
  private final transient boolean _abs;
  private final transient ContributionComposer contributionComposer = new ContributionComposer();

  public PredictTreeSHAPSortingTask(DataInfo di, XGBoostModelInfo modelInfo, XGBoostOutput output,
                                    Model.Contributions.ContributionsOptions options,
                                    String[] contribNames, int topN, int topBottomN, boolean abs) {
    super(di,modelInfo,output, options);
    _topN = topN;
    _topBottomN = topBottomN;
    _abs = abs;
    _contribNames = contribNames;
  }

  @Override
  public void map(Chunk[] chks, NewChunk[] nc) {
    MutableOneHotEncoderFVec rowFVec = new MutableOneHotEncoderFVec(_di, _output._sparse);

    double[] input = MemoryManager.malloc8d(chks.length);
    float[] contribs = MemoryManager.malloc4f(nc.length);
    float[] output = _outputAggregated ? MemoryManager.malloc4f(nc.length) : contribs;

    Object workspace = _mojo.makeContributionsWorkspace();

    for (int row = 0; row < chks[0]._len; row++) {
      for (int i = 0; i < chks.length; i++) {
        input[i] = chks[i].atd(row);
      }
      Arrays.fill(contribs, 0);
      rowFVec.setInput(input);

      // calculate Shapley values
      _mojo.calculateContributions(rowFVec, contribs, workspace);

      if (_outputAggregated) {
        rowFVec.decodeAggregate(contribs, output);
        output[output.length - 1] = contribs[contribs.length - 1]; // bias term
      }

      KeyValue[] contribsSorted = contributionComposer.composeContributions(contribs, _contribNames, _topN, _topBottomN, _abs);

      for (int i = 0, inputPointer = 0; i < nc.length-1; i+=2, inputPointer++) {
        nc[i].addStr(contribsSorted[inputPointer].key);
        nc[i+1].addNum(contribsSorted[inputPointer].value);
      }
      nc[nc.length-1].addNum(contribsSorted[contribs.length-1].value); // bias
    }
  }

}
