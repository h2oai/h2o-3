package hex.genmodel.attributes;

import com.google.gson.JsonObject;
import hex.ModelCategory;
import hex.genmodel.MojoModel;
import hex.genmodel.attributes.metrics.*;

import java.io.Serializable;

/**
 * Attributes of a MOJO model extracted from the MOJO itself.
 */
public class ModelAttributes implements Serializable {

  private final Table _modelSummary;
  private final Table _scoring_history;
  private final MojoModelMetrics _trainingMetrics;
  private final MojoModelMetrics _validation_metrics;
  private final MojoModelMetrics _cross_validation_metrics;
  private final Table _cross_validation_metrics_summary;

  public ModelAttributes(MojoModel model, final JsonObject modelJson) {
    _modelSummary = ModelJsonReader.readTable(modelJson, "output.model_summary");
    _scoring_history = ModelJsonReader.readTable(modelJson, "output.scoring_history");


    if (ModelJsonReader.elementExists(modelJson, "output.training_metrics")) {
      _trainingMetrics = determineModelMetricsType(model._category);
      ModelJsonReader.fillObject(_trainingMetrics, modelJson, "output.training_metrics");
    } else _trainingMetrics = null;

    if (ModelJsonReader.elementExists(modelJson, "output.validation_metrics")) {
      _validation_metrics = determineModelMetricsType(model._category);
      ModelJsonReader.fillObject(_validation_metrics, modelJson, "output.validation_metrics");
    } else _validation_metrics = null;

    if (ModelJsonReader.elementExists(modelJson, "output.cross_validation_metrics")) {
      _cross_validation_metrics_summary = ModelJsonReader.readTable(modelJson, "output.cross_validation_metrics_summary");
      _cross_validation_metrics = determineModelMetricsType(model._category);
      ModelJsonReader.fillObject(_cross_validation_metrics, modelJson, "output.cross_validation_metrics");
    } else {
      _cross_validation_metrics = null;
      _cross_validation_metrics_summary = null;
    }
  }

  private static MojoModelMetrics determineModelMetricsType(final ModelCategory modelCategory) {
    switch (modelCategory) {
      case Binomial:
        return new MojoModelMetricsBinomial();
      case Multinomial:
        return new MojoModelMetricsMultinomial();
      case Regression:
        return new MojoModelMetricsRegression();
      case AnomalyDetection:
        return new MojoModelMetricsAnomaly();
      case Unknown:
      case Ordinal:
      case Clustering:
      case AutoEncoder:
      case DimReduction:
      case WordEmbedding:
      case CoxPH:
      default:
        return new MojoModelMetrics(); // Basic model metrics if nothing else is available
    }
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

  /**
   * @return A {@link MojoModelMetrics} instance with training metrics. If available, otherwise null.
   */
  public MojoModelMetrics getTrainingMetrics() {
    return _trainingMetrics;
  }

  /**
   * @return A {@link MojoModelMetrics} instance with validation metrics. If available, otherwise null.
   */
  public MojoModelMetrics getValidationMetrics() {
    return _validation_metrics;
  }

  /**
   *
   * @return A {@link MojoModelMetrics} instance with cross-validation metrics. If available, otherwise null.
   */
  public MojoModelMetrics getCrossValidationMetrics() {
    return _cross_validation_metrics;
  }

  /**
   *
   * @return A {@link Table} instance with summary table of the cross-validation metrics. If available, otherwise null.
   */
  public Table getCrossValidationMetricsSummary() {
    return _cross_validation_metrics_summary;
  }
}
