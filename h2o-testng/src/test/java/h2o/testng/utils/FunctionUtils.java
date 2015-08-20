package h2o.testng.utils;

import hex.Distribution.Family;
import hex.Model;
import hex.glm.GLM;
import hex.glm.GLMConfig;
import hex.glm.GLMModel;
import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Solver;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFConfig;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMConfig;
import hex.tree.gbm.GBMModel;
import hex.tree.gbm.GBMModel.GBMParameters;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.testng.Assert;

import water.Key;
import water.Scope;
import water.TestNGUtil;
import water.fvec.FVecTest;
import water.fvec.Frame;
import water.parser.ParseDataset;

public class FunctionUtils {

	public static String validate(Param[] params, List<String> tcHeaders, String train_dataset_id,
			Dataset train_dataset, String[] input) {

		System.out.println("Validate DRFParameters object with testcase: " + input[tcHeaders.indexOf(testcase_id)]);
		String result = null;

		if (StringUtils.isEmpty(train_dataset_id)) {
			result = "Dataset files is empty";
		}
		else if (train_dataset == null) {
			result = "Dataset characteristic file is empty";
		}
		else if (!train_dataset.isAvailabel()) {
			result = "Dataset characteristic is not available";
		}
		else {
			result = Param.validateAutoSetParams(params, tcHeaders, input);
		}

		if (result != null) {
			result = "[INVALID] " + result;
		}

		return result;
	}

