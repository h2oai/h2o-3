package water;

import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import water.util.Log;

/**
 * The Thread that looks for TCP Cloud requests.
 *
 * This thread just spins on reading TCP requests from other Nodes.
 * @author <a href="mailto:cliffc@0xdata.com"></a>
 * @version 1.0
 */

public class TCPReceiverThread extends Thread {
  public static ServerSocketChannel SOCK;
  public TCPReceiverThread() { super("TCP-Accept"); }

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
          SOCK.socket().setReceiveBufferSize(AutoBuffer.BBSIZE);
          SOCK.socket().bind(H2O.SELF._key);
        }

        // Block for TCP connection and setup to read from it.
        SocketChannel sock = SOCK.accept();

        // Pass off the TCP connection to a separate reader thread
        new TCPReaderThread(sock,new AutoBuffer(sock)).start();

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

  // A private thread for reading from this open socket.
  public static class TCPReaderThread extends Thread {
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
          TimeLine.record_recv(_ab, true,0);
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
        } catch( Exception e ) {
          // On any error from anybody, close everything
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
