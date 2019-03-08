package ai.h2o.automl;

import water.DKV;
import water.Key;
import water.Keyed;
import water.util.Log;
import water.util.TwoDimTable;

import static water.Key.make;

public class UserFeedback extends Keyed<UserFeedback> {
  transient public AutoML autoML; // don't serialize
  public UserFeedbackEvent[] feedbackEvents; // wish we had IcedArrayList(). . .

  public UserFeedback(AutoML autoML) {
    this._key = make(idForRun(autoML._key));
    this.autoML = autoML;

    UserFeedback old = DKV.getGet(this._key);

    if (null == old || null == feedbackEvents) {
      feedbackEvents = new UserFeedbackEvent[0];
      DKV.put(this);
    }
  }

  public static String idForRun(Key<AutoML> runKey) {
    if (null == runKey)
      return "AutoML_Feedback_dummy";
    return "AutoML_Feedback_" + runKey.toString();
  }

  /** Add a Debug UserFeedbackEvent and log. */
  public void debug(UserFeedbackEvent.Stage stage, String message) {
    Log.debug(stage+": "+message);
    addEvent(new UserFeedbackEvent(autoML, UserFeedbackEvent.Level.Debug, stage, message));
  }

  /** Add a Info UserFeedbackEvent and log. */
  public void info(UserFeedbackEvent.Stage stage, String message) {
    Log.info(stage+": "+message);
    addEvent(new UserFeedbackEvent(autoML, UserFeedbackEvent.Level.Info, stage, message));
  }

  /** Add a Warn UserFeedbackEvent and log. */
  public void warn(UserFeedbackEvent.Stage stage, String message) {
    Log.warn(stage+": "+message);
    addEvent(new UserFeedbackEvent(autoML, UserFeedbackEvent.Level.Warn, stage, message));
  }

  /** Add a UserFeedbackEvent, but don't log. */
  public void addEvent(UserFeedbackEvent.Level level, UserFeedbackEvent.Stage stage, String message) {
    addEvent(new UserFeedbackEvent(autoML, level, stage, message));
  }

  /** Add a UserFeedbackEvent, but don't log. */
  public void addEvent(UserFeedbackEvent event) {
    UserFeedbackEvent[] oldEvents = feedbackEvents;
    feedbackEvents = new UserFeedbackEvent[feedbackEvents.length + 1];
    System.arraycopy(oldEvents, 0, feedbackEvents, 0, oldEvents.length);
    feedbackEvents[oldEvents.length] = event;
  } // addEvent

  /**
   * Delete everything in the DKV that this points to.  We currently need to be able to call this after deleteWithChildren().
   */
  public void delete() {
    feedbackEvents = new UserFeedbackEvent[0];
    remove();
  }

  public TwoDimTable toTwoDimTable() {
    return toTwoDimTable("User Feedback");
  }

  public TwoDimTable toTwoDimTable(String tableHeader) {
    TwoDimTable table = UserFeedbackEvent.makeTwoDimTable(tableHeader, feedbackEvents.length);

    for (int i = 0; i < feedbackEvents.length; i++)
      feedbackEvents[i].addTwoDimTableRow(table, i);
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
