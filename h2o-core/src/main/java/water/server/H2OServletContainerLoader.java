package water.server;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Finds implementation of {@link H2OServletContainerFacade} on the classpath.
 * There must be exactly one present.
 */
public class H2OServletContainerLoader {
  public static final H2OServletContainerFacade INSTANCE;
  static {
    final ServiceLoader<H2OServletContainerFacade> serviceLoader = ServiceLoader.load(H2OServletContainerFacade.class);
    final Iterator<H2OServletContainerFacade> iter = serviceLoader.iterator();
    if (! iter.hasNext()) {
      throw new IllegalStateException("No implementation of H2OServletContainerFacade found on classpath");
    }
    INSTANCE = iter.next();
    if (iter.hasNext()) {
      final StringBuilder sb = new StringBuilder(INSTANCE.getClass().getName());
      while (iter.hasNext()) {
        sb.append(",");
        sb.append(iter.next().getClass().getName());
      }
      throw new IllegalStateException("Multiple implementations of H2OServletContainerFacade found on classpath: " + sb);
    }
  }
}