	/**
	 * Only use for DRF algorithm
	 * 
	 * @param tcHeaders
	 * @param input
	 * @return
	 */
	public static String checkImplemented(List<String> tcHeaders, String[] input) {

		System.out.println("check modelParameter object with testcase: " + input[tcHeaders.indexOf(testcase_id)]);
		String result = null;

		if (StringUtils.isNotEmpty(input[tcHeaders.indexOf("_offset_column")].trim())) {
			result = "offset_column is not implemented";
		}
		else if (StringUtils.isNotEmpty(input[tcHeaders.indexOf("_weights_column")].trim())) {
			result = "weights_column is not implemented";
		}
		else if (StringUtils.isNotEmpty(input[tcHeaders.indexOf("_nfolds")].trim())) {
			result = "nfolds is not implemented";
		}
		else if (StringUtils.isNotEmpty(input[tcHeaders.indexOf("fold_column")].trim())) {
			result = "fold_column is not implemented";
		}
		else if (StringUtils.isNotEmpty(input[tcHeaders.indexOf("gaussian")].trim())
				|| StringUtils.isNotEmpty(input[tcHeaders.indexOf("binomial")].trim())
				|| StringUtils.isNotEmpty(input[tcHeaders.indexOf("multinomial")].trim())
				|| StringUtils.isNotEmpty(input[tcHeaders.indexOf("poisson")].trim())
				|| StringUtils.isNotEmpty(input[tcHeaders.indexOf("gamma")].trim())
				|| StringUtils.isNotEmpty(input[tcHeaders.indexOf("tweedie")].trim())) {
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
	 * @param tcHeaders
	 * @param rawInput
	 * @param trainFrame
	 * @param responseColumn
	 * @return null if testcase do not require betaconstraints otherwise return beta constraints frame
	 */
	private static Frame createBetaConstraints(List<String> tcHeaders, String[] rawInput, Frame trainFrame,
			String responseColumn) {

		Frame betaConstraints = null;
		boolean isBetaConstraints = Param.parseBoolean(rawInput[tcHeaders.indexOf("betaConstraints")]);
		String lowerBound = rawInput[tcHeaders.indexOf("lowerBound")];
		String upperBound = rawInput[tcHeaders.indexOf("upperBound")];

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
		// predictorNames.remove(glmParams._response_column); // remove the response column name. we only want
		// predictors
		for (String name : predictorNames) {
			if (!name.equals(responseColumn)) {
				if (trainFrame.vec(name).isEnum()) { // need coefficient names for each level of a categorical
														// column
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

	public static Model.Parameters toModelParameter(Param[] params, List<String> tcHeaders, String algorithm,
			String train_dataset_id, String validate_dataset_id, Dataset train_dataset, Dataset validate_dataset,
			String[] input) {

		System.out.println("Create modelParameter object with testcase: " + input[tcHeaders.indexOf(testcase_id)]);

		Model.Parameters modelParameter = null;

		switch (algorithm) {
			case FunctionUtils.drf:

				modelParameter = new DRFModel.DRFParameters();

				// set distribution param
				Family drfFamily = (Family) DRFConfig.familyParams.getValue(input, tcHeaders);

				if (drfFamily != null) {
					System.out.println("Set _distribution: " + drfFamily);
					modelParameter._distribution = drfFamily;
				}
				break;

			case FunctionUtils.glm:

				modelParameter = new GLMParameters();

				hex.glm.GLMModel.GLMParameters.Family glmFamily = (hex.glm.GLMModel.GLMParameters.Family) GLMConfig.familyOptionsParams
						.getValue(input, tcHeaders);
				Solver s = (Solver) GLMConfig.solverOptionsParams.getValue(input, tcHeaders);

				if (glmFamily != null) {
					System.out.println("Set _family: " + glmFamily);
					((GLMParameters) modelParameter)._family = glmFamily;
				}
				if (s != null) {
					System.out.println("Set _solver: " + s);
					((GLMParameters) modelParameter)._solver = s;
				}
				break;

			case FunctionUtils.gbm:

				modelParameter = new GBMParameters();

				// set distribution param
				Family gbmFamily = (Family) GBMConfig.familyParams.getValue(input, tcHeaders);

				if (gbmFamily != null) {
					System.out.println("Set _distribution: " + gbmFamily);
					modelParameter._distribution = gbmFamily;
				}
				break;

			default:
				System.out.println("can not parse to object parameter with algorithm: " + algorithm);
		}

		// set AutoSet params
		Param.setAutoSetParams(modelParameter, params, tcHeaders, input);

		// set response_column param
		modelParameter._response_column = train_dataset.getResponseColumn();

		// set train/validate params
		Frame trainFrame = null;
		Frame validateFrame = null;
		Frame betaConstraints = null;

		try {

			System.out.println("Create train frame: " + train_dataset_id);
			trainFrame = train_dataset.getFrame();

			if (StringUtils.isNotEmpty(train_dataset_id) && validate_dataset != null && validate_dataset.isAvailabel()) {
				System.out.println("Create validate frame: " + train_dataset_id);
				validateFrame = validate_dataset.getFrame();
			}

			if (algorithm.equals(FunctionUtils.glm)) {
				betaConstraints = createBetaConstraints(tcHeaders, input, trainFrame, train_dataset.getResponseColumn());
				if (betaConstraints != null) {
					((GLMParameters) modelParameter)._beta_constraints = betaConstraints._key;
				}
			}
		}
		catch (Exception e) {
			if (trainFrame != null) {
				trainFrame.remove();
			}
			if (validateFrame != null) {
				validateFrame.remove();
			}
			if (betaConstraints != null) {
				betaConstraints.remove();
			}
			throw e;
		}

		System.out.println("Set train frame");
		modelParameter._train = trainFrame._key;

		if (validateFrame != null) {
			System.out.println("Set validate frame");
			modelParameter._valid = validateFrame._key;
		}

		System.out.println("Create success DRFParameters object.");
		return modelParameter;
	}

	public static void basicTesting(String algorithm, Model.Parameters parameter, boolean isNegativeTestcase,
			String[] rawInput) {

		Frame trainFrame = null;
		Frame score = null;

		DRF drfJob = null;
		DRFModel drfModel = null;

		Key modelKey = Key.make("model");
		GLM glmJob = null;
		GLMModel glmModel = null;
		HashMap<String, Double> coef = null;

		GBM gbmJob = null;
		GBMModel gbmModel = null;

		trainFrame = parameter._train.get();

		try {
			Scope.enter();
			switch (algorithm) {
				case FunctionUtils.drf:

					System.out.println("Build model");
					drfJob = new DRF((DRFModel.DRFParameters) parameter);

					System.out.println("Train model:");
					drfModel = drfJob.trainModel().get();

					System.out.println("Predict testcase");
					score = drfModel.score(trainFrame);
					break;

				case FunctionUtils.glm:

					System.out.println("Build model");
					glmJob = new GLM(modelKey, "basic glm test", (GLMParameters) parameter);

					System.out.println("Train model");
					glmModel = glmJob.trainModel().get();

					coef = glmModel.coefficients();

					System.out.println("Predict testcase ");
					score = glmModel.score(trainFrame);
					break;

				case FunctionUtils.gbm:

					System.out.println("Build model ");
					gbmJob = new GBM((GBMParameters) parameter);

					System.out.println("Train model");
					gbmModel = gbmJob.trainModel().get();

					System.out.println("Predict testcase ");
					score = gbmModel.score(trainFrame);
					break;
			}

			if (isNegativeTestcase) {
				Assert.fail("It is negative testcase");
			}
			else {
				System.out.println("Testcase is passed.");
			}
		}
		catch (Exception ex) {
			System.out.println("Testcase is failed");
			ex.printStackTrace();
			if (!isNegativeTestcase) {
				Assert.fail("Testcase is failed", ex);
			}
		}
		catch (AssertionError ae) {

			System.out.println("Testcase is failed");
			ae.printStackTrace();
			if (!isNegativeTestcase) {
				Assert.fail("Testcase is failed", ae);
			}
		}
		finally {
			if (drfJob != null) {
				drfJob.remove();
			}
			if (drfModel != null) {
				drfModel.delete();
			}
			if (glmJob != null) {
				glmJob.remove();
			}
			if (glmModel != null) {
				glmModel.delete();
			}
			if (gbmJob != null) {
				gbmJob.remove();
			}
			if (gbmModel != null) {
				gbmModel.delete();
			}
			if (score != null) {
				score.remove();
				score.delete();
			}
			Scope.exit();
		}
	}

	public static Object[][] dataProvider(HashMap<String, Dataset> dataSetCharacteristic, List<String> tcHeaders,
			String algorithm, String positiveTestcaseFilePath, String negativeTestcaseFilePath, int firstRow) {

		Object[][] result = null;
		Object[][] positiveData = null;
		Object[][] negativeData = null;
		int numRow = 0;
		int numCol = 0;
		int r = 0;

		positiveData = readTestcaseFile(dataSetCharacteristic, tcHeaders, positiveTestcaseFilePath, firstRow,
				algorithm, false);
		negativeData = readTestcaseFile(dataSetCharacteristic, tcHeaders, negativeTestcaseFilePath, firstRow,
				algorithm, true);

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

	private static Object[][] readTestcaseFile(HashMap<String, Dataset> dataSetCharacteristic, List<String> tcHeaders,
			String fileName, int firstRow, String algorithm, boolean isNegativeTestcase) {

		Object[][] result = null;
		List<String> lines = null;

		if (StringUtils.isEmpty(fileName)) {
			return null;
		}

		try {
			// read data from file
			lines = Files.readAllLines(TestNGUtil.find_test_file_static(fileName).toPath(), Charset.defaultCharset());
		}
		catch (Exception ignore) {
			System.out.println("Cannot open file: " + fileName);
			ignore.printStackTrace();
			return null;
		}

		// remove headers
		lines.removeAll(lines.subList(0, firstRow));

		result = new Object[lines.size()][9];
		int r = 0;
		for (String line : lines) {
			String[] variables = line.trim().split(",", -1);

			result[r][0] = variables[tcHeaders.indexOf(testcase_id)];
			result[r][1] = variables[tcHeaders.indexOf(test_description)];
			result[r][2] = variables[tcHeaders.indexOf(train_dataset_id)];
			result[r][3] = variables[tcHeaders.indexOf(validate_dataset_id)];
			result[r][4] = dataSetCharacteristic.get(variables[tcHeaders.indexOf(train_dataset_id)]);
			result[r][5] = dataSetCharacteristic.get(variables[tcHeaders.indexOf(validate_dataset_id)]);
			result[r][6] = algorithm;
			result[r][7] = isNegativeTestcase;
			result[r][8] = variables;

			r++;
		}

		return result;
	}

	public static HashMap<String, Dataset> readDataSetCharacteristic() {

		HashMap<String, Dataset> result = new HashMap<String, Dataset>();
		final String dataSetCharacteristicFilePath = "h2o-testng/src/test/resources/datasetCharacteristics.csv";

		final int numCols = 6;
		final int dataSetId = 0;
		final int dataSetDirectory = 1;
		final int fileName = 2;
		final int responseColumn = 3;
		final int columnNames = 4;
		final int columnTypes = 5;

		File file = null;
		List<String> lines = null;

		try {
			System.out.println("read dataset characteristic");
			file = TestNGUtil.find_test_file_static(dataSetCharacteristicFilePath);
			lines = Files.readAllLines(file.toPath(), Charset.defaultCharset());
		}
		catch (Exception e) {
			System.out.println("Cannot open dataset characteristic file: " + dataSetCharacteristicFilePath);
			e.printStackTrace();
		}

		for (String line : lines) {
			System.out.println("read line: " + line);
			String[] arr = line.trim().split(",", -1);

			if (arr.length < numCols) {
				System.out.println("length of line is short");
			}
			else {
				System.out.println("parse to DataSet object");
				Dataset dataset = new Dataset(arr[dataSetId], arr[dataSetDirectory], arr[fileName],
						arr[responseColumn], arr[columnNames], arr[columnTypes]);

				result.put(arr[dataSetId], dataset);
			}
		}

		return result;
	}

	public static void removeDatasetInDatasetCharacteristic(HashMap<String, Dataset> mapDatasetCharacteristic,
			String dataSetDirectory) {

		if (StringUtils.isEmpty(dataSetDirectory)) {
			return;
		}

		Dataset temp = null;
		for (String key : mapDatasetCharacteristic.keySet()) {

			temp = mapDatasetCharacteristic.get(key);
			if (temp != null && dataSetDirectory.equals(temp.getDataSetDirectory())) {
				temp.closeFrame();
				mapDatasetCharacteristic.remove(key);
			}
		}
	}

	public static void closeAllFrameInDatasetCharacteristic(HashMap<String, Dataset> mapDatasetCharacteristic) {

		for (String key : mapDatasetCharacteristic.keySet()) {

			mapDatasetCharacteristic.get(key).closeFrame();
		}
	}

	public final static String testcase_id = "testcase_id";
	public final static String test_description = "test_description";
	public final static String train_dataset_id = "train_dataset_id";
	public final static String validate_dataset_id = "validate_dataset_id";

	public final static String glm = "glm";
	public final static String gbm = "gbm";
	public final static String drf = "drf";
	
	public final static String smalldata = "smalldata";
	public final static String bigdata = "bigdata";
}
