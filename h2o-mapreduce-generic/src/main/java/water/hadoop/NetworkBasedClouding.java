package water.hadoop;

import org.apache.hadoop.conf.Configuration;
import water.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

class NetworkBasedClouding extends AbstractClouding {

  private static final int FETCH_FILE_RETRYS = Integer.parseInt(System.getProperty("sys.ai.h2o.hadoop.callback.retrys", "3")); 

  private volatile String _driverCallbackIp;
  private volatile int _driverCallbackPort = -1;
  private volatile String _embeddedWebServerIp = "(Unknown)";
  private volatile int _embeddedWebServerPort = -1;
  private volatile int _cloudSize = -1;

  void setDriverCallbackIp(String value) {
    _driverCallbackIp = value;
  }

  void setDriverCallbackPort(int value) {
    _driverCallbackPort = value;
  }

  @Override
  public void init(Configuration conf) {
    _driverCallbackIp = conf.get(h2omapper.H2O_DRIVER_IP_KEY);
    _driverCallbackPort = conf.getInt(h2omapper.H2O_DRIVER_PORT_KEY, -1);
    _cloudSize = conf.getInt(h2omapper.H2O_CLOUD_SIZE_KEY, -1);
  }

  private class BackgroundWriterThread extends Thread {
    MapperToDriverMessage _m;

    void setMessage (MapperToDriverMessage value) {
      _m = value;
    }

    public void run() {
      try (Socket s = new Socket(_m.getDriverCallbackIp(), _m.getDriverCallbackPort())) {
        _m.write(s);
      }
      catch (java.net.ConnectException e) {
        System.out.println("NetworkBasedClouding: BackgroundWriterThread could not connect to driver at " + _driverCallbackIp + ":" + _driverCallbackPort);
        System.out.println("(This is normal when the driver disowns the hadoop job and exits.)");
      }
      catch (Exception e) {
        System.out.println("NetworkBasedClouding: BackgroundWriterThread caught an Exception");
        e.printStackTrace();
      }
    }
  }

  void setEmbeddedWebServerInfo(String ip, int port) {
    _embeddedWebServerIp = ip;
    _embeddedWebServerPort = port;
  }
  
  @Override
  public void notifyAboutEmbeddedWebServerIpPort(InetAddress ip, int port) {
    setEmbeddedWebServerInfo(ip.getHostAddress(), port);

    try {
      MapperToDriverMessage msg = new MapperToDriverMessage();
      msg.setDriverCallbackIpPort(_driverCallbackIp, _driverCallbackPort);
      msg.setMessageEmbeddedWebServerIpPort(ip.getHostAddress(), port);
      BackgroundWriterThread bwt = new BackgroundWriterThread();
      System.out.printf("NetworkBasedClouding: notifyAboutEmbeddedWebServerIpPort called (%s, %d)\n", ip.getHostAddress(), port);
      bwt.setMessage(msg);
      bwt.start();
    }
    catch (Exception e) {
      System.out.println("NetworkBasedClouding: notifyAboutEmbeddedWebServerIpPort caught an Exception");
      e.printStackTrace();
    }
  }

  @Override
  public boolean providesFlatfile() {
    return true;
  }

  @Override
  public String fetchFlatfile() throws Exception {
    System.out.println("NetworkBasedClouding: fetchFlatfile called");
    DriverToMapperMessage response = null;
    for (int i = 0; i < FETCH_FILE_RETRYS; i++) {
      try {
        System.out.println("NetworkBasedClouding: Attempting to fetch flatfile (attempt #" + i + ")");
        MapperToDriverMessage msg = new MapperToDriverMessage();
        msg.setMessageFetchFlatfile(_embeddedWebServerIp, _embeddedWebServerPort);
        Socket s = new Socket(_driverCallbackIp, _driverCallbackPort);
        msg.write(s);
        response = new DriverToMapperMessage();
        response.read(s);
        s.close();
        break;
      } catch (IOException ioex) {
        if (i + 1 == FETCH_FILE_RETRYS)
          throw ioex;
        reportFetchfileAttemptFailure(ioex, i);
      }
    }
    assert response != null;
    char type = response.getType();
    if (type != DriverToMapperMessage.TYPE_FETCH_FLATFILE_RESPONSE) {
      int typeAsInt = (int)type & 0xff;
      String str = "DriverToMapperMessage type unrecognized (" + typeAsInt + ")";
      Log.err(str);
      throw new Exception(str);
    }
    String flatfile = response.getFlatfile();
    System.out.println("NetworkBasedClouding: fetchFlatfile returned");
    System.out.println("------------------------------------------------------------");
    System.out.println(flatfile);
    System.out.println("------------------------------------------------------------");
    return flatfile;
  }

  protected void reportFetchfileAttemptFailure(IOException ioex, int attempt) throws IOException {
    System.out.println("NetworkBasedClouding: Attempt #" + attempt + " to fetch flatfile failed");
    ioex.printStackTrace();
  }
  
  @Override
  public void notifyAboutCloudSize(InetAddress ip, int port, InetAddress leaderIp, int leaderPort, int size) {
    _embeddedWebServerIp = ip.getHostAddress();
    _embeddedWebServerPort = port;
    try {
      MapperToDriverMessage msg = new MapperToDriverMessage();
      msg.setDriverCallbackIpPort(_driverCallbackIp, _driverCallbackPort);
      msg.setMessageCloudSize(ip.getHostAddress(), port, leaderIp.getHostAddress(), leaderPort, size);
      BackgroundWriterThread bwt = new BackgroundWriterThread();
      System.out.printf("NetworkBasedClouding: notifyAboutCloudSize called (%s, %d, %d)\n", ip.getHostAddress(), port, size);
      bwt.setMessage(msg);
      bwt.start();
    }
    catch (Exception e) {
      System.out.println("NetworkBasedClouding: notifyAboutCloudSize caught an Exception");
      e.printStackTrace();
    }
    if (size == _cloudSize) {
      cloudingFinished();
    }
  }

  @Override
  public void exit(int status) {
    try {
      MapperToDriverMessage msg = new MapperToDriverMessage();
      msg.setDriverCallbackIpPort(_driverCallbackIp, _driverCallbackPort);
      msg.setMessageExit(_embeddedWebServerIp, _embeddedWebServerPort, status);
      System.out.printf("NetworkBasedClouding: exit called (%d)\n", status);
      BackgroundWriterThread bwt = new BackgroundWriterThread();
      bwt.setMessage(msg);
      bwt.start();
      System.out.println("NetworkBasedClouding: after bwt.start()");
    }
    catch (Exception e) {
      System.out.println("NetworkBasedClouding: failed to send message to driver");
      e.printStackTrace();
    }
    
    invokeExit(status);
  }

  @Override
  public void print() {
    System.out.println("NetworkBasedClouding print()");
    System.out.println("    Driver callback IP: " + ((_driverCallbackIp != null) ? _driverCallbackIp : "(null)"));
    System.out.println("    Driver callback port: " + _driverCallbackPort);
    System.out.println("    Embedded webserver IP: " + ((_embeddedWebServerIp != null) ? _embeddedWebServerIp : "(null)"));
    System.out.println("    Embedded webserver port: " + _embeddedWebServerPort);
  }
}
