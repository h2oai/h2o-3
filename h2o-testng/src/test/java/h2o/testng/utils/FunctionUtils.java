package h2o.testng.utils;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;

import water.TestNGUtil;

public class FunctionUtils {

	public static final String testcase_type = "testcase_type";

	public static boolean isNegativeTestcase(List<String> tcHeaders, String[] input) {

		final String negative = "negative";

		if (negative.equals(input[tcHeaders.indexOf(testcase_type)].trim())) {
			return true;
		}

		return false;
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
}
