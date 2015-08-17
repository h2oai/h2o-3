package h2o.testng.utils;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import water.TestNGUtil;

public class FunctionUtils {

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
		
		if(StringUtils.isEmpty(fileName)){
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
}
