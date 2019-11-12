package water.webserver.iface;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;

/**
 * Exposes part of H2O functionality for the purposes of HTTP server adapter.
 * Contains also logic for handling authentication and other functionality, so that it can be easily shared among various server implementations.
 */
public interface H2OHttpView {

  /**
   * @return configuration related to HTTP server
   */
  H2OHttpConfig getConfig();

  /**
   * @return map of servlets with their context paths
   */
  LinkedHashMap<String, Class<? extends HttpServlet>> getServlets();

  /**
   * @return custom authentication extensions if any
   */
  Collection<RequestAuthExtension> getAuthExtensions();

  boolean authenticationHandler(HttpServletRequest request, HttpServletResponse response) throws IOException;

  void gateHandler(HttpServletRequest request, HttpServletResponse response);

  boolean loginHandler(String target, HttpServletRequest request, HttpServletResponse response) throws IOException;

  boolean proxyLoginHandler(String target, HttpServletRequest request, HttpServletResponse response) throws IOException;
}
