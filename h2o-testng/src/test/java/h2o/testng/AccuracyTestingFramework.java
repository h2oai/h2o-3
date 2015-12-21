package h2o.testng;

import h2o.testng.db.MySQLConfig;
import h2o.testng.utils.*;
import water.util.Log;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.*;

import water.TestNGUtil;

public class AccuracyTestingFramework extends TestNGUtil {
	@DataProvider(name = "TestCaseProvider")
	public Object[][] testCaseProvider() {
		// Retrieve the Accuracy database configuration information
		//dbConfigFilePath = System.getProperty("dbConfigFilePath");
		String dbConfigFilePath = "/Users/ece/0xdata/h2o-dev/DBConfig.properties";

		// 	Setup Accuracy database connection configuration
		if (!(null == dbConfigFilePath)) MySQLConfig.initConfig().setConfigFilePath(dbConfigFilePath);

		// Create the set of test cases

		// Retrieve algorithm and testcaseId command-line parameters. These are used to filter the set of test cases.
		//algorithm = System.getProperty("algo");
		String algorithm = null;

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
		tc.execute();

		tc.cleanUp();

		//resetStandardStreams();
	}
}
