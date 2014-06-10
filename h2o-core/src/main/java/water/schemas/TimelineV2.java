package water.schemas;

import water.*;
import water.api.Timeline;
import water.util.DocGen;
import water.util.TimelineSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * Created by tomasnykodym on 6/5/14.
 */
public class TimelineV2 extends Schema<Timeline,TimelineV2> {
  @API(help="Current time in millis.")
  public long now;

  @API(help="This node")
  public String self;


  public abstract static class Event extends Iced {
    @API(help="Time when the event was recorded. Format is hh:mm:ss:ms")
    public final String date;
    @API(help="Time in nanos")
    public final long nanos;
    public enum EventType {heartbeat, network_msg, io};
    @API(help="typ of recorded event")
    public final EventType type;
    public Event(EventType type, long millis, long nanos){
      this.type = type;
      this.date = sdf.format(new Date(millis));
      this.nanos = nanos;
    }
    public abstract String who();
    public abstract String ioType();
    public abstract String event();
    public abstract String bytes();
    @Override public DocGen.HTML writeHTML_impl( DocGen.HTML ab ) {ab.cell("haha"); return ab;}

  }

  public static class HeartBeatEvent extends Event {
    @API(help = "number of sent heartbeats")
    final int sends;
    @API(help = "number of received heartbeats")
    final int recvs;

    public HeartBeatEvent(int sends, int recvs, long lastMs, long lastNs){
      super(EventType.heartbeat,lastMs,lastNs);
      this.sends = sends;
      this.recvs = recvs;
    }
    @Override
    public String who() { return "many -> many";}
    @Override
    public String ioType() {return "UDP";}
    @Override
    public String event() {return "heartbeat";}
    @Override
    public String bytes() {return sends + " sent " + ", " + recvs + " received";}
    @Override public DocGen.HTML writeHTML_impl( DocGen.HTML ab ) {ab.cell("haha"); return ab;}

    @Override public String toString(){
      return "HeartBeat(" + sends + " sends, " + recvs + " receives)";
    }
  }

  public static class NetworkEvent extends Event {
    @API(help="Boolean flag distinguishing between sends (true) and receives(false)")
    public final boolean isSend;
    @API(help="network protocol (UDP/TCP)")
    public final String protocol;
    @API(help="UDP type(exec,ack, ackack,...")
    public final String msgType; // udp
    @API(help="Sending node")
    public final String from;
    @API(help="Receiving node")
    public final String to;
    @API(help="Pretty print of the first few bytes of the msg payload. Contains class name for tasks.")
    public final String data;

    public NetworkEvent(long ms, long ns,boolean isSend,String protocol, String msgType, String from, String to, String data){
      super(EventType.network_msg,ms,ns);
      this.isSend = isSend;
      this.protocol = protocol;
      this.msgType = msgType;
      this.from = from;
      this.to = to;
      this.data = data;
    }
    @Override
    public String who() { return from + " -> " + to;}
    @Override
    public String ioType() {return protocol;}
    @Override
    public String event() {return msgType;}

    @Override
    public String bytes() {return data;}

    @Override public String toString(){
      return "NetworkMsg(" + from + " -> " + to + ", protocol = '" + protocol +  "', data = '" + data + "')";
    }
  }

  public static class IOEvent extends Event {
    @API(help="flavor of the recorded io (ice/hdfs/...)")
    public final String ioFlavor;
    @API(help="node where this io event happened")
    public final String node;
    @API(help="data info")
    public final String data;
    public IOEvent(long ms, long ns, String node, String ioFlavor, String data){
      super(EventType.io,ms,ns);
      this.ioFlavor = ioFlavor;
      this.node = node;
      this.data = data;
    }
    @Override public String who(){return node;}
    @Override public String ioType() {return ioFlavor;}
    @Override public String event() {return "i_o";}
    @Override public String bytes() { return data;}
    @Override public String toString() {
      return "I_O('" + ioFlavor + "')";
    }
  }

  @API(help="recorded timeline events")
  public Event [] events;

  static SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss:SSS");

  @Override
  public TimelineV2 fillInto(Timeline timeline) {
    return this;
  }

  @Override
  public TimelineV2 fillFrom(Timeline timeline) {
    ArrayList<Event> outputEvents = new ArrayList<Event>();
    ArrayList<TimelineSnapshot.Event> heartbeats = new ArrayList();
    H2O cloud = TimeLine.CLOUD;
    for(TimelineSnapshot.Event event:timeline.snapshot) {
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
      if(msgType == UDP.udp.i_o) { // handle io event
        outputEvents.add(new IOEvent(ms,ns,event.recoH2O().toString(),event.ioflavor(),UDP.printx16(event.dataLo(),event.dataHi())));
      } else { // network msg
        String from, to;
        if( event.isSend() ) {
          from = h2o.toString();
          to = event.packH2O() == null ? "multicast" : event.packH2O().toString();
        } else {
          from = event.packH2O().toString();
          to = h2o.toString();
        }
        outputEvents.add(new NetworkEvent(ms,ns,event.isSend(),event.isTCP()?"TCP":"UDP",msgType.toString(),from,to,UDP.printx16(event.dataLo(),event.dataHi())));
      }
    }
    events = outputEvents.toArray(new Event[outputEvents.size()]);
    return this;
  }

  @Override public DocGen.HTML writeHTML_impl( DocGen.HTML ab ) {
    ab.title("Timeline");
    ab.bodyHead();
    ab.arrayHead(new String []{"hh:mm:ss:ms","nanosec","who","I/O Kind","event","bytes"});
    for(Event e:events)
      ab.arrayRow(new String[]{e.date,""+e.nanos,e.who(),e.ioType(),e.event(),e.bytes()});
    ab.arrayTail();
    ab.bodyTail();
    return ab;
  }
}
