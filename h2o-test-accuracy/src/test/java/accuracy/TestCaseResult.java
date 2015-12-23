package accuracy;

import java.sql.Connection;
import java.sql.DriverManager;
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

  public void saveToAccuracyDB() throws Exception {
    // Construct the sql command to be executed
    String sql = makeSQLCmd();

    // Connect to the Accuracy database
    Class.forName("com.mysql.jdbc.Driver");
    String url = String.format("jdbc:mysql://%s:%s/%s", AccuracyTestingUtil.accuracyDBHost,
      AccuracyTestingUtil.accuracyDBPort, AccuracyTestingUtil.accuracyDBName);
    Connection connection = DriverManager.getConnection(url, AccuracyTestingUtil.accuracyDBUser,
      AccuracyTestingUtil.accuracyDBPwd);

    // Execute the SQL command
    Statement statement = connection.createStatement();
    statement.executeUpdate(sql);
    connection.close();
    AccuracyTestingUtil.info("Successfully executed the following sql statement: " + sql);
  }

  private String makeSQLCmd() {
    String sql = String.format("insert into %s values(%s, ", AccuracyTestingUtil.accuracyDBTableName, testCaseId);
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
