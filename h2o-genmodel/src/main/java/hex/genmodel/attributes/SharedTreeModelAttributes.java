package hex.genmodel.attributes;

import com.google.gson.JsonObject;
import hex.genmodel.MojoModel;
import hex.genmodel.algos.tree.SharedTreeMojoModel;

import java.util.Arrays;

public class SharedTreeModelAttributes extends ModelAttributes {

  private final VariableImportances _variableImportances;

  public <M extends SharedTreeMojoModel> SharedTreeModelAttributes(JsonObject modelJson, M model) {
    super(modelJson);
    _variableImportances = extractVariableImportances(modelJson, model);
  }

  private VariableImportances extractVariableImportances(final JsonObject modelJson, final MojoModel model) {
    final Table table = ModelJsonReader.extractTableFromJson(modelJson, "output.variable_importances");
    if (table == null) return null;
    final double[] relativeVarimps = new double[table.rows()];
    final int column = table.findColumnIndex("Relative Importance");
    if (column == -1) return null;
    for (int i = 0; i < table.rows(); i++) {
      relativeVarimps[i] = (double) table.getCell(column, i);
    }

    return new VariableImportances(Arrays.copyOf(model._names, model.nfeatures()), relativeVarimps);
  }

  /**
   * @return A {@link VariableImportances} instance with variable importances for each feature used.
   */
  public VariableImportances getVariableImportances() {
    return _variableImportances;
  }
}
