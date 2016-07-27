package water.codegen.java.mixins;

import hex.genmodel.annotations.CG;

/**
 * Created by michal on 5/12/16.
 */
public class GBMMixin extends SharedTreeModelMixin {

  @CG.Manual(comment = "Distribution family is Bernoulli")
  public static final boolean GEN_IS_BERNOULLI = false;

  @CG.Delegate(target = "_output._init_f", comment = "InitF value (for zero trees)")
  public static final double GEN_INIT_F = 0;


  public static double[] unifyPreds(double[] preds) {
    if (GEN_IS_BERNOULLI) {
      preds[2] = preds[1] + GEN_INIT_F;
      preds[2] = linkInv(preds[2]);
      preds[1] = 1.0 - preds[2];
    } else {
      if (!GEN_IS_CLASSIFIER) { // Regression model
        preds[0] += GEN_INIT_F;
        preds[0] =  linkInv(preds[0]);
      } else if (NCLASSES == 2) {
        preds[1] += GEN_INIT_F;
        preds[2] = - preds[1];
      }
      hex.genmodel.GenModel.GBM_rescale(preds);
    }
    return preds;
  }

  static double linkInv(double pred) {
    throw new hex.genmodel.annotations.CG.CGException("Need to be implemented by a generator");
  }
}
