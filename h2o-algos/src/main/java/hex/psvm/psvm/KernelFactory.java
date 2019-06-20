package hex.psvm.psvm;

import hex.genmodel.algos.psvm.KernelParameters;
import hex.genmodel.algos.psvm.KernelType;

public class KernelFactory {

  public static Kernel make(KernelType type, KernelParameters parms) {
    switch (type) {
      case gaussian:
        return new GaussianKernel(parms);
      default:
        throw new UnsupportedOperationException("Kernel type '" + type.name() + "' is not yet supported.");
    }
  }

}
