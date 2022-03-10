package water.util;

import hex.Interaction;
import hex.Model;
import hex.ToEigenVec;
import water.*;
import water.H2O.H2OCountedCompleter;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;

import java.util.ArrayList;
import java.util.List;

public final class CategoricalEncoders {

  private CategoricalEncoders() { /* do not instantiate */ }

  static abstract class BaseCategoricalEncoder extends Iced implements CategoricalEncoder {

    @Override
    public Frame encode(Frame fr, String[] skipCols) {
      return exec(fr, skipCols).get();
    }

    Job<Frame> exec(Frame fr, String[] skipCols) {
      if (fr == null)
        throw new IllegalArgumentException("Frame doesn't exist.");
      Key<Frame> destKey = Key.makeSystem(Key.make().toString());
      Job<Frame> job = new Job<>(destKey, Frame.class.getName(), getClass().getSimpleName());
      int workAmount = fr.lastVec().nChunks();
      H2OCountedCompleter completer = newDriver(fr, destKey, skipCols);
      return job.start(completer, workAmount);
    }

    abstract H2OCountedCompleter newDriver(Frame fr, Key<Frame> destKey, String[] skipCols);
  }
  
  
  static class NoopEncoder extends BaseCategoricalEncoder {
    @Override
    H2OCountedCompleter newDriver(Frame fr, Key<Frame> destKey, String[] skipCols) {
      return null;
    }

    @Override
    public Frame encode(Frame fr, String[] skipCols) {
      return super.encode(fr, skipCols);
    }
  }

  public static class CategoricalOneHotEncoder extends BaseCategoricalEncoder {

    @Override
    H2OCountedCompleter newDriver(Frame fr, Key<Frame> destKey, String[] skipCols) {
      return new CategoricalOneHotEncoderDriver(fr, destKey, skipCols);
    }

    /**
     * Driver for CategoricalOneHotEncoder
     */
    class CategoricalOneHotEncoderDriver extends H2OCountedCompleter {
      
      final Frame _frame;
      final Key<Frame> _destKey;
      final String[] _skipCols;
      
      CategoricalOneHotEncoderDriver(Frame frame, Key<Frame> destKey, String[] skipCols) {
        _frame = frame; 
        _destKey = destKey; 
        _skipCols = skipCols; 
      }

      class OneHotConverter extends MRTask<OneHotConverter> {
        
        int[] _categorySizes;
        
        public OneHotConverter(int[] categorySizes) { 
          _categorySizes = categorySizes; 
        }

        @Override public void map(Chunk[] cs, NewChunk[] ncs) {
          int targetColOffset = 0;
          for (int iCol = 0; iCol < cs.length; ++iCol) {
            Chunk col = cs[iCol];
            int numTargetColumns = _categorySizes[iCol];
            for (int iRow = 0; iRow < col._len; ++iRow) {
              long val = col.isNA(iRow)? numTargetColumns-1 : col.at8(iRow);
              for (int j = 0; j < numTargetColumns; ++j) {
                ncs[targetColOffset + j].addNum(val==j ? 1 : 0, 0);
              }
            }
            targetColOffset += numTargetColumns;
          }
        }
      }

