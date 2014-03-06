package water;

import java.net.*;

public class H2ONode { 
  public static class HeartBeat {
    long _jar_md5;
    long _cloud_hash;
  }
  public int _unique_idx; // Dense integer index, skipping 0.  NOT cloud-wide unique.
  public long _last_heard_from; // Time in msec since we last heard from this Node
  public boolean _announcedLostContact;  // True if heartbeat published a no-contact msg
  public volatile HeartBeat _heartbeat;  // My health info.  Changes 1/sec.
  public int _tcp_readers;               // Count of started TCP reader threads
  public boolean _node_healthy;

  // A JVM is uniquely named by machine IP address and port#
  public H2Okey _key;
  public static final class H2Okey extends InetSocketAddress implements Comparable {
    final int _ipv4;     // cheapo ipv4 address
    public H2Okey(InetAddress inet, int port) {
      super(inet,port);
      byte[] b = inet.getAddress();
      _ipv4 = ((b[0]&0xFF)<<0)+((b[1]&0xFF)<<8)+((b[2]&0xFF)<<16)+((b[3]&0xFF)<<24);
    }
    public int htm_port() { return getPort()-1; }
    public int udp_port() { return getPort()  ; }
    public String toString() { return getAddress()+":"+htm_port(); }
    //AutoBuffer write( AutoBuffer ab ) {
    //  return ab.put4(_ipv4).put2((char)udp_port());
    //}
    //static H2Okey read( AutoBuffer ab ) {
    //  InetAddress inet;
    //  try { inet = InetAddress.getByAddress(ab.getA1(4)); }
    //  catch( UnknownHostException e ) { throw  Log.errRTExcept(e); }
    //  int port = ab.get2();
    //  return new H2Okey(inet,port);
    //}
    // Canonical ordering based on inet & port
    @Override public int compareTo( Object x ) {
      if( x == null ) return -1;   // Always before null
      H2Okey key = ((H2ONode)x)._key;
      if( key == this ) return 0;
      // Must be unsigned long-math, or overflow will make a broken sort
      long res = (_ipv4&0xFFFFFFFFL) - (key._ipv4&0xFFFFFFFFL);
      if( res != 0 ) return res < 0 ? -1 : 1;
      return udp_port() - key.udp_port();
    }
  }


  // Create and/or re-use an H2ONode.  Each gets a unique dense index, and is
  // *interned*: there is only one per InetAddress.
  public static final H2ONode intern( H2Okey key ) {
    return new H2ONode();
  }

  public static final H2ONode intern( InetAddress ip, int port ) { return intern(new H2Okey(ip,port)); }

  public static final H2ONode intern( int ip, int port ) {
    return new H2ONode();
  }

  public static H2ONode self( InetAddress inet ) {
    return new H2ONode();
  }
}
