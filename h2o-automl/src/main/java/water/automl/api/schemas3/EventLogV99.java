package water.automl.api.schemas3;

import ai.h2o.automl.events.EventLog;
import water.api.API;
import water.api.Schema;
import water.api.schemas3.TwoDimTableV3;

public class EventLogV99 extends Schema<EventLog, EventLogV99> {
  @API(help="ID of the AutoML run for which the event log was recorded", direction=API.Direction.INOUT)
  public AutoMLV99.AutoMLKeyV3 automl_id;

  @API(help="List of events produced during the AutoML run", direction=API.Direction.OUTPUT)
  public EventLogEntryV99[] events;

  @API(help="A table representation of this event log, for easy rendering", direction=API.Direction.OUTPUT)
  public TwoDimTableV3 table;

  @Override public EventLogV99 fillFromImpl(EventLog eventLog) {
    super.fillFromImpl(eventLog, new String[] { "events" });

    if (null != eventLog._events) {
      events = new EventLogEntryV99[eventLog._events.length];
      for (int i = 0; i < eventLog._events.length; i++)
        events[i] = new EventLogEntryV99().fillFromImpl(eventLog._events[i]);
    }
    table = new TwoDimTableV3().fillFromImpl(eventLog.toTwoDimTable());
    return this;
  }
}
