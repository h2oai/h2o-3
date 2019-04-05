package hex.tree.xgboost;

import hex.DataInfo;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import water.MRTask;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.util.FrameUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.*;

public class XGBoostUtilsTest extends TestUtil {

  private static final int DEFAULT_SPARSE_MATRIX_SIZE = XGBoostUtils.SPARSE_MATRIX_DIM;
  
  @BeforeClass
  public static void beforeClass(){
    TestUtil.stall_till_cloudsize(1);
  }

  @After
  public void tearDown() {
    revertDefaultSparseMatrixMaxSize();
  }
  @Test
  public void parseFeatureScores() throws IOException, ParseException {
    String[] modelDump = readLines(getClass().getResource("xgbdump.txt"));
    String[] expectedVarImps = readLines(getClass().getResource("xgbvarimps.txt"));

    Map<String, XGBoostUtils.FeatureScore> scores = XGBoostUtils.parseFeatureScores(modelDump);
    double totalGain = 0;
    double totalCover = 0;
    double totalFrequency = 0;
    for (XGBoostUtils.FeatureScore score : scores.values()) {
      totalGain += score._gain;
      totalCover += score._cover;
      totalFrequency += score._frequency;
    }

    NumberFormat nf = NumberFormat.getInstance(Locale.US);
    for (String varImp : expectedVarImps) {
      String[] vals = varImp.split(" ");
      XGBoostUtils.FeatureScore score = scores.get(vals[0]);
      assertNotNull("Score " + vals[0] + " should ve calculated", score);
      float expectedGain = nf.parse(vals[1]).floatValue();
      assertEquals("Gain of " + vals[0], expectedGain, score._gain / totalGain, 1e-6);
      float expectedCover = nf.parse(vals[2]).floatValue();
      assertEquals("Cover of " + vals[0], expectedCover, score._cover / totalCover, 1e-6);
      float expectedFrequency = nf.parse(vals[3]).floatValue();
      assertEquals("Frequency of " + vals[0], expectedFrequency, score._frequency / totalFrequency, 1e-6);
    }
  }

