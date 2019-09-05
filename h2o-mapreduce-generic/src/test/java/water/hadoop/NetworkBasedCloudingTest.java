package water.hadoop;

import org.junit.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;

public class NetworkBasedCloudingTest {

  private ServerSocket _blocked_server_socket;
  private int _blocked_port;

  private ServerSocket _open_server_socket;
  private int _open_port;

  @Before
  public void beforeTest() throws IOException {
    _blocked_server_socket = new ServerSocket(0, 1);
    _blocked_port = _blocked_server_socket.getLocalPort();
    // fill backlog queue by this request so consequent requests will be blocked
    new Socket().connect(_blocked_server_socket.getLocalSocketAddress());

    _open_server_socket = new ServerSocket(0);
    _open_port = _open_server_socket.getLocalPort();
  }

  @Test
  public void testFetchFile_failure() throws Exception {
    ExCollectingNetworkBasedClouding cfg = new ExCollectingNetworkBasedClouding();
    cfg.setDriverCallbackIp("127.0.0.1");
    cfg.setDriverCallbackPort(_blocked_port);

    SocketException e = null;
    try {
      cfg.fetchFlatfile();
    } catch (SocketException se) {
      e = se;
    }
    assertNotNull(e);

    assertEquals(2, cfg.exceptions.size());
  }

  @Test
  public void testFetchFile() throws Exception {
    h2odriver.CallbackManager cm = new h2odriver().new CallbackManager(1) {
      @Override
      protected ServerSocket bindCallbackSocket() throws IOException {
        return _open_server_socket;
      }
    };
    cm.start();

    NetworkBasedClouding cfg = new SocketClosingNetworkBasedClouding();
    cfg.setDriverCallbackIp("127.0.0.1");
    cfg.setDriverCallbackPort(_blocked_port);
    cfg.setEmbeddedWebServerInfo("h2o.ai", 600);

    String flatfile = cfg.fetchFlatfile();
    assertEquals("h2o.ai:600\n", flatfile);
  }
  
  @After
  public void afterTest() throws IOException {
    if (_blocked_server_socket != null && !_blocked_server_socket.isClosed()) {
      _blocked_server_socket.close();
    }
  }
  
  private static class ExCollectingNetworkBasedClouding extends NetworkBasedClouding {
    private final List<IOException> exceptions = new LinkedList<>();
    @Override
    protected void reportFetchfileAttemptFailure(IOException ioex, int attempt) throws IOException {
      assertEquals(attempt, exceptions.size());
      exceptions.add(ioex);
      super.reportFetchfileAttemptFailure(ioex, attempt);
    }
  }

  private class SocketClosingNetworkBasedClouding extends NetworkBasedClouding {
    @Override
    protected void reportFetchfileAttemptFailure(IOException ioex, int attempt) {
      setDriverCallbackPort(_open_port);
    }
  }

}
