package hex.genmodel.algos.gbm;

import com.google.common.io.ByteStreams;
import hex.genmodel.ModelMojoReader;
import hex.genmodel.MojoReaderBackend;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.exception.PredictException;
import hex.genmodel.easy.prediction.BinomialModelPrediction;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static org.junit.Assert.*;

public class GbmMojoModelTest {

  private GbmMojoModel mojo12;

  @Before
  public void setup() throws Exception {
    mojo12 = (GbmMojoModel) ModelMojoReader.readFrom(new ClasspathReaderBackend());
    assertNotNull(mojo12);
  }

  @Test
  public void testScore0() throws Exception {
    double[] row = {18.7, 1.51, 1.003, 132.53, 1.15, 0.2, 1.153, 8.3, 0.34, 0.0, 0.0};
    double[] preds = mojo12.score0(row, new double[3]);
    assertArrayEquals(new double[]{1, 0.5416688, 0.4583312}, preds, 1e-5);
  }

  @Test
  public void scoreSingleTree() throws Exception {
    double[] row = {18.7, 1.51, 1.003, 132.53, 1.15, 0.2, 1.153, 8.3, 0.34, 0.0, 0.0};
    for (int tree = 0; tree < 10; tree++) {
      // Score single tree layer explicitly
      double[] singleTreePreds = new double[3];
      mojo12.scoreSingleTree(row, tree, singleTreePreds);
      // Score single tree layer using the range API
      double[] rangeTreePreds = new double[3];
      mojo12.scoreTreeRange(row, tree, tree + 1, rangeTreePreds);
      assertArrayEquals(rangeTreePreds, singleTreePreds, 0);
    }
  }

  @Test
  public void testScoreTreeRange() throws Exception {
    double[] row = {18.7, 1.51, 1.003, 132.53, 1.15, 0.2, 1.153, 8.3, 0.34, 0.0, 0.0};
    double[] preds = new double[3];
    for (int tree = 0; tree < 10; tree++) {
      double[] singleTreePreds = new double[preds.length];
      // Score individually each layer of trees (one tree per class, for all classes)
      mojo12.scoreTreeRange(row, tree, tree + 1, singleTreePreds);
      // Manually accumulate the predictions
      for (int i = 0; i < preds.length; i++)
        preds[i] += singleTreePreds[i];
    }
    // Generate final predictions from the partial predictions
    mojo12.unifyPreds(row, 0, preds);
    double[] expectedPreds = mojo12.score0(row, new double[preds.length]);
    // Manually calculated predictions should be the same as the output from score0
    assertArrayEquals(expectedPreds, preds, 1e-8);
  }

  @Test
  public void testPredict() throws Exception {
    EasyPredictModelWrapper wrapper = new EasyPredictModelWrapper(mojo12);

    BinomialModelPrediction pred = (BinomialModelPrediction) wrapper.predict(new RowData() {{
      put("SegSumT", 18.7);
      put("SegTSeas", 1.51);
      put("SegLowFlow", 1.003);
      put("DSDist", 132.53);
      put("DSMaxSlope", 1.15);
      put("USAvgT", 0.2);
      put("USRainDays", 1.153);
      put("USSlope", 8.3);
      put("USNative", 0.34);
      put("DSDam", 0.0);
      put("Method", "electric");
    }});

    assertEquals(1, pred.labelIndex);
    assertEquals("1", pred.label);
    assertArrayEquals(new double[]{0.5416688, 0.4583312}, pred.classProbabilities, 1e-5);
    assertArrayEquals(new double[]{0.3920402, 0.6079598}, pred.calibratedClassProbabilities, 1e-5);
  }

  @Test
  public void testPredictWithLeafAssignments() throws IOException, PredictException {
    EasyPredictModelWrapper wrapper = new EasyPredictModelWrapper(
            new EasyPredictModelWrapper.Config().setModel(mojo12).setEnableLeafAssignment(true)
    );
    BinomialModelPrediction p = wrapper.predictBinomial(new RowData());
    assertNull(p.leafNodeAssignmentIds); // MOJO 1.2 doesn't support Node Id assignment
    assertArrayEquals(
            new String[]{"LRLR", "LRLR", "LRLR", "LRLR", "LRLR", "RLLRR", "RLLRL", "RLLLL", "LRLRL", "RRLRL"},
            p.leafNodeAssignments
    );
  }

  private static class ClasspathReaderBackend implements MojoReaderBackend {
    @Override
    public BufferedReader getTextFile(String filename) throws IOException {
      InputStream is = GbmMojoModelTest.class.getResourceAsStream("calibrated/" + filename);
      return new BufferedReader(new InputStreamReader(is));
    }

    @Override
    public byte[] getBinaryFile(String filename) throws IOException {
      InputStream is = GbmMojoModelTest.class.getResourceAsStream("calibrated/" + filename);
      return ByteStreams.toByteArray(is);
    }

    @Override
    public boolean exists(String name) {
      return true;
    }
  }

}