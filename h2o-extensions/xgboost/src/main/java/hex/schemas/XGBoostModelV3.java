package hex.schemas;

import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostOutput;
import water.api.API;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;
import water.api.schemas3.TwoDimTableV3;

public class XGBoostModelV3 extends ModelSchemaV3<
        XGBoostModel,
        XGBoostModelV3,
        XGBoostModel.XGBoostParameters,
        XGBoostV3.XGBoostParametersV3,
        XGBoostOutput,
        XGBoostModelV3.XGBoostModelOutputV3> {

  public static final class XGBoostModelOutputV3 extends ModelOutputSchemaV3<XGBoostOutput, XGBoostModelOutputV3> {
    @API(help="Variable Importances", direction=API.Direction.OUTPUT, level = API.Level.secondary)
    TwoDimTableV3 variable_importances;

    @API(help="XGBoost Native Parameters", direction=API.Direction.OUTPUT, level = API.Level.secondary)
    TwoDimTableV3 native_parameters;

    @API(help="Sparse", direction=API.Direction.OUTPUT, level = API.Level.secondary)
    boolean sparse;
  }

  public XGBoostV3.XGBoostParametersV3 createParametersSchema() { return new XGBoostV3.XGBoostParametersV3(); }
  public XGBoostModelOutputV3 createOutputSchema() { return new XGBoostModelOutputV3(); }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @Override public XGBoostModel createImpl() {
    XGBoostV3.XGBoostParametersV3 p = this.parameters;
    XGBoostModel.XGBoostParameters parms = p.createImpl();
    return new XGBoostModel(model_id.key(), parms, new XGBoostOutput(null), null, null);
  }
}
