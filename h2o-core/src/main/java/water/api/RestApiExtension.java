package water.api;

import water.AbstractH2OExtension;

import java.util.List;

/**
 * REST API registration endpoint.
 *
 * The interface should be overriden by clients which would like
 * to provide additional REST API endpoints.
 *
 * The registration is divided into two parts:
 *   - register Handlers to expose a new REST API endpoint (e.g., /3/ModelBuilder/XGBoost/)
 *   - register Schemas to provide a new definition of REST API input/output
 */
public interface RestApiExtension {

  /**
   * TODO: would be better to pass Jetty object to register `jetty.register(new ApiHandler())
   * @param relativeResourcePath
   *
   * @deprecated use {@link #registerEndPoints(RestApiContext)}
   */
  @Deprecated
  void register(String relativeResourcePath);

  /**
   *
   * @param context
   */
  void registerEndPoints(RestApiContext context);

  /**
   * 
   * @param context
   */
  void registerSchemas(RestApiContext context);

  /** Provide name of the REST API extension. */
  String getName();

  /** List of core extensions on which this rest api depends on */
  List<String> getRequiredCoreExtensions();
}