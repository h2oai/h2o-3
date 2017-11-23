package water.api;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Response;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import water.DKV;
import water.JettyHTTPD;
import water.Key;
import water.Value;
import water.fvec.UploadFileVec;

/**
 * Upload any value to K/V store.
 *
 * Example
 * ```
 * curl -v -F "file=@my.jar" "http://localhost:54321/3/PutKey.bin?destination_key=my.jar"
 * ```
 */
public class PutKeyServlet extends HttpServlet {

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    String uri = JettyHTTPD.getDecodedUri(request);
    
    try {
      String destKey = paramDestinationKey(request, response);
      Boolean overwrite = paramOverwrite(request, response);
      if (!validate(destKey, overwrite, response)) {
        return;
      }

      InputStream is = JettyHTTPD.extractPartInputStream(request, response);
      if (is == null) {
        return;
      }

      //
      // Note: this is necessary since we are saving data into local K/V.
      //
      byte[] ba = IOUtils.toByteArray(is);
      // Save the binary data into K/V
      Key key = Key.make(destKey);
      DKV.put(key, new Value(key, ba));
      
      String responsePayload = "{ " +
          "\"destination_key\": \"" + destKey + "\", " +
          "\"total_bytes\": " + ba.length + " " +
          "}\n";
      response.setContentType("application/json");
      response.getWriter().write(responsePayload);
    } catch (Exception e) {
      JettyHTTPD.sendErrorResponse(response, e, uri);
    } finally {
      JettyHTTPD.logRequest("POST", request, response);
    }
  }

  private String paramDestinationKey(HttpServletRequest request, HttpServletResponse response) {
    String keyName = request.getParameter("destination_key");
    return keyName != null ? keyName : "func_" + Key.rand();
  }

  private Boolean paramOverwrite(HttpServletRequest request, HttpServletResponse response) {
    String val = request.getParameter("overwrite");
    return val != null ? Boolean.valueOf(val) : null;
  }

  private boolean validate(String destKey, Boolean overwrite, HttpServletResponse response) throws IOException {
    if (destKey == null) {
      JettyHTTPD.sendResponseError(response, Response.SC_BAD_REQUEST, "The field 'destination_frame` is compulsory!");
      return false;
    }
    return true;
  }
}
