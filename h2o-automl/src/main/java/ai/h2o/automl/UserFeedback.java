package ai.h2o.automl;

import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import water.DKV;
import water.Key;
import water.Keyed;
import water.util.Log;
import water.util.TwoDimTable;

import java.text.SimpleDateFormat;
import java.util.Date;

import static water.Key.make;

public class UserFeedback extends Keyed<UserFeedback> {
  private UserFeedbackEvent[] feedbackEvents; // wish we had IcedArrayList(). . .

  private final String eckoHelpMessage = "Ecko server is NOT enabled for user feedback updates.  To use Ecko, start a server on localhost:55555 before you run your AutoML-enabled h2o.";
  private final String eckoFailedMessage = "Ecko server failed.  Disabling.";
  private final String eckoHost = "http://localhost:55555/";

  private boolean eckoEnabled = true;
  private boolean eckoInitialized = false;

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

  private final String feedbackCellStyle = "-font-size 0.8em";
  private final String feedbackRowStyle = " set Feedback Timestamp "
                                          + feedbackCellStyle
                                          + " set Feedback Level "
                                          + feedbackCellStyle
                                          + " set Feedback Stage "
                                          + feedbackCellStyle
                                          + " set Feedback Message "
                                          + feedbackCellStyle;
  private final String feedbackTableStyle = " set \"User Feedback\" -font-weight 120";

  private void initializeEcko() {
    int httpStatus = -1;
    try {
      httpStatus = Request.Put(eckoHost)
              .connectTimeout(1000)
              .socketTimeout(1000)
              .bodyString("at / def Feedback Timestamp Level Stage Message add \"User Feedback\" as map of Feedback" + feedbackTableStyle + feedbackRowStyle, ContentType.TEXT_PLAIN)
              .execute()
              .returnResponse()
              .getStatusLine()
              .getStatusCode();
    }
    catch (Exception e) {

    }

    if (httpStatus == 200) {
      Log.info("Ecko server is enabled for user feedback updates.  Open http://localhost:55555 in your browser.");
      eckoInitialized = true;
    } else {
      eckoEnabled = false;
    }

    if (!eckoEnabled)
      Log.info(eckoHelpMessage);
  }

  private static final SimpleDateFormat timestampFormat = new SimpleDateFormat("HH:mm:ss.S");

  /** Add a UserFeedbackEvent, but don't log. */
  public void addEvent(UserFeedbackEvent event) {
    UserFeedbackEvent[] oldEvents = feedbackEvents;
    feedbackEvents = new UserFeedbackEvent[feedbackEvents.length + 1];
    System.arraycopy(oldEvents, 0, feedbackEvents, 0, oldEvents.length);
    feedbackEvents[oldEvents.length] = event;

    if (eckoEnabled && !eckoInitialized) {
      initializeEcko();
    }

    if (eckoEnabled) {
      int httpStatus = -1;
      try {
        httpStatus = Request.Put(eckoHost)
                .connectTimeout(1000)
                .socketTimeout(1000)
                .bodyString("at / put \"User Feedback\" " +
                        String.format("%1$05d", feedbackEvents.length - 1) + " " +
                        timestampFormat.format(new Date(event.getTimestamp())) + " " +
                        event.getLevel() + " " +
                        event.getStage() + " " +
                        "\"" + event.getMessage() + "\"",
                        ContentType.TEXT_PLAIN)
                .execute()
                .returnResponse()
                .getStatusLine()
                .getStatusCode();
      } catch (Exception e) {
      }

      if (httpStatus == 200) {
        // silent
      } else {
        eckoEnabled = false;
        Log.info(eckoFailedMessage);
      }
    } // eckoEnabled
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
