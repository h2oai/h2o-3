package ai.h2o.targetencoding;

import water.MRTask;
import water.fvec.CategoricalWrappedVec;
import water.fvec.Chunk;
import water.fvec.Frame;

class BroadcastJoinForTargetEncoder {

  static class FrameWithEncodingDataToArray extends MRTask<FrameWithEncodingDataToArray> {
    
    /**
     * As it is less likely that we have more folds than categorical levels It is more preferable to keep `num` and `den` in a separate (but adjacent) rows of the 2D array and not in the same row with shifting.
     *     For _foldColumnIdx == -1
     *        _encodingDataPerNode[0] will be storing numerators
     *        _encodingDataPerNode[1] will be storing denominators
     *     For _foldColumnIdx != -1
     *        _encodingDataPerNode[2k-2] will be storing numerators, 
     *        _encodingDataPerNode[2k-1] will be storing denominators, where k is a current fold ; folds = {1,2,3...k}
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
        _encodingDataPerNode = new int[2][cardinalityOfCatCol];
      } else {
        assert maxFoldValue >= 1 : "There should be at leas two folds in the fold column";
        assert cardinalityOfCatCol > 0 : "Cardinality of categ. column should be greater than zero";
        _encodingDataPerNode = new int[(maxFoldValue + 1) * 2][cardinalityOfCatCol];
      }
    }

    @Override
    public void map(Chunk[] cs) {
      Chunk categoricalChunk = cs[_categoricalColumnIdx];
      Chunk numeratorChunk = cs[_numeratorIdx];
      Chunk denominatorChunk = cs[_denominatorIdx];
      for (int i = 0; i < categoricalChunk.len(); i++) {
        int levelValue = (int) categoricalChunk.at8(i);
        int[] arrForNumerators = null;
        int[] arrForDenominators = null;

        synchronized (_encodingDataPerNode) { // Sync on whole array as there is no way to sync on two objects ( two rows of 2D array)
          if (_foldColumnIdx != -1) {
          long foldValueFromVec = cs[_foldColumnIdx].at8(i);
          // We are allowed to do casting to `int` as we have validation before submitting this MRTask
          int foldValue = (int) foldValueFromVec; 
          arrForNumerators = _encodingDataPerNode[2 * foldValue];
          arrForDenominators = _encodingDataPerNode[2 * foldValue + 1];
        } else {
          arrForNumerators = _encodingDataPerNode[0];
          arrForDenominators = _encodingDataPerNode[1];
        }

          arrForNumerators[levelValue] = (int) numeratorChunk.at8(i);
          arrForDenominators[levelValue] = (int) denominatorChunk.at8(i);
        }
      }
    }

    @Override
    public void reduce(FrameWithEncodingDataToArray mrt) {
      int[][] leftArr = getEncodingDataArray();
      int[][] rightArr = mrt.getEncodingDataArray();
      for(int rowIdx = 0 ; rowIdx < leftArr.length; rowIdx++) {
        for(int colIdx = 0 ; colIdx < leftArr[rowIdx].length; colIdx++) {
          synchronized (leftArr) {
            int valueFromLeftArr = leftArr[rowIdx][colIdx];
            int valueFromRIghtArr = rightArr[rowIdx][colIdx];
            // Want to check what happens in a multiNode scenario
            if(valueFromLeftArr != valueFromRIghtArr) throw new IllegalStateException("Arrays are different instances");
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
   * @param leftFrame frame that we want to keep order of
   * @param leftCatColumnsIdxs indices of the categorical columns from `leftFrame` we want to use to calculate encodings
   * @param leftFoldColumnIdx index of the fold column from `leftFrame` or `-1` if we don't use folds
   * @param broadcastedFrame supposedly small frame that we will broadcast to all nodes and use it as lookup table for joining
   * @param rightCatColumnsIdxs indices of the categorical columns from `broadcastedFrame` we want to use to calculate encodings 
   * @param rightFoldColumnIdx index of the fold column from `broadcastedFrame` or `-1` if we don't use folds
   * @return
   */
  static Frame join(Frame leftFrame, int[] leftCatColumnsIdxs, int leftFoldColumnIdx, Frame broadcastedFrame, int[] rightCatColumnsIdxs, int rightFoldColumnIdx, int maxFoldValue) {
    int numeratorIdx = broadcastedFrame.find(TargetEncoder.NUMERATOR_COL_NAME);
    int denominatorIdx = broadcastedFrame.find(TargetEncoder.DENOMINATOR_COL_NAME);

    int broadcastedFrameCatCardinality = broadcastedFrame.vec(rightCatColumnsIdxs[0]).cardinality();

    if(rightFoldColumnIdx != -1 && broadcastedFrame.vec(rightFoldColumnIdx).max() > Integer.MAX_VALUE) 
      throw new IllegalArgumentException("Fold value should be a non-negative integer (i.e. should belong to [0, Integer.MAX_VALUE] range)");
      
    // Note: for our goals there is no need to store it in 2d array
    // Use `broadcastedFrame` as major frame(first argument) while computing level mappings as it contains all the levels from `training` dataset
    int[][] levelMappings = {CategoricalWrappedVec.computeMap( broadcastedFrame.vec(0).domain(), leftFrame.vec(leftCatColumnsIdxs[0]).domain())};

    int[][] encodingDataMap = new FrameWithEncodingDataToArray(rightCatColumnsIdxs[0], rightFoldColumnIdx, numeratorIdx, denominatorIdx, broadcastedFrameCatCardinality, maxFoldValue)
            .doAll(broadcastedFrame)
            .getEncodingDataArray();
    new BroadcastJoiner(leftCatColumnsIdxs, leftFoldColumnIdx, encodingDataMap, levelMappings).doAll(leftFrame);
    return leftFrame;
  }

  static class BroadcastJoiner extends MRTask<BroadcastJoiner> {
    int _categoricalColumnIdx, _foldColumnIdx;
    int[][] _encodingDataArray;
    int[][] _levelMappings;

    BroadcastJoiner(int[] categoricalColumnsIdxs, int foldColumnIdx, int[][] encodingDataMap, int[][] levelMappings) {
      assert categoricalColumnsIdxs.length == 1 : "Only single column target encoding(i.e. one categorical column is used to produce its encodings) is supported for now";

      _categoricalColumnIdx = categoricalColumnsIdxs[0];
      _foldColumnIdx = foldColumnIdx;
      _encodingDataArray = encodingDataMap;
      _levelMappings = levelMappings;
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

        int[] arrForNumerators = null;
        int[] arrForDenominators = null;

        int foldValue = -1;
        if (_foldColumnIdx != -1) {
          long foldValueFromVec = cs[_foldColumnIdx].at8(i);
          foldValue = (int) foldValueFromVec;

        } else {
          foldValue = 0;
        }

        arrForNumerators = _encodingDataArray[2 * foldValue];
        arrForDenominators = _encodingDataArray[2 * foldValue + 1];

        if(mappedLevelValue >= arrForDenominators.length) {
          setEncodingComponentsToNAs(num, den, i);
        } else {
          int denominator = arrForDenominators[mappedLevelValue];
          if (denominator == 0) {
            setEncodingComponentsToNAs(num, den, i);
          } else {
            int numerator = arrForNumerators[mappedLevelValue];
            num.set(i, numerator);
            den.set(i, denominator);
          }
        }
      }
    }

    // Later - in `CalcEncodings` task - NAs will be imputed by prior
    private void setEncodingComponentsToNAs(Chunk num, Chunk den, int i) {
      num.setNA(i);
      den.setNA(i);
    }
  }
}
