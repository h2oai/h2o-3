package hex.tree.xgboost;

import hex.DataInfo;
import hex.tree.xgboost.matrix.SparseMatrix;
import hex.tree.xgboost.matrix.SparseMatrixDimensions;
import hex.tree.xgboost.matrix.SparseMatrixFactory;
import hex.tree.xgboost.util.FeatureScore;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.Rabit;
import ml.dmlc.xgboost4j.java.XGBoostError;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.MRTask;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.util.VecUtils;

import java.io.*;
import java.net.URL;
import java.security.SecureRandom;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;

import static org.junit.Assert.*;

@Ignore("Parent for XGBoostUtilsTest, no actual tests here")
public class XGBoostUtilsTest extends TestUtil {

  protected static final int DEFAULT_SPARSE_MATRIX_SIZE = SparseMatrix.MAX_DIM;
  protected static final int MAX_ARR_SIZE = Integer.MAX_VALUE - 10;

  @BeforeClass
  public static void beforeClass(){
    TestUtil.stall_till_cloudsize(1);
  }

  @After
  public void tearDown() {
    revertDefaultSparseMatrixMaxSize();
  }

  public static final class XGBoostUtilsTestSingleRun extends XGBoostUtilsTest {

    @Test
    public void parseFeatureScores() throws IOException, ParseException {
      String[] modelDump = readLines(getClass().getResource("xgbdump.txt"));
      String[] expectedVarImps = readLines(getClass().getResource("xgbvarimps.txt"));

      Map<String, FeatureScore> scores = XGBoostUtils.parseFeatureScores(modelDump);
      double totalGain = 0;
      double totalCover = 0;
      double totalFrequency = 0;
      for (FeatureScore score : scores.values()) {
        totalGain += score._gain;
        totalCover += score._cover;
        totalFrequency += score._frequency;
      }

      NumberFormat nf = NumberFormat.getInstance(Locale.US);
      for (String varImp : expectedVarImps) {
        String[] vals = varImp.split(" ");
        FeatureScore score = scores.get(vals[0]);
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
    public void testSparsematrixNumLines() throws XGBoostError {

      Frame frame = null;
      try {
        frame = Scope.track(new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("C1", "C2", "C3")
                .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
                .withDataForCol(0, ard(0, 1, 0))
                .withDataForCol(1, ard(0, 2, 0))
                .withDataForCol(2, ard(0, 3, 0))
                .build());
        final DMatrix response = XGBoostUtils.convertFrameToDMatrix(
            new DataInfo(
                frame, null, true, DataInfo.TransformType.NONE, false, 
                false, false), 
            frame, "C3", null, null, true
        );
        assertNotNull(response);
        assertEquals(3, response.rowNum());
        assertArrayEquals(arf(0, 3, 0), response.getLabel(), 0f);
        

      } finally {
        if (frame != null) frame.remove();
      }
    }

    @Test
    public void testSparsematrixInit_emptyRowHandling() throws XGBoostError {

      Frame frame = null;
      try {
        frame = Scope.track(new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("C1", "C2", "C3")
                .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
                .withDataForCol(0, ard(0, 1, 0))
                .withDataForCol(1, ard(0, 2, 0))
                .withDataForCol(2, ard(0, 3, 0))
                .build());
        final String response = "C3";
        final Vec vec = frame.anyVec();
        final int[] chunksIds = VecUtils.getLocalChunkIds(frame.anyVec());
        float[] resp = new float[(int) vec.length()];
        final DataInfo di = new DataInfo(frame, null, true, DataInfo.TransformType.NONE, false, false, false);
        final int nrows = (int) vec.length();

        XGBoostUtilsTest.setSparseMatrixMaxDimensions(3);
        // Calculate sparse matrix dimensions
        final SparseMatrixDimensions sparseMatrixDimensions = SparseMatrixFactory.calculateCSRMatrixDimensions(frame, chunksIds, null, di);
        assertNotNull(sparseMatrixDimensions);
        assertEquals(3, sparseMatrixDimensions._nonZeroElementsCount);
        assertEquals(4, sparseMatrixDimensions._rowHeadersCount); // 3 rows + 1 final index

        // Allocate necessary memory blocks
        final SparseMatrix sparseMatrix = SparseMatrixFactory.allocateCSRMatrix(sparseMatrixDimensions);

        XGBoostUtilsTest.checkSparseDataStructuresAllocation(sparseMatrix, sparseMatrixDimensions._nonZeroElementsCount,
                nrows);

        // Initialize allocated matrices with actual data
        int actualRows = SparseMatrixFactory.initializeFromChunkIds(
                frame, chunksIds, null, null, di, sparseMatrix, sparseMatrixDimensions, 
                frame.vec(response), resp, null, null
        );

        assertEquals(3, actualRows);

        checkSparseDataInitialization(sparseMatrix, new float[]{1, 2, 3},
                new long[]{0, 0, 3, 3},// First row has zero NZEs, zero is also at the beginning of second row.  Stays 3 after second row.
                new int[]{0, 1, 2}); // All three cells have non-zero value occupied in second row


        final DMatrix dMatrix = new DMatrix(sparseMatrix._rowHeaders, sparseMatrix._colIndices, sparseMatrix._sparseData,
                DMatrix.SparseType.CSR, di.fullN(), actualRows + 1, sparseMatrixDimensions._nonZeroElementsCount);
        
        assertEquals(nrows, dMatrix.rowNum());
      } finally {
        if (frame != null) frame.remove();
      }
    }

    @Test
    public void testSparsematrixInit_identity() throws XGBoostError {

      Frame frame = null;
      try {
        frame = Scope.track(new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("C1", "C2", "C3")
                .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
                .withDataForCol(0, ard(1, 0, 0))
                .withDataForCol(1, ard(0, 1, 0))
                .withDataForCol(2, ard(0, 0, 1))
                .build());
        final String response = "C3";
        final Vec vec = frame.anyVec();
        final int[] chunksIds = VecUtils.getLocalChunkIds(frame.anyVec());
        float[] resp = new float[(int) vec.length()];
        final DataInfo di = new DataInfo(frame, null, true, DataInfo.TransformType.NONE, false, false, false);
        final int nrows = (int) vec.length();

        XGBoostUtilsTest.setSparseMatrixMaxDimensions(3);
        // Calculate sparse matrix dimensions
        final SparseMatrixDimensions sparseMatrixDimensions = SparseMatrixFactory.calculateCSRMatrixDimensions(frame, chunksIds, null, di);
        assertNotNull(sparseMatrixDimensions);
        assertEquals(3, sparseMatrixDimensions._nonZeroElementsCount);
        assertEquals(4, sparseMatrixDimensions._rowHeadersCount); // 3 rows + 1 final index

        // Allocate necessary memory blocks
        final SparseMatrix sparseMatrix = SparseMatrixFactory.allocateCSRMatrix(sparseMatrixDimensions);

        XGBoostUtilsTest.checkSparseDataStructuresAllocation(sparseMatrix, sparseMatrixDimensions._nonZeroElementsCount,
                nrows);

        // Initialize allocated matrices with actual data
        int actualRows = SparseMatrixFactory.initializeFromChunkIds(
            frame, chunksIds, null, null, di, sparseMatrix, sparseMatrixDimensions,
                frame.vec(response), resp, null, null);

        assertEquals(3, actualRows);

        checkSparseDataInitialization(sparseMatrix, new float[]{1, 1, 1},
                new long[]{0, 1, 2, 3},// One NZE per row
                new int[]{0, 1, 2}); // Identity matrix, one NZE per column 


        final DMatrix dMatrix = new DMatrix(sparseMatrix._rowHeaders, sparseMatrix._colIndices, sparseMatrix._sparseData,
                DMatrix.SparseType.CSR, di.fullN(), actualRows + 1, sparseMatrixDimensions._nonZeroElementsCount);

        assertEquals(nrows, dMatrix.rowNum());
      } finally {
        if (frame != null) frame.remove();
      }
    }

    /**
     * Tests if dimensions of internal array representation of the data are handled correctly
     */
    @Test
    public void testSparsematrixInit_dimensions_test() throws XGBoostError {

      Frame frame = null;
      try {
        frame = Scope.track(new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("C1", "C2", "C3")
                .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
                .withDataForCol(0, ard(10, 0, 0))
                .withDataForCol(1, ard(0, 20, 0))
                .withDataForCol(2, ard(0, 0, 30))
                .build());
        final String response = "C3";
        final Vec vec = frame.anyVec();
        final int[] chunksIds = VecUtils.getLocalChunkIds(frame.anyVec());
        float[] resp = new float[(int) vec.length()];
        final DataInfo di = new DataInfo(frame, null, true, DataInfo.TransformType.NONE, false, false, false);
        final int nrows = (int) vec.length();

        XGBoostUtilsTest.setSparseMatrixMaxDimensions(1); // 3 arrays in each direction for colIndices and data, 4 for rowHeaders

        // Calculate sparse matrix dimensions
        final SparseMatrixDimensions sparseMatrixDimensions = SparseMatrixFactory.calculateCSRMatrixDimensions(frame, chunksIds, null, di);
        assertNotNull(sparseMatrixDimensions);
        assertEquals(3, sparseMatrixDimensions._nonZeroElementsCount);
        assertEquals(4, sparseMatrixDimensions._rowHeadersCount); // 3 rows + 1 final index

        // Allocate necessary memory blocks
        final SparseMatrix sparseMatrix = SparseMatrixFactory.allocateCSRMatrix(sparseMatrixDimensions);

        XGBoostUtilsTest.checkSparseDataStructuresAllocation(sparseMatrix, sparseMatrixDimensions._nonZeroElementsCount,
                nrows);

        // Initialize allocated matrices with actual data
        int actualRows = SparseMatrixFactory.initializeFromChunkIds(
            frame, chunksIds, null, null, di, sparseMatrix, sparseMatrixDimensions,
            frame.vec(response), resp, null, null);

        assertEquals(3, actualRows);

        checkSparseDataInitialization(sparseMatrix, new float[]{10, 20, 30},
                new long[]{0, 1, 2, 3},
                new int[]{0, 1, 2});


        final DMatrix dMatrix = new DMatrix(sparseMatrix._rowHeaders, sparseMatrix._colIndices, sparseMatrix._sparseData,
                DMatrix.SparseType.CSR, di.fullN(), actualRows + 1, sparseMatrixDimensions._nonZeroElementsCount);

        assertEquals(nrows, dMatrix.rowNum());
      } finally {
        if (frame != null) frame.remove();
      }
    }

    /**
     * Tests if dimensions of internal array representation of the data are handled correctly
     */
    @Test
    public void testSparsematrixInit_categoricals_2D() throws XGBoostError {

      Frame frame = null;
      try {
        frame = Scope.track(new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("C1", "C2", "C3")
                .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
                .withDataForCol(0, ard(10, 0, 0))
                .withDataForCol(1, ar("a", "b", "c"))
                .withDataForCol(2, ard(0, 0, 30))
                .build());
        final String response = "C3";
        final Vec vec = frame.anyVec();
        final int[] chunksIds = VecUtils.getLocalChunkIds(frame.anyVec());
        float[] resp = new float[(int) vec.length()];
        final DataInfo di = new DataInfo(frame, null, true, DataInfo.TransformType.NONE, false, false, false);
        final int nrows = (int) vec.length();

        // Force the internal representation to utilize both dimensions
        XGBoostUtilsTest.setSparseMatrixMaxDimensions(1);

        // Calculate sparse matrix dimensions
        final SparseMatrixDimensions sparseMatrixDimensions = SparseMatrixFactory.calculateCSRMatrixDimensions(frame, chunksIds, null, di);
        assertNotNull(sparseMatrixDimensions);
        assertEquals(5, sparseMatrixDimensions._nonZeroElementsCount);
        assertEquals(4, sparseMatrixDimensions._rowHeadersCount); // 3 rows + 1 final index

        // Allocate necessary memory blocks
        final SparseMatrix sparseMatrix = SparseMatrixFactory.allocateCSRMatrix(sparseMatrixDimensions);

        XGBoostUtilsTest.checkSparseDataStructuresAllocation(sparseMatrix, sparseMatrixDimensions._nonZeroElementsCount,
                nrows);

        // Initialize allocated matrices with actual data
        int actualRows = SparseMatrixFactory.initializeFromChunkIds(
            frame, chunksIds, null, null, di, sparseMatrix, sparseMatrixDimensions,
            frame.vec(response), resp, null, null);

        assertEquals(3, actualRows);

        // Categoricals are always handled first for given row, thus are always before numerical values
        checkSparseDataInitialization(sparseMatrix, new float[]{1, 10, 1, 1, 30}, // One-hot encoding
                new long[]{0, 2, 3, 5},
                new int[]{0, 3, 1, 2, 4}); // One-hot encoding creates 3 more columns in internal representation


        final DMatrix dMatrix = new DMatrix(sparseMatrix._rowHeaders, sparseMatrix._colIndices, sparseMatrix._sparseData,
                DMatrix.SparseType.CSR, di.fullN(), actualRows + 1, sparseMatrixDimensions._nonZeroElementsCount);

        assertEquals(nrows, dMatrix.rowNum());
      } finally {
        if (frame != null) frame.remove();
      }
    }
  }

  /**
   * Checks size allocations of sparse data
   *
   * @param sparseMatrix    Sparse matrix data structures, including preallocated two-dimensional array prepared to be filled
   *                        with non-zero elements
   * @param nonZeroElements Number of non-zero elements found in original non-compressed matrix
   * @param originalMatrixrows Number of rows in the original matrix
   */
  private static void checkSparseDataStructuresAllocation(final SparseMatrix sparseMatrix, final long nonZeroElements,
                                                          final int originalMatrixrows) {
    assertNotNull(sparseMatrix);
    assertNotNull(sparseMatrix._colIndices);
    assertNotNull(sparseMatrix._rowHeaders);
    
    final float[][] data = sparseMatrix._sparseData;
    final int[][] colIndices = sparseMatrix._colIndices;
    assertNotNull(data);

    long expectArrNumrows = nonZeroElements / SparseMatrix.MAX_DIM;
    if (nonZeroElements % SparseMatrix.MAX_DIM != 0) expectArrNumrows++;

    assertEquals(expectArrNumrows, data.length);

    long expectedArrRowSize = Math.min(MAX_ARR_SIZE, nonZeroElements);
    expectedArrRowSize = Math.min(expectedArrRowSize, SparseMatrix.MAX_DIM);


    for (int i = 0; i < data.length - 1; i++) { // Last row might be of different size
      assertEquals(expectedArrRowSize, data[i].length);
      assertEquals(expectedArrRowSize, colIndices[i].length); // Number of column indices equals the number of NZE
    }

    final long expectedLastArrRowSize = nonZeroElements % SparseMatrix.MAX_DIM;

    if (expectedLastArrRowSize == 0) { // Is last row differently sized ?
      assertEquals(expectedArrRowSize, data[data.length - 1].length);
      assertEquals(expectedArrRowSize, colIndices[colIndices.length - 1].length);
    } else {
      assertEquals(expectedLastArrRowSize, data[data.length - 1].length);
      assertEquals(expectedLastArrRowSize, colIndices[colIndices.length - 1].length);
    }
    
    long numRowHeaders = 0;
    for (int i = 0; i < sparseMatrix._rowHeaders.length; i++) {
      numRowHeaders += sparseMatrix._rowHeaders[i].length;
    }
    
    assertEquals(originalMatrixrows + 1, numRowHeaders);
  }

  private static void checkSparseDataInitialization(final SparseMatrix sparseMatrix,
                                                    final float[] expectedNZEs, final long[] expectedRowHeaders,
                                                    final int[] expectedColIndices) {

    // Check expected non-zero elements are in the resulting array structures
    final float[][] data = sparseMatrix._sparseData;
    int nzePointer = 0;
    for (int i = 0; i < data.length; i++) {
      for (int j = 0; j < data[i].length; j++) {
        assertEquals(expectedNZEs[nzePointer++], data[i][j], 0d);
      }
    }

    // Check expected row headers are properly filled in the matrix
    final long[][] rowHeaders = sparseMatrix._rowHeaders;
    int rowHeadersPtr = 0;
    for (int i = 0; i < rowHeaders.length; i++) {
      for (int j = 0; j < rowHeaders[i].length; j++) {
        assertEquals(expectedRowHeaders[rowHeadersPtr++], rowHeaders[i][j], 0d);
      }
    }

    final int[][] colIndices = sparseMatrix._colIndices;
    int colIdxPtr = 0;

    for (int i = 0; i < colIndices.length; i++) {
      for (int j = 0; j < colIndices[i].length; j++) {
        assertEquals(expectedColIndices[colIdxPtr++], colIndices[i][j], 0d);

      }
    }

  }


  private static String[] readLines(URL url) throws IOException {
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
      SparseMatrix.MAX_DIM = _maxMatrixDimension;
    }
  }


