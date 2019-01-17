package ai.h2o.targetencoding;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.IcedHashMap;

public class BroadcastJoinForTargetEncoder {

  static class CompositeLookupKey {
    private String _levelValue;
    private Long _foldValue;

    CompositeLookupKey(String levelValue, long fold) {
      this._levelValue = levelValue;
      this._foldValue = fold;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj != null && obj instanceof CompositeLookupKey) {
        CompositeLookupKey s = (CompositeLookupKey) obj;
        return _levelValue.equals(s._levelValue) && _foldValue.equals(s._foldValue);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return (_levelValue + _foldValue).hashCode();
    }
  }

  static class EncodingData {
    private long numerator;
    private long denominator;
    
    EncodingData(long numerator, long denominator) {
      this.numerator = numerator;
      this.denominator = denominator;
    }

    long getNumerator() {
      return numerator;
    }

    long getDenominator() {
      return denominator;
    }
  }

  static class FrameWithEncodingDataToHashMap extends MRTask<FrameWithEncodingDataToHashMap> {

    IcedHashMap<CompositeLookupKey, EncodingData> getEncodingDataMap() {
      return _encodingDataMap;
    }

    IcedHashMap<CompositeLookupKey, EncodingData> _encodingDataMap = new IcedHashMap<>();
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
        long foldValue = _foldColumnIdx != -1 ? cs[_foldColumnIdx].at8(i) : -1;
        _encodingDataMap.put(new CompositeLookupKey(factor, foldValue), new EncodingData(numeratorChunk.at8(i), denominatorChunk.at8(i)));
      }
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
    int numeratorIdx = broadcastedFrame.find("numerator");
    int denominatorIdx = broadcastedFrame.find("denominator");

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
      for (int i = 0; i < num.len(); i++) {
        long levelValue = categoricalChunk.at8(i);
        String factor = categoricalChunk.vec().factor(levelValue);
        long foldValue = _foldColumnIdx != -1 ? cs[_foldColumnIdx].at8(i) : -1;
        CompositeLookupKey lookupKey = new CompositeLookupKey(factor, foldValue);
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
