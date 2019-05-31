package hex.svm;

import hex.DataInfo;
import water.Freezable;
import water.Iced;

public interface Kernel extends Freezable {

  double calcKernelWithLabel(DataInfo.Row a, DataInfo.Row b);

  double calcKernel(DataInfo.Row a, DataInfo.Row b);

}

final class GaussianKernel extends Iced implements Kernel {

  private final double _rbf_gamma;
  
  GaussianKernel(double rbfGamma) {
    _rbf_gamma = rbfGamma;
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
