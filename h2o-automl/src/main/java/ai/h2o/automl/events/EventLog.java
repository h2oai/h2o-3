package ai.h2o.automl.events;

import ai.h2o.automl.events.EventLogEntry.Level;
import ai.h2o.automl.events.EventLogEntry.Stage;
import water.DKV;
import water.Futures;
import water.Key;
import water.Keyed;
import water.util.Log;
import water.util.TwoDimTable;

import java.io.Serializable;


/**
 * EventLog instances store significant events occurring during an AutoML run.
 * Events are formatted with the intent of rendering on client side.
 */
public class EventLog extends Keyed<EventLog> {

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
    Log.debug(stage+": "+message);
    return addEvent(Level.Debug, stage, message);
  }

  /** Add a Info EventLogEntry and log. */
  public <V extends Serializable> EventLogEntry<V> info(Stage stage, String message) {
    Log.info(stage+": "+message);
    return addEvent(Level.Info, stage, message);
  }

  /** Add a Warn EventLogEntry and log. */
  public <V extends Serializable> EventLogEntry<V> warn(Stage stage, String message) {
    Log.warn(stage+": "+message);
    return addEvent(Level.Warn, stage, message);
  }

  /** Add a EventLogEntry, but don't log. */
  public <V extends Serializable> EventLogEntry<V> addEvent(Level level, Stage stage, String message) {
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
    String name = _runner_id == null ? "(new)" : _runner_id.toString();
    return toTwoDimTable("Event Log for:" + name);
  }

  public TwoDimTable toTwoDimTable(String tableHeader) {
    final EventLogEntry[] events = _events.clone();
    TwoDimTable table = EventLogEntry.makeTwoDimTable(tableHeader, events.length);

    for (int i = 0; i < events.length; i++)
      events[i].addTwoDimTableRow(table, i);
    return table;
  }

  public String toString(String tableHeader) {
    return this.toTwoDimTable(tableHeader).toString();
  }

  @Override
  public String toString() {
    return this.toTwoDimTable().toString();
  }
}
