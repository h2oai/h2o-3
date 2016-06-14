package water.api;

import hex.schemas.ModelBuilderSchema;
import water.Iced;

// Input fields
public class ModelBuildersBase<I extends Iced, S extends ModelBuildersBase<I, S>> extends SchemaV3<I, S> {
  @API(help="Algo of ModelBuilder of interest", json=false) // TODO: no validation yet, because right now fields are required if they have validation.
  String algo;

  // Output fields
  @API(help="ModelBuilders", direction=API.Direction.OUTPUT)
  ModelBuilderSchema.IcedHashMapStringModelBuilderSchema model_builders;
}
