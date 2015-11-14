package water.init;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import water.H2O;
import water.H2ONode;
import water.H2ONode.H2OSmallMessage;
import water.JettyHTTPD;
import water.util.Log;

/**
 * Data structure for holding network info specified by the user on the command line.
 */
public class NetworkInit {
  int _o1;
  int _o2;
  int _o3;
  int _o4;
  int _bits;

  /**
   * Create object from user specified data.
   * @param o1 First octet
   * @param o2 Second octet
   * @param o3 Third octet
   * @param o4 Fourth octet
   * @param bits Bits on the left to compare
   */
  NetworkInit(int o1, int o2, int o3, int o4, int bits) {
    _o1 = o1;
    _o2 = o2;
    _o3 = o3;
    _o4 = o4;
    _bits = bits;
  }

  private boolean oValid(int o) { return 0 <= o && o <= 255;  }
  private boolean valid() {
    if (! (oValid(_o1))) return false;
    if (! (oValid(_o2))) return false;
    if (! (oValid(_o3))) return false;
    if (! (oValid(_o4))) return false;
    return 0 <= _bits && _bits <= 32;
  }

  /**
   * Test if an internet address lives on this user specified network.
   * @param ia Address to test.
   * @return true if the address is on the network; false otherwise.
   */
  boolean inetAddressOnNetwork(InetAddress ia) {
    int i = (_o1 << 24) |
            (_o2 << 16) |
            (_o3 << 8) |
            (_o4 << 0);

    byte[] barr = ia.getAddress();
    if (barr.length != 4) {
      return false;
    }

    int j = (((int)barr[0] & 0xff) << 24) |
            (((int)barr[1] & 0xff) << 16) |
            (((int)barr[2] & 0xff) << 8) |
            (((int)barr[3] & 0xff) << 0);

    // Do mask math in 64-bit to handle 32-bit wrapping cases.
    long mask1 = ((long)1 << (32 - _bits));
    long mask2 = mask1 - 1;
    long mask3 = ~mask2;
    int mask4 = (int) (mask3 & 0xffffffff);

    return (i & mask4) == (j & mask4);

  }

  /**
   * Finds inetaddress for specified -ip parameter or
   * guess address if parameter is not specified.
   *
   * It also computes address for web server if -web-ip parameter is passed.
   *
   * @return inet address for this node.
   */
  public static InetAddress findInetAddressForSelf() throws Error {
    if( H2O.SELF_ADDRESS != null) return H2O.SELF_ADDRESS;
    if ((H2O.ARGS.ip != null) && (H2O.ARGS.network != null)) {
      Log.err("ip and network options must not be used together");
      H2O.exit(-1);
    }

    ArrayList<NetworkInit> networkList = NetworkInit.calcArrayList(H2O.ARGS.network);
    if (networkList == null) {
      Log.err("Exiting.");
      H2O.exit(-1);
    }

    // Get a list of all valid IPs on this machine.
    ArrayList<InetAddress> ips = calcPrioritizedInetAddressList();

    InetAddress local = null;   // My final choice

    // Check for an "-ip xxxx" option and accept a valid user choice; required
    // if there are multiple valid IP addresses.
    if (H2O.ARGS.ip != null) {
      local = getInetAddress(H2O.ARGS.ip, ips);
    } else if (networkList.size() > 0) {
      // Return the first match from the list, if any.
      // If there are no matches, then exit.
      Log.info("Network list was specified by the user.  Searching for a match...");
      ArrayList<InetAddress> validIps = new ArrayList();
      for( InetAddress ip : ips ) {
        Log.info("    Considering " + ip.getHostAddress() + " ...");
        for ( NetworkInit n : networkList ) {
          if (n.inetAddressOnNetwork(ip)) {
            Log.info("    Matched " + ip.getHostAddress());
            return (H2O.SELF_ADDRESS = ip);
          }
        }
      }

      Log.err("No interface matches the network list from the -network option.  Exiting.");
      H2O.exit(-1);
    }
    else {
      // No user-specified IP address.  Attempt auto-discovery.  Roll through
      // all the network choices on looking for a single Inet4.
      ArrayList<InetAddress> validIps = new ArrayList();
      for( InetAddress ip : ips ) {
        // make sure the given IP address can be found here
        if( ip instanceof Inet4Address &&
            !ip.isLoopbackAddress() &&
            !ip.isLinkLocalAddress() ) {
          validIps.add(ip);
        }
      }
      if( validIps.size() == 1 ) {
        local = validIps.get(0);
      } else {
        local = guessInetAddress(validIps);
      }
    }

    // The above fails with no network connection, in that case go for a truly
    // local host.
    if( local == null ) {
      try {
        Log.warn("Failed to determine IP, falling back to localhost.");
        // set default ip address to be 127.0.0.1 /localhost
        local = InetAddress.getByName("127.0.0.1");
      } catch( UnknownHostException e ) { 
        Log.throwErr(e);
      }
    }
    return (H2O.SELF_ADDRESS = local);
  }

