package hex.genmodel.attributes;

import com.google.gson.JsonObject;
import hex.genmodel.MojoModel;
import hex.genmodel.algos.tree.SharedTreeMojoModel;

import java.util.Arrays;

public class SharedTreeModelAttributes extends ModelAttributes {

  private final VariableImportances _variableImportances;
  private final MojoModelMetrics _trainingMetrics;

  public <M extends SharedTreeMojoModel> SharedTreeModelAttributes(JsonObject modelJson, M model) {
    super(modelJson);
    _variableImportances = extractVariableImportances(modelJson, model);
    final MojoModelMetrics mojoModelMetrics = new MojoModelMetrics();
    ModelJsonReader.fillObject(mojoModelMetrics, modelJson, "output.training_metrics");
    _trainingMetrics = mojoModelMetrics;
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
  
  public MojoModelMetrics getTrainingMetrics(){
    return _trainingMetrics;
  }
}
