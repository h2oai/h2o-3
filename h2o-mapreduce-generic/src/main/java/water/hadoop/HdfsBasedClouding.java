package water.hadoop;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;
import water.init.AbstractEmbeddedH2OConfig;

import java.io.*;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

public class HdfsBasedClouding extends AbstractEmbeddedH2OConfig {

  private InetAddress _self_ip;
  private int _self_port = -1;
  
  private final int _n;
  private final Path _path;
  private final FileSystem _fs;
  private final ExitEvent _exit_event;

  private final Map<String, String> _nodes;

  HdfsBasedClouding(int n, String path, Configuration conf, ExitEvent exitEvent) throws IOException {
    _n = n;
    _path = new Path(path);
    _fs = FileSystem.get(conf);
    _nodes = new HashMap<>(_n);
    _exit_event = exitEvent;
  }

  @Override
  public void notifyAboutCloudSize(InetAddress ip, int port, InetAddress leaderIp, int leaderPort, int size) {
    if (size != _n)
      return;
    String leader = leaderIp.getHostName() + ":" + leaderPort;
    Path path = toNodePath(ip, port, "leader");
    try {
      writeFile(path, leader);
    } catch (IOException e) {
      e.printStackTrace();
      exit(162);
    }
  }

  @Override
  public void notifyAboutEmbeddedWebServerIpPort(InetAddress ip, int port) {
    _self_ip = ip;
    _self_port = port;

    Path nodePath = toNodePath(ip, port, "node");
    try {
      String nodeInfo = ip.getHostAddress() + ":" + port;
      writeFile(nodePath, nodeInfo);
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
    while (_nodes.size() < _n) {
      for (FileStatus fs : _fs.listStatus(_path, new NewNodesPathFilter())) {
        String name = fs.getPath().getName();
        if (name.endsWith(".exit")) {
          exit(161);
          return null;
        }
        try (InputStream is = _fs.open(fs.getPath());
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
          _nodes.put(name, br.readLine());
        }
      }
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    StringBuilder sb = new StringBuilder();
    for (String node : _nodes.values()) {
      sb.append(node);
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
    _exit_event.send(status);
  }

  @Override
  public void print() {
    
  }

  private void writeFile(Path path, String content) throws IOException {
    try (OutputStream os = _fs.create(path, false);
         PrintWriter wr = new PrintWriter(os)) {
      wr.write(content);
      wr.write('\n');
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
  
}
