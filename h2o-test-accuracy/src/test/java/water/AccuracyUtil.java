package water;

import water.util.Log;

import java.io.*;
import java.util.Properties;
import java.util.UUID;

public class AccuracyUtil extends TestUtil {
    private static boolean _stall_called_before = false;
    private static String accuracyDBHost, accuracyDBPort, accuracyDBUser, accuracyDBPwd, accuracyDBName,
      accuracyDBTableName, accuracyDBTimeout;
    private static PrintStream summaryPrintStream;

    public AccuracyUtil() { super(); }
    public AccuracyUtil(int minCloudSize) { super(minCloudSize); }

    // Logging utils
    public static void setupLogging() throws IOException {
        File h2oResultsDir = new File(find_test_file_static("h2o-test-accuracy").getCanonicalFile().toString() +
          "/results");
        if (!h2oResultsDir.exists()) { h2oResultsDir.mkdir(); }

        File suiteSummary = new File(find_test_file_static("h2o-test-accuracy/results").getCanonicalFile().toString() +
          "/summary.txt");
        suiteSummary.createNewFile();
        summaryPrintStream = new PrintStream(new FileOutputStream(suiteSummary, false));

        System.out.println("Commenced logging to h2o-test-accuracy/results directory.");
    }
    public static void log(String info) { summaryPrintStream.println(info + "\n"); }
    public static void logStackTrace(Exception e) {
        StringWriter stringWriter = new StringWriter();
        e.printStackTrace(new PrintWriter(stringWriter));
        log(stringWriter.toString());
    }
    public static void info(String message) {
        Log.info("");
        Log.info("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        Log.info(message);
        Log.info("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        Log.info("");
    }

    // Database utils
    public static void configureAccessToDB() {
        //String configPath = System.getProperty("dbConfigFilePath");
        String configPath = "/Users/ece/0xdata/h2o-3/DBConfig.properties";
        if (!(null == configPath)) {
            File configFile = new File(configPath);
            Properties properties = new Properties();
            try {
                properties.load(new BufferedReader(new FileReader(configFile)));
            } catch (IOException e) {
                summaryPrintStream.println("Cannot load database configuration: " + configPath);
                summaryPrintStream.println(e.getMessage());
                System.exit(-1);
            }
            summaryPrintStream.println("The following database configuration settings were read from " + configPath);
            summaryPrintStream.println(accuracyDBHost = properties.getProperty("db.host"));
            summaryPrintStream.println(accuracyDBPort = properties.getProperty("db.port"));
            summaryPrintStream.println(accuracyDBUser = properties.getProperty("db.user"));
            summaryPrintStream.println(accuracyDBPwd = properties.getProperty("db.password"));
            summaryPrintStream.println(accuracyDBName = properties.getProperty("db.databaseName"));
            summaryPrintStream.println(accuracyDBTableName = properties.getProperty("db.tableName"));
            summaryPrintStream.println(accuracyDBTimeout = properties.getProperty("db.queryTimeout"));
            summaryPrintStream.println("");
        }
    }
    public static String getDBHost()      { return accuracyDBHost; }
    public static String getDBPort()      { return accuracyDBPort; }
    public static String getDBName()      { return accuracyDBName; }
    public static String getDBUser()      { return accuracyDBUser; }
    public static String getDBPwd()       { return accuracyDBPwd; }
    public static String getDBTableName() { return accuracyDBTableName; }

    // H2O cloud setup utils
    public static void setupH2OCloud(int numNodes) {
        stall_till_cloudsize(numNodes);
        _initial_keycnt = H2O.store_size();
    }

    // ==== Test Setup & Teardown Utilities ====
    // Stall test until we see at least X members of the Cloud
    public static void stall_till_cloudsize(int x) {
        if (! _stall_called_before) {
            if (H2O.getCloudSize() < x) {
                // Leader node, where the tests execute from.
                String cloudName = UUID.randomUUID().toString();
                String[] args = new String[]{"-name",cloudName,"-ice_root",find_test_file_static("h2o-test-accuracy/" +
                  "results").toString()};
                H2O.main(args);

                // Secondary nodes, skip if expected to be pre-built
                if( System.getProperty("ai.h2o.skipNodeCreation") == null )
                    for( int i = 0; i < x-1; i++ )
                        new NodeContainer(args).start();

                H2O.waitForCloudSize(x, 30000);

                _stall_called_before = true;
            }
        }
    }
}
