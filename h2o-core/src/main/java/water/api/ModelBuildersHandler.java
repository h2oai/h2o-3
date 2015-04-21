package water.api;

import hex.ModelBuilder;
import hex.schemas.ModelBuilderSchema;

import java.util.Map;

class ModelBuildersHandler extends Handler {
  /** Return all the modelbuilders. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ModelBuildersV3 list(int version, ModelBuildersV3 m) {
    Map<String, Class<? extends ModelBuilder>> builders = ModelBuilder.getModelBuilders();
    m.model_builders = new ModelBuilderSchema.IcedHashMapStringModelBuilderSchema();

    for (Map.Entry<String, Class<? extends ModelBuilder>> entry : builders.entrySet()) {
        String algo = entry.getKey();
        ModelBuilder builder = ModelBuilder.createModelBuilder(algo);
        m.model_builders.put(algo, (ModelBuilderSchema)Schema.schema(version, builder).fillFromImpl(builder));
    }
    return m;
  }

  /** Return a single modelbuilder. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ModelBuildersV3 fetch(int version, ModelBuildersV3 m) {
    m.model_builders = new ModelBuilderSchema.IcedHashMapStringModelBuilderSchema();
    ModelBuilder builder = ModelBuilder.createModelBuilder(m.algo);
    m.model_builders.put(m.algo, (ModelBuilderSchema)Schema.schema(version, builder).fillFromImpl(builder));
    return m;
  }
}


