package ai.h2o.jetty8;

import ai.h2o.jetty8.proxy.Jetty8ProxyStarter;
import water.server.Credentials;
import water.server.H2OServletContainer;
import water.server.H2OServletContainerFacade;

public class Jetty8Facade implements H2OServletContainerFacade {
  @Override
  public H2OServletContainer createServletContainer() {
    return new Jetty8HTTPD();
  }

  @Override
  public String startProxy(String[] otherArgs, Credentials proxyCredentials, String clusterUrl, boolean reportHostname) {
    return Jetty8ProxyStarter.start(otherArgs, proxyCredentials, clusterUrl, reportHostname);
  }
}
