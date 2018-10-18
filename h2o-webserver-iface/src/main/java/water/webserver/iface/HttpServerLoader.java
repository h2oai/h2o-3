package water.webserver.iface;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Finds implementation of {@link HttpServerFacade} found on the classpath.
 * There must be exactly one present.
 */
public class HttpServerLoader {
  public static final HttpServerFacade INSTANCE;
  static {
    final ServiceLoader<HttpServerFacade> serviceLoader = ServiceLoader.load(HttpServerFacade.class);
    final Iterator<HttpServerFacade> iter = serviceLoader.iterator();
    if (! iter.hasNext()) {
      throw new IllegalStateException("No implementation of HttpServerFacade found on classpath");
    }
    INSTANCE = iter.next();
    if (iter.hasNext()) {
      final StringBuilder sb = new StringBuilder(INSTANCE.getClass().getName());
      while (iter.hasNext()) {
        sb.append(",");
        sb.append(iter.next().getClass().getName());
      }
      throw new IllegalStateException("Multiple implementations of HttpServerFacade found on classpath: " + sb);
    }
  }
}
