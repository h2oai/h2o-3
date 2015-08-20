package h2o.testng;

import h2o.testng.utils.Dataset;
import h2o.testng.utils.FunctionUtils;
import h2o.testng.utils.Param;
import h2o.testng.utils.RecordingTestcase;
import hex.Model;
import hex.glm.GLMConfig;
import hex.tree.drf.DRFConfig;
import hex.tree.gbm.GBMConfig;

import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import water.TestNGUtil;

public class TestNG extends TestNGUtil {

	@BeforeClass
	public void beforeClass() {

		algorithm = System.getProperty("algorithm");
		size = System.getProperty("size");
		testcaseId = System.getProperty("testcaseId");

		if (StringUtils.isNotEmpty(testcaseId)) {
			algorithm = "";
			size = "";
		}

		dataSetCharacteristic = FunctionUtils.readDataSetCharacteristic();

		System.out.println(String.format("run TestNG with algorithm: %s, size: %s, testcaseId: %s", algorithm, size,
				testcaseId));

	}

	@DataProvider(name = "SingleTestcase")
	public static Object[][] drfCases() {

		Object[][] result = null;

		Object[][] data = readAllTestcase(algorithm);

		if (data == null || data.length == 0) {
			return null;
		}

		// set testcaseId
		if (StringUtils.isNotEmpty(testcaseId)) {

			result = new Object[1][data[0].length];
			int i = 0;

			for (i = 0; i < data.length; i++) {
				if (testcaseId.equals(data[i][0])) {
					result[0] = data[i];
					break;
				}
			}

			if (i == data.length) {
				System.out.println("Can not find testcase_id:" + testcaseId);
				return null;
			}

			return result;
		}

		// set size
		result = removeAllTestcase(data, size);
		return result;
	}

	@Test(dataProvider = "SingleTestcase")
	public void basic(String testcase_id, String test_description, String train_dataset_id, String validate_dataset_id,
			Dataset train_dataset, Dataset validate_dataset, String algorithm, boolean isNegativeTestcase,
			String[] rawInput) {

		RecordingTestcase rt = new RecordingTestcase();

		Param[] params = null;
		List<String> tcHeaders = null;

		Model.Parameters modelParameter = null;
		String invalidMessage = null;
		String notImplMessage = null;

		redirectStandardStreams();

		switch (algorithm) {
			case FunctionUtils.drf:
				params = DRFConfig.params;
				tcHeaders = DRFConfig.tcHeaders;
				break;

			case FunctionUtils.gbm:
				params = GBMConfig.params;
				tcHeaders = GBMConfig.tcHeaders;
				break;

			case FunctionUtils.glm:
				params = GLMConfig.params;
				tcHeaders = GLMConfig.tcHeaders;
				break;

			default:
				System.out.println("do not implement for algorithm: " + algorithm);
		}

		try {
			invalidMessage = FunctionUtils.validate(params, tcHeaders, train_dataset_id, train_dataset, rawInput);
			if (FunctionUtils.drf.equals(algorithm)) {
				notImplMessage = FunctionUtils.checkImplemented(tcHeaders, rawInput);
			}

			if (StringUtils.isNotEmpty(invalidMessage)) {
				System.out.println(invalidMessage);
				Assert.fail(String.format(invalidMessage));
			}
			else if (StringUtils.isNotEmpty(notImplMessage)) {
				System.out.println(notImplMessage);
				Assert.fail(String.format(notImplMessage));
			}
			else {
				// TODO: move modelParameter from here to FunctionUtils
				modelParameter = FunctionUtils.toModelParameter(params, tcHeaders, algorithm, train_dataset_id,
						validate_dataset_id, train_dataset, validate_dataset, rawInput);

				FunctionUtils.basicTesting(algorithm, modelParameter, isNegativeTestcase, rawInput);
			}
		}
		finally {

			// TODO: get memory by H2O's API
			System.out.println("Total Memory used in testcase:" + (rt.getUsedMemory() / RecordingTestcase.MB) + "MB");
			System.out.println("Total Time used in testcase:" + (rt.getTimeRecording()) + "millis");

			// wait 100 mili-sec for output/error to be stored
			try {
				Thread.sleep(100);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}

			resetStandardStreams();
		}
	}

