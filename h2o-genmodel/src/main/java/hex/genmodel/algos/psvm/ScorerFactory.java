package hex.genmodel.algos.psvm;

public class ScorerFactory {

  public static SupportVectorScorer makeScorer(KernelType kt, KernelParameters parms, byte[] svs) {
    switch (kt) {
      case gaussian:
        return new GaussianScorer(parms, svs);
      default:
        throw new UnsupportedOperationException("Scoring for kernel " + kt + " is not yet implemented");
    }
  }

}
