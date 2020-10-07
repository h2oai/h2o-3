package ai.h2o.targetencoding;

import water.MRTask;
import water.MemoryManager;
import water.fvec.CategoricalWrappedVec;
import water.fvec.Chunk;
import water.fvec.Frame;

class TargetEncoderBroadcastJoin {

  /**
   * @param leftFrame frame for which we want to keep the rows order.
   * @param leftCatColumnsIdxs indices of the categorical columns from `leftFrame` for which we want to calculate encodings.
   *        Only one categorical column is currently supported.
   * @param leftFoldColumnIdx index of the fold column from `leftFrame` or `-1` if we don't use folds.
   * @param rightFrame supposedly small frame that we will broadcast to all nodes and use as a lookup table for joining.
   * @param rightCatColumnsIdxs indices of the categorical columns from `rightFrame`.
   *        Only one categorical column is currently supported.
   * @param rightFoldColumnIdx index of the fold column from `rightFrame` or `-1` if we don't use folds.
   * @param maxFoldValue the highest fold value (e.g. 4 if 5 folds).
   * @return the `leftFrame` with joined numerators and denominators.
   */
  static Frame join(Frame leftFrame, int[] leftCatColumnsIdxs, int leftFoldColumnIdx,
                    Frame rightFrame, int[] rightCatColumnsIdxs, int rightFoldColumnIdx,
                    int maxFoldValue) {
    int rightNumeratorIdx = rightFrame.find(TargetEncoderHelper.NUMERATOR_COL);
    int rightDenominatorIdx = rightFrame.find(TargetEncoderHelper.DENOMINATOR_COL);
    
    // currently supporting only one categorical column
    assert leftCatColumnsIdxs.length == 1;
    assert rightCatColumnsIdxs.length == 1;
    int leftCatColumnIdx = leftCatColumnsIdxs[0];
    int rightCatColumnIdx = rightCatColumnsIdxs[0];
    int rightCatCardinality = rightFrame.vec(rightCatColumnIdx).cardinality();

    if (rightFoldColumnIdx != -1 && rightFrame.vec(rightFoldColumnIdx).max() > Integer.MAX_VALUE)
      throw new IllegalArgumentException("Fold value should be a non-negative integer (i.e. should belong to [0, Integer.MAX_VALUE] range)");
      
    int[][] levelMappings = {
            CategoricalWrappedVec.computeMap(
                    leftFrame.vec(leftCatColumnIdx).domain(), 
                    rightFrame.vec(rightCatColumnIdx).domain()
            )
    };

    double[][] encodingData = encodingsToArray(
            rightFrame, rightCatColumnIdx, rightFoldColumnIdx, 
            rightNumeratorIdx, rightDenominatorIdx, 
            rightCatCardinality, maxFoldValue
    );

    // BroadcastJoiner is currently modifying the frame in-place, so we need to provide the numerator and denominator columns.
    Frame resultFrame = new Frame(leftFrame);
    resultFrame.add(TargetEncoderHelper.NUMERATOR_COL, resultFrame.anyVec().makeCon(0));
    resultFrame.add(TargetEncoderHelper.DENOMINATOR_COL, resultFrame.anyVec().makeCon(0));
    new BroadcastJoiner(leftCatColumnsIdxs, leftFoldColumnIdx, encodingData, levelMappings, rightCatCardinality-1)
            .doAll(resultFrame);
    return resultFrame;
  }
  
  static double[][] encodingsToArray(Frame encodingsFrame, int categoricalColIdx, int foldColIdx, 
                                     int numColIdx, int denColIdx, 
                                     int categoricalColCardinality, int maxFoldValue) {
      return new FrameWithEncodingDataToArray(categoricalColIdx, foldColIdx, numColIdx, denColIdx, categoricalColCardinality, maxFoldValue)
              .doAll(encodingsFrame)
              .getEncodingDataArray();
  }

  private static class FrameWithEncodingDataToArray extends MRTask<FrameWithEncodingDataToArray> {

    /**
     * Numerators and denominators are being stored in the same row-array of the 2D array.
     *
     *        _encodingDataPerNode[k][2*i] will be storing numerators
     *        _encodingDataPerNode[k][2*i+1] will be storing denominators
     *        (easier to inspect when debugging).
     *
     *        where k is a current fold value (folds = {0,1,2,3...k}) or 0 when _foldColumnIdx == -1; 
     */
    final double[][] _encodingDataPerNode;

    final int _categoricalColumnIdx, _foldColumnIdx, _numeratorIdx, _denominatorIdx, _cardinalityOfCatCol;

    FrameWithEncodingDataToArray(int categoricalColumnIdx, int foldColumnIdx, int numeratorIdx, int denominatorIdx, int cardinalityOfCatCol, int maxFoldValue) {
      _categoricalColumnIdx = categoricalColumnIdx;
      _foldColumnIdx = foldColumnIdx;
      _numeratorIdx = numeratorIdx;
      _denominatorIdx = denominatorIdx;
      _cardinalityOfCatCol = cardinalityOfCatCol;

      if (foldColumnIdx == -1) {
        _encodingDataPerNode = MemoryManager.malloc8d(1, _cardinalityOfCatCol * 2);
      } else {
        assert maxFoldValue >= 1 : "There should be at least two folds in the fold column";
        assert _cardinalityOfCatCol > 0 && _cardinalityOfCatCol < (Integer.MAX_VALUE / 2)  : "Cardinality of categ. column should be within range (0, Integer.MAX_VALUE / 2 )";
        _encodingDataPerNode = MemoryManager.malloc8d(maxFoldValue + 1,_cardinalityOfCatCol * 2);
      }
    }

