package biz.k11i.xgboost.tree;

import biz.k11i.xgboost.Predictor;
import biz.k11i.xgboost.util.FVec;
import hex.genmodel.algos.tree.TreeSHAP;
import hex.genmodel.algos.tree.TreeSHAPPredictor;

import java.lang.reflect.Field;

public class TreeSHAPHelper {

  public static TreeSHAPPredictor<FVec> makePredictor(RegTree tree) {
    RegTreeImpl treeImpl = (RegTreeImpl) tree;
    return new TreeSHAP<>(treeImpl.getNodes(), treeImpl.getStats(), 0);
  }

  public static float getInitPrediction(Predictor predictor) {
    Object mparam = getFieldValue(predictor, "mparam");
    Float initPred = getFieldValue(mparam, "base_score");
    if (initPred == null) {
      throw new IllegalStateException("Incompatible model, base_score is not defined");
    }
    return initPred;
  }

  @SuppressWarnings("unchecked")
  private static <T> T getFieldValue(Object o, String fieldName) {
    Field f = findNamedField(o, fieldName);
    if (f == null) {
      return null;
    } else {
      try {
        return (T) f.get(o);
      } catch (IllegalAccessException e) {
        return null;
      }
    }
  }

  private static Field findNamedField(Object o, String field_name) {
    Class clz = o.getClass();
    Field f;
    do {
      try {
        f = clz.getDeclaredField(field_name);
        f.setAccessible(true);
        return f;
      }
      catch (NoSuchFieldException e) {
        // fall through and try our parent
      }

      clz = clz.getSuperclass();
    } while (clz != Object.class);
    return null;
  }

}
