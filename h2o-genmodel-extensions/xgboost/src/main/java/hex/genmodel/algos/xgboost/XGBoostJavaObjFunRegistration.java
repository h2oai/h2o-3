package hex.genmodel.algos.xgboost;

import biz.k11i.xgboost.learner.ObjFunction;

public class XGBoostJavaObjFunRegistration {

  public static void register() {
    ObjFunction.register("reg:gamma", new RegObjFunction());
    ObjFunction.register("reg:tweedie", new RegObjFunction());
    ObjFunction.register("count:poisson", new RegObjFunction());
  }

  private static class RegObjFunction extends ObjFunction {
    @Override
    public float[] predTransform(float[] preds) {
      if (preds.length != 1)
        throw new IllegalStateException(
            "Regression problem is supposed to have just a single predicted value, got " + preds.length + " instead."
        );
      preds[0] = (float) Math.exp(preds[0]);
      return preds;
    }

    @Override
    public float predTransform(float pred) {
      return (float) Math.exp(pred);
    }
  }

}
