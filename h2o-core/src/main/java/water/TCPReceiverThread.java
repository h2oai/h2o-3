package water;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ByteChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

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
        InetAddress inetAddress = sock.socket().getInetAddress();
        H2ONode h2o = H2ONode.intern(inetAddress,port);
        // Pass off the TCP connection to a separate reader thread
        switch( chanType ) {
        case 1: new UDP_TCP_ReaderThread(h2o, wrappedSocket).start(); break;
        case 2: new TCPReaderThread(wrappedSocket,new AutoBuffer(wrappedSocket, inetAddress), inetAddress).start(); break;
        default: throw H2O.fail("unexpected channel type " + chanType + ", only know 1 - Small and 2 - Big");
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
          UDPReceiverThread.basic_packet_handling(new AutoBuffer(_h2o, ary, 0, sz));
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

}
