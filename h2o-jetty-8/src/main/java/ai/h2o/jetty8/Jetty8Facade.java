package ai.h2o.jetty8;

import water.server.Credentials;
import water.server.H2oServletContainer;
import water.server.H2oServletContainerFacade;

public class Jetty8Facade implements H2oServletContainerFacade {
  @Override
  public H2oServletContainer createServletContainer() {
    return new Jetty8HTTPD();
  }

  @Override
  public String startProxy(String[] otherArgs, Credentials proxyCredentials, String clusterUrl, boolean reportHostname) {
// TODO    return Jetty8ProxyStarter.start(otherArgs, proxyCredentials, clusterUrl, reportHostname);
    throw new UnsupportedOperationException();
  }
}
