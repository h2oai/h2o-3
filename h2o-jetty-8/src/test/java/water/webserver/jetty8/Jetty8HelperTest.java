package water.webserver.jetty8;

import org.eclipse.jetty.security.Authenticator;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import water.webserver.config.ConnectionConfiguration;
import water.webserver.iface.H2OHttpConfig;
import water.webserver.iface.H2OHttpView;

import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.*;

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
    assertTrue(connectors[0] instanceof SslSelectChannelConnector);

    SslContextFactory contextFactory = ((SslSelectChannelConnector) connectors[0]).getSslContextFactory();
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

  @Test
  public void testConfigureConnector() {
    boolean origRedirects = Response.RELATIVE_REDIRECT_ALLOWED;
    try {
      Response.RELATIVE_REDIRECT_ALLOWED = true;
      Connector defaultConnectorMock = mock(Connector.class);

      ConnectionConfiguration cfg = new ConnectionConfiguration(false) {
        @Override
        public boolean isRelativeRedirectAllowed() {
          return false;
        }
      };
      Jetty8Helper.configureConnector(defaultConnectorMock, cfg);

      verify(defaultConnectorMock).setRequestHeaderSize(32 * 1024);
      verify(defaultConnectorMock).setRequestBufferSize(32 * 1024);
      verify(defaultConnectorMock).setMaxIdleTime(5 * 60 * 1000);
      assertFalse(Response.RELATIVE_REDIRECT_ALLOWED);
    } finally {
      Response.RELATIVE_REDIRECT_ALLOWED = origRedirects;
    }
  }

  @Test
  public void testMakeFormAuthenticator() {
    assertTrue(Jetty8Helper.makeFormAuthenticator(false) 
                    instanceof org.eclipse.jetty.security.authentication.FormAuthenticator);
    Authenticator customFormAuthenticator = Jetty8Helper.makeFormAuthenticator(true);
    assertTrue(customFormAuthenticator instanceof water.webserver.jetty8.security.FormAuthenticator);
    assertTrue(((water.webserver.jetty8.security.FormAuthenticator) customFormAuthenticator).getUseRelativeRedirects());
  }
  
}
