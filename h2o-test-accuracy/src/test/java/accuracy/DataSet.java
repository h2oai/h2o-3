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

public class DataSet extends TestUtil{
	private static String dataSetsPath = "h2o-testng/src/test/resources/datasetCharacteristics.csv";

	private int id;
	private String uri;
	private int responseColumn;
	private File csvFile;
	private Frame frame;

	public DataSet(int id, File csvFile, List<String> entries) throws Exception{
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
		if (!foundDataSet) { throw new Exception("Couldn't find data set id: " + id + " in csv: " + dataSetsPath); }

	}

	public DataSet(int id, File csvFile) throws Exception {
		this(id, TestNGUtil.find_test_file_static(dataSetsPath),
			Files.readAllLines(csvFile.toPath(), Charset.defaultCharset()));
	}

	public DataSet(int id) throws Exception{
		this(id, TestNGUtil.find_test_file_static(dataSetsPath));
	}

	public void load(boolean regression) throws IOException {
		AccuracyTestingUtil.info("Loading data set: " + this.id);
		frame = TestUtil.parse_test_file(makeDataSetFile(this.uri).getCanonicalPath());
		if (!regression) {
			String responseColumnName = frame._names[responseColumn];
			AccuracyTestingUtil.info("Converting response column (idx/name): " + responseColumn + "/" + responseColumnName
				+ " to categorical for dataset: " + this.id);
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
