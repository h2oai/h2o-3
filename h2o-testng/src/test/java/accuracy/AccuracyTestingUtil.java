package accuracy;

import water.util.Log;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.testng.annotations.*;

import water.TestNGUtil;

public class AccuracyTestingUtil extends TestNGUtil {
	public static String accuracyDBHost;
	public static String accuracyDBPort;
	public static String accuracyDBUser;
	public static String accuracyDBPwd;
	public static String accuracyDBName;
	public static String accuracyDBTableName;
	public static String accuracyDBTimeout;

	private void setDBConfig(String configPath) {
		File configFile = new File(configPath);
		Properties properties = new Properties();
		try { properties.load(new BufferedReader(new FileReader(configFile))); }
		catch (IOException e) {
			Log.err("Cannot load database configuration: " + configPath);
			e.printStackTrace();
		}
		accuracyDBHost = properties.getProperty("db.host");
		accuracyDBPort = properties.getProperty("db.port");
		accuracyDBUser = properties.getProperty("db.user");
		accuracyDBPwd = properties.getProperty("db.password");
		accuracyDBName = properties.getProperty("db.databaseName");
		accuracyDBTableName = properties.getProperty("db.tableName");
		accuracyDBTimeout = properties.getProperty("db.queryTimeout");
	}

	@DataProvider(name = "TestCaseProvider")
	public Object[][] testCaseProvider() {
		// Configure access to the Accuracy database
		//dbConfigFilePath = System.getProperty("dbConfigFilePath");
		String dbConfigFilePath = "/Users/ece/0xdata/h2o-dev/DBConfig.properties";
		if (!(null == dbConfigFilePath)) setDBConfig(dbConfigFilePath);

		// Create the set of test cases
		// Retrieve algorithm and testcaseId command-line parameters. These are used to filter the set of test cases.
		//algorithm = System.getProperty("algo");
		String algorithm = "dl";
		//testCaseId = System.getProperty("testcaseId");
		int testCaseId = Integer.parseInt("348");
		return createTestCases(algorithm, testCaseId);
	}

	public Object[][] createTestCases(String algorithm, int testCaseId) {
		List<String> testCaseEntries = null;
		try {
			Log.info("Reading test cases from: " + TestCase.getTestCasesPath());
			testCaseEntries = Files.readAllLines(TestNGUtil.find_test_file_static(TestCase.getTestCasesPath()).toPath(),
				Charset.defaultCharset());
		}
		catch (Exception e) {
			Log.err("Cannot read the test cases from: " + TestCase.getTestCasesPath());
			Log.err(e.getMessage());
			System.exit(-1);
		}

		testCaseEntries.remove(0); // remove header line
		ArrayList<TestCase> testCaseArray = new ArrayList<>();
		String[] testCaseEntry;
		int testCaseCounter = 0;
		for (String t : testCaseEntries) {
			testCaseEntry = t.trim().split(",", -1);

			// If algorithm is specified, load all test cases for that algorithm. Else, if a specific test case is specified
			// only load that test case. Otherwise, load all the test cases.
			if (!(null == algorithm)) {
				if (!(algorithm.equals(testCaseEntry[1]))) { continue; }
			}
			else if (!(testCaseId == 0)) {
				if (!(testCaseId == Integer.parseInt(testCaseEntry[0]))) { continue; }
			}
			Log.info("Creating test case: " + t);
			try {
				testCaseArray.add(testCaseCounter++, new TestCase(Integer.parseInt(testCaseEntry[0]), testCaseEntry[1],
					testCaseEntry[2], testCaseEntry[3].equals("1"), testCaseEntry[4].equals("1"),
					Integer.parseInt(testCaseEntry[5]), Integer.parseInt(testCaseEntry[6])));
			} catch (IOException e) {
				Log.err("Couldn't create test case: " + t);
				Log.err(e.getMessage());
				System.exit(-1);
			}
		}

		Object[][] theTestCases = new Object[testCaseArray.size()][1];
		for (int t = 0; t < testCaseArray.size(); t++) { theTestCases[t][0] = testCaseArray.get(t); }

		return theTestCases;
	}

	@Test(dataProvider = "TestCaseProvider")
	public void accuracyTest(TestCase tc) {
		//redirectStandardStreams();

		Log.info("Running test case: " + tc.testCaseId);

		// Only load the data sets when the test case is about to be executed, instead of when the test case is
		// constructed in the testCaseProvider(), for example.
		tc.loadTestCaseDataSets();

		// Only make the model parameters object once the training and testing data sets have been loaded into H2O
		tc.setModelParameters();

		// Execute the provided test case
		TestCaseResult result = tc.execute();

		// Store the test case result
		result.saveToAccuracyDB();

		tc.cleanUp();

		//resetStandardStreams();
	}
}
