package water.api;

import water.Key;
import water.fvec.UploadFileVec;
import water.server.ServletUtils;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;

/**
 */
public class PostFileServlet extends HttpServlet {

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    String uri = ServletUtils.getDecodedUri(request);

    try {
      String destination_frame = request.getParameter("destination_frame");
      if (destination_frame == null) {
        destination_frame = "upload" + Key.rand();
      }

      //
      // Here is an example of how to upload a file from the command line.
      //
      // curl -v -F "file=@allyears2k_headers.zip" "http://localhost:54321/3/PostFile.bin?destination_frame=a.zip"
      //
      // JSON Payload returned is:
      //     { "destination_frame": "key_name", "total_bytes": nnn }
      //
      InputStream is = ServletUtils.extractPartInputStream(request, response);
      if (is == null) {
        return;
      }

      UploadFileVec.ReadPutStats stats = new UploadFileVec.ReadPutStats();
      UploadFileVec.readPut(destination_frame, is, stats);
      String responsePayload = "{ " +
          "\"destination_frame\": \"" + destination_frame + "\", " +
          "\"total_bytes\": " + stats.total_bytes + " " +
          "}\n";
      response.setContentType("application/json");
      response.getWriter().write(responsePayload);
    } catch (Exception e) {
      ServletUtils.sendErrorResponse(response, e, uri);
    } finally {
      ServletUtils.logRequest("POST", request, response);
    }
  }
}
