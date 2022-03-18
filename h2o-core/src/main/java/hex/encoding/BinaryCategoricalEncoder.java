package hex.encoding;

import water.DKV;
import water.H2O;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.MathUtils;

/**
 * Helper to convert a categorical variable into a "binary" encoding format. In this format each categorical value is
 * first assigned an integer value, then that integer is written in binary, and each bit column is converted into a
 * separate column. This is intended as an improvement to an existing one-hot transformation.
 * For each categorical variable we assume that the number of categories is 1 + domain cardinality, the extra
 * category is reserved for NAs.
 * See http://www.willmcginnis.com/2015/11/29/beyond-one-hot-an-exploration-of-categorical-variables/
 */
public class BinaryCategoricalEncoder extends BaseCategoricalEncoder {

  @Override
  H2O.H2OCountedCompleter newDriver(Frame fr, Key<Frame> destKey, String[] skipCols) {
    return new CategoricalBinaryEncoderDriver(fr, destKey, skipCols);
  }

  /**
   * Driver for CategoricalBinaryEncoder
   */
  class CategoricalBinaryEncoderDriver extends H2O.H2OCountedCompleter {

    final Frame _frame;
    final Key<Frame> _destKey;
    final String[] _skipCols;

    CategoricalBinaryEncoderDriver(Frame frame, Key<Frame> destKey, String[] skipCols) {
      _frame = frame;
      _destKey = destKey;
      _skipCols = skipCols;
    }

    class BinaryConverter extends MRTask<CategoricalBinaryEncoderDriver.BinaryConverter> {

      int[] _categorySizes;

      public BinaryConverter(int[] categorySizes) {
        _categorySizes = categorySizes;
      }

      @Override
      public void map(Chunk[] cs, NewChunk[] ncs) {
        int targetColOffset = 0;
        for (int iCol = 0; iCol < cs.length; ++iCol) {
          Chunk col = cs[iCol];
          int numTargetColumns = _categorySizes[iCol];
          for (int iRow = 0; iRow < col._len; ++iRow) {
            long val = col.isNA(iRow) ? 0 : 1+col.at8(iRow);
            for (int j = 0; j < numTargetColumns; ++j) {
              ncs[targetColOffset+j].addNum(val & 1, 0);
              val >>>= 1;
            }
            assert val == 0 : "";
          }
          targetColOffset += numTargetColumns;
        }
      }
    }

    @Override
    public void compute2() {
      Vec[] frameVecs = _frame.vecs();
      int numCategoricals = 0;
      for (int i = 0; i < frameVecs.length; ++i)
        if (frameVecs[i].isCategorical() && (_skipCols == null || ArrayUtils.find(_skipCols, _frame._names[i]) == -1))
          numCategoricals++;

      Vec[] extraVecs = _skipCols == null ? null : new Vec[_skipCols.length];
      if (extraVecs != null) {
        for (int i = 0; i < extraVecs.length; ++i) {
          Vec v = _frame.vec(_skipCols[i]); //can be null
          if (v != null) extraVecs[i] = v;
        }
      }

      Frame categoricalFrame = new Frame();
      Frame outputFrame = new Frame(_destKey);
      int[] binaryCategorySizes = new int[numCategoricals];
      int numOutputColumns = 0;
      for (int i = 0, j = 0; i < frameVecs.length; ++i) {
        if (_skipCols != null && ArrayUtils.find(_skipCols, _frame._names[i]) >= 0) continue;
        int numCategories = frameVecs[i].cardinality(); // Returns -1 if non-categorical variable
        if (numCategories > 0) {
          categoricalFrame.add(_frame.name(i), frameVecs[i]);
          binaryCategorySizes[j] = 1+MathUtils.log2(numCategories-1+1/* for NAs */);
          numOutputColumns += binaryCategorySizes[j];
          ++j;
        } else
          outputFrame.add(_frame.name(i), frameVecs[i].makeCopy());
      }
      CategoricalBinaryEncoderDriver.BinaryConverter mrtask = new CategoricalBinaryEncoderDriver.BinaryConverter(binaryCategorySizes);
      Frame binaryCols = mrtask.doAll(numOutputColumns, Vec.T_NUM, categoricalFrame).outputFrame();
      // change names of binaryCols so that they reflect the original names of the categories
      for (int i = 0, j = 0; i < binaryCategorySizes.length; j += binaryCategorySizes[i++]) {
        for (int k = 0; k < binaryCategorySizes[i]; ++k) {
          binaryCols._names[j+k] = categoricalFrame.name(i)+":"+k;
        }
      }
      outputFrame.add(binaryCols);
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
