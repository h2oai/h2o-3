package water;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.junit.BeforeClass;
import org.junit.Test;


import static org.junit.Assert.*;

public class AbstractHTTPDTest extends TestUtil {

  @BeforeClass()
  public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void testRequestNoServerInfo() throws Exception {
    DefaultHttpClient client = new DefaultHttpClient();

    HttpParams params = new BasicHttpParams();
    params.setParameter(ClientPNames.HANDLE_REDIRECTS, false);

    HttpGet request = new HttpGet(H2O.getURL("http"));
    request.setParams(params);

    HttpResponse resp = client.execute(request);

    assertEquals(HttpStatus.SC_MOVED_PERMANENTLY, resp.getStatusLine().getStatusCode());
    Header[] serverInfo = resp.getHeaders("Server");
    assertNotNull(serverInfo);
    assertEquals(0, serverInfo.length); // no information about the Http Server (see PUBDEV-5458)
  }

}