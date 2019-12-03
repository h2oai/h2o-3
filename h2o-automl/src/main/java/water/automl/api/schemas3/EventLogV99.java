package water.automl.api.schemas3;

import ai.h2o.automl.events.EventLog;
import water.api.API;
import water.api.Schema;

public class EventLogV99 extends Schema<EventLog, EventLogV99> {
  @API(help="ID of the AutoML run for which the event log was recorded", direction=API.Direction.INOUT)
  public AutoMLV99.AutoMLKeyV3 automl_id;

  @API(help="List of events produced during the AutoML run", direction=API.Direction.OUTPUT)
  public EventLogEntryV99[] events;

  @Override public EventLogV99 fillFromImpl(EventLog eventLog) {
    super.fillFromImpl(eventLog, new String[] { "events" });

    if (null != eventLog._events) {
      this.events = new EventLogEntryV99[eventLog._events.length];
      for (int i = 0; i < eventLog._events.length; i++)
        this.events[i] = new EventLogEntryV99().fillFromImpl(eventLog._events[i]);
    }

    return this;
  }
}
