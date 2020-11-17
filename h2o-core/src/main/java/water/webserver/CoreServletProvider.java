package water.webserver;

import water.api.*;
import water.server.ServletMeta;
import water.server.ServletProvider;

import java.util.*;

public class CoreServletProvider implements ServletProvider {

  private static final List<ServletMeta> SERVLETS = Collections.unmodifiableList(Arrays.asList(
    new ServletMeta("/3/NodePersistentStorage.bin/*", NpsBinServlet.class),
    new ServletMeta("/3/PostFile.bin", PostFileServlet.class),
    new ServletMeta("/3/PostFile", PostFileServlet.class),
    new ServletMeta("/3/DownloadDataset", DatasetServlet.class),
    new ServletMeta("/3/DownloadDataset.bin", DatasetServlet.class),
    new ServletMeta("/3/PutKey.bin", PutKeyServlet.class),
    new ServletMeta("/3/PutKey", PutKeyServlet.class),
    new ServletMeta("/", RequestServer.class)
  ));

  @Override
  public List<ServletMeta> servlets() {
    return SERVLETS;
  }

  @Override
  public int priority() {
    return 0;
  }

}
