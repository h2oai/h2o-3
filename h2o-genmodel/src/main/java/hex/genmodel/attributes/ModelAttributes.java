package hex.genmodel.attributes;

import com.google.gson.JsonObject;

public class ModelAttributes {

  private final Table _modelSummary;

  public ModelAttributes(final JsonObject modelJson) {
    _modelSummary = ModelJsonReader.extractTableFromJson(modelJson, "output.model_summary");
  }

  /**
   * Model summary might vary not only per model, but per each version of the model.
   *
   * @return A {@link Table} with summary information about the underlying model.
   */
  public Table getModelSummary() {
    return _modelSummary;
  }
}
