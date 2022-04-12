package hex.encoding;

import hex.Interaction;
import water.DKV;
import water.H2O;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;

/**
 * Helper to convert a categorical variable into the first eigenvector of the dummy-expanded matrix.
 */
public class EnumLimitedCategoricalEncoder extends BaseCategoricalEncoder {

  final int _maxLevels;

  public EnumLimitedCategoricalEncoder(int maxLevels) {
    _maxLevels = maxLevels;
  }

  public EnumLimitedCategoricalEncoder(CategoricalEncodingSupport params) {
    _maxLevels = params.getMaxCategoricalLevels();
  }

  @Override
  H2O.H2OCountedCompleter newDriver(Frame fr, Key<Frame> destKey, String[] skippedCols) {
    return new CategoricalEnumLimitedDriver(fr, destKey, skippedCols);
  }

  /**
   * Driver for CategoricalEnumLimited
   */
  class CategoricalEnumLimitedDriver extends H2O.H2OCountedCompleter {

    final Frame _frame;
    final Key<Frame> _destKey;
    final String[] _skipCols;

    CategoricalEnumLimitedDriver(Frame frame, Key<Frame> destKey, String[] skipCols) {
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
      //        Log.info(_frame.toTwoDimTable(0, (int)_frame.numRows()));
      Frame outputFrame = new Frame(_destKey);
      for (int i = 0; i < frameVecs.length; ++i) {
        Vec src = frameVecs[i];
        if (_skipCols != null && ArrayUtils.find(_skipCols, _frame._names[i]) >= 0) continue;
        if (src.cardinality() > _maxLevels && !(src.isDomainTruncated(_maxLevels))) { //avoid double-encoding by checking it was not previously truncated on first encoding
          Key<Frame> source = Key.make();
          Key<Frame> dest = Key.make();
          Frame train = new Frame(source, new String[]{"enum"}, new Vec[]{src});
          DKV.put(train);
          Log.info("Reducing the cardinality of a categorical column with "+src.cardinality()+" levels to "+_maxLevels);
          train = Interaction.getInteraction(train._key, train.names(), _maxLevels).execImpl(dest).get();
          outputFrame.add(_frame.name(i)+".top_"+_maxLevels+"_levels", train.anyVec().makeCopy());
          train.remove();
          DKV.remove(source);
        } else {
          outputFrame.add(_frame.name(i), frameVecs[i].makeCopy());
        }
      }
      for (int i = 0; i < extraVecs.length; ++i) {
        if (extraVecs[i] != null)
          outputFrame.add(_skipCols[i], extraVecs[i].makeCopy());
      }
      //        Log.info(outputFrame.toTwoDimTable(0, (int)outputFrame.numRows()));
      DKV.put(outputFrame);
      tryComplete();
    }
  }
}
