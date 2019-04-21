package water.api;

import org.apache.commons.io.IOUtils;
import water.DKV;
import water.Key;
import water.Value;
import water.server.ServletUtils;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;

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
    String uri = ServletUtils.getDecodedUri(request);
    
    try {
      String destKey = paramDestinationKey(request, response);
      Boolean overwrite = paramOverwrite(request, response, true);
      if (!validate(destKey, overwrite, response)) {
        return;
      }

      InputStream is = ServletUtils.extractPartInputStream(request, response);
      if (is == null) {
        return;
      }

      //
      // Note: this is necessary since we are saving data into local K/V.
      //
      Key key = Key.make(destKey);
      int bytesStored = -1;
      if (DKV.get(key) == null || overwrite) {
        byte[] ba = IOUtils.toByteArray(is);
        // Save the binary data into K/V
        DKV.put(key, new Value(key, ba));
        bytesStored = ba.length;
      }
      
      String responsePayload = "{ " +
          "\"destination_key\": \"" + destKey + "\", " +
          "\"total_bytes\": " + bytesStored + " " +
          "}\n";
      response.setContentType("application/json");
      response.getWriter().write(responsePayload);
    } catch (Exception e) {
      ServletUtils.sendErrorResponse(response, e, uri);
    } finally {
      ServletUtils.logRequest("POST", request, response);
    }
  }

  private String paramDestinationKey(HttpServletRequest request, HttpServletResponse response) {
    String keyName = request.getParameter("destination_key");
    return keyName != null ? keyName : "func_" + Key.rand();
  }

  private Boolean paramOverwrite(HttpServletRequest request, HttpServletResponse response, boolean defaultValue) {
    String val = request.getParameter("overwrite");
    return val != null ? Boolean.valueOf(val) : defaultValue;
  }

  private boolean validate(String destKey, Boolean overwrite, HttpServletResponse response) throws IOException {
    if (destKey == null) {
      ServletUtils.sendResponseError(response, HttpServletResponse.SC_BAD_REQUEST, "The field 'destination_frame` is compulsory!");
      return false;
    }
    return true;
  }
}
