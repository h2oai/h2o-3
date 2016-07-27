package water.codegen.java.mixins;

import hex.genmodel.annotations.CG;

/**
 * KMeans Model Mixin.
 */
public class KMeansModelMixin extends ModelMixin {
  @CG.Delegate(target = "._output._normSub", comment = "Column means of training data")
  public final static double[] MEANS = null;

  @CG.Delegate(target = "._output._normMul", comment = "Reciprocal of column standard deviations of training data")
  public final static double[] MULTS = null;

  @CG.Manual
  public final static double[][] CENTERS = null;

  @CG.Delegate(target = "._parms._standardize")
  private final static boolean GEN_STANDARDIZE = false;

  public double[] score0(double[] data, double[] preds) {
    // The compiler optimizes this part since code contains a constant GEN_STANDARDIZE
    if (GEN_STANDARDIZE) {
      preds[0] = hex.genmodel.GenModel.KMeans_closest(CENTERS, data, DOMAINS, MEANS, MULTS);
    } else {
      preds[0] = hex.genmodel.GenModel.KMeans_closest(CENTERS, data, DOMAINS, null, null);
    }
    return preds;
  }
}
