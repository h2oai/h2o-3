package water.server;

import water.webserver.iface.H2OWebsocketServlet;

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

  public synchronized LinkedHashMap<String, Class<? extends HttpServlet>> getAlwaysEnabledServlets() {
    return StreamSupport
            .stream(_loader.spliterator(), false)
            .sorted(Comparator.comparing(ServletProvider::priority).reversed())
            .flatMap(provider -> provider.servlets().stream())
            .filter(servletMeta -> servletMeta.isAlwaysEnabled())
            .collect(Collectors.toMap(ServletMeta::getContextPath, ServletMeta::getServletClass,
                    (val1, val2) -> val2, // Latest always wins
                    LinkedHashMap::new));
  }

  public synchronized LinkedHashMap<String, Class<? extends HttpServlet>> getAllServlets() {
    return StreamSupport
            .stream(_loader.spliterator(), false)
            .sorted(Comparator.comparing(ServletProvider::priority).reversed())
            .flatMap(provider -> provider.servlets().stream())
            .collect(Collectors.toMap(ServletMeta::getContextPath, ServletMeta::getServletClass,
                    (val1, val2) -> val2, // Latest always wins
                    LinkedHashMap::new));
  }

  public synchronized LinkedHashMap<String, Class<? extends H2OWebsocketServlet>> getAllWebsockets() {
    return StreamSupport
        .stream(_loader.spliterator(), false)
        .sorted(Comparator.comparing(ServletProvider::priority).reversed())
        .flatMap(provider -> provider.websockets().stream())
        .collect(Collectors.toMap(WebsocketMeta::getContextPath, WebsocketMeta::getHandlerClass,
            (val1, val2) -> val2, // Latest always wins
            LinkedHashMap::new));
  }
}
