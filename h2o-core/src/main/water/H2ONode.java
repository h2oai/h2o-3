package water;

import java.util.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.channels.SocketChannel;
import water.nbhm.*;
import water.util.Log;
import java.nio.channels.DatagramChannel;

/**
 * A <code>Node</code> in an <code>H2O</code> Cloud.
 * Basically a worker-bee with CPUs, Memory and Disk.
 * One of this is the self-Node, but the rest are remote Nodes.
 *
 * @author <a href="mailto:cliffc@0xdata.com"></a>
 * @version 1.0
 */

public class H2ONode extends Iced implements Comparable {
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

  public final int ip4() { return _key._ipv4; }

  // These are INTERN'd upon construction, and are uniquely numbered within the
  // same run of a JVM.  If a remote Node goes down, then back up... it will
  // come back with the SAME IP address, and the same unique_idx and history
  // relative to *this* Node.  They can be compared with pointer-equality.  The
  // unique idx is used to know which remote Nodes have cached which Keys, even
  // if the Home#/Replica# change for a Key due to an unrelated change in Cloud
  // membership.  The unique_idx is *per Node*; not all Nodes agree on the same
  // indexes.
  private H2ONode( H2Okey key, int unique_idx ) {
    _key = key;
    _unique_idx = unique_idx;
    _last_heard_from = System.currentTimeMillis();
    _heartbeat = new HeartBeat();
    _node_healthy = true;
  }

  // ---------------
  // A dense integer index for every unique IP ever seen, since the JVM booted.
  // Used to track "known replicas" per-key across Cloud change-ups.  Just use
  // an array-of-H2ONodes, and a limit of 255 unique H2ONodes
  static private final NonBlockingHashMap<H2Okey,H2ONode> INTERN = new NonBlockingHashMap<H2Okey,H2ONode>();
  static private final AtomicInteger UNIQUE = new AtomicInteger(1);
  static public H2ONode IDX[] = new H2ONode[1];

  // Create and/or re-use an H2ONode.  Each gets a unique dense index, and is
  // *interned*: there is only one per InetAddress.
  public static final H2ONode intern( H2Okey key ) {
    H2ONode h2o = INTERN.get(key);
    if( h2o != null ) return h2o;
    final int idx = UNIQUE.getAndIncrement();
    h2o = new H2ONode(key,idx);
    H2ONode old = INTERN.putIfAbsent(key,h2o);
    if( old != null ) return old;
    synchronized(H2O.class) {
      while( idx >= IDX.length )
        IDX = Arrays.copyOf(IDX,IDX.length<<1);
      IDX[idx] = h2o;
    }
    return h2o;
  }
  public static final H2ONode intern( InetAddress ip, int port ) { return intern(new H2Okey(ip,port)); }

  public static final H2ONode intern( int ip, int port ) {
    byte[] b = new byte[4];
    b[0] = (byte)(ip>> 0);
    b[1] = (byte)(ip>> 8);
    b[2] = (byte)(ip>>16);
    b[3] = (byte)(ip>>24);
    try {
      return intern(InetAddress.getByAddress(b),port);
    } catch( UnknownHostException e ) {
      H2O.die(e);
      return null;
    }
  }

  // Read & return interned from wire
  //@Override public AutoBuffer write( AutoBuffer ab ) { return _key.write(ab); }
  //@Override public H2ONode read( AutoBuffer ab ) { return intern(H2Okey.read(ab));  }
  public H2ONode( ) { }