      @Override public void compute2() {
        Vec[] frameVecs = _frame.vecs();
        int numCategoricals = 0;
        for (int i=0;i<frameVecs.length;++i)
          if (frameVecs[i].isCategorical() && ArrayUtils.find(_skipCols, _frame._names[i])==-1)
            numCategoricals++;

        Vec[] extraVecs = new Vec[_skipCols.length];
        for (int i=0; i< extraVecs.length; ++i) {
          Vec v = _frame.vec(_skipCols[i]); //can be null
          if (v!=null) extraVecs[i] = v;
        }

        Frame categoricalFrame = new Frame();
        Frame outputFrame = new Frame(_destKey);
        int[] categorySizes = new int[numCategoricals];
        int numOutputColumns = 0;
        List<String> catnames= new ArrayList<>();
        for (int i = 0, j = 0; i < frameVecs.length; ++i) {
          if (ArrayUtils.find(_skipCols, _frame._names[i])>=0) continue;
          int numCategories = frameVecs[i].cardinality(); // Returns -1 if non-categorical variable
          if (numCategories > 0) {
            categoricalFrame.add(_frame.name(i), frameVecs[i]);
            categorySizes[j] = numCategories + 1/* for NAs */;
            numOutputColumns += categorySizes[j];
            for (int k=0;k<categorySizes[j]-1;++k)
              catnames.add(_frame.name(i) + "." + _frame.vec(i).domain()[k]);
            catnames.add(_frame.name(i) + ".missing(NA)");
            ++j;
          } else {
            outputFrame.add(_frame.name(i), frameVecs[i].makeCopy());
          }
        }
        OneHotConverter mrtask = new OneHotConverter(categorySizes);
        Frame binaryCols = mrtask.doAll(numOutputColumns, Vec.T_NUM, categoricalFrame).outputFrame();
        binaryCols.setNames(catnames.toArray(new String[0]));
        outputFrame.add(binaryCols);
        for (int i=0;i<extraVecs.length;++i) {
          if (extraVecs[i]!=null)
            outputFrame.add(_skipCols[i], extraVecs[i].makeCopy());
        }
        DKV.put(outputFrame);
        tryComplete();
      }
    }
  }

  public static class CategoricalLabelEncoder extends BaseCategoricalEncoder {

    @Override
    H2OCountedCompleter newDriver(Frame fr, Key<Frame> destKey, String[] skipCols) {
      return new CategoricalLabelEncoderDriver(fr, destKey, skipCols);
    }

    /**
     * Driver for CategoricalLabelEncoder
     */
    class CategoricalLabelEncoderDriver extends H2OCountedCompleter {
      
      final Frame _frame;
      final Key<Frame> _destKey;
      final String[] _skipCols;
      
      CategoricalLabelEncoderDriver(Frame frame, Key<Frame> destKey, String[] skipCols) {
        _frame = frame;
        _destKey = destKey; 
        _skipCols = skipCols;
      }

