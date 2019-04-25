package hex.svm;

import water.Iced;

class LLMatrix extends Iced<LLMatrix> {

  private final int _dim;
  private final double[][] _data;

  LLMatrix(int dim) {
    _dim = dim;
    _data = new double[dim][];
    for (int i = 0; i < dim; i++) {
      _data[i] = new double[dim - i];
    }
  }

  int dim() {
    return _dim;
  }

  double get(int x, int y) {
    return _data[y][x-y];
  }

  void set(int x, int y, double value) { 
    _data[y][x-y] = value;
  }

}
