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

import hex.DataInfo;
import hex.genmodel.algos.psvm.KernelParameters;
import water.Freezable;
import water.Iced;

public interface Kernel extends Freezable {

  double calcKernelWithLabel(DataInfo.Row a, DataInfo.Row b);

  double calcKernel(DataInfo.Row a, DataInfo.Row b);
  
}

final class GaussianKernel extends Iced implements Kernel {
  private final double _rbf_gamma;

  GaussianKernel(KernelParameters parms) {
    this(parms._gamma);
  }

  GaussianKernel(double rbf_gamma) {
    _rbf_gamma = rbf_gamma;
  }
  
  @Override
  public double calcKernel(DataInfo.Row a, DataInfo.Row b) {
    // ||a - b||^2 = (||a||^2 - 2 * a.b + ||b||^2)
    double norm_a_b_sq = a.response[1] + b.response[1] - 2 * a.innerProduct(b);
    return Math.exp(-_rbf_gamma * norm_a_b_sq);
  }

  @Override
  public double calcKernelWithLabel(DataInfo.Row a, DataInfo.Row b) {
    if ((int) a.response[0] != (int) b.response[0]) {
      return -calcKernel(a, b);
    } else {
      return calcKernel(a, b);
    }
  }

}
