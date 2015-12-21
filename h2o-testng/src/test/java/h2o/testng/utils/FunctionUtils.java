package h2o.testng.utils;

import hex.deeplearning.DeepLearningConfig;
import hex.glm.GLMConfig;
import hex.tree.drf.DRFConfig;
import hex.tree.gbm.GBMConfig;

import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.testng.Assert;

import au.com.bytecode.opencsv.CSVReader;
import water.Key;
import water.TestNGUtil;
import water.fvec.FVecTest;
import water.fvec.Frame;
import water.parser.ParseDataset;
import water.util.Log;

public class FunctionUtils {

	public final static String glm = "glm";
	public final static String gbm = "gbm";
	public final static String drf = "drf";
	public final static String dl = "dl";

	public final static String smalldata = "smalldata";
	public final static String bigdata = "bigdata";

	// ---------------------------------------------- //
	// execute testcase
	// ---------------------------------------------- //
	private static String validateTestcaseType(DataSet train_dataset, HashMap<String, String> rawInput) {
		return null;
	}

	public static String validate(Param[] params, String train_dataset_id, DataSet train_dataset,
			String validate_dataset_id, DataSet validate_dataset, HashMap<String, String> rawInput) {

		Log.info("Validate Parameters object with testcase: " + rawInput.get(CommonHeaders.test_case_id));
		String result = null;

		String verifyTrainDSMessage = validateTestcaseType(train_dataset, rawInput);
		String verifyValidateDSMessage = validateTestcaseType(validate_dataset, rawInput);

		if (StringUtils.isEmpty(train_dataset_id)) {
			result = "In testcase, train dataset id is empty";
		}
		else if (train_dataset == null) {
			result = String.format("Dataset id %s do not have in dataset characteristic", train_dataset_id);
		}
		else if (verifyTrainDSMessage != null) {
			result = verifyTrainDSMessage;
		}
		else if (verifyValidateDSMessage != null) {
			result = verifyValidateDSMessage;
		}
		else {
			result = Param.validateAutoSetParams(params, rawInput);

		}

		if (result != null) {
			result = "[INVALID] " + result;
		}

		return result;
	}

	/**
	 * Only use for DRF algorithm
	 *
	 * @param rawInput
	 * @return
	 */
	public static String checkImplemented(HashMap<String, String> rawInput) {

		Log.info("check modelParameter object with testcase: " + rawInput.get(CommonHeaders.test_case_id));
		String result = null;

		if (StringUtils.isNotEmpty(rawInput.get(CommonHeaders.family_gaussian).trim())
				|| StringUtils.isNotEmpty(rawInput.get(CommonHeaders.family_binomial).trim())
				|| StringUtils.isNotEmpty(rawInput.get(CommonHeaders.family_multinomial).trim())
				|| StringUtils.isNotEmpty(rawInput.get(CommonHeaders.family_poisson).trim())
				|| StringUtils.isNotEmpty(rawInput.get(CommonHeaders.family_gamma).trim())
				|| StringUtils.isNotEmpty(rawInput.get(CommonHeaders.family_tweedie).trim())) {

			result = "Only AUTO family is implemented";
		}

		if (result != null) {
			result = "[NOT IMPL] " + result;
		}

		return result;
	}

	/**
	 * This function is only used for GLM algorithm.
	 *
	 * @param rawInput
	 * @param trainFrame
	 * @param responseColumn
	 * @return null if testcase do not require betaconstraints otherwise return beta constraints frame
	 */
	private static Frame createBetaConstraints(HashMap<String, String> rawInput, Frame trainFrame, int responseColumn) {

		Frame betaConstraints = null;
		boolean isBetaConstraints = Param.parseBoolean(rawInput.get("betaConstraints"));
		String lowerBound = rawInput.get("lowerBound");
		String upperBound = rawInput.get("upperBound");

		if (!isBetaConstraints) {
			return null;
		}
		// Here's an example of how to make the beta constraints frame.
		// First, represent the beta constraints frame as a string, for example:

		// "names, lower_bounds, upper_bounds\n"+
		// "AGE, -.5, .5\n"+
		// "RACE, -.5, .5\n"+
		// "GLEASON, -.5, .5"
		String betaConstraintsString = "names, lower_bounds, upper_bounds\n";
		List<String> predictorNames = Arrays.asList(trainFrame._names);
		for (String name : predictorNames) {
			// ignore the response column and any constant column in bc.
			// we only want predictors
			if (!name.equals(responseColumn) && !trainFrame.vec(name).isConst()) {
				// need coefficient names for each level of a categorical column
				if (trainFrame.vec(name).isCategorical()) {
					for (String level : trainFrame.vec(name).domain()) {
						betaConstraintsString += String.format("%s.%s,%s,%s\n", name, level, lowerBound, upperBound);
					}
				}
				else { // numeric columns only need one coefficient name
					betaConstraintsString += String.format("%s,%s,%s\n", name, lowerBound, upperBound);
				}
			}
		}
		Key betaConsKey = Key.make("beta_constraints");
		FVecTest.makeByteVec(betaConsKey, betaConstraintsString);
		betaConstraints = ParseDataset.parse(Key.make("beta_constraints.hex"), betaConsKey);

		return betaConstraints;
	}

