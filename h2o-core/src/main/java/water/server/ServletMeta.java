package water.server;

import javax.servlet.http.HttpServlet;

/**
 * Describes how to register a Servlet in H2O Server
 */
public class ServletMeta {

  private final String _contextPath;
  private final Class<? extends HttpServlet> _servletClass;
  private final boolean _alwaysEnabled;


  /**
   * Constructs a new instance of {@link ServletMeta} with the alwaysEnabled functionality turned off.
   *
   * @param contextPath Context path the underlying servlet handles
   * @param servletClass Specific implementation of the {@link HttpServlet} to handle the context path
   */
  public ServletMeta(final String contextPath, final Class<? extends HttpServlet> servletClass) {
    this(contextPath, servletClass, false);
  }

  /**
   * Constructs a new instance of {@link ServletMeta} with the alwaysEnabled functionality either turned on or off.
   * An always enabled servlet is guaranteed to be active on every node, even if the rest of the API is shut down
   * on a particular node. This is useful for vital, per-node APIs. Typically used by non user-facing functionality.
   * 
   * @param contextPath Context path the underlying servlet handles
   * @param servletClass Specific implementation of the {@link HttpServlet} to handle the context path
   * @param alwaysEnabled Set to true if this servlet should remain active even the API is turned off on a particular node.
   */
  public ServletMeta(final String contextPath, final Class<? extends HttpServlet> servletClass, final boolean alwaysEnabled) {
    _contextPath = contextPath;
    _servletClass = servletClass;
    _alwaysEnabled = alwaysEnabled;
  }

  public String getContextPath() {
    return _contextPath;
  }

  public Class<? extends HttpServlet> getServletClass() {
    return _servletClass;
  }
  
  public boolean isAlwaysEnabled(){
      return _alwaysEnabled;
  }

}