  private static InetAddress guessInetAddress(List<InetAddress> ips) {
    String m = "Multiple local IPs detected:\n";
    for(InetAddress ip : ips) m+="  " + ip;
    m+="\nAttempting to determine correct address...\n";
    Socket s = null;
    try {
      // using google's DNS server as an external IP to find
      s = new Socket("8.8.8.8", 53);
      m+="Using " + s.getLocalAddress() + "\n";
      return s.getLocalAddress();
    } catch( java.net.SocketException se ) {
      return null;           // No network at all?  (Laptop w/wifi turned off?)
    } catch( Throwable t ) {
      Log.err(t);
      return null;
    } finally {
      Log.warn(m);
      if( s != null ) try { s.close(); } catch( java.io.IOException ie ) { }
    }
  }

  private static InetAddress getInetAddress(String ip, List<InetAddress> allowedIps) {
    InetAddress addr = null;

    if (ip != null) {
      try {
        addr = InetAddress.getByName(ip);
      } catch (UnknownHostException e) {
        Log.err(e);
        H2O.exit(-1);
      }
      if (!(addr instanceof Inet4Address)) {
        Log.warn("Only IP4 addresses allowed.");
        H2O.exit(-1);
      }
      if (allowedIps != null) {
        if (!allowedIps.contains(addr)) {
          Log.warn("IP address not found on this machine");
          H2O.exit(-1);
        }
      }
    }

    return addr;
  }

  /**
   * Return a list of interfaces sorted by importance (most important first).
   * This is the order we want to test for matches when selecting an interface.
   */
  private static ArrayList<NetworkInterface> calcPrioritizedInterfaceList() {
    ArrayList<NetworkInterface> networkInterfaceList = null;
    try {
      Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
      ArrayList<NetworkInterface> tmpList = Collections.list(nis);

      Comparator<NetworkInterface> c = new Comparator<NetworkInterface>() {
        @Override public int compare(NetworkInterface lhs, NetworkInterface rhs) {
          // Handle null inputs.
          if ((lhs == null) && (rhs == null)) { return 0; }
          if (lhs == null) { return 1; }
          if (rhs == null) { return -1; }

          // If the names are equal, then they are equal.
          if (lhs.getName().equals (rhs.getName())) { return 0; }

          // If both are bond drivers, choose a precedence.
          if (lhs.getName().startsWith("bond") && (rhs.getName().startsWith("bond"))) {
            Integer li = lhs.getName().length();
            Integer ri = rhs.getName().length();

            // Bond with most number of characters is always highest priority.
            if (li.compareTo(ri) != 0) {
              return li.compareTo(ri);
            }

            // Otherwise, sort lexicographically by name.
            return lhs.getName().compareTo(rhs.getName());
          }

          // If only one is a bond driver, give that precedence.
          if (lhs.getName().startsWith("bond")) { return -1; }
          if (rhs.getName().startsWith("bond")) { return 1; }

          // Everything that isn't a bond driver is equal.
          return 0;
        }
      };

      Collections.sort(tmpList, c);
      networkInterfaceList = tmpList;
    } catch( SocketException e ) { Log.err(e); }

    return networkInterfaceList;
  }

  /**
   * Return a list of internet addresses sorted by importance (most important first).
   * This is the order we want to test for matches when selecting an internet address.
   */
  static ArrayList<java.net.InetAddress> calcPrioritizedInetAddressList() {
    ArrayList<java.net.InetAddress> ips = new ArrayList<>();
    {
      ArrayList<NetworkInterface> networkInterfaceList = calcPrioritizedInterfaceList();

      for (NetworkInterface ni : networkInterfaceList) {
        Enumeration<InetAddress> ias = ni.getInetAddresses();
        while (ias.hasMoreElements()) {
          InetAddress ia;
          ia = ias.nextElement();
          ips.add(ia);
          Log.info("Possible IP Address: " + ni.getName() + " (" + ni.getDisplayName() + "), " + ia.getHostAddress());
        }
      }
    }

    return ips;
  }

