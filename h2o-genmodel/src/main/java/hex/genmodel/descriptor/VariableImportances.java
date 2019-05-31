package hex.genmodel.descriptor;

import java.io.Serializable;

/**
 * Represents model's variables and their relative importances in the model.
 * The structure is model-independent.
 */
public class VariableImportances {

    public final String[] _variables;
    public final double[] _importances;

    public VariableImportances(String[] variableNames, double[] relativeImportances) {
        this._variables = variableNames;
        this._importances = relativeImportances;
    }
}
