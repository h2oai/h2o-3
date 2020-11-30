package hex.deeplearning;

import hex.deeplearning.DeepLearningModel.DeepLearningParameters;
import hex.genmodel.algos.deeplearning.DeeplearningMojoModel;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.exception.PredictException;
import hex.genmodel.easy.prediction.AutoEncoderModelPrediction;
import hex.quantile.Quantile;
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

    Frame train=null;

    try {
      NFSFileVec nfs = TestUtil.makeNfsFileVec(PATH);
      train = ParseDataset.parse(Key.make("train.hex"), nfs._key);

      DeepLearningParameters p = new DeepLearningParameters();
      p._train = train._key;
      p._autoencoder = true;
      p._response_column = train.names()[train.names().length - 1];
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

      Frame recon_train = null, l2 = null, df1 = null, df2 = null, df3 = null;
      DeepLearningModel mymodel = null;
      StringBuilder sb = new StringBuilder();

      try {
        DeepLearning dl = new DeepLearning(p);
        mymodel = dl.trainModel().get();

        // Verification of results
        sb.append("Verifying results.\n");
        sb.append("Reported mean reconstruction error: " + mymodel.mse() + "\n");

        // Training data
        // Reconstruct data using the same helper functions and verify that self-reported MSE agrees
        final Frame rec = mymodel.scoreAutoEncoder(train, Key.make(), true);
        sb.append("Reconstruction error per feature: " + rec.toString() + "\n");
        rec.remove();

        l2 = mymodel.scoreAutoEncoder(train, Key.make(), false);
        final Vec l2vec = l2.anyVec();
        sb.append("Actual   mean reconstruction error: " + l2vec.mean() + "\n");

        // print stats and potential outliers
        double quantile = 1 - 5. / train.numRows();
        sb.append("The following training points are reconstructed with an error above the "
                + quantile * 100 + "-th percentile - potential \"outliers\" in testing data.\n");
        double thresh = Quantile.calcQuantile(l2vec, quantile);
        for (long i = 0; i < l2vec.length(); i++) {
          if (l2vec.at(i) > thresh) {
            sb.append(String.format("row %d : l2vec error = %5f\n", i, l2vec.at(i)));
          }
        }
        Log.info(sb.toString());

        Assert.assertEquals(l2vec.mean(), mymodel.mse(), 1e-8 * mymodel.mse());

        // Create reconstruction
        Log.info("Creating full reconstruction.");
        recon_train = mymodel.score(train);
        Assert.assertTrue(mymodel.testJavaScoring(train, recon_train, 1e-5));

        df1 = mymodel.scoreDeepFeatures(train, 0);
        Assert.assertEquals(10, df1.numCols());
        Assert.assertEquals(train.numRows(), df1.numRows());

        df2 = mymodel.scoreDeepFeatures(train, 1);
        Assert.assertEquals(5, df2.numCols());
        Assert.assertEquals(train.numRows(), df2.numRows());

        df3 = mymodel.scoreDeepFeatures(train, 2);
        Assert.assertEquals(3, df3.numCols());
        Assert.assertEquals(train.numRows(), df3.numRows());

        // check if reconstruction error is the same from model and mojo model too. Testcase for PUBDEV-6030.
        try {
          DeeplearningMojoModel mojoModel = (DeeplearningMojoModel) mymodel.toMojo();
          EasyPredictModelWrapper model = new EasyPredictModelWrapper(mojoModel);
          AutoEncoderModelPrediction tmpPrediction;
          double calcNormMse = 0;
          for (int r = 0; r < train.numRows(); r++) {
            RowData tmpRow = new RowData();
            BufferedString bStr = new BufferedString();
            for (int c = 0; c < train.numCols(); c++) {
              if (train.vec(c).isCategorical()) {
                tmpRow.put(train.names()[c], train.vec(c).atStr(bStr, r).toString());
              } else {
                tmpRow.put(train.names()[c], train.vec(c).at(r));
              }
            }
            tmpPrediction = model.predictAutoEncoder(tmpRow);
            calcNormMse += tmpPrediction.mse;
          }
          double mojoMeanError = calcNormMse / train.numRows();
          sb.append("Mojo mean reconstruction error (train): ").append(mojoMeanError).append("\n");
          sb.append("Mean reconstruction error should be the same from model compare to mojo model " +
                  "reconstruction error: ");
          sb.append(mymodel.mse()).append(" == ").append(mojoMeanError).append("\n");
          Assert.assertEquals(mymodel.mse(), mojoMeanError, 1e-7);
        } catch (IOException error) {
          Assert.fail(error.getStackTrace().toString());
        } catch (PredictException error) {
          Assert.fail(error.getStackTrace().toString());
        }
      } finally {
        Log.info(sb);
        if(recon_train != null) recon_train.delete();
        if(l2 != null) l2.delete();
        if(mymodel != null) mymodel.delete();
        if(df1 != null) df1.delete();
        if(df2 != null) df2.delete();
        if(df3 != null) df3.delete();
      }
    } finally {
      if(train != null)train.delete();
    }
  }
}

