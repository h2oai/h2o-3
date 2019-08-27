package hex.psvm;

import water.AutoBuffer;

class SupportVector {
  private double _alpha;
  private double[] _numVals;
  private int[] _binIds;

  SupportVector fill(double alpha, double[] numVals, int[] binIds) {
    _alpha = alpha;
    _numVals = numVals;
    _binIds = binIds;
    return this;
  }

  int estimateSize() {
    return 8 + (4 + (_numVals.length * 8)) + (4 + (_binIds.length * 4));
  }

  void compress(AutoBuffer ab) {
    ab.put8d(_alpha);
    // categorical columns
    ab.put4(_binIds.length);
    for (int v : _binIds) {
      ab.put4(v);
    }
    // numeric columns
    ab.put4(_numVals.length);
    for (double v : _numVals) {
      ab.put8d(v);
    }
  }

}
