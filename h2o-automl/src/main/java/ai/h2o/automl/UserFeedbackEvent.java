package ai.h2o.automl;

import water.Iced;
import water.util.TwoDimTable;

import java.text.SimpleDateFormat;
import java.util.Date;

public class UserFeedbackEvent extends Iced {
  public enum Level {
    Debug, Info, Warn;
  }

  public enum Stage {
    Workflow,
    DataImport,
    FeatureAnalysis,
    FeatureReduction,
    FeatureCreation,
    ModelTraining,
  }

  private long timestamp;
  transient private AutoML autoML;
  private Level level;
  private Stage stage;
  private String message;

  public long getTimestamp() {
    return timestamp;
  }

  public AutoML getAutoML() { return autoML; }

  public Level getLevel() {
    return level;
  }

  public Stage getStage() {
    return stage;
  }

  public String getMessage() {
    return message;
  }

  public UserFeedbackEvent(AutoML autoML, Level level, Stage stage, String message) {
    this.timestamp = System.currentTimeMillis();
    this.autoML = autoML;
    this.level = level;
    this.stage = stage;
    this.message = message;
  }


  protected static final String[] colHeaders = { "timestamp",
                                                 "level",
                                                 "stage",
                                                 "message" };

  protected static final String[] colTypes= { "string",
                                              "string",
                                              "string",
                                              "string" };

  protected static final String[] colFormats= { "%s",
                                                "%s",
                                                "%s",
                                                "%s" };

  public static final TwoDimTable makeTwoDimTable(String tableHeader, int length) {
    String[] rowHeaders = new String[length];
    for (int i = 0; i < length; i++) rowHeaders[i] = "" + i;

    return new TwoDimTable(tableHeader,
                           "Actions taken and discoveries made by AutoML",
                           rowHeaders,
                           UserFeedbackEvent.colHeaders,
                           UserFeedbackEvent.colTypes,
                           UserFeedbackEvent.colFormats, "#");
  }

  private static final SimpleDateFormat timestampFormat = new SimpleDateFormat("HH:mm:ss.S");
  public void addTwoDimTableRow(TwoDimTable table, int row) {
    int col = 0;
    table.set(row, col++, timestampFormat.format(new Date(timestamp)));
    table.set(row, col++, level);
    table.set(row, col++, stage);
    table.set(row, col++, message);
  }
}
