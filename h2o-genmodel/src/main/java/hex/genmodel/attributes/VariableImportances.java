package hex.genmodel.attributes;


import com.google.gson.JsonObject;
import hex.genmodel.attributes.parameters.KeyValue;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

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

    /**
     *
     * @param n how many variables is in the output. If n >= number of variables or n <= 0 then all variables are returned.
     * @return descending sorted array of String -> double.
     *          Where String is variable and double is relative importance of the variable.
     */
    public KeyValue[] topN(int n) {
        if (n <= 0 || n > _importances.length) {
            n = _importances.length;
        }
        KeyValue[] sortedImportances = new KeyValue[_importances.length];
        for (int i = 0; i < _importances.length; i++) {
            sortedImportances[i] = new KeyValue(_variables[i], _importances[i]);
        }
        Arrays.sort(sortedImportances, new Comparator<KeyValue>() {
            @Override
            public int compare(KeyValue o1, KeyValue o2) {
                return o1.getValue() > o2.getValue() ? -1 : 0;
            }
        });
        return Arrays.copyOfRange(sortedImportances, 0, n);
    }
}
