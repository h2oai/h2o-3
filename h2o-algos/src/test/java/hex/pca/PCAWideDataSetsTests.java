package hex.pca;

import hex.DataInfo;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;

import java.util.concurrent.ExecutionException;

import static water.TestUtil.parse_test_file;

/**
	* Created by wendycwong on 2/27/17.
	*/
public class PCAWideDataSetsTests extends TestUtil {
		public static final double TOLERANCE = 1e-6;
		@BeforeClass
		public static void setup() { stall_till_cloudsize(1); }

		/*
		This unit test uses the prostate datasets with NAs, calculate the eigenvectors/values with original PCA implementation.
		Next, it calculates the eigenvectors/values using PCA with wide dataset flag set to true.  Then, we
		compare the eigenvalues/vectors from both methods and they should agree.  Dataset contains numerical and
		categorical columns.  All pca methods are tested.
		*/
		@Test
		public void testWideDataSetsWithNAs() throws InterruptedException, ExecutionException {
				ActualPCATests.testWideDataSetsWithNAs(PCAModel.PCAParameters.Method.GramSVD, TOLERANCE);	// pca_method=GramSVD
				ActualPCATests.testWideDataSetsWithNAs(PCAModel.PCAParameters.Method.Power, TOLERANCE);	// pca_method=Power
//				ActualPCATests.testWideDataSetsWithNAs(PCAModel.PCAParameters.Method.Randomized, TOLERANCE);	// pca_method=Randomized
//				ActualPCATests.testWideDataSetsWithNAs(PCAModel.PCAParameters.Method.GLRM, TOLERANCE);	// pca_method=GLRM
		}

		/*
		This unit test uses the prostate datasets, calculate the eigenvectors/values with original PCA implementation.
		Next, it calculates the eigenvectors/values using PCA with wide dataset flag set to true.  Then, we
		compare the eigenvalues/vectors from both methods and they should agree.  Dataset contains numerical and
		categorical columns.  All pca methods are tested.
		*/
		@Test public void testWideDataSets() throws InterruptedException, ExecutionException {
				ActualPCATests.testWideDataSets(PCAModel.PCAParameters.Method.GramSVD, TOLERANCE);	// pca_method=GramSVD
				ActualPCATests.testWideDataSets(PCAModel.PCAParameters.Method.Power, TOLERANCE);	// pca_method=Power
//				ActualPCATests.testWideDataSets(PCAModel.PCAParameters.Method.Randomized, TOLERANCE);	// pca_method=Randomized
//				ActualPCATests.testWideDataSets(PCAModel.PCAParameters.Method.GLRM, TOLERANCE);	// pca_method=GLRM
		}

		/*
		This unit test uses a small datasets, calculate the eigenvectors/values with original PCA implementation.
		Next, it calculates the eigenvectors/values using PCA with wide dataset flag set to true.  Then, we
		compare the eigenvalues/vectors from both methods and they should agree.  In this case, we only
		have numerical columns and no categorical columns.  All pca methods are tested.
		*/
		@Test public void testWideDataSetsSmallDataNumeric() throws InterruptedException, ExecutionException {
				ActualPCATests.testWideDataSetsSmallDataNumeric(PCAModel.PCAParameters.Method.GramSVD, TOLERANCE);	// pca_method=GramSVD
				ActualPCATests.testWideDataSetsSmallDataNumeric(PCAModel.PCAParameters.Method.Power, TOLERANCE);	// pca_method=Power
//				ActualPCATests.testWideDataSetsSmallDataNumeric(PCAModel.PCAParameters.Method.Randomized, TOLERANCE);	// pca_method=Randomized
//				ActualPCATests.testWideDataSetsSmallDataNumeric(PCAModel.PCAParameters.Method.GLRM, TOLERANCE);	// pca_method=GLRM
		}

