package water;
import java.net.*;

import water.util.Log;

/**
 * The Thread that looks for Multicast UDP Cloud requests.
 *
 * This thread just spins on reading multicast UDP packets from the kernel and
 * either dispatching on them directly itself (if the request is known short)
 * or queuing them up for worker threads.  Multicast *Channels* are available
 * Java 7, but we are writing to Java 6 JDKs.  SO back to the old-school
 * MulticastSocket.
 * @author <a href="mailto:cliffc@0xdata.com"></a>
 * @version 1.0
 */

public class MultiReceiverThread extends Thread {
  public MultiReceiverThread() { super("Multi-UDP-R"); }

  // The Run Method.
  // ---
  // Started by main() on a single thread, this code manages reading UDP packets
  @SuppressWarnings("resource")
  public void run() {
    // No multicast?  Then do not bother with listening for them
    if( H2O.STATIC_H2OS != null ) return;
    Thread.currentThread().setPriority(Thread.MAX_PRIORITY);

    MulticastSocket sock = null, errsock = null;
    InetAddress group = null, errgroup = null;
    boolean saw_error = false;

    // Loop forever accepting Cloud Management requests
    while( true ) {
      try {
        // ---
        // Cleanup from any prior socket failures.  Rare unless we're really sick.
        if( errsock != null && errgroup != null ) { // socket error AND group present
          final InetAddress tmp = errgroup; errgroup = null;
          errsock.leaveGroup(tmp); // Could throw, but errgroup cleared for next pass
        }
        if( errsock != null ) { // One time attempt a socket close
          final MulticastSocket tmp2 = errsock; errsock = null;
          tmp2.close();       // Could throw, but errsock cleared for next pass
        }
        if( saw_error ) Thread.sleep(1000); // prevent deny-of-service endless socket-creates
        saw_error = false;

        // ---
        // Actually do the common-case setup of Inet multicast group
        if( group == null ) group = H2O.CLOUD_MULTICAST_GROUP;
        // More common-case setup of a MultiCast socket
        if( sock == null ) {
          sock = new MulticastSocket(H2O.CLOUD_MULTICAST_PORT);
          if( H2O.CLOUD_MULTICAST_IF != null )
            sock.setNetworkInterface(H2O.CLOUD_MULTICAST_IF);
          sock.joinGroup(group);
        }

        // Receive a packet & handle it
        byte[] buf = new byte[AutoBuffer.MTU];
        DatagramPacket pack = new DatagramPacket(buf,buf.length);
        sock.receive(pack);
        UDPReceiverThread.basic_packet_handling(new AutoBuffer(pack));

      } catch( Exception e ) {
        // On any error from anybody, close all sockets & re-open
        Log.err("Multicast "+H2O.CLOUD_MULTICAST_GROUP+":"+H2O.CLOUD_MULTICAST_PORT, e);
        saw_error = true;
        errsock  = sock ;  sock  = null; // Signal error recovery on the next loop
        errgroup = group;  group = null;
      }
    }
  }
}
