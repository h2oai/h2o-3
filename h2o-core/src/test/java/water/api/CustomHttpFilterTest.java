package water.api;


import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.BeforeClass;
import org.junit.Test;
import water.H2O;
import water.JettyHTTPD;
import water.TestUtil;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class CustomHttpFilterTest extends TestUtil {
  @BeforeClass static public void setup() {
    stall_till_cloudsize(1);
    // h2o-core/.., register the web bits (so we don't get errs below)
    String relativeResourcePath = System.getProperty("user.dir")+ "/..";
    H2O.registerResourceRoot(new File(relativeResourcePath + File.separator + "h2o-web/src/main/resources/www"));
    H2O.registerResourceRoot(new File(relativeResourcePath + File.separator + "h2o-core/src/main/resources/www"));
    H2O.startServingRestApi();  // calls jetty.acceptRequests
  }

  @Test public void testNoLog() throws Exception {
    final Logger _logger = Logger.getLogger("water.default");
    _logger.addAppender(new AppenderSkeleton() {
      @Override protected void append(LoggingEvent event) {
        if(event.getRenderedMessage().contains("GET") ) throw new RuntimeException("All GETs should be filtered");
      }
      @Override public void close() { _logger.removeAppender(this); }
      @Override public boolean requiresLayout() {return false;}
    });

    // let's filter out all GETs
    RequestServer.setFilters(RequestServer.defaultFilter(), new RequestServer.HttpLogFilter() {
      @Override public boolean filter(RequestUri uri, Properties header, Properties parms) {
        String[] path = uri.getPath();
        return path[1].equals("GET");
      }
    });

    // mock up a "GET /flow/index.html" call
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(request.getServletPath()).thenReturn("/flow/index.html");
    when(request.getRequestURI()).thenReturn("/flow/index.html");

    // mock up the headers
    // define the headers you want to be returned
    HashMap<String, String> headers = new HashMap<>();
    headers.put("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/55.0.2883.95 Safari/537.36");
    headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
    headers.put("Cookie", "_yummy");
    headers.put("Accept-Encoding", "gzip, deflate, sdch");
    headers.put("Host", H2O.getIpPortString());
    headers.put("Upgrade-Insecure-Requests", "1");
    headers.put("Accept-Language", "en-US,en;q=0.8");
    headers.put("Connection", "keep-alive");

    // create an Enumeration over the header keys
    final Iterator<String> iterator = headers.keySet().iterator();
    Enumeration headerNames = new Enumeration<String>() {
      @Override
      public boolean hasMoreElements() {
        return iterator.hasNext();
      }

      @Override
      public String nextElement() {
        return iterator.next();
      }
    };
    when(request.getHeaderNames()).thenReturn(headerNames);
    when(request.getHeader("User-Agent")).thenReturn(headers.get("User-Agent"));
    when(request.getHeader("Accept")).thenReturn(headers.get("Accept"));
    when(request.getHeader("Cookie")).thenReturn(headers.get("Cookie"));
    when(request.getHeader("Accept-Encoding")).thenReturn(headers.get("Accept-Encoding"));
    when(request.getHeader("Host")).thenReturn(headers.get("Host"));
    when(request.getHeader("Upgrade-Insecure-Requests")).thenReturn(headers.get("Upgrade-Insecure-Requests"));
    when(request.getHeader("Accept-Language")).thenReturn(headers.get("Accept-Language"));
    when(request.getHeader("Connection")).thenReturn(headers.get("Connection"));
    when(request.getParameterMap()).thenReturn(new HashMap<String, String[]>());

    when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
      @Override
      public boolean isReady() {
        return false;
      }

      @Override
      public void setWriteListener(WriteListener writeListener) {
      }

      @Override public void write(int b) {
      }
    });

    // start the request lifecycle
    H2O.getJetty().getServer().getChildHandlersByClass(JettyHTTPD.GateHandler.class)[0].handle("/", null, request, response);
    new RequestServer().doGet(request, response);

  }
}
