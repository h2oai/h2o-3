package water.webserver;

import org.junit.Test;
import water.TestBase;
import water.webserver.iface.H2OHttpView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

public class H2OHttpViewImplTest extends TestBase {

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
}
