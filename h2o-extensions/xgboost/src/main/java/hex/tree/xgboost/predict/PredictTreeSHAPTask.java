package hex.tree.xgboost.predict;

import hex.DataInfo;
import hex.genmodel.algos.xgboost.XGBoostJavaMojoModel;
import hex.tree.xgboost.XGBoostModelInfo;
import hex.tree.xgboost.XGBoostOutput;
import water.MRTask;
import water.MemoryManager;
import water.fvec.Chunk;
import water.fvec.NewChunk;

import java.util.Arrays;

import static hex.Model.Contributions.ContributionsOptions;
import static hex.Model.Contributions.ContributionsOutputFormat;

public class PredictTreeSHAPTask extends MRTask<PredictTreeSHAPTask> {

  protected final DataInfo _di;
  protected final XGBoostModelInfo _modelInfo;
  protected final XGBoostOutput _output;
  protected final boolean _outputAggregated;


  protected transient XGBoostJavaMojoModel _mojo;

  public PredictTreeSHAPTask(DataInfo di, XGBoostModelInfo modelInfo, XGBoostOutput output,
                             ContributionsOptions options) {
    _di = di;
    _modelInfo = modelInfo;
    _output = output;
    _outputAggregated = ContributionsOutputFormat.Compact.equals(options._outputFormat);
  }

  @Override
  protected void setupLocal() {
    _mojo = new XGBoostJavaMojoModel(
            _modelInfo._boosterBytes, _modelInfo.auxNodeWeightBytes(), 
            _output._names, _output._domains, _output.responseName(), 
            true
    );
  }

  protected void fillInput(Chunk chks[], int row, double[] input, float[] contribs) {
    for (int i = 0; i < chks.length; i++) {
      input[i] = chks[i].atd(row);
    }
    Arrays.fill(contribs, 0);
  }

  @Override
  public void map(Chunk[] chks, NewChunk[] nc) {
    MutableOneHotEncoderFVec rowFVec = new MutableOneHotEncoderFVec(_di, _output._sparse);

    double[] input = MemoryManager.malloc8d(chks.length);
    float[] contribs = MemoryManager.malloc4f(_di.fullN() + 1);
    float[] output = _outputAggregated ? MemoryManager.malloc4f(nc.length) : contribs;

    Object workspace = _mojo.makeContributionsWorkspace();

    for (int row = 0; row < chks[0]._len; row++) {
      fillInput(chks, row, input, contribs);
      rowFVec.setInput(input);

      // calculate Shapley values
      _mojo.calculateContributions(rowFVec, contribs, workspace);

      handleOutputFormat(rowFVec, contribs, output);

      addContribToNewChunk(output, nc);
    }
  }

  protected void handleOutputFormat(final MutableOneHotEncoderFVec rowFVec, final float[] contribs, final float[] output) {
    if (_outputAggregated) {
      rowFVec.decodeAggregate(contribs, output);
      output[output.length - 1] = contribs[contribs.length - 1]; // bias term
    }
  }

  protected void addContribToNewChunk(final float[] contribs, final NewChunk[] nc) {
    for (int i = 0; i < nc.length; i++) {
      nc[i].addNum(contribs[i]);
    }
  }

}
