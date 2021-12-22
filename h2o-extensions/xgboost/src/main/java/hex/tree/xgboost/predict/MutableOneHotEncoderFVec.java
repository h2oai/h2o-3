package hex.tree.xgboost.predict;

import biz.k11i.xgboost.util.FVec;
import hex.DataInfo;
import hex.genmodel.GenModel;

public class MutableOneHotEncoderFVec implements FVec {

  private final DataInfo _di;
  private final boolean _treatsZeroAsNA;
  private final int[] _catMap;
  private final int[] _catValues;
  private final float[] _numValues;
  private final float _notHot;

  public MutableOneHotEncoderFVec(DataInfo di, boolean treatsZeroAsNA) {
    _di = di;
    _catValues = new int[_di._cats];
    _treatsZeroAsNA = treatsZeroAsNA;
    _notHot = _treatsZeroAsNA ? Float.NaN : 0;
    if (_di._catOffsets == null) {
      _catMap = new int[0];
    } else {
      _catMap = new int[_di._catOffsets[_di._cats]];
      for (int c = 0; c < _di._cats; c++) {
        for (int j = _di._catOffsets[c]; j < _di._catOffsets[c+1]; j++)
          _catMap[j] = c;
      }
    }
    _numValues = new float[_di._nums];
  }

  public void setInput(double[] input) {
    GenModel.setCats(input, _catValues, _di._cats, _di._catOffsets, _di._useAllFactorLevels);
    for (int i = 0; i < _numValues.length; i++) {
      float val = (float) input[_di._cats + i];
      _numValues[i] = _treatsZeroAsNA && (val == 0) ? Float.NaN : val;
    }
  }

  @Override
  public final float fvalue(int index) {
    if (index >= _catMap.length)
      return _numValues[index - _catMap.length];

    final boolean isHot = _catValues[_catMap[index]] == index;
    return isHot ? 1 : _notHot;
  }

  public void decodeAggregate(float[] encoded, float[] output) {
    for (int c = 0; c < _di._cats; c++) {
      float sum = 0;
      for (int i = _di._catOffsets[c]; i < _di._catOffsets[c + 1]; i++) {
        sum += encoded[i];
      }
      output[c] = sum;
    }
    int numStart = _di._catOffsets[_di._cats];
    if (_di._nums >= 0) 
      System.arraycopy(encoded, numStart, output, _di._cats, _di._nums);
  }
}
