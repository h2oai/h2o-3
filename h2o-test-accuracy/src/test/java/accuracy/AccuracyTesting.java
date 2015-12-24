package accuracy;

import org.testng.TestNGException;
import water.util.Log;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.*;

public class AccuracyTesting {

	@BeforeClass
	@Parameters("numNodes")
	private void accuracySuiteSetup(@Optional("1") String numNodes) throws IOException {
		AccuracyUtil.setupLogging();
		AccuracyUtil.configureAccessToDB();
		AccuracyUtil.setupH2OCloud(Integer.parseInt(numNodes));
	}

	@DataProvider(name = "TestCaseProvider")
	public Object[][] testCaseProvider() {
		// Create the set of test cases
		// Retrieve algorithm and testcaseId command-line parameters. These are used to filter the set of test cases.
		//String algorithm = System.getProperty("algorithm");
		String algorithm = "dl";
		String testCaseId = System.getProperty("testcaseId");
		//String testCaseId = "880";
		return createTestCases(algorithm, testCaseId);
	}

	public Object[][] createTestCases(String algorithm, String testCaseId) {
		List<String> testCaseEntries = null;
		try {
			AccuracyUtil.log("Reading test cases from: " + TestCase.getTestCasesPath());
			testCaseEntries = Files.readAllLines(AccuracyUtil.find_test_file_static(TestCase.getTestCasesPath()).toPath(),
				Charset.defaultCharset());
		}
		catch (Exception e) {
			AccuracyUtil.log("Cannot read the test cases from: " + TestCase.getTestCasesPath());
			AccuracyUtil.logStackTrace(e);
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
			AccuracyUtil.log("Creating test case: " + t);
			try {
				testCaseArray.add(testCaseCounter++, new TestCase(Integer.parseInt(testCaseEntry[0]), testCaseEntry[1],
					testCaseEntry[2], testCaseEntry[3].equals("1"), testCaseEntry[4].equals("1"),
					Integer.parseInt(testCaseEntry[5]), Integer.parseInt(testCaseEntry[6])));
			} catch (Exception e) {
				AccuracyUtil.log("Couldn't create test case: " + t);
				AccuracyUtil.logStackTrace(e);
				System.exit(-1);
			}
		}

		Object[][] theTestCases = new Object[testCaseArray.size()][1];
		for (int t = 0; t < testCaseArray.size(); t++) { theTestCases[t][0] = testCaseArray.get(t); }

		return theTestCases;
	}

	@Test(dataProvider = "TestCaseProvider")
	public void accuracyTest(TestCase tc) {
		tc.cleanUp();
		String errorMessage;
		info("Running test case: " + tc.testCaseId);
		AccuracyUtil.log("****************** Running test case: " + tc.testCaseId + "******************");

		// Only load the data sets when the test case is about to be executed, instead of when the test case is
		// constructed in the testCaseProvider(), for example.
		try {
			tc.loadTestCaseDataSets();
		} catch (IOException e) {
			errorMessage = "Couldn't load data sets for test case: " + tc.testCaseId;
			AccuracyUtil.log(errorMessage);
			AccuracyUtil.logStackTrace(e);
			throw new TestNGException(errorMessage);
		}

		// Only make the model parameters object once the training and testing data sets have been loaded into H2O
		try {
			tc.setModelParameters();
		} catch (Exception e) {
			errorMessage = "Couldn't set the model parameters for test case: " + tc.testCaseId;
			AccuracyUtil.log(errorMessage);
			AccuracyUtil.logStackTrace(e);
			throw new TestNGException(errorMessage);
		}

		// Execute the provided test case
		TestCaseResult result;
		try {
			result = tc.execute();
		} catch (Exception e) {
			errorMessage = "Couldn't execute test case: " + tc.testCaseId;
			AccuracyUtil.log(errorMessage);
			AccuracyUtil.logStackTrace(e);
			throw new TestNGException(errorMessage);
		} catch (AssertionError ae) {
			errorMessage = "Couldn't execute test case: " + tc.testCaseId;
			AccuracyUtil.log(errorMessage);
			AccuracyUtil.log(ae.getMessage());
			throw new TestNGException(errorMessage);
		}

		// Store the test case result
		try {
			result.saveToAccuracyDB();
		} catch (Exception e) {
			errorMessage = "Couldn't save the test case result for test case: " + tc.testCaseId + " to the " +
				"Accuracy database.";
			AccuracyUtil.log(errorMessage);
			AccuracyUtil.logStackTrace(e);
			throw new TestNGException(errorMessage);
		}
	}

	public static void info(String message) {
		Log.info("");
		Log.info("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
		Log.info(message);
		Log.info("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
		Log.info("");
	}
}