  // Get a nice Node Name for this Node in the Cloud.  Basically it's the
  // InetAddress we use to communicate to this Node.
  public static H2ONode self(InetAddress local) {
    assert H2O.H2O_PORT != 0;
    try {
      // Figure out which interface matches our IP address
      List<NetworkInterface> matchingIfs = new ArrayList();
      Enumeration<NetworkInterface> netIfs = NetworkInterface.getNetworkInterfaces();
      while( netIfs.hasMoreElements() ) {
        NetworkInterface netIf = netIfs.nextElement();
        Enumeration<InetAddress> addrs = netIf.getInetAddresses();
        while( addrs.hasMoreElements() ) {
          InetAddress addr = addrs.nextElement();
          if( addr.equals(local) ) {
            matchingIfs.add(netIf);
            break;
          }
        }
      }
      switch( matchingIfs.size() ) {
      case 0: H2O.CLOUD_MULTICAST_IF = null; break;
      case 1: H2O.CLOUD_MULTICAST_IF = matchingIfs.get(0); break;
      default:
        String msg = "Found multiple network interfaces for ip address " + local;
        for( NetworkInterface ni : matchingIfs ) {
          msg +="\n\t" + ni;
        }
        msg +="\nUsing " + matchingIfs.get(0) + " for UDP broadcast";
        Log.warn(msg);
        H2O.CLOUD_MULTICAST_IF = matchingIfs.get(0);
      }
    } catch( SocketException e ) {
      Log.throwErr(e);
    }
    try {
      assert water.init.NetworkInit.CLOUD_DGRAM == null;
      water.init.NetworkInit.CLOUD_DGRAM = DatagramChannel.open();
    } catch( Exception e ) {
      Log.throwErr(e);
    }
    return intern(new H2Okey(local,H2O.H2O_PORT));
  }

  // Happy printable string
  @Override public String toString() { return _key.toString (); }
  @Override public int hashCode() { return _key.hashCode(); }
  @Override public boolean equals(Object o) { return _key.equals(o); }
  @Override public int compareTo( Object o) { return _key.compareTo(o); }

  // index of this node in the current cloud... can change at the next cloud.
  public int index() { return H2O.CLOUD.nidx(this); }

  // max memory for this node.
  // no need to ask the (possibly not yet populated) heartbeat if we want to know the local max memory.
  public long get_max_mem() { return this == H2O.SELF ? Runtime.getRuntime().maxMemory() : _heartbeat.get_max_mem(); }

  // ---------------
  // A queue of available TCP sockets
  // Public re-usable TCP socket opened to this node, or null.
  // This is essentially a BlockingQueue/Stack that allows null.
  private SocketChannel _socks[] = new SocketChannel[2];
  private int _socksAvail=_socks.length;
  // Count of concurrent TCP requests both incoming and outgoing
  public static final AtomicInteger TCPS = new AtomicInteger(0);
  public SocketChannel getTCPSocket() throws IOException {
    // Under lock, claim an existing open socket if possible
    synchronized(this) {
      // Limit myself to the number of open sockets from node-to-node
      while( _socksAvail == 0 )
        try { wait(); } catch( InterruptedException ie ) { }
      // Claim an open socket
      SocketChannel sock = _socks[--_socksAvail];
      if( sock != null ) {
        if( sock.isOpen() ) return sock; // Return existing socket!
        // Else its an already-closed socket, lower open TCP count
        assert TCPS.get() > 0;
        TCPS.decrementAndGet();
      }
    }
    // Must make a fresh socket
    SocketChannel sock2 = SocketChannel.open();
    throw H2O.unimpl();
    //sock2.socket().setSendBufferSize(AutoBuffer.BBSIZE);
    //boolean res = sock2.connect( _key );
    //assert res && !sock2.isConnectionPending() && sock2.isBlocking() && sock2.isConnected() && sock2.isOpen();
    //TCPS.incrementAndGet();     // Cluster-wide counting
    //return sock2;
  }
  public synchronized void freeTCPSocket( SocketChannel sock ) {
    assert 0 <= _socksAvail && _socksAvail < _socks.length;
    if( sock != null && !sock.isOpen() ) sock = null;
    _socks[_socksAvail++] = sock;
    assert TCPS.get() > 0;
    if( sock == null ) TCPS.decrementAndGet();
    notify();
  }

  // ---------------
  // The *outgoing* client-side calls; pending tasks this Node wants answered.



  // This Node rebooted recently; we can quit tracking prior work history
  void rebooted() {
    //_work.clear();
  }
}
