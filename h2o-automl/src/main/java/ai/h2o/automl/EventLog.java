package ai.h2o.automl;

import ai.h2o.automl.EventLogEntry.Level;
import ai.h2o.automl.EventLogEntry.Stage;
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

  public Key<AutoML> _automlKey;
  public EventLogEntry[] _events;

  public EventLog(Key<AutoML> automlKey) {
    _automlKey = automlKey;
    _key = Key.make(idForRun(automlKey));
    _events = new EventLogEntry[0];
  }

  static EventLog getOrMake(Key<AutoML> runKey) {
    EventLog eventLog = DKV.getGet(Key.make(idForRun(runKey)));
    if (null == eventLog) {
      eventLog = new EventLog(runKey);
    }
    DKV.put(eventLog);
    return eventLog;
  }

  static EventLog make(Key<AutoML> runKey) {
    EventLog eventLog = new EventLog(runKey);
    DKV.put(eventLog);
    return eventLog;
  }

  private static String idForRun(Key<AutoML> runKey) {
    if (null == runKey)
      return "AutoML_Events_dummy";
    return "AutoML_Events_" + runKey.toString();
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
    EventLogEntry<V> entry = new EventLogEntry<>(_automlKey, level, stage, message);
    addEvent(entry);
    return entry;
  }

  /** Add a EventLogEntry, but don't log. */
  public void addEvent(EventLogEntry event) {
    EventLogEntry[] oldEvents = _events;
    _events = new EventLogEntry[_events.length + 1];
    System.arraycopy(oldEvents, 0, _events, 0, oldEvents.length);
    _events[oldEvents.length] = event;
  } // addEvent

  /**
   * Delete object and its dependencies from DKV, including models.
   */
  @Override
  protected Futures remove_impl(Futures fs) {
    _events = new EventLogEntry[0];
    return super.remove_impl(fs);
  }

  public TwoDimTable toTwoDimTable() {
    String name = _automlKey == null ? "(new)" : _automlKey.toString();
    return toTwoDimTable("Event Log for AutoML:" + name);
  }

  public TwoDimTable toTwoDimTable(String tableHeader) {
    TwoDimTable table = EventLogEntry.makeTwoDimTable(tableHeader, _events.length);

    for (int i = 0; i < _events.length; i++)
      _events[i].addTwoDimTableRow(table, i);
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
