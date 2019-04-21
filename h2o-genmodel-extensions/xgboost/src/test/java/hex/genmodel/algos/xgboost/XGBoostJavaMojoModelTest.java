package hex.genmodel.algos.xgboost;

import biz.k11i.xgboost.learner.ObjFunction;
import org.junit.Test;

import static org.junit.Assert.*;

public class XGBoostJavaMojoModelTest {

  @Test
  public void testObjFunction() { // make sure we have implementation for all supported obj functions
    for (XGBoostMojoModel.ObjectiveType type : XGBoostMojoModel.ObjectiveType.values()) {
      assertNotNull(type.getId());
      assertFalse(type.getId().isEmpty());
      // check we have an implementation of ObjFunction
      assertNotNull(XGBoostJavaMojoModel.getObjFunction(type.getId()));
    }
  }

}