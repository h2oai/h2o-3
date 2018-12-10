package hex.deeplearning;

import hex.deeplearning.DeepLearningModel.DeepLearningParameters;
import hex.genmodel.algos.deeplearning.DeeplearningMojoModel;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.exception.PredictException;
import hex.genmodel.easy.prediction.AutoEncoderModelPrediction;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.parser.ParseDataset;
import water.util.Log;

import java.io.IOException;

public class DeepLearningAutoEncoderCategoricalTest extends TestUtil {
  static final String PATH = "smalldata/airlines/AirlinesTrain.csv.zip";

  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void run() {
    long seed = 0xDECAF;

    NFSFileVec  nfs = TestUtil.makeNfsFileVec(PATH);
    Frame train = ParseDataset.parse(Key.make("train.hex"), nfs._key);

    DeepLearningParameters p = new DeepLearningParameters();
    p._train = train._key;
    p._autoencoder = true;
    p._response_column = train.names()[train.names().length-1];
    p._seed = seed;
    p._hidden = new int[]{10, 5, 3};
    p._adaptive_rate = true;
//    String[] n = train.names();
//    p._ignored_columns = new String[]{n[0],n[1],n[2],n[3],n[6],n[7],n[8],n[10]}; //Optional: ignore all categoricals
//    p._ignored_columns = new String[]{train.names()[4], train.names()[5], train.names()[9]}; //Optional: ignore all numericals
    p._l1 = 1e-4;
    p._activation = DeepLearningParameters.Activation.Tanh;
    p._max_w2 = 10;
    p._train_samples_per_iteration = -1;
    p._loss = DeepLearningParameters.Loss.Huber;
    p._epochs = 0.2;
    p._force_load_balance = true;
    p._score_training_samples = 0;
    p._score_validation_samples = 0;
    p._reproducible = true;

    DeepLearning dl = new DeepLearning(p);
    DeepLearningModel mymodel = dl.trainModel().get();

    // Verification of results
    StringBuilder sb = new StringBuilder();
    sb.append("Verifying results.\n");
    sb.append("Reported mean reconstruction error: " + mymodel.mse() + "\n");

    // Training data
    // Reconstruct data using the same helper functions and verify that self-reported MSE agrees
    final Frame rec = mymodel.scoreAutoEncoder(train, Key.make(), true);
    sb.append("Reconstruction error per feature: " + rec.toString() + "\n");
    rec.remove();

    final Frame l2 = mymodel.scoreAutoEncoder(train, Key.make(), false);
    final Vec l2vec = l2.anyVec();
    sb.append("Actual   mean reconstruction error: " + l2vec.mean() + "\n");

    // print stats and potential outliers
    double quantile = 1 - 5. / train.numRows();
    sb.append("The following training points are reconstructed with an error above the "
            + quantile * 100 + "-th percentile - potential \"outliers\" in testing data.\n");
    double thresh = mymodel.calcOutlierThreshold(l2vec, quantile);
    for (long i = 0; i < l2vec.length(); i++) {
      if (l2vec.at(i) > thresh) {
        sb.append(String.format("row %d : l2vec error = %5f\n", i, l2vec.at(i)));
      }
    }
    Log.info(sb.toString());

    Assert.assertEquals(l2vec.mean(), mymodel.mse(), 1e-8*mymodel.mse());

    // Create reconstruction
    Log.info("Creating full reconstruction.");
    final Frame recon_train = mymodel.score(train);
    Assert.assertTrue(mymodel.testJavaScoring(train,recon_train,1e-5));

    Frame df1 = mymodel.scoreDeepFeatures(train, 0);
    Assert.assertTrue(df1.numCols() == 10);
    Assert.assertTrue(df1.numRows() == train.numRows());
    df1.delete();

    Frame df2 = mymodel.scoreDeepFeatures(train, 1);
    Assert.assertTrue(df2.numCols() == 5);
    Assert.assertTrue(df2.numRows() == train.numRows());
    df2.delete();

    Frame df3 = mymodel.scoreDeepFeatures(train, 2);
    Assert.assertTrue(df3.numCols() == 3);
    Assert.assertTrue(df3.numRows() == train.numRows());
    df3.delete();

    // check if reconstruction error is the same from model and mojo model too. Testcase for PUBDEV-6030.
    try {
      DeeplearningMojoModel mojoModel = (DeeplearningMojoModel) mymodel.toMojo();
      EasyPredictModelWrapper model = new EasyPredictModelWrapper(mojoModel);
      AutoEncoderModelPrediction tmpPrediction;
      RowData tmpRow;
      BufferedString bStr;
      double calcNormMse = 0;
      for (int r = 0; r < train.numRows(); r++) {
        tmpRow = new RowData();
        bStr = new BufferedString();
        for (int c = 0; c < train.numCols(); c++) {
          if (train.vec(c).isCategorical()) {
            tmpRow.put(train.names()[c], train.vec(c).atStr(bStr, r).toString());
          } else {
            tmpRow.put(train.names()[c],  train.vec(c).at(r));
          }
        }
        tmpPrediction = model.predictAutoEncoder(tmpRow);
        calcNormMse += tmpPrediction.mse;
      }
      double mojoMeanError = calcNormMse/train.numRows();
      sb.append("Mojo mean reconstruction error (train): ").append(mojoMeanError).append("\n");
      sb.append("Mean reconstruction error should be the same from model compare to mojo model " +
              "reconstruction error: ");
      sb.append(mymodel.mse()).append(" == ").append(mojoMeanError).append("\n");
      Assert.assertEquals(mymodel.mse(), mojoMeanError, 1e-7);

    } catch (IOException error) {
      Assert.fail(error.getStackTrace().toString());
    } catch (PredictException error){
      Assert.fail(error.getStackTrace().toString());
    } finally {
      // cleanup
      Log.info(sb);
      recon_train.delete();
      train.delete();
      mymodel.delete();
      l2.delete();
    }
  }
}

