package water.api.schemas3;

import hex.schemas.ModelBuilderSchema;
import water.Iced;
import water.api.API;

public class ModelBuildersV3 extends SchemaV3<Iced, ModelBuildersV3> {

  // TODO: no validation yet, because right now fields are required if they have validation.
  @API(help = "Algo of ModelBuilder of interest", json = false)
  public String algo;

  // Output fields
  @API(help = "ModelBuilders", direction = API.Direction.OUTPUT)
  public ModelBuilderSchema.IcedHashMapStringModelBuilderSchema model_builders;
}
