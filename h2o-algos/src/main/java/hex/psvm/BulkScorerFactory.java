package hex.psvm;

import hex.genmodel.algos.psvm.KernelParameters;
import hex.genmodel.algos.psvm.KernelType;

public class BulkScorerFactory {

  /**
   * Creates an instance of BulkSupportVectorScorer.
   * 
   * @param kt type of kernel
   * @param parms kernel parameters
   * @param svs compressed representation of the support vectors
   * @param svsCount number of support vectors
   * @param scoreRawBytes prefer a scorer that scores directly using the compressed vectors and doesn't allocate extra memory
   *                      (might not be available for all kernel types)
   * @return instance of BulkSupportVectorScorer
   */
  public static BulkSupportVectorScorer makeScorer(KernelType kt, KernelParameters parms, byte[] svs, 
                                                   int svsCount, boolean scoreRawBytes) {
    switch (kt) {
      case gaussian:
        if (scoreRawBytes)
          return new GaussianScorerRawBytes(parms, svs);
        else
          return new GaussianScorerParsed(parms, svs, svsCount);
      default:
        throw new UnsupportedOperationException("Scoring for kernel " + kt + " is not yet implemented");
    }
  }

}
