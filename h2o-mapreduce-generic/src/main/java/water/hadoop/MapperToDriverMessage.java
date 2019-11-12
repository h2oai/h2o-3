package water.hadoop;

import java.net.Socket;

/**
 * Simple class to help serialize messages from the Mapper to the Driver.
 */
class MapperToDriverMessage extends AbstractMessage {
  public static final char TYPE_EMBEDDED_WEB_SERVER_IP_PORT = 12;
  public static final char TYPE_FETCH_FLATFILE = 13;
  public static final char TYPE_CLOUD_SIZE = 14;
  public static final char TYPE_EXIT = 15;


  private String _driverCallbackIp = null;
  private int _driverCallbackPort = -1;
  private char _type = TYPE_UNKNOWN;

  private String _embeddedWebServerIp = "";
  private int _embeddedWebServerPort = -1;
  private String _leaderWebServerIp = "";
  private int _leaderWebServerPort = -1;
  private int _cloudSize = -1;
  private int _exitStatus = -1;

  public MapperToDriverMessage() {
  }

  // Readers
  // -------
  public char getType() { return _type; }
  public String getEmbeddedWebServerIp() { return _embeddedWebServerIp; }
  public int getEmbeddedWebServerPort() { return _embeddedWebServerPort; }
  public String getLeaderWebServerIp() { return _leaderWebServerIp; }
  public int getLeaderWebServerPort() { return _leaderWebServerPort; }
  public int getCloudSize() { return _cloudSize; }
  public int getExitStatus() { return _exitStatus; }

  public void read(Socket s) throws Exception {
    _type = readType(s);

    if (_type == TYPE_EMBEDDED_WEB_SERVER_IP_PORT) {
      _embeddedWebServerIp = readString(s);
      _embeddedWebServerPort = readInt(s);
    }
    else if (_type == TYPE_FETCH_FLATFILE) {
      _embeddedWebServerIp = readString(s);
      _embeddedWebServerPort = readInt(s);
    }
    else if (_type == TYPE_CLOUD_SIZE) {
      _embeddedWebServerIp = readString(s);
      _embeddedWebServerPort = readInt(s);
      _leaderWebServerIp = readString(s);
      _leaderWebServerPort = readInt(s);
      _cloudSize = readInt(s);
    }
    else if (_type == TYPE_EXIT) {
      _embeddedWebServerIp = readString(s);
      _embeddedWebServerPort = readInt(s);
      _exitStatus = readInt(s);
    }
    else {
      System.out.println ("MapperToDriverMessage: read: Unknown type");
      // Ignore unknown types.
    }
  }

  // Writers
  // -------

  public void setDriverCallbackIpPort(String ip, int port) {
    _driverCallbackIp = ip;
    _driverCallbackPort = port;
  }

  public String getDriverCallbackIp() { return _driverCallbackIp; }
  public int getDriverCallbackPort() { return _driverCallbackPort; }

  public void setMessageEmbeddedWebServerIpPort(String ip, int port) {
    _type = TYPE_EMBEDDED_WEB_SERVER_IP_PORT;
    _embeddedWebServerIp = ip;
    _embeddedWebServerPort = port;
  }

  public void setMessageFetchFlatfile(String ip, int port) {
    _type = TYPE_FETCH_FLATFILE;
    _embeddedWebServerIp = ip;
    _embeddedWebServerPort = port;
  }

  public void setMessageCloudSize(String ip, int port, String leaderIp, int leaderPort, int cloudSize) {
    _type = TYPE_CLOUD_SIZE;
    _embeddedWebServerIp = ip;
    _embeddedWebServerPort = port;
    _leaderWebServerIp = leaderIp;
    _leaderWebServerPort = leaderPort;
    _cloudSize = cloudSize;
  }

  public void setMessageExit(String ip, int port, int exitStatus) {
    _type = TYPE_EXIT;
    _embeddedWebServerIp = ip;
    _embeddedWebServerPort = port;
    _exitStatus = exitStatus;
  }

  public void write(Socket s) throws Exception {
    if (_type == TYPE_EMBEDDED_WEB_SERVER_IP_PORT) {
      writeMessageEmbeddedWebServerIpPort(s);
    }
    else if (_type == TYPE_FETCH_FLATFILE) {
      writeMessageFetchFlatfile(s);
    }
    else if (_type == TYPE_CLOUD_SIZE) {
      writeMessageCloudSize(s);
    }
    else if (_type == TYPE_EXIT) {
      writeMessageExit(s);
    }
    else {
      throw new Exception("MapperToDriverMessage: write: Unknown type");
    }

    s.getOutputStream().flush();
  }

  //-----------------------------------------------------------------
  // Private below this line.
  //-----------------------------------------------------------------

  private void writeMessageEmbeddedWebServerIpPort(Socket s) throws Exception {
    writeType(s, TYPE_EMBEDDED_WEB_SERVER_IP_PORT);
    writeString(s, _embeddedWebServerIp);
    writeInt(s, _embeddedWebServerPort);
  }

  private void writeMessageFetchFlatfile(Socket s) throws Exception {
    writeType(s, TYPE_FETCH_FLATFILE);
    writeString(s, _embeddedWebServerIp);
    writeInt(s, _embeddedWebServerPort);
  }

  private void writeMessageCloudSize(Socket s) throws Exception {
    writeType(s, TYPE_CLOUD_SIZE);
    writeString(s, _embeddedWebServerIp);
    writeInt(s, _embeddedWebServerPort);
    writeString(s, _leaderWebServerIp);
    writeInt(s, _leaderWebServerPort);
    writeInt(s, _cloudSize);
  }

  private void writeMessageExit(Socket s) throws Exception {
    writeType(s, TYPE_EXIT);
    writeString(s, _embeddedWebServerIp);
    writeInt(s, _embeddedWebServerPort);
    writeInt(s, _exitStatus);
  }
}
