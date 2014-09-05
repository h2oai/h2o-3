package water.api;

import hex.ModelBuilder;
import water.H2O;
import water.Iced;
import water.util.IcedHashMap;

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
  public Schema list(int version, ModelBuilders m) {
    Map<String, Class<? extends ModelBuilder>> builders = ModelBuilder.getModelBuilders();
    m.model_builders = new IcedHashMap<>();

    for (Map.Entry<String, Class<? extends ModelBuilder>> entry : builders.entrySet()) {
        String algo = entry.getKey();
        m.model_builders.put(algo, ModelBuilder.createModelBuilder(algo));
    }
    return this.schema(version).fillFromImpl(m);
  }

  /** Return a single modelbuilder. */
  public Schema fetch(int version, ModelBuilders m) {
    m.model_builders = new IcedHashMap<>();
    m.model_builders.put(m.algo, ModelBuilder.createModelBuilder(m.algo));
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


