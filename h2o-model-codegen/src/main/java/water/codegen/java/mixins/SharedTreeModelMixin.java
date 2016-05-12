package water.codegen.java.mixins;

import hex.genmodel.annotations.CG;

/**
 * Common mixin for all tree models.
 */
public class SharedTreeModelMixin extends ModelMixin {

  @CG.Delegate(target = "._output._ntrees", comment = "Number of generated trees")
  public static final int GEN_NTREES = 0;

  @CG.Delegate(target = "_parms._binomial_double_trees", comment = "Use two trees to represent a single binomial decision tree.")
  public static final boolean GEN_BINOMIAL_DOUBLE_TREES = false;

  public double[] score0(double[] data, double[] preds ) {
    java.util.Arrays.fill(preds,0);
    // Tree model specific
    scoreImpl(data, preds);
    // Unify prediction
    unifyPreds(preds);
    // Unify class probabilities
    if (GEN_IS_CLASSIFIER) {
      if (GEN_BALANCE_CLASSES) {
        hex.genmodel.GenModel.correctProbabilities(preds, PRIOR_CLASS_DISTRIB, MODEL_CLASS_DISTRIB);
      }
      preds[0] = hex.genmodel.GenModel.getPrediction(preds, PRIOR_CLASS_DISTRIB, data, DEFAULT_THRESHOLD);
    }
    return preds;
  }

  public static double[] scoreImpl(double[] data, double[] preds) {
    throw new CG.CGException("The method needs to be implemented!");
  }

  public static double[] unifyPreds(double[] preds) {
    throw new CG.CGException("The method needs to be implemented!");
  }
}
