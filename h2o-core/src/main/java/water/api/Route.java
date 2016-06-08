package water.api;

import com.google.code.regexp.Pattern;

import java.lang.reflect.Method;
import java.util.Arrays;

import water.H2O;
import water.Iced;
import water.util.MarkdownBuilder;

/**
* Routing of an http request to a handler method, with path parameter parsing.
*/
final class Route extends Iced {
  static final int MIN_VERSION = 1;

  // TODO: handlers are now stateless, so create a single instance and stash it here
  // TODO: all fields should be final!
  // TODO: remove no-args ctor, since it is not used
  public String  _http_method;
  public String _url_pattern_raw;
  public Pattern _url_pattern;
  public String _summary;
  public String _api_name;
  public Class<? extends Handler> _handler_class;
  public Method _handler_method;
  // NOTE: Java 7 captures and lets you look up subpatterns by name but won't give you the list of names, so we need this redundant list:
  public String[] _path_params; // list of params we capture from the url pattern, e.g. for /17/MyComplexObj/(.*)/(.*)
  public Handler _handler;

  /** Handler factory configures a way how handler is instantiated.
   *
   * PLEASE: do not remove it even H2O is not using it. It is used by Sparkling Water, since
   * it needs to pass a Spark context to a new handler
   */
  final HandlerFactory _handler_factory;

  public Route() {
    _handler_factory = null;
  }

  public Route(String http_method,
               String url_pattern_raw,
               Pattern url_pattern,
               String summary,
               String api_name,
               Class<? extends Handler> handler_class,
               Method handler_method,
               String[] path_params,
               HandlerFactory handler_factory) {
    assert http_method != null && url_pattern != null && handler_class != null && handler_method != null && path_params != null;
    assert handler_factory != null : "handler_factory should be not null, caller has to pass it!";
    _http_method = http_method;
    _url_pattern_raw = url_pattern_raw;
    _url_pattern = url_pattern;
    _summary = summary;
    _api_name = api_name;
    _handler_class = handler_class;
    _handler_method = handler_method;
    _path_params = path_params;
    _handler_factory = handler_factory;
    try {
      _handler = _handler_factory.create(_handler_class);
    } catch (Exception ie) {
      throw H2O.fail("failed to register handler " + handler_class.getSimpleName() + "." + handler_method.getName(), ie);
    }
  }

  /**
   * Generate Markdown documentation for this Route.
   */
  public StringBuffer markdown(Schema sinput, Schema soutput) {
    MarkdownBuilder builder = new MarkdownBuilder();
    builder.comment("Preview with http://jbt.github.io/markdown-editor");
    builder.heading1(_http_method, _url_pattern_raw);
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
           _url_pattern_raw.equals(route._url_pattern_raw) &&
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
            ", _url_pattern=" + _url_pattern_raw +
            ", _summary='" + _summary + '\'' +
            ", _api_name='" + _api_name + "'" +
            ", _handler_class=" + _handler_class +
            ", _handler_method=" + _handler_method +
            ", _input_schema=" + Handler.getHandlerMethodInputSchema(_handler_method) +
            ", _output_schema=" + Handler.getHandlerMethodOutputSchema(_handler_method) +
            ", _path_params=" + Arrays.toString(_path_params) +
            '}';
  }
} // Route