  /**
   * @param dim Dimension of the sparse square matrix.
   */
  public static void setSparseMatrixMaxDimensions(final int dim) {
    new XGBSparseMatrixDimTask(dim)
            .doAllNodes();
  }

  /**
   * Reverts sparse matrix maximum size back to the default collected during initiation of this test suite
   */
  public static void revertDefaultSparseMatrixMaxSize() {
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


  protected static float[] createRandomLabelCol(final int colLen) {
    float[] label = new float[colLen];
    final Random random = new SecureRandom();

    for (int i = 0; i < label.length; i++) {
      label[i] = (float) random.nextGaussian();
    }


    return label;
  }

  protected static void attachLabelToFrame(final Frame frame, final float[] values) {
    final Vec vec = Vec.makeVec(values, frame.anyVec().group().addVec());
    frame.add("response", vec);
  }

  protected static Matrices createIdentityMatrices(final int dim, final int maxArrLen, final int chunkLen) throws XGBoostError {
    long[][] rowHeaders = createLayout(dim + 1, maxArrLen).allocateLong();
    int[][] colIndices = createLayout(dim, maxArrLen).allocateInt();
    float[][] values = createLayout(dim, maxArrLen).allocateFloat();
    
    assertTrue("Indentity matrix dimension must be divisible by chunkLen without remainder", dim % chunkLen == 0);
    
    long[] chunkLayout = new long[dim / chunkLen];
    for (int i = 0; i < dim / chunkLen; i++) {
      chunkLayout[i] = chunkLen;
    }

    TestFrameBuilder testFrameBuilder = new TestFrameBuilder()
            .withUniformVecTypes(dim, Vec.T_NUM)
            .withChunkLayout(chunkLayout);

    long pos = 0;
    for (int m = 0; m < dim; m++) {
      int arr_idx = (int) (pos / maxArrLen);
      int arr_pos = (int) (pos % maxArrLen);

      testFrameBuilder = testFrameBuilder.withDataForCol(m, genIdentityMatrixFrameCol(m, dim));
      values[arr_idx][arr_pos] = 1;
      colIndices[arr_idx][arr_pos] = m;
      rowHeaders[arr_idx][arr_pos] = pos;
      pos++;
    }
    int arr_idx = (int) (pos / maxArrLen);
    int arr_pos = (int) (pos % maxArrLen);
    rowHeaders[arr_idx][arr_pos] = pos;
    assertEquals(dim, pos);

    final DMatrix dMatrix = new DMatrix(rowHeaders, colIndices, values, DMatrix.SparseType.CSR, dim, dim + 1, dim);

    return new Matrices(dMatrix, testFrameBuilder.build());
  }

  private static double[] genIdentityMatrixFrameCol(final int colIdx, final int len) {
    final double[] column = new double[len];
    column[colIdx] = 1;
    return column;
  }


  protected static class Matrices {
    private final DMatrix _dmatrix;
    private final Frame _h2oFrame;

    public Matrices(DMatrix dmatrix, Frame h2oFrame) {
      _dmatrix = dmatrix;
      _h2oFrame = h2oFrame;
    }
  }

  private static CsrLayout createLayout(long size, int maxArrLen) {
    CsrLayout l = new CsrLayout();
    l._numRegRows = (int) (size / maxArrLen);
    l._regRowLen = maxArrLen;
    l._lastRowLen = (int) (size - ((long) l._numRegRows * l._regRowLen)); // allow empty last row (easier and it shouldn't matter)
    return l;
  }

  private static class CsrLayout {
    int _numRegRows;
    int _regRowLen;
    int _lastRowLen;

    long[][] allocateLong() {
      long[][] result = new long[_numRegRows + 1][];
      for (int i = 0; i < _numRegRows; i++) {
        result[i] = new long[_regRowLen];
      }
      result[result.length - 1] = new long[_lastRowLen];
      return result;
    }

    int[][] allocateInt() {
      int[][] result = new int[_numRegRows + 1][];
      for (int i = 0; i < _numRegRows; i++) {
        result[i] = new int[_regRowLen];
      }
      result[result.length - 1] = new int[_lastRowLen];
      return result;
    }

    float[][] allocateFloat() {
      float[][] result = new float[_numRegRows + 1][];
      for (int i = 0; i < _numRegRows; i++) {
        result[i] = new float[_regRowLen];
      }
      result[result.length - 1] = new float[_lastRowLen];
      return result;
    }

  }

  /**
   * Compares H2O XGBoost preds with native preds
   *
   * @param nativePreds Native predictions
   * @param h2oPreds    H2O-provided predictions
   * @param delta
   */
  private static void comparePreds(final float[][] nativePreds, final Vec h2oPreds, final float delta) {
    if (nativePreds.length != h2oPreds.length()) {
      throw new IllegalStateException(String.format("Predictions do not have the same length. Native: %x, H2O: %x",
              nativePreds.length,
              h2oPreds.length()));

    }
    for (int i = 0; i < nativePreds.length; i++) {
      assertEquals(nativePreds[i][0], (float) h2oPreds.at(i), delta);
    }
  }


  @RunWith(Parameterized.class)
  public static final class XGBoostSparseMatrixTest extends XGBoostUtilsTest {

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
      return Arrays.asList(new Object[][]{
              {30, 10, MAX_ARR_SIZE, 30},
              {30, 10, 10, 30},
              {30, 10, MAX_ARR_SIZE, 10}, // 3 chunks per vec
              {30, 10, 10, 10}, // 3 chunks per vec
              {30, MAX_ARR_SIZE, MAX_ARR_SIZE, 30},
              {300, 10, MAX_ARR_SIZE, 300},
              {300, 10, 10, 300},
              {300, 10, 10, 10}, // 30 chunks per vec
              {300, MAX_ARR_SIZE, MAX_ARR_SIZE, 300},
              {300, MAX_ARR_SIZE, MAX_ARR_SIZE, 10}, // 30 chunks per vec
              {1000, 10, MAX_ARR_SIZE, 1000},
              {1000, 10, MAX_ARR_SIZE, 100}, // 100 chunks per vec
              {1000, 10, 10, 1000},
              {1000, MAX_ARR_SIZE, MAX_ARR_SIZE, 1000}
      });
    }

    @Parameterized.Parameter(0)
    public int matrixDimension;
    @Parameterized.Parameter(1)
    public int maxArrayLen; // Maximum length of second dimension of arrays used in XGBoostUtils to represent sparse data
    @Parameterized.Parameter(2)
    public int maxNativeArrayLen; //Maximum length of the second dimension of arrays used to hand over sparse data to "native" XGBoost4J
    @Parameterized.Parameter(3)
    public int chunkLen;

    @Test
    public void testCSRPredictions_compare_with_native() throws XGBoostError {

      Booster booster = null;
      try {
        Scope.enter();
        Map<String, String> rabitEnv = new HashMap<>();
        rabitEnv.put("DMLC_TASK_ID", "0");
        Rabit.init(rabitEnv);

        // Prepare data matrices & label
        final Matrices matrices = createIdentityMatrices(matrixDimension, Integer.MAX_VALUE - 10, chunkLen);
        Frame trainingFrame = matrices._h2oFrame;
        Scope.track(trainingFrame);
        final DMatrix train = matrices._dmatrix;
        float[] label = createRandomLabelCol(matrixDimension);
        train.setLabel(label);
        attachLabelToFrame(matrices._h2oFrame, label);

        // Train native XGBoost model via XGBoost4J
        final int nround = 5;
        final Map<String, Object> nativeParms = new HashMap<String, Object>() {
          {
            put("objective", "reg:linear");
            put("eta", 1.0); //ETA 1.0 to make any differences in training matrices instantly noticeable
            put("max_depth", 16);
            put("ntrees", 5);
            put("colsample_bytree", 1.0);
            put("tree_method", "exact");
            put("backend", "cpu");
            put("booster", "gbtree");
            put("lambda", 1.0);
            put("grow_policy", "depthwise");
            put("nthread", 12);
            put("subsample", 1.0);
            put("colsample_bylevel", 1.0);
            put("max_delta_step", 0.0);
            put("min_child_weight", 1.0);
            put("gamma", 0.0);
            put("seed", 1);
          }
        };

        Map<String, DMatrix> watches = new HashMap<String, DMatrix>() {
          {
            put("train", train);
          }
        };


        booster = ml.dmlc.xgboost4j.java.XGBoost.train(train, nativeParms, nround, watches, null, null);
        assertNotNull(booster);
        final float[][] predict = booster.predict(train);
        assertNotNull(predict);


        // Train H2O XGBoostModel
        setSparseMatrixMaxDimensions(maxArrayLen); // Force the internal representation of the matrix to use both dimension of the array
        XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
        parms._ntrees = 5;
        parms._eta = 1.0; //ETA 1.0 to make any differences in training matrices instantly noticeable
        parms._max_depth = 16;
        parms._stopping_rounds = nround;
        parms._train = matrices._h2oFrame._key;
        parms._response_column = "response";
        parms._backend = XGBoostModel.XGBoostParameters.Backend.cpu;
        parms._tree_method = XGBoostModel.XGBoostParameters.TreeMethod.exact;
        parms._seed = 1;
        XGBoostModel model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
        assertNotNull(model);
        Scope.track_generic(model);

        Frame h2oPreds = model.score(trainingFrame);
        Scope.track(h2oPreds);

        comparePreds(predict, h2oPreds.vec("predict"), 1e-6f);


      } finally {
        Scope.exit();
        Rabit.shutdown();
        booster.dispose();
      }
    }

  }


}
