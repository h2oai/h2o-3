package h2o.testng.utils;

import java.io.File;
import java.io.IOException;

import water.TestNGUtil;
import water.TestUtil;
import water.fvec.Frame;

public class Dataset extends TestUtil{
	public int dataSetId;
	public String dataSetURI;
	public int responseColumn;
	public File dataSetFile;
	public Frame dataSetFrame;

	public Dataset(int dataSetId, String dataSetURI, int responseColumn) {
		this.dataSetId = dataSetId;
		this.dataSetURI = dataSetURI;
		this.responseColumn = responseColumn;
		this.dataSetFile = makeDataSetFile(dataSetURI);
		try {
			this.dataSetFrame = TestUtil.parse_test_file(dataSetFile.getCanonicalPath());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void closeFrame() {
		if (dataSetFrame != null) {
			dataSetFrame.remove();
			dataSetFrame.delete();
		}
	}
	private File makeDataSetFile(String dataSetURI) {
		String filePath;
		if (dataSetURI.contains("bigdata"))
			filePath = "bigdata/laptop/testng/";
		else
			filePath = "smalldata/testng/";

		String[] uriTokens = dataSetURI.trim().split("/", -1);
		String fileName = uriTokens[uriTokens.length - 1];

		return TestNGUtil.find_test_file_static(filePath + fileName);
	}
}
