package water.api;

import water.DKV;
import water.fvec.Frame;
import water.server.ServletUtils;
import water.util.FileUtils;
import water.util.Log;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;

/**
 */
public class DatasetServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    String uri = ServletUtils.getDecodedUri(request);
    try {
      String f_name = request.getParameter("frame_id");
      String hex_string = request.getParameter("hex_string");
      String escape_quotes_string = request.getParameter("escape_quotes");
      if (f_name == null) {
        throw new RuntimeException("Cannot find value for parameter 'frame_id'");
      }
      Frame dataset = DKV.getGet(f_name);
      Frame.CSVStreamParams parms = new Frame.CSVStreamParams()
          .setHexString(Boolean.parseBoolean(hex_string))
          .setEscapeQuotes(Boolean.parseBoolean(escape_quotes_string));
      InputStream is = dataset.toCSV(parms);
      response.setContentType("application/octet-stream");
      // todo: get this somehow from dataset
      response.setCharacterEncoding("UTF-8");
      // Clean up the file name
      int x = f_name.length() - 1;
      boolean dot = false;
      for (; x >= 0; x--)
        if (!Character.isLetterOrDigit(f_name.charAt(x)) && f_name.charAt(x) != '_')
          if (f_name.charAt(x) == '.' && !dot) dot = true;
          else break;
      String suggested_fname = f_name.substring(x + 1).replace(".hex", ".csv");
      if (!suggested_fname.endsWith(".csv"))
        suggested_fname = suggested_fname + ".csv";
      f_name = suggested_fname;
      response.addHeader("Content-Disposition", "attachment; filename=" + f_name);
      ServletUtils.setResponseStatus(response, HttpServletResponse.SC_OK);
      OutputStream os = null;
      try {
        os = response.getOutputStream();
        FileUtils.copyStream(is, os, 2048);
      } finally {
        if (os != null) {
          try {
            os.close();
          }
          catch (Exception e) {
            Log.err(e);
          }
        }
      }
    } catch (Exception e) {
      ServletUtils.sendErrorResponse(response, e, uri);
    } finally {
      ServletUtils.logRequest("GET", request, response);
    }
  }

}
