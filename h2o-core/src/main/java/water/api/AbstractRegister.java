package water.api;

import java.util.ServiceLoader;
import hex.ModelBuilder;
import water.H2O;

public abstract class AbstractRegister implements RestApiExtension {

  @Override
  public void registerEndPoints(RestApiContext context) {
  }

  @Override
  public void registerSchemas(RestApiContext context) {
    assert context != null : "Context needs to be passed!";
    ServiceLoader<Schema> schemaLoader = ServiceLoader.load(Schema.class);
    for (Schema schema : schemaLoader) {
      context.registerSchema(schema);
    }
  }

  @Override
  public String getName() {
    return this.getClass().getName();
  }

  /**
   * Register algorithm common REST interface.
   *
   * @param mbProto  prototype instance of algorithm model builder
   * @param version  registration version
   */
  protected final void registerModelBuilder(ModelBuilder mbProto, int version) {
    String base = mbProto.getClass().getSimpleName();
    String lbase = base.toLowerCase();
    // This is common model builder handler
    Class<? extends water.api.Handler> handlerClass = water.api.ModelBuilderHandler.class;

    H2O.register("POST /" + version + "/ModelBuilders/" + lbase, handlerClass, "train",
                 "train_" + lbase,
                 "Train a " + base + " model.");

    H2O.register("POST /"+version+"/ModelBuilders/"+lbase+"/parameters", handlerClass, "validate_parameters",
                 "validate_" + lbase,
                 "Validate a set of " + base + " model builder parameters.");

    // Grid search is experimental feature
    H2O.register("POST /99/Grid/"+lbase, GridSearchHandler.class, "train",
                 "grid_search_" + lbase,
                 "Run grid search for "+base+" model.");
  }
}
