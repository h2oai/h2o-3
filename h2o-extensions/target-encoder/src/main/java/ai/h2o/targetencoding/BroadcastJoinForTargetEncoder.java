package ai.h2o.targetencoding;

import water.MRTask;
import water.MemoryManager;
import water.fvec.CategoricalWrappedVec;
import water.fvec.Chunk;
import water.fvec.Frame;

class BroadcastJoinForTargetEncoder {

  static class FrameWithEncodingDataToArray extends MRTask<FrameWithEncodingDataToArray> {
    
    /**
     * Numerators and denominators are being stored in the same row-array of the 2D array.
     * 
     *        _encodingDataPerNode[k][0 .. numOfCatLevels -1] will be storing numerators
     *        _encodingDataPerNode[k][numOfCatLevels .. (2*numOfCatLevels -1)] will be storing denominators,
     *        
     *        where k is a current fold value (folds = {0,1,2,3...k}) or 0 when _foldColumnIdx == -1; 
     */
    int[][] _encodingDataPerNode = null;
    
    int _categoricalColumnIdx, _foldColumnIdx, _numeratorIdx, _denominatorIdx, _cardinalityOfCatCol, _maxFoldValue;

    FrameWithEncodingDataToArray(int categoricalColumnIdx, int foldColumnId, int numeratorIdx, int denominatorIdx, int cardinalityOfCatCol, int maxFoldValue) {
      _categoricalColumnIdx = categoricalColumnIdx;
      _foldColumnIdx = foldColumnId;
      _numeratorIdx = numeratorIdx;
      _denominatorIdx = denominatorIdx;
      _cardinalityOfCatCol = cardinalityOfCatCol;

      if(foldColumnId == -1) {
        _encodingDataPerNode = MemoryManager.malloc4(1, _cardinalityOfCatCol * 2);
      } else {
        assert maxFoldValue >= 1 : "There should be at leas two folds in the fold column";
        assert _cardinalityOfCatCol > 0 && _cardinalityOfCatCol < (Integer.MAX_VALUE / 2)  : "Cardinality of categ. column should be within range (0, Integer.MAX_VALUE / 2 )";
        _encodingDataPerNode = MemoryManager.malloc4(maxFoldValue + 1,_cardinalityOfCatCol * 2);
      }
    }

    @Override
    public void map(Chunk[] cs) {
      Chunk categoricalChunk = cs[_categoricalColumnIdx];
      Chunk numeratorChunk = cs[_numeratorIdx];
      Chunk denominatorChunk = cs[_denominatorIdx];
      for (int i = 0; i < categoricalChunk.len(); i++) {
        int levelValue = (int) categoricalChunk.at8(i);

        // We are allowed to do casting to `int` as we have validation before submitting this MRTask
        int foldValue = _foldColumnIdx != -1 ? (int) cs[_foldColumnIdx].at8(i) : 0;
        int[] arrForNumeratorsAndDenominators = _encodingDataPerNode[foldValue];

        arrForNumeratorsAndDenominators[levelValue] = (int) numeratorChunk.at8(i);
        arrForNumeratorsAndDenominators[_cardinalityOfCatCol + levelValue] = (int) denominatorChunk.at8(i);
      }
    }

