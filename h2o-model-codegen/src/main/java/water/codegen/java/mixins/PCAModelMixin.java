package water.codegen.java.mixins;

import hex.genmodel.annotations.CG;

/**
 * Model mixin for PCA model.
 */
public class PCAModelMixin extends ModelMixin {
  @CG.Delegate(target = "._output._normMul", comment = "Standardization/Normalization scaling factor for numerical variables.")
  public final static double[] NORMMUL = null;

  @CG.Delegate(target = "._output._normSub", comment = "Standardization/Normalization offset for numerical variables.")
  public final static double[] NORMSUB = null;

  @CG.Delegate(target = "._output._catOffsets", comment = "Categorical column offsets.")
  public final static int[] CATOFFS = null;

  @CG.Delegate(target = "._output._permutation", comment = "Permutation index vector.")
  public final static int[] PERMUTE = null;

  @CG.Delegate(target = "._output._eigenvectors_raw", comment = "Eigenvector matrix.")
  public final static double[][] EIGVECS = null;

  @CG.Delegate(target = "._parms._k", comment = "Number of principal components")
  public final static int K = 0;

  @CG.Delegate(target = "_output._ncats")
  private final static int GEN_NCATS = 0;

  @CG.Delegate(target = "_output._nnums")
  private final static int GEN_NNUMS = 0;

  @CG.Delegate(target = "_parms._use_all_factor_levels")
  private final static boolean GEN_USE_ALL_FACTOR_LEVELS = true;

  public double[] score0(double[] data, double[] preds) {
    java.util.Arrays.fill(preds,0);

    final int nstart = CATOFFS[CATOFFS.length-1];
    for (int i = 0; i < K; i++) {
      // Handle categorical columns
      for (int j = 0; j < GEN_NCATS; j++) {
        double d = data[PERMUTE[j]];
        if (Double.isNaN(d)) continue;
        int last = CATOFFS[j+1] - CATOFFS[j] - 1;
        int c = (int) d - (GEN_USE_ALL_FACTOR_LEVELS ? 0 : -1);
        if (c < 0 || c > last) continue;
        preds[i] += EIGVECS[CATOFFS[j] + c][i];
      }
      // Handle numeric columns
      for (int j = 0; j < GEN_NNUMS; j++) {
        preds[i] += (data[PERMUTE[j + GEN_NCATS]] - NORMSUB[j])*NORMMUL[j]*EIGVECS[j + (GEN_NCATS > 0 ? nstart : 0)][i];
      }
    }
    return preds;
  }

}
