package water.api;

/** Request type.
 *
 * Requests can have multiple types.  Basic types include the plain json type
 * in which the result is returned as a JSON object, a html type that acts as
 * the webpage, or the help type that displays the extended help for the
 * request.
 */
public enum RequestType {
  json , // json type request, a result is a JSON structure
  html , // webpage request
  help , // should display the help on the given request
  query, // Displays the query for the argument in html mode
  png  , // image, e.g. plot
  txt  , // text, e.g. a script
  java , // java program
  xml  , // xml request
    ;

  /** Returns the request type of a given URL. JSON request type is the default
   *  type when the extension from the URL cannot be determined.  */
  static RequestType requestType(String requestUrl) {
    int i = requestUrl.lastIndexOf('.');
    assert i != -1;
    return valueOf(requestUrl.substring(i+1));
  }

  /** Returns the name of the request, that is the request url without the
   *  request suffix. */
  String requestName(String requestUrl) {
    String s = "."+toString();
    return requestUrl.endsWith(s) ? requestUrl.substring(0, requestUrl.length()-s.length()) : requestUrl;
  }
}
