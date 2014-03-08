package water;

import sun.misc.Unsafe;
import water.nbhm.UtilUnsafe;

/**
 * Do Something with an incoming UDP packet
 *
 * Classic Single Abstract Method pattern.
 * @author <a href="mailto:cliffc@0xdata.com"></a>
 * @version 1.0
 */

public abstract class UDP {
  // Types of UDP packets I grok
  public static enum udp {
    bad(false,null), // Do not use the zero packet, too easy to make mistakes
//      // Some health-related packet types.  These packets are all stateless, in
//      // that we do not need to send any replies back.
//      heartbeat     ( true, new UDPHeartbeat()),
      rebooted      ( true, new UDPRebooted()),  // This node has rebooted recently
      timeline      (false, new TimeLine()),     // Get timeline dumps from across the Cloud
//
//      // All my *reliable* tasks (below), are sent to remote nodes who then ACK
//      // back an answer.  To be reliable, I might send the TASK multiple times.
//      // To get a reliable answer, the remote might send me multiple ACKs with
//      // the same answer every time.  When does the remote know it can quit
//      // tracking reply ACKs?  When it recieves an ACKACK.
//      ackack(false,new UDPAckAck()),  // a generic ACKACK for a UDP async task
//      ack   (false,new UDPAck   ()),  // a generic ACK    for a UDP async task
//
//      // These packets all imply some sort of request/response handshake.
//      // We'll hang on to these packets; filter out dup sends and auto-reply
//      // identical result ACK packets.
      exec(false,null/*new RPC.RemoteHandler()*/),   // Remote hi-q execution request
      i_o (false,new UDP.IO_record());       // Only used to profile I/O

    final UDP _udp;           // The Callable S.A.M. instance
    final boolean _paxos;     // Ignore (or not) packets from outside the Cloud
    udp( boolean paxos, UDP udp ) { _paxos = paxos; _udp = udp; }
    static public udp[] UDPS = values();
    // Default: most tasks go to the hi-priority queue
//    //ForkJoinPool pool() { return this==execlo ? H2O.FJP_NORM : H2O.FJP_HI; }
  };

  // Handle an incoming I/O transaction, probably from a UDP packet.  The
  // returned Autobuffer will be closed().  If the returned buffer is not the
  // passed-in buffer, the call() method must close it's AutoBuffer arg.
  abstract AutoBuffer call(AutoBuffer ab);

  // Pretty-print bytes 1-15; byte 0 is the udp_type enum
  static final char[] cs = new char[32];
  static char hex(int x) { x &= 0xf; return (char)(x+((x<10)?'0':('a'-10))); }
  public String print16( AutoBuffer ab ) {
    for( int i=0; i<16; i++ ) {
      int b = ab.get1();
      cs[(i<<1)+0   ] = hex(b>>4);
      cs[(i<<1)+1   ] = hex(b   );
    }
    return new String(cs);
  }

  // Dispatch on the enum opcode and return a pretty string
  static private final byte[] pbuf = new byte[16];
  static public String printx16( long lo, long hi ) {
    set8(pbuf,0,lo);
    set8(pbuf,8,hi);
    return udp.UDPS[(int)(lo&0xFF)]._udp.print16(new AutoBuffer(pbuf));
  }

  // ---
  private static final Unsafe _unsafe = UtilUnsafe.getUnsafe();
  private static final long _Bbase  = _unsafe.arrayBaseOffset(byte[].class);
  public static int    get2 ( byte[] buf, int off ) { return _unsafe.getShort (buf, _Bbase+off); }
  public static int    get4 ( byte[] buf, int off ) { return _unsafe.getInt   (buf, _Bbase+off); }
  public static long   get8 ( byte[] buf, int off ) { return _unsafe.getLong  (buf, _Bbase+off); }
  public static float  get4f( byte[] buf, int off ) { return _unsafe.getFloat (buf, _Bbase+off); }
  public static double get8d( byte[] buf, int off ) { return _unsafe.getDouble(buf, _Bbase+off); }

  public static int set2 (byte[] buf, int off, short x ) {_unsafe.putShort (buf, _Bbase+off, x); return 2;}
  public static int set4 (byte[] buf, int off, int x   ) {_unsafe.putInt   (buf, _Bbase+off, x); return 4;}
  public static int set4f(byte[] buf, int off, float f ) {_unsafe.putFloat (buf, _Bbase+off, f); return 4;}
  public static int set8 (byte[] buf, int off, long x  ) {_unsafe.putLong  (buf, _Bbase+off, x); return 8;}
  public static int set8d(byte[] buf, int off, double x) {_unsafe.putDouble(buf, _Bbase+off, x); return 8;}

  private static class IO_record extends UDP {
    public AutoBuffer call(AutoBuffer ab) { throw H2O.unimpl(); }
    public String print16( AutoBuffer ab ) {
      int flavor = ab.get1(3);
      int iotime = ab.get4(4);
      int size   = ab.get4(8);
      return "I/O "+Value.nameOfPersist(flavor)+" "+iotime+"ms "+size+"b";
    }
  }
}
