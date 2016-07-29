package water.codegen.java.mixins;

/**
 * DRF model mixin - this class is used for code generation.
 *
 * @see water.codegen.java.DRFModelPOJOCodeGen
 */
public class DRFMixin extends SharedTreeModelMixin {

  public static double[] unifyPreds(double[] preds) {
    if (!GEN_IS_CLASSIFIER) { // Regression model
      preds[0] /= GEN_NTREES;
    } else {
      if (NCLASSES == 2 && GEN_BINOMIAL_DOUBLE_TREES) {
        preds[1] /= GEN_NTREES;
        preds[2] = 1.0 - preds[1];
      } else {
        double sum = 0;
        for(int i=1; i<preds.length; i++) { sum += preds[i]; }
        if (sum > 0) for(int i=1; i<preds.length; i++) { preds[i] /= sum; }
      }
    }
    return preds;
  }
}
