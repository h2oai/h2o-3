package water.api;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelMojoWriter;
import hex.schemas.ModelBuilderSchema;
import water.H2O;
import water.Iced;
import water.api.schemas3.ModelBuildersV3;
import water.api.schemas3.SchemaV3;
import water.api.schemas4.ListRequestV4;
import water.api.schemas4.ModelInfoV4;
import water.api.schemas4.ModelsInfoV4;
import water.util.ReflectionUtils;

import java.lang.reflect.Method;

class ModelBuildersHandler extends Handler {

  /** Return all the modelbuilders. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ModelBuildersV3 list(int version, ModelBuildersV3 m) {
    m.model_builders = new ModelBuilderSchema.IcedHashMapStringModelBuilderSchema();
    for( String algo : ModelBuilder.algos() ) {
      ModelBuilderSchema schema = makeSchema(algo, version);
      if (schema != null) {
        m.model_builders.put(algo.toLowerCase(), schema);
      }
    }
    return m;
  }

  /** Return a single modelbuilder. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ModelBuildersV3 fetch(int version, ModelBuildersV3 m) {
    m.model_builders = new ModelBuilderSchema.IcedHashMapStringModelBuilderSchema();
    ModelBuilderSchema schema = makeSchema(m.algo, version);
    if (schema != null)
      m.model_builders.put(m.algo.toLowerCase(), schema);
    return m;
  }

  public static class ModelIdV3 extends SchemaV3<Iced, ModelIdV3> {
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

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ModelsInfoV4 modelsInfo(int version, ListRequestV4 m) {
    String[] algos = ModelBuilder.algos();
    ModelInfoV4[] infos = new ModelInfoV4[algos.length];
    ModelsInfoV4 res = new ModelsInfoV4();
    for (int i = 0; i < algos.length; i++) {
      ModelBuilder builder = ModelBuilder.make(algos[i], null, null);
      infos[i] = new ModelInfoV4();
      infos[i].algo = algos[i];
      infos[i].maturity = builder.builderVisibility() == ModelBuilder.BuilderVisibility.Stable? "stable" :
          builder.builderVisibility() == ModelBuilder.BuilderVisibility.Beta? "beta" : "alpha";
      infos[i].have_mojo = builder.haveMojo();
      infos[i].have_pojo = builder.havePojo();
      infos[i].mojo_version = infos[i].have_mojo? detectMojoVersion(builder) : null;
    }
    res.models = infos;
    return res;
  }

  private ModelBuilderSchema makeSchema(String algo, int version) {
    ModelBuilder builder = ModelBuilder.make(algo, null, null);
    if (ModelBuilder.schemaDirectory(builder.getName()) == null) return null; // this builder disabled schema
    return (ModelBuilderSchema)SchemaServer.schema(version, builder).fillFromImpl(builder);  // use ModelBuilderHandlerUtils.makeBuilderSchema instead?
  }

  private String detectMojoVersion(ModelBuilder builder) {
    Class<? extends Model> modelClass = ReflectionUtils.findActualClassParameter(builder.getClass(), 0);
    try {
      Method getMojoMethod = modelClass.getDeclaredMethod("getMojo");
      Class<?> retClass = getMojoMethod.getReturnType();
      if (retClass == ModelMojoWriter.class || !ModelMojoWriter.class.isAssignableFrom(retClass))
        throw new RuntimeException("Method getMojo() in " + modelClass + " must return the concrete implementation " +
            "of the ModelMojoWriter class. The return type is declared as " + retClass);
      try {
        ModelMojoWriter mmw = (ModelMojoWriter) retClass.newInstance();
        return mmw.mojoVersion();
      } catch (InstantiationException e) {
        throw getMissingCtorException(retClass, e);
      } catch (IllegalAccessException e) {
        throw getMissingCtorException(retClass, e);
      }
    } catch (NoSuchMethodException e) {
      throw new RuntimeException("Model class " + modelClass + " is expected to have method getMojo();");
    }
  }

  private RuntimeException getMissingCtorException(Class<?> retClass, Exception e) {
    return new RuntimeException("MojoWriter class " + retClass + " must define a no-arg constructor.\n" + e);
  }
}


