package water.init;

import water.H2O;
import water.H2ONode;
import water.TCPReceiverThread;
import water.util.Log;
import water.util.NetworkUtils;
import water.util.StringUtils;
import water.webserver.H2OHttpViewImpl;
import water.webserver.iface.H2OHttpConfig;
import water.webserver.iface.HttpServerLoader;
import water.webserver.iface.LoginType;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Data structure for holding network info specified by the user on the command line.
 */
public class NetworkInit {

  private static ServerSocketChannel _tcpSocket;

  public static H2OHttpViewImpl h2oHttpView;

  public static InetAddress findInetAddressForSelf() throws Error {
    if (H2O.SELF_ADDRESS != null)
      return H2O.SELF_ADDRESS;
    else {
      if (H2O.ARGS.disable_web && H2O.ARGS.disable_net) {
        //  if we don't need an address just use loopback as a filler, SELF_ADDRESS always needs to be defined
        return InetAddress.getLoopbackAddress();
      }
      try {
        return HostnameGuesser.findInetAddressForSelf(H2O.ARGS.ip, H2O.ARGS.network);
      } catch (HostnameGuesser.HostnameGuessingException e) {
        if (e.getCause() != null)
          Log.err(e.getCause());
        else
          Log.err(e.getMessage());
        H2O.clusterInitializationFailed();
      }
    }
    assert false; // should never be reached
    return null;
  }

