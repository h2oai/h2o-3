/*
Copyright 2007 Google Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package hex.psvm.psvm;

import water.Iced;
import water.MemoryManager;

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

  private int dim() {
    return _dim;
  }

  final double get(int x, int y) {
    return _data[y][x-y];
  }

  final void set(int x, int y, double value) { 
    _data[y][x-y] = value;
  }

  final void addUnitMat() {
    for (double[] col : _data)
      col[0] += 1;
  }
  
  double[] cholSolve(double[] b) {
    double[] x = MemoryManager.malloc8d(b.length);
    cholForwardSub(b, x);
    cholBackwardSub(x, b);
    return b;
  }

  private void cholBackwardSub(double[] b, double[] x) {
    final int dim = dim();
    for (int k = dim - 1; k >= 0; k--) {
      double tmp = b[k];
      for (int i = k + 1; i < dim; i++) {
        tmp -= x[i] * get(i, k);
      }
      x[k] = tmp / get(k, k);
    }
  }

  private void cholForwardSub(double[] b, double[] x) {
    final int dim = dim();
    for (int k = 0; k < dim; ++k) {
      double tmp = b[k];
      for (int i = 0; i < k; ++i) {
        tmp -= x[i] * get(k, i);
      }
      x[k] = tmp / get(k, k);
    }
  }

  LLMatrix cf() {
    final int dim = dim();
    LLMatrix m = new LLMatrix(dim);
    for (int i = 0; i < dim; ++i) {
      for (int j = i; j < dim; ++j) {
        double sum = get(j, i);
        for (int k = i-1; k >= 0; --k) {
          sum -= m.get(i, k) * m.get(j, k);
        }
        if (i == j) {
          if (sum <= 0) {  // sum should be larger than 0
            throw new IllegalStateException("Only symmetric positive definite matrix can perform Cholesky factorization.");
          }
          m.set(i, i, Math.sqrt(sum));
        } else {
          m.set(j, i, sum / m.get(i, i));
        }
      }
    }
    return m;
  }

}
