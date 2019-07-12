package hex.genmodel.attributes;

import com.google.gson.JsonObject;

public class ModelAttributes {

  private final Table _modelSummary;
  private final Table _scoring_history;

  public ModelAttributes(final JsonObject modelJson) {
    _modelSummary = ModelJsonReader.readTable(modelJson, "output.model_summary");
    _scoring_history = ModelJsonReader.readTable(modelJson, "output.scoring_history");
  }

  /**
   * Model summary might vary not only per model, but per each version of the model.
   *
   * @return A {@link Table} with summary information about the underlying model.
   */
  public Table getModelSummary() {
    return _modelSummary;
  }

  /**
   * Retrieves model's scoring history.
   * @return A {@link Table} with model's scoring history, if existing. Otherwise null.
   */
  public Table getScoringHistory(){
    return _scoring_history;
  }
}
