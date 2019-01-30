package water.hadoop;

import water.util.Log;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

class EmbeddedH2OConfig extends water.init.AbstractEmbeddedH2OConfig {

  private volatile String _driverCallbackIp;
  private volatile int _driverCallbackPort = -1;
  private volatile int _mapperCallbackPort = -1;
  private volatile String _embeddedWebServerIp = "(Unknown)";
  private volatile int _embeddedWebServerPort = -1;

  void setDriverCallbackIp(String value) {
    _driverCallbackIp = value;
  }

  void setDriverCallbackPort(int value) {
    _driverCallbackPort = value;
  }

  void setMapperCallbackPort(int value) {
    _mapperCallbackPort = value;
  }

  private class BackgroundWriterThread extends Thread {
    MapperToDriverMessage _m;

    void setMessage (MapperToDriverMessage value) {
      _m = value;
    }

    public void run() {
      try {
        Socket s = new Socket(_m.getDriverCallbackIp(), _m.getDriverCallbackPort());
        _m.write(s);
        s.close();
      }
      catch (java.net.ConnectException e) {
        System.out.println("EmbeddedH2OConfig: BackgroundWriterThread could not connect to driver at " + _driverCallbackIp + ":" + _driverCallbackPort);
        System.out.println("(This is normal when the driver disowns the hadoop job and exits.)");
      }
      catch (Exception e) {
        System.out.println("EmbeddedH2OConfig: BackgroundWriterThread caught an Exception");
        e.printStackTrace();
      }
    }
  }

  @Override
  public void notifyAboutEmbeddedWebServerIpPort(InetAddress ip, int port) {
    _embeddedWebServerIp = ip.getHostAddress();
    _embeddedWebServerPort = port;

    try {
      MapperToDriverMessage msg = new MapperToDriverMessage();
      msg.setDriverCallbackIpPort(_driverCallbackIp, _driverCallbackPort);
      msg.setMessageEmbeddedWebServerIpPort(ip.getHostAddress(), port);
      BackgroundWriterThread bwt = new BackgroundWriterThread();
      System.out.printf("EmbeddedH2OConfig: notifyAboutEmbeddedWebServerIpPort called (%s, %d)\n", ip.getHostAddress(), port);
      bwt.setMessage(msg);
      bwt.start();
    }
    catch (Exception e) {
      System.out.println("EmbeddedH2OConfig: notifyAboutEmbeddedWebServerIpPort caught an Exception");
      e.printStackTrace();
    }
  }

  @Override
  public boolean providesFlatfile() {
    return true;
  }

  @Override
  public String fetchFlatfile() throws Exception {
    System.out.println("EmbeddedH2OConfig: fetchFlatfile called");
    MapperToDriverMessage msg = new MapperToDriverMessage();
    msg.setMessageFetchFlatfile(_embeddedWebServerIp, _embeddedWebServerPort);
    Socket s = new Socket(_driverCallbackIp, _driverCallbackPort);
    msg.write(s);
    DriverToMapperMessage msg2 = new DriverToMapperMessage();
    msg2.read(s);
    char type = msg2.getType();
    if (type != DriverToMapperMessage.TYPE_FETCH_FLATFILE_RESPONSE) {
      int typeAsInt = (int)type & 0xff;
      String str = "DriverToMapperMessage type unrecognized (" + typeAsInt + ")";
      Log.err(str);
      throw new Exception(str);
    }
    s.close();
    String flatfile = msg2.getFlatfile();
    System.out.println("EmbeddedH2OConfig: fetchFlatfile returned");
    System.out.println("------------------------------------------------------------");
    System.out.println(flatfile);
    System.out.println("------------------------------------------------------------");
    return flatfile;
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
      System.out.printf("EmbeddedH2OConfig: notifyAboutCloudSize called (%s, %d, %d)\n", ip.getHostAddress(), port, size);
      bwt.setMessage(msg);
      bwt.start();
    }
    catch (Exception e) {
      System.out.println("EmbeddedH2OConfig: notifyAboutCloudSize caught an Exception");
      e.printStackTrace();
    }
  }

  @Override
  public void exit(int status) {
    try {
      MapperToDriverMessage msg = new MapperToDriverMessage();
      msg.setDriverCallbackIpPort(_driverCallbackIp, _driverCallbackPort);
      msg.setMessageExit(_embeddedWebServerIp, _embeddedWebServerPort, status);
      System.out.printf("EmbeddedH2OConfig: exit called (%d)\n", status);
      BackgroundWriterThread bwt = new BackgroundWriterThread();
      bwt.setMessage(msg);
      bwt.start();
      System.out.println("EmbeddedH2OConfig: after bwt.start()");
    }
    catch (Exception e) {
      System.out.println("EmbeddedH2OConfig: exit caught an exception 1");
      e.printStackTrace();
    }

    try {
      // Wait one second to deliver the message before exiting.
      Thread.sleep (1000);
      Socket s = new Socket("127.0.0.1", _mapperCallbackPort);
      byte[] b = new byte[] { (byte) status };
      OutputStream os = s.getOutputStream();
      os.write(b);
      os.flush();
      s.close();
      System.out.println("EmbeddedH2OConfig: after write to mapperCallbackPort");

      Thread.sleep(60 * 1000);
      // Should never make it this far!
    }
    catch (Exception e) {
      System.out.println("EmbeddedH2OConfig: exit caught an exception 2");
      e.printStackTrace();
    }

    System.exit(111);
  }

  @Override
  public void print() {
    System.out.println("EmbeddedH2OConfig print()");
    System.out.println("    Driver callback IP: " + ((_driverCallbackIp != null) ? _driverCallbackIp : "(null)"));
    System.out.println("    Driver callback port: " + _driverCallbackPort);
    System.out.println("    Embedded webserver IP: " + ((_embeddedWebServerIp != null) ? _embeddedWebServerIp : "(null)"));
    System.out.println("    Embedded webserver port: " + _embeddedWebServerPort);
  }
}
