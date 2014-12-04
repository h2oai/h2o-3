package water.api;

import hex.schemas.ModelBuilderSchema;
import water.Iced;
import water.util.IcedHashMap;

// Input fields
abstract class ModelBuildersBase<S extends ModelBuildersBase<S>> extends Schema<Iced, ModelBuildersBase<S>> {
  @API(help="Algo of ModelBuilder of interest", json=false) // TODO: no validation yet, because right now fields are required if they have validation.
  String algo;;

  // Output fields
  @API(help="ModelBuilders", direction=API.Direction.OUTPUT)
  IcedHashMap<String, ModelBuilderSchema> model_builders;
}
