package water.webserver;

import org.junit.Test;
import water.webserver.iface.H2OHttpView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import static org.junit.Assert.assertEquals;

public class H2OHttpViewImplTest {

  @Test
  public void gateHandlerRejectsTraceRequests() {
    H2OHttpView httpView = new H2OHttpViewImpl(null) {
      @Override
      protected boolean isAcceptingRequests() {
        return true;
      }
    };

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    
    when(request.getMethod()).thenReturn("TRACE");
    
    httpView.gateHandler(request, response);

    verify(response).setStatus(405);
  }

  @Test
  public void customServletRegistered() {
    H2OHttpView httpView = new H2OHttpViewImpl(null);
    assertEquals(EchoServletProvider.EchoServlet.class, httpView.getServlets().get("/99/Echo"));
  }

  @Test
  public void customWebsocketRegistered() {
    H2OHttpView httpView = new H2OHttpViewImpl(null);
    assertEquals(EchoServletProvider.EchoWebsocket.class, httpView.getWebsockets().get("/99/EchoWs"));
  }

}
