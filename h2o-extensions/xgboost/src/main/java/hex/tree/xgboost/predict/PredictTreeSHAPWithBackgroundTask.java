package hex.tree.xgboost.predict;

import hex.*;
import hex.genmodel.algos.xgboost.XGBoostJavaMojoModel;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.xgboost.XGBoostModelInfo;
import hex.tree.xgboost.XGBoostOutput;
import water.MemoryManager;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;

import java.util.Arrays;

public class PredictTreeSHAPWithBackgroundTask extends ContributionsWithBackgroundFrameTask<PredictTreeSHAPWithBackgroundTask> {
  protected final DataInfo _di;
  protected final XGBoostModelInfo _modelInfo;
  protected final XGBoostOutput _output;
  protected final boolean _outputAggregated;
  protected final boolean _outputSpace;
  protected final Distribution _distribution;
  protected transient XGBoostJavaMojoModel _mojo;

  public PredictTreeSHAPWithBackgroundTask(DataInfo di, XGBoostModelInfo modelInfo, XGBoostOutput output,
                                           Model.Contributions.ContributionsOptions options, Frame frame, Frame backgroundFrame, boolean perReference, boolean outputSpace) {
    super(frame._key, backgroundFrame._key, perReference);
    _di = di;
    _modelInfo = modelInfo;
    _output = output;
    _outputAggregated = Model.Contributions.ContributionsOutputFormat.Compact.equals(options._outputFormat);
    _outputSpace = outputSpace;
    // FIXME: What's the proper way of getting the link here? _modelInfo._parameters._distribution is set to AUTO by default
    _distribution = outputSpace ? (
            _modelInfo._parameters.getDistributionFamily().equals(DistributionFamily.AUTO) && _output.isBinomialClassifier()
                    ? DistributionFactory.getDistribution(DistributionFamily.bernoulli)
                    : DistributionFactory.getDistribution(_modelInfo._parameters)
    ): null;
  }


  @Override
  protected void setupLocal() {
    _mojo = new XGBoostJavaMojoModel(
            _modelInfo._boosterBytes, _modelInfo.auxNodeWeightBytes(),
            _output._names, _output._domains, _output.responseName(),
            true
    );
  }

  protected void fillInput(Chunk chks[], int row, double[] input) {
    for (int i = 0; i < chks.length; i++) {
      input[i] = chks[i].atd(row);
    }
  }

  protected void addContribToNewChunk(double[] contribs, NewChunk[] nc) {
    double transformationRatio = 1;
    double biasTerm = contribs[contribs.length - 1];
    if (_outputSpace) {
      final double linkSpaceX = Arrays.stream(contribs).sum();
      final double linkSpaceBg = biasTerm;
      final double outSpaceX = _distribution.linkInv(linkSpaceX);
      final double outSpaceBg = _distribution.linkInv(linkSpaceBg);
      transformationRatio = Math.abs(linkSpaceX - linkSpaceBg) < 1e-6 ? 0 : (outSpaceX - outSpaceBg) / (linkSpaceX - linkSpaceBg);
      biasTerm = outSpaceBg;
    }
    for (int i = 0; i < nc.length - 1; i++) {
      nc[i].addNum(contribs[i] * transformationRatio);
    }
    nc[nc.length - 1].addNum(biasTerm);
  }


  @Override
  protected void map(Chunk[] cs, Chunk[] bgCs, NewChunk[] ncs) {
    MutableOneHotEncoderFVec rowFVec = new MutableOneHotEncoderFVec(_di, _output._sparse);
    MutableOneHotEncoderFVec rowFBgVec = new MutableOneHotEncoderFVec(_di, _output._sparse);

    double[] input = MemoryManager.malloc8d(cs.length);
    double[] inputBg = MemoryManager.malloc8d(cs.length);
    double[] contribs = MemoryManager.malloc8d(_outputAggregated ? ncs.length : _di.fullN() + 1);

    for (int row = 0; row < cs[0]._len; row++) {
      fillInput(cs, row, input);
      rowFVec.setInput(input);
      for (int bgRow = 0; bgRow < bgCs[0]._len; bgRow++) {
        Arrays.fill(contribs, 0);
        fillInput(bgCs, bgRow, inputBg);
        rowFBgVec.setInput(inputBg);
        // calculate Shapley values
        _mojo.calculateInterventionalContributions(rowFVec, rowFBgVec, contribs, _outputAggregated ? _di._catOffsets : null, false);

        // FIXME: This is questionable decision. It seems logical at first to assign the contribution to the level
        // that was present in the data but since this is in the expanded features it might happen that the level 
        // now represented as one dimension is not used at all. To make it simpler to think about let's imagine
        // GLM with category color x=red, b=blue. In GLM we can look and see that color.red has no importance (beta_{red} == 0) and
        // the color.blue is the only important feature so the contribution is basically from switching blue 
        // from 1 to 0 not from blue to zero and red to one. Can we get such information in tree models?
//                if (!_outputAggregated) {
//                    // make sure the contribution is on the level that's present in the foreground sample
//                    for (int i = 0; i < _di._catOffsets.length-1; i++) {
//                        final int fgIdx = Double.isNaN(input[i]) ? _di._catOffsets[i+1]-1 : _di._catOffsets[i] + (int)input[i];
//                        final int bgIdx = Double.isNaN(inputBg[i]) ? _di._catOffsets[i+1]-1 : _di._catOffsets[i] + (int)inputBg[i];
//                        contribs[fgIdx] += contribs[bgIdx];
//                        contribs[bgIdx] = 0;
//                    }
//                }

        addContribToNewChunk(contribs, ncs);
      }
    }
  }
}
