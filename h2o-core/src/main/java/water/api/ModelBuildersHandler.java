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

  /** Return all the modelbuilders. */
  protected Schema list(int version, ModelBuilders m) {
    Map<String, Class<? extends ModelBuilder>> builders = ModelBuilder.getModelBuilders();
    m.model_builders = new IcedHashMap<>();

    int i = 0;
    for (Map.Entry<String, Class<? extends ModelBuilder>> entry : builders.entrySet()) {
      try {
        Class<? extends ModelBuilder> clz = entry.getValue();
        String algo = entry.getKey();

        if (! (clz.getGenericSuperclass() instanceof ParameterizedType)) {
          throw H2O.fail("Class is not parameterized as expected: " + clz);
        }

        Type[] handler_type_parms = ((ParameterizedType)(clz.getGenericSuperclass())).getActualTypeArguments();
        // [0] is the Model type; [1] is the Model.Parameters type; [2] is the Model.Output type.
        Class<? extends Model.Parameters> pclz = (Class<? extends Model.Parameters>)handler_type_parms[1];

        ModelBuilder modelbuilder = clz.getDeclaredConstructor(new Class[] { (Class)handler_type_parms[1] }).newInstance(pclz.newInstance());
        m.model_builders.put(algo, modelbuilder);
      }
      catch (Exception e) {
        throw H2O.fail("Exception when trying to instantiate ModelBuilder for: " + entry.getKey() + ": " + e);
      }
    }
    return this.schema(version).fillFromImpl(m);
  }

  /** Return a single modelbuilder. */
  protected Schema fetch(int version, ModelBuilders m) {
    try {
      Class<? extends ModelBuilder> clz = ModelBuilder.getModelBuilder(m.algo);
      if (! (clz.getGenericSuperclass() instanceof ParameterizedType)) {
        throw H2O.fail("Class is not parameterized as expected: " + clz);
      }

      Type[] handler_type_parms = ((ParameterizedType)(clz.getGenericSuperclass())).getActualTypeArguments();
      // [0] is the Model type; [1] is the Model.Parameters type; [2] is the Model.Output type.
      Class<? extends Model.Parameters> pclz = (Class<? extends Model.Parameters>)handler_type_parms[1];

      ModelBuilder modelbuilder = clz.getDeclaredConstructor(new Class[] { (Class)handler_type_parms[1] }).newInstance(pclz.newInstance());

      m.model_builders = new IcedHashMap<>();
      m.model_builders.put(m.algo, modelbuilder);
    }
    catch (Exception e) {
      throw H2O.fail("Exception when trying to instantiate ModelBuilder for: " + m.algo + ": " + e);
    }
    return this.schema(version).fillFromImpl(m);
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


