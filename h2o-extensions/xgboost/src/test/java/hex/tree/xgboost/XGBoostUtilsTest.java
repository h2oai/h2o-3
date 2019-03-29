package hex.tree.xgboost;

import hex.ModelMetricsMultinomial;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.MRTask;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;

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

public class XGBoostUtilsTest {

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
  public void testSparseMatrixPopulation() {
    setSparseMatrixMaxDimensions(10);

    Scope.enter();
    try {
      final String response = "cylinders";

      Frame f = TestUtil.parse_test_file("smalldata/junit/cars.csv");
      f.replace(f.find(response), f.vecs()[f.find(response)].toCategoricalVec()).remove();
      DKV.put(Scope.track(f));

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._dmatrix_type = XGBoostModel.XGBoostParameters.DMatrixType.auto;
      parms._response_column = response;
      parms._train = f._key;
      parms._dmatrix_type = XGBoostModel.XGBoostParameters.DMatrixType.sparse;
      parms._ignored_columns = new String[]{"name"};

      XGBoostModel model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      Scope.track_generic(model);
    } finally {
      Scope.exit();
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
    }
  }

}
