package h2o.testng.utils;

import hex.Distribution.Family;
import hex.Model;
import hex.glm.GLM;
import hex.glm.GLMBasic;
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
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.testng.Assert;

import water.Key;
import water.Scope;
import water.TestNGUtil;
import water.fvec.Frame;

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
			result = Param.validateAutoSetParams(params, input, tcHeaders);
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

	public static Model.Parameters toModelParameter(Param[] params, List<String> tcHeaders, String algorithm,
			String train_dataset_id, String validate_dataset_id, Dataset train_dataset, Dataset validate_dataset,
			String[] input) {

		System.out.println("Create modelParameter object with testcase: " + input[tcHeaders.indexOf(testcase_id)]);

		Model.Parameters modelParameter = null;

		switch (algorithm) {
			case FunctionUtils.drf:
				modelParameter = new DRFModel.DRFParameters();

				// set distribution param
				Family fDRF = (Family) DRFConfig.familyParams.getValue(input, tcHeaders);

				if (fDRF != null) {
					System.out.println("Set _distribution: " + fDRF);
					modelParameter._distribution = fDRF;
				}
				break;
			case FunctionUtils.glm:
				modelParameter = new GLMParameters();

				hex.glm.GLMModel.GLMParameters.Family fGLM = (hex.glm.GLMModel.GLMParameters.Family) GLMConfig.familyOptionsParams
						.getValue(input, tcHeaders);
				Solver s = (Solver) GLMConfig.solverOptionsParams.getValue(input, tcHeaders);

				if (fGLM != null) {
					System.out.println("Set _family: " + fGLM);
					((GLMParameters) modelParameter)._family = fGLM;
				}
				if (s != null) {
					System.out.println("Set _solver: " + s);
					((GLMParameters) modelParameter)._solver = s;
				}
				//TODO: do not implement beta constrainst
				break;
			case FunctionUtils.gbm:
				modelParameter = new GBMParameters();

				// set distribution param
				Family f = (Family) GBMConfig.familyParams.getValue(input, tcHeaders);

				if (f != null) {
					System.out.println("Set _distribution: " + f);
					modelParameter._distribution = f;
				}
				break;
			default:
				// TODO: log is not clearly
				System.out.println("do not implement for algorithm: " + algorithm);
		}

		// set AutoSet params
		for (Param p : params) {
			if (p.isAutoSet) {
				p.parseAndSet(modelParameter, input[tcHeaders.indexOf(p.name)]);
			}
		}

		// set response_column param
		modelParameter._response_column = train_dataset.getResponseColumn();

		// set train/validate params
		Frame trainFrame = null;
		Frame validateFrame = null;

		try {

			System.out.println("Create train frame: " + train_dataset_id);
			trainFrame = train_dataset.getFrame();

			if (StringUtils.isNotEmpty(train_dataset_id) && validate_dataset != null && validate_dataset.isAvailabel()) {
				System.out.println("Create validate frame: " + train_dataset_id);
				validateFrame = validate_dataset.getFrame();
			}
		}
		catch (Exception e) {
			if (trainFrame != null) {
				trainFrame.remove();
			}
			if (validateFrame != null) {
				validateFrame.remove();
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
		DRF drfJob = null;
		DRFModel drfModel = null;
		Frame score = null;

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
					gbmJob = new GBM((GBMParameters)parameter);
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
			if (score != null) {
				score.remove();
				score.delete();
			}
			Scope.exit();
		}
	}

	public static Object[][] dataProvider(HashMap<String, Dataset> dataSetCharacteristic, List<String> tcHeaders,
			String positiveTestcaseFilePath, String negativeTestcaseFilePath, int firstRow) {

		Object[][] result = null;
		Object[][] positiveData = null;
		Object[][] negativeData = null;
		int numRow = 0;
		int numCol = 0;
		int r = 0;

		positiveData = readTestcaseFile(dataSetCharacteristic, tcHeaders, positiveTestcaseFilePath, firstRow, false);
		negativeData = readTestcaseFile(dataSetCharacteristic, tcHeaders, negativeTestcaseFilePath, firstRow, true);

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
			String fileName, int firstRow, boolean isNegativeTestcase) {

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

		result = new Object[lines.size()][8];
		int r = 0;
		for (String line : lines) {
			String[] variables = line.trim().split(",", -1);

			result[r][0] = variables[tcHeaders.indexOf(testcase_id)];
			result[r][1] = variables[tcHeaders.indexOf(test_description)];
			result[r][2] = variables[tcHeaders.indexOf(train_dataset_id)];
			result[r][3] = variables[tcHeaders.indexOf(validate_dataset_id)];
			result[r][4] = dataSetCharacteristic.get(variables[tcHeaders.indexOf(train_dataset_id)]);
			result[r][5] = dataSetCharacteristic.get(variables[tcHeaders.indexOf(validate_dataset_id)]);
			result[r][6] = isNegativeTestcase;
			result[r][7] = variables;

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
}
