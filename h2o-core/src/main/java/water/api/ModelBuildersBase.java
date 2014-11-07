package water.api;

import hex.ModelBuilder;
import hex.schemas.ModelBuilderSchema;
import water.api.ModelBuildersHandler.ModelBuilders;
import water.util.IcedHashMap;

import java.util.Map;

// Input fields
abstract class ModelBuildersBase<I extends ModelBuilders, S extends ModelBuildersBase<I, S>> extends Schema<I, ModelBuildersBase<I, S>> {
  @API(help="Algo of ModelBuilder of interest", json=false) // TODO: no validation yet, because right now fields are required if they have validation.
  String algo;;

  // Output fields
  @API(help="ModelBuilders", direction=API.Direction.OUTPUT)
  IcedHashMap<String, ModelBuilderSchema> model_builders;

  @Override public ModelBuilders fillImpl(ModelBuilders m) {
    m.algo = this.algo;

    if (null != model_builders) {
      m.model_builders = new IcedHashMap<>();

      int i = 0;
      for (Map.Entry<String, ModelBuilderSchema> entry : this.model_builders.entrySet()) {
        String algo = entry.getKey();
        ModelBuilderSchema model_builder = entry.getValue();
        m.model_builders.put(algo, (ModelBuilder)model_builder.createAndFillImpl());
      }
    }
    return m;
  }

  @Override public ModelBuildersBase fillFromImpl(ModelBuilders m) {
    this.algo = m.algo;

    if (null != m.model_builders) {
      this.model_builders = new IcedHashMap<>();

      int i = 0;
      for (Map.Entry<String, ModelBuilder> entry: m.model_builders.entrySet()) {
        ModelBuilder model_builder = entry.getValue();
        String algo = entry.getKey();
        this.model_builders.put(algo, model_builder.schema().fillFromImpl(entry.getValue()));
      }
    }
    return this;
  }
}