		/*
		This unit test uses a small datasets, calculate the eigenvectors/values with original PCA implementation.
		Next, it calculates the eigenvectors/values using PCA with wide dataset flag set to true.  Then, we
		compare the eigenvalues/vectors from both methods and they should agree.  In this case, we only
		have numerical columns and no categorical columns.  All pca methods are tested.
		*/
		@Test public void testWideDataSetsSmallDataNumericNAs() throws InterruptedException, ExecutionException {
				ActualPCATests.testWideDataSetsSmallDataNumericNAs(PCAModel.PCAParameters.Method.GramSVD, TOLERANCE);	// pca_method=GramSVD
				ActualPCATests.testWideDataSetsSmallDataNumericNAs(PCAModel.PCAParameters.Method.Power, TOLERANCE);	// pca_method=Power
//				ActualPCATests.testWideDataSetsSmallDataNumericNAs(PCAModel.PCAParameters.Method.Randomized, TOLERANCE);	// pca_method=Randomized
//				ActualPCATests.testWideDataSetsSmallDataNumericNAs(PCAModel.PCAParameters.Method.GLRM, TOLERANCE);	// pca_method=GLRM
		}

		/*
		This unit test uses a small datasets, calculate the eigenvectors/values with original PCA implementation.
		Next, it calculates the eigenvectors/values using PCA with wide dataset flag set to true.  Then, we
		compare the eigenvalues/vectors from both methods and they should agree.  Dataset contains numerical and
		categorical columns.  All pca methods are tested.
		*/
		@Test public void testWideDataSetsSmallData() throws InterruptedException, ExecutionException {
				ActualPCATests.testWideDataSetsSmallData(PCAModel.PCAParameters.Method.GramSVD, TOLERANCE);	// pca_method=GramSVD
				ActualPCATests.testWideDataSetsSmallData(PCAModel.PCAParameters.Method.Power, TOLERANCE);	// pca_method=Power
//				ActualPCATests.testWideDataSetsSmallData(PCAModel.PCAParameters.Method.Randomized, TOLERANCE);	// pca_method=Randomized
//				ActualPCATests.testWideDataSetsSmallData(PCAModel.PCAParameters.Method.GLRM, TOLERANCE);	// pca_method=GLRM
		}

		/*
		This unit test uses a small datasets, calculate the eigenvectors/values with original PCA implementation.
		Next, it calculates the eigenvectors/values using PCA with wide dataset flag set to true.  Then, we
		compare the eigenvalues/vectors from both methods and they should agree.  Dataset contains numerical and
		categorical columns.  All pca methods are tested.
		*/
		@Test public void testWideDataSetsSmallDataNA() throws InterruptedException, ExecutionException {
				ActualPCATests.testWideDataSetsSmallDataNA(PCAModel.PCAParameters.Method.GramSVD, TOLERANCE);	// pca_method=GramSVD
				ActualPCATests.testWideDataSetsSmallDataNA(PCAModel.PCAParameters.Method.Power, TOLERANCE);	// pca_method=Power
//				ActualPCATests.testWideDataSetsSmallDataNA(PCAModel.PCAParameters.Method.Randomized, TOLERANCE);	// pca_method=Randomized
//				ActualPCATests.testWideDataSetsSmallDataNA(PCAModel.PCAParameters.Method.GLRM, TOLERANCE);	// pca_method=GLRM
		}
}

/*
  This class performs the actual PCA tests.
 */
class ActualPCATests {
		public static void testWideDataSetsWithNAs(PCAModel.PCAParameters.Method pcaMethod, double tolerance)
										throws InterruptedException, ExecutionException {
				Scope.enter();
				PCAModel modelN = null;     // store PCA models generated with original implementation
				PCAModel modelW = null;     // store PCA models generated with wideDataSet set to true
				Frame train = null, scoreN = null, scoreW = null;
				try {
						train = parse_test_file(Key.make("prostate_catNA.hex"), "smalldata/prostate/prostate_cat.csv");
						Scope.track(train);
						train.vec(0).setNA(0);
						train.vec(3).setNA(10);
						train.vec(5).setNA(100);
						DKV.put(train);
						PCAModel.PCAParameters parms = new PCAModel.PCAParameters();
						parms._train = train._key;
						parms._k = 7;
						parms._transform = DataInfo.TransformType.STANDARDIZE;
						parms._use_all_factor_levels = true;
						parms._pca_method = PCAModel.PCAParameters.Method.GramSVD;
						parms._impute_missing=false;
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

						// check to make sure eigenvalues and eigenvectors are the same
						// compare eigenvectors and eigenvalues generated by original PCA and wide dataset PCA.
						TestUtil.checkStddev(modelW._output._std_deviation, modelN._output._std_deviation, tolerance);
						boolean[] flippedEig = TestUtil.checkEigvec( modelW._output._eigenvectors, modelN._output._eigenvectors, tolerance);
						TestUtil.checkProjection(scoreW, scoreN, tolerance, flippedEig);
						// Build a POJO, check results with original PCA
						Assert.assertTrue(modelN.testJavaScoring(train,scoreN,tolerance));
						// Build a POJO, check results with wide dataset PCA
						Assert.assertTrue(modelW.testJavaScoring(train,scoreW,tolerance));
				} finally {
						Scope.exit();
				}
		}

