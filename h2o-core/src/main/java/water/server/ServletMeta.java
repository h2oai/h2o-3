package water.server;

import javax.servlet.http.HttpServlet;
import java.util.Objects;

/**
 * Describes how to register a Servlet in H2O Server
 */
public class ServletMeta {

  private String _contextPath;
  private Class<? extends HttpServlet> _servletClass;
  private boolean _alwaysEnabled;

  /**
   * Constructs a new instance of {@link ServletMeta} with the alwaysEnabled functionality turned off.
   *
   * @param contextPath Context path the underlying servlet handles
   * @param servletClass Specific implementation of the {@link HttpServlet} to handle the context path
   */
  public ServletMeta(final String contextPath, final Class<? extends HttpServlet> servletClass) {
    Objects.requireNonNull(contextPath);
    Objects.requireNonNull(servletClass);
    _contextPath = contextPath;
    _servletClass = servletClass;
    _alwaysEnabled = false;
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
  
  public static class Builder {
    private final ServletMeta servletMeta;

    /**
     * Constructs a new instance of {@link ServletMeta.Builder} with basic required parameters
     *
     * @param contextPath Context path the underlying servlet handles
     * @param servletClass Specific implementation of the {@link HttpServlet} to handle the context path
     */
    public Builder(final String contextPath, final Class<? extends HttpServlet> servletClass) {
      Objects.requireNonNull(contextPath);
      Objects.requireNonNull(servletClass);
      servletMeta = new ServletMeta(contextPath, servletClass);
    }

    /**
     * @return The underlying ServletMeta object. Returns reference to the same object if called multiple times. Never null.
     */
    public ServletMeta build(){
      return servletMeta;
    }

    /**
     * 
     * @param alwaysEnabled
     * @return This builder
     */
    public ServletMeta.Builder withAlwaysEnabled(final boolean alwaysEnabled){
      servletMeta._alwaysEnabled = alwaysEnabled;
      return this;
    }

  }

}
