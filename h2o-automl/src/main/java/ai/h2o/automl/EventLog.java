package ai.h2o.automl;

import water.DKV;
import water.Key;
import water.Keyed;
import water.util.Log;
import water.util.TwoDimTable;

import static water.Key.make;

public class EventLog extends Keyed<EventLog> {
  transient public AutoML autoML; // don't serialize
  public EventLogItem[] items;

  public EventLog(AutoML autoML) {
    this._key = make(idForRun(autoML._key));
    this.autoML = autoML;

    EventLog old = DKV.getGet(this._key);

    if (null == old || null == items) {
      items = new EventLogItem[0];
      DKV.put(this);
    }
  }

  public static String idForRun(Key<AutoML> runKey) {
    if (null == runKey)
      return "AutoML_Events_dummy";
    return "AutoML_Events_" + runKey.toString();
  }

  /** Add a Debug EventLogItem and log. */
  public void debug(EventLogItem.Stage stage, String message) {
    Log.debug(stage+": "+message);
    addEvent(new EventLogItem(autoML, EventLogItem.Level.Debug, stage, message));
  }

  /** Add a Info EventLogItem and log. */
  public void info(EventLogItem.Stage stage, String message) {
    Log.info(stage+": "+message);
    addEvent(new EventLogItem(autoML, EventLogItem.Level.Info, stage, message));
  }

  /** Add a Warn EventLogItem and log. */
  public void warn(EventLogItem.Stage stage, String message) {
    Log.warn(stage+": "+message);
    addEvent(new EventLogItem(autoML, EventLogItem.Level.Warn, stage, message));
  }

  /** Add a EventLogItem, but don't log. */
  public void addEvent(EventLogItem.Level level, EventLogItem.Stage stage, String message) {
    addEvent(new EventLogItem(autoML, level, stage, message));
  }

  /** Add a EventLogItem, but don't log. */
  public void addEvent(EventLogItem event) {
    EventLogItem[] oldEvents = items;
    items = new EventLogItem[items.length + 1];
    System.arraycopy(oldEvents, 0, items, 0, oldEvents.length);
    items[oldEvents.length] = event;
  } // addEvent

  /**
   * Delete everything in the DKV that this points to.  We currently need to be able to call this after deleteWithChildren().
   */
  public void delete() {
    items = new EventLogItem[0];
    remove();
  }

  public TwoDimTable toTwoDimTable() {
    return toTwoDimTable("AutoML Event Log");
  }

  public TwoDimTable toTwoDimTable(String tableHeader) {
    TwoDimTable table = EventLogItem.makeTwoDimTable(tableHeader, items.length);

    for (int i = 0; i < items.length; i++)
      items[i].addTwoDimTableRow(table, i);
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
