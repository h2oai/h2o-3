package water.webserver.jetty9;

import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.HandlerWrapper;
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
import water.webserver.iface.LoginType;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Jetty9HelperTest {

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

    Server s = new Jetty9Helper(_hhView).createJettyServer("127.0.0.1", 0);
    Connector[] connectors = s.getConnectors();

    assertEquals(1, connectors.length);

    List<ConnectionFactory> contextFactories = new ArrayList<>(connectors[0].getConnectionFactories());
    assertEquals(2, contextFactories.size());

    SslConnectionFactory sslConnectionFactory = (SslConnectionFactory) (contextFactories.get(0) instanceof SslConnectionFactory ?
            contextFactories.get(0) : contextFactories.get(1));
    SslContextFactory contextFactory = sslConnectionFactory.getSslContextFactory();

    assertEquals("file:///path/to/keystore.jks", contextFactory.getKeyStorePath());
    assertEquals("test-alias", contextFactory.getCertAlias());
  }

  @Test
  public void testEnsureDaemonThreads() {
    H2OHttpConfig cfg = new H2OHttpConfig();
    cfg.ensure_daemon_threads = true;

    when(_hhView.getConfig()).thenReturn(cfg);

    Server s = new Jetty9Helper(_hhView).createJettyServer("127.0.0.1", 0);
    assertTrue(s.getThreadPool() instanceof QueuedThreadPool);
    assertTrue(((QueuedThreadPool) s.getThreadPool()).isDaemon());
  }

  @Test
  public void testMakeHttpConfiguration() {
    HttpConfiguration defaultCfg = Jetty9Helper.makeHttpConfiguration(new ConnectionConfiguration(false));
    assertFalse(defaultCfg.getSendServerVersion());
    assertEquals(defaultCfg.getRequestHeaderSize(), 32 * 1024);
    assertEquals(defaultCfg.getResponseHeaderSize(), 32 * 1024);
    assertEquals(defaultCfg.getOutputBufferSize(), new HttpConfiguration().getOutputBufferSize());
    assertTrue(defaultCfg.isRelativeRedirectAllowed());
    
    ConnectionConfiguration ccMock = mock(ConnectionConfiguration.class);
    when(ccMock.getRequestHeaderSize()).thenReturn(42);
    when(ccMock.getResponseHeaderSize()).thenReturn(43);
    when(ccMock.getOutputBufferSize(anyInt())).thenReturn(44);
    when(ccMock.getIdleTimeout()).thenReturn(45);
    when(ccMock.isRelativeRedirectAllowed()).thenReturn(false);

    HttpConfiguration customCfg = Jetty9Helper.makeHttpConfiguration(ccMock);
    assertFalse(customCfg.getSendServerVersion());
    assertEquals(customCfg.getRequestHeaderSize(), 42);
    assertEquals(customCfg.getResponseHeaderSize(), 43);
    assertEquals(customCfg.getOutputBufferSize(), 44);
    assertEquals(customCfg.getIdleTimeout(), 45);
    assertFalse(customCfg.isRelativeRedirectAllowed());
  }

  @Test
  public void testAuthWrapper() {
    H2OHttpConfig cfg = new H2OHttpConfig();
    cfg.loginType = LoginType.HASH;

    when(_hhView.getConfig()).thenReturn(cfg);

    final Server server = new Server();
    final Jetty9Helper helper = new Jetty9Helper(_hhView);
    final HandlerWrapper auth = helper.authWrapper(server);

    assertTrue(auth instanceof ConstraintSecurityHandler);
    ConstraintSecurityHandler securityHandler = (ConstraintSecurityHandler) auth; 
    assertEquals("BASIC", securityHandler.getAuthMethod());
    assertTrue(securityHandler.getLoginService() instanceof HashLoginService);
  }
}