  /**
   *  Parse arguments and set cloud name in any case. Strip out "-name NAME"
   *  and "-flatfile <filename>". Ignore the rest. Set multi-cast port as a hash
   *  function of the name. Parse node ip addresses from the filename.
   * @todo this method introduces mutual dependency between classes {@link H2O} and {@link NetworkInit} ! Move it out!
   */
  public static void initializeNetworkSockets( ) {
    // Assign initial ports
    H2O.API_PORT = H2O.ARGS.port == 0 ? H2O.ARGS.baseport : H2O.ARGS.port;

    // Late instantiation of web server, if needed.
    if (H2O.getWebServer() == null && !H2O.ARGS.disable_web) {
      final H2OHttpConfig config = webServerConfig(H2O.ARGS);
      h2oHttpView = new H2OHttpViewImpl(config);
      H2O.setWebServer(HttpServerLoader.INSTANCE.createWebServer(h2oHttpView));
    }

    // API socket is only used to find opened port on given ip.
    ServerSocket apiSocket = null;

    // At this point we would like to allocate 2 consecutive ports - by default (if `port_offset` is not specified).
    // If `port_offset` is specified we are trying to allocate a pair (port, port + port_offset).
    //
    while (true) {
      H2O.H2O_PORT = H2O.API_PORT + H2O.ARGS.port_offset;
      try {
        // kbn. seems like we need to set SO_REUSEADDR before binding?
        // http://www.javadocexamples.com/java/net/java.net.ServerSocket.html#setReuseAddress:boolean
        // When a TCP connection is closed the connection may remain in a timeout state
        // for a period of time after the connection is closed (typically known as the
        // TIME_WAIT state or 2MSL wait state). For applications using a well known socket address
        // or port it may not be possible to bind a socket to the required SocketAddress
        // if there is a connection in the timeout state involving the socket address or port.
        // Enabling SO_REUSEADDR prior to binding the socket using bind(SocketAddress)
        // allows the socket to be bound even though a previous connection is in a timeout state.
        // cnc: this is busted on windows.  Back to the old code.
        if (!H2O.ARGS.disable_web) {
          apiSocket = H2O.ARGS.web_ip == null // Listen to any interface
                      ? new ServerSocket(H2O.API_PORT)
                      : new ServerSocket(H2O.API_PORT, -1, getInetAddress(H2O.ARGS.web_ip));
          apiSocket.setReuseAddress(true);
        }
        if (!H2O.ARGS.disable_net) {
          InetSocketAddress isa = new InetSocketAddress(H2O.SELF_ADDRESS, H2O.H2O_PORT);
          // Bind to the TCP socket also
          _tcpSocket = ServerSocketChannel.open();
          _tcpSocket.socket().setReceiveBufferSize(water.AutoBuffer.TCP_BUF_SIZ);
          _tcpSocket.socket().bind(isa);
        }

        // Warning: There is a ip:port race between socket close and starting Jetty
        if (!H2O.ARGS.disable_web) {
          apiSocket.close();
          H2O.getWebServer().start(H2O.ARGS.web_ip, H2O.API_PORT);
        }

        break;

      } catch (IOException e) {
        Log.trace("Cannot allocate API port " + H2O.API_PORT + " because of following exception: ", e);
        if( apiSocket != null ) try { apiSocket.close(); } catch( IOException ohwell ) { Log.err(ohwell); }
        if( _tcpSocket != null ) try { _tcpSocket.close(); } catch( IOException ie ) { }
        apiSocket = null;
        _tcpSocket = null;
        if( H2O.ARGS.port != 0 )
          H2O.die("On " + H2O.SELF_ADDRESS +
              " some of the required ports " + H2O.ARGS.port +
              ", " + (H2O.ARGS.port+H2O.ARGS.port_offset) +
              " are not available, change -port PORT and try again.");
      }
      // Try next available port to bound
      H2O.API_PORT += (H2O.ARGS.port_offset == 1) ? 2 : 1;
      if (H2O.API_PORT > (1<<16)) {
        Log.err("Cannot find free port for " + H2O.SELF_ADDRESS + " from baseport = " + H2O.ARGS.baseport);
        H2O.exit(-1);
      }
    }
    Log.notifyAboutNetworkingInitialized();
    boolean isIPv6 = H2O.SELF_ADDRESS instanceof Inet6Address; // Is IPv6 address was assigned to this node
    H2O.SELF = H2ONode.self(H2O.SELF_ADDRESS);
    if (!H2O.ARGS.disable_web) {
      Log.info("Internal communication uses port: ", H2O.H2O_PORT, "\n" +
          "Listening for HTTP and REST traffic on " + H2O.getURL(h2oHttpView.getScheme()) + "/");
    }
    try {
      Log.debug("Interface MTU: ", (NetworkInterface.getByInetAddress(H2O.SELF_ADDRESS)).getMTU());
    } catch (SocketException se) {
      Log.debug("No MTU due to SocketException. " + se.toString());
    }

    String embeddedConfigFlatfile = null;
    AbstractEmbeddedH2OConfig ec = H2O.getEmbeddedH2OConfig();
    if (ec != null) {
      ec.notifyAboutEmbeddedWebServerIpPort(H2O.SELF_ADDRESS, H2O.API_PORT);
      if (ec.providesFlatfile()) {
        try {
          embeddedConfigFlatfile = ec.fetchFlatfile();
        }
        catch (Exception e) {
          Log.err("Failed to get embedded config flatfile");
          Log.err(e);
          H2O.exit(1);
        }
      }
    }

    // Read a flatfile of allowed nodes
    if (embeddedConfigFlatfile != null)
      H2O.setFlatfile(parseFlatFileFromString(embeddedConfigFlatfile));
    else
      H2O.setFlatfile(parseFlatFile(H2O.ARGS.flatfile));

    // All the machines has to agree on the same multicast address (i.e., multicast group)
    // Hence use the cloud name to generate multicast address
    // Note: if necessary we should permit configuration of multicast address manually
    // Note:
    //   - IPv4 Multicast IPs are in the range E1.00.00.00 to EF.FF.FF.FF
    //   - IPv6 Multicast IPs are in the range defined in NetworkUtils
    int hash = H2O.ARGS.name.hashCode();
    try {
      H2O.CLOUD_MULTICAST_GROUP = isIPv6 ? NetworkUtils.getIPv6MulticastGroup(hash, NetworkUtils.getIPv6Scope(H2O.SELF_ADDRESS))
                                         : NetworkUtils.getIPv4MulticastGroup(hash);
    } catch (UnknownHostException e) {
      Log.err("Cannot get multicast group address for " + H2O.SELF_ADDRESS);
      Log.throwErr(e);
    }
    H2O.CLOUD_MULTICAST_PORT = NetworkUtils.getMulticastPort(hash);
  }

  public static TCPReceiverThread makeReceiverThread() {
    return new TCPReceiverThread(NetworkInit._tcpSocket);
  }

  public static void close() throws IOException {
    ServerSocketChannel tcpSocket = _tcpSocket;
    if (tcpSocket != null) {
      _tcpSocket = null;
      tcpSocket.close();
    }
  }

  public static H2OHttpConfig webServerConfig(H2O.OptArgs args) {
    final H2OHttpConfig config = new H2OHttpConfig();
    config.jks = args.jks;
    config.jks_pass = args.jks_pass;
    config.jks_alias = getJksAlias(args);
    config.loginType = parseLoginType(args);
    configureLoginType(config.loginType, args.login_conf);
    config.login_conf = args.login_conf;
    config.spnego_properties = args.spnego_properties;
    config.form_auth = args.form_auth;
    config.session_timeout = args.session_timeout;
    config.user_name = args.user_name;
    config.context_path = args.context_path;
    config.ensure_daemon_threads = args.embedded;
    return config;
  }