  static ArrayList<NetworkInit> calcArrayList(String networkOpt) {
    ArrayList<NetworkInit> networkList = new ArrayList<>();

    if (networkOpt == null) return networkList;

    String[] networks;
    if (networkOpt.contains(",")) {
      networks = networkOpt.split(",");
    }
    else {
      networks = new String[1];
      networks[0] = networkOpt;
    }

    for (String n : networks) {
      Pattern p = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+)/(\\d+)");
      Matcher m = p.matcher(n);
      boolean b = m.matches();
      if (!b) {
        Log.err("network invalid: " + n);
        return null;
      }
      assert (m.groupCount() == 5);
      int o1 = Integer.parseInt(m.group(1));
      int o2 = Integer.parseInt(m.group(2));
      int o3 = Integer.parseInt(m.group(3));
      int o4 = Integer.parseInt(m.group(4));
      int bits = Integer.parseInt(m.group(5));

      NetworkInit usn = new NetworkInit(o1, o2, o3, o4, bits);
      if (!usn.valid()) {
        Log.err("network invalid: " + n);
        return null;
      }

      networkList.add(usn);
    }

    return networkList;
  }

  public static DatagramChannel _udpSocket;
  public static ServerSocketChannel _tcpSocketBig;
  public static ServerSocketChannel _tcpSocketSmall;

  // Default NIO Datagram channel
  public static DatagramChannel CLOUD_DGRAM;

  // Parse arguments and set cloud name in any case. Strip out "-name NAME"
  // and "-flatfile <filename>". Ignore the rest. Set multi-cast port as a hash
  // function of the name. Parse node ip addresses from the filename.
  public static void initializeNetworkSockets( ) {
    // Assign initial ports
    H2O.API_PORT = H2O.ARGS.port == 0 ? H2O.ARGS.baseport : H2O.ARGS.port;

    // Late instantiation of Jetty object, if needed.
    if (H2O.getJetty() == null) {
      H2O.setJetty(new JettyHTTPD());
    }

    // API socket is only used to find opened port on given ip.
    ServerSocket apiSocket = null;

    while (true) {
      H2O.H2O_PORT = H2O.API_PORT+1;
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
                      : new ServerSocket(H2O.API_PORT, -1, getInetAddress(H2O.ARGS.web_ip, null));
          apiSocket.setReuseAddress(true);
        }
        // Bind to the UDP socket
        _udpSocket = DatagramChannel.open();
        _udpSocket.socket().setReuseAddress(true);
        InetSocketAddress isa = new InetSocketAddress(H2O.SELF_ADDRESS, H2O.H2O_PORT);
        _udpSocket.socket().bind(isa);
        // Bind to the TCP socket also
        _tcpSocketBig = ServerSocketChannel.open();
        _tcpSocketBig.socket().setReceiveBufferSize(water.AutoBuffer.TCP_BUF_SIZ);
        _tcpSocketBig.socket().bind(isa);

        if(!H2O.ARGS.useUDP) {
          _tcpSocketSmall = ServerSocketChannel.open();
          _tcpSocketBig.socket().setReceiveBufferSize(water.AutoBuffer.TCP_BUF_SIZ);
          InetSocketAddress isa2 = new InetSocketAddress(H2O.SELF_ADDRESS, H2O.H2O_PORT+1);
          _tcpSocketSmall.socket().bind(isa2);
        }
        // Warning: There is a ip:port race between socket close and starting Jetty
        if (! H2O.ARGS.disable_web) {
          apiSocket.close();
          H2O.getJetty().start(H2O.ARGS.web_ip, H2O.API_PORT);
        }
        break;
      } catch (Exception e) {
        if( apiSocket != null ) try { apiSocket.close(); } catch( IOException ohwell ) { Log.err(ohwell); }
        if( _udpSocket != null ) try { _udpSocket.close(); } catch( IOException ie ) { }
        if( _tcpSocketBig != null ) try { _tcpSocketBig.close(); } catch( IOException ie ) { }
        if( _tcpSocketSmall != null ) try { _tcpSocketSmall.close(); } catch( IOException ie ) { }
        apiSocket = null;
        _udpSocket = null;
        _tcpSocketBig = null;
        _tcpSocketSmall = null;
        if( H2O.ARGS.port != 0 )
          H2O.die("On " + H2O.SELF_ADDRESS +
              " some of the required ports " + H2O.ARGS.port +
              ", " + (H2O.ARGS.port+1) +
              " are not available, change -port PORT and try again.");
      }
      H2O.API_PORT += 2;
    }
    H2O.SELF = H2ONode.self(H2O.SELF_ADDRESS);
    Log.info("Internal communication uses port: ", H2O.H2O_PORT, "\n" +
             "Listening for HTTP and REST traffic on " + H2O.getJetty().getScheme() + "://", H2O.getIpPortString() + "/");
    try { Log.debug("Interface MTU: ",  (NetworkInterface.getByInetAddress(H2O.SELF_ADDRESS)).getMTU());
    } catch (SocketException se) { Log.debug("No MTU due to SocketException. "+se.toString()); }

    String embeddedConfigFlatfile = null;
    AbstractEmbeddedH2OConfig ec = H2O.getEmbeddedH2OConfig();
    if (ec != null) {
      ec.notifyAboutEmbeddedWebServerIpPort (H2O.SELF_ADDRESS, H2O.API_PORT);
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
      H2O.STATIC_H2OS = parseFlatFileFromString(embeddedConfigFlatfile);
    else 
      H2O.STATIC_H2OS = parseFlatFile(H2O.ARGS.flatfile);

    // Multi-cast ports are in the range E1.00.00.00 to EF.FF.FF.FF
    int hash = H2O.ARGS.name.hashCode()&0x7fffffff;
    int port = (hash % (0xF0000000-0xE1000000))+0xE1000000;
    byte[] ip = new byte[4];
    for( int i=0; i<4; i++ )
      ip[i] = (byte)(port>>>((3-i)<<3));
    try {
      H2O.CLOUD_MULTICAST_GROUP = InetAddress.getByAddress(ip);
    } catch( UnknownHostException e ) { Log.throwErr(e); }
    H2O.CLOUD_MULTICAST_PORT = (port>>>16);
  }

  // Multicast send-and-close.  Very similar to udp_send, except to the
  // multicast port (or all the individuals we can find, if multicast is
  // disabled).
  public static void multicast( ByteBuffer bb , int priority) {
    try { multicast2(bb, priority); }
    catch (Exception ie) {}
  }

  static private void multicast2( ByteBuffer bb, int priority ) {
    if( H2O.STATIC_H2OS == null ) {
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
          H2O.CLOUD_MULTICAST_SOCKET.send(new DatagramPacket(buf, buf.length, H2O.CLOUD_MULTICAST_GROUP,H2O.CLOUD_MULTICAST_PORT));
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
      HashSet<H2ONode> nodes = (HashSet<H2ONode>)H2O.STATIC_H2OS.clone();
      nodes.addAll(water.Paxos.PROPOSED.values());
      bb.mark();
      H2OSmallMessage msg = H2O.ARGS.useUDP?null:H2OSmallMessage.make(bb,priority);
      for( H2ONode h2o : nodes ) {
        try {
          if(H2O.ARGS.useUDP) {
            bb.reset();
            CLOUD_DGRAM.send(bb, h2o._key);
          } else {
            h2o.sendMessage(msg);
          }
        } catch( IOException e ) {
          Log.warn("Multicast Error to "+h2o, e);
        }
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
      h2os.add(H2ONode.intern(entry.inet, entry.port+1));// use the UDP port here
    return h2os;
  }

  static HashSet<H2ONode> parseFlatFileFromString( String s ) {
    HashSet<H2ONode> h2os = new HashSet<>();
    InputStream is = new ByteArrayInputStream(s.getBytes());
    List<FlatFileEntry> list = parseFlatFile(is);
    for(FlatFileEntry entry : list)
      h2os.add(H2ONode.intern(entry.inet, entry.port+1));// use the UDP port here
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
        int colonIdx = strLine.indexOf(':');
        if( slashIdx == -1 && colonIdx == -1 ) {
          ip = strLine;
        } else if( slashIdx == -1 ) {
          ip = strLine.substring(0, colonIdx);
          portStr = strLine.substring(colonIdx+1);
        } else if( colonIdx == -1 ) {
          ip = strLine.substring(slashIdx+1);
        } else if( slashIdx > colonIdx ) {
          H2O.die("Invalid format, must be name/ip[:port], not '"+strLine+"'");
        } else {
          ip = strLine.substring(slashIdx+1, colonIdx);
          portStr = strLine.substring(colonIdx+1);
        }

        InetAddress inet = InetAddress.getByName(ip);
        if( !(inet instanceof Inet4Address) )
          H2O.die("Only IP4 addresses allowed: given " + ip);
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

