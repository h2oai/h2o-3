package accuracy;

import org.testng.TestNGException;
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
			err("Cannot load database configuration: " + configPath);
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
		String dbConfigFilePath = System.getProperty("dbConfigFilePath");
		//String dbConfigFilePath = "/Users/ece/0xdata/h2o-dev/DBConfig.properties";
		if (!(null == dbConfigFilePath)) setDBConfig(dbConfigFilePath);

		// Create the set of test cases
		// Retrieve algorithm and testcaseId command-line parameters. These are used to filter the set of test cases.
		String algorithm = System.getProperty("algorithm");
		//String algorithm = "dl";
		String testCaseId = System.getProperty("testcaseId");
		return createTestCases(algorithm, testCaseId);
	}

	public Object[][] createTestCases(String algorithm, String testCaseId) {
		List<String> testCaseEntries = null;
		try {
			info("Reading test cases from: " + TestCase.getTestCasesPath());
			testCaseEntries = Files.readAllLines(TestNGUtil.find_test_file_static(TestCase.getTestCasesPath()).toPath(),
				Charset.defaultCharset());
		}
		catch (Exception e) {
			err("Cannot read the test cases from: " + TestCase.getTestCasesPath());
			e.printStackTrace();
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
			else if (!(testCaseId.isEmpty())) {
				if (!(testCaseId.equals(testCaseEntry[0]))) { continue; }
			}
			info("Creating test case: " + t);
			try {
				testCaseArray.add(testCaseCounter++, new TestCase(Integer.parseInt(testCaseEntry[0]), testCaseEntry[1],
					testCaseEntry[2], testCaseEntry[3].equals("1"), testCaseEntry[4].equals("1"),
					Integer.parseInt(testCaseEntry[5]), Integer.parseInt(testCaseEntry[6])));
			} catch (Exception e) {
				err("Couldn't create test case: " + t);
				e.printStackTrace();
				System.exit(-1);
			}
		}

		Object[][] theTestCases = new Object[testCaseArray.size()][1];
		for (int t = 0; t < testCaseArray.size(); t++) { theTestCases[t][0] = testCaseArray.get(t); }

		return theTestCases;
	}

	@Test(dataProvider = "TestCaseProvider")
	public void accuracyTest(TestCase tc) {
		info("Running test case: " + tc.testCaseId);

		// Only load the data sets when the test case is about to be executed, instead of when the test case is
		// constructed in the testCaseProvider(), for example.
		try {
			tc.loadTestCaseDataSets();
		} catch (IOException e) {
			AccuracyTestingUtil.err("Couldn't load data sets for test case: " + tc.testCaseId);
			e.printStackTrace();
			throw new TestNGException("Test case " + tc.testCaseId + " failed");
		}

		// Only make the model parameters object once the training and testing data sets have been loaded into H2O
		try {
			tc.setModelParameters();
		} catch (Exception e) {
			AccuracyTestingUtil.err("Couldn't set the model parameters for test case: " + tc.testCaseId);
			e.printStackTrace();
			throw new TestNGException("Test case " + tc.testCaseId + " failed");
		}

		// Execute the provided test case
		TestCaseResult result;
		try {
			result = tc.execute();
		} catch (Exception e) {
			AccuracyTestingUtil.err("Couldn't execute test case: " + tc.testCaseId);
			e.printStackTrace();
			throw new TestNGException("Test case " + tc.testCaseId + " failed");
		}

		// Store the test case result
		try {
			result.saveToAccuracyDB();
		} catch (Exception e) {
			AccuracyTestingUtil.err("Couldn't save the test case result for test case: " + tc.testCaseId + " to the " +
				"Accuracy database.");
			e.printStackTrace();
			throw new TestNGException("Test case " + tc.testCaseId + " failed");
		}

		tc.cleanUp();
	}

	public static void info(String message) {
		Log.info("");
		Log.info("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
		Log.info(message);
		Log.info("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
		Log.info("");
	}

	public static void err(String message) {
		Log.err("");
		Log.err("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
		Log.err(message);
		Log.err("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
		Log.err("");
	}
}
