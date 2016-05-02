package water;

import java.net.InetAddress;
import java.sql.Connection;
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
  private String modelJson;
  private static final String resultsDBTableName = "AccuracyTestCaseResults"; //TODO: get this from the connection instead
  private TestCase tc;
  private DataSet tr;
  private DataSet tt;

  public TestCaseResult(int testCaseId, HashMap<String,Double> trainingMetrics, HashMap<String,Double> testingMetrics,
                        double modelBuildTime, String modelJson, TestCase tc, DataSet tr, DataSet tt) throws Exception {
    this.testCaseId = testCaseId;
    this.trainingMetrics = trainingMetrics;
    this.testingMetrics = testingMetrics;
    this.modelBuildTime = modelBuildTime;
    this.modelJson = modelJson;
    this.tc = tc;
    this.tr = tr;
    this.tt = tt;

    this.ipAddr = InetAddress.getLocalHost().getCanonicalHostName();
    this.ncpu = Runtime.getRuntime().availableProcessors();
    this.h2oVersion = H2O.ABV.projectVersion();
    this.gitHash = H2O.ABV.lastCommitHash();
  }

  public void saveToAccuracyTable(Connection conn) throws Exception {
    String sql = makeSQLCmd();
    Statement statement = conn.createStatement();
    statement.executeUpdate(sql);
    AccuracyTestingSuite.summaryLog.println("Successfully executed the following sql statement: " + sql);
  }

  public void printValidationMetrics(boolean crossVal) {
    if (crossVal) { AccuracyTestingSuite.summaryLog.println("Cross Validation metrics:"); }
    else { AccuracyTestingSuite.summaryLog.println("Validation metrics:"); }
    for (String m : metrics) {
      AccuracyTestingSuite.summaryLog.println("Metric: " + m + ", Value: " + (testingMetrics.get(m) == null ||
              Double.isNaN(testingMetrics.get(m)) ? "NULL " : Double.toString(testingMetrics.get(m))));
    }
  }
  
  private String makeSQLCmd() {
    AccuracyTestingSuite.summaryLog.println("Making the sql statement.");
    String sql = String.format("insert into %s values(%s, ", resultsDBTableName, testCaseId);
    for (String m : metrics) {
      sql += (trainingMetrics.get(m) == null || Double.isNaN(trainingMetrics.get(m)) ? "NULL, " :
        Double.toString(trainingMetrics.get(m)) + ", ");
    }
    for (String m : metrics) {
      sql += (testingMetrics.get(m) == null || Double.isNaN(testingMetrics.get(m)) ? "NULL, " :
        Double.toString(testingMetrics.get(m)) + ", ");
    }
    sql += String.format("%s, '%s', '%s', '%s', %s, '%s', %s, '%s', '%s', '%s', %s, '%s', '%s', %s, '%s', '%s')",
            "NOW()", "H2O", h2oVersion, ipAddr, ncpu, gitHash, modelBuildTime, modelJson, tc.algo, tc.algoParameters,
            tc.grid, tc.gridParameters, tc.modelSelectionCriteria, tc.regression, tr.uri,tt.uri);
    return sql;
  }
}
