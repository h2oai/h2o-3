package ai.h2o.automl;

import ai.h2o.automl.EventLogEntry.Level;
import ai.h2o.automl.EventLogEntry.Stage;
import water.DKV;
import water.Key;
import water.Keyed;
import water.util.Log;
import water.util.TwoDimTable;

import java.io.Serializable;

import static water.Key.make;

/**
 * EventLog instances store significant events occurring during an AutoML run.
 * Events are formatted with the intent of rendering on client side.
 */
public class EventLog extends Keyed<EventLog> {

  public EventLogEntry[] events;
  transient public AutoML autoML; // don't serialize

  public EventLog(AutoML autoML) {
    this._key = make(idForRun(autoML._key));
    this.autoML = autoML;

    EventLog old = DKV.getGet(this._key);

    if (null == old || null == events) {
      events = new EventLogEntry[0];
      DKV.put(this);
    }
  }

  public static String idForRun(Key<AutoML> runKey) {
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
    EventLogEntry<V> entry = new EventLogEntry<>(autoML, level, stage, message);
    addEvent(entry);
    return entry;
  }

  /** Add a EventLogEntry, but don't log. */
  public void addEvent(EventLogEntry event) {
    EventLogEntry[] oldEvents = events;
    events = new EventLogEntry[events.length + 1];
    System.arraycopy(oldEvents, 0, events, 0, oldEvents.length);
    events[oldEvents.length] = event;
  } // addEvent

  /**
   * Delete everything in the DKV that this points to.  We currently need to be able to call this after deleteWithChildren().
   */
  public void delete() {
    events = new EventLogEntry[0];
    remove();
  }

  public TwoDimTable toTwoDimTable() {
    return toTwoDimTable("AutoML Event Log");
  }

  public TwoDimTable toTwoDimTable(String tableHeader) {
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
