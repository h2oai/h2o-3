package water;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ByteChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Date;
import java.util.Random;

import water.network.SocketChannelFactory;
import water.util.Log;
import water.util.SB;

/**
 * The Thread that looks for TCP Cloud requests.
 *
 * This thread just spins on reading TCP requests from other Nodes.
 * @author <a href="mailto:cliffc@h2o.ai"></a>
 * @version 1.0
 */

public class TCPReceiverThread extends Thread {
  private ServerSocketChannel SOCK;
  private SocketChannelFactory socketChannelFactory;


  /**
   * Byte representing TCP communication for small data
   */
  static final byte TCP_SMALL = 1;

  /**
   * Byte representing TCP communication for big data
   */
  static final byte TCP_BIG = 2;

  /**
   * Byte representing TCP communication for communicating with H2O backend from non-H2O environment
   */
  static final byte TCP_EXTERNAL = 3;

  public TCPReceiverThread(
          ServerSocketChannel sock) {
    super("TCP-Accept");
    SOCK = sock;
    this.socketChannelFactory = H2O.SELF.getSocketFactory();
  }

  // The Run Method.
  // Started by main() on a single thread, this code manages reading TCP requests
  @SuppressWarnings("resource")
  public void run() {
    Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
    ServerSocketChannel errsock = null;
    boolean saw_error = false;

    while( true ) {
      try {
        // Cleanup from any prior socket failures.  Rare unless we're really sick.
        if( errsock != null ) { // One time attempt a socket close
          final ServerSocketChannel tmp2 = errsock; errsock = null;
          tmp2.close();       // Could throw, but errsock cleared for next pass
        }
        if( saw_error ) Thread.sleep(100); // prevent deny-of-service endless socket-creates
        saw_error = false;

        // ---
        // More common-case setup of a ServerSocket
        if( SOCK == null ) {
          SOCK = ServerSocketChannel.open();
          SOCK.socket().setReceiveBufferSize(AutoBuffer.BBP_BIG._size);
          SOCK.socket().bind(H2O.SELF._key);
        }
        // Block for TCP connection and setup to read from it.
        SocketChannel sock = SOCK.accept();
        ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder());
        ByteChannel wrappedSocket = socketChannelFactory.serverChannel(sock);
        bb.limit(bb.capacity());
        bb.position(0);
        while(bb.hasRemaining()) { // read first 8 bytes
          wrappedSocket.read(bb);
        }
        bb.flip();
        int chanType = bb.get(); // 1 - small , 2 - big
        int port = bb.getChar();
        int sentinel = (0xFF) & bb.get();
        if(sentinel != 0xef) {
          if(H2O.SELF.getSecurityManager().securityEnabled) {
            throw new IOException("Missing EOM sentinel when opening new SSL tcp channel.");
          } else {
            throw H2O.fail("missing eom sentinel when opening new tcp channel");
          }
        }
        // todo compare against current cloud, refuse the con if no match


        // Do H2O.Intern in corresponding case branch, we can't do H2O.intern here since it wouldn't work
        // with ExternalFrameHandling ( we don't send the same information there as with the other communication)
        InetAddress inetAddress = sock.socket().getInetAddress();
        // Pass off the TCP connection to a separate reader thread
        switch( chanType ) {
        case TCP_SMALL:
          H2ONode h2o = H2ONode.intern(inetAddress, port);
          new UDP_TCP_ReaderThread(h2o, wrappedSocket).start();
          break;
        case TCP_BIG:
          new TCPReaderThread(wrappedSocket, new AutoBuffer(wrappedSocket, inetAddress), inetAddress).start();
          break;
        case TCP_EXTERNAL:
          new ExternalFrameHandlerThread(wrappedSocket, new AutoBuffer(wrappedSocket, null)).start();
          break;
        default:
          throw H2O.fail("unexpected channel type " + chanType + ", only know 1 - Small, 2 - Big and 3 - ExternalFrameHandling");
        }
      } catch( java.nio.channels.AsynchronousCloseException ex ) {
        break;                  // Socket closed for shutdown
      } catch( Exception e ) {
        e.printStackTrace();
        // On any error from anybody, close all sockets & re-open
        Log.err("IO error on TCP port "+H2O.H2O_PORT+": ",e);
        saw_error = true;
        errsock = SOCK ;  SOCK = null; // Signal error recovery on the next loop
      }
    }
  }

  // A private thread for reading from this open socket.
  static class TCPReaderThread extends Thread {
    public ByteChannel _sock;
    public AutoBuffer _ab;
    private final InetAddress address;

    public TCPReaderThread(ByteChannel sock, AutoBuffer ab, InetAddress address) {
      super("TCP-"+ab._h2o+"-"+(ab._h2o._tcp_readers++));
      _sock = sock;
      _ab = ab;
      this.address = address;
      setPriority(MAX_PRIORITY-1);
    }

    public void run() {
      while( true ) { // Loop, reading fresh TCP requests until the sender closes
        try {
          // Record the last time we heard from any given Node
          _ab._h2o._last_heard_from = System.currentTimeMillis();
          TimeLine.record_recv(_ab, true, 0);
          // Hand off the TCP connection to the proper handler
          int ctrl = _ab.getCtrl();
          int x = ctrl;
          if( ctrl < 0 || ctrl >= UDP.udp.UDPS.length ) x = 0;
          switch( UDP.udp.UDPS[x] ) {
          case exec:     RPC.remote_exec  (_ab); break;
          case ack:      RPC.tcp_ack      (_ab); break;
          case timeline: TimeLine.tcp_call(_ab); break;
          default: throw new RuntimeException("Unknown TCP Type: " + ctrl+" "+_ab._h2o);
          }
        } catch( java.nio.channels.AsynchronousCloseException ex ) {
          break;                // Socket closed for shutdown
        } catch( Throwable e ) {
          // On any error from anybody, close everything
          System.err.println("IO error");
          e.printStackTrace();
          Log.err("IO error on TCP port "+H2O.H2O_PORT+": ",e);
          break;
        }
        // Reuse open sockets for the next task
        try {
          if( !_sock.isOpen() ) break;
          _ab = new AutoBuffer(_sock, address);
        } catch( Exception e ) {
          // Exceptions here are *normal*, this is an idle TCP connection and
          // either the OS can time it out, or the cloud might shutdown.  We
          // don't care what happens to this socket.
          break;         // Ignore all errors; silently die if socket is closed
        }
      }
    }
  }


  /** A private thread reading small messages from a tcp channel.  The thread
   *  reads the raw bytes of a message from the channel, copies them into a
   *  byte array which is than passed on to FJQ.  Each message is expected to
   *  be MSG_SZ(2B) MSG BODY(MSG_SZ*B) EOM MARKER (1B - 0xef). */
  static class UDP_TCP_ReaderThread extends Thread {
    private final ByteChannel _chan;
    private final ByteBuffer _bb;
    private final H2ONode _h2o;

    public UDP_TCP_ReaderThread(H2ONode h2o, ByteChannel chan) {
      super("UDP-TCP-READ-" + h2o);
      _h2o = h2o;
      _chan = chan;
      _bb = ByteBuffer.allocateDirect(AutoBuffer.BBP_BIG._size).order(ByteOrder.nativeOrder());
      _bb.flip();               // Prep for reading; zero bytes available
    }

    public String printBytes(ByteBuffer bb, int start, int sz) {
      SB sb = new SB();
      int idx = start + sz;
      try {
        for (int i = 5; i > 0; --i)
          sb.p("-").p(i).p(":").p(0xFF & bb.get(idx - i)).p(" ");
        sb.p("0: ").p(0xFF & bb.get(idx)).p(" ");
        for (int i = 1; i <= 5; ++i)
          sb.p("+").p(i).p(":").p(0xFF & bb.get(idx + i)).p(" ");
      } catch(Throwable t) {/*ignore, just a debug print*/}
      return sb.toString();
    }

    // Read until there are at least N bytes in the ByteBuffer
    private ByteBuffer read(int n) throws IOException {
      if( _bb.remaining() < n ) { // Not enuf bytes between position and limit
        _bb.compact();            // move data down to 0, set position to remaining bytes
        while(_bb.position() < n) {
          int res = _chan.read(_bb); // Slide position forward (up to limit)
          if (res <= 0) throw new IOException("Didn't read any data: res=" + res);         // no eof & progress made
          _h2o._last_heard_from = System.currentTimeMillis();
        }
        _bb.flip();             // Limit to amount of data, position to 0
      }
      return _bb;
    }

    @Override public void run() {
      assert !_bb.hasArray();   // Direct ByteBuffer only
      boolean idle = false;
      try {
        //noinspection InfiniteLoopStatement
        while (true) {
          idle = true; // OK to have remote suicide while idle; happens during normal shutdown
          int sz = read(2).getChar(); // 2 bytes of next-message-size
          idle = false;
          assert sz < AutoBuffer.BBP_SML._size : "Incoming message is too big, should've been sent by TCP-BIG, got " + sz + " bytes";
          byte[] ary = MemoryManager.malloc1(Math.max(16,sz));
          int sentinel = read(sz+1).get(ary,0,sz).get(); // extract the message bytes, then the sentinel byte
          assert (0xFF & sentinel) == 0xef : "Missing expected sentinel (0xef) at the end of the message from " + _h2o + ", likely out of sync, size = " + sz + ", position = " + _bb.position() +", bytes = " + printBytes(_bb, _bb.position(), sz);
          // package the raw bytes into an array and pass it on to FJQ for further processing
          basic_packet_handling(new AutoBuffer(_h2o, ary, 0, sz));
        }
      } catch(Throwable t) {
        if( !idle || !(t instanceof IOException) ) {
          t.printStackTrace();
          Log.err(t);
        }
      } finally {
        AutoBuffer.BBP_BIG.free(_bb);
        if(_chan != null && _chan.isOpen())
          try { _chan.close();} catch (IOException e) {/*ignore error on close*/}
      }
    }
  }

  static private int  _unknown_packets_per_sec = 0;
  static private long _unknown_packet_time = 0;
  static final Random RANDOM_UDP_DROP = new Random();
  // Basic packet handling:
  //   - Timeline record it
  static public void basic_packet_handling( AutoBuffer ab ) throws java.io.IOException {
    // Randomly drop 1/10th of the packets, as-if broken network.  Dropped
    // packets are timeline recorded before dropping - and we still will
    // respond to timelines and suicide packets.
    int drop = H2O.ARGS.random_udp_drop &&
            RANDOM_UDP_DROP.nextInt(5) == 0 ? 2 : 0;

    // Record the last time we heard from any given Node
    TimeLine.record_recv(ab, false, drop);
    final long now = ab._h2o._last_heard_from = System.currentTimeMillis();

    // Snapshots are handled *IN THIS THREAD*, to prevent more UDP packets from
    // being handled during the dump.  Also works for packets from outside the
    // Cloud... because we use Timelines to diagnose Paxos failures.
    int ctrl = ab.getCtrl();
    ab.getPort(); // skip the port bytes
    if( ctrl == UDP.udp.timeline.ordinal() ) {
      UDP.udp.timeline._udp.call(ab);
      return;
    }

    // Suicide packet?  Short-n-sweet...
    if( ctrl == UDP.udp.rebooted.ordinal())
      UDPRebooted.checkForSuicide(ctrl, ab);

    // Drop the packet.
    if( drop != 0 ) return;

    // Get the Cloud we are operating under for this packet
    H2O cloud = H2O.CLOUD;
    // Check cloud membership; stale ex-members are "fail-stop" - we mostly
    // ignore packets from them (except paxos packets).
    boolean is_member = cloud.contains(ab._h2o);
    boolean is_client = ab._h2o._heartbeat._client;

    // Some non-Paxos packet from a non-member.  Probably should record & complain.
    // Filter unknown-packet-reports.  In bad situations of poisoned Paxos
    // voting we can get a LOT of these packets/sec, flooding the logs.
    if( !(UDP.udp.UDPS[ctrl]._paxos || is_member || is_client) ) {
      _unknown_packets_per_sec++;
      long timediff = ab._h2o._last_heard_from - _unknown_packet_time;
      if( timediff > 1000 ) {
        // If this is a recently booted client node... coming up right after a
        // prior client was shutdown, it might see leftover trash UDP packets
        // from the servers intended for the prior client.
        if( !(H2O.ARGS.client && now-H2O.START_TIME_MILLIS.get() < HeartBeatThread.CLIENT_TIMEOUT) )
          Log.warn("UDP packets from outside the cloud: "+_unknown_packets_per_sec+"/sec, last one from "+ab._h2o+ " @ "+new Date());
        _unknown_packets_per_sec = 0;
        _unknown_packet_time = ab._h2o._last_heard_from;
      }
      ab.close();
      return;
    }

    // Paxos stateless packets & ACKs just fire immediately in a worker
    // thread.  Dups are handled by these packet handlers directly.  No
    // current membership check required for Paxos packets.
    //
    // Handle the case of packet flooding draining all the available
    // ByteBuffers and running the JVM out of *native* memory, triggering
    // either a large RSS (and having YARN kill us for being over-budget) or
    // simply tossing a OOM - but a out-of-native-memory nothing to do with
    // heap memory.
    //
    // All UDP packets at this stage have fairly short lifetimes - Exec packets
    // (which you might think to be unboundedly slow) are actually just going
    // through the deserialization call in RPC.remote_exec - and the deser'd
    // DTask gets tossed on a low priority queue to do "the real work".  Since
    // this is coming from a UDP packet the deser work is actually small.


    H2O.submitTask(new FJPacket(ab,ctrl));
  }

}
