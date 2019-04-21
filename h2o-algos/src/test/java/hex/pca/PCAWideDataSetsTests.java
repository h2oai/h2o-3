package hex.pca;

import hex.DataInfo;
import hex.pca.PCAModel.PCAParameters;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.util.Log;

import java.util.Random;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertTrue;
import static org.junit.runners.Parameterized.Parameter;
import static org.junit.runners.Parameterized.Parameters;
import static water.TestUtil.parse_test_file;

/**
	* Created by wendycwong on 2/27/17.
	*/
@RunWith(Parameterized.class)
public class PCAWideDataSetsTests extends TestUtil {
		public static final double _TOLERANCE = 1e-6;
		public static  String _smallDataset = "smalldata/pca_test/decathlon.csv";
		public static final String _prostateDataset = "smalldata/prostate/prostate_cat.csv";
		public static final DataInfo.TransformType[] _transformTypes = {DataInfo.TransformType.NONE,
										DataInfo.TransformType.STANDARDIZE, DataInfo.TransformType.DEMEAN, DataInfo.TransformType.DESCALE};
		public Random _rand = new Random();
		public PCAModel _golden = null;	//
		private PCAParameters pcaParameters;
	
		@Parameters
		public static PCAImplementation[] parametersForSvdImplementation() {
			return hex.pca.PCAImplementation.values();
		}
		
		@Parameter
		public PCAImplementation PCAImplementation;
		
		@BeforeClass
		public static void setup() {
				stall_till_cloudsize(1);
		}
		
		@Before
		public void setupPcaParameters() {
			pcaParameters = new PCAParameters();
			pcaParameters._pca_implementation = PCAImplementation;
			water.util.Log.info("pcaParameters._PCAImplementation: " + pcaParameters._pca_implementation.name());
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
				ActualPCATests.testWideDataSets(PCAParameters.Method.GramSVD, PCAParameters.Method.GramSVD,
												_TOLERANCE, _smallDataset, false, true,
												_transformTypes[_rand.nextInt(_transformTypes.length)], pcaParameters);  // case 1
				ActualPCATests.testWideDataSets(PCAParameters.Method.GramSVD, PCAParameters.Method.GramSVD,
												_TOLERANCE, _smallDataset, true, true,
												_transformTypes[_rand.nextInt(_transformTypes.length)], pcaParameters);  // case 2
				ActualPCATests.testWideDataSets(PCAParameters.Method.GramSVD, PCAParameters.Method.GramSVD,
												_TOLERANCE, _smallDataset, false, false,
												_transformTypes[_rand.nextInt(_transformTypes.length)], pcaParameters);  // case 3
				ActualPCATests.testWideDataSets(PCAParameters.Method.GramSVD, PCAParameters.Method.GramSVD,
												_TOLERANCE, _smallDataset, true, false,
												_transformTypes[_rand.nextInt(_transformTypes.length)], pcaParameters);  // case 4
				ActualPCATests.testWideDataSets(PCAParameters.Method.GramSVD, PCAParameters.Method.GramSVD,
												_TOLERANCE, _prostateDataset, false, false,
												_transformTypes[_rand.nextInt(_transformTypes.length)], pcaParameters);  // case 5
				ActualPCATests.testWideDataSets(PCAParameters.Method.GramSVD, PCAParameters.Method.GramSVD,
												_TOLERANCE, _prostateDataset, true, false,
												_transformTypes[_rand.nextInt(_transformTypes.length)], pcaParameters);		  // case 6
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
				ActualPCATests.testWideDataSets(PCAParameters.Method.GramSVD, PCAParameters.Method.Power,
												_TOLERANCE, _smallDataset, false, true,
												_transformTypes[_rand.nextInt(_transformTypes.length)], pcaParameters);  // case 1
				ActualPCATests.testWideDataSets(PCAParameters.Method.GramSVD,
												PCAParameters.Method.Power, _TOLERANCE, _smallDataset, true, true,
												_transformTypes[_rand.nextInt(_transformTypes.length)], pcaParameters);   // case 2
				ActualPCATests.testWideDataSets(PCAParameters.Method.GramSVD,
												PCAParameters.Method.Power, _TOLERANCE, _smallDataset, false, false,
												_transformTypes[_rand.nextInt(_transformTypes.length)], pcaParameters);  // case 3
				ActualPCATests.testWideDataSets(PCAParameters.Method.GramSVD,
												PCAParameters.Method.Power, _TOLERANCE, _smallDataset, true, false,
												_transformTypes[_rand.nextInt(_transformTypes.length)], pcaParameters);  // case 4
				ActualPCATests.testWideDataSets(PCAParameters.Method.GramSVD,
												PCAParameters.Method.Power, _TOLERANCE, _prostateDataset, false, false,
												_transformTypes[_rand.nextInt(_transformTypes.length)], pcaParameters);  // case 5
				ActualPCATests.testWideDataSets(PCAParameters.Method.GramSVD,
												PCAParameters.Method.Power, _TOLERANCE, _prostateDataset, true, false,
												_transformTypes[_rand.nextInt(_transformTypes.length)], pcaParameters);  // case 6
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
				ActualPCATests.testWideDataSets(PCAParameters.Method.GramSVD,
												PCAParameters.Method.Randomized, _TOLERANCE, _smallDataset, false, true,
												_transformTypes[_rand.nextInt(_transformTypes.length)], pcaParameters);  // case 1
				ActualPCATests.testWideDataSets(PCAParameters.Method.GramSVD,
												PCAParameters.Method.Randomized, _TOLERANCE, _smallDataset, true, true,
												_transformTypes[_rand.nextInt(_transformTypes.length)], pcaParameters);   // case 2
				ActualPCATests.testWideDataSets(PCAParameters.Method.GramSVD,
												PCAParameters.Method.Randomized, _TOLERANCE, _smallDataset, false, false,
												_transformTypes[_rand.nextInt(_transformTypes.length)], pcaParameters);  // case 3
				ActualPCATests.testWideDataSets(PCAParameters.Method.GramSVD,
												PCAParameters.Method.Randomized, _TOLERANCE, _smallDataset, true, false,
												_transformTypes[_rand.nextInt(_transformTypes.length)], pcaParameters);  // case 4
				ActualPCATests.testWideDataSets(PCAParameters.Method.GramSVD,
												PCAParameters.Method.Randomized, _TOLERANCE, _prostateDataset, false, false,
												_transformTypes[_rand.nextInt(_transformTypes.length)], pcaParameters);  // case 5
				ActualPCATests.testWideDataSets(PCAParameters.Method.GramSVD,
												PCAParameters.Method.Randomized, _TOLERANCE, _prostateDataset, true, false,
												_transformTypes[_rand.nextInt(_transformTypes.length)], pcaParameters);  // case 6
		}

