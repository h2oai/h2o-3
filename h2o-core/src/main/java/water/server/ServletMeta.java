package water.server;

import javax.servlet.http.HttpServlet;

/**
 * Describes how to register a Servlet in H2O Server
 */
public class ServletMeta {

  private final String _contextPath;
  private Class<? extends HttpServlet> _servletClass;

  public ServletMeta(String contextPath, Class<? extends HttpServlet> servletClass) {
    _contextPath = contextPath;
    _servletClass = servletClass;
  }

  public String getContextPath() {
    return _contextPath;
  }

  public Class<? extends HttpServlet> getServletClass() {
    return _servletClass;
  }

}
