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

import java.util.ArrayList;
import java.util.List;

public class OneHotCategoricalEncoder extends BaseCategoricalEncoder {

  @Override
  H2O.H2OCountedCompleter newDriver(Frame fr, Key<Frame> destKey, String[] skipCols) {
    return new CategoricalOneHotEncoderDriver(fr, destKey, skipCols);
  }

  /**
   * Driver for CategoricalOneHotEncoder
   */
  class CategoricalOneHotEncoderDriver extends H2O.H2OCountedCompleter {

    final Frame _frame;
    final Key<Frame> _destKey;
    final String[] _skipCols;

    CategoricalOneHotEncoderDriver(Frame frame, Key<Frame> destKey, String[] skipCols) {
      _frame = frame;
      _destKey = destKey;
      _skipCols = skipCols;
    }

    class OneHotConverter extends MRTask<CategoricalOneHotEncoderDriver.OneHotConverter> {

      int[] _categorySizes;

      public OneHotConverter(int[] categorySizes) {
        _categorySizes = categorySizes;
      }

      @Override
      public void map(Chunk[] cs, NewChunk[] ncs) {
        int targetColOffset = 0;
        for (int iCol = 0; iCol < cs.length; ++iCol) {
          Chunk col = cs[iCol];
          int numTargetColumns = _categorySizes[iCol];
          for (int iRow = 0; iRow < col._len; ++iRow) {
            long val = col.isNA(iRow) ? numTargetColumns-1 : col.at8(iRow);
            for (int j = 0; j < numTargetColumns; ++j) {
              ncs[targetColOffset+j].addNum(val == j ? 1 : 0, 0);
            }
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
        if (frameVecs[i].isCategorical() && ArrayUtils.find(_skipCols, _frame._names[i]) == -1)
          numCategoricals++;

      Vec[] extraVecs = new Vec[_skipCols.length];
      for (int i = 0; i < extraVecs.length; ++i) {
        Vec v = _frame.vec(_skipCols[i]); //can be null
        if (v != null) extraVecs[i] = v;
      }

      Frame categoricalFrame = new Frame();
      Frame outputFrame = new Frame(_destKey);
      int[] categorySizes = new int[numCategoricals];
      int numOutputColumns = 0;
      List<String> catnames = new ArrayList<>();
      for (int i = 0, j = 0; i < frameVecs.length; ++i) {
        if (ArrayUtils.find(_skipCols, _frame._names[i]) >= 0) continue;
        int numCategories = frameVecs[i].cardinality(); // Returns -1 if non-categorical variable
        if (numCategories > 0) {
          categoricalFrame.add(_frame.name(i), frameVecs[i]);
          categorySizes[j] = numCategories+1/* for NAs */;
          numOutputColumns += categorySizes[j];
          for (int k = 0; k < categorySizes[j]-1; ++k)
            catnames.add(_frame.name(i)+"."+_frame.vec(i).domain()[k]);
          catnames.add(_frame.name(i)+".missing(NA)");
          ++j;
        } else {
          outputFrame.add(_frame.name(i), frameVecs[i].makeCopy());
        }
      }
      CategoricalOneHotEncoderDriver.OneHotConverter mrtask = new CategoricalOneHotEncoderDriver.OneHotConverter(categorySizes);
      Frame binaryCols = mrtask.doAll(numOutputColumns, Vec.T_NUM, categoricalFrame).outputFrame();
      binaryCols.setNames(catnames.toArray(new String[0]));
      outputFrame.add(binaryCols);
      for (int i = 0; i < extraVecs.length; ++i) {
        if (extraVecs[i] != null)
          outputFrame.add(_skipCols[i], extraVecs[i].makeCopy());
      }
      DKV.put(outputFrame);
      tryComplete();
    }
  }
}
