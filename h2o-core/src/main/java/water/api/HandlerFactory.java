package water.api;

/**
 * Handler factory supports different strategies to
 * create an instance of handler class for given registered route.
 */
public interface HandlerFactory {

  /** Shared default factory to create handler by using no-arg ctor
   * and reflection. */
  HandlerFactory DEFAULT = new HandlerFactory() {

    @Override
    public Handler create(Class<? extends Handler> handlerClz) throws Exception {
      return handlerClz.newInstance();
    }
  };

  Handler create(Class<? extends Handler> handler) throws Exception;
}