  static String getJksAlias(H2O.OptArgs args) {
    return getJksAlias(args, H2O.SELF_ADDRESS);
  }

  static String getJksAlias(H2O.OptArgs args, InetAddress self) {
    final String alias;
    if (args.hostname_as_jks_alias) {
      alias = args.ip != null ? args.ip : HostnameGuesser.localAddressToHostname(self);
    } else
      alias = args.jks_alias;
    if (alias != null)
      Log.info("HTTPS will be secured using a certificate with alias `" + alias + "`");
    return alias;
  }

  /**
   * @param args commandline arguments to parse
   * @return one of login types - never returns null
   */
  private static LoginType parseLoginType(H2O.BaseArgs args) {
    final LoginType loginType;
    if (args.hash_login) {
      loginType = LoginType.HASH;
    } else if (args.ldap_login) {
      loginType = LoginType.LDAP;
    } else if (args.kerberos_login) {
      loginType = LoginType.KERBEROS;
    } else if (args.spnego_login) {
      loginType = LoginType.SPNEGO;
    } else if (args.pam_login) {
      loginType = LoginType.PAM;
    } else {
      return LoginType.NONE;
    }
    return loginType;
  }

  private static void configureLoginType(LoginType loginType, String loginConf) {
    if (loginType == LoginType.NONE) {
      return;
    }
    if (loginConf == null) {
      throw new IllegalArgumentException("Must specify -login_conf argument");
    }
    if (loginType.needToCheckUserName()) {
      // LDAP, KERBEROS, PAM
      Log.info(String.format("Configuring LoginService (with %s)", loginType));
      System.setProperty("java.security.auth.login.config", loginConf);
    } else {
      // HASH only
      Log.info("Configuring HashLoginService");
    }
  }

  /**
   * Get address for given IP.
   * @param ip  textual representation of IP (host)
   * @return IPv4 or IPv6 address which matches given IP and is in specified range
   */
  private static InetAddress getInetAddress(String ip) {
    if (ip == null)
      return null;

    InetAddress addr = null;
    try {
      addr = InetAddress.getByName(ip);
    } catch (UnknownHostException e) {
      Log.err(e);
      H2O.exit(-1);
    }
    return addr;
  }

  // Multicast send-and-close.  Very similar to udp_send, except to the
  // multicast port (or all the individuals we can find, if multicast is
  // disabled).
  public static void multicast( ByteBuffer bb , byte priority) {
    if (H2O.ARGS.disable_net)
      return;
    try { multicast2(bb, priority); }
    catch (Exception ie) {}
  }

  static private void multicast2( ByteBuffer bb, byte priority ) {
    if( !H2O.isFlatfileEnabled() ) {
      byte[] buf = new byte[bb.remaining()];
      bb.get(buf);

      synchronized( H2O.class ) { // Sync'd so single-thread socket create/destroy
        assert H2O.CLOUD_MULTICAST_IF != null;
        try {
          if( H2O.CLOUD_MULTICAST_SOCKET == null ) {
            H2O.CLOUD_MULTICAST_SOCKET = new MulticastSocket();
            // Allow multicast traffic to go across subnets
            H2O.CLOUD_MULTICAST_SOCKET.setTimeToLive(2);
            H2O.CLOUD_MULTICAST_SOCKET.setNetworkInterface(H2O.CLOUD_MULTICAST_IF);
          }
          // Make and send a packet from the buffer
          H2O.CLOUD_MULTICAST_SOCKET.send(new DatagramPacket(buf, buf.length, H2O.CLOUD_MULTICAST_GROUP, H2O.CLOUD_MULTICAST_PORT));
        } catch( Exception e ) {  // On any error from anybody, close all sockets & re-open
          // No error on multicast fail: common occurrance for laptops coming
          // awake from sleep.
          if( H2O.CLOUD_MULTICAST_SOCKET != null )
            try { H2O.CLOUD_MULTICAST_SOCKET.close(); }
            catch( Exception e2 ) { Log.err("Got",e2); }
            finally { H2O.CLOUD_MULTICAST_SOCKET = null; }
        }
      }
    } else {
      // Multicast Simulation
      // The multicast simulation is little bit tricky. To achieve union of all
      // specified nodes' flatfiles (via option -flatfile), the simulated
      // multicast has to send packets not only to nodes listed in the node's
      // flatfile (H2O.STATIC_H2OS), but also to all cloud members (they do not
      // need to be specified in THIS node's flatfile but can be part of cloud
      // due to another node's flatfile).
      //
      // Furthermore, the packet have to be send also to Paxos proposed members
      // to achieve correct functionality of Paxos.  Typical situation is when
      // this node receives a Paxos heartbeat packet from a node which is not
      // listed in the node's flatfile -- it means that this node is listed in
      // another node's flatfile (and wants to create a cloud).  Hence, to
      // allow cloud creation, this node has to reply.
      //
      // Typical example is:
      //    node A: flatfile (B)
      //    node B: flatfile (C), i.e., A -> (B), B-> (C), C -> (A)
      //    node C: flatfile (A)
      //    Cloud configuration: (A, B, C)
      //

      // Hideous O(n) algorithm for broadcast - avoid the memory allocation in
      // this method (since it is heavily used)

      Set<H2ONode> nodes = H2O.getFlatfile();
      nodes.addAll(water.Paxos.PROPOSED.values());
      bb.mark();
      for( H2ONode h2o : nodes ) {
        if(h2o.isRemovedFromCloud()) {
          continue;
        }
        bb.reset();
        h2o.sendMessage(bb, priority);
      }
    }
  }


