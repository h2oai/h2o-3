package water.api;

import water.H2O;
import water.Iced;
import water.util.MarkdownBuilder;

import java.lang.reflect.Method;
import java.util.Arrays;
import com.google.code.regexp.Pattern;

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
  public Class<? extends Handler> _handler_class;
  public Method _handler_method;
  public Method  _doc_method;
  // NOTE: Java 7 captures and lets you look up subpatterns by name but won't give you the list of names, so we need this redundant list:
  public String[] _path_params; // list of params we capture from the url pattern, e.g. for /17/MyComplexObj/(.*)/(.*)
  /* package */ HandlerFactory _handler_factory;
  public Handler _handler;

  public Route() { }

  public Route(String http_method,
               String url_pattern_raw,
               Pattern url_pattern,
               String summary,
               Class<? extends Handler> handler_class,
               Method handler_method,
               Method doc_method,
               String[] path_params,
               HandlerFactory handler_factory) {
    assert http_method != null && url_pattern != null && handler_class != null && handler_method != null && path_params != null;
    assert handler_factory != null : "handler_factory should be not null, caller has to pass it!";
    _http_method = http_method;
    _url_pattern_raw = url_pattern_raw;
    _url_pattern = url_pattern;
    _summary = summary;
    _handler_class = handler_class;
    _handler_method = handler_method;
    _doc_method = doc_method;
    _path_params = path_params;
    _handler_factory = handler_factory;
    try {
      _handler = _handler_factory.create(_handler_class);
    } catch (Exception e) {
      throw H2O.fail("Could not create handler", e);
    }
  }

  /**
   * Generate Markdown documentation for this Route.
   */
  public StringBuffer markdown(StringBuffer appendToMe) {
    MarkdownBuilder builder = new MarkdownBuilder();

    builder.comment("Preview with http://jbt.github.io/markdown-editor");
    builder.heading1(_http_method, _url_pattern_raw.replace("(?<", "{").replace(">.*)", "}"));
    builder.hline();
    builder.paragraph(_summary);

    // parameters and output tables
    try {
      builder.heading1("Input schema: ");
      {
        Class<? extends Schema> clz = Handler.getHandlerMethodInputSchema(_handler_method);
        Schema s = Schema.newInstance(clz);
        builder.append(s.markdown(null, true, false));
      }

      builder.heading1("Output schema: ");
      {
        Class<? extends Schema> clz = Handler.getHandlerMethodOutputSchema(_handler_method);

          Schema s = Schema.newInstance(clz);

          if (null == s)
            throw H2O.fail("Call to Schema.newInstance(clz) failed for class: " + clz);

          builder.append(s.markdown(null, false, true));
      }

      // TODO: render examples and other stuff, if it's passed in
    }
    catch (Exception e) {
      throw H2O.fail("Caught exception using reflection on handler method: " + _handler_method + ": " + e);
    }

    if (null != appendToMe)
      appendToMe.append(builder.stringBuffer());

    return builder.stringBuffer();
  }

  @Override
  public boolean equals(Object o) {
    if( this == o ) return true;
    if( !(o instanceof Route) ) return false;
    Route route = (Route) o;
    if( !_handler_class .equals(route._handler_class )) return false;
    if( !_handler_method.equals(route._handler_method)) return false;
    if( !_doc_method.equals(route._doc_method)) return false;
    if( !_http_method.equals(route._http_method)) return false;
    if( !_url_pattern_raw.equals(route._url_pattern_raw)) return false;
    if( !Arrays.equals(_path_params, route._path_params)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    long result = _http_method.hashCode();
    result = 31 * result + _url_pattern_raw.hashCode();
    result = 31 * result + _handler_class.hashCode();
    result = 31 * result + _handler_method.hashCode();
    result = 31 * result + _doc_method.hashCode();
    result = 31 * result + Arrays.hashCode(_path_params);
    return (int)result;
  }

  @Override
  public String toString() {
    return "Route{" +
            "_http_method='" + _http_method + '\'' +
            ", _url_pattern=" + _url_pattern_raw +
            ", _summary='" + _summary + '\'' +
            ", _handler_class=" + _handler_class +
            ", _handler_method=" + _handler_method +
            ", _input_schema=" + Handler.getHandlerMethodInputSchema(_handler_method) +
            ", _output_schema=" + Handler.getHandlerMethodOutputSchema(_handler_method) +
            ", _doc_method=" + _doc_method +
            ", _path_params=" + Arrays.toString(_path_params) +
            '}';
  }
} // Route
