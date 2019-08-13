package hex.genmodel.attributes;

import com.google.gson.JsonObject;
import hex.genmodel.algos.tree.SharedTreeMojoModel;


public class SharedTreeModelAttributes extends ModelAttributes {

  private final VariableImportances _variableImportances;

  public <M extends SharedTreeMojoModel> SharedTreeModelAttributes(JsonObject modelJson, M model) {
    super(model, modelJson);
    _variableImportances = extractVariableImportances(modelJson);

  }


  private VariableImportances extractVariableImportances(final JsonObject modelJson) {
    final Table table = ModelJsonReader.readTable(modelJson, "output.variable_importances");
    if (table == null) return null;
    final double[] relativeVarimps = new double[table.rows()];
    final String[] varNames = new String[table.rows()];
    final int varImportanceCol = table.findColumnIndex("Relative Importance");
    final int varNameCol = table.findColumnIndex("Variable");
    if (varImportanceCol == -1) return null;
    if (varNameCol == -1) return null;
    for (int i = 0; i < table.rows(); i++) {
      relativeVarimps[i] = (double) table.getCell(varImportanceCol, i);
      varNames[i] = (String) table.getCell(varNameCol, i);
    }

    return new VariableImportances(varNames, relativeVarimps);
  }

  /**
   * @return A {@link VariableImportances} instance with variable importances for each feature used.
   */
  public VariableImportances getVariableImportances() {
    return _variableImportances;
  }
  
}
