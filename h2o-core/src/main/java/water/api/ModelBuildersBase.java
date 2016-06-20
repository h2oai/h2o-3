package water.api;

import hex.schemas.ModelBuilderSchema;
import water.Iced;
import water.api.schemas3.SchemaV3;

// Input fields
public class ModelBuildersBase<I extends Iced, S extends ModelBuildersBase<I, S>> extends SchemaV3<I, S> {
  @API(help = "Algo of ModelBuilder of interest", json = false)
  public // TODO: no validation yet, because right now fields are required if they have validation.
  String algo;

  // Output fields
  @API(help = "ModelBuilders", direction = API.Direction.OUTPUT)
  public ModelBuilderSchema.IcedHashMapStringModelBuilderSchema model_builders;
}