    @Override
    public void map(Chunk[] cs) {
      Chunk categoricalChunk = cs[_categoricalColumnIdx];
      Chunk numeratorChunk = cs[_numeratorIdx];
      Chunk denominatorChunk = cs[_denominatorIdx];
      for (int i = 0; i < categoricalChunk.len(); i++) {
        int level = (int) categoricalChunk.at8(i);

        // We are allowed to do casting to `int` as we have validation before submitting this MRTask
        int foldValue = _foldColumnIdx != -1 ? (int) cs[_foldColumnIdx].at8(i) : 0;
        double[] numDenArray = _encodingDataPerNode[foldValue];
        numDenArray[2*level] = numeratorChunk.atd(i);
        numDenArray[2*level+1] = denominatorChunk.at8(i);
      }
    }

    @Override
    public void reduce(FrameWithEncodingDataToArray mrt) {
      double[][] leftArr = getEncodingDataArray();
      double[][] rightArr = mrt.getEncodingDataArray();

      // Note: we need to add `leftArr != rightArr` check due to the following circumstances: 
      //       1. MRTasks are being only shallow cloned i.e. all internal fields are references to the same objects in memory
      //       2. MRTasks' shallow copies, that were also sent to other nodes, will become a deep copies of the original shallow copies (due to serialisation/deserialisation)
      //       3. there is a chance that reduce phase will start before map phase is finished:
      //
      //          In the following example we are only concerned with what is happening within a single node (due to 2.).
      //
      //                      T(r)
      //                   /        \
      //                  /          \
      //                Tl(r)         Tr(r)
      //              /      \       /     \
      //            Tll(m)  Tlr(m)  Trl(m)  Trr(m)              
      //                                                , where (r) stands for reduce phase only, (m) - map phase only
      //
      //        
      //        Tll(m) and Tlr(m) manipulate the same array (due to 1.), but not the same cells -> no race condition.
      //        The race arises because, for example, Tl(r) can  occur in parallel with Trl(m), Tr(r) or both.
      //        Once both Tl(r) and Tr(r) are completed, T(r) itself is safe.
      //        
      //        Steps that led to the race in a FrameWithEncodingDataToArray.reduce without `leftArr != rightArr` check:
      //          - Tl(r); Math.max for a particular cell Cij was computed to be 0 (not yet assigning result to Cij cell)
      //          - Trl(m) & Trr(m); Cij was updated during map phase with non-zero value of 42
      //          - Tr(r); Math.max for Cij was computed to be 42 and assigned to Cij cell of 2D array
      //          - Tl(r); assigned to Cij cell of 2D array value of 0 and effectively overriding previously assigned value of 42
      //       
      if (leftArr != rightArr) {
        for (int rowIdx = 0; rowIdx < leftArr.length; rowIdx++) {
          for (int colIdx = 0; colIdx < leftArr[rowIdx].length; colIdx++) {
            double valueFromLeftArr = leftArr[rowIdx][colIdx];
            double valueFromRIghtArr = rightArr[rowIdx][colIdx];
            leftArr[rowIdx][colIdx] = Math.max(valueFromLeftArr, valueFromRIghtArr);
          }
        }
      }
    }

    double[][] getEncodingDataArray() {
      return _encodingDataPerNode;
    }
  }

  private static class BroadcastJoiner extends MRTask<BroadcastJoiner> {
    int _categoricalColumnIdx, _foldColumnIdx, _maxKnownCatLevel;
    double[][] _encodingDataArray;
    int[][] _levelMappings;

    BroadcastJoiner(int[] categoricalColumnsIdxs, int foldColumnIdx, double[][] encodingDataArray, int[][] levelMappings, int maxKnownCatLevel) {
      assert categoricalColumnsIdxs.length == 1 : "Only single column target encoding (i.e. one categorical column is used to produce its encodings) is supported for now";
      assert levelMappings.length == 1;
      
      _categoricalColumnIdx = categoricalColumnsIdxs[0];
      _foldColumnIdx = foldColumnIdx;
      _encodingDataArray = encodingDataArray;
      _levelMappings = levelMappings;
      _maxKnownCatLevel = maxKnownCatLevel;
    }

    @Override
    public void map(Chunk[] cs) {
      int[] levelMapping = _levelMappings[0]; //see constraint in constructor
      
      Chunk categoricalChunk = cs[_categoricalColumnIdx];
      Chunk num = cs[cs.length - 2]; // numerator and denominator columns are appended in #join method.
      Chunk den = cs[cs.length - 1];
      for (int i = 0; i < num.len(); i++) {
        int level = (int) categoricalChunk.at8(i);
        if (level >= levelMapping.length) { // should never happen: when joining, level is a category in the left frame, and levelMapping.length == size of the domain on that frame
          setEncodingComponentsToNAs(num, den, i);
          continue;
        }
        
        int mappedLevel = levelMapping[level];
        int foldValue = _foldColumnIdx >= 0 ? (int)cs[_foldColumnIdx].at8(i) : 0;
        double[] numDenArray = _encodingDataArray[foldValue];

        if (mappedLevel > _maxKnownCatLevel) {  // level not in encodings (unseen in training data) -> NA
          setEncodingComponentsToNAs(num, den, i);
        } else {
          double denominator = numDenArray[2*mappedLevel+1];
          if (denominator == 0) { // no occurrence of current level for this fold -> NA
            setEncodingComponentsToNAs(num, den, i);
          } else {
            double numerator = numDenArray[2*mappedLevel];
            num.set(i, numerator);
            den.set(i, denominator);
          }
        }
      }
    }

    // Note: Later - in `TargetEncodingHelper.ApplyEncodings` task - NAs will be imputed by prior
    private void setEncodingComponentsToNAs(Chunk num, Chunk den, int i) {
      num.setNA(i);
      den.setNA(i);
    }
  }
}
