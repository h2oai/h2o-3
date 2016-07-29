package water.codegen.java.mixins;

import hex.genmodel.annotations.CG;

/**
 * GBM mixin - the code is used for POJO code generation.
 *
 * @see water.codegen.java.GBMModelPOJOCodeGen
 */
public class GBMMixin extends SharedTreeModelMixin {

  @CG.Manual(comment = "Distribution family is Bernoulli")
  private static final boolean GEN_IS_BERNOULLI = false;

  @CG.Manual(comment = "Distribution family is Bernoulli")
  private static final boolean GEN_IS_MODIFIED_HUBER = false;

  @CG.Delegate(target = "._output._init_f", comment = "InitF value (for zero trees)")
  private static final double GEN_INIT_F = 0;

  public static double[] unifyPreds(double[] preds) {
    if (GEN_IS_BERNOULLI || GEN_IS_MODIFIED_HUBER) {
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
        hex.genmodel.GenModel.GBM_rescale(preds);
      } else {
        hex.genmodel.GenModel.GBM_rescale(preds);
      }
    }
    return preds;
  }

  static double linkInv(double pred) {
    throw new hex.genmodel.annotations.CG.CGException("Need to be implemented by a generator");
  }
}
