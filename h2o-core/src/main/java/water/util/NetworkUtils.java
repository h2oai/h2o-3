package water.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Utilities to support networking code.
 */
public class NetworkUtils {

  public static int[] IPV4_MULTICAST_ALLOCATION_RANGE = new int[] { /* low */ 0xE1000000, /* high */ 0xEFFFFFFF };

  // The preconfigured scopes of IPv6 multicast groups - see https://en.wikipedia.org/wiki/Multicast_address#IPv6
  public static long[][] IPV6_MULTICAST_ALLOCATION_RANGE = new long[][] { /* low  */ new long[] {0xff18000000000000L, 0x0L}, // T-flag for transient, 8 = organization scope
                                                                          /* high */ new long[] {0xff18FFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFFL}};

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

  public static InetAddress getIPv6MulticastGroup(int hash) throws UnknownHostException {
    return getIPv6MulticastGroup(hash, IPV6_MULTICAST_ALLOCATION_RANGE[0], IPV6_MULTICAST_ALLOCATION_RANGE[1]);
  }

  public static InetAddress getIPv6MulticastGroup(int hash, long[] lowIp, long[] highIp) throws UnknownHostException {
    hash = hash & 0x7fffffff; // delete sign
    byte[] ip = ArrayUtils.toByteArray(lowIp[0] | hash, lowIp[1] | hash); // Simple encoding of the hash into multicast group
    return InetAddress.getByAddress(ip);
  }

  public static int getMulticastPort(int hash) {
    hash = hash & 0x7fffffff; // delete sign
    int port = (hash % (0xF0000000-0xE1000000))+0xE1000000;
    return port>>>16;
  }

}
