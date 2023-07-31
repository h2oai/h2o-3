package hex.tree.xgboost.predict;

import hex.ContributionsWithBackgroundFrameTask;
import hex.DataInfo;
import hex.Model;
import hex.genmodel.algos.xgboost.XGBoostJavaMojoModel;
import hex.tree.xgboost.XGBoostModelInfo;
import hex.tree.xgboost.XGBoostOutput;
import water.Job;
import water.Key;
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

    protected transient XGBoostJavaMojoModel _mojo;

    public PredictTreeSHAPWithBackgroundTask(DataInfo di, XGBoostModelInfo modelInfo, XGBoostOutput output,
                                             Model.Contributions.ContributionsOptions options, Frame frame, Frame backgroundFrame) {
        super(frame, backgroundFrame);
        _di = di;
        _modelInfo = modelInfo;
        _output = output;
        _outputAggregated = Model.Contributions.ContributionsOutputFormat.Compact.equals(options._outputFormat);
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
        for (int i = 0; i < nc.length; i++) {
            nc[i].addNum(contribs[i]);
        }
    }


    @Override
    protected void map(Chunk[] cs, Chunk[] bgCs, NewChunk[] ncs) {
        MutableOneHotEncoderFVec rowFVec = new MutableOneHotEncoderFVec(_di, _output._sparse);
        MutableOneHotEncoderFVec rowFBgVec = new MutableOneHotEncoderFVec(_di, _output._sparse);

        double[] input = MemoryManager.malloc8d(cs.length);
        double[] inputBg = MemoryManager.malloc8d(cs.length);
        double[] contribs = MemoryManager.malloc8d(_outputAggregated ? ncs.length: _di.fullN() + 1);
        // double[] output = _outputAggregated ? MemoryManager.malloc8d(ncs.length) : contribs;

        for (int row = 0; row < cs[0]._len; row++) {
            fillInput(cs, row, input);
            rowFVec.setInput(input);
            for (int bgRow = 0; bgRow < bgCs[0]._len; bgRow++) {
                Arrays.fill(contribs, 0);
                fillInput(bgCs, bgRow, inputBg);
                rowFBgVec.setInput(inputBg);
                // calculate Shapley values
                _mojo.calculateInterventionalContributions(rowFVec, rowFBgVec, contribs, _outputAggregated ? _di._catOffsets : null, false);
                
                if (!_outputAggregated) {
                    // make sure the contribution is on the level that's present in the foreground sample
                    for (int i = 0; i < _di._catOffsets.length-1; i++) {
                        final int fgIdx = Double.isNaN(input[i]) ? _di._catOffsets[i+1]-1 : _di._catOffsets[i] + (int)input[i];
                        final int bgIdx = Double.isNaN(inputBg[i]) ? _di._catOffsets[i+1]-1 : _di._catOffsets[i] + (int)inputBg[i];
                        contribs[fgIdx] += contribs[bgIdx];
                        contribs[bgIdx] = 0;
                    }
                }
                
                addContribToNewChunk(contribs, ncs);
            }
        }
    }
}
