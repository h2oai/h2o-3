package hex.schemas;

import hex.aggregator.AggregatorModel;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;

public class AggregatorModelV99 extends ModelSchemaV3<AggregatorModel, AggregatorModelV99, AggregatorModel.AggregatorParameters, AggregatorV99.AggregatorParametersV99, AggregatorModel.AggregatorOutput, AggregatorModelV99.AggregatorModelOutputV99> {
  public static final class AggregatorModelOutputV99 extends ModelOutputSchemaV3<AggregatorModel.AggregatorOutput, AggregatorModelOutputV99> {
    @API(help = "Aggregated Frame of Exemplars")
    public KeyV3.FrameKeyV3 output_frame;

    @API(help ="Aggregated Frame mapping to the rows in the original data")
    public KeyV3.FrameKeyV3 mapping_frame;
  }

  // TODO: I think we can implement the following two in ModelSchemaV3, using reflection on the type parameters.
  public AggregatorV99.AggregatorParametersV99 createParametersSchema() { return new AggregatorV99.AggregatorParametersV99(); }
  public AggregatorModelOutputV99 createOutputSchema() { return new AggregatorModelOutputV99(); }

  // Version&Schema-specific filling into the impl
  @Override public AggregatorModel createImpl() {
    AggregatorModel.AggregatorParameters parms = parameters.createImpl();
    return new AggregatorModel( model_id.key(), parms, null );
  }
}
