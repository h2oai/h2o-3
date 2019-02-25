package water.automl.api.schemas3;

import ai.h2o.automl.AutoML;
import ai.h2o.automl.EventLog;
import water.api.API;
import water.api.Schema;

public class EventLogV99 extends Schema<EventLog, EventLogV99> {
  @API(help="ID of the AutoML run for which the event log was recorded", direction=API.Direction.INOUT)
  public AutoML.AutoMLKeyV3 automl_id;

  @API(help="List of events produced during the AutoML run", direction=API.Direction.OUTPUT)
  public EventLogEntryV99[] events;

  @Override public EventLogV99 fillFromImpl(EventLog eventLog) {
    super.fillFromImpl(eventLog, new String[] { "automl_id", "events" });

    if (null != eventLog.autoML) {
      this.automl_id = new AutoML.AutoMLKeyV3(eventLog.autoML._key);
    }

    if (null != eventLog.events) {
      this.events = new EventLogEntryV99[eventLog.events.length];
      for (int i = 0; i < eventLog.events.length; i++)
        this.events[i] = new EventLogEntryV99().fillFromImpl(eventLog.events[i]);
    }

    return this;
  }
}
