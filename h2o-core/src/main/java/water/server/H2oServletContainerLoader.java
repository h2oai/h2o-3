package water.server;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Finds implementation of {@link H2oServletContainerFacade} found on the classpath.
 * There must be exactly one present.
 */
public class H2oServletContainerLoader {
  public static final H2oServletContainerFacade INSTANCE;
  static {
    final ServiceLoader<H2oServletContainerFacade> serviceLoader = ServiceLoader.load(H2oServletContainerFacade.class);
    final Iterator<H2oServletContainerFacade> iter = serviceLoader.iterator();
    if (! iter.hasNext()) {
      throw new IllegalStateException("No implementation of H2oServletContainerFacade found on classpath");
    }
    INSTANCE = iter.next();
    if (iter.hasNext()) {
      final StringBuilder sb = new StringBuilder(INSTANCE.getClass().getName());
      while (iter.hasNext()) {
        sb.append(",");
        sb.append(iter.next().getClass().getName());
      }
      throw new IllegalStateException("Multiple implementations of H2oServletContainerFacade found on classpath: " + sb);
    }
  }
}
