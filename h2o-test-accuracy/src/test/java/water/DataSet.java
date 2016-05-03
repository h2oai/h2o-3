package water;

import java.io.File;
import java.io.IOException;

import water.fvec.Frame;

public class DataSet {

	private int id;
	public String uri;
	private int responseColumn;

	private Frame frame;

	public DataSet(int id) throws Exception {
		this.id = id;
		String[] dataSetEntry;
		boolean foundDataSet = false;
		for (String d : AccuracyTestingSuite.dataSetsCSVRows) {
			dataSetEntry = d.trim().split(",", -1);
			if (dataSetEntry[0].equals(Integer.toString(this.id))) { // found the data set
				uri = dataSetEntry[1];
				responseColumn = Integer.parseInt(dataSetEntry[2]);
				foundDataSet = true;
				break;
			}
		}
		if (!foundDataSet) { throw new Exception("Couldn't find data set id: " + this.id + " in data sets csv."); }
	}

	public void load(boolean regression) throws IOException {
		AccuracyTestingSuite.summaryLog.println("Loading data set: " + this.id);
		frame = TestUtil.parse_test_file(makeDataSetFile(this.uri).getCanonicalPath());
		if (!regression) {
			String responseColumnName = frame._names[responseColumn];
			AccuracyTestingSuite.summaryLog.println("Converting response column (idx/name): " + responseColumn + "/" +
				responseColumnName + " to categorical for dataset: " + this.id);
			Scope.track(frame.replace(responseColumn, frame.vecs()[responseColumn].toCategoricalVec()));
			DKV.put(frame);
		}
	}

	public int getId() { return id; }
	public int getResponseColumn() { return responseColumn; }
	public Frame getFrame() { return frame; }
	public void removeFrame() {
		if (frame != null) {
			AccuracyTestingSuite.summaryLog.println("Removing frame: " + frame._key.toString() + " for data set id: " +
					id);
			frame.remove();
			frame.delete();
		}
	}

	private File makeDataSetFile(String uri) {
		String filePath;
		if (uri.contains("bigdata")) { filePath = "bigdata/laptop/testng/"; }
		else if (uri.contains("smalldata")) { filePath = "smalldata/testng/"; }
		else if (uri.contains("tmp")) { filePath = "/tmp/"; }
		else { filePath = ""; }

		String[] uriTokens = uri.trim().split("/", -1);
		String fileName = uriTokens[uriTokens.length - 1];

		return AccuracyTestingUtil.find_test_file_static(filePath + fileName);
	}
}
