package hex.tree.xgboost.predict;

import hex.DataInfo;
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

  private transient XGBoostJavaMojoModel _mojo; 

  public PredictTreeSHAPTask(DataInfo di, XGBoostModelInfo modelInfo, XGBoostOutput output) {
    _di = di;
    _modelInfo = modelInfo;
    _output = output;
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
    float[] contribs = MemoryManager.malloc4f(nc.length);

    Object workspace = _mojo.makeContributionsWorkspace();

    for (int row = 0; row < chks[0]._len; row++) {
      for (int i = 0; i < chks.length; i++) {
        input[i] = chks[i].atd(row);
      }
      Arrays.fill(contribs, 0);
      rowFVec.setInput(input);

      // calculate Shapley values
      _mojo.calculateContributions(rowFVec, contribs, workspace);

      for (int i = 0; i < nc.length; i++) {
        nc[i].addNum(contribs[i]);
      }
    }
  }

}
