package water.server;

/**
 * Facade for servlet container. We typically use Jetty behind this; however, due to use of various major versions,
 * we cannot afford anymore to depend on Jetty directly; the changes between its major versions are as significant
 * as if it was a completely different webserver.
 *
 * This interface is supposed to hide all those dependencies.
 */
public interface H2OServletContainerFacade {
  H2OServletContainer createServletContainer();

  String startProxy(String[] otherArgs, Credentials proxyCredentials, String clusterUrl, boolean reportHostname);
}