	/**
	 * If the testcase is negative testcase. The function will compare error message with a message in testcase
	 * 
	 * @param isNegativeTestcase
	 * @param exception
	 * @param rawInput
	 */
	private static void handleTestcaseFailed(boolean isNegativeTestcase, Throwable exception,
			HashMap<String, String> rawInput) {

		Log.info("Testcase failed");
		exception.printStackTrace();
		if (isNegativeTestcase) {

			Log.info("This is negative testcase");
			Log.info("Error message in testcase: " + rawInput.get(CommonHeaders.error_message));
			Log.info("Error message in program: " + exception.getMessage());

			if (StringUtils.isEmpty(rawInput.get(CommonHeaders.error_message))
					|| !exception.getMessage().contains(rawInput.get(CommonHeaders.error_message))) {
				Assert.fail("Error message do not match with testcase", exception);
			}
			else {
				Log.info("Error message match with testcase");
			}
		}
		else {
			Assert.fail(exception.getMessage(), exception);
		}
	}

	// ---------------------------------------------- //
	// initiate data. Read testcase files
	// ---------------------------------------------- //
	public static Object[][] readAllTestcase(HashMap<Integer, DataSet> dataSetCharacteristic) {
		Object[][] result = null;
		int nrows = 0;
		int ncols = 0;
		int r = 0;
		int i = 0;

		Object[][] drfTestcase = readAllTestcaseOneAlgorithm(dataSetCharacteristic, FunctionUtils.drf);
		Object[][] gbmTestcase = readAllTestcaseOneAlgorithm(dataSetCharacteristic, FunctionUtils.gbm);
		Object[][] glmTestcase = readAllTestcaseOneAlgorithm(dataSetCharacteristic, FunctionUtils.glm);
		Object[][] dlTestcase = readAllTestcaseOneAlgorithm(dataSetCharacteristic, FunctionUtils.dl);

		if (drfTestcase != null && drfTestcase.length != 0) {
			nrows = drfTestcase[0].length;
			ncols += drfTestcase.length;
		}
		if (gbmTestcase != null && gbmTestcase.length != 0) {
			nrows = gbmTestcase[0].length;
			ncols += gbmTestcase.length;
		}
		if (glmTestcase != null && glmTestcase.length != 0) {
			nrows = glmTestcase[0].length;
			ncols += glmTestcase.length;
		}
		if (dlTestcase != null && dlTestcase.length != 0) {
			nrows = dlTestcase[0].length;
			ncols += dlTestcase.length;
		}

		result = new Object[ncols][nrows];

		if (drfTestcase != null && drfTestcase.length != 0) {
			for (i = 0; i < drfTestcase.length; i++) {
				result[r++] = drfTestcase[i];
			}
		}
		if (gbmTestcase != null && gbmTestcase.length != 0) {
			for (i = 0; i < gbmTestcase.length; i++) {
				result[r++] = gbmTestcase[i];
			}
		}
		if (glmTestcase != null && glmTestcase.length != 0) {
			for (i = 0; i < glmTestcase.length; i++) {
				result[r++] = glmTestcase[i];
			}
		}
		if (dlTestcase != null && dlTestcase.length != 0) {
			for (i = 0; i < dlTestcase.length; i++) {
				result[r++] = dlTestcase[i];
			}
		}
		return result;
	}

	private static Object[][] readAllTestcaseOneAlgorithm(HashMap<Integer, DataSet> dataSetCharacteristic,
			String algorithm) {

		String positiveTestcaseFilePath = null;
		String negativeTestcaseFilePath = null;
		List<String> testCaseSchema = null;

		switch (algorithm) {
			case FunctionUtils.drf:
				positiveTestcaseFilePath = DRFConfig.positiveTestcaseFilePath;
				negativeTestcaseFilePath = DRFConfig.negativeTestcaseFilePath;
				testCaseSchema = DRFConfig.testCaseSchema;
				break;

			case FunctionUtils.glm:
				positiveTestcaseFilePath = GLMConfig.positiveTestcaseFilePath;
				negativeTestcaseFilePath = GLMConfig.negativeTestcaseFilePath;
				testCaseSchema = GLMConfig.testCaseSchema;
				break;

			case FunctionUtils.gbm:
				positiveTestcaseFilePath = GBMConfig.positiveTestcaseFilePath;
				negativeTestcaseFilePath = GBMConfig.negativeTestcaseFilePath;
				testCaseSchema = GBMConfig.testCaseSchema;
				break;
			case FunctionUtils.dl:
				positiveTestcaseFilePath = DeepLearningConfig.positiveTestcaseFilePath;
				negativeTestcaseFilePath = DeepLearningConfig.negativeTestcaseFilePath;
				testCaseSchema = DeepLearningConfig.testCaseSchema;
				break;

			default:
				Log.info("do not implement for algorithm: " + algorithm);
				return null;
		}

		Object[][] result = FunctionUtils.dataProvider(dataSetCharacteristic, testCaseSchema, algorithm,
				positiveTestcaseFilePath, negativeTestcaseFilePath);

		return result;
	}

