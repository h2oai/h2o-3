package water.server;

import javax.servlet.http.HttpServlet;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class ServletService {

  public static final ServletService INSTANCE = new ServletService();

  private final ServiceLoader<ServletProvider> _loader;

  private ServletService() {
    _loader = ServiceLoader.load(ServletProvider.class);
  }

  /**
   * Locates all available non-core primitives of the Rapid language.
   * @return list of Rapid primitives
   */
  public synchronized LinkedHashMap<String, Class<? extends HttpServlet>> getAllServlets() {
    return StreamSupport
            .stream(_loader.spliterator(), false)
            .sorted(Comparator.comparing(ServletProvider::priority).reversed())
            .flatMap(provider -> provider.servlets().stream())
            .collect(Collectors.toMap(ServletMeta::getContextPath, ServletMeta::getServletClass,
                    (val1, val2) -> val2, // Latest always wins
                    LinkedHashMap::new));
  }
}
