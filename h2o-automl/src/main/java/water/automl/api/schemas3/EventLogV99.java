package water.automl.api.schemas3;

import ai.h2o.automl.events.EventLog;
import ai.h2o.automl.events.EventLogEntry;
import water.api.API;
import water.api.Schema;
import water.api.schemas3.TwoDimTableV3;
import water.logging.LoggingLevel;

import java.util.function.Predicate;
import java.util.stream.Stream;

public class EventLogV99 extends Schema<EventLog, EventLogV99> {
  
  @API(help="ID of the AutoML run for which the event log was recorded", direction=API.Direction.INOUT)
  public AutoMLV99.AutoMLKeyV3 automl_id;

  @API(help="List of events produced during the AutoML run", direction=API.Direction.OUTPUT)
  public EventLogEntryV99[] events;

  @API(help="A table representation of this event log, for easy rendering", direction=API.Direction.OUTPUT)
  public TwoDimTableV3 table;

  @API(help="Verbosity level of the returned event log", direction=API.Direction.INOUT,
          valuesProvider= EventLogEntryV99.LevelProvider.class)
  public LoggingLevel verbosity;

  @Override public EventLogV99 fillFromImpl(EventLog eventLog) {
    super.fillFromImpl(eventLog, new String[] { "events" });

    Predicate<EventLogEntry> predicate = (e) -> verbosity == null || e.getLevel().ordinal() >= verbosity.ordinal();
    if (null != eventLog._events) {
      events = Stream.of(eventLog._events.clone())
              .filter(predicate)
              .map(e -> new EventLogEntryV99().fillFromImpl(e))
              .toArray(EventLogEntryV99[]::new);
    }
    table = new TwoDimTableV3().fillFromImpl(eventLog.toTwoDimTable(predicate));
    return this;
  }
}
