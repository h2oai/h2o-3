package water.webserver;

import org.junit.Ignore;
import water.server.ServletMeta;
import water.server.ServletProvider;
import water.server.ServletUtils;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

@Ignore
public class EchoServletProvider implements ServletProvider {

  @Override
  public List<ServletMeta> servlets() {
    return Collections.singletonList(new ServletMeta("/99/Echo", EchoServlet.class));
  }

  public static class EchoServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
      String message = request.getQueryString();
      String uri = ServletUtils.getDecodedUri(request);
      try {
        response.setContentType("text/plain");
        ServletUtils.setResponseStatus(response, HttpServletResponse.SC_OK);
        try (OutputStream os = response.getOutputStream()){
          os.write(message.getBytes());
        }
        ServletUtils.setResponseStatus(response, HttpServletResponse.SC_OK);
      } catch (Exception e) {
        ServletUtils.sendErrorResponse(response, e, uri);
      } finally {
        ServletUtils.logRequest("GET", request, response);
      }
    }
  }

}
