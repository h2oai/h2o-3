package hex.deeplearning;

import hex.ScoreKeeper;
import hex.genmodel.algos.deeplearning.DeeplearningMojoModel;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.exception.PredictException;
import hex.genmodel.easy.prediction.AutoEncoderModelPrediction;
import hex.quantile.Quantile;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.*;
import water.parser.BufferedString;
import water.parser.ParseDataset;
import water.util.Log;
import hex.deeplearning.DeepLearningModel.DeepLearningParameters;
import hex.genmodel.easy.EasyPredictModelWrapper;

import java.io.IOException;
import java.util.HashSet;

public class DeepLearningAutoEncoderTest extends TestUtil {
  /*
    Visualize outliers with the following R code (from smalldata/anomaly dir):

    train <- scan("ecg_discord_train.csv", sep=",")
    test  <- scan("ecg_discord_test.csv",  sep=",")
    plot.ts(train)
    plot.ts(test)
  */

  static final String PATH = "smalldata/anomaly/ecg_discord_train.csv"; //first 20 points
  static final String PATH2 = "smalldata/anomaly/ecg_discord_test.csv"; //first 22 points

  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void run() {
    long seed = 0xDECAF;

    Frame train=null, test=null;

    try {

      NFSFileVec  nfs = TestUtil.makeNfsFileVec(PATH);
      train = ParseDataset.parse(Key.make("train.hex"), nfs._key);
      NFSFileVec  nfs2 = TestUtil.makeNfsFileVec(PATH2);
      test = ParseDataset.parse(Key.make("test.hex"), nfs2._key);

      for (float sparsity_beta : new float[]{0, 0.1f}) {
        DeepLearningParameters p = new DeepLearningParameters();
        p._train = train._key;
        p._valid = test._key;
        p._autoencoder = true;
        p._response_column = train.names()[train.names().length-1];
        p._seed = seed;
        p._hidden = new int[]{37, 12};
        p._adaptive_rate = true;
        p._train_samples_per_iteration = -1;
        p._sparsity_beta = sparsity_beta;
        p._average_activation = -0.7;
        p._l1 = 1e-4;
        p._activation = DeepLearningParameters.Activation.TanhWithDropout;
        p._loss = DeepLearningParameters.Loss.Absolute;
        p._epochs = 13.3;
        p._force_load_balance = true;
        p._elastic_averaging = false;

        // same parameters for the non-standardized model
        DeepLearningParameters pNoStand = (DeepLearningParameters) p.clone();
        pNoStand._standardize = false;

        // train default
        DeepLearning dl = new DeepLearning(p);
        DeepLearningModel mymodel = dl.trainModel().get();
        Assert.assertEquals(ScoreKeeper.StoppingMetric.MSE, p._stopping_metric); // AE early-stops on MSE

        // train non-standardized
        DeepLearning dlNoStand = new DeepLearning(pNoStand);
        DeepLearningModel mymodelNoStand = dlNoStand.trainModel().get();
        Assert.assertEquals(ScoreKeeper.StoppingMetric.MSE, pNoStand._stopping_metric);

        Frame l2_frame_train=null, l2_frame_test=null;

        // Verification of results
        StringBuilder sb = new StringBuilder();
        try {

          sb.append("Verifying results.\n");

          // Training data

          // Reconstruct data using the same helper functions and verify that self-reported MSE agrees
          double quantile = 0.95;

          l2_frame_test = mymodel.scoreAutoEncoder(test, Key.make(), true);
          sb.append("Reconstruction error per feature (test): ").append(l2_frame_test.toString()).append("\n");
          l2_frame_test.remove();

          l2_frame_test = mymodel.scoreAutoEncoder(test, Key.make(), false);
          Vec l2_test = l2_frame_test.anyVec();
          sb.append("Mean reconstruction error (test): ").append(l2_test.mean()).append("\n");
          Assert.assertEquals(l2_test.mean(), mymodel.mse(), 1e-7);
          Assert.assertTrue("too big a reconstruction error: " + l2_test.mean(), l2_test.mean() < 2.0);
          l2_test.remove();

          // manually compute L2
          Frame reconstr = mymodel.score(train); //this creates real values in original space
          Assert.assertTrue(mymodel.testJavaScoring(train,reconstr,1e-6));

          l2_frame_train = mymodel.scoreAutoEncoder(train, Key.make(), false);
          final Vec l2_train = l2_frame_train.anyVec();
          double mean_l2 = 0;
          for (int r = 0; r < reconstr.numRows(); ++r) {
            double my_l2 = 0;
            for (int c = 0; c < reconstr.numCols(); ++c) {
              my_l2 += Math.pow((reconstr.vec(c).at(r) - train.vec(c).at(r)) * mymodel.model_info().data_info()._normMul[c], 2); //undo normalization here
            }
            my_l2 /= reconstr.numCols();
            mean_l2 += my_l2;
          }
          mean_l2 /= reconstr.numRows();
          reconstr.delete();
          sb.append("Mean reconstruction error (train): ").append(l2_train.mean()).append("\n");
          Assert.assertEquals(mymodel._output.errors.scored_train._mse, mean_l2, 1e-7);

          // print stats and potential outliers
          sb.append("The following training points are reconstructed with an error above the ").append(quantile * 100).append("-th percentile - check for \"goodness\" of training data.\n");
          double thresh_train = Quantile.calcQuantile(l2_train, quantile);
          for (long i = 0; i < l2_train.length(); i++) {
            if (l2_train.at(i) > thresh_train) {
              sb.append(String.format("row %d : l2_train error = %5f\n", i, l2_train.at(i)));
            }
          }

          // Test data

          // Reconstruct data using the same helper functions and verify that self-reported MSE agrees
          l2_frame_test.remove();
          l2_frame_test = mymodel.scoreAutoEncoder(test, Key.make(), false);
          l2_test = l2_frame_test.anyVec();
          double mult = 10;
          double thresh_test = mult * thresh_train;
          sb.append("\nFinding outliers.\n");
          sb.append("Mean reconstruction error (test): ").append(l2_test.mean()).append("\n");

          Frame df1 = mymodel.scoreDeepFeatures(test, 0);
          Assert.assertTrue(df1.numCols() == 37);
          Assert.assertTrue(df1.numRows() == test.numRows());
          df1.delete();

          Frame df2 = mymodel.scoreDeepFeatures(test, 1);
          Assert.assertTrue(df2.numCols() == 12);
          Assert.assertTrue(df2.numRows() == test.numRows());
          df2.delete();


          // print stats and potential outliers
          sb.append("The following test points are reconstructed with an error greater than ").append(mult)
                  .append(" times the mean reconstruction error of the training data:\n");
          HashSet<Long> outliers = new HashSet<>();
          for (long i = 0; i < l2_test.length(); i++) {
            if (l2_test.at(i) > thresh_test) {
              outliers.add(i);
              sb.append(String.format("row %d : l2 error = %5f\n", i, l2_test.at(i)));
            }
          }

          // check that the all outliers are found (and nothing else)
          Assert.assertTrue(outliers.contains(20L));
          Assert.assertTrue(outliers.contains(21L));
          Assert.assertTrue(outliers.contains(22L));
          Assert.assertEquals(3, outliers.size());

          // check if reconstruction error is the same from model and mojo model too - test case for PUBDEV-6030
          // also check if reconstruction error is calculated correctly if the parameter standardize is set to false 
          // - test case for PUBDEV-6267
          try {
            DeeplearningMojoModel mojoModel = (DeeplearningMojoModel) mymodel.toMojo();
            EasyPredictModelWrapper model = new EasyPredictModelWrapper(mojoModel);

            DeeplearningMojoModel  mojoModelNoStand = (DeeplearningMojoModel) mymodelNoStand.toMojo();
            EasyPredictModelWrapper modelNoStand = new EasyPredictModelWrapper(mojoModelNoStand);
            
            double calcNormMse = 0;
            double calcNormMseNoStand = 0;
            for (int r = 0; r < train.numRows(); r++) {
              RowData tmpRow = new RowData();
              BufferedString bStr = new BufferedString();
              for (int c = 0; c < train.numCols(); c++) {
                if (train.vec(c).isCategorical()) {
                  tmpRow.put(train.names()[c], train.vec(c).atStr(bStr, r).toString());
                } else {
                  tmpRow.put(train.names()[c],  train.vec(c).at(r));
                }
              }
              AutoEncoderModelPrediction tmpPrediction = model.predictAutoEncoder(tmpRow);
              calcNormMse += tmpPrediction.mse;

              AutoEncoderModelPrediction tmpPredictionNoStand = modelNoStand.predictAutoEncoder(tmpRow);
              calcNormMseNoStand += tmpPredictionNoStand.mse;
            }
            double mojoMeanError = calcNormMse/train.numRows();
            sb.append("Mojo mean reconstruction error (train): ").append(mojoMeanError).append("\n");
            sb.append("Mean reconstruction error should be the same from model compare to mojo model " +
                    "reconstruction error: ");
            sb.append(mean_l2).append(" == ").append(mojoMeanError).append("\n");
            Assert.assertEquals( mean_l2, mojoMeanError, 1e-7);
            
            double mojoMeanErrorNoStand = calcNormMseNoStand/train.numRows();
            sb.append("Mojo mean reconstruction error (train): ").append(mojoMeanErrorNoStand).append("\n");
            sb.append("Mean reconstruction error should be the same from model compare to mojo model " +
                    "reconstruction error: ");
            sb.append(mymodelNoStand._output.errors.scored_train._mse).append(" == ").append(mojoMeanErrorNoStand).append("\n");
            Assert.assertEquals(mymodelNoStand._output.errors.scored_train._mse, mojoMeanErrorNoStand, 1e-7);
            
          } catch (IOException error) {
            Assert.fail("IOException when testing mojo mean reconstruction error: "+error.toString());
          } catch (PredictException error){
            Assert.fail("PredictException when testing mojo mean reconstruction error: "+error.toString());
          }
        } finally {
          Log.info(sb);
          // cleanup
          if (mymodelNoStand != null) mymodelNoStand.delete();
          if (mymodel != null) mymodel.delete();
          if (l2_frame_train != null) l2_frame_train.delete();
          if (l2_frame_test != null) l2_frame_test.delete();
        }
      }
    } finally {
      if (train!=null) train.delete();
      if (test!=null) test.delete();
    }
  }
}