	private static Object[][] dataProvider(HashMap<Integer, DataSet> dataSetCharacteristic, List<String> testCaseSchema,
			String algorithm, String positiveTestcaseFilePath, String negativeTestcaseFilePath) {

		Object[][] result = null;
		Object[][] positiveData = null;
		Object[][] negativeData = null;
		int numRow = 0;
		int numCol = 0;
		int r = 0;

		positiveData = readTestcaseFile(dataSetCharacteristic, testCaseSchema, positiveTestcaseFilePath, algorithm, false);

		if (positiveData != null && positiveData.length != 0) {
			numRow += positiveData.length;
			numCol = positiveData[0].length;
		}
		if (negativeData != null && negativeData.length != 0) {
			numRow += negativeData.length;
			numCol = negativeData[0].length;
		}

		if (numRow == 0) {
			return null;
		}

		result = new Object[numRow][numCol];

		if (positiveData != null && positiveData.length != 0) {
			for (int i = 0; i < positiveData.length; i++) {
				result[r++] = positiveData[i];
			}
		}
		if (negativeData != null && negativeData.length != 0) {
			for (int i = 0; i < negativeData.length; i++) {
				result[r++] = negativeData[i];
			}
		}

		return result;
	}

	private static Object[][] readTestcaseFile(HashMap<Integer, DataSet> dataSetCharacteristic,
			List<String> testCaseSchema, String fileName, String algorithm, boolean isNegativeTestcase) {

		Log.info("Read testcase: " + fileName);

		Object[][] result = null;
		CSVReader csvReader = null;
		List<String[]> contents = null;
		String[] headerRow = null;

		if (StringUtils.isEmpty(fileName)) {
			Log.info("Not found file: " + fileName);
			return null;
		}

		// read data from file
		try {
			csvReader = new CSVReader(new FileReader(TestNGUtil.find_test_file_static(fileName)));
		}
		catch (Exception ignore) {
			Log.err("Cannot open file: " + fileName);
			ignore.printStackTrace();
			return null;
		}

		// read all content from CSV file
		try {
			contents = csvReader.readAll();
		}
		catch (IOException e) {
			Log.err("Cannot read content from CSV file");
			e.printStackTrace();
			return null;
		}
		finally {
			try {
				csvReader.close();
			}
			catch (IOException e) {
				Log.err("Cannot close CSV file");
				e.printStackTrace();
			}
		}

		contents.remove(0);

		result = new Object[contents.size()][8];
		int r = 0;
		for (String[] testCase : contents) {
			HashMap<String, String> rawInput = parseToHashMap(testCaseSchema, testCase);

			result[r][0] = rawInput.get(CommonHeaders.test_case_id);
			result[r][1] = rawInput.get(CommonHeaders.training_dataset_id);
			result[r][2] = rawInput.get(CommonHeaders.testing_dataset_id);
			result[r][3] = dataSetCharacteristic.get(Integer.parseInt((String) result[r][1]));
			result[r][4] = dataSetCharacteristic.get(Integer.parseInt((String) result[r][2]));
			result[r][5] = algorithm;
			result[r][6] = isNegativeTestcase;
			result[r][7] = rawInput;

			r++;
		}

		return result;
	}

	private static boolean validateTestcaseFile(List<String> testCaseSchema, String fileName, String[] headerRow) {

		Log.info("validate file: " + fileName);

		for (String header : testCaseSchema) {
			if (Arrays.asList(headerRow).indexOf(header) < 0) {
				Log.info(String.format("find not found %s column in %s", header, fileName));
				return false;
			}

		}

		return true;
	}

	private static HashMap<String, String> parseToHashMap(List<String> testCaseSchema, String[] testCase) {
		HashMap<String, String> result = new HashMap<String, String>();

		int f = 0;
		for (String field : testCaseSchema) {
			result.put(field, testCase[f++]);
		}

		return result;
	}
}
