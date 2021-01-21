package water.hadoop.clouding.fs;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;
import water.hadoop.AbstractClouding;
import water.hadoop.h2omapper;

import java.io.*;
import java.net.InetAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class FileSystemBasedClouding extends AbstractClouding {

  private InetAddress _self_ip;
  private int _self_port = -1;
  
  private int _cloudSize;
  private Path _path;
  private FileSystem _fs;

  private final Map<String, URI> _nodes = new HashMap<>();

  public void init(Configuration conf) throws IOException {
    _cloudSize = conf.getInt(h2omapper.H2O_CLOUD_SIZE_KEY, -1);
    _path = new Path(conf.get(h2omapper.H2O_CLOUDING_DIR_KEY));
    _fs = FileSystem.get(conf);
  }

  @Override
  public void notifyAboutCloudSize(InetAddress ip, int port, InetAddress leaderIp, int leaderPort, int size) {
    if (size != _cloudSize) {
      return;
    }
    Path path = toNodePath(ip, port, "leader");
    try {
      writeFile(path, h2oUri(leaderIp, leaderPort));
    } catch (IOException e) {
      e.printStackTrace();
      exit(162);
    }
    cloudingFinished();
  }

  @Override
  public void notifyAboutEmbeddedWebServerIpPort(InetAddress ip, int port) {
    _self_ip = ip;
    _self_port = port;

    Path nodePath = toNodePath(ip, port, "node");
    try {
      writeFile(nodePath, h2oUri(ip, port));
    } catch (IOException e) {
      e.printStackTrace();
      exit(160);
    }
  }

  @Override
  public boolean providesFlatfile() {
    return true;
  }

  @Override
  public String fetchFlatfile() throws Exception {
    while (_nodes.size() < _cloudSize) {
      for (FileStatus fs : _fs.listStatus(_path, new NewNodesPathFilter())) {
        String name = fs.getPath().getName();
        if (name.endsWith(".exit")) {
          exit(161);
          return null;
        }
        try (InputStream is = _fs.open(fs.getPath());
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
          String node = br.readLine();
          _nodes.put(name, URI.create(node));
        }
      }
      Thread.sleep(1000);
    }
    StringBuilder sb = new StringBuilder();
    for (URI node : _nodes.values()) {
      sb.append(node.getHost()).append(':').append(node.getPort());
      sb.append('\n');
    }
    return sb.toString();
  }
  
  @Override
  public void exit(int status) {
    Path exitPath = toNodePath(_self_ip, _self_port, "exit");
    try {
      writeFile(exitPath, String.valueOf(status));
    } catch (Exception e) {
      e.printStackTrace();
    }
    invokeExit(status);
  }

  @Override
  public void print() {
    System.out.println("FileSystemBasedClouding print()");
    System.out.println("    Clouding directory: " + ((_path != null) ? _path : "(null)"));
    System.out.println("    Target cloud size: " + _cloudSize);
  }

  private void writeFile(Path path, String content) throws IOException {
    Path temp = new Path(path.getParent(), path.getName() + ".temp");
    try (OutputStream os = _fs.create(temp, false);
         PrintWriter wr = new PrintWriter(os)) {
      wr.println(content);
    }
    if (! _fs.rename(temp, path)) {
      throw new IOException("Failed to create file " + path + " (rename failed).");
    }
  }
  
  private Path toNodePath(InetAddress ip, int port, String type) {
    String addr = ip != null ? ip.getHostAddress() : "unknown"; 
    return new Path(_path, addr + "_" + port + "." + type);
  }

  class NewNodesPathFilter implements PathFilter {
    @Override
    public boolean accept(Path path) {
      String name = path.getName();
      if (name.endsWith(".node")) {
        return !_nodes.containsKey(name);
      }
      return name.endsWith(".exit");
    }
  }
  
  private static String h2oUri(InetAddress ip, int port) {
    return URI.create("h2o://" + ip.getHostName() + ":" + port).toString();
  } 
  
}
