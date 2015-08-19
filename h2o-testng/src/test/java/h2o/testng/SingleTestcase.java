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

public class SingleTestcase extends TestNGUtil {

	@BeforeClass
	public void beforeClass() {

		System.out.println("only run testcase: " + System.getProperty("testcase"));

		final String regex = ":";

		String testcase = System.getProperty("testcase");
		String[] array = testcase.split(regex);

		if (array.length == 2) {
			algorithm = array[0].trim();
			testcaseId = array[1].trim();
			dataSetCharacteristic = FunctionUtils.readDataSetCharacteristic();
		}

		switch (algorithm) {
			case FunctionUtils.drf:
				firstRow = DRFConfig.firstRow;
				positiveTestcaseFilePath = DRFConfig.positiveTestcaseFilePath;
				negativeTestcaseFilePath = DRFConfig.negativeTestcaseFilePath;
				params = DRFConfig.params;
				tcHeaders = DRFConfig.tcHeaders;
				break;
			case FunctionUtils.glm:
				firstRow = GLMConfig.firstRow;
				positiveTestcaseFilePath = GLMConfig.positiveTestcaseFilePath;
				negativeTestcaseFilePath = GLMConfig.negativeTestcaseFilePath;
				params = GLMConfig.params;
				tcHeaders = GLMConfig.tcHeaders;
				break;
			case FunctionUtils.gbm:
				firstRow = GBMConfig.firstRow;
				positiveTestcaseFilePath = GBMConfig.positiveTestcaseFilePath;
				negativeTestcaseFilePath = GBMConfig.negativeTestcaseFilePath;
				params = GBMConfig.params;
				tcHeaders = GBMConfig.tcHeaders;
				break;

			default:
				System.out.println("do not implement for algorithm: " + algorithm);
		}
	}

	@DataProvider(name = "SingleTestcase")
	public static Object[][] drfCases() {

		Object[][] result = null;

		Object[][] data = FunctionUtils.dataProvider(dataSetCharacteristic, tcHeaders, positiveTestcaseFilePath,
				negativeTestcaseFilePath, firstRow);

		if (data == null || data.length == 0 || StringUtils.isEmpty(testcaseId)) {
			return null;
		}

		result = new Object[1][data[0].length];

		for (int i = 0; i < data.length; i++) {
			if (testcaseId.equals(data[i][0])) {
				result[0] = data[i];
				break;
			}
		}

		if (result == null || result.length == 0) {
			System.out.println("Can not find testcase_id:" + testcaseId);
		}

		return result;
	}

	@Test(dataProvider = "SingleTestcase")
	public void basic(String testcase_id, String test_description, String train_dataset_id, String validate_dataset_id,
			Dataset train_dataset, Dataset validate_dataset, boolean isNegativeTestcase, String[] rawInput) {

		RecordingTestcase rt = new RecordingTestcase();
		Model.Parameters modelParameter = null;
		String invalidMessage = null;
		String notImplMessage = null;

		redirectStandardStreams();

		try {
			invalidMessage = FunctionUtils.validate(params, tcHeaders, train_dataset_id, train_dataset, rawInput);
			if (FunctionUtils.drf.equals(algorithm)) {
				notImplMessage = FunctionUtils.checkImplemented(tcHeaders, rawInput);
			}

			if (invalidMessage != null) {
				System.out.println(invalidMessage);
				Assert.fail(String.format(invalidMessage));
			}
			else if (notImplMessage != null) {
				System.out.println(notImplMessage);
				Assert.fail(String.format(notImplMessage));
			}
			else {
				//TODO: move modelParameter from here to FunctionUtils
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

	private static String algorithm = "";
	private static String testcaseId = null;

	private static HashMap<String, Dataset> dataSetCharacteristic;
	
	private static int firstRow = 0;
	private static String positiveTestcaseFilePath = null;
	private static String negativeTestcaseFilePath = null;
	private static Param[] params = null;
	private static List<String> tcHeaders = null;

}