      @Override public void compute2() {
        Vec[] frameVecs = _frame.vecs();
        Vec[] extraVecs = _skipCols==null?null:new Vec[_skipCols.length];
        if (extraVecs!=null) {
          for (int i = 0; i < extraVecs.length; ++i) {
            Vec v = _frame.vec(_skipCols[i]); //can be null
            if (v != null) extraVecs[i] = v;
          }
        }
        Frame outputFrame = new Frame(_destKey);
        for (int i = 0, j = 0; i < frameVecs.length; ++i) {
          if (_skipCols!=null && ArrayUtils.find(_skipCols, _frame._names[i])>=0) continue;
          int numCategories = frameVecs[i].cardinality(); // Returns -1 if non-categorical variable
          if (numCategories > 0) {
            outputFrame.add(_frame.name(i), frameVecs[i].toNumericVec());
          } else
            outputFrame.add(_frame.name(i), frameVecs[i].makeCopy());
        }
        if (_skipCols!=null) {
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

  /**
   * Helper to convert a categorical variable into a "binary" encoding format. In this format each categorical value is
   * first assigned an integer value, then that integer is written in binary, and each bit column is converted into a
   * separate column. This is intended as an improvement to an existing one-hot transformation.
   * For each categorical variable we assume that the number of categories is 1 + domain cardinality, the extra
   * category is reserved for NAs.
   * See http://www.willmcginnis.com/2015/11/29/beyond-one-hot-an-exploration-of-categorical-variables/
   */
  public static class CategoricalBinaryEncoder extends BaseCategoricalEncoder {

    @Override
    H2OCountedCompleter newDriver(Frame fr, Key<Frame> destKey, String[] skipCols) {
      return new CategoricalBinaryEncoderDriver(fr, destKey, skipCols); 
    }

    /**
     * Driver for CategoricalBinaryEncoder
     */
    class CategoricalBinaryEncoderDriver extends H2OCountedCompleter {
      
      final Frame _frame;
      final Key<Frame> _destKey;
      final String[] _skipCols;
      
      CategoricalBinaryEncoderDriver(Frame frame, Key<Frame> destKey, String[] skipCols) {
        _frame = frame; 
        _destKey = destKey; 
        _skipCols = skipCols;
      }

      class BinaryConverter extends MRTask<BinaryConverter> {
        
        int[] _categorySizes;
        
        public BinaryConverter(int[] categorySizes) {
          _categorySizes = categorySizes;
        }

        @Override public void map(Chunk[] cs, NewChunk[] ncs) {
          int targetColOffset = 0;
          for (int iCol = 0; iCol < cs.length; ++iCol) {
            Chunk col = cs[iCol];
            int numTargetColumns = _categorySizes[iCol];
            for (int iRow = 0; iRow < col._len; ++iRow) {
              long val = col.isNA(iRow)? 0 : 1 + col.at8(iRow);
              for (int j = 0; j < numTargetColumns; ++j) {
                ncs[targetColOffset + j].addNum(val & 1, 0);
                val >>>= 1;
              }
              assert val == 0 : "";
            }
            targetColOffset += numTargetColumns;
          }
        }
      }

      @Override public void compute2() {
        Vec[] frameVecs = _frame.vecs();
        int numCategoricals = 0;
        for (int i=0;i<frameVecs.length;++i)
          if (frameVecs[i].isCategorical() && (_skipCols==null || ArrayUtils.find(_skipCols, _frame._names[i])==-1))
            numCategoricals++;

        Vec[] extraVecs = _skipCols==null?null:new Vec[_skipCols.length];
        if (extraVecs!=null) {
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
          if (_skipCols!=null && ArrayUtils.find(_skipCols, _frame._names[i])>=0) continue;
          int numCategories = frameVecs[i].cardinality(); // Returns -1 if non-categorical variable
          if (numCategories > 0) {
            categoricalFrame.add(_frame.name(i), frameVecs[i]);
            binaryCategorySizes[j] = 1 + MathUtils.log2(numCategories - 1 + 1/* for NAs */);
            numOutputColumns += binaryCategorySizes[j];
            ++j;
          } else
            outputFrame.add(_frame.name(i), frameVecs[i].makeCopy());
        }
        BinaryConverter mrtask = new BinaryConverter(binaryCategorySizes);
        Frame binaryCols = mrtask.doAll(numOutputColumns, Vec.T_NUM, categoricalFrame).outputFrame();
        // change names of binaryCols so that they reflect the original names of the categories
        for (int i = 0, j = 0; i < binaryCategorySizes.length; j += binaryCategorySizes[i++]) {
          for (int k = 0; k < binaryCategorySizes[i]; ++k) {
            binaryCols._names[j + k] = categoricalFrame.name(i) + ":" + k;
          }
        }
        outputFrame.add(binaryCols);
        if (_skipCols!=null) {
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

  /**
   * Helper to convert a categorical variable into the first eigenvector of the dummy-expanded matrix.
   */
  public static class CategoricalEnumLimitedEncoder extends BaseCategoricalEncoder {
    
    final int _maxLevels;

    public CategoricalEnumLimitedEncoder(int maxLevels) {
      _maxLevels = maxLevels;
    }

    public CategoricalEnumLimitedEncoder(Model.AdaptFrameParameters params) {
      _maxLevels = params.getMaxCategoricalLevels();
    }

    @Override
    H2OCountedCompleter newDriver(Frame fr, Key<Frame> destKey, String[] skipCols) {
      return new CategoricalEnumLimitedDriver(fr, destKey, skipCols);
    }

    /**
     * Driver for CategoricalEnumLimited
     */
    class CategoricalEnumLimitedDriver extends H2OCountedCompleter {
      
      final Frame _frame;
      final Key<Frame> _destKey;
      final String[] _skipCols;
      
      CategoricalEnumLimitedDriver(Frame frame, Key<Frame> destKey, String[] skipCols) {
        _frame = frame; 
        _destKey = destKey; 
        _skipCols = skipCols;
      }

      @Override public void compute2() {
        Vec[] frameVecs = _frame.vecs();
        Vec[] extraVecs = new Vec[_skipCols==null?0:_skipCols.length];
        for (int i=0; i< extraVecs.length; ++i) {
          Vec v = _skipCols==null||_skipCols.length<=i?null:_frame.vec(_skipCols[i]); //can be null
          if (v!=null) extraVecs[i] = v;
        }
        //        Log.info(_frame.toTwoDimTable(0, (int)_frame.numRows()));
        Frame outputFrame = new Frame(_destKey);
        for (int i = 0; i < frameVecs.length; ++i) {
          Vec src = frameVecs[i];
          if (_skipCols!=null && ArrayUtils.find(_skipCols, _frame._names[i])>=0) continue;
          if (src.cardinality() > _maxLevels && !(src.isDomainTruncated(_maxLevels))) { //avoid double-encoding by checking it was not previously truncated on first encoding
            Key<Frame> source = Key.make();
            Key<Frame> dest = Key.make();
            Frame train = new Frame(source, new String[]{"enum"}, new Vec[]{src});
            DKV.put(train);
            Log.info("Reducing the cardinality of a categorical column with " + src.cardinality() + " levels to " + _maxLevels);
            train = Interaction.getInteraction(train._key, train.names(), _maxLevels).execImpl(dest).get();
            outputFrame.add(_frame.name(i) + ".top_" + _maxLevels + "_levels", train.anyVec().makeCopy());
            train.remove();
            DKV.remove(source);
          } else {
            outputFrame.add(_frame.name(i), frameVecs[i].makeCopy());
          }
        }
        for (int i=0;i<extraVecs.length;++i) {
          if (extraVecs[i]!=null)
            outputFrame.add(_skipCols[i], extraVecs[i].makeCopy());
        }
        //        Log.info(outputFrame.toTwoDimTable(0, (int)outputFrame.numRows()));
        DKV.put(outputFrame);
        tryComplete();
      }
    }
  }

  /**
   * Helper to convert a categorical variable into the first eigenvector of the dummy-expanded matrix.
   */
  public static class CategoricalEigenEncoder extends BaseCategoricalEncoder {
    final ToEigenVec _tev;

    public CategoricalEigenEncoder(ToEigenVec tev) {
      assert tev != null;
      _tev = tev;
    }

    public CategoricalEigenEncoder(Model.AdaptFrameParameters params) {
      _tev = params.getToEigenVec();
    }

    @Override
    H2OCountedCompleter newDriver(Frame fr, Key<Frame> destKey, String[] skipCols) {
      return new CategoricalEigenEncoderDriver(fr, destKey, skipCols);
    }

    /**
     * Driver for CategoricalEigenEncoder
     */
    class CategoricalEigenEncoderDriver extends H2OCountedCompleter {
      
      final Frame _frame;
      final Key<Frame> _destKey;
      final String[] _skipCols;
      
      CategoricalEigenEncoderDriver(Frame frame, Key<Frame> destKey, String[] skipCols) {
        _frame = frame; 
        _destKey = destKey;
        _skipCols = skipCols;
      }

      @Override public void compute2() {
        Vec[] frameVecs = _frame.vecs();
        Vec[] extraVecs = new Vec[_skipCols==null?0:_skipCols.length];
        for (int i=0; i< extraVecs.length; ++i) {
          Vec v = _skipCols==null||_skipCols.length<=i?null:_frame.vec(_skipCols[i]); //can be null
          if (v!=null) extraVecs[i] = v;
        }
        Frame outputFrame = new Frame(_destKey);
        for (int i = 0; i < frameVecs.length; ++i) {
          if (_skipCols!=null && ArrayUtils.find(_skipCols, _frame._names[i])>=0) continue;
          if (frameVecs[i].isCategorical())
            outputFrame.add(_frame.name(i) + ".Eigen", _tev.toEigenVec(frameVecs[i]));
          else
            outputFrame.add(_frame.name(i), frameVecs[i].makeCopy());
        }
        for (int i=0;i<extraVecs.length;++i) {
          if (extraVecs[i]!=null)
            outputFrame.add(_skipCols[i], extraVecs[i].makeCopy());
        }
        DKV.put(outputFrame);
        tryComplete();
      }
    }
  }
  
  public static class TargetEncoder extends BaseCategoricalEncoder {
    
    
    
    public TargetEncoder(Model.AdaptFrameParameters params) {
    }

    @Override
    H2OCountedCompleter newDriver(Frame fr, Key<Frame> destKey, String[] skipCols) {
      throw new UnsupportedOperationException("should not be called");
    }

    @Override
    public Frame encode(Frame fr, String[] skipCols) {
      return super.encode(fr, skipCols);
    }
  }
}
