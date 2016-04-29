package hex.schemas;

import hex.aggregator.AggregatorModel;
import water.api.API;
import water.api.KeyV3;
import water.api.ModelOutputSchema;
import water.api.ModelSchema;

public class AggregatorModelV3 extends ModelSchema<AggregatorModel, AggregatorModelV3, AggregatorModel.AggregatorParameters, AggregatorV3.AggregatorParametersV3, AggregatorModel.AggregatorOutput, AggregatorModelV3.AggregatorModelOutputV3> {
  public static final class AggregatorModelOutputV3 extends ModelOutputSchema<AggregatorModel.AggregatorOutput, AggregatorModelOutputV3> {
    @API(help = "Aggregated Frame of Exemplars")
    public KeyV3.FrameKeyV3 output_frame;
  }

  // TODO: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public AggregatorV3.AggregatorParametersV3 createParametersSchema() { return new AggregatorV3.AggregatorParametersV3(); }
  public AggregatorModelOutputV3 createOutputSchema() { return new AggregatorModelOutputV3(); }

  // Version&Schema-specific filling into the impl
  @Override public AggregatorModel createImpl() {
    AggregatorModel.AggregatorParameters parms = parameters.createImpl();
    return new AggregatorModel( model_id.key(), parms, null );
  }
}
