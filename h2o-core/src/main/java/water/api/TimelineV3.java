package water.api;

import water.*;
import water.api.TimelineHandler.Timeline;
import water.init.TimelineSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/** Display of a Timeline
 *  Created by tomasnykodym on 6/5/14.
 */
public class TimelineV3 extends SchemaV3<Timeline,TimelineV3> {
  // This schema has no input params
  @API(help="Current time in millis.", direction=API.Direction.OUTPUT)
  private long now;

  @API(help="This node", direction=API.Direction.OUTPUT)
  private String self;

  @API(help="recorded timeline events", direction=API.Direction.OUTPUT)
  public EventV3[] events;

  public static class EventV3<I, S extends EventV3<I, S>> extends SchemaV3<Iced, S> {
    @API(help="Time when the event was recorded. Format is hh:mm:ss:ms")
    private final String date;

    @API(help="Time in nanos")
    private final long nanos;

    enum EventType {unknown, heartbeat, network_msg, io}
    @API(help="type of recorded event", values = {"unknown", "heartbeat", "network_msg", "io"})
    private final EventType type;

    @SuppressWarnings("unused")
    public EventV3() { date = null; nanos = -1; type = EventType.unknown; }
    private EventV3(EventType type, long millis, long nanos){
      this.type = type;
      this.date = new SimpleDateFormat("HH:mm:ss:SSS").format(new Date(millis));
      this.nanos = nanos;
    }

    protected String who() { throw H2O.unimpl(); };
    protected String ioType() { throw H2O.unimpl(); };
    protected String event() { throw H2O.unimpl(); };
    public    String bytes() { throw H2O.unimpl(); };
  } // Event

  private static class HeartBeatEvent extends EventV3<Iced, HeartBeatEvent> {
    @API(help = "number of sent heartbeats")
    final int sends;

    @API(help = "number of received heartbeats")
    final int recvs;

    public HeartBeatEvent() { super(); sends = -1; recvs = -1; }
    private HeartBeatEvent(int sends, int recvs, long lastMs, long lastNs){
      super(EventType.heartbeat,lastMs,lastNs);
      this.sends = sends;
      this.recvs = recvs;
    }
    @Override protected String who() { return "many -> many";}
    @Override protected String ioType() {return "UDP";}
    @Override protected String event() {return "heartbeat";}
    @Override public    String bytes() {return sends + " sent " + ", " + recvs + " received";}
    @Override public    String toString() { return "HeartBeat(" + sends + " sends, " + recvs + " receives)"; }
  } // HeartBeatEvent

  public static class NetworkEvent extends EventV3<Iced, NetworkEvent> {
    @API(help="Boolean flag distinguishing between sends (true) and receives(false)")
    public final boolean is_send;
    @API(help="network protocol (UDP/TCP)")
    private final String protocol;
    @API(help="UDP type (exec,ack, ackack,...")
    private final String msg_type; // udp
    @API(help="Sending node")
    public final String from;
    @API(help="Receiving node")
    public final String to;
    @API(help="Pretty print of the first few bytes of the msg payload. Contains class name for tasks.")
    private final String data;

    public NetworkEvent() { super(); is_send = false; protocol = "unknown"; msg_type = "unknown"; from = "unknown"; to = "unknown"; data = "unknown"; }
    private NetworkEvent(long ms, long ns, boolean is_send, String protocol, String msg_type, String from, String to, String data){
      super(EventType.network_msg,ms,ns);
      this.is_send = is_send;
      this.protocol = protocol;
      this.msg_type = msg_type;
      this.from = from;
      this.to = to;
      this.data = data;
    }
    @Override protected String who() { return from + " -> " + to;}
    @Override protected String ioType() {return protocol;}
    @Override protected String event() {return msg_type;}
    @Override public    String bytes() {return data;}
    @Override public    String toString() {
      return "NetworkMsg(" + from + " -> " + to + ", protocol = '" + protocol +  "', data = '" + data + "')";
    }
  } // NetworkEvent

  private static class IOEvent extends EventV3<Iced, IOEvent> {
    @API(help="flavor of the recorded io (ice/hdfs/...)")
    private final String io_flavor;
    @API(help="node where this io event happened")
    private final String node;
    @API(help="data info")
    private final String data;

    public IOEvent() { this(-1, -1, "unknown", "unknown", "unknown"); }
    private IOEvent(long ms, long ns, String node, String io_flavor, String data){
      super(EventType.io,ms,ns);
      this.io_flavor = io_flavor;
      this.node = node;
      this.data = data;
    }
    @Override protected String who(){return node;}
    @Override protected String ioType() {return io_flavor;}
    @Override protected String event() {return "i_o";}
    @Override public    String bytes() { return data;}
    @Override public    String toString() { return "I_O('" + io_flavor + "')"; }
  } // IOEvent

  @Override public TimelineV3 fillFromImpl(Timeline timeline) {
    ArrayList<EventV3> outputEvents = new ArrayList<>();
    ArrayList<TimelineSnapshot.Event> heartbeats = new ArrayList();
    H2O cloud = TimeLine.getCLOUD();

    if (null != timeline.snapshot) {
      for (TimelineSnapshot.Event event : timeline.snapshot) {
        H2ONode h2o = cloud.members()[event._nodeId];
        // The event type.  First get payload.
        UDP.udp msgType = event.udpType();
        // Accumulate repeated heartbeats
        if (msgType == UDP.udp.heartbeat) {
          heartbeats.add(event);
          continue;
        }
        // Now dump out accumulated heartbeats
        if (!heartbeats.isEmpty()) {
          long firstMs = heartbeats.get(0).ms();
          long lastMs = heartbeats.get(heartbeats.size() - 1).ms();
          int totalSends = 0;
          int totalRecvs = 0;
          int totalDrops = 0;
          int[] sends = new int[cloud.size()];
          int[] recvs = new int[cloud.size()];
          for (TimelineSnapshot.Event h : heartbeats) {
            if (h.isSend()) {
              ++totalSends;
              ++sends[h._nodeId];
            } else if (h.isDropped()) {
              ++totalDrops;
            } else {
              ++totalRecvs;
              ++recvs[h._nodeId];
            }
          }
          heartbeats.clear();
          outputEvents.add(new HeartBeatEvent(totalSends, totalRecvs, firstMs, lastMs));
        }
        long ms = event.ms();
        long ns = event.ns();
        if (msgType == UDP.udp.i_o) { // handle io event
          outputEvents.add(new IOEvent(ms, ns, event.recoH2O().toString(), event.ioflavor(), UDP.printx16(event.dataLo(), event.dataHi())));
        } else { // network msg
          String from, to;
          if (event.isSend()) {
            from = h2o.toString();
            to = event.packH2O() == null ? "multicast" : event.packH2O().toString();
          } else {
            from = event.packH2O().toString();
            to = h2o.toString();
          }
          outputEvents.add(new NetworkEvent(ms, ns, event.isSend(), event.isTCP() ? "TCP" : "UDP", msgType.toString(), from, to, UDP.printx16(event.dataLo(), event.dataHi())));
        }
      }
    } // if timeline.snapshot
    events = outputEvents.toArray(new EventV3[null == outputEvents ? 0 : outputEvents.size()]);
    return this;
  }
}
