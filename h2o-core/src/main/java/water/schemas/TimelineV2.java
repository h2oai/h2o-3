package water.schemas;

import water.H2O;
import water.H2ONode;
import water.TimeLine;
import water.UDP;
import water.api.Timeline;
import water.util.TimelineSnapshot;

import java.util.ArrayList;

/**
 * Created by tomasnykodym on 6/5/14.
 */
public class TimelineV2 extends Schema<Timeline,TimelineV2> {
  // This schema has no input params

  @API(help="Current time in millis.")
  public long now;

  @API(help="This node")
  public String self;


  public static class Event {
    public enum EventType {heartbeat};
    public final EventType type;
    public Event(EventType type){this.type = type;}

  }

  @API(help="recorded timeline events")
  public Event [] events;

  @Override
  public TimelineV2 fillInto(Timeline timeline) {
    return this;
  }

  @Override
  public TimelineV2 fillFrom(Timeline timeline) {
    ArrayList<Event> outputEvents = new ArrayList<Event>();
    ArrayList<TimelineSnapshot.Event> heartbeats = new ArrayList();
    H2O cloud = TimeLine.getCLOUD();
    for(TimelineSnapshot.Event event:timeline.snapshot) {
      H2ONode h2o = cloud.members()[event._nodeId];
      // The event type.  First get payload.
      long l0 = event.dataLo();
      long h8 = event.dataHi();
      int udp_type = (int) (l0 & 0xff); // First byte is UDP packet type
      UDP.udp e = UDP.getUdp(udp_type);
      // Accumulate repeated heartbeats
      if (e == UDP.udp.heartbeat) {
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
        outputEvents.add(new Event(Event.EventType.heartbeat));
      }
    }
    events = outputEvents.toArray(new Event[outputEvents.size()]);
    return this;
  }
}
