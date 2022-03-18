package hex.encoding;

import hex.ToEigenVec;
import water.DKV;
import water.H2O;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;

/**
 * Helper to convert a categorical variable into the first eigenvector of the dummy-expanded matrix.
 */
public class EigenCategoricalEncoder extends BaseCategoricalEncoder {
  final ToEigenVec _tev;

  public EigenCategoricalEncoder(ToEigenVec tev) {
    assert tev != null;
    _tev = tev;
  }

  public EigenCategoricalEncoder(CategoricalEncodingSupport params) {
    _tev = params.getToEigenVec();
  }

  @Override
  H2O.H2OCountedCompleter newDriver(Frame fr, Key<Frame> destKey, String[] skipCols) {
    return new CategoricalEigenEncoderDriver(fr, destKey, skipCols);
  }

  /**
   * Driver for EigenCategoricalEncoder
   */
  class CategoricalEigenEncoderDriver extends H2O.H2OCountedCompleter {

    final Frame _frame;
    final Key<Frame> _destKey;
    final String[] _skipCols;

    CategoricalEigenEncoderDriver(Frame frame, Key<Frame> destKey, String[] skipCols) {
      _frame = frame;
      _destKey = destKey;
      _skipCols = skipCols;
    }

    @Override
    public void compute2() {
      Vec[] frameVecs = _frame.vecs();
      Vec[] extraVecs = new Vec[_skipCols == null ? 0 : _skipCols.length];
      for (int i = 0; i < extraVecs.length; ++i) {
        Vec v = _skipCols == null || _skipCols.length <= i ? null : _frame.vec(_skipCols[i]); //can be null
        if (v != null) extraVecs[i] = v;
      }
      Frame outputFrame = new Frame(_destKey);
      for (int i = 0; i < frameVecs.length; ++i) {
        if (_skipCols != null && ArrayUtils.find(_skipCols, _frame._names[i]) >= 0) continue;
        if (frameVecs[i].isCategorical())
          outputFrame.add(_frame.name(i)+".Eigen", _tev.toEigenVec(frameVecs[i]));
        else
          outputFrame.add(_frame.name(i), frameVecs[i].makeCopy());
      }
      for (int i = 0; i < extraVecs.length; ++i) {
        if (extraVecs[i] != null)
          outputFrame.add(_skipCols[i], extraVecs[i].makeCopy());
      }
      DKV.put(outputFrame);
      tryComplete();
    }
  }
}
