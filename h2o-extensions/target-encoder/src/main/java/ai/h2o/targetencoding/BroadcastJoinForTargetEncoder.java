package ai.h2o.targetencoding;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.IcedHashMap;

import java.util.Objects;

class BroadcastJoinForTargetEncoder {
  
  static class CompositeLookupKey {
    // Note: Consider using indexes of levels instead of strings to save space. It will probably require 
    // to use CategoricalWrappedVec.computeMap() since indexes for levels could differ in both frames for the same level values.
    private String _levelValue;
    private int _foldValue;

    CompositeLookupKey(String levelValue, int fold) {
      this._levelValue = levelValue;
      this._foldValue = fold;
    }

    CompositeLookupKey() {
      this._levelValue = null;
      this._foldValue = -1;
    }
    
    public void update(String levelValue, int fold) {
      this._levelValue = levelValue;
      this._foldValue = fold;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      CompositeLookupKey lookupKey = (CompositeLookupKey) o;
      return _foldValue == lookupKey._foldValue &&
              _levelValue.equals(lookupKey._levelValue);
    }

    @Override
    public int hashCode() {
      return Objects.hash(_levelValue, _foldValue);
    }
  }

  static class EncodingData {
    private long _numerator;
    private long _denominator;
    
    EncodingData(long numerator, long denominator) {
      this._numerator = numerator;
      this._denominator = denominator;
    }

    long getNumerator() {
      return _numerator;
    }

    long getDenominator() {
      return _denominator;
    }
  }

  private static String FOLD_VALUE_IS_OUT_OF_RANGE_MSG = "Fold value should be a non-negative integer (i.e. should belong to [0, Integer.MAX_VALUE] range)";


  static class FrameWithEncodingDataToHashMap extends MRTask<FrameWithEncodingDataToHashMap> {

    IcedHashMap<CompositeLookupKey, EncodingData> _encodingDataMapPerNode = new IcedHashMap<>();
    int _categoricalColumnIdx, _foldColumnIdx, _numeratorIdx, _denominatorIdx;

    FrameWithEncodingDataToHashMap(int categoricalColumnIdx, int foldColumnId, int numeratorIdx, int denominatorIdx) {
      this._categoricalColumnIdx = categoricalColumnIdx;
      this._foldColumnIdx = foldColumnId;
      this._numeratorIdx = numeratorIdx;
      this._denominatorIdx = denominatorIdx;
    }

    @Override
    public void map(Chunk[] cs) {
      Chunk categoricalChunk = cs[_categoricalColumnIdx];
      Chunk numeratorChunk = cs[_numeratorIdx];
      Chunk denominatorChunk = cs[_denominatorIdx];
      for (int i = 0; i < categoricalChunk.len(); i++) {
        long levelValue = categoricalChunk.at8(i);
        String factor = categoricalChunk.vec().factor(levelValue);

        int foldValue = -1;
        if(_foldColumnIdx != -1) {
          long foldValueFromVec = cs[_foldColumnIdx].at8(i);
          assert foldValueFromVec <= Integer.MAX_VALUE && foldValueFromVec >= 0 : FOLD_VALUE_IS_OUT_OF_RANGE_MSG;
          foldValue = (int) foldValueFromVec;
        }
        _encodingDataMapPerNode.put(new CompositeLookupKey(factor, foldValue), new EncodingData(numeratorChunk.at8(i), denominatorChunk.at8(i)));
      }
    }

    @Override
    public void reduce(FrameWithEncodingDataToHashMap mrt) {
      _encodingDataMapPerNode.putAll(mrt.getEncodingDataMap());
    }
    
    IcedHashMap<CompositeLookupKey, EncodingData> getEncodingDataMap() {
      return _encodingDataMapPerNode;
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
  static Frame join(Frame leftFrame, int[] leftCatColumnsIdxs, int leftFoldColumnIdx, Frame broadcastedFrame, int[] rightCatColumnsIdxs, int rightFoldColumnIdx) {
    int numeratorIdx = broadcastedFrame.find(TargetEncoder.NUMERATOR_COL_NAME);
    int denominatorIdx = broadcastedFrame.find(TargetEncoder.DENOMINATOR_COL_NAME);

    IcedHashMap<CompositeLookupKey, EncodingData> encodingDataMap = new FrameWithEncodingDataToHashMap(rightCatColumnsIdxs[0], rightFoldColumnIdx, numeratorIdx, denominatorIdx)
            .doAll(broadcastedFrame)
            .getEncodingDataMap();
    new BroadcastJoiner(leftCatColumnsIdxs, leftFoldColumnIdx, encodingDataMap).doAll(leftFrame);
    return leftFrame;
  }

  static class BroadcastJoiner extends MRTask<BroadcastJoiner> {
    int _categoricalColumnIdx, _foldColumnIdx;
    IcedHashMap<CompositeLookupKey, EncodingData> _encodingDataMap;

    BroadcastJoiner(int[] categoricalColumnsIdxs, int foldColumnIdx, IcedHashMap<CompositeLookupKey, EncodingData> encodingDataMap) {
      assert categoricalColumnsIdxs.length == 1 : "Only single column target encoding is supported for now";

      this._categoricalColumnIdx = categoricalColumnsIdxs[0];
      this._foldColumnIdx = foldColumnIdx;
      this._encodingDataMap = encodingDataMap;
    }

    @Override
    public void map(Chunk[] cs) {
      Chunk categoricalChunk = cs[_categoricalColumnIdx];
      int numOfVecs = cs.length;
      Chunk num = cs[numOfVecs - 2];
      Chunk den = cs[numOfVecs - 1];
      CompositeLookupKey lookupKey = new CompositeLookupKey();
      for (int i = 0; i < num.len(); i++) {
        long levelValue = categoricalChunk.at8(i);
        String factor = categoricalChunk.vec().factor(levelValue);
        
        int foldValue = -1;
        if(_foldColumnIdx != -1) {
          long foldValueFromVec = cs[_foldColumnIdx].at8(i);
          assert foldValueFromVec <= Integer.MAX_VALUE && foldValueFromVec >= 0 : FOLD_VALUE_IS_OUT_OF_RANGE_MSG;
          foldValue = (int) foldValueFromVec;
        }
        
        lookupKey.update(factor, foldValue);
        EncodingData encodingData = _encodingDataMap.get(lookupKey);
        if(encodingData == null) {
          num.setNA(i);
          den.setNA(i);
        } else {
          num.set(i, encodingData.getNumerator());
          den.set(i, encodingData.getDenominator());
        }
      }
    }
  }
}
