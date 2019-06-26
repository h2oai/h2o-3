package hex.genmodel.attributes;


/**
 * Represents model's variables and their relative importances in the model.
 * The structure is model-independent.
 */
public class VariableImportances {

    // Index is the shared key to both. A record under index {i} in variables is the name of the variable 
    public final String[] _variables;
    public final double[] _importances;

    public VariableImportances(String[] variableNames, double[] relativeImportances) {
        this._variables = variableNames;
        this._importances = relativeImportances;
    }
}
