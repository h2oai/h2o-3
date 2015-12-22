package h2o.testng.utils;

import h2o.testng.AccuracyTestingFramework;
import water.util.Log;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;

public class TestCaseResult {
  private int testCaseId;
  private HashMap<String,Double> trainingMetrics, testingMetrics;
  private double modelBuildTime;
  private String ipAddr;
  private int ncpu;
  private String h2oVersion;
  private String gitHash;
  private static final String[] metrics = new String[]{ "R2", "Logloss", "MeanResidualDeviance", "AUC", "AIC", "Gini",
    "MSE", "ResidualDeviance", "ResidualDegreesOfFreedom", "NullDeviance", "NullDegreesOfFreedom", "F1", "F2",
    "F0point5", "Accuracy", "Error", "Precision", "Recall", "MCC", "MaxPerClassError"};

  public TestCaseResult(int testCaseId, HashMap<String,Double> trainingMetrics, HashMap<String,Double> testingMetrics,
                        double modelBuildTime, String ipAddr, int ncpu, String h2oVersion, String gitHash) {
    this.testCaseId = testCaseId;
    this.trainingMetrics = trainingMetrics;
    this.testingMetrics = testingMetrics;
    this.modelBuildTime = modelBuildTime;
    this.ipAddr = ipAddr;
    this.ncpu = ncpu;
    this.h2oVersion = h2oVersion;
    this.gitHash = gitHash;
  }

  public void saveToAccuracyDB() {
    // Construct the sql command to be executed
    String sql = makeSQLCmd();

    // Connect to the Accuracy database
    try { Class.forName("com.mysql.jdbc.Driver");
    } catch (ClassNotFoundException e) { e.printStackTrace(); }
    String url = String.format("jdbc:mysql://%s:%s/%s", AccuracyTestingFramework.accuracyDBHost,
      AccuracyTestingFramework.accuracyDBPort, AccuracyTestingFramework.accuracyDBName);
    Connection connection = null;
    try {
      connection = DriverManager.getConnection(url, AccuracyTestingFramework.accuracyDBUser,
        AccuracyTestingFramework.accuracyDBPwd);
    } catch (SQLException e) {
      Log.err("Unable to connect to Accuracy database.");;
      e.printStackTrace();
    }

    // Execute the SQL command
    boolean sqlSuccess = true;
    Statement statement = null;
    try { statement = connection.createStatement();
    } catch (SQLException e) {
      sqlSuccess = false;
      Log.err("Unable to create sql statement.");
      e.printStackTrace();
    }
    try { statement.executeUpdate(sql);
    } catch (SQLException e) {
      sqlSuccess = false;
      Log.err("Unable to execute sql statement: " + sql);
      e.printStackTrace();
    }
    try {
      connection.close();
    } catch (SQLException e) {
      sqlSuccess = false;
      Log.err("Unable to close sql connection");
      e.printStackTrace();
    }
    if (sqlSuccess) Log.info("Successfully executed the following sql statement: " + sql);
  }

  private String makeSQLCmd() {
    String sql = String.format("insert into %s values(%s, ", AccuracyTestingFramework.accuracyDBTableName, testCaseId);
    for (String m : metrics) {
      sql += (trainingMetrics.get(m) == null || Double.isNaN(trainingMetrics.get(m)) ? "NULL, " :
        Double.toString(trainingMetrics.get(m)) + ", ");
    }
    for (String m : metrics) {
      sql += (testingMetrics.get(m) == null || Double.isNaN(testingMetrics.get(m)) ? "NULL, " :
        Double.toString(testingMetrics.get(m)) + ", ");
    }
    sql += String.format("%s, '%s', '%s', '%s', %s, '%s', %s)", "NOW()", "H2O", h2oVersion, ipAddr, ncpu, gitHash,
      modelBuildTime);
    return sql;
  }
}
