package ai.h2o.jetty9;

import ai.h2o.jetty9.proxy.Jetty9ProxyStarter;
import water.server.Credentials;
import water.server.H2OServletContainer;
import water.server.H2OServletContainerFacade;

public class Jetty9Facade implements H2OServletContainerFacade {
  @Override
  public H2OServletContainer createServletContainer() {
    return new Jetty9HTTPD();
  }

  @Override
  public String startProxy(String[] otherArgs, Credentials proxyCredentials, String clusterUrl, boolean reportHostname) {
    return Jetty9ProxyStarter.start(otherArgs, proxyCredentials, clusterUrl, reportHostname);
  }
}
