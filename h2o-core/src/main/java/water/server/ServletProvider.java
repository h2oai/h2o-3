package water.server;

import java.util.Collections;
import java.util.List;

public interface ServletProvider {

  /**
   * Provides a collection of Servlets that should be registered.
   * @return a map of context path to a Servlet class
   */
  List<ServletMeta> servlets();
  
  default List<WebsocketMeta> websockets() {
    return Collections.emptyList();
  }

  /**
   * Provider priority, providers with higher priority will be used first. H2O Core Provider will be used last and
   * will override any mappings previously registered with the same context path. It is thus not possible to override
   * the H2O Core Servlets.
   * 
   * A typical application will have just one custom provider and users don't need to worry about setting a priority.
   * If your use case requires multiple Servlet Providers, please make sure your priorities are set properly and or
   * the context paths do not overlap.
   * 
   * @return a positive integer number (0 priority is reserved for H2O Core servlets)
   */
  default int priority() {
    return 1;
  }
  
}
