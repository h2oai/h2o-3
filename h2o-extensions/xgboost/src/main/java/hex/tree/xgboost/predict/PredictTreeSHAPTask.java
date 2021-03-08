package hex.tree.xgboost.predict;

import hex.DataInfo;
import hex.Model;
import hex.genmodel.algos.xgboost.XGBoostJavaMojoModel;
import hex.tree.xgboost.XGBoostOutput;
import hex.tree.xgboost.XGBoostModelInfo;
import water.MRTask;
import water.MemoryManager;
import water.fvec.Chunk;
import water.fvec.NewChunk;

import java.util.Arrays;

public class PredictTreeSHAPTask extends MRTask<PredictTreeSHAPTask> {

  private final DataInfo _di;
  private final XGBoostModelInfo _modelInfo;
  private final XGBoostOutput _output;
  private final boolean _outputAggregated;

  private transient XGBoostJavaMojoModel _mojo;

  public PredictTreeSHAPTask(DataInfo di, XGBoostModelInfo modelInfo, XGBoostOutput output,
                             Model.ContributionsOptions options) {
    _di = di;
    _modelInfo = modelInfo;
    _output = output;
    _outputAggregated = options._outputCompact;
  }

  @Override
  protected void setupLocal() {
    _mojo = new XGBoostJavaMojoModel(
        _modelInfo._boosterBytes, _output._names, _output._domains, _output.responseName(), true
    );
  }

  @Override
  public void map(Chunk[] chks, NewChunk[] nc) {
    MutableOneHotEncoderFVec rowFVec = new MutableOneHotEncoderFVec(_di, _output._sparse);

    double[] input = MemoryManager.malloc8d(chks.length);
    float[] contribs = MemoryManager.malloc4f(_di.fullN() + 1);
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
      
      for (int i = 0; i < nc.length; i++) {
        nc[i].addNum(output[i]);
      }
    }
  }

}
