package hex.schemas;

import hex.deeplearning.DeepLearningModel;
import water.Key;
import water.api.*;

public class DeepLearningModelV2 extends ModelSchema<DeepLearningModel, DeepLearningModelV2, DeepLearningModel.DeepLearningParameters, DeepLearningV2.DeepLearningParametersV2, DeepLearningModel.DeepLearningModelOutput, DeepLearningModelV2.DeepLearningModelOutputV2> {

  public static final class DeepLearningModelOutputV2 extends ModelOutputSchema<DeepLearningModel.DeepLearningModelOutput, DeepLearningModelOutputV2> {
    @API(help="Scoring information")
    DeepLearningScoringV2 errors;

    @API(help="Model summary")
    TwoDimTableV1 modelSummary;

    @API(help="Whether the model is an autoencoder")
    boolean autoencoder;

    @API(help="Scoring history")
    TwoDimTableV1 scoringHistory;

    @API(help="Training data model metrics")
    ModelMetricsBase trainMetrics;

    @API(help="Validation data model metrics")
    ModelMetricsBase validMetrics;

    @API(help="Variable Importances")
    TwoDimTableV1 variableImportances;

    @API(help="Training time (seconds)")
    double run_time;
  } // DeepLearningModelOutputV2

  // TODO: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public DeepLearningV2.DeepLearningParametersV2 createParametersSchema() { return new DeepLearningV2.DeepLearningParametersV2(); }
  public DeepLearningModelOutputV2 createOutputSchema() { return new DeepLearningModelOutputV2(); }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @Override public DeepLearningModel createImpl() {
    DeepLearningV2.DeepLearningParametersV2 p = ((DeepLearningV2.DeepLearningParametersV2)this.parameters);
    DeepLearningModel.DeepLearningParameters parms = p.createImpl();
    return new DeepLearningModel(Key.make() /*dest*/, parms, new DeepLearningModel.DeepLearningModelOutput(null), null, null);
  }
}
