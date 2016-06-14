package water.api;

import hex.ModelBuilder;
import hex.schemas.ModelBuilderSchema;
import water.H2O;
import water.Iced;

class ModelBuildersHandler extends Handler {

  /** Return all the modelbuilders. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ModelBuildersV3 list(int version, ModelBuildersV3 m) {
    m.model_builders = new ModelBuilderSchema.IcedHashMapStringModelBuilderSchema();
    for( String algo : ModelBuilder.algos() ) {
      ModelBuilder builder = ModelBuilder.make(algo, null, null);
      m.model_builders.put(algo.toLowerCase(), (ModelBuilderSchema)Schema.schema(version, builder).fillFromImpl(builder));
    }
    return m;
  }

  /** Return a single modelbuilder. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ModelBuildersV3 fetch(int version, ModelBuildersV3 m) {
    m.model_builders = new ModelBuilderSchema.IcedHashMapStringModelBuilderSchema();
    ModelBuilder builder = ModelBuilder.make(m.algo, null, null);
    m.model_builders.put(m.algo.toLowerCase(), (ModelBuilderSchema)Schema.schema(version, builder).fillFromImpl(builder));
    return m;
  }

  public static class ModelIdV3 extends SchemaV3<Iced, ModelIdV3>{
    @API(help="Model ID", direction = API.Direction.OUTPUT)
    String model_id;
  }

  /** Calculate next unique model_id. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ModelIdV3 calcModelId(int version, ModelBuildersV3 m) {
    m.model_builders = new ModelBuilderSchema.IcedHashMapStringModelBuilderSchema();
    String model_id = H2O.calcNextUniqueModelId(m.algo);
    ModelIdV3 mm = new ModelIdV3();
    mm.model_id = model_id;
    return mm;
  }
}


