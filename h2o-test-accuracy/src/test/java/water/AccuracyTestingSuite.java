package water;

import org.testng.annotations.*;
import water.util.Log;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;

public class AccuracyTestingSuite {
  private String logDir;
  private String resultsDBTableConfig;
  private int numH2ONodes;
  private String dataSetsCSVPath;
  private String testCasesCSVPath;
  private String testCasesFilterString;

  public static PrintStream summaryLog;
  private Connection resultsDBTableConn;
  private boolean recordResults;
  public static List<String> dataSetsCSVRows;
  private ArrayList<TestCase> testCasesList;

  @BeforeClass
  @Parameters( {"logDir", "resultsDBTableConfig", "numH2ONodes", "dataSetsCSVPath", "testCasesCSVPath",
      "testCasesFilterString" } )
      private void accuracySuiteSetup(@org.testng.annotations.Optional("h2o-test-accuracy") String logDir,
                                      @org.testng.annotations.Optional("") String resultsDBTableConfig,
                                      @org.testng.annotations.Optional("1") String numH2ONodes,
                                      @org.testng.annotations.Optional("h2o-test-accuracy/src/test/resources/accuracyDataSets.csv")
                                      String dataSetsCSVPath,
                                      @org.testng.annotations.Optional("h2o-test-accuracy/src/test/resources/accuracyTestCases.csv")
                                      String testCasesCSVPath,
                                      @org.testng.annotations.Optional("") String testCasesFilterString)
  {
    // Logging
    this.logDir = logDir;
    File resultsDir = null, h2oLogsDir = null;
    try {
      resultsDir = new File(AccuracyTestingUtil.find_test_file_static(logDir).getCanonicalFile().toString() + "/results");
      h2oLogsDir = new File(AccuracyTestingUtil.find_test_file_static(logDir).getCanonicalFile().toString() + "/results/h2ologs");
    } catch (IOException e) {
      System.out.println("Couldn't create directory.");
      e.printStackTrace();
      System.exit(-1);
    }
    resultsDir.mkdir();
    for(File f: resultsDir.listFiles()) f.delete();
    h2oLogsDir.mkdir();
    for(File f: h2oLogsDir.listFiles()) f.delete();

    File suiteSummary;
    try {
      suiteSummary = new File(AccuracyTestingUtil.find_test_file_static(logDir).getCanonicalFile().toString() +
                              "/results/accuracySuiteSummary.log");
      suiteSummary.createNewFile();
      summaryLog = new PrintStream(new FileOutputStream(suiteSummary, false));
    } catch (IOException e) {
      System.out.println("Couldn't create the accuracySuiteSummary.log");
      e.printStackTrace();
      System.exit(-1);
    }
    System.out.println("Commenced logging to h2o-test-accuracy/results directory.");

    // Results database table
    this.resultsDBTableConfig = resultsDBTableConfig;
    if (this.resultsDBTableConfig.isEmpty()) {
      summaryLog.println("No results database configuration specified, so test case results will not be saved.");
      recordResults = false;
    } else {
      summaryLog.println("Results database configuration specified specified by: " + this.resultsDBTableConfig);
      resultsDBTableConn = makeResultsDBTableConn();
      recordResults = true;
    }

    // H2O Cloud
    this.numH2ONodes = Integer.parseInt(numH2ONodes);
    summaryLog.println("Setting up the H2O Cloud with " + this.numH2ONodes + " nodes.");;
    AccuracyTestingUtil.setupH2OCloud(this.numH2ONodes, this.logDir);

    // Data sets
    this.dataSetsCSVPath = dataSetsCSVPath;
    File dataSetsFile = AccuracyTestingUtil.find_test_file_static(this.dataSetsCSVPath);
    try {
      dataSetsCSVRows = Files.readAllLines(dataSetsFile.toPath(), Charset.defaultCharset());
    } catch (IOException e) {
      summaryLog.println("Cannot read the lines of the the data sets file: " + dataSetsFile.toPath());
      writeStackTrace(e,summaryLog);
      System.exit(-1);
    }
    dataSetsCSVRows.remove(0); // remove the header

    // Test Cases
    this.testCasesCSVPath = testCasesCSVPath;
    this.testCasesFilterString = testCasesFilterString;
    testCasesList = makeTestCasesList();
  }

  @Test
  public void accuracyTest() {
    TestCase tc = null;
    TestCaseResult tcResult;
    int id;
    boolean suiteFailure = false;
    Iterator i = testCasesList.iterator();
    while(i.hasNext()) {
      tc = (TestCase) i.next();
      id = tc.getTestCaseId();
      try {
        //removeAll();

        summaryLog.println("\n-----------------------------");
        summaryLog.println("Accuracy Suite Test Case: " + id);
        summaryLog.println("-----------------------------\n");

        Log.info("-----------------------------");
        Log.info("Accuracy Suite Test Case: " + id);
        Log.info("-----------------------------");

        tcResult = tc.execute();

        tcResult.printValidationMetrics(tc.isCrossVal());
        if (recordResults) {
          summaryLog.println("Recording test case " + id + " result.");
          tcResult.saveToAccuracyTable(resultsDBTableConn);
        }

      } catch (Exception e) {
        StringWriter stringWriter = new StringWriter();
        e.printStackTrace(new PrintWriter(stringWriter));
        String stackTraceString = stringWriter.toString();

        Log.err("Test case " + id + " failed on: ");
        Log.err(stackTraceString);

        summaryLog.println("Test case " + id + " failed on: ");
        summaryLog.println(stackTraceString);

        suiteFailure = true;
      } catch (AssertionError ae) {
        Log.err("Test case " + id + " failed on: ");
        Log.err(ae.getMessage());

        summaryLog.println("Test case " + id + " failed on: ");
        summaryLog.println(ae.getMessage());

        suiteFailure = true;
      }
    }

    if (suiteFailure) {
      System.out.println("The suite failed due to one or more test case failures.");
      System.exit(-1);
    }
  }

