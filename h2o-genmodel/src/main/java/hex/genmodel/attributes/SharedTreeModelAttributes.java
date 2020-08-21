package hex.genmodel.attributes;

import com.google.gson.JsonObject;
import hex.genmodel.MojoModel;


public class SharedTreeModelAttributes extends ModelAttributes {

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
