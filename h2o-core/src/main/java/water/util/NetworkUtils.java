package water.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;

import static water.util.ArrayUtils.toByteArray;

/**
 * Utilities to support networking code.
 *
 * See:
 *  - http://www.tcpipguide.com/free/diagrams/ipv6scope.png for IPV6 Scope explanations
 *  - https://en.wikipedia.org/wiki/Multicast_address
 */
public class NetworkUtils {

  // Google DNS https://developers.google.com/speed/public-dns/docs/using#important_before_you_start
  public static byte[] GOOGLE_DNS_IPV4 = new byte[] {8, 8 , 8, 8};
  public static byte[] GOOGLE_DNS_IPV6 = toByteArray(new int[] {0x20, 0x01, 0x48, 0x60, 0x48, 0x60, 0x00, 0x00,
                                                                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x88, 0x88 });

  /** Override IPv6 scope by a defined value */
  private static String H2O_SYSTEM_SCOPE_PARAM = "sys.ai.h2o.network.ipv6.scope";

  /** Define timeout in ms to figure out if local ip is reachable */
  private static String H2O_SYSTEM_LOCAL_IP_PING_TIMEOUT = "sys.ai.h2o.network.ip.ping.timeout";

  // See IPv6 Multicast scopes:
  public static long SCOPE_IFACE_LOCAL  = 0x0001000000000000L;
  public static long SCOPE_LINK_LOCAL   = 0x0002000000000000L;
  public static long SCOPE_SITE_LOCAL   = 0x0005000000000000L;
  public static long SCOPE_ORG_LOCAL    = 0x0008000000000000L;
  public static long SCOPE_GLOBAL_LOCAL = 0x000e000000000000L;
  public static long SCOPE_MASK = ~0x000f000000000000L;

  public static int[] IPV4_MULTICAST_ALLOCATION_RANGE = new int[] { /* low */ 0xE1000000, /* high */ 0xEFFFFFFF };

  // The preconfigured scopes of IPv6 multicast groups - see https://en.wikipedia.org/wiki/Multicast_address#IPv6
  public static long[][] IPV6_MULTICAST_ALLOCATION_RANGE = new long[][] { /* low  */ new long[] {0xff08000000000000L, 0x0L}, // T-flag for transient, 8 = organization scope (will be replace by real scope
                                                                          /* high */ new long[] {0xff08FFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFFL}};

  public static boolean isIPv6Preferred() {
    return Boolean.parseBoolean(System.getProperty("java.net.preferIPv6Addresses", "false"))
        || (System.getProperty("java.net.preferIPv4Addresses") != null && !Boolean.parseBoolean(System.getProperty("java.net.preferIPv4Addresses")));
  }

  public static boolean isIPv4Preferred() {
    return Boolean.parseBoolean(System.getProperty("java.net.preferIPv4Addresses", "true"));
  }

  public static InetAddress getIPv4MulticastGroup(int hash) throws UnknownHostException {
    return getIPv4MulticastGroup(hash, IPV4_MULTICAST_ALLOCATION_RANGE[0], IPV4_MULTICAST_ALLOCATION_RANGE[1]);
  }

  public static InetAddress getIPv4MulticastGroup(int hash, int lowIp, int highIp) throws UnknownHostException {
    hash = hash & 0x7fffffff; // delete sign
    int port = (hash % (highIp-lowIp+1)) + lowIp;
    byte[] ip = new byte[4];
    for( int i=0; i<4; i++ )
      ip[i] = (byte)(port>>>((3-i)<<3));
    return InetAddress.getByAddress(ip);
  }

  public static InetAddress getIPv6MulticastGroup(int hash, long scope) throws UnknownHostException {
    return getIPv6MulticastGroup(hash,
                                 IPV6_MULTICAST_ALLOCATION_RANGE[0],
                                 IPV6_MULTICAST_ALLOCATION_RANGE[1], scope);
  }

  public static InetAddress getIPv6MulticastGroup(int hash, long[] lowIp, long[] highIp, long scope) throws UnknownHostException {
    hash = hash & 0x7fffffff; // delete sign
    byte[] ip = ArrayUtils.toByteArray(((lowIp[0] & SCOPE_MASK) | scope) | hash, lowIp[1] | hash); // Simple encoding of the hash into multicast group
    return InetAddress.getByAddress(ip);
  }

  public static int getMulticastPort(int hash) {
    hash = hash & 0x7fffffff; // delete sign
    int port = (hash % (0xF0000000-0xE1000000))+0xE1000000;
    return port>>>16;
  }

  /** Return IPv6 scope for given IPv6 address. */
  public static long getIPv6Scope(InetAddress ip) {
    Long value = OSUtils.getLongProperty(H2O_SYSTEM_SCOPE_PARAM) != null
                 ? OSUtils.getLongProperty(H2O_SYSTEM_SCOPE_PARAM)
                 : OSUtils.getLongProperty(H2O_SYSTEM_SCOPE_PARAM, 16);
    if (value != null && ArrayUtils.equalsAny(value,
                                              SCOPE_IFACE_LOCAL, SCOPE_LINK_LOCAL,
                                              SCOPE_SITE_LOCAL, SCOPE_ORG_LOCAL,
                                              SCOPE_GLOBAL_LOCAL)) return value;
    if (ip.isLoopbackAddress()) return SCOPE_IFACE_LOCAL;
    if (ip.isLinkLocalAddress()) return SCOPE_LINK_LOCAL;
    if (ip.isSiteLocalAddress()) return SCOPE_SITE_LOCAL;
    return SCOPE_ORG_LOCAL;
  }

  public static boolean isUp(NetworkInterface iface) {
    try { return iface.isUp(); } catch (SocketException e) {
      return false;
    }
  }

  public static boolean isReachable(NetworkInterface iface, InetAddress address, int timeout) {
    try {
      return address.isReachable(iface, 0, timeout);
    } catch (IOException e) {
      return false;
    }
  }

  public static int getLocalIpPingTimeout() {
    String value = System.getProperty(H2O_SYSTEM_LOCAL_IP_PING_TIMEOUT, "150" /* ms */);
    try {
      return Integer.valueOf(value);
    } catch (NumberFormatException e) {
      return 150;
    }
  }
}
