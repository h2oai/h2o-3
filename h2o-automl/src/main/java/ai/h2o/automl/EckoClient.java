package ai.h2o.automl;

import hex.Model;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import water.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class EckoClient {
  private static int numEvents = 0;
  private static Map<String, ProjectStatus> statuses = new HashMap<>(); // remember the last status for each project

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
  private static final String projectPageDefs = "at %s def Feedback Timestamp Level Stage Message" +
          // " def LeaderboardModel ID Metric" +
//            " add Error in vis  input tsv  mark point  x Rank x-type O  y Error y-type Q" +
          // " add Error in vis  input tsv  mark point y Error y-type O" +
          " add Leaderboard in table" +
          " add \"User Feedback\" as map of Feedback" +
          feedbackTableStyle +
          feedbackRowStyle;
  private static final String projectTableStyle = " set Projects -font-weight 120";
  private static final String homePageDefs = "at / def BuildStatus Project Message PercentDone Leader" +
          " add Projects as map of BuildStatus" +
          projectTableStyle;

  private final static class ProjectStatus {
    public ProjectStatus(String project) {
      this.project = project;
    }

    final String project;
    public UserFeedbackEvent lastEvent;
    public double progress;
    public Leaderboard leaderboard;
    public Model leader;
    public String leaderMetric;
    public double leaderError;
  }

  private static void initializeEcko() {
    int httpStatus = -1;
    try {
      httpStatus = Request.Put(eckoHost)
              .connectTimeout(eckoTimeout)
              .socketTimeout(eckoTimeout)
              .bodyString(homePageDefs, ContentType.TEXT_PLAIN)
              .execute()
              .returnResponse()
              .getStatusLine()
              .getStatusCode();
    }
    catch (Exception e) {
      // handle below
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

  private static void initializeProjectPage(String project) {
    if (!eckoEnabled || statuses.containsKey(project)) return;

    int httpStatus = -1;
    statuses.put(project, new ProjectStatus(project));
    try {
      httpStatus = Request.Put(eckoHost)
              .connectTimeout(eckoTimeout)
              .socketTimeout(eckoTimeout)
              .bodyString(String.format(projectPageDefs, project), ContentType.TEXT_PLAIN)
              .execute()
              .returnResponse()
              .getStatusLine()
              .getStatusCode();
    }
    catch (Exception e) {
      // handle below
    }

    if (! (httpStatus == 200)) {
      eckoEnabled = false;
    }

    if (!eckoEnabled)
      Log.info(eckoFailedMessage);
  }


  public static final void addEvent(UserFeedbackEvent event) {
    if (eckoEnabled && !eckoInitialized) {
      initializeEcko();  // NOTE: can set eckoEnabled to false
    }

    if (eckoEnabled) {
      AutoML autoML = event.getAutoML();
      String project = autoML.project();
      initializeProjectPage(project);

      ProjectStatus status = statuses.get(statuses.get(event.getAutoML().project()));
      status.lastEvent = event;

      int httpStatus = -1;
      try {
        httpStatus = Request.Put(eckoHost)
                .connectTimeout(eckoTimeout)
                .socketTimeout(eckoTimeout)
                .bodyString("at %s put \"User Feedback\" ".format(project) +
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

      updateHomePage(status);
    } // eckoEnabled
  } // addEvent


  public static final void updateLeaderboard(Leaderboard leaderboard) {
    if (eckoEnabled && !eckoInitialized) {
      initializeEcko();  // NOTE: can set eckoEnabled to false
    }

    if (eckoEnabled) {
      String project = leaderboard.getProject();
      initializeProjectPage(project);

      ProjectStatus status = statuses.get(project);
      status.leaderboard = leaderboard;
      status.leader = leaderboard.getLeader();
      status.leaderError = leaderboard.defaultMetricForModel(status.leader)[0]; //First value is sort metric
      status.leaderMetric = leaderboard.defaultMetricNameForModel(status.leader)[0]; //First value is sort metric

      //String leaderboardTsv = leaderboard.toString(project, leaderboard.getModels(), "\\t", "\\n", false, true, true);
      String leaderboardTsv = leaderboard.toString(project, leaderboard.getModels(), "\\t", "\\n", false, true);
      String rankTsv = leaderboard.rankTsv();
      String timeTsv = leaderboard.timeTsv();

      int httpStatus = -1;
      try {
        httpStatus = Request.Put(eckoHost)
                .connectTimeout(eckoTimeout)
                .socketTimeout(eckoTimeout)
                .bodyString(
                        "at %s put Leaderboard \"".format(project) +
                        leaderboardTsv + "\"" +
//                        "at / put Error \"" +
//                        rankTsv + "\"",
                                "",
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

      updateHomePage(status);
    } // eckoEnabled
  } // updateLeaderboard

  public static final void updateProgress(AutoML autoML) {
    ProjectStatus status = statuses.get(autoML.project());
    double progress = (autoML.job() == null ? 0.0 : autoML.job().progress());
    status.progress = progress;
    updateHomePage(status);
  }

  public static final void updateHomePage(ProjectStatus status) {
    if (eckoEnabled && !eckoInitialized) {
      initializeEcko();  // NOTE: can set eckoEnabled to false
    }

    String project = status.project;
    if (eckoEnabled) {
      int httpStatus = -1;
      try {
        httpStatus = Request.Put(eckoHost)
                .connectTimeout(eckoTimeout)
                .socketTimeout(eckoTimeout)
                .bodyString(
                        "at %s put Leaderboard \"".format(project) +
//                                leaderboardTsv + "\"" +
//                        "at / put Error \"" +
//                        rankTsv + "\"",
                                "",
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
  } // updateLeaderboard

}
