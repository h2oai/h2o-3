package hex.schemas;

import hex.deeplearning.DeepLearningModel;
import water.Key;
import water.api.API;
import water.api.ModelOutputSchema;
import water.api.ModelSchema;
import water.util.TwoDimTable;

public class DeepLearningModelV2 extends ModelSchema<DeepLearningModel, DeepLearningModel.DeepLearningParameters, DeepLearningModel.DeepLearningOutput, DeepLearningModelV2> {

  public static final class DeepLearningModelOutputV2 extends ModelOutputSchema<DeepLearningModel.DeepLearningOutput, DeepLearningModelOutputV2> {
    @API(help="Scoring information")
    DeepLearningModel.Errors errors;
    @API(help="Model summary")
    TwoDimTable modelSummary;
  } // DeepLearningModelOutputV2

  // TOOD: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public DeepLearningV2.DeepLearningParametersV2 createParametersSchema() { return new DeepLearningV2.DeepLearningParametersV2(); }
  public DeepLearningModelOutputV2 createOutputSchema() { return new DeepLearningModelOutputV2(); }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @Override public DeepLearningModel createImpl() {
    DeepLearningV2.DeepLearningParametersV2 p = ((DeepLearningV2.DeepLearningParametersV2)this.parameters);
    DeepLearningModel.DeepLearningParameters parms = p.createImpl();
    return new DeepLearningModel(Key.make() /*dest*/, parms, new DeepLearningModel.DeepLearningOutput(null), null, null);
  }
}
