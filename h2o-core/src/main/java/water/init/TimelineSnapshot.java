package water.init;

import java.net.InetAddress;
import java.util.*;

import water.*;
import water.util.Log;

/**
 * Wrapper around timeline snapshot. Implements iterator interface (events are
 * ordered according to send/receive dependencies across the nodes and trivial time
 * dependencies inside node)
 *
 * @author tomas
 */
public final class TimelineSnapshot implements
  Iterable<TimelineSnapshot.Event>, Iterator<TimelineSnapshot.Event> {
  final long[][] _snapshot;
  final Event[] _events;
  final HashMap<Event, Event> _edges;
  final public HashMap<Event, ArrayList<Event>> _sends;
  final H2O _cloud;
  boolean _processed;

  public TimelineSnapshot(H2O cloud, long[][] snapshot) {
    _cloud = cloud;
    _snapshot = snapshot;
    _edges = new HashMap<Event, Event>();
    _sends = new HashMap<Event, ArrayList<Event>>();
    _events = new Event[snapshot.length];

    // DEBUG: print out the event stack as we got it
//    System.out.println("# of nodes: " + _events.length);
//    for (int j = 0; j < TimeLine.length(); ++j) {
//      System.out.print("row# " + j + ":");
//      for (int i = 0; i < _events.length; ++i) {
//        System.out.print("  ||  " + new Event(i, j));
//      }
//      System.out.println("  ||");
//    }

    for (int i = 0; i < _events.length; ++i) {
      // For a new Snapshot, most of initial entries are all zeros. Skip them
      // until we start finding entries... which will be the oldest entries.
      // The timeline is age-ordered (per-thread, we hope the threads are
      // fairly consistent)
      _events[i] = new Event(i, 0);
      if (_events[i].isEmpty()) {
        if (!_events[i].next())
          _events[i] = null;
      }
      if (_events[i] != null)
        processEvent(_events[i]);
      assert (_events[i] == null) || (_events[i]._eventIdx < TimeLine.MAX_EVENTS);
    }

    // now build the graph (i.e. go through all the events once)
    for (@SuppressWarnings("unused") Event e : this) ;

    _processed = true;
    for (int i = 0; i < _events.length; ++i) {
      // For a new Snapshot, most of initial entries are all zeros. Skip them
      // until we start finding entries... which will be the oldest entries.
      // The timeline is age-ordered (per-thread, we hope the threads are
      // fairly consistent)
      _events[i] = new Event(i, 0);
      if (_events[i].isEmpty()) {
        if (!_events[i].next())
          _events[i] = null;
      }
      assert (_events[i] == null) || (_events[i]._eventIdx < TimeLine.MAX_EVENTS);
    }
  }

  // convenience wrapper around event stored in snapshot
  // contains methods to access event data, move to the next previous event
  // and to test whether two events form valid sender/receiver pair
  //
  // it is also needed to keep track of send/recv dependencies when iterating
  // over events in timeline
  public class Event {
    public final int _nodeId;   // Which node/column# in the snapshot
    final long[] _val;          // The column from the snapshot
    int _eventIdx;              // Which row in the snapshot
    // For send-packets, the column# is the cloud-wide idx of the sender, and
    // the packet contains the reciever.  Vice-versa for received packets,
    // where the column# is the cloud-wide idx of the receiver, and the packet
    // contains the sender.
    H2ONode _packh2o;           // The H2O in the packet
    boolean _blocked;

    public UDP.udp udpType(){
      return UDP.getUdp((int)(dataLo() & 0xff));
    } // First byte is UDP packet type

    public Event(int nodeId, int eventIdx) {
      _nodeId   = nodeId;
      _eventIdx = eventIdx;
      _val = _snapshot[nodeId];
      computeH2O(false);
    }

    @Override public final int hashCode() { return (_nodeId <<10)^_eventIdx; }
    @Override public final boolean equals(Object o) {
      Event e = (Event)o;
      return _nodeId==e._nodeId && _eventIdx==e._eventIdx;
    }

    // (re)compute the correct H2ONode, if the _eventIdx changes.
    private boolean computeH2O(boolean b) {
      H2ONode h2o = null;
      if( dataLo() != 0 ) {     // Dead/initial packet
        InetAddress inet = addrPack();
        if( !inet.isMulticastAddress() ) { // Is multicast?
          h2o = H2ONode.intern(inet,portPack());
          if( isSend() && h2o == recoH2O() ) // Another multicast indicator: sending to self
            h2o = null;                      // Flag as multicast
        }
      }
      _packh2o = h2o;
      return b;                 // For flow-coding
    }

    public final int send_recv() { return TimeLine.send_recv(_val, _eventIdx); }
    public final int dropped  () { return TimeLine.dropped  (_val, _eventIdx); }
    public final boolean isSend() { return send_recv() == 0; }
    public final boolean isRecv() { return send_recv() == 1; }
    public final boolean isDropped() { return dropped() != 0; }
    public final InetAddress addrPack() { return TimeLine.inet(_val, _eventIdx); }
    public final long dataLo() { return TimeLine.l0(_val, _eventIdx); }
    public final long dataHi() { return TimeLine.l8(_val, _eventIdx); }
    public final long ns() { return TimeLine.ns(_val, _eventIdx); }
    public final boolean isTCP(){return (ns() & 4) != 0;}
    public final long ms() { return TimeLine.ms(_val, _eventIdx) + recoH2O()._heartbeat.jvmBootTimeMsec(); }
    public H2ONode packH2O() { return _packh2o; } // H2O in packet
    public H2ONode recoH2O() { return _cloud.members()[_nodeId]; } // H2O recording packet
    public final int portPack() {
      int i = (int) dataLo();
      // 1st byte is UDP type, so shift right by 8.
      // Next 2 bytes are UDP port #, so mask by 0xFFFF.
      return ((0xFFFF) & (i >> 8));
    }
    public final String addrString() { return _packh2o==null ? "multicast" : _packh2o.toString(); }
    public final String ioflavor() {
      int flavor = is_io();
      return flavor == -1 ? (isTCP()?"TCP":"UDP") : Value.nameOfPersist(flavor);
    }
    public final int is_io() {
      int udp_type = (int) (dataLo() & 0xff); // First byte is UDP packet type
      return UDP.udp.i_o.ordinal() == udp_type ? (int)((dataLo()>>24)&0xFF) : -1;
    }
    // ms doing I/O
    public final int ms_io() { return (int)(dataLo()>>32); }
    public final int size_io() { return (int)dataHi(); }

    public String toString() {
      int udp_type = (int) (dataLo() & 0xff); // First byte is UDP packet type
      UDP.udp udpType = UDP.getUdp(udp_type);
      String operation = isSend() ? " SEND " : " RECV ";
      String host1 = addrString();
      String host2 = recoH2O().toString();
      String networkPart = isSend()
        ? (host2 + " -> " + host1)
        : (host1 + " -> " + host2);
      return "Node(" + _nodeId + ": " + ns() + ") " + udpType.toString()
        + operation + networkPart + (isDropped()?" DROPPED ":"") + ", data = '"
        + Long.toHexString(this.dataLo()) + ','
        + Long.toHexString(this.dataHi()) + "'";
    }

    /**
     * Check if two events form valid sender/receiver pair.
     *
     * Two events are valid sender/receiver pair iff the ports, adresses and
     * payload match.
     *
     * @param ev
     * @return true iff the two events form valid sender/receiver pair
     */
    final boolean match(Event ev) {
      // check we're matching send and receive
      if (send_recv() == ev.send_recv())
        return false;
      // compare the packet payload matches
      long myl0 =    dataLo();
      long evl0 = ev.dataLo();
      int my_udp_type = (int) (myl0 & 0xff); // first byte is udp type
      int ev_udp_type = (int) (evl0 & 0xff); // first byte is udp type
      if (my_udp_type != ev_udp_type)
        return false;
      UDP.udp e = UDP.getUdp(my_udp_type);
      switch (e) {
        case rebooted:
        case timeline:
          // compare only first 3 bytes here (udp type and port),
          // but port# is checked below as part of address
          break;
        case ack:
        case nack:
        case fetchack:
        case ackack:
        case exec:
        case heartbeat:
          // compare 3 ctrl bytes + 4 bytes task #
          //  if ((myl0 & 0xFFFFFFFFFFFFFFl) != (evl0 & 0xFFFFFFFFFFFFFFl))
          if( (int)(myl0>>24) != (int)(evl0>>24))
            return false;
          break;
        case i_o:                 // Shows up as I/O-completing recorded packets
          return false;
        default:
          throw new RuntimeException("unexpected udp packet type " + e.toString());
      }

      // Check that port numbers are compatible.  Really check that the
      // H2ONode's are compatible.  The port#'s got flipped during recording to
      // allow this check (and a null _packh2o is a multicast).
      if(    _packh2o!=null &&    _packh2o.index()!=ev._nodeId ) return false;
      if( ev._packh2o!=null && ev._packh2o.index()!=   _nodeId ) return false;
      return true;
    }

    public final boolean isEmpty() {
      return (_eventIdx < TimeLine.length()) ? TimeLine.isEmpty(_val, _eventIdx) : false;
    }

    public final Event clone() {
      return new Event(_nodeId, _eventIdx);
    }

    boolean prev(int minIdx) {
      int min = Math.max(minIdx, -1);
      if (_eventIdx <= minIdx)
        return false;
      while (--_eventIdx > min)
        if (!isEmpty())
          return computeH2O(true);
      return computeH2O(false);
    }

    boolean prev() {
      return prev(-1);
    }

    Event previousEvent(int minIdx) {
      Event res = new Event(_nodeId, _eventIdx);
      return (res.prev(minIdx)) ? res : null;
    }

    Event previousEvent() {
      return previousEvent(-1);
    }

    boolean next(int maxIdx) {
      int max = Math.min(maxIdx, TimeLine.length());
      if (_eventIdx >= max)
        return false;
      while (++_eventIdx < max)
        if (!isEmpty())
          return computeH2O(true);
      return computeH2O(false);
    }

    boolean next() {
      return next(TimeLine.length());
    }

    Event nextEvent(int maxIdx) {
      Event res = new Event(_nodeId, _eventIdx);
      return (res.next(maxIdx)) ? res : null;
    }

    Event nextEvent() {
      return nextEvent(TimeLine.length());
    }

    /**
     * Used to determine ordering of events not bound by any dependency.
     *
     * Events compared according to following rules:
     *   Receives go before sends.  Since we are only here with unbound events,
     *   unbound receives means their sender has already appeared and they
     *   should go adjacent to their sender.
     *   For two sends, pick the one with receives with smallest timestamp (ms)
     *   otherwise pick the sender with smallest timestamp (ms)
     *
     * @param ev  other Event to compare
     * @return
     */
    public final int compareTo(Event ev) {
      if( ev == null ) return -1;
      if( ev == this ) return  0;
      if( ev.equals(this) ) return 0;
      int res = ev.send_recv() - send_recv(); // recvs should go before sends
      if( res != 0 ) return res;
      if (isSend()) {
        // compare by the time of receivers
        long myMinMs = Long.MAX_VALUE;
        long evMinMs = Long.MAX_VALUE;
        ArrayList<Event> myRecvs = _sends.get(this);
        ArrayList<Event> evRecvs = _sends.get(ev  );
        for (Event e : myRecvs)
          if (e.ms() < myMinMs)
            myMinMs = e.ms();
        for (Event e : evRecvs)
          if (e.ms() < evMinMs)
            evMinMs = e.ms();
        res = (int) (myMinMs - evMinMs);
        if( myMinMs == Long.MAX_VALUE && evMinMs != Long.MAX_VALUE ) res = -1;
        if( myMinMs != Long.MAX_VALUE && evMinMs == Long.MAX_VALUE ) res =  1;
      }
      if (res == 0)
        res = (int) (ms() - ev.ms());
      if( res == 0 )
        res = (int) (ns() - ev.ns());
      return res;
    }
  }

  /**
   * Check whether two events can be put together in sender/recv relationship.
   *
   * Events must match, also each sender can have only one receiver per node.
   *
   * @param senderCnd
   * @param recvCnd
   * @return
   */
  private boolean isSenderRecvPair(Event senderCnd, Event recvCnd) {
    if (senderCnd.isSend() && recvCnd.isRecv() && senderCnd.match(recvCnd)) {
      ArrayList<Event> recvs = _sends.get(senderCnd);
      if (recvs.isEmpty() || senderCnd.packH2O()==null ) {
        for (Event e : recvs)
          if (e._nodeId == recvCnd._nodeId)
            return false;
        return true;
      }
    }
    return false;
  }

  /**
   * Process new event. For sender, check if there are any blocked receives
   * waiting for this send. For receiver, try to find matching sender, otherwise
   * block.
   *
   * @param e
   */
  void processEvent(Event e) {
    assert !_processed;
    // Event e = _events[idx];
    if (e.isSend()) {
      _sends.put(e, new ArrayList<TimelineSnapshot.Event>());
      for (Event otherE : _events) {
        if ((otherE != null) && (otherE != e) && (!otherE.equals(e)) && otherE._blocked
          && otherE.match(e)) {
          _edges.put(otherE, e);
          _sends.get(e).add(otherE);
          otherE._blocked = false;
        }
      }
    } else { // look for matching send, otherwise set _blocked
      assert !_edges.containsKey(e);
      int senderIdx = e.packH2O().index();
      if (senderIdx < 0) { // binary search did not find member, should not happen?
        // no possible sender - return and do not block
        Log.warn("no sender found! port = " + e.portPack() + ", ip = " + e.addrPack().toString());
        return;
      }
      Event senderCnd = _events[senderIdx];
      if (senderCnd != null) {
        if (isSenderRecvPair(senderCnd, e)) {
          _edges.put(e, senderCnd.clone());
          _sends.get(senderCnd).add(e);
          return;
        }
        senderCnd = senderCnd.clone();
        while (senderCnd.prev()) {
          if (isSenderRecvPair(senderCnd, e)) {
            _edges.put(e, senderCnd);
            _sends.get(senderCnd).add(e);
            return;
          }
        }
      }
      e._blocked = true;
    }
    assert (e == null) || (e._eventIdx < TimeLine.MAX_EVENTS);
  }

  @Override
  public Iterator<TimelineSnapshot.Event> iterator() {
    return this;
  }

  /**
   * Just check if there is any non null non-issued event.
   */
  @Override
  public boolean hasNext() {
    for (int i = 0; i < _events.length; ++i)
      if (_events[i] != null && (!_events[i].isEmpty() || _events[i].next())) {
        assert (_events[i] == null)
          || ((_events[i]._eventIdx < TimeLine.MAX_EVENTS) && !_events[i].isEmpty());
        return true;
      } else {
        assert (_events[i] == null)
          || ((_events[i]._eventIdx < TimeLine.MAX_EVENTS) && !_events[i].isEmpty());
        _events[i] = null;
      }
    return false;
  }

  public Event getDependency(Event e) {
    return _edges.get(e);
  }

  /**
   * Get the next event of the timeline according to the ordering. Ordering is
   * performed in this method. Basically there are n ordered stream of events
   * with possible dependenencies caused by send/rcv relation.
   *
   * Sends are always eligible to be scheduled. Receives are eligible only if
   * their matching send was already issued. In situation when current events of
   * all streams are blocked (should not happen!) the oldest one is unblocked
   * and issued.
   *
   * Out of all eligible events, the smallest one (according to Event.compareTo)
   * is picked.
   */
  @Override
  public TimelineSnapshot.Event next() {
    if (!hasNext())
      throw new NoSuchElementException();
    int selectedIdx = -1;

    for (int i = 0; i < _events.length; ++i) {
      if (_events[i] == null || _events[i]._blocked)
        continue;
      if (_events[i].isRecv()) { // check edge dependency
        Event send = _edges.get(_events[i]);
        if ((send != null) && (_events[send._nodeId] != null)
          && send._eventIdx >= _events[send._nodeId]._eventIdx)
          continue;
      }
      selectedIdx = ((selectedIdx == -1) || _events[i]
        .compareTo(_events[selectedIdx]) < 0) ? i : selectedIdx;
    }
    if (selectedIdx == -1) { // we did not select anything -> all event streams
      // must be blocked return the oldest one (assuming
      // corresponding send was in previous snapshot)
      // System.out.println("*** all blocked ***");
      selectedIdx = 0;
      long selectedNs = (_events[selectedIdx] != null) ? _events[selectedIdx]
        .ns() : Long.MAX_VALUE;
      long selectedMs = (_events[selectedIdx] != null) ? _events[selectedIdx]
        .ms() : Long.MAX_VALUE;
      for (int i = 1; i < _events.length; ++i) {
        if (_events[i] == null)
          continue;

        if ((_events[i].ms() < selectedMs) && (_events[i].ns() < selectedNs)) {
          selectedIdx = i;
          selectedNs = _events[i].ns();
          selectedMs = _events[i].ms();
        }
      }
    }
    assert (selectedIdx != -1);
    assert (_events[selectedIdx] != null)
      && ((_events[selectedIdx]._eventIdx < TimeLine.MAX_EVENTS) && !_events[selectedIdx]
      .isEmpty());
    Event res = _events[selectedIdx];
    _events[selectedIdx] = _events[selectedIdx].nextEvent();
    if (_events[selectedIdx] != null && !_processed)
      processEvent(_events[selectedIdx]);
    // DEBUG
//    if (_processed)
//      if (res.isRecv())
//        System.out.println("# " + res + " PAIRED WITH "
//            + (_edges.containsKey(res) ? _edges.get(res) : "*** NONE ****"));
//      else
//        System.out.println("# " + res + " receivers: "
//            + _sends.get(res).toString());
    return res;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }
}