  /**
   * Read a set of Nodes from a file. Format is:
   *
   * name/ip_address:port
   * - name is unused and optional
   * - port is optional
   * - leading '#' indicates a comment
   *
   * For example:
   *
   * 10.10.65.105:54322
   * # disabled for testing
   * # 10.10.65.106
   * /10.10.65.107
   * # run two nodes on 108
   * 10.10.65.108:54322
   * 10.10.65.108:54325
   */
  private static HashSet<H2ONode> parseFlatFile( String fname ) {
    if( fname == null ) return null;
    File f = new File(fname);
    if( !f.exists() ) {
      Log.warn("-flatfile specified but not found: " + fname);
      return null; // No flat file
    }
    HashSet<H2ONode> h2os = new HashSet<>();
    List<FlatFileEntry> list = parseFlatFile(f);
    for(FlatFileEntry entry : list)
      h2os.add(H2ONode.intern(entry.inet, entry.port+H2O.ARGS.port_offset));// use the UDP port here
    return h2os;
  }

  static HashSet<H2ONode> parseFlatFileFromString( String s ) {
    HashSet<H2ONode> h2os = new HashSet<>();
    InputStream is = new ByteArrayInputStream(StringUtils.bytesOf(s));
    List<FlatFileEntry> list = parseFlatFile(is);
    for(FlatFileEntry entry : list)
      h2os.add(H2ONode.intern(entry.inet, entry.port+H2O.ARGS.port_offset));// use the UDP port here
    return h2os;
  }

  static class FlatFileEntry {
    InetAddress inet;
    int port;
  }

  static List<FlatFileEntry> parseFlatFile( File f ) {
    InputStream is = null;
    try {
      is = new FileInputStream(f);
    }
    catch (Exception e) { H2O.die(e.toString()); }
    return parseFlatFile(is);
  }

  static List<FlatFileEntry> parseFlatFile( InputStream is ) {
    List<FlatFileEntry> list = new ArrayList<>();
    BufferedReader br = null;
    int port = H2O.ARGS.port;
    try {
      br = new BufferedReader(new InputStreamReader(is));
      String strLine = null;
      while( (strLine = br.readLine()) != null) {
        strLine = strLine.trim();
        // be user friendly and skip comments and empty lines
        if (strLine.startsWith("#") || strLine.isEmpty()) continue;

        String ip = null, portStr = null;
        int slashIdx = strLine.indexOf('/');
        int colonIdx = strLine.lastIndexOf(':'); // Get the last index in case it is IPv6 address
        if( slashIdx == -1 && colonIdx == -1 ) {
          ip = strLine;
        } else if( slashIdx == -1 ) {
          ip = strLine.substring(0, colonIdx);
          portStr = strLine.substring(colonIdx+1);
        } else if( colonIdx == -1 ) {
          ip = strLine.substring(slashIdx+1);
        } else if( slashIdx > colonIdx ) {
          H2O.die("Invalid format, must be [name/]ip[:port], not '"+strLine+"'");
        } else {
          ip = strLine.substring(slashIdx+1, colonIdx);
          portStr = strLine.substring(colonIdx+1);
        }

        InetAddress inet = InetAddress.getByName(ip);
        if( portStr!=null && !portStr.equals("") ) {
          try {
            port = Integer.decode(portStr);
          } catch( NumberFormatException nfe ) {
            H2O.die("Invalid port #: "+portStr);
          }
        }
        FlatFileEntry entry = new FlatFileEntry();
        entry.inet = inet;
        entry.port = port;
        list.add(entry);
      }
    } catch( Exception e ) { H2O.die(e.toString()); }
    finally { 
      if( br != null ) try { br.close(); } catch( IOException ie ) { }
    }
    return list;
  }

}

