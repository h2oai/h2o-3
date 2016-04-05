package water.codegen.java.mixins;

import hex.genmodel.annotations.CG;

/**
 * Created by michal on 3/28/16.
 */
public class KMeansModelMixin extends ModelMixin {
  @CG(delegate = "._output._normSub", comment = "Column means of training data")
  public final static double[] MEANS = null;

  @CG(delegate = "._output._normMul", comment = "Reciprocal of column standard deviations of training data")
  public final static double[] MULTS = null;

  @CG(delegate = CG.NA)
  public final static double[][] CENTERS = null;

  @CG(delegate = "._parms._standardize")
  private final static boolean GEN_STANDARDIZE = false;

  public static double[] score0(double[] data, double[] preds) {
    // Ask compiler to optimize this part
    if (GEN_STANDARDIZE) {
      preds[0] = hex.genmodel.GenModel.KMeans_closest(CENTERS, data, DOMAINS, MEANS, MULTS);
    } else {
      preds[0] = hex.genmodel.GenModel.KMeans_closest(CENTERS, data, DOMAINS, null, null);
    }
    return preds;
  }
}
