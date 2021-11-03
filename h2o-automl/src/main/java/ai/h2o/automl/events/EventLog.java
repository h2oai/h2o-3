package ai.h2o.automl.events;

import ai.h2o.automl.events.EventLogEntry.Stage;
import water.DKV;
import water.Futures;
import water.Key;
import water.Keyed;
import water.logging.Logger;
import water.logging.LoggerFactory;
import water.logging.LoggingLevel;
import water.util.TwoDimTable;

import java.io.Serializable;
import java.util.function.Predicate;
import java.util.stream.Stream;


/**
 * EventLog instances store significant events occurring during an AutoML run.
 * Events are formatted with the intent of rendering on client side.
 */
public class EventLog extends Keyed<EventLog> {
  
  private static final Logger log = LoggerFactory.getLogger(EventLog.class);

  public final Key _runner_id;
  public EventLogEntry[] _events;

  public EventLog(Key runKey) {
    _runner_id = runKey;
    _key = Key.make(idForRun(runKey));
    _events = new EventLogEntry[0];
  }

  public static EventLog getOrMake(Key runKey) {
    EventLog eventLog = DKV.getGet(Key.make(idForRun(runKey)));
    if (null == eventLog) {
      eventLog = new EventLog(runKey);
    }
    DKV.put(eventLog);
    return eventLog;
  }

  private static String idForRun(Key runKey) {
    if (null == runKey)
      return "Events_dummy";
    return "Events_" + runKey.toString();
  }

  /** Add a Debug EventLogEntry and log. */
  public <V extends Serializable> EventLogEntry<V> debug(Stage stage, String message) {
    log.debug(stage+": "+message);
    return addEvent(LoggingLevel.DEBUG, stage, message);
  }

  /** Add a Info EventLogEntry and log. */
  public <V extends Serializable> EventLogEntry<V> info(Stage stage, String message) {
    log.info(stage+": "+message);
    return addEvent(LoggingLevel.INFO, stage, message);
  }

  /** Add a Warn EventLogEntry and log. */
  public <V extends Serializable> EventLogEntry<V> warn(Stage stage, String message) {
    log.warn(stage+": "+message);
    return addEvent(LoggingLevel.WARN, stage, message);
  }

  /** Add an Error EventLogEntry and log. */
  public <V extends Serializable> EventLogEntry<V> error(Stage stage, String message) {
    log.error(stage+": "+message);
    return addEvent(LoggingLevel.ERROR, stage, message);
  }

  /** Add a EventLogEntry, but don't log. */
  public <V extends Serializable> EventLogEntry<V> addEvent(LoggingLevel level, Stage stage, String message) {
    EventLogEntry<V> entry = new EventLogEntry<>(_runner_id, level, stage, message);
    addEvent(entry);
    return entry;
  }

  /** Add a EventLogEntry, but don't log. */
  public void addEvent(EventLogEntry event) {
    EventLogEntry[] oldEvents = _events;
    EventLogEntry[] newEvents = new EventLogEntry[_events.length + 1];
    System.arraycopy(oldEvents, 0, newEvents, 0, oldEvents.length);
    newEvents[oldEvents.length] = event;
    _events = newEvents;
  } // addEvent

  /**
   * Delete object and its dependencies from DKV, including models.
   */
  @Override
  protected Futures remove_impl(Futures fs, boolean cascade) {
    _events = new EventLogEntry[0];
    return super.remove_impl(fs, cascade);
  }

  public TwoDimTable toTwoDimTable() {
    return toTwoDimTable(null);
  }
  
  public TwoDimTable toTwoDimTable(Predicate<EventLogEntry> predicate) {
    String name = _runner_id == null ? "(new)" : _runner_id.toString();
    return toTwoDimTable("Event Log for:" + name, predicate);
  }

  public TwoDimTable toTwoDimTable(String tableHeader, Predicate<EventLogEntry> predicate) {
    final EventLogEntry[] events = predicate == null 
            ? _events.clone() 
            : Stream.of(_events.clone())
                    .filter(predicate)
                    .toArray(EventLogEntry[]::new);
    TwoDimTable table = EventLogEntry.makeTwoDimTable(tableHeader, events.length);
    for (int i = 0; i < events.length; i++)
      events[i].addTwoDimTableRow(table, i);
    return table;
  }

  @Override
  public String toString() {
    return this.toTwoDimTable().toString();
  }
}