		public static void testWideDataSets(PCAModel.PCAParameters.Method pcaMethod, double tolerance)
										throws InterruptedException, ExecutionException {
				Scope.enter();
				PCAModel modelN = null;     // store PCA models generated with original implementation
				PCAModel modelW = null;     // store PCA models generated with wideDataSet set to true
				Frame train = null, scoreN = null, scoreW = null;
				try {
						train = parse_test_file(Key.make("prostate_cat.hex"), "smalldata/prostate/prostate_cat.csv");
						Scope.track(train);
						PCAModel.PCAParameters parms = new PCAModel.PCAParameters();
						parms._train = train._key;
						parms._k = 7;
						parms._transform = DataInfo.TransformType.STANDARDIZE;
						parms._use_all_factor_levels = true;
						parms._pca_method = PCAModel.PCAParameters.Method.GramSVD;
						parms._impute_missing=false;
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

						// check to make sure eigenvalues and eigenvectors are the same
						// compare eigenvectors and eigenvalues generated by original PCA and wide dataset PCA.
						TestUtil.checkStddev(modelW._output._std_deviation, modelN._output._std_deviation, tolerance);
						boolean[] flippedEig = TestUtil.checkEigvec( modelW._output._eigenvectors, modelN._output._eigenvectors, tolerance);
						TestUtil.checkProjection(scoreN, scoreW, tolerance, flippedEig);
						// Build a POJO, check results with original PCA
						Assert.assertTrue(modelN.testJavaScoring(train,scoreN,tolerance));
						// Build a POJO, check results with wide dataset PCA
						Assert.assertTrue(modelW.testJavaScoring(train,scoreW,tolerance));
				} finally {
						Scope.exit();
				}
		}

		public static void testWideDataSetsSmallDataNumeric(PCAModel.PCAParameters.Method pcaMethod, double tolerance)
										throws InterruptedException, ExecutionException {
				Scope.enter();
				PCAModel modelN = null;     // store PCA models generated with original implementation
				PCAModel modelW = null;     // store PCA models generated with wideDataSet set to true
				Frame train = null, scoreN = null, scoreW = null;
				try {
						train = parse_test_file(Key.make("decathlonN.hex"), "smalldata/pca_test/decathlon.csv");
						Scope.track(train);
						train.remove(12).remove();    // remove categorical columns
						train.remove(11).remove();
						train.remove(10).remove();
						DKV.put(train);
						PCAModel.PCAParameters parms = new PCAModel.PCAParameters();
						parms._train = train._key;
						parms._k = 3;
						parms._transform = DataInfo.TransformType.NONE;
						parms._use_all_factor_levels = true;
						parms._pca_method = PCAModel.PCAParameters.Method.GramSVD;
						parms._impute_missing=false;
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
						boolean[] flippedEig = TestUtil.checkEigvec( modelW._output._eigenvectors, modelN._output._eigenvectors, tolerance);
						TestUtil.checkProjection(scoreW, scoreN, tolerance, flippedEig);
						// Build a POJO, check results with original PCA
						Assert.assertTrue(modelN.testJavaScoring(train,scoreN,tolerance));
						// Build a POJO, check results with wide dataset PCA
						Assert.assertTrue(modelW.testJavaScoring(train,scoreW,tolerance));
				} finally {
						Scope.exit();
				}
		}

