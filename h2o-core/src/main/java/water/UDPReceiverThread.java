package water;

import java.nio.channels.DatagramChannel;
import java.util.Date;
import java.util.Random;

import water.util.Log;

/**
 * The Thread that looks for UDP Cloud requests.
 *
 * This thread just spins on reading UDP packets from the kernel and either
 * dispatching on them directly itself (if the request is known short) or
 * queuing them up for worker threads.
 * @author <a href="mailto:cliffc@h2o.ai"></a>
 * @version 1.0
 */

public class UDPReceiverThread extends Thread {
  static private int  _unknown_packets_per_sec = 0;
  static private long _unknown_packet_time = 0;
  static final Random RANDOM_UDP_DROP = new Random();

  private final DatagramChannel datagramChannel;

  public UDPReceiverThread(DatagramChannel datagramChannel) {
    super("D-UDP-Recv");
    this.datagramChannel = datagramChannel;
  }

  // ---
  // Started by main() on a single thread, this code manages reading UDP packets
  @SuppressWarnings("resource")
  public void run() {
    Thread.currentThread().setPriority(Thread.MAX_PRIORITY-1);
    DatagramChannel sock = datagramChannel, errsock = null;
    boolean saw_error = false;

    while( true ) {
      try {
        // Cleanup from any prior socket failures.  Rare unless we're really sick.
        if( errsock != null ) { // One time attempt a socket close
          final DatagramChannel tmp2 = errsock; errsock = null;
          tmp2.close();       // Could throw, but errsock cleared for next pass
        }
        if( saw_error ) Thread.sleep(1000); // prevent deny-of-service endless socket-creates
        saw_error = false;

        // ---
        // Common-case setup of a socket
        if( sock == null ) {
          sock = DatagramChannel.open();
          sock.socket().bind(H2O.SELF._key);
        }

        // Receive a packet & handle it
        basic_packet_handling(new AutoBuffer(sock));

      } catch( java.nio.channels.AsynchronousCloseException ex ) {
        break;                  // Socket closed for shutdown
      } catch( java.nio.channels.ClosedChannelException ex ) {
        break;                  // Socket closed for shutdown
      } catch( Exception e ) {
        // On any error from anybody, close all sockets & re-open
        Log.err("UDP Receiver error on port "+H2O.H2O_PORT,e);
        saw_error = true;
        errsock  = sock ;
        sock  = null; // Signal error recovery on the next loop
      }
    }
  }

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