  @Test
  public void testCSRPredictionComparison_cars() {
    //Cars is a 100% dense dataset (useful edge case)
    try {
      Scope.enter();
      final String response = "cylinders";
      final Frame frame = TestUtil.parse_test_file("smalldata/junit/cars.csv");
      Scope.track(frame);
      final Frame testFrame = TestUtil.parse_test_file("smalldata/testng/cars_test.csv");
      Scope.track(testFrame);

      testCSRPredictions(frame, response, testFrame);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testCSRPredictionComparison_airlines() {
    try {
      Scope.enter();
      final String response = "IsDepDelayed";

      final Frame frame = TestUtil.parse_test_file("smalldata/testng/airlines.csv");
      Scope.track(frame);
      final Frame testFrame = TestUtil.parse_test_file("smalldata/testng/airlines_test.csv");
      Scope.track(testFrame);
      
      testCSRPredictions(frame, response, testFrame );
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testCSRPredictionComparison_airQuality() {
    try {
      Scope.enter();
      final String response = "Ozone";

      final Frame frame = TestUtil.parse_test_file("smalldata/testng/airquality_train1.csv");
      Scope.track(frame);
      final Frame testFrame = TestUtil.parse_test_file("smalldata/testng/airquality_validation1.csv");
      Scope.track(testFrame);

      testCSRPredictions(frame, response, testFrame );
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testCSRPredictionComparison_prostate() {
    try {
      Scope.enter();
      final String response = "GLEASON";

      final Frame frame = TestUtil.parse_test_file("smalldata/testng/prostate_train.csv");
      Scope.track(frame);
      final Frame testFrame = TestUtil.parse_test_file("smalldata/testng/prostate_test.csv");
      Scope.track(testFrame);

      testCSRPredictions(frame, response, testFrame );
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testCSRPredictionComparison_synthetic_sparse() {
    try {
      Scope.enter();
      final String response = "y_gamma";

      final Frame frame = TestUtil.parse_test_file("smalldata/testng/synthetic_sparse_train.csv");
      Scope.track(frame);
      final Frame testFrame = TestUtil.parse_test_file("smalldata/testng/synthetic_sparse_test.csv");
      Scope.track(testFrame);

      testCSRPredictions(frame, response, testFrame );
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testSparsematrixNumLines() throws XGBoostError {

    Frame frame = null;
    try {
      frame = Scope.track(new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("C1", "C2", "C3", "C4")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ard(1, 0, 3, 1))
              .withDataForCol(1, ard(0, 0, 0, 0))
              .withDataForCol(2, ard(2, 0, 0, 0))
              .withDataForCol(3, ard(0, 0, 0, 4))
              .build());
      final DMatrix response = XGBoostUtils.convertFrameToDMatrix(new DataInfo(frame, null, true, DataInfo.TransformType.NONE, false, false, false),
              frame, true, "C4", null, null, true);
      assertNotNull(response);
      assertEquals(4, response.rowNum());
      assertArrayEquals(arf(0, 0, 0, 4), response.getLabel(), 0f);

    } finally {
      if (frame != null) frame.remove();
    }
  }

  private static String[] readLines(URL url) throws IOException{
    List<String> lines = new ArrayList<>();
    try (BufferedReader r = new BufferedReader(new InputStreamReader(url.openStream()))) {
      String line;
      while ((line = r.readLine()) != null) {
        lines.add(line);
      }
    }
    return lines.toArray(new String[0]);
  }
  /**
   * Sets maximum dimensions of XGBoost's sparse matrix filled with data
   */
  private static final class XGBSparseMatrixDimTask extends MRTask<XGBSparseMatrixDimTask> {

    private final int _maxMatrixDimension;

    private XGBSparseMatrixDimTask(int maxMatrixDimension) {
      if (maxMatrixDimension < 1) throw new IllegalArgumentException("Max matrix dimension must be greater than 0.");
      _maxMatrixDimension = maxMatrixDimension;
    }

    @Override
    protected void setupLocal() {
      XGBoostUtils.SPARSE_MATRIX_DIM = _maxMatrixDimension;
      assertEquals(XGBoostUtils.SPARSE_MATRIX_DIM, _maxMatrixDimension);
    }
  }


  /**
   * @param dim Dimension of the sparse square matrix.
   */
  private static void setSparseMatrixMaxDimensions(final int dim) {
    new XGBSparseMatrixDimTask(dim)
            .doAllNodes();
  }

  /**
   * Reverts sparse matrix maximum size back to the default collected during initiation of this test suite
   */
  private static void revertDefaultSparseMatrixMaxSize() {
    new XGBSparseMatrixDimTask(DEFAULT_SPARSE_MATRIX_SIZE)
            .doAllNodes();
  }


  /**
   * Builds 3 models using the same training dataset and performs prediction with each model. Then checks if resulting 
   * frames are 100% the same.
   * @param trainingFrame 
   * @param response Response column
   * @param validationFrame
   */
  private static void testCSRPredictions(final Frame trainingFrame, final String response, final Frame validationFrame) {
    try {

      Scope.enter();
      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._response_column = response;
      parms._train = trainingFrame._key;
      parms._ntrees = 10;
      parms._backend = XGBoostModel.XGBoostParameters.Backend.cpu;
      parms._dmatrix_type = XGBoostModel.XGBoostParameters.DMatrixType.sparse;

      // First SPARSE model with small matrix allocation size
      setSparseMatrixMaxDimensions(10);
      XGBoostModel firstSparseModel = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      Scope.track_generic(firstSparseModel);
      final Frame firstSparsePredictions = firstSparseModel.score(validationFrame);
      Scope.track(firstSparsePredictions);
      assertNotNull(firstSparsePredictions);


      // Second SPARSE model with large matrix allocation size
      setSparseMatrixMaxDimensions(Integer.MAX_VALUE - 10);
      XGBoostModel secondSparseModel = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      assertNotNull(secondSparseModel);
      Scope.track_generic(secondSparseModel);
      final Frame secondSparsePredictions = secondSparseModel.score(validationFrame);
      Scope.track(secondSparsePredictions);
      

      // Compare two sparse models to be the same
      assertNotEquals(firstSparsePredictions, secondSparsePredictions); // By no means should these point to the same object
      assertTrue(TestUtil.compareFrames(firstSparsePredictions, secondSparsePredictions));

      // DENSE model (dense matrices unaffected by matrix dimension settings)
      parms._dmatrix_type = XGBoostModel.XGBoostParameters.DMatrixType.dense;
      XGBoostModel denseModel = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      assertNotNull(denseModel);
      Scope.track_generic(denseModel);
      final Frame densePredictions = denseModel.score(validationFrame);
      Scope.track(densePredictions);

      // Compare dense and one of the sparse matrices (at this point, sparse matrices are guaranteed to be identical
      // due to the the previous check).
      assertTrue(TestUtil.compareFrames(densePredictions, secondSparsePredictions));
    } finally {
      Scope.exit();
    }
  }

}
