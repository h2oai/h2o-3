package hex.genmodel.attributes;

import com.google.gson.JsonObject;
import hex.genmodel.MojoModel;
import hex.genmodel.attributes.parameters.IVariableImportancesHolder;


public class SharedTreeModelAttributes extends ModelAttributes implements IVariableImportancesHolder {

  private final VariableImportances _variableImportances;

  public SharedTreeModelAttributes(JsonObject modelJson, MojoModel model) {
    super(model, modelJson);
    _variableImportances = VariableImportances.extractFromJson(modelJson);

  }

  /**
   * @return A {@link VariableImportances} instance with variable importances for each feature used.
   */
  public VariableImportances getVariableImportances() {
    return _variableImportances;
  }
  
}
