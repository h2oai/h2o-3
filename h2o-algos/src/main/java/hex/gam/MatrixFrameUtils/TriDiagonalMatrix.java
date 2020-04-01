package hex.gam.MatrixFrameUtils;

import water.MemoryManager;

public class TriDiagonalMatrix {
  public double[] _first_diag;
  public double[] _second_diag;
  public double[] _third_diag;

  public int _size;  // number of diagonal elements.  Matrix size is _size by _size+2

  public TriDiagonalMatrix(int size) {
    assert size>2:"Size of BiDiagonalMatrix must exceed 2 but is "+size;
    _size = size;
    _first_diag = MemoryManager.malloc8d(size);
    _second_diag = MemoryManager.malloc8d(size);
    _third_diag = MemoryManager.malloc8d(size);
  }

  // Implement the generation of D matrix.  Refer to doc 6.1
  public TriDiagonalMatrix(double[] hj) {
    this(hj.length-1);  // hj size k-1
    int diagSize = _size;
    for (int index=0; index < diagSize; index++) {
      double oneOhj = 1.0/hj[index];
      double oneOhjP1 = 1/hj[index+1];
      _first_diag[index] = oneOhj;
      _second_diag[index] = -oneOhj-oneOhjP1;
      _third_diag[index] = oneOhjP1;
    }
  }
}
