package water.webserver.jetty8;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ssl.SslSocketConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import water.webserver.iface.H2OHttpConfig;
import water.webserver.iface.H2OHttpView;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

public class Jetty8HelperTest {

  @Rule
  public MockitoRule _mockito = MockitoJUnit.rule();

  @Mock
  public H2OHttpView _hhView;

  @Test
  public void testCreateJettyServerWithSSL() {
    H2OHttpConfig cfg = new H2OHttpConfig();
    cfg.jks = "/path/to/keystore.jks";
    cfg.jks_pass = "test-password";
    cfg.jks_alias = "test-alias";

    when(_hhView.getConfig()).thenReturn(cfg);

    Server s = new Jetty8Helper(_hhView).createJettyServer("127.0.0.1", 0);
    Connector[] connectors = s.getConnectors();

    assertEquals(1, connectors.length);
    assertTrue(connectors[0] instanceof SslSocketConnector);

    SslContextFactory contextFactory = ((SslSocketConnector) connectors[0]).getSslContextFactory();
    assertEquals("/path/to/keystore.jks", contextFactory.getKeyStorePath());
    assertEquals("test-alias", contextFactory.getCertAlias());
  }

  @Test
  public void testEnsureDaemonThreads() {
    H2OHttpConfig cfg = new H2OHttpConfig();
    cfg.ensure_daemon_threads = true;

    when(_hhView.getConfig()).thenReturn(cfg);

    Server s = new Jetty8Helper(_hhView).createJettyServer("127.0.0.1", 0);
    assertTrue(s.getThreadPool() instanceof QueuedThreadPool);
    assertTrue(((QueuedThreadPool) s.getThreadPool()).isDaemon());
  }

}
