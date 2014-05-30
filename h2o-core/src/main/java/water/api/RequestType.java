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
  private static final RequestType[] _values = values();

  /** Returns the request type of a given URL. 
   *  Missing type defaults to HTML.
   *  Unknown type defaults to JSON. */
  static RequestType requestType(String url) {
    int i = url.indexOf('.');
    if(  i == -1 ) return html; // Default for no extension
    String s = url.substring(i+1);
    // valueOf(s) throws IAE if there is no match.
    for( RequestType t : _values )
      if( s.equals(t.name()) ) return t;
    return json;                // None of the above; use json
  }

  /** Returns the name of the request, that is the request url without the
   *  request suffix.  E.g. converts "/GBM.html/crunk" into "/GBM/crunk" */
  String requestName(String url) {
    String s = "."+toString();
    int i = url.indexOf(s);
    if( i== -1 ) return url;    // No, or default, type
    return url.substring(0,i)+url.substring(i+s.length());
  }
}
