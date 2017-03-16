package ai.h2o.automl;

import water.DKV;
import water.Key;
import water.Keyed;
import water.util.TwoDimTable;

import static water.Key.make;

public class UserFeedback extends Keyed<UserFeedback> {
  private UserFeedbackEvent[] feedbackEvents; // wish we had IcedArrayList(). . .

  public UserFeedback(Key<AutoML> runKey) {
    this._key = make(idForRun(runKey));

    UserFeedback old = DKV.getGet(this._key);

    if (null == old) {
      feedbackEvents = new UserFeedbackEvent[0];
      DKV.put(this);
    }
  }

  private UserFeedback() {
  }

  public static String idForRun(Key<AutoML> runKey) { return "AutoML_Feedback_" + runKey.toString(); }

  /** Add a Debug UserFeedbackEvent and log. */
  public void debug(UserFeedbackEvent.Stage stage, String message) {
    addEvent(new UserFeedbackEvent(UserFeedbackEvent.Level.Debug, stage, message));
  }

  /** Add a Info UserFeedbackEvent and log. */
  public void info(UserFeedbackEvent.Stage stage, String message) {
    addEvent(new UserFeedbackEvent(UserFeedbackEvent.Level.Info, stage, message));
  }

  /** Add a Warn UserFeedbackEvent and log. */
  public void warn(UserFeedbackEvent.Stage stage, String message) {
    addEvent(new UserFeedbackEvent(UserFeedbackEvent.Level.Warn, stage, message));
  }

  /** Add a UserFeedbackEvent, but don't log. */
  public void addEvent(UserFeedbackEvent.Level level, UserFeedbackEvent.Stage stage, String message) {
    addEvent(new UserFeedbackEvent(level, stage, message));
  }

  /** Add a UserFeedbackEvent, but don't log. */
  public void addEvent(UserFeedbackEvent event) {
    UserFeedbackEvent[] oldEvents = feedbackEvents;
    feedbackEvents = new UserFeedbackEvent[feedbackEvents.length + 1];
    System.arraycopy(oldEvents, 0, feedbackEvents, 0, oldEvents.length);
    feedbackEvents[oldEvents.length] = event;

    EckoClient.addEvent(event);

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
