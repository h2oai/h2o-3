package ai.h2o.automl;

import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import water.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;

public class EckoClient {
  private static int numEvents = 0;

  private static final int eckoTimeout = 10000;
  private static final String eckoHelpMessage = "Ecko server is NOT enabled for user feedback updates.  To use Ecko, start a server on localhost:55555 before you run your AutoML-enabled h2o.";
  private static final String eckoFailedMessage = "Ecko server failed.  Disabling.";
  private static final String eckoExceptionMessage = "Caught exception trying to communicate with the Ecko server: ";
  private static final String eckoHost = "http://localhost:55555/";

  private static boolean eckoEnabled = true;
  private static boolean eckoInitialized = false;

  private static final SimpleDateFormat timestampFormat = new SimpleDateFormat("HH:mm:ss.SSS");

  private static final String feedbackCellStyle = "-font-size 0.8em";
  private static final String feedbackRowStyle = " set Feedback Timestamp "
                                                 + feedbackCellStyle
                                                 + " set Feedback Level "
                                                 + feedbackCellStyle
                                                 + " set Feedback Stage "
                                                 + feedbackCellStyle
                                                 + " set Feedback Message "
                                                 + feedbackCellStyle;
  private static final String feedbackTableStyle = " set \"User Feedback\" -font-weight 120";

  private static void initializeEcko() {
    int httpStatus = -1;
    try {
      httpStatus = Request.Put(eckoHost)
              .connectTimeout(eckoTimeout)
              .socketTimeout(eckoTimeout)
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

  public static final void addEvent(UserFeedbackEvent event) {
    if (eckoEnabled && !eckoInitialized) {
      initializeEcko();
    }

    if (eckoEnabled) {
      int httpStatus = -1;
      try {
        httpStatus = Request.Put(eckoHost)
                .connectTimeout(eckoTimeout)
                .socketTimeout(eckoTimeout)
                .bodyString("at / put \"User Feedback\" " +
                        String.format("%1$05d", numEvents++) + " " +
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
        Log.info(eckoExceptionMessage + e);
      }

      if (httpStatus == 200) {
        // silent
      } else {
        eckoEnabled = false;
        Log.info(eckoFailedMessage);
      }
    } // eckoEnabled
  }
}
