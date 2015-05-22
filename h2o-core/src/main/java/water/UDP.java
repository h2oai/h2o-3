package water;

import water.util.UnsafeUtils;

/**
 * Do Something with an incoming UDP packet
 *
 * Classic Single Abstract Method pattern.
 * @author <a href="mailto:cliffc@h2o.ai"></a>
 * @version 1.0
 */

public abstract class UDP {
  /** UDP packet types, and their handlers */
  public static enum udp {
    bad(false,null,(byte)-1), // Do not use the zero packet, too easy to make mistakes
    // Some health-related packet types.  These packets are all stateless, in
    // that we do not need to send any replies back.
    heartbeat     ( true, new UDPHeartbeat(),H2O.MAX_PRIORITY),
    rebooted      ( true, new UDPRebooted() ,H2O.MAX_PRIORITY), // This node has rebooted recently
    timeline      (false, new TimeLine()    ,H2O.MAX_PRIORITY), // Get timeline dumps from across the Cloud

    // All my *reliable* tasks (below), are sent to remote nodes who then ACK
    // back an answer.  To be reliable, I might send the TASK multiple times.
    // To get a reliable answer, the remote might send me multiple ACKs with
    // the same answer every time.  When does the remote know it can quit
    // tracking reply ACKs?  When it recieves an ACKACK.
    ackack(false,new UDPAckAck(),H2O.ACK_ACK_PRIORITY), // a generic ACKACK for a UDP async task
    // In order to unpack an ACK (which contains an arbitrary returned POJO)
    // the reciever might need to fetch a id/class mapping from the leader -
    // while inside an ACK-priority thread holding onto lots of resources
    // (e.g. TCP channel).  Allow the fetch to complete on a higher priority
    // thread.
    fetchack(false,new UDPFetchAck(),H2O.FETCH_ACK_PRIORITY), // a class/id fetch ACK
    ack   (false,new UDPAck (),H2O.ACK_PRIORITY),  // a generic ACK for a UDP async task
    nack  (false,new UDPNack(),H2O.ACK_PRIORITY),  // a generic NACK
    
    // These packets all imply some sort of request/response handshake.
    // We'll hang on to these packets; filter out dup sends and auto-reply
    // identical result ACK packets.
    exec(false,new RPC.RemoteHandler(),H2O.DESERIAL_PRIORITY), // Remote hi-q execution request
    i_o (false,new UDP.IO_record(),(byte)-1); // Only used to profile I/O
    
    final UDP _udp;           // The Callable S.A.M. instance
    final byte _prior;        // Priority
    final boolean _paxos;     // Ignore (or not) packets from outside the Cloud
    udp( boolean paxos, UDP udp, byte prior ) { _paxos = paxos; _udp = udp; _prior = prior; }
    static udp[] UDPS = values();
  }
  public static udp getUdp(int id){return udp.UDPS[id];}
  // Handle an incoming I/O transaction, probably from a UDP packet.  The
  // returned Autobuffer will be closed().  If the returned buffer is not the
  // passed-in buffer, the call() method must close it's AutoBuffer arg.
  abstract AutoBuffer call(AutoBuffer ab);

  // Pretty-print bytes 1-15; byte 0 is the udp_type enum
  static final char[] cs = new char[32];
  static char hex(int x) { x &= 0xf; return (char)(x+((x<10)?'0':('a'-10))); }
  String print16( AutoBuffer ab ) {
    for( int i=0; i<16; i++ ) {
      int b = ab.get1U();
      cs[(i<<1)     ] = hex(b>>4);
      cs[(i<<1)+1   ] = hex(b   );
    }
    return new String(cs);
  }

  // Dispatch on the enum opcode and return a pretty string
  static private final byte[] pbuf = new byte[16];
  static public String printx16( long lo, long hi ) {
    UnsafeUtils.set8(pbuf, 0, lo);
    UnsafeUtils.set8(pbuf, 8, hi);
    return udp.UDPS[(int)(lo&0xFF)]._udp.print16(new AutoBuffer(pbuf));
  }
  private static class IO_record extends UDP {
    AutoBuffer call(AutoBuffer ab) { throw H2O.fail(); }
    String print16( AutoBuffer ab ) {
      int flavor = ab.get1U(3);
      int iotime = ab.get4 (4);
      int size   = ab.get4 (8);
      return "I/O "+Value.nameOfPersist(flavor)+" "+iotime+"ms "+size+"b";
    }
  }
}
