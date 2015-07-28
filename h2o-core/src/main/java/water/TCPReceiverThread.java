package water;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import water.AutoBuffer.AutoBufferException;
import water.util.Log;

/**
 * The Thread that looks for TCP Cloud requests.
 *
 * This thread just spins on reading TCP requests from other Nodes.
 * @author <a href="mailto:cliffc@h2o.ai"></a>
 * @version 1.0
 */

public class TCPReceiverThread extends Thread {
  private ServerSocketChannel SOCK;
  private final boolean _udpLike; // true if this channel is for small async udp-like messages
  public TCPReceiverThread(ServerSocketChannel sock, boolean udpLike) { super("TCP-Accept"); SOCK = sock; _udpLike = udpLike; }

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
          assert !_udpLike;
          SOCK = ServerSocketChannel.open();
          SOCK.socket().setReceiveBufferSize(AutoBuffer.BBP_BIG.size());
          SOCK.socket().bind(H2O.SELF._key);
        }
        // Block for TCP connection and setup to read from it.
        SocketChannel sock = SOCK.accept();
        // Pass off the TCP connection to a separate reader thread
        if(_udpLike)
          new TCP_UDP_ReaderThread(sock.socket().getInetAddress(),sock).start();
        else new TCPReaderThread(sock,new AutoBuffer(sock)).start();
      } catch( java.nio.channels.AsynchronousCloseException ex ) {
        break;                  // Socket closed for shutdown
      } catch( Exception e ) {
        // On any error from anybody, close all sockets & re-open
        Log.err("IO error on TCP port "+H2O.H2O_PORT+": ",e);
        saw_error = true;
        errsock = SOCK ;  SOCK = null; // Signal error recovery on the next loop
      }
    }
  }

  static class TCP_UDP_ReaderThread extends Thread {
    private final SocketChannel _chan;
    private final ByteBuffer _bb;
    private final InetAddress _ia;
    private H2ONode _h2o;

    public TCP_UDP_ReaderThread(InetAddress ia,SocketChannel chan) {
      super("TCP-UDP");
      _ia = ia;
      _chan = chan;
      _bb = AutoBuffer.BBP_BIG.make();
    }

    private int read(int n)  throws IOException {
      if(_bb.remaining() < n)
        throw new IllegalStateException("Reading more bytes than available, reading " + n + " bytes, remaining = " + _bb.remaining());
      int sizeRead = 0;
      while(sizeRead < n) {
        int res = 0; // Read more
        res = _chan.read(_bb);
        if( res == -1 )
          throw new EOFException("Reading "+n+" bytes, AB="+this);
        if( res ==  0 ) throw new RuntimeException("Reading zero bytes - so no progress?");
        sizeRead += res;
      }
      return sizeRead;
    }
    public void run(){
      int start = 0;
      boolean idle = true;
      try {
        while (true) {
          idle = true;
          if (_h2o != null)
            _h2o._last_heard_from = System.currentTimeMillis();
          if (start > _bb.position() - 2)
            read(start + 2 - _bb.position());
          idle = false;
          int sz = _bb.getShort(start); // message size in bytes
          assert sz < AutoBuffer.BBP_SML.size() : "Incoming message is too big, should've been sent by TCP-BIG, got " + sz + " bytes, start = " + start;
          read(start + sz - _bb.position());
          assert (0xFF & _bb.get(start + sz)) == 0xef:"Missing expected sentinel at the end of the message, likely out of sync, start = " + start + ", size = " + sz;
          // extract the bytes
          byte[] ary = new byte[Math.max(sz, 18)];
          for (int i = 0; i < sz; ++i)
            ary[i] = _bb.get(start + i);
          if (_h2o == null)
            _h2o = H2ONode.intern(_ia, new AutoBuffer(null, ary).getPort());
          AutoBuffer ab = new AutoBuffer(_h2o, ary);
          int ctrl = ab.getCtrl();
          TimeLine.record_recv(ab, false, 0);
          H2O.submitTask(new FJPacket(ab, ctrl));
          start += sz + 1;
          if (_bb.remaining() < AutoBuffer.BBP_SML.size() + 2 + 1) { // + 2 bytes for size + 1 byte for 0xef sentinel
            _bb.limit(_bb.position());
            _bb.position(start);
            _bb.compact();
            start = 0;
          }
        }
      } catch (IOException ioe) {
        if(!idle) {
          Log.err("Got IO Error when reading small messages over TCP");
          Log.err(ioe);
        }
      } finally {
        AutoBuffer.BBP_BIG.free(_bb);
        if(_chan != null && _chan.isOpen())
          try { _chan.close();} catch (IOException e) {}
      }
    }
  }
  // A private thread for reading from this open socket.
  static class TCPReaderThread extends Thread {
    public SocketChannel _sock;
    public AutoBuffer _ab;
    public TCPReaderThread(SocketChannel sock, AutoBuffer ab) {
      super("TCP-"+ab._h2o+"-"+(ab._h2o._tcp_readers++));
      _sock = sock;
      _ab = ab;
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
          int sz = _ab._bb.getShort(0);
          assert sz == 0:"message size must be set to 0 for large tcp transfers";
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
          _ab = new AutoBuffer(_sock);
        } catch( Exception e ) {
          // Exceptions here are *normal*, this is an idle TCP connection and
          // either the OS can time it out, or the cloud might shutdown.  We
          // don't care what happens to this socket.
          break;         // Ignore all errors; silently die if socket is closed
        }
      }
    }
  }
}
