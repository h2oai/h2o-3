package water.api;

import water.H2O;
import water.Iced;
import water.util.MarkdownBuilder;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
* Routing of an http request to a handler method, with path parameter parsing.
*/
final class Route extends Iced {
  // TODO: handlers are now stateless, so create a single instance and stash it here
  public final String  _http_method;
  public final Pattern _url_pattern;
  public final String _summary;
  public final Class<? extends Handler> _handler_class;
  public final Method _handler_method;
  public final Method  _doc_method;
  // NOTE: Java 7 captures and lets you look up subpatterns by name but won't give you the list of names, so we need this redundant list:
  public final String[] _path_params; // list of params we capture from the url pattern, e.g. for /17/MyComplexObj/(.*)/(.*)

  public Route(String http_method, Pattern url_pattern, String summary, Class<? extends Handler> handler_class, Method handler_method, Method doc_method, String[] path_params) {
    assert http_method != null && url_pattern != null && handler_class != null && handler_method != null && path_params != null;
    _http_method = http_method;
    _url_pattern = url_pattern;
    _summary = summary;
    _handler_class = handler_class;
    _handler_method = handler_method;
    _doc_method = doc_method;
    _path_params = path_params;
  }

  /**
   * Generate Markdown documentation for this Route.
   */
  public StringBuffer markdown(StringBuffer appendToMe) {
    MarkdownBuilder builder = new MarkdownBuilder();

    builder.comment("Preview with http://jbt.github.io/markdown-editor");
    builder.heading1(_http_method, _url_pattern.toString().replace("(?<", "{").replace(">.*)", "}"));
    builder.hline();
    builder.paragraph(_summary);

    // parameters and outputs tables
    try {
      Handler h = _handler_class.newInstance();
      Schema s = h.schema(h.min_ver()); // TODO: iterate over each version!
      SchemaMetadata meta = new SchemaMetadata(s);

      boolean first; // don't print the table at all if there are no rows

      first = true;
      builder.heading2("parameters");
      for (SchemaMetadata.FieldMetadata field_meta : meta.fields.values()) {
        if (field_meta.direction == API.Direction.INPUT || field_meta.direction == API.Direction.INOUT) {
          if (first) {
            builder.tableHeader("name", "required?", "level", "type", "default", "description", "values");
            first = false;
          }
          builder.tableRow(field_meta.name, String.valueOf(field_meta.required), field_meta.level.name(), field_meta.type, field_meta.value, field_meta.help, (field_meta.values == null || field_meta.values.length == 0 ? "" : Arrays.toString(field_meta.values)));
        }
      }
      if (first)
        builder.paragraph("(none)");

      first = true;
      builder.heading2("output");
      for (SchemaMetadata.FieldMetadata field_meta : meta.fields.values()) {
        if (field_meta.direction == API.Direction.OUTPUT || field_meta.direction == API.Direction.INOUT) {
          if (first) {
            builder.tableHeader("name", "type", "default", "description", "values");
            first = false;
          }
          builder.tableRow(field_meta.name, field_meta.type, field_meta.value, field_meta.help, (field_meta.values == null || field_meta.values.length == 0 ? "" : Arrays.toString(field_meta.values)));
        }
      }
      if (first)
        builder.paragraph("(none)");

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
    if( !_http_method   .equals(route._http_method)) return false;
    if( !_url_pattern   .equals(route._url_pattern   )) return false;
    if( !Arrays.equals(_path_params, route._path_params)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    long result = _http_method.hashCode();
    result = 31 * result + _url_pattern.hashCode();
    result = 31 * result + _handler_class.hashCode();
    result = 31 * result + _handler_method.hashCode();
    result = 31 * result + _doc_method.hashCode();
    result = 31 * result + Arrays.hashCode(_path_params);
    return (int)result;
  }
} // Route