		/*
	This unit test will test that pca method GLRM works with wide datasets.  It will first build a model
	using GramSVD under normal setting (_wideDataset is set to false).  Next, it builds a GLRM model with
	_wideDataSet set to true.  The eigenvalues and eigenvectors from the two models are compared.  Test will fail
	if any difference exceeds 1e-6 only for numerical dataset.  For categorical datasets, GLRM will not
	generate the same set of eigenvalues/vectors.  Hence for datasets with Enum columns, we will compare the two
	models built with normal and widedataset GLRM.
*/
		@Test
		public void testWideDataSetGLRM() throws InterruptedException, ExecutionException {
				ActualPCATests.testWideDataSets(PCAParameters.Method.GramSVD, PCAParameters.Method.GLRM,
												0.0001, _smallDataset, false, true,
												_transformTypes[1], pcaParameters);  // case 1 numerical
				ActualPCATests.testWideDataSets(PCAParameters.Method.GLRM, PCAParameters.Method.GLRM,_TOLERANCE,
												_smallDataset, true, true, _transformTypes[_rand.nextInt(_transformTypes.length)], pcaParameters);   // case 2
				ActualPCATests.testWideDataSets(PCAParameters.Method.GLRM, PCAParameters.Method.GLRM,_TOLERANCE,
												_smallDataset, false, false, _transformTypes[_rand.nextInt(_transformTypes.length)], pcaParameters);  // case 3
				ActualPCATests.testWideDataSets(PCAParameters.Method.GLRM, PCAParameters.Method.GLRM,_TOLERANCE,
												_smallDataset, true, false, _transformTypes[_rand.nextInt(_transformTypes.length)], pcaParameters);  // case 4
				ActualPCATests.testWideDataSets(PCAParameters.Method.GLRM, PCAParameters.Method.GLRM, _TOLERANCE,
												_prostateDataset, false, false,
												_transformTypes[_rand.nextInt(_transformTypes.length)], pcaParameters);  // case 5
				ActualPCATests.testWideDataSets(PCAParameters.Method.GLRM, PCAParameters.Method.GLRM, _TOLERANCE,
												_prostateDataset, true, false,
												_transformTypes[_rand.nextInt(_transformTypes.length)], pcaParameters);  // case 6
		}
}

/*
		This class performs the actual PCA tests.
 */
class ActualPCATests {
		public static void testWideDataSets(PCAParameters.Method pcaMethod, PCAParameters.Method pcaMethod2,
		                                    double tolerance, String datafile,
		                                    boolean addNAs, boolean removeColumns, DataInfo.TransformType transformType,
		                                    PCAParameters pcaParameters)
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

						pcaParameters._train = train._key;
						pcaParameters._k = 3;
						pcaParameters._transform = transformType;
						Log.info("Data transformation applied is "+pcaParameters._transform.name());
						pcaParameters._use_all_factor_levels = true;
						pcaParameters._pca_method = pcaMethod;
						pcaParameters._impute_missing = false;
						pcaParameters._seed = 12345;
						PCA pcaParms = new PCA(pcaParameters);
						modelN = pcaParms.trainModel().get(); // get normal data
						scoreN = modelN.score(train);
						Scope.track(scoreN);
						Scope.track_generic(modelN);

						pcaParameters._pca_method = pcaMethod2;
						PCA pcaParmsW = new PCA(pcaParameters);
						pcaParmsW.setWideDataset(true);  // force to treat dataset as wide even though it is not.
						modelW = pcaParmsW.trainModel().get();
						scoreW = modelW.score(train);
						Scope.track(scoreW);
						Scope.track_generic(modelW);

						// compare eigenvectors and eigenvalues generated by original PCA and wide dataset PCA.
						TestUtil.checkStddev(modelW._output._std_deviation, modelN._output._std_deviation, tolerance);
						boolean[] flippedEig = TestUtil.checkEigvec(modelW._output._eigenvectors, modelN._output._eigenvectors, tolerance);
						TestUtil.checkProjection(scoreW, scoreN, tolerance, flippedEig);

						if (!(pcaMethod == PCAParameters.Method.GLRM)) {	// GLRM only works with numerical columns well

								// Build a POJO, check results with original PCA
								assertTrue(modelN.testJavaScoring(train, scoreN, tolerance));
								// Build a POJO, check results with wide dataset PCA
								assertTrue(modelW.testJavaScoring(train, scoreW, tolerance));
						}
				} finally {
						Scope.exit();
				}
		}
}
