package hex.genmodel.algos.xgboost;

import com.google.gson.JsonObject;
import hex.genmodel.MojoModel;
import hex.genmodel.attributes.*;


public class XGBoostModelAttributes extends SharedTreeModelAttributes {

  public XGBoostModelAttributes(JsonObject modelJson, MojoModel model) {
    super(modelJson, model);
  }
}
