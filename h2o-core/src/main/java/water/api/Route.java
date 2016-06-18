package water.api;

import java.lang.reflect.Method;
import java.util.Arrays;

import water.H2O;
import water.Iced;
import water.util.MarkdownBuilder;

/**
* Routing of an http request to a handler method, with path parameter parsing.
*/
final public class Route extends Iced {
  static final int MIN_VERSION = 1;

  // TODO: handlers are now stateless, so create a single instance and stash it here
  // TODO: all fields should be final!
  // TODO: remove no-args ctor, since it is not used
  public String  _http_method;
  public String _url;
  public String _summary;
  public String _api_name;
  public Class<? extends Handler> _handler_class;
  public Method _handler_method;
  // NOTE: Java 7 captures and lets you look up subpatterns by name but won't give you the list of names, so we need this redundant list:
  public String[] _path_params; // list of params we capture from the url pattern, e.g. for /17/MyComplexObj/(.*)/(.*)
  public Handler _handler;
  private RequestUri _uri;

  /** Handler factory configures a way how handler is instantiated.
   *
   * PLEASE: do not remove it even H2O is not using it. It is used by Sparkling Water, since
   * it needs to pass a Spark context to a new handler
   */
  final HandlerFactory _handler_factory;

  public Route() {
    _handler_factory = null;
  }

  public Route(RequestUri uri,
               String api_name,
               String summary,
               Class<? extends Handler> handler_class,
               String handler_method,
               HandlerFactory handler_factory) {
    assert uri != null && handler_class != null && handler_method != null;
    assert handler_factory != null : "handler_factory should not be null, caller has to pass it!";
    _uri = uri;
    _http_method = uri.getMethod();
    _url = uri.getUrl();
    _summary = summary;
    _api_name = api_name;
    _handler_class = handler_class;
    _handler_method = resolveMethod(handler_class, handler_method);
    _path_params = uri.getParamsList();
    _handler_factory = handler_factory;
    try {
      _handler = _handler_factory.create(_handler_class);
    } catch (Exception ie) {
      throw H2O.fail("failed to register handler " + handler_class.getSimpleName() + "." + handler_method, ie);
    }
  }

  public RequestUri getUri() { return _uri; }

  public int getVersion() { return _uri.getVersion(); }

  /**
   * Generate Markdown documentation for this Route.
   */
  public StringBuffer markdown(Schema sinput, Schema soutput) {
    MarkdownBuilder builder = new MarkdownBuilder();
    builder.comment("Preview with http://jbt.github.io/markdown-editor");
    builder.heading1(_http_method, _url);
    builder.hline();
    builder.paragraph(_summary);
    // parameters and output tables
    builder.heading1("Input schema: ");
    builder.append(sinput .markdown(true ,false));
    builder.heading1("Output schema: ");
    builder.append(soutput.markdown(false, true));
    return builder.stringBuffer();
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof Route)) return false;
    Route route = (Route) o;
    return _api_name.equals(route._api_name) &&
           _handler_class .equals(route._handler_class) &&
           _handler_method.equals(route._handler_method) &&
           _http_method.equals(route._http_method) &&
           _url.equals(route._url) &&
           Arrays.equals(_path_params, route._path_params);
  }

  @Override
  public int hashCode() {
    return _api_name.hashCode();
  }

  @Override
  public String toString() {
    return "Route{" +
            "_http_method='" + _http_method + '\'' +
            ", _url_pattern=" + _url +
            ", _summary='" + _summary + '\'' +
            ", _api_name='" + _api_name + "'" +
            ", _handler_class=" + _handler_class +
            ", _handler_method=" + _handler_method +
            ", _input_schema=" + Handler.getHandlerMethodInputSchema(_handler_method) +
            ", _output_schema=" + Handler.getHandlerMethodOutputSchema(_handler_method) +
            ", _path_params=" + Arrays.toString(_path_params) +
            '}';
  }

  /**
   * Search the provided class (and all its superclasses) for the requested method.
   * @param handler_class Class to be searched
   * @param handler_method Name of the method to look for. The method must have signature (int, Schema).
   * @return The callable Method object.
   */
  private static Method resolveMethod(Class<? extends Handler> handler_class, String handler_method) {
    for (Method method : handler_class.getMethods())
      if (method.getName().equals(handler_method)) {
        Class[] pt = method.getParameterTypes();
        if (pt != null && pt.length == 2 && pt[0] == Integer.TYPE && Schema.class.isAssignableFrom(pt[1]))
          return method;
      }
    throw H2O.fail("Failed to find handler method: " + handler_method + " in class: " + handler_class.getSimpleName());
  }

}
