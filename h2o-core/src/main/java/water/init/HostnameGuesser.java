package water.init;

import water.util.Log;
import water.util.NetworkUtils;
import water.util.OSUtils;

import java.net.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HostnameGuesser {

  /**
   * Finds InetAddress for a specified IP, guesses the address if parameter is not specified
   * (uses network as a hint if provided).
   *
   * @param ip ip address (optional)
   * @param network network (optional)
   * @return inet address for this node.
   */
  public static InetAddress findInetAddressForSelf(String ip, String network) throws Error {
    if ((ip != null) && (network != null)) {
      throw new HostnameGuessingException("ip and network options must not be used together");
    }

    ArrayList<CIDRBlock> networkList = calcArrayList(network);
    if (networkList == null) {
      throw new HostnameGuessingException("No network found! Exiting.");
    }

    // Get a list of all valid IPs on this machine.
    ArrayList<InetAddress> ias = calcPrioritizedInetAddressList();

    InetAddress local;   // My final choice

    // Check for an "-ip xxxx" option and accept a valid user choice; required
    // if there are multiple valid IP addresses.

    if (ip != null) {
      local = getInetAddress(ip, ias);
    } else if (networkList.size() > 0) {
      // Return the first match from the list, if any.
      // If there are no matches, then exit.
      Log.info("Network list was specified by the user.  Searching for a match...");
      for( InetAddress ia : ias ) {
        Log.info("    Considering " + ia.getHostAddress() + " ...");
        for (CIDRBlock n : networkList) {
          if (n.isInetAddressOnNetwork(ia)) {
            Log.info("    Matched " + ia.getHostAddress());
            return ia;
          }
        }
      }

      throw new HostnameGuessingException("No interface matches the network list from the -network option.  Exiting.");
    } else {
      // No user-specified IP address.  Attempt auto-discovery.  Roll through
      // all the network choices on looking for a single non-local address.
      // Right now the loop up order is: site local address > link local address > fallback loopback
      ArrayList<InetAddress> globalIps = new ArrayList<>();
      ArrayList<InetAddress> siteLocalIps = new ArrayList<>();
      ArrayList<InetAddress> linkLocalIps = new ArrayList<>();

      boolean isIPv6Preferred = NetworkUtils.isIPv6Preferred();
      boolean isIPv4Preferred = NetworkUtils.isIPv4Preferred();
      for( InetAddress ia : ias ) {
        // Make sure the given IP address can be found here
        if(!(ia.isLoopbackAddress() || ia.isAnyLocalAddress())) {
          // Always prefer IPv4
          if (isIPv6Preferred && !isIPv4Preferred && ia instanceof Inet4Address) continue;
          if (isIPv4Preferred && ia instanceof Inet6Address) continue;
          if (ia.isSiteLocalAddress()) siteLocalIps.add(ia);
          if (ia.isLinkLocalAddress()) linkLocalIps.add(ia);
          globalIps.add(ia);
        }
      }
      // The ips were already sorted in priority based way, so use it
      // There is only a single global or site local address, use it
      if (globalIps.size() == 1) {
        local = globalIps.get(0);
      } else if (siteLocalIps.size() == 1) {
        local = siteLocalIps.get(0);
      } else if (linkLocalIps.size() > 0) { // Always use link local address on IPv6
        local = linkLocalIps.get(0);
      } else {
        local = guessInetAddress(siteLocalIps);
      }
    }

    // The above fails with no network connection, in that case go for a truly
    // local host.
    if( local == null ) {
      try {
        Log.warn("Failed to determine IP, falling back to localhost.");
        // set default ip address to be 127.0.0.1 /localhost
        local = NetworkUtils.isIPv6Preferred() && ! NetworkUtils.isIPv4Preferred()
                ? InetAddress.getByName("::1") // IPv6 localhost
                : InetAddress.getByName("127.0.0.1");
      } catch (UnknownHostException e) {
        Log.throwErr(e);
      }
    }
    return local;
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
  private static ArrayList<java.net.InetAddress> calcPrioritizedInetAddressList() {
    ArrayList<java.net.InetAddress> ips = new ArrayList<>();
    ArrayList<NetworkInterface> networkInterfaceList = calcPrioritizedInterfaceList();
    boolean isWindows = OSUtils.isWindows();
    boolean isWsl = OSUtils.isWsl();
    int localIpTimeout = NetworkUtils.getLocalIpPingTimeout();
    for (NetworkInterface nIface : networkInterfaceList) {
      Enumeration<InetAddress> ias = nIface.getInetAddresses();
      if (NetworkUtils.isUp(nIface)) {
        while (ias.hasMoreElements()) {
          InetAddress ia = ias.nextElement();
          // Windows specific code, since isReachable was not able to detect live IPs on Windows8.1 machines
          if (isWindows || isWsl || NetworkUtils.isReachable(null, ia, localIpTimeout /* ms */)) {
            ips.add(ia);
            Log.info("Possible IP Address: ", nIface.getName(), " (", nIface.getDisplayName(), "), ", ia.getHostAddress());
          } else {
            Log.info("Network address/interface is not reachable in 150ms: ", ia, "/", nIface);
          }
        }
      } else {
        Log.info("Network interface is down: ", nIface);
      }
    }

    return ips;
  }

  private static InetAddress guessInetAddress(List<InetAddress> ips) {
    String m = "Multiple local IPs detected:\n";
    for(InetAddress ip : ips) m+="  " + ip;
    m += "\nAttempting to determine correct address...\n";
    Socket s = null;
    try {
      // using google's DNS server as an external IP to find
      s = NetworkUtils.isIPv6Preferred() && !NetworkUtils.isIPv4Preferred()
              ? new Socket(InetAddress.getByAddress(NetworkUtils.GOOGLE_DNS_IPV6), 53)
              : new Socket(InetAddress.getByAddress(NetworkUtils.GOOGLE_DNS_IPV4), 53);
      m += "Using " + s.getLocalAddress() + "\n";
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

  /**
   * Get address for given IP.
   * @param ip  textual representation of IP (host)
   * @param allowedIps  range of allowed IPs on this machine
   * @return IPv4 or IPv6 address which matches given IP and is in specified range
   */
  private static InetAddress getInetAddress(String ip, List<InetAddress> allowedIps) {
    InetAddress addr = null;

    if (ip != null) {
      try {
        addr = InetAddress.getByName(ip);
      } catch (UnknownHostException e) {
        throw new HostnameGuessingException(e);
      }
      if (allowedIps != null) {
        if (!allowedIps.contains(addr)) {
          throw new HostnameGuessingException("IP address not found on this machine");
        }
      }
    }

    return addr;
  }

  private static ArrayList<CIDRBlock> calcArrayList(String networkOpt) {
    ArrayList<CIDRBlock> networkList = new ArrayList<>();

    if (networkOpt == null) return networkList;

    String[] networks;
    if (networkOpt.contains(",")) {
      networks = networkOpt.split(",");
    } else {
      networks = new String[1];
      networks[0] = networkOpt;
    }

    for (String n : networks) {
      CIDRBlock usn = CIDRBlock.parse(n);
      if (n == null || !usn.valid()) {
        Log.err("network invalid: " + n);
        return null;
      }
      networkList.add(usn);
    }

    return networkList;
  }

  /** Representation of a single CIDR block (subnet).
   */
  public static class CIDRBlock {
    /** Patterns to recognize IPv4 CIDR selector (network routing prefix */
    private static Pattern NETWORK_IPV4_CIDR_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+)/(\\d+)");

    /** Patterns to recognize IPv6 CIDR selector (network routing prefix
     * Warning: the pattern recognize full IPv6 specification and does not support short specification via :: replacing block of 0s.
     *
     * From wikipedia: An IPv6 address is represented as eight groups of four hexadecimal digits
     * https://en.wikipedia.org/wiki/IPv6_address#Presentation
     */
    private static Pattern NETWORK_IPV6_CIDR_PATTERN = Pattern.compile("([a-fA-F\\d]+):([a-fA-F\\d]+):([a-fA-F\\d]+):([a-fA-F\\d]+):([a-fA-F\\d]+):([a-fA-F\\d]+):([a-fA-F\\d]+):([a-fA-F\\d]+)/(\\d+)");

    final int[] ip;
    final int bits;

    public static CIDRBlock parse(String cidrBlock) {
      boolean isIPV4 = cidrBlock.contains(".");
      Matcher m = isIPV4 ? NETWORK_IPV4_CIDR_PATTERN.matcher(cidrBlock) : NETWORK_IPV6_CIDR_PATTERN.matcher(cidrBlock);
      boolean b = m.matches();
      if (!b) {
        return null;
      }
      assert (isIPV4 && m.groupCount() == 5 || m.groupCount() == 9);
      int len = isIPV4 ? 4 : 8;
      int[] ipBytes = new int[len];
      for(int i = 0; i < len; i++) {
        ipBytes[i] = isIPV4 ? Integer.parseInt(m.group(i + 1)) : Integer.parseInt(m.group(i + 1), 16);
      }
      // Active bits in CIDR specification
      int bits = Integer.parseInt(m.group(len + 1));

      CIDRBlock usn = isIPV4 ? CIDRBlock.createIPv4(ipBytes, bits)
              : CIDRBlock.createIPv6(ipBytes, bits);
      return usn.valid() ? usn : null;
    }

    public static CIDRBlock createIPv4(int[] ip, int bits) {
      assert ip.length  == 4;
      return new CIDRBlock(ip, bits);
    }

    public static CIDRBlock createIPv6(int[] ip, int bits) {
      assert ip.length  == 8;
      // Expand 8 double octets into 16 octets
      int[] ipLong = new int[16];
      for (int i = 0; i < ip.length; i++) {
        ipLong[2*i + 0] = (ip[i] >> 8) & 0xff;
        ipLong[2*i + 1] = ip[i] & 0xff;
      }
      return new CIDRBlock(ipLong, bits);
    }

    /**
     * Create object from user specified data.
     *
     * @param ip   Array of octets specifying IP (4 for IPv4, 16 for IPv6)
     * @param bits Bits specifying active part of IP
     */
    private CIDRBlock(int[] ip, int bits) {
      assert ip.length == 4 || ip.length == 16 : "Wrong number of bytes to construct IP: " + ip.length;
      this.ip = ip;
      this.bits = bits;
    }

    private boolean validOctet(int o) {
      return 0 <= o && o <= 255;
    }

    private boolean valid() {
      for (int i = 0; i < ip.length; i++) {
        if (!validOctet(ip[i])) return false;
      }
      return 0 <= bits && bits <= ip.length * 8;
    }

    /**
     * Test if an internet address lives on this user specified network.
     *
     * @param ia Address to test.
     * @return true if the address is on the network; false otherwise.
     */
    boolean isInetAddressOnNetwork(InetAddress ia) {
      byte[] ipBytes = ia.getAddress();
      return isInetAddressOnNetwork(ipBytes);
    }

    boolean isInetAddressOnNetwork(byte[] ipBytes) {

      // Compare common byte prefix
      int i = 0;
      for (i = 0; i < bits/8; i++) {
        if (((int) ipBytes[i] & 0xff) != ip[i]) return false;
      }
      // Compare remaining bit-prefix
      int remaining = 0;
      if ((remaining = 8-(bits % 8)) < 8) {
        int mask = ~((1 << remaining) - 1) & 0xff; // Remaining 3bits for comparison: 1110 0000
        return (((int) ipBytes[i] & 0xff) & mask) == (ip[i] & mask);
      }
      return true;
    }
  }

  static class HostnameGuessingException extends RuntimeException {
    private HostnameGuessingException(String message) {
      super(message);
    }
    private HostnameGuessingException(Exception e) {
      super(e);
    }
  }

}
