package water.api;

/**
 * REST API registration interfaces.
 *
 * This is an abstraction layer between a rest server and registration
 * modules.
 */
public interface RestApiContext {

  Route registerEndpoint(String apiName,
                         String methodUri,
                         Class<? extends Handler> handlerClass,
                         String handlerMethod,
                         String summary);

  Route registerEndpoint(String apiName,
                         String httpMethod,
                         String url,
                         Class<? extends Handler> handlerClass,
                         String handlerMethod,
                         String summary,
                         HandlerFactory handlerFactory);


  Route registerEndpoint(String methodUri,
                         Class<? extends RestApiHandler> handlerClass);

  void registerSchema(Schema... schemas);
}
