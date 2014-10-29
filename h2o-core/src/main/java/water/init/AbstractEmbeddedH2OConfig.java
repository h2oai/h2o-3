package water.init;

import java.net.InetAddress;

/**
 * This class is a small shim between a main java program (such as a
 * Hadoop mapper) and an embedded full-capability H2O.
 */
public abstract class AbstractEmbeddedH2OConfig {
  /**
   * Tell the embedding software that H2O has started an embedded
   * web server on an IP and port.
   * This may be nonblocking.
   *
   * @param ip IP address this H2O can be reached at.
   * @param port Port this H2O can be reached at (for REST API and browser).
   */
  public abstract void notifyAboutEmbeddedWebServerIpPort(InetAddress ip, int port);

  /**
   * Whether H2O gets a flatfile config from this config object.
   * @return true if H2O should query the config object for a flatfile.  false otherwise.
   */
  public abstract boolean providesFlatfile();

  /**
   * If configProvidesFlatfile, get it.  This may incur a blocking network call.
   * This must be called after notifyAboutEmbeddedWebServerIpPort() or the behavior
   * will be undefined.
   *
   * This method includes it's own address, because the config may be building up
   * and managing a directory of H2O nodes.
   *
   * If this method throws any kind of exception, the node failed to get it's config,
   * and this H2O is hosed and should exit gracefully.
   *
   * @return A string with the multi-line flatfile text.
   */
  public abstract String fetchFlatfile() throws Exception;

  /**
   * Tell the embedding software that this H2O instance belongs to
   * a cloud of a certain size.
   * This may be nonblocking.
   *
   * @param ip IP address this H2O can be reached at.
   * @param port Port this H2O can be reached at (for REST API and browser).
   * @param size Number of H2O instances in the cloud.
   */
  public abstract void notifyAboutCloudSize(InetAddress ip, int port, int size);

  /**
   * Tell the embedding software that H2O wants the process to exit.
   * This should not return.  The embedding software should do any
   * required cleanup and then call exit with the status.
   *
   * @param status Process-level exit status
   */
  public abstract void exit (int status);

  /**
   * Print debug information.
   */
  public abstract void print();
}
