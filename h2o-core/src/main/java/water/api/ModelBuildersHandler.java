package water.api;

import hex.ModelBuilder;
import water.H2O;
import water.Iced;
import water.Model;
import water.util.IcedHashMap;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;

class ModelBuildersHandler extends Handler<ModelBuildersHandler.ModelBuilders, ModelBuildersBase> {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  /** Class which contains the internal representation of the modelbuilders list and params. */
  protected static final class ModelBuilders extends Iced {
    String algo;
    IcedHashMap<String, ModelBuilder> model_builders;
  }

  /** Create a ModelBuilder instance of the correct class given the algo name. */
  private ModelBuilder createModelBuilder(String algo) {
    ModelBuilder modelBuilder = null;

    try {
      Class<? extends ModelBuilder> clz = ModelBuilder.getModelBuilder(algo);
      if (! (clz.getGenericSuperclass() instanceof ParameterizedType)) {
        throw H2O.fail("Class is not parameterized as expected: " + clz);
      }

      Type[] handler_type_parms = ((ParameterizedType)(clz.getGenericSuperclass())).getActualTypeArguments();
      // [0] is the Model type; [1] is the Model.Parameters type; [2] is the Model.Output type.
      Class<? extends Model.Parameters> pclz = (Class<? extends Model.Parameters>)handler_type_parms[1];

      modelBuilder = clz.getDeclaredConstructor(new Class[] { (Class)handler_type_parms[1] }).newInstance(pclz.newInstance());
    }
    catch (Exception e) {
      throw H2O.fail("Exception when trying to instantiate ModelBuilder for: " + algo + ": " + e);
    }

    return modelBuilder;
  }

  /** Return all the modelbuilders. */
  protected Schema list(int version, ModelBuilders m) {
    Map<String, Class<? extends ModelBuilder>> builders = ModelBuilder.getModelBuilders();
    m.model_builders = new IcedHashMap<>();

    for (Map.Entry<String, Class<? extends ModelBuilder>> entry : builders.entrySet()) {
        String algo = entry.getKey();
        m.model_builders.put(algo, createModelBuilder(algo));
    }
    return this.schema(version).fillFromImpl(m);
  }

  /** Return a single modelbuilder. */
  protected Schema fetch(int version, ModelBuilders m) {
    m.model_builders = new IcedHashMap<>();
    m.model_builders.put(m.algo, createModelBuilder(m.algo));
    return this.schema(version).fillFromImpl(m);
  }

  /** Create a model by launching a ModelBuilder algo. */
  protected Schema train(int version, ModelBuilders m) {
    ModelBuilder builder = createModelBuilder(m.algo);

    // TODO: WRONG: needs to return a schema containing the job
    return this.schema(version);
  }

  @Override protected ModelBuildersBase schema(int version) {
    switch (version) {
    case 2:   return new ModelBuildersV2();
    default:  throw H2O.fail("Bad version for ModelBuilders schema: " + version);
    }
  }

  // Need to stub this because it's required by H2OCountedCompleter:
  @Override public void compute2() { throw H2O.fail(); }
}


