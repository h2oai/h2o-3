package hex.pca;

import hex.DataInfo;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.util.Log;

import java.util.Random;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertTrue;
import static water.TestUtil.parse_test_file;

/**
	* Created by wendycwong on 2/27/17.
	*/
public class PCAWideDataSetsTests extends TestUtil {
		public static final double _TOLERANCE = 1e-6;
		public static final String _smallDataset = "smalldata/pca_test/decathlon.csv";
		public static final String _prostateDataset = "smalldata/prostate/prostate_cat.csv";
		public static final DataInfo.TransformType[] _transformTypes = {DataInfo.TransformType.NONE,
										DataInfo.TransformType.STANDARDIZE, DataInfo.TransformType.DEMEAN, DataInfo.TransformType.DESCALE};
		public Random _rand = new Random();
		public PCAModel _golden = null;	//

		@BeforeClass
		public static void setup() {
				stall_till_cloudsize(1);
		}

		/*
		This unit test will test that pca method GramSVD works with wide datasets.  It will first build a model
		using GramSVD under normal setting (_wideDataset is set to false).  Next, it builds a GramSVD model with
		_wideDataSet set to true.  The eigenvalues and eigenvectors from the two models are compared.  Test will fail
		if any difference exceeds 1e-6.

		Six test cases are used:
		case 1. we test with a small dataset with all numerical data columns and make sure it works.
		case 2. we add NA rows to the	small dataset with all numerical data columns.
		case 3. test with the same small dataset while preserving the categorical columns;
		case 4. test with the same small dataset with categorical columns and add NA rows;
		case 5. test with prostate dataset;
		case 6. test with prostate dataset with NA rows added.
		*/
		@Test
		public void testWideDataSetGramSVD() throws InterruptedException, ExecutionException {
				ActualPCATests.testWideDataSets(PCAModel.PCAParameters.Method.GramSVD, _TOLERANCE, _smallDataset,
												false, true, _transformTypes[_rand.nextInt(_transformTypes.length)]);  // case 1
				ActualPCATests.testWideDataSets(PCAModel.PCAParameters.Method.GramSVD, _TOLERANCE, _smallDataset,
												true, true, _transformTypes[_rand.nextInt(_transformTypes.length)]);  // case 2
				ActualPCATests.testWideDataSets(PCAModel.PCAParameters.Method.GramSVD, _TOLERANCE, _smallDataset,
												false, false, _transformTypes[_rand.nextInt(_transformTypes.length)]);  // case 3
				ActualPCATests.testWideDataSets(PCAModel.PCAParameters.Method.GramSVD, _TOLERANCE, _smallDataset,
												true, false, _transformTypes[_rand.nextInt(_transformTypes.length)]);  // case 4
				ActualPCATests.testWideDataSets(PCAModel.PCAParameters.Method.GramSVD, _TOLERANCE, _prostateDataset,
												false, false, _transformTypes[_rand.nextInt(_transformTypes.length)]);  // case 5
				ActualPCATests.testWideDataSets(PCAModel.PCAParameters.Method.GramSVD, _TOLERANCE, _prostateDataset,
				true, false, _transformTypes[_rand.nextInt(_transformTypes.length)]);		  // case 6
		}


		/*
	 This unit test will test that pca method Power works with wide datasets.  It will first build a model
  using GramSVD under normal setting (_wideDataset is set to false).  Next, it builds a Power model with
  _wideDataSet set to true.  The eigenvalues and eigenvectors from the two models are compared.  Test will fail
  if any difference exceeds 1e-6.

		The same six test cases are used here.
*/
		@Test
		public void testWideDataSetPower() throws InterruptedException, ExecutionException {
				ActualPCATests.testWideDataSets(PCAModel.PCAParameters.Method.Power, _TOLERANCE, _smallDataset,
												false, true, _transformTypes[_rand.nextInt(_transformTypes.length)]);  // case 1
				ActualPCATests.testWideDataSets(PCAModel.PCAParameters.Method.Power, _TOLERANCE, _smallDataset,
												true, true, _transformTypes[_rand.nextInt(_transformTypes.length)]);   // case 2
				ActualPCATests.testWideDataSets(PCAModel.PCAParameters.Method.Power, _TOLERANCE, _smallDataset,
												false, false, _transformTypes[_rand.nextInt(_transformTypes.length)]);  // case 3
				ActualPCATests.testWideDataSets(PCAModel.PCAParameters.Method.Power, _TOLERANCE, _smallDataset,
												true, false, _transformTypes[_rand.nextInt(_transformTypes.length)]);  // case 4
				ActualPCATests.testWideDataSets(PCAModel.PCAParameters.Method.Power, _TOLERANCE, _prostateDataset,
												false, false, _transformTypes[_rand.nextInt(_transformTypes.length)]);  // case 5
				ActualPCATests.testWideDataSets(PCAModel.PCAParameters.Method.Power, _TOLERANCE, _prostateDataset,
												true, false, _transformTypes[_rand.nextInt(_transformTypes.length)]);  // case 6
		}