    @Override
    public void reduce(FrameWithEncodingDataToArray mrt) {
      int[][] leftArr = getEncodingDataArray();
      int[][] rightArr = mrt.getEncodingDataArray();
      
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
            int valueFromLeftArr = leftArr[rowIdx][colIdx];
            int valueFromRIghtArr = rightArr[rowIdx][colIdx];
            leftArr[rowIdx][colIdx] = Math.max(valueFromLeftArr, valueFromRIghtArr);
          }
        }
      }
    }
    
    int[][] getEncodingDataArray() {
      return _encodingDataPerNode;
    }
  }

  /**
   * 
   * @param leftFrame frame that we want to keep rows order of
   * @param leftCatColumnsIdxs indices of the categorical columns from `leftFrame` we want to use to calculate encodings
   * @param leftFoldColumnIdx index of the fold column from `leftFrame` or `-1` if we don't use folds
   * @param broadcastedFrame supposedly small frame that we will broadcast to all nodes and use it as lookup table for joining
   * @param rightCatColumnsIdxs indices of the categorical columns from `broadcastedFrame` we want to use to calculate encodings 
   * @param rightFoldColumnIdx index of the fold column from `broadcastedFrame` or `-1` if we don't use folds
   * @return `leftFrame` with joined numerators and denominators
   */
  static Frame join(Frame leftFrame, int[] leftCatColumnsIdxs, int leftFoldColumnIdx, Frame broadcastedFrame, int[] rightCatColumnsIdxs, int rightFoldColumnIdx, int maxFoldValue) {
    int numeratorIdx = broadcastedFrame.find(TargetEncoder.NUMERATOR_COL_NAME);
    int denominatorIdx = broadcastedFrame.find(TargetEncoder.DENOMINATOR_COL_NAME);

    int broadcastedFrameCatCardinality = broadcastedFrame.vec(rightCatColumnsIdxs[0]).cardinality();

    if(rightFoldColumnIdx != -1 && broadcastedFrame.vec(rightFoldColumnIdx).max() > Integer.MAX_VALUE) 
      throw new IllegalArgumentException("Fold value should be a non-negative integer (i.e. should belong to [0, Integer.MAX_VALUE] range)");
      
    int[][] levelMappings = {CategoricalWrappedVec.computeMap( leftFrame.vec(leftCatColumnsIdxs[0]).domain(), broadcastedFrame.vec(0).domain())};

    int[][] encodingDataArray = new FrameWithEncodingDataToArray(rightCatColumnsIdxs[0], rightFoldColumnIdx, numeratorIdx, denominatorIdx, broadcastedFrameCatCardinality, maxFoldValue)
            .doAll(broadcastedFrame)
            .getEncodingDataArray();
    new BroadcastJoiner(leftCatColumnsIdxs, leftFoldColumnIdx, encodingDataArray, levelMappings, broadcastedFrameCatCardinality)
            .doAll(leftFrame);
    return leftFrame;
  }

  static class BroadcastJoiner extends MRTask<BroadcastJoiner> {
    int _categoricalColumnIdx, _foldColumnIdx, _cardinalityOfCatCol;
    int[][] _encodingDataArray;
    int[][] _levelMappings;

    BroadcastJoiner(int[] categoricalColumnsIdxs, int foldColumnIdx, int[][] encodingDataArray, int[][] levelMappings, int cardinalityOfCatCol) {
      assert categoricalColumnsIdxs.length == 1 : "Only single column target encoding(i.e. one categorical column is used to produce its encodings) is supported for now";

      _categoricalColumnIdx = categoricalColumnsIdxs[0];
      _foldColumnIdx = foldColumnIdx;
      _encodingDataArray = encodingDataArray;
      _levelMappings = levelMappings;
      _cardinalityOfCatCol = cardinalityOfCatCol;
    }

    @Override
    public void map(Chunk[] cs) {
      Chunk categoricalChunk = cs[_categoricalColumnIdx];
      int numOfVecs = cs.length;
      Chunk num = cs[numOfVecs - 2];
      Chunk den = cs[numOfVecs - 1];
      for (int i = 0; i < num.len(); i++) {
        int levelValue = (int) categoricalChunk.at8(i);
        int mappedLevelValue = -1;
        int numberOfLevelValues = _levelMappings[0].length;
        if(levelValue < numberOfLevelValues ) {
          mappedLevelValue = _levelMappings[0][levelValue];
        } else {
          setEncodingComponentsToNAs(num, den, i);
          continue;
        }

        int[] arrForNumeratorsAndDenominators = null;

        int foldValue = -1;
        if (_foldColumnIdx != -1) {
          long foldValueFromVec = cs[_foldColumnIdx].at8(i);
          foldValue = (int) foldValueFromVec;

        } else {
          foldValue = 0;
        }

        arrForNumeratorsAndDenominators = _encodingDataArray[foldValue];

        if(mappedLevelValue >= _cardinalityOfCatCol) {
          setEncodingComponentsToNAs(num, den, i);
        } else {
          int denominator = arrForNumeratorsAndDenominators[_cardinalityOfCatCol + mappedLevelValue];
          if (denominator == 0) {
            setEncodingComponentsToNAs(num, den, i);
          } else {
            int numerator = arrForNumeratorsAndDenominators[mappedLevelValue];
            num.set(i, numerator);
            den.set(i, denominator);
          }
        }
      }
    }

    // Note: Later - in `CalcEncodings` task - NAs will be imputed by prior
    private void setEncodingComponentsToNAs(Chunk num, Chunk den, int i) {
      num.setNA(i);
      den.setNA(i);
    }
  }
}
