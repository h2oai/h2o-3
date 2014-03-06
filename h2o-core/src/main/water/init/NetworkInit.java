package water.init;

import water.H2O;
import water.util.Log;
import java.net.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
  public NetworkInit(int o1, int o2, int o3, int o4, int bits) {
    _o1 = o1;
    _o2 = o2;
    _o3 = o3;
    _o4 = o4;
    _bits = bits;
  }

  private boolean oValid(int o) {
    if (o < 0) return false;
    if (o > 255) return false;
    return true;
  }

  private boolean valid() {
    if (! (oValid(_o1))) return false;
    if (! (oValid(_o2))) return false;
    if (! (oValid(_o3))) return false;
    if (! (oValid(_o4))) return false;
    if (_bits < 0) return false;
    if (_bits > 32) return false;
    return true;
  }

  /**
   * Test if an internet address lives on this user specified network.
   * @param ia Address to test.
   * @return true if the address is on the network; false otherwise.
   */
  public boolean inetAddressOnNetwork(InetAddress ia) {
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

    if ((i & mask4) == (j & mask4)) {
      return true;
    }

    return false;
  }

  // Start up an H2O Node and join any local Cloud
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
    InetAddress arg = null;
    if (H2O.ARGS.ip != null) {
      try{
        arg = InetAddress.getByName(H2O.ARGS.ip);
      } catch( UnknownHostException e ) {
        Log.err(e);
        H2O.exit(-1);
      }
      if( !(arg instanceof Inet4Address) ) {
        Log.warn("Only IP4 addresses allowed.");
        H2O.exit(-1);
      }
      if( !ips.contains(arg) ) {
        Log.warn("IP address not found on this machine");
        H2O.exit(-1);
      }
      local = arg;
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
        throw  Log.errRTExcept(e);
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
      if( s != null ) try { s.close(); } catch( java.io.IOException _ ) { }
    }
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
        public int compare(NetworkInterface lhs, NetworkInterface rhs) {
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
  public static ArrayList<java.net.InetAddress> calcPrioritizedInetAddressList() {
    ArrayList<java.net.InetAddress> ips = new ArrayList<java.net.InetAddress>();
    {
      ArrayList<NetworkInterface> networkInterfaceList = calcPrioritizedInterfaceList();

      for (int i = 0; i < networkInterfaceList.size(); i++) {
        NetworkInterface ni = networkInterfaceList.get(i);
        Enumeration<InetAddress> ias = ni.getInetAddresses();
        while( ias.hasMoreElements() ) {
          InetAddress ia;
          ia = ias.nextElement();
          ips.add(ia);
          Log.info("Possible IP Address: " + ni.getName() + " (" + ni.getDisplayName() + "), " + ia.getHostAddress());
        }
      }
    }

    return ips;
  }

  public static ArrayList<NetworkInit> calcArrayList(String networkOpt) {
    ArrayList<NetworkInit> networkList = new ArrayList<NetworkInit>();

    if (networkOpt == null) return networkList;

    String[] networks;
    if (networkOpt.contains(",")) {
      networks = networkOpt.split(",");
    }
    else {
      networks = new String[1];
      networks[0] = networkOpt;
    }

    for (int j = 0; j < networks.length; j++) {
      String n = networks[j];
      Pattern p = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+)/(\\d+)");
      Matcher m = p.matcher(n);
      boolean b = m.matches();
      if (! b) {
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
      if (! usn.valid()) {
        Log.err("network invalid: " + n);
        return null;
      }

      networkList.add(usn);
    }

    return networkList;
  }
}

