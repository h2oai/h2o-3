package water.api;

import hex.ModelBuilder;
import water.H2O;

/**
 * Abstract base class for registering Rest API for algorithms
 */
public abstract class AlgoAbstractRegister extends AbstractRegister {

  /**
   * Register algorithm common REST interface.
   *
   * @param mbProto  prototype instance of algorithm model builder
   * @param version  registration version
   */
  protected final void registerModelBuilder(RestApiContext context, ModelBuilder mbProto, int version) {
    if (H2O.ARGS.features_level.compareTo(mbProto.builderVisibility()) > 0) {
      return; // Skip endpoint registration
    }
    String base = mbProto.getClass().getSimpleName();
    String lbase = mbProto.getName();
    Class<? extends water.api.Handler> handlerClass = water.api.ModelBuilderHandler.class;
    Class<? extends water.api.Handler> bulkHandlerClass = water.api.BulkModelBuilderHandler.class;

    // This is common model builder handler
    context.registerEndpoint(
        "train_" + lbase,
        "POST /" + version + "/ModelBuilders/" + lbase,
        handlerClass,
        "train",
        "Train a " + base + " model."
    );

    context.registerEndpoint(
            "bulk_train_" + lbase,
            "POST /" + version + "/BulkModelBuilders/" + lbase,
            bulkHandlerClass,
            "bulk_train",
            "Validate a set of " + base + " model builder parameters."
    );

    context.registerEndpoint(
        "validate_" + lbase,
        "POST /" + version + "/ModelBuilders/" + lbase + "/parameters",
        handlerClass,
        "validate_parameters",
        "Validate a set of " + base + " model builder parameters."
    );

    context.registerEndpoint(
        "grid_search_" + lbase,
        "POST /99/Grid/" + lbase,
        GridSearchHandler.class,
        "train",
        "Run grid search for " + base + " model."
    );
  }

}
