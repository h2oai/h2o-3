package hex.genmodel.attributes;


import com.google.gson.JsonObject;

import java.io.Serializable;

/**
 * Represents model's variables and their relative importances in the model.
 * The structure is model-independent.
 */
public class VariableImportances implements Serializable {

    // Index is the shared key to both. A record under index {i} in variables is the name of the variable 
    public final String[] _variables;
    public final double[] _importances;

    public VariableImportances(String[] variableNames, double[] relativeImportances) {
        _variables = variableNames;
        _importances = relativeImportances;
    }
    
    protected static VariableImportances extractFromJson(final JsonObject modelJson) {
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
}
