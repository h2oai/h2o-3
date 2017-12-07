package hex.pca;

import hex.DataInfo;
import water.DKV;
import water.Key;
import water.fvec.Frame;

import java.util.Random;

import static hex.pca.JMHConfiguration.logLevel;
import static hex.pca.PCAModel.PCAParameters.Method.GramSVD;
import static water.TestUtil.parse_test_file;

/**
 * micro-benchmark based on hex.pca.PCAWideDataSetsTest
 * <p>
 * This benchmark will measure the PCA method GramSVD with wide datasets.  It will first build a model
 * using GramSVD under normal setting (_wideDataset is set to false).  Next, it builds a GramSVD model with
 * _wideDataSet set to true.
 */
public class PCAWideDataSets {
	private static final int numberOfModels = 6;
	private Frame trainingFrame = null;
	private PCA pca = null;
	private int dataSetCase;
	private PCAModel pcaModel;
	private Frame pcaScore;
	private PCAImplementation PCAImplementation;

	PCAWideDataSets(int dataSetCase) {
		setDataSetCase(dataSetCase);
		setup();
	}
	
	public void setDataSetCase(int customDataSetCase) {
		if (customDataSetCase <= 0 || customDataSetCase > numberOfModels) {
			throw new IllegalArgumentException("Illegal data set case!");
		} else {
			this.dataSetCase = customDataSetCase;
		}
	}
	
	public void setup() {
		water.util.Log.setLogLevel(logLevel);
		final String _smallDataSet = "smalldata/pca_test/decathlon.csv";
		final String _prostateDataSet = "smalldata/prostate/prostate_cat.csv";
		final DataInfo.TransformType[] _transformTypes = {DataInfo.TransformType.NONE,
			DataInfo.TransformType.STANDARDIZE, DataInfo.TransformType.DEMEAN, DataInfo.TransformType.DESCALE};
		Random _rand = new Random();

		/*
		 *  Six cases are measured:
		 * case 1. we test with a small dataset with all numerical data columns and make sure it works.
		 * case 2. we add NA rows to the	small dataset with all numerical data columns.
		 * case 3. test with the same small dataset while preserving the categorical columns;
		 * case 4. test with the same small dataset with categorical columns and add NA rows;
		 * case 5. test with prostate dataset;
		 * case 6. test with prostate dataset with NA rows added.
		 */
		switch (dataSetCase) {
			case 1:
				pca = preparePCAModel(_smallDataSet, false, true,
					_transformTypes[_rand.nextInt(_transformTypes.length)]);  // case 1
				break;
			case 2:
				pca = preparePCAModel(_smallDataSet, true, true,
					_transformTypes[_rand.nextInt(_transformTypes.length)]);  // case 2
				break;
			case 3:
				pca = preparePCAModel(_smallDataSet, false, false,
					_transformTypes[_rand.nextInt(_transformTypes.length)]);  // case 3
				break;
			case 4:
				pca = preparePCAModel(_smallDataSet, true, false,
					_transformTypes[_rand.nextInt(_transformTypes.length)]);  // case 4
				break;
			case 5:
				pca = preparePCAModel(_prostateDataSet, false, false,
					_transformTypes[_rand.nextInt(_transformTypes.length)]);  // case 5
				break;
			case 6:
				pca = preparePCAModel(_prostateDataSet, true, false,
					_transformTypes[_rand.nextInt(_transformTypes.length)]);  // case 6
				break;
		}
	}
	
	private PCA preparePCAModel(String datafile, boolean addNAs, boolean removeColumns,
	                            DataInfo.TransformType transformType) {
		trainingFrame = parse_test_file(Key.make(datafile), datafile);
		if (removeColumns) {
			trainingFrame.remove(12).remove();    // remove categorical columns
			trainingFrame.remove(11).remove();
			trainingFrame.remove(10).remove();
		}
		if (addNAs) {
			trainingFrame.vec(0).setNA(0);          // set NAs
			trainingFrame.vec(3).setNA(10);
			trainingFrame.vec(5).setNA(20);
		}
		DKV.put(trainingFrame);
		
		PCAModel.PCAParameters parameters = new PCAModel.PCAParameters();
		parameters._train = trainingFrame._key;
		parameters._k = 3;
		parameters._transform = transformType;
		parameters._use_all_factor_levels = true;
		parameters._pca_method = GramSVD;
		parameters._pca_implementation = getPCAImplementation();
		parameters._impute_missing = false;
		parameters._seed = 12345;
		
		PCA pcaParametersWide = new PCA(parameters);
		pcaParametersWide.setWideDataset(true);  // force to treat dataset as wide even though it is not.
		return pcaParametersWide;
	}
	
	public void tearDown() {
		if (trainingFrame != null) {
			trainingFrame.delete();
		}
		if (pcaModel != null) {
			pcaModel.delete();
		}
		if (pcaScore != null) {
			pcaScore.delete();
		}
	}
	
	public boolean train() {
		try {
			pcaModel = pca.trainModel().get();
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	public boolean score() {
		try {
			pcaScore = pcaModel.score(trainingFrame);
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public PCAImplementation getPCAImplementation() {
		return PCAImplementation;
	}

	public void setPCAImplementation(PCAImplementation PCAImplementation) {
		this.PCAImplementation = PCAImplementation;
	}
}
