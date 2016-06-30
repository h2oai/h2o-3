package water.api;

import water.H2O;
import water.JettyHTTPD;
import water.init.NodePersistentStorage;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 */
public class NpsBinServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    String uri = JettyHTTPD.getDecodedUri(request);
    try {
      Pattern p = Pattern.compile(".*/NodePersistentStorage.bin/([^/]+)/([^/]+)");
      Matcher m = p.matcher(uri);
      boolean b = m.matches();
      if (!b) {
        JettyHTTPD.setResponseStatus(response, HttpServletResponse.SC_BAD_REQUEST);
        response.getWriter().write("Improperly formatted URI");
        return;
      }

      String categoryName = m.group(1);
      String keyName = m.group(2);
      NodePersistentStorage nps = H2O.getNPS();
      AtomicLong length = new AtomicLong();
      InputStream is = nps.get(categoryName, keyName, length);
      if (length.get() > (long) Integer.MAX_VALUE) {
        throw new Exception("NPS value size exceeds Integer.MAX_VALUE");
      }
      response.setContentType("application/octet-stream");
      response.setContentLength((int) length.get());
      response.addHeader("Content-Disposition", "attachment; filename=" + keyName + ".flow");
      JettyHTTPD.setResponseStatus(response, HttpServletResponse.SC_OK);
      OutputStream os = response.getOutputStream();
      water.util.FileUtils.copyStream(is, os, 2048);
    } catch (Exception e) {
      JettyHTTPD.sendErrorResponse(response, e, uri);
    } finally {
      JettyHTTPD.logRequest("GET", request, response);
    }
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    String uri = JettyHTTPD.getDecodedUri(request);
    try {
      Pattern p = Pattern.compile(".*NodePersistentStorage.bin/([^/]+)/([^/]+)");
      Matcher m = p.matcher(uri);
      boolean b = m.matches();
      if (!b) {
        JettyHTTPD.setResponseStatus(response, HttpServletResponse.SC_BAD_REQUEST);
        response.getWriter().write("Improperly formatted URI");
        return;
      }

      String categoryName = m.group(1);
      String keyName = m.group(2);

      InputStream is = JettyHTTPD.extractPartInputStream(request, response);
      if (is == null) {
        return;
      }

      H2O.getNPS().put(categoryName, keyName, is);
      long length = H2O.getNPS().get_length(categoryName, keyName);
      String responsePayload = "{ " +
          "\"category\" : " + "\"" + categoryName + "\", " +
          "\"name\" : " + "\"" + keyName + "\", " +
          "\"total_bytes\" : " + length + " " +
          "}\n";
      response.setContentType("application/json");
      response.getWriter().write(responsePayload);
    } catch (Exception e) {
      JettyHTTPD.sendErrorResponse(response, e, uri);
    } finally {
      JettyHTTPD.logRequest("POST", request, response);
    }
  }
}