  private ArrayList<TestCase> makeTestCasesList() {
    String[] algorithms = filterForAlgos(testCasesFilterString);
    String[] testCases = filterForTestCases(testCasesFilterString);
    List<String> testCaseEntries = null;

    try {
      summaryLog.println("Reading test cases from: " + testCasesCSVPath);
      File testCasesFile = AccuracyTestingUtil.find_test_file_static(this.testCasesCSVPath);
      testCaseEntries = Files.readAllLines(testCasesFile.toPath(), Charset.defaultCharset());
    }
    catch (Exception e) {
      summaryLog.println("Cannot read the test cases from: " + testCasesCSVPath);
      writeStackTrace(e,summaryLog);
      System.exit(-1);
    }

    testCaseEntries.remove(0); // remove header line
    ArrayList<TestCase> testCaseArray = new ArrayList<>();
    String[] testCaseEntry;
    for (String t : testCaseEntries) {
      testCaseEntry = t.trim().split(",", -1);

      // If algorithms are specified in the testCaseFilterString, load all test cases for these algorithms. Otherwise,
      // if specific test cases are specified, then only load those. Else, load all the test cases.
      if (null != algorithms) {
        if (!Arrays.asList(algorithms).contains(testCaseEntry[1])) { continue; }
      }
      else if (null != testCases) {
        if (!Arrays.asList(testCases).contains(testCaseEntry[0])) { continue; }
      }
      summaryLog.println("Creating test case: " + t);
      try {
        testCaseArray.add(
                          new TestCase(Integer.parseInt(testCaseEntry[0]), testCaseEntry[1], testCaseEntry[2],
                                  testCaseEntry[3].equals("1"), testCaseEntry[4], testCaseEntry[5], testCaseEntry[6],
                                  testCaseEntry[7].equals("1"), Integer.parseInt(testCaseEntry[8]),
                                  Integer.parseInt(testCaseEntry[9]), testCaseEntry[10])
                          );
      } catch (Exception e) {
        summaryLog.println("Couldn't create test case: " + t);
        writeStackTrace(e, summaryLog);
        System.exit(-1);
      }
    }
    return testCaseArray;
  }

  private String[] filterForAlgos(String selectionString) {
    if (selectionString.isEmpty()) return null;
    String algoSelectionString = selectionString.trim().split(";", -1)[0];
    if (algoSelectionString.isEmpty()) return null;
    return algoSelectionString.trim().split(",", -1);
  }

  private String[] filterForTestCases(String selectionString) {
    if (selectionString.isEmpty()) return null;
    String testCaseSelectionString = selectionString.trim().split(";", -1)[1];
    if (null == testCaseSelectionString || testCaseSelectionString.isEmpty()) return null;
    return testCaseSelectionString.trim().split(",", -1);
  }

  private Connection makeResultsDBTableConn() {
    Connection connection = null;
    try {
      summaryLog.println("Reading the database configuration settings from: " + resultsDBTableConfig);
      File configFile = new File(resultsDBTableConfig);
      Properties properties = new Properties();
      properties.load(new BufferedReader(new FileReader(configFile)));

      summaryLog.println("Establishing connection to the database.");
      Class.forName("com.mysql.jdbc.Driver");
      String url = String.format("jdbc:mysql://%s:%s/%s", properties.getProperty("db.host"),
                                 properties.getProperty("db.port"), properties.getProperty("db.databaseName"));
      connection = DriverManager.getConnection(url, properties.getProperty("db.user"),
                                               properties.getProperty("db.password"));
    } catch (Exception e) {
      summaryLog.println("Couldn't connect to the database.");
      writeStackTrace(e, summaryLog);
      System.exit(-1);
    }
    return connection;
  }

  private static void writeStackTrace(Exception e, PrintStream ps) {
    StringWriter stringWriter = new StringWriter();
    e.printStackTrace(new PrintWriter(stringWriter));
    ps.println(stringWriter.toString());
  }

  private void removeAll() {
    //FIXME: This was just copied over from RemoveAllHandler.
    summaryLog.println("Removing all.");
    Futures fs = new Futures();
    for( Job j : Job.jobs() ) { j.stop(); j.remove(fs); }
    fs.blockForPending();
    new MRTask(){
      @Override public void setupLocal() {  H2O.raw_clear();  water.fvec.Vec.ESPC.clear(); }
    }.doAllNodes();
    H2O.getPM().getIce().cleanUp();
  }
}
