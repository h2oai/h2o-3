package hex.encoding;

import water.DKV;
import water.H2O;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;

public class LabelCategoricalEncoder extends BaseCategoricalEncoder {

  @Override
  H2O.H2OCountedCompleter newDriver(Frame fr, Key<Frame> destKey, String[] skippedCols) {
    return new CategoricalLabelEncoderDriver(fr, destKey, skippedCols);
  }

  /**
   * Driver for CategoricalLabelEncoder
   */
  class CategoricalLabelEncoderDriver extends H2O.H2OCountedCompleter {

    final Frame _frame;
    final Key<Frame> _destKey;
    final String[] _skipCols;

    CategoricalLabelEncoderDriver(Frame frame, Key<Frame> destKey, String[] skipCols) {
      _frame = frame;
      _destKey = destKey;
      _skipCols = skipCols;
    }

    @Override
    public void compute2() {
      Vec[] frameVecs = _frame.vecs();
      Vec[] extraVecs = _skipCols == null ? null : new Vec[_skipCols.length];
      if (extraVecs != null) {
        for (int i = 0; i < extraVecs.length; ++i) {
          Vec v = _frame.vec(_skipCols[i]); //can be null
          if (v != null) extraVecs[i] = v;
        }
      }
      Frame outputFrame = new Frame(_destKey);
      for (int i = 0, j = 0; i < frameVecs.length; ++i) {
        if (_skipCols != null && ArrayUtils.find(_skipCols, _frame._names[i]) >= 0) continue;
        int numCategories = frameVecs[i].cardinality(); // Returns -1 if non-categorical variable
        if (numCategories > 0) {
          outputFrame.add(_frame.name(i), frameVecs[i].toNumericVec());
        } else
          outputFrame.add(_frame.name(i), frameVecs[i].makeCopy());
      }
      if (_skipCols != null) {
        for (int i = 0; i < extraVecs.length; ++i) {
          if (extraVecs[i] != null)
            outputFrame.add(_skipCols[i], extraVecs[i].makeCopy());
        }
      }
      DKV.put(outputFrame);
      tryComplete();
    }
  }
}