		public static void testWideDataSetsSmallDataNumericNAs(PCAModel.PCAParameters.Method pcaMethod, double tolerance)
										throws InterruptedException, ExecutionException {
				Scope.enter();
				PCAModel modelN = null;     // store PCA models generated with original implementation
				PCAModel modelW = null;     // store PCA models generated with wideDataSet set to true
				Frame train = null, scoreN = null, scoreW = null;
				try {
						train = parse_test_file(Key.make("decathlonNNA.hex"), "smalldata/pca_test/decathlon.csv");
						Scope.track(train);
						train.remove(12).remove();    // remove categorical columns
						train.remove(11).remove();
						train.remove(10).remove();

						// set NAs
						train.vec(0).setNA(0);
						train.vec(3).setNA(10);
						train.vec(5).setNA(20);
						DKV.put(train);

						PCAModel.PCAParameters parms = new PCAModel.PCAParameters();
						parms._train = train._key;
						parms._k = 3;
						parms._transform = DataInfo.TransformType.NONE;
						parms._use_all_factor_levels = true;
						parms._pca_method = PCAModel.PCAParameters.Method.GramSVD;
						parms._impute_missing=false;
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
						boolean[] flippedEig = TestUtil.checkEigvec( modelW._output._eigenvectors, modelN._output._eigenvectors, tolerance);
						TestUtil.checkProjection(scoreW, scoreN, tolerance, flippedEig);
						// Build a POJO, check results with original PCA
						Assert.assertTrue(modelN.testJavaScoring(train,scoreN,tolerance));
						// Build a POJO, check results with wide dataset PCA
						Assert.assertTrue(modelW.testJavaScoring(train,scoreW,tolerance));
				} finally {
						Scope.exit();
				}
		}

		public static void testWideDataSetsSmallData(PCAModel.PCAParameters.Method pcaMethod, double tolerance)
										throws InterruptedException, ExecutionException {
				Scope.enter();
				PCAModel modelN = null;     // store PCA models generated with original implementation
				PCAModel modelW = null;     // store PCA models generated with wideDataSet set to true
				Frame train = null, scoreN = null, scoreW = null;
				try {
						train = parse_test_file(Key.make("decathlon.hex"), "smalldata/pca_test/decathlon.csv");
						Scope.track(train);
						PCAModel.PCAParameters parms = new PCAModel.PCAParameters();
						parms._train = train._key;
						parms._k = 3;
						parms._transform = DataInfo.TransformType.NONE;
						parms._use_all_factor_levels = true;
						parms._pca_method = PCAModel.PCAParameters.Method.GramSVD;
						parms._impute_missing=false;
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
						boolean[] flippedEig = TestUtil.checkEigvec( modelW._output._eigenvectors, modelN._output._eigenvectors, tolerance);
						TestUtil.checkProjection(scoreW, scoreN, tolerance, flippedEig);
						// Build a POJO, check results with original PCA
						Assert.assertTrue(modelN.testJavaScoring(train,scoreN,tolerance));
						// Build a POJO, check results with wide dataset PCA
						Assert.assertTrue(modelW.testJavaScoring(train,scoreW,tolerance));
				} finally {
						Scope.exit();
				}
		}

		public static void testWideDataSetsSmallDataNA(PCAModel.PCAParameters.Method pcaMethod, double tolerance)
										throws InterruptedException, ExecutionException {
				Scope.enter();
				PCAModel modelN = null;     // store PCA models generated with original implementation
				PCAModel modelW = null;     // store PCA models generated with wideDataSet set to true
				Frame train = null, scoreN = null, scoreW = null;
				try {
						train = parse_test_file(Key.make("decalthonNA.hex"), "smalldata/pca_test/decathlon.csv");
						Scope.track(train);
						// set NAs
						train.vec(0).setNA(0);
						train.vec(3).setNA(10);
						train.vec(5).setNA(20);
						DKV.put(train);

						PCAModel.PCAParameters parms = new PCAModel.PCAParameters();
						parms._train = train._key;
						parms._k = 3;
						parms._transform = DataInfo.TransformType.NONE;
						parms._use_all_factor_levels = true;
						parms._pca_method = PCAModel.PCAParameters.Method.GramSVD;
						parms._impute_missing=false;
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
						boolean[] flippedEig = TestUtil.checkEigvec( modelW._output._eigenvectors, modelN._output._eigenvectors, tolerance);
						TestUtil.checkProjection(scoreW, scoreN, tolerance, flippedEig);
						// Build a POJO, check results with original PCA
						Assert.assertTrue(modelN.testJavaScoring(train,scoreN,tolerance));
						// Build a POJO, check results with wide dataset PCA
						Assert.assertTrue(modelW.testJavaScoring(train,scoreW,tolerance));
				} finally {
						Scope.exit();
				}
		}
}