		/*
			This unit test will test that pca method Randomized works with wide datasets.  It will first build a model
			using GramSVD under normal setting (_wideDataset is set to false).  Next, it builds a Randomized model with
			_wideDataSet set to true.  The eigenvalues and eigenvectors from the two models are compared.  Test will fail
			if any difference exceeds 1e-6.

			The same six test cases are used here.
		*/
		@Test
		public void testWideDataSetRandomized() throws InterruptedException, ExecutionException {
				ActualPCATests.testWideDataSets(PCAModel.PCAParameters.Method.Randomized, _TOLERANCE, _smallDataset,
												false, true, _transformTypes[_rand.nextInt(_transformTypes.length)]);  // case 1
				ActualPCATests.testWideDataSets(PCAModel.PCAParameters.Method.Randomized, _TOLERANCE, _smallDataset,
												true, true, _transformTypes[_rand.nextInt(_transformTypes.length)]);   // case 2
				ActualPCATests.testWideDataSets(PCAModel.PCAParameters.Method.Randomized, _TOLERANCE, _smallDataset,
												false, false, _transformTypes[_rand.nextInt(_transformTypes.length)]);  // case 3
				ActualPCATests.testWideDataSets(PCAModel.PCAParameters.Method.Randomized, _TOLERANCE, _smallDataset,
												true, false, _transformTypes[_rand.nextInt(_transformTypes.length)]);  // case 4
				ActualPCATests.testWideDataSets(PCAModel.PCAParameters.Method.Randomized, _TOLERANCE, _prostateDataset,
												false, false, _transformTypes[_rand.nextInt(_transformTypes.length)]);  // case 5
				ActualPCATests.testWideDataSets(PCAModel.PCAParameters.Method.Randomized, _TOLERANCE, _prostateDataset,
												true, false, _transformTypes[_rand.nextInt(_transformTypes.length)]);  // case 6
		}
}

/*
		This class performs the actual PCA tests.
 */
class ActualPCATests {
		public static void testWideDataSets(PCAModel.PCAParameters.Method pcaMethod, double tolerance, String datafile,
																																						boolean addNAs, boolean removeColumns, DataInfo.TransformType transformType)
										throws InterruptedException, ExecutionException {
				Scope.enter();
				PCAModel modelN = null;     // store PCA models generated with original implementation
				PCAModel modelW = null;     // store PCA models generated with wideDataSet set to true
				Frame train = null, scoreN = null, scoreW = null;
				try {
						train = parse_test_file(Key.make(datafile), datafile);
						Scope.track(train);
						if (removeColumns) {
								train.remove(12).remove();    // remove categorical columns
								train.remove(11).remove();
								train.remove(10).remove();
						}
						if (addNAs) {
								train.vec(0).setNA(0);          // set NAs
								train.vec(3).setNA(10);
								train.vec(5).setNA(20);
						}
						DKV.put(train);

						PCAModel.PCAParameters parms = new PCAModel.PCAParameters();
						parms._train = train._key;
						parms._k = 3;
						parms._transform = transformType;
						Log.info("Data transformation applied is "+parms._transform.name());
						parms._use_all_factor_levels = true;
						parms._pca_method = PCAModel.PCAParameters.Method.GramSVD;
						parms._impute_missing = false;
						parms._seed = 12345;
						PCA pcaParms = new PCA(parms);
						modelN = pcaParms.trainModel().get(); // get normal data
						scoreN = modelN.score(train);
						Scope.track(scoreN);
						Scope.track_generic(modelN);

						parms._pca_method = pcaMethod;
						PCA pcaParmsW = new PCA(parms);
						pcaParmsW.setWideDataset(true);  // force to treat dataset as wide even though it is not.
						modelW = pcaParmsW.trainModel().get();
						scoreW = modelW.score(train);
						Scope.track(scoreW);
						Scope.track_generic(modelW);

						// compare eigenvectors and eigenvalues generated by original PCA and wide dataset PCA.
						TestUtil.checkStddev(modelW._output._std_deviation, modelN._output._std_deviation, tolerance);
						boolean[] flippedEig = TestUtil.checkEigvec(modelW._output._eigenvectors, modelN._output._eigenvectors, tolerance);
						TestUtil.checkProjection(scoreW, scoreN, tolerance, flippedEig);
						// Build a POJO, check results with original PCA
						assertTrue(modelN.testJavaScoring(train, scoreN, tolerance));
						// Build a POJO, check results with wide dataset PCA
						assertTrue(modelW.testJavaScoring(train, scoreW, tolerance));
				} finally {
						Scope.exit();
				}
		}
}
