package h2o.testng;

import h2o.testng.db.MySQLConfig;
import h2o.testng.utils.*;
import water.util.Log;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.testng.annotations.*;

import water.TestNGUtil;

public class AccuracyTestingFramework extends TestNGUtil {
	static HashMap<Integer, DataSet> dataSets = new HashMap<Integer, DataSet>();

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
		String algorithm = "drf";

		//testCaseId = System.getProperty("testcaseId");
		int testCaseId = Integer.parseInt("406");

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
				if (!(algorithm.equals(testCaseEntry[1]))) {
					continue;
				}
			}
			else if (!(testCaseId == 0)) {
				if (!(testCaseId == Integer.parseInt(testCaseEntry[0]))) {
					continue;
				}
			}
			Log.info("Creating test case: " + t);
			try {
				testCaseArray.add(testCaseCounter++, new TestCase(Integer.parseInt(testCaseEntry[0]), testCaseEntry[1],
					testCaseEntry[2], testCaseEntry[3].equals("1"), testCaseEntry[4].equals("1"),
					Integer.parseInt(testCaseEntry[5]), Integer.parseInt(testCaseEntry[6])));
			} catch (IOException e) {
				Log.err("Couldn't create test case: " + t);
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
		//redirectStandardStreams();

		// Only load the data sets when the test case is about to be executed, instead of when the test case is
		// constructed in the testCaseProvider(), for example.
		tc.loadTestCaseDataSets();

		// Only make the model parameters object once the training and testing data sets have been loaded into H2O
		tc.setModelParameters();

		// Execute the provided test case
		tc.execute();

		tc.cleanUp();

		//resetStandardStreams();

//		String testcaseStatus = "PASSED";

//		RecordingTestcase rt = new RecordingTestcase();
//
//		Param[] params = null;
//
//		Model.Parameters modelParameter = null;
//		String invalidMessage = null;
//		String notImplMessage = null;
//
//		redirectStandardStreams();
//
//		switch (algorithm) {
//			case FunctionUtils.drf:
//				params = DRFConfig.params;
//				break;
//
//			case FunctionUtils.gbm:
//				params = GBMConfig.params;
//				break;
//
//			case FunctionUtils.glm:
//				params = GLMConfig.params;
//				break;
//
//			case FunctionUtils.dl:
//				params = DeepLearningConfig.params;
//				break;
//
//			default:
//				Log.info("Do not implement for algorithm: " + algorithm);
//		}

//		try {
//			invalidMessage = FunctionUtils.validate(params, train_dataset_id, train_dataset, validate_dataset_id,
//					validate_dataset, rawInput);
//			if (FunctionUtils.drf.equals(algorithm)) {
//				notImplMessage = FunctionUtils.checkImplemented(rawInput);
//			}
//
//			if (StringUtils.isNotEmpty(invalidMessage)) {
//				Log.info(invalidMessage);
//				Assert.fail(String.format(invalidMessage));
//			}
//			else if (StringUtils.isNotEmpty(notImplMessage)) {
//				Log.info(notImplMessage);
//				Assert.fail(String.format(notImplMessage));
//			}
//			else {
//			modelParameter = FunctionUtils.toModelParameter(params, algorithm, train_dataset_id, validate_dataset_id,
//				train_dataset, validate_dataset, rawInput);
//
//			FunctionUtils.basicTesting(algorithm, modelParameter, isNegativeTestcase, rawInput);
//		}
//		catch (AssertionError ae) {
//			testcaseStatus = "FAILED";
//			throw ae;
//		}
//		finally {
//			Log.info("Total nodes: " + H2O.CLOUD.size());
//			Log.info("Total cores: " + H2O.NUMCPUS);
//			Log.info("Total time: " + (rt.getTimeRecording()) + "millis");
//			// TODO: get memory by H2O's API
//			Log.info("Total memory used in testcase:" + (rt.getUsedMemory() / RecordingTestcase.MB) + "MB");
//
//			resetStandardStreams();
//
//			testcaseStatus = String.format("Testcase %s %s", rawInput.get(CommonHeaders.test_case_id), testcaseStatus);
//			Reporter.log(testcaseStatus, true);
//		}
	}

//	public void loadDataSets() {
//		File dataSetsFile;
//		List<String> dataSetEntries = null;
//
//		try {
//			Log.info("Loading data sets");
//			dataSetsFile = TestNGUtil.find_test_file_static(dataSetsPath);
//			dataSetEntries = Files.readAllLines(dataSetsFile.toPath(), Charset.defaultCharset());
//		}
//		catch (Exception e) {
//			Log.info("Cannot load the data sets csv: " + dataSetsPath);
//			e.printStackTrace();
//		}
//
//		// only load data sets required by the test cases
//		ArrayList<Integer> requiredDataSets = new ArrayList<Integer>();
//		for (TestCase t : testCases.values()) {
//			requiredDataSets.add(t.trainingDataSetId);
//			requiredDataSets.add(t.testingDataSetId);
//		}
//
//		dataSetEntries.remove(0); // remove the header
//		String[] dataSetEntry;
//		for (String d : dataSetEntries) {
//			dataSetEntry = d.trim().split(",", -1);
//			if(!dataSetEntry[2].equals("") && requiredDataSets.contains(Integer.parseInt(dataSetEntry[2]))) {
//				Log.info("Loading data set: " + d);
//				dataSets.put(Integer.parseInt(dataSetEntry[0]), new Dataset(Integer.parseInt(dataSetEntry[0]), dataSetEntry[1],
//					Integer.parseInt(dataSetEntry[2])));
//			}
//		}
//	}

	@AfterClass
	public void afterClass() {
		FunctionUtils.closeAllFrameInDatasetCharacteristic(dataSets);
	}
}
