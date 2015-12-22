package accuracy;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;

import water.DKV;
import water.Scope;
import water.TestNGUtil;
import water.TestUtil;
import water.fvec.Frame;
import water.util.Log;

public class DataSet extends TestUtil{
	private static String dataSetsPath = "h2o-testng/src/test/resources/datasetCharacteristics.csv";

	private int id;
	private String uri;
	private int responseColumn;
	private File csvFile;
	private Frame frame;

	public DataSet(int id, File csvFile, List<String> entries) {
		this.id = id;
		this.csvFile = csvFile;

		entries.remove(0); // remove the header
		String[] dataSetEntry;
		boolean foundDataSet = false;
		for (String d : entries) {
			dataSetEntry = d.trim().split(",", -1);
			if (dataSetEntry[0].equals(Integer.toString(id))) { // found the data set
				uri = dataSetEntry[1];
				responseColumn = Integer.parseInt(dataSetEntry[2]);
				foundDataSet = true;
				break;
			}
		}
		if (!foundDataSet) {
			Log.err("Couldn't find data set id: " + id + " in csv: " + dataSetsPath);
			System.exit(-1);
		}

	}

	public DataSet(int id, File csvFile) throws IOException {
		this(id, TestNGUtil.find_test_file_static(dataSetsPath),
			Files.readAllLines(csvFile.toPath(), Charset.defaultCharset()));
	}

	public DataSet(int id) throws IOException {
		this(id, TestNGUtil.find_test_file_static(dataSetsPath));
	}

	public void load(boolean regression) throws IOException {
		Log.info("Loading data set: " + this.id);
		frame = TestUtil.parse_test_file(makeDataSetFile(this.uri).getCanonicalPath());
		if (!regression) {
			String responseColumnName = frame._names[responseColumn];
			Log.info("Converting response column idx/name: " + responseColumn + "/" + responseColumnName + " to " +
				"categorical");
			Scope.track(frame.replace(responseColumn, frame.vecs()[responseColumn].toCategoricalVec()));
			DKV.put(frame);
		}
	}
	public int getId() { return id; }
	public int getResponseColumn() { return responseColumn; }
	public Frame getFrame() { return frame; }
	public void closeFrame() {
		if (frame != null) {
			frame.remove();
			frame.delete();
		}
	}

	private File makeDataSetFile(String uri) {
		String filePath;
		if (uri.contains("bigdata")) { filePath = "bigdata/laptop/testng/"; }
		else {                         filePath = "smalldata/testng/"; }

		String[] uriTokens = uri.trim().split("/", -1);
		String fileName = uriTokens[uriTokens.length - 1];

		return TestNGUtil.find_test_file_static(filePath + fileName);
	}
}