	@AfterClass
	public void afterClass() {

		FunctionUtils.closeAllFrameInDatasetCharacteristic(dataSetCharacteristic);
	}

	private static Object[][] readAllTestcase(String algorithm) {

		if (StringUtils.isNotEmpty(algorithm)) {
			return readAllTestcaseOneAlgorithm(algorithm);
		}
		Object[][] result = null;
		int nrows = 0;
		int ncols = 0;
		int r = 0;
		int i = 0;

		Object[][] drfTestcase = FunctionUtils.dataProvider(dataSetCharacteristic, DRFConfig.tcHeaders,
				FunctionUtils.drf, DRFConfig.positiveTestcaseFilePath, DRFConfig.negativeTestcaseFilePath,
				DRFConfig.firstRow);

		Object[][] gbmTestcase = FunctionUtils.dataProvider(dataSetCharacteristic, GBMConfig.tcHeaders,
				FunctionUtils.gbm, GBMConfig.positiveTestcaseFilePath, GBMConfig.negativeTestcaseFilePath,
				GBMConfig.firstRow);

		Object[][] glmTestcase = FunctionUtils.dataProvider(dataSetCharacteristic, GLMConfig.tcHeaders,
				FunctionUtils.glm, GLMConfig.positiveTestcaseFilePath, GLMConfig.negativeTestcaseFilePath,
				GLMConfig.firstRow);

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

		return result;
	}

	private static Object[][] readAllTestcaseOneAlgorithm(String algorithm) {

		int firstRow = 0;
		String positiveTestcaseFilePath = null;
		String negativeTestcaseFilePath = null;
		List<String> tcHeaders = null;

		switch (algorithm) {
			case FunctionUtils.drf:
				firstRow = DRFConfig.firstRow;
				positiveTestcaseFilePath = DRFConfig.positiveTestcaseFilePath;
				negativeTestcaseFilePath = DRFConfig.negativeTestcaseFilePath;
				tcHeaders = DRFConfig.tcHeaders;
				break;

			case FunctionUtils.glm:
				firstRow = GLMConfig.firstRow;
				positiveTestcaseFilePath = GLMConfig.positiveTestcaseFilePath;
				negativeTestcaseFilePath = GLMConfig.negativeTestcaseFilePath;
				tcHeaders = GLMConfig.tcHeaders;
				break;

			case FunctionUtils.gbm:
				firstRow = GBMConfig.firstRow;
				positiveTestcaseFilePath = GBMConfig.positiveTestcaseFilePath;
				negativeTestcaseFilePath = GBMConfig.negativeTestcaseFilePath;
				tcHeaders = GBMConfig.tcHeaders;
				break;

			default:
				System.out.println("do not implement for algorithm: " + algorithm);
				return null;
		}

		Object[][] result = FunctionUtils.dataProvider(dataSetCharacteristic, tcHeaders, algorithm,
				positiveTestcaseFilePath, negativeTestcaseFilePath, firstRow);

		return result;
	}

	private static Object[][] removeAllTestcase(Object[][] testcases, String size) {

		if (testcases == null || testcases.length == 0) {
			return null;
		}

		if (StringUtils.isEmpty(size)) {
			return testcases;
		}

		Object[][] result = null;
		Object[][] temp = null;
		int nrows = 0;
		int ncols = 0;
		int r = 0;
		int i = 0;
		Dataset dataset = null;

		ncols = testcases.length;
		nrows = testcases[0].length;
		temp = new Object[ncols][nrows];

		for (i = 0; i < ncols; i++) {

			dataset = (Dataset) testcases[i][4];

			if (dataset == null) {
				// because we have to show any INVALID testcase thus we have to add this testcase
				temp[r++] = testcases[i];
			}
			else if (size.equals(dataset.getDataSetDirectory())) {
				temp[r++] = testcases[i];
			}
		}

		if (r == 0) {
			System.out.println(String.format("dataset characteristic have no size what is: %s.", size));
		}
		else {

			result = new Object[r][nrows];

			for (i = 0; i < r; i++) {
				result[i] = temp[i];
			}
		}

		return result;
	}

	private static String algorithm = "";
	private static String size = null;
	private static String testcaseId = null;

	private static HashMap<String, Dataset> dataSetCharacteristic;
}
