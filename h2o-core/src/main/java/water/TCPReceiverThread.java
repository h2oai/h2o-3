package water;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

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

  public TCPReceiverThread(ServerSocketChannel sock) { super("TCP-Accept"); SOCK = sock;  }

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
          SOCK.socket().setReceiveBufferSize(AutoBuffer.BBP_BIG.size());
          SOCK.socket().bind(H2O.SELF._key);
        }
        // Block for TCP connection and setup to read from it.
        SocketChannel sock = SOCK.accept();
        ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder());
        bb.limit(bb.capacity());
        bb.position(0);
        while(bb.hasRemaining()) // read first 8 bytes
          sock.read(bb);
        bb.flip();
        int chanType = bb.get(); // 1 - small , 2 - big
        int port = bb.getChar();
        int sentinel = (0xFF) & bb.get();
        if(sentinel != 0xef)
          throw H2O.fail("missing eom sentinel when opening new tcp channel");
        // todo compare against current cloud, refuse the con if no match
        H2ONode h2o = H2ONode.intern(sock.socket().getInetAddress(),port);
        // Pass off the TCP connection to a separate reader thread
        if(chanType == 1) {
          Log.info("starting new UDP-TCP receiver thread connected to " + sock.getRemoteAddress());
          new UDP_TCP_ReaderThread(h2o, sock).start();
        } else if(chanType == 2)
          new TCPReaderThread(sock,new AutoBuffer(sock)).start();
        else throw H2O.fail("unexpected channel type " + chanType + ", only know 1 - Small and 2 - Big");
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

  static class UDP_TCP_ReaderThread extends Thread {
    private final SocketChannel _chan;
    private final ByteBuffer _bb;
    private final H2ONode _h2o;

    public UDP_TCP_ReaderThread(H2ONode h2o, SocketChannel chan) {
      super("UDP-TCP");
      _h2o = h2o;
      _chan = chan;
      _bb = ByteBuffer.allocateDirect(AutoBuffer.BBP_BIG.size()).order(ByteOrder.nativeOrder());
//      _bb = ByteBuffer.wrap(new byte[AutoBuffer.BBP_BIG.size()]).order(ByteOrder.nativeOrder());
    }

    public String printBytes(ByteBuffer bb, int start, int sz) {
      StringBuilder sb = new StringBuilder();
      int idx = start + sz;
      try {
        for (int i = 5; i > 0; --i)
          sb.append("-" + i + ":" + (0xFF & bb.get(idx - i)) + " ");
        sb.append("0: " + (0xFF & bb.get(idx)) + " ");
        for (int i = 1; i <= 5; ++i)
          sb.append("+" + i + ":" + (0xFF & bb.get(idx + i)) + " ");
      } catch(Throwable t) {}
      return sb.toString();
    }
    private int read(int n)  throws IOException {
      if(_bb.remaining() < n)
        throw new IllegalStateException("Reading more bytes than available, reading " + n + " bytes, remaining = " + _bb.remaining());
      int sizeRead = 0;
      while(sizeRead < n) {
        int res = _chan.read(_bb);
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
          int sz = ((0xFF & _bb.get(start+1)) << 8) | (0xFF & _bb.get(start)); // message size in bytes
          assert sz < AutoBuffer.BBP_SML.size() : "Incoming message is too big, should've been sent by TCP-BIG, got " + sz + " bytes, start = " + start;
          read(start + 2 + sz + 1 - _bb.position());
          if ((0xFF & _bb.get(start + 2 + sz)) != 0xef)
            H2O.fail("Missing expected sentinel (0xef==239) at the end of the message from " + _h2o + ", likely out of sync, start = " + start + ", size = " + sz + ", position = " + _bb.position() +", bytes = " + printBytes(_bb, start, sz));
          // extract the bytes
          byte[] ary = MemoryManager.malloc1(Math.max(16,sz)); // fixme: 16 for timeline which always accesses first 16 bytes
          if( _bb.hasArray()){
            System.arraycopy(_bb.array(),start+2,ary,0,sz);
          } else {
            int pos = _bb.position();
            _bb.position(start+2);
            _bb.get(ary,0,sz);
            _bb.position(pos);
          }
          AutoBuffer ab = new AutoBuffer(_h2o, ary);
          int ctrl = ab.getCtrl();
          TimeLine.record_recv(ab, false, 0);
          H2O.submitTask(new FJPacket(ab, ctrl));
          start += sz + 2 + 1;
          if (_bb.remaining() < AutoBuffer.BBP_SML.size() + 2 + 1) { // + 2 bytes for size + 1 byte for 0xef sentinel
            _bb.limit(_bb.position());
            _bb.position(start);
            _bb.compact();
            start = 0;
          }
        }
      } catch (IOException ioe) {
        if (!idle) {
          Log.err("Got IO Error when reading small messages over TCP");
          Log.err(ioe);
        }
      } catch(Throwable t){
        t.printStackTrace();
        Log.err("unexpected error in UDP-TCP thread.");
        Log.err(t);
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
