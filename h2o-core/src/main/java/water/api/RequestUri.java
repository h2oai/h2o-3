package water.api;

import com.google.code.regexp.Pattern;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 *
 */
public class RequestUri {

  private static Pattern version_pattern = Pattern.compile("^/(?i)(\\d+|EXPERIMENTAL|LATEST)/(.*)");
  private static Set<String> http_methods = new HashSet<>(Arrays.asList("HEAD", "GET", "POST", "DELETE"));

  private String method;
  private String url;
  private String[] path;
  private boolean is_api_url;

  public RequestUri(String request_method, String request_url) throws MalformedURLException {
    if (!http_methods.contains(request_method))
      throw new MalformedURLException("Bad HTTP method: " + request_method);

    method = request_method;
    url = request_url;
    is_api_url = version_pattern.matcher(request_url).matches();
    path = null;
  }

  public String getUrl() { return url; }
  public boolean isApiUrl() { return is_api_url; }

  public String getMethod() { return method; }
  public boolean isGetMethod() { return method.equals("GET"); }
  public boolean isPostMethod() { return method.equals("POST"); }
  public boolean isHeadMethod() { return method.equals("HEAD"); }

  public String[] getPath() {
    computePathIfNeeded();
    return path;
  }

  public String[] getParamsList() {
    computePathIfNeeded();
    ArrayList<String> params_list = new ArrayList<>();
    for (int i = 2; i < path.length; i++)
      if (path[i].startsWith("{") && path[i].endsWith("}"))
        params_list.add(path[i].substring(1, path[i].length()-1));
    return params_list.toArray(new String[params_list.size()]);
  }

  public int getVersion() {
    computePathIfNeeded();
    String ver = path[path.length - 1];
    return ver.isEmpty()? 0 : Integer.parseInt(ver);
  }

  public String toString() {
    return method + " " + url;
  }

  /**
   * Convert the provided HTTP_method/URL pair into a "path" suitable for lookups in the RouteTree. This is mostly
   * equivalent to url.split("/"), with a few caveats:
   *   - if the url contains "special" version (LATEST/EXPERIMENTAL), it will be replaced with its numeric value;
   *   - the order of url chunks is modified: the version is always moved to the end, its place taken by http_method;
   * Examples:
   *   "GET", "/3/Models/{model_id}"  =>  ["", "GET", "Models", "{model_id}", "3"]
   *   "GET", "/"  =>  ["", "GET", ""]
   * First chunk is always "" because that is the root of the RouteTree.
   */
  private void computePathIfNeeded() {
    if (path == null) {
      // This will make sure path array has one extra element in the end, where we will store the version string.
      // Pass -1 because otherwise split() removes any trailing empty strings.
      path = (url + "/").split("/", -1);

      assert path[0].isEmpty() && path.length >= 3;

      String ver = path[1].toUpperCase();
      if (ver.equals("EXPERIMENTAL")) ver = ((Integer) SchemaServer.getExperimentalVersion()).toString();
      if (ver.equals("LATEST")) ver = ((Integer) SchemaServer.getLatestOrHighestSupportedVersion()).toString();

      path[1] = method;
      path[path.length - 1] = ver;
    }
  }

}

