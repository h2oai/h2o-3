package water.api;

import hex.ModelBuilder;
import hex.schemas.ModelBuilderSchema;
import water.util.IcedHashMap;

import java.util.Map;

class ModelBuildersHandler extends Handler {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  /** Return all the modelbuilders. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ModelBuildersV2 list(int version, ModelBuildersV2 m) {
    Map<String, Class<? extends ModelBuilder>> builders = ModelBuilder.getModelBuilders();
    m.model_builders = new IcedHashMap<>();

    for (Map.Entry<String, Class<? extends ModelBuilder>> entry : builders.entrySet()) {
        String algo = entry.getKey();
        ModelBuilder builder = ModelBuilder.createModelBuilder(algo);
        m.model_builders.put(algo, (ModelBuilderSchema)Schema.schema(2, builder).fillFromImpl(builder));
    }
    return m;
  }

  /** Return a single modelbuilder. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ModelBuildersV2 fetch(int version, ModelBuildersV2 m) {
    m.model_builders = new IcedHashMap<>();
    ModelBuilder builder = ModelBuilder.createModelBuilder(m.algo);
    m.model_builders.put(m.algo, (ModelBuilderSchema)Schema.schema(2, builder).fillFromImpl(builder));
    return m;
  }
}


