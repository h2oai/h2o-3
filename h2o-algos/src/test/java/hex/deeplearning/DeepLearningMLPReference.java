package hex.deeplearning;

import org.junit.Ignore;
import hex.deeplearning.DeepLearningParameters.Activation;
import hex.deeplearning.DeepLearningParameters.Loss;
import java.text.DecimalFormat;
import java.util.Random;


/**
 * James McCaffrey's MLP on Iris.
 * <nl>
 * Adapted to Java as a reference implementation for testing.
 * <nl>
 * http://channel9.msdn.com/Events/Build/2013/2-401
 */
@Ignore
public class DeepLearningMLPReference {
  static final DecimalFormat _format = new DecimalFormat("0.000");

  double[][] _trainData;
  double[][] _testData;
  NeuralNetwork _nn;

  public void init(DeepLearningParameters.Activation activation, Random rand, double holdout_ratio, int numHidden) {
    double[][] ds = new double[150][];
    int r = 0;
    ds[r++] = new double[] { 5.1, 3.5, 1.4, 0.2, 0, 0, 1 };
    ds[r++] = new double[] { 4.9, 3, 1.4, 0.2, 0, 0, 1 };
    ds[r++] = new double[] { 4.7, 3.2, 1.3, 0.2, 0, 0, 1 };
    ds[r++] = new double[] { 4.6, 3.1, 1.5, 0.2, 0, 0, 1 };
    ds[r++] = new double[] { 5, 3.6, 1.4, 0.2, 0, 0, 1 };
    ds[r++] = new double[] { 5.4, 3.9, 1.7, 0.4, 0, 0, 1 };
    ds[r++] = new double[] { 4.6, 3.4, 1.4, 0.3, 0, 0, 1 };
    ds[r++] = new double[] { 5, 3.4, 1.5, 0.2, 0, 0, 1 };
    ds[r++] = new double[] { 4.4, 2.9, 1.4, 0.2, 0, 0, 1 };
    ds[r++] = new double[] { 4.9, 3.1, 1.5, 0.1, 0, 0, 1 };
    ds[r++] = new double[] { 5.4, 3.7, 1.5, 0.2, 0, 0, 1 };
    ds[r++] = new double[] { 4.8, 3.4, 1.6, 0.2, 0, 0, 1 };
    ds[r++] = new double[] { 4.8, 3, 1.4, 0.1, 0, 0, 1 };
    ds[r++] = new double[] { 4.3, 3, 1.1, 0.1, 0, 0, 1 };
    ds[r++] = new double[] { 5.8, 4, 1.2, 0.2, 0, 0, 1 };
    ds[r++] = new double[] { 5.7, 4.4, 1.5, 0.4, 0, 0, 1 };
    ds[r++] = new double[] { 5.4, 3.9, 1.3, 0.4, 0, 0, 1 };
    ds[r++] = new double[] { 5.1, 3.5, 1.4, 0.3, 0, 0, 1 };
    ds[r++] = new double[] { 5.7, 3.8, 1.7, 0.3, 0, 0, 1 };
    ds[r++] = new double[] { 5.1, 3.8, 1.5, 0.3, 0, 0, 1 };
    ds[r++] = new double[] { 5.4, 3.4, 1.7, 0.2, 0, 0, 1 };
    ds[r++] = new double[] { 5.1, 3.7, 1.5, 0.4, 0, 0, 1 };
    ds[r++] = new double[] { 4.6, 3.6, 1, 0.2, 0, 0, 1 };
    ds[r++] = new double[] { 5.1, 3.3, 1.7, 0.5, 0, 0, 1 };
    ds[r++] = new double[] { 4.8, 3.4, 1.9, 0.2, 0, 0, 1 };
    ds[r++] = new double[] { 5, 3, 1.6, 0.2, 0, 0, 1 };
    ds[r++] = new double[] { 5, 3.4, 1.6, 0.4, 0, 0, 1 };
    ds[r++] = new double[] { 5.2, 3.5, 1.5, 0.2, 0, 0, 1 };
    ds[r++] = new double[] { 5.2, 3.4, 1.4, 0.2, 0, 0, 1 };
    ds[r++] = new double[] { 4.7, 3.2, 1.6, 0.2, 0, 0, 1 };
    ds[r++] = new double[] { 4.8, 3.1, 1.6, 0.2, 0, 0, 1 };
    ds[r++] = new double[] { 5.4, 3.4, 1.5, 0.4, 0, 0, 1 };
    ds[r++] = new double[] { 5.2, 4.1, 1.5, 0.1, 0, 0, 1 };
    ds[r++] = new double[] { 5.5, 4.2, 1.4, 0.2, 0, 0, 1 };
    ds[r++] = new double[] { 4.9, 3.1, 1.5, 0.1, 0, 0, 1 };
    ds[r++] = new double[] { 5, 3.2, 1.2, 0.2, 0, 0, 1 };
    ds[r++] = new double[] { 5.5, 3.5, 1.3, 0.2, 0, 0, 1 };
    ds[r++] = new double[] { 4.9, 3.1, 1.5, 0.1, 0, 0, 1 };
    ds[r++] = new double[] { 4.4, 3, 1.3, 0.2, 0, 0, 1 };
    ds[r++] = new double[] { 5.1, 3.4, 1.5, 0.2, 0, 0, 1 };
    ds[r++] = new double[] { 5, 3.5, 1.3, 0.3, 0, 0, 1 };
    ds[r++] = new double[] { 4.5, 2.3, 1.3, 0.3, 0, 0, 1 };
    ds[r++] = new double[] { 4.4, 3.2, 1.3, 0.2, 0, 0, 1 };
    ds[r++] = new double[] { 5, 3.5, 1.6, 0.6, 0, 0, 1 };
    ds[r++] = new double[] { 5.1, 3.8, 1.9, 0.4, 0, 0, 1 };
    ds[r++] = new double[] { 4.8, 3, 1.4, 0.3, 0, 0, 1 };
    ds[r++] = new double[] { 5.1, 3.8, 1.6, 0.2, 0, 0, 1 };
    ds[r++] = new double[] { 4.6, 3.2, 1.4, 0.2, 0, 0, 1 };
    ds[r++] = new double[] { 5.3, 3.7, 1.5, 0.2, 0, 0, 1 };
    ds[r++] = new double[] { 5, 3.3, 1.4, 0.2, 0, 0, 1 };
    ds[r++] = new double[] { 7, 3.2, 4.7, 1.4, 0, 1, 0 };
    ds[r++] = new double[] { 6.4, 3.2, 4.5, 1.5, 0, 1, 0 };
    ds[r++] = new double[] { 6.9, 3.1, 4.9, 1.5, 0, 1, 0 };
    ds[r++] = new double[] { 5.5, 2.3, 4, 1.3, 0, 1, 0 };
    ds[r++] = new double[] { 6.5, 2.8, 4.6, 1.5, 0, 1, 0 };
    ds[r++] = new double[] { 5.7, 2.8, 4.5, 1.3, 0, 1, 0 };
    ds[r++] = new double[] { 6.3, 3.3, 4.7, 1.6, 0, 1, 0 };
    ds[r++] = new double[] { 4.9, 2.4, 3.3, 1, 0, 1, 0 };
    ds[r++] = new double[] { 6.6, 2.9, 4.6, 1.3, 0, 1, 0 };
    ds[r++] = new double[] { 5.2, 2.7, 3.9, 1.4, 0, 1, 0 };
    ds[r++] = new double[] { 5, 2, 3.5, 1, 0, 1, 0 };
    ds[r++] = new double[] { 5.9, 3, 4.2, 1.5, 0, 1, 0 };
    ds[r++] = new double[] { 6, 2.2, 4, 1, 0, 1, 0 };
    ds[r++] = new double[] { 6.1, 2.9, 4.7, 1.4, 0, 1, 0 };
    ds[r++] = new double[] { 5.6, 2.9, 3.6, 1.3, 0, 1, 0 };
    ds[r++] = new double[] { 6.7, 3.1, 4.4, 1.4, 0, 1, 0 };
    ds[r++] = new double[] { 5.6, 3, 4.5, 1.5, 0, 1, 0 };
    ds[r++] = new double[] { 5.8, 2.7, 4.1, 1, 0, 1, 0 };
    ds[r++] = new double[] { 6.2, 2.2, 4.5, 1.5, 0, 1, 0 };
    ds[r++] = new double[] { 5.6, 2.5, 3.9, 1.1, 0, 1, 0 };
    ds[r++] = new double[] { 5.9, 3.2, 4.8, 1.8, 0, 1, 0 };
    ds[r++] = new double[] { 6.1, 2.8, 4, 1.3, 0, 1, 0 };
    ds[r++] = new double[] { 6.3, 2.5, 4.9, 1.5, 0, 1, 0 };
    ds[r++] = new double[] { 6.1, 2.8, 4.7, 1.2, 0, 1, 0 };
    ds[r++] = new double[] { 6.4, 2.9, 4.3, 1.3, 0, 1, 0 };
    ds[r++] = new double[] { 6.6, 3, 4.4, 1.4, 0, 1, 0 };
    ds[r++] = new double[] { 6.8, 2.8, 4.8, 1.4, 0, 1, 0 };
    ds[r++] = new double[] { 6.7, 3, 5, 1.7, 0, 1, 0 };
    ds[r++] = new double[] { 6, 2.9, 4.5, 1.5, 0, 1, 0 };
    ds[r++] = new double[] { 5.7, 2.6, 3.5, 1, 0, 1, 0 };
    ds[r++] = new double[] { 5.5, 2.4, 3.8, 1.1, 0, 1, 0 };
    ds[r++] = new double[] { 5.5, 2.4, 3.7, 1, 0, 1, 0 };
    ds[r++] = new double[] { 5.8, 2.7, 3.9, 1.2, 0, 1, 0 };
    ds[r++] = new double[] { 6, 2.7, 5.1, 1.6, 0, 1, 0 };
    ds[r++] = new double[] { 5.4, 3, 4.5, 1.5, 0, 1, 0 };
    ds[r++] = new double[] { 6, 3.4, 4.5, 1.6, 0, 1, 0 };
    ds[r++] = new double[] { 6.7, 3.1, 4.7, 1.5, 0, 1, 0 };
    ds[r++] = new double[] { 6.3, 2.3, 4.4, 1.3, 0, 1, 0 };
    ds[r++] = new double[] { 5.6, 3, 4.1, 1.3, 0, 1, 0 };
    ds[r++] = new double[] { 5.5, 2.5, 4, 1.3, 0, 1, 0 };
    ds[r++] = new double[] { 5.5, 2.6, 4.4, 1.2, 0, 1, 0 };
    ds[r++] = new double[] { 6.1, 3, 4.6, 1.4, 0, 1, 0 };
    ds[r++] = new double[] { 5.8, 2.6, 4, 1.2, 0, 1, 0 };
    ds[r++] = new double[] { 5, 2.3, 3.3, 1, 0, 1, 0 };
    ds[r++] = new double[] { 5.6, 2.7, 4.2, 1.3, 0, 1, 0 };
    ds[r++] = new double[] { 5.7, 3, 4.2, 1.2, 0, 1, 0 };
    ds[r++] = new double[] { 5.7, 2.9, 4.2, 1.3, 0, 1, 0 };
    ds[r++] = new double[] { 6.2, 2.9, 4.3, 1.3, 0, 1, 0 };
    ds[r++] = new double[] { 5.1, 2.5, 3, 1.1, 0, 1, 0 };
    ds[r++] = new double[] { 5.7, 2.8, 4.1, 1.3, 0, 1, 0 };
    ds[r++] = new double[] { 6.3, 3.3, 6, 2.5, 1, 0, 0 };
    ds[r++] = new double[] { 5.8, 2.7, 5.1, 1.9, 1, 0, 0 };
    ds[r++] = new double[] { 7.1, 3, 5.9, 2.1, 1, 0, 0 };
    ds[r++] = new double[] { 6.3, 2.9, 5.6, 1.8, 1, 0, 0 };
    ds[r++] = new double[] { 6.5, 3, 5.8, 2.2, 1, 0, 0 };
    ds[r++] = new double[] { 7.6, 3, 6.6, 2.1, 1, 0, 0 };
    ds[r++] = new double[] { 4.9, 2.5, 4.5, 1.7, 1, 0, 0 };
    ds[r++] = new double[] { 7.3, 2.9, 6.3, 1.8, 1, 0, 0 };
    ds[r++] = new double[] { 6.7, 2.5, 5.8, 1.8, 1, 0, 0 };
    ds[r++] = new double[] { 7.2, 3.6, 6.1, 2.5, 1, 0, 0 };
    ds[r++] = new double[] { 6.5, 3.2, 5.1, 2, 1, 0, 0 };
    ds[r++] = new double[] { 6.4, 2.7, 5.3, 1.9, 1, 0, 0 };
    ds[r++] = new double[] { 6.8, 3, 5.5, 2.1, 1, 0, 0 };
    ds[r++] = new double[] { 5.7, 2.5, 5, 2, 1, 0, 0 };
    ds[r++] = new double[] { 5.8, 2.8, 5.1, 2.4, 1, 0, 0 };
    ds[r++] = new double[] { 6.4, 3.2, 5.3, 2.3, 1, 0, 0 };
    ds[r++] = new double[] { 6.5, 3, 5.5, 1.8, 1, 0, 0 };
    ds[r++] = new double[] { 7.7, 3.8, 6.7, 2.2, 1, 0, 0 };
    ds[r++] = new double[] { 7.7, 2.6, 6.9, 2.3, 1, 0, 0 };
    ds[r++] = new double[] { 6, 2.2, 5, 1.5, 1, 0, 0 };
    ds[r++] = new double[] { 6.9, 3.2, 5.7, 2.3, 1, 0, 0 };
    ds[r++] = new double[] { 5.6, 2.8, 4.9, 2, 1, 0, 0 };
    ds[r++] = new double[] { 7.7, 2.8, 6.7, 2, 1, 0, 0 };
    ds[r++] = new double[] { 6.3, 2.7, 4.9, 1.8, 1, 0, 0 };
    ds[r++] = new double[] { 6.7, 3.3, 5.7, 2.1, 1, 0, 0 };
    ds[r++] = new double[] { 7.2, 3.2, 6, 1.8, 1, 0, 0 };
    ds[r++] = new double[] { 6.2, 2.8, 4.8, 1.8, 1, 0, 0 };
    ds[r++] = new double[] { 6.1, 3, 4.9, 1.8, 1, 0, 0 };
    ds[r++] = new double[] { 6.4, 2.8, 5.6, 2.1, 1, 0, 0 };
    ds[r++] = new double[] { 7.2, 3, 5.8, 1.6, 1, 0, 0 };
    ds[r++] = new double[] { 7.4, 2.8, 6.1, 1.9, 1, 0, 0 };
    ds[r++] = new double[] { 7.9, 3.8, 6.4, 2, 1, 0, 0 };
    ds[r++] = new double[] { 6.4, 2.8, 5.6, 2.2, 1, 0, 0 };
    ds[r++] = new double[] { 6.3, 2.8, 5.1, 1.5, 1, 0, 0 };
    ds[r++] = new double[] { 6.1, 2.6, 5.6, 1.4, 1, 0, 0 };
    ds[r++] = new double[] { 7.7, 3, 6.1, 2.3, 1, 0, 0 };
    ds[r++] = new double[] { 6.3, 3.4, 5.6, 2.4, 1, 0, 0 };
    ds[r++] = new double[] { 6.4, 3.1, 5.5, 1.8, 1, 0, 0 };
    ds[r++] = new double[] { 6, 3, 4.8, 1.8, 1, 0, 0 };
    ds[r++] = new double[] { 6.9, 3.1, 5.4, 2.1, 1, 0, 0 };
    ds[r++] = new double[] { 6.7, 3.1, 5.6, 2.4, 1, 0, 0 };
    ds[r++] = new double[] { 6.9, 3.1, 5.1, 2.3, 1, 0, 0 };
    ds[r++] = new double[] { 5.8, 2.7, 5.1, 1.9, 1, 0, 0 };
    ds[r++] = new double[] { 6.8, 3.2, 5.9, 2.3, 1, 0, 0 };
    ds[r++] = new double[] { 6.7, 3.3, 5.7, 2.5, 1, 0, 0 };
    ds[r++] = new double[] { 6.7, 3, 5.2, 2.3, 1, 0, 0 };
    ds[r++] = new double[] { 6.3, 2.5, 5, 1.9, 1, 0, 0 };
    ds[r++] = new double[] { 6.5, 3, 5.2, 2, 1, 0, 0 };
    ds[r++] = new double[] { 6.2, 3.4, 5.4, 2.3, 1, 0, 0 };
    ds[r++] = new double[] { 5.9, 3, 5.1, 1.8, 1, 0, 0 };

    double[][] allData = new double[ds.length][ds[0].length];
    for( int j = 0; j < allData.length; j++ ) {
      for( int i = 0; i < allData[j].length; i++ )
        allData[j][i] = ds[j][i];

      allData[j][4] = ds[j][6];
      allData[j][5] = ds[j][5];
      allData[j][6] = ds[j][4];
    }

    int trainRows = (int) (allData.length * holdout_ratio);
    int testRows = allData.length - trainRows;
    _trainData = new double[trainRows][];
    _testData = new double[testRows][];
    MakeTrainTest(allData, _trainData, _testData, rand);

    // Normalize all data using train stats
    for( int i = 0; i < 4; i++ ) {
      double mean = 0;
      for( int n = 0; n < _trainData.length; n++ )
        mean += _trainData[n][i];
      mean /= _trainData.length;

      double sigma = 0;
      for( int n = 0; n < _trainData.length; n++ ) {
        double d = _trainData[n][i] - mean;
        sigma += d * d;
      }
      sigma = Math.sqrt(sigma / (_trainData.length - 1));
      for( int n = 0; n < _trainData.length; n++ ) {
        _trainData[n][i] -= mean;
        _trainData[n][i] /= sigma;
      }
      for( int n = 0; n < _testData.length; n++ ) {
        _testData[n][i] -= mean;
        _testData[n][i] /= sigma;
      }
    }

    int numInput = 4;
    int numOutput = 3;
    _nn = new NeuralNetwork(activation, numInput, numHidden, numOutput);
    _nn.InitializeWeights();
  }

  void train(int maxEpochs, double learnRate, double momentum, Loss loss) {
    _nn.Train(_trainData, maxEpochs, learnRate, momentum, loss);
  }

  void MakeTrainTest(double[][] allData, double[][] trainData, double[][] testData, Random rand) {
    // split allData into 80% trainData and 20% testData
    int numCols = allData[0].length;

    int[] shuffle = new int[allData.length]; // create a random sequence of indexes
    for( int i = 0; i < shuffle.length; ++i )
      shuffle[i] = i;
    NeuralNetwork.shuffle(shuffle, rand);

    int si = 0; // index into sequence[]
    int j = 0; // index into trainData or testData

    for( ; si < trainData.length; ++si ) // first rows to train data
    {
      trainData[j] = new double[numCols];
      int idx = shuffle[si];
      System.arraycopy(allData[idx], 0, trainData[j], 0, numCols);
      ++j;
    }

    j = 0; // reset to start of test data
    for( ; si < allData.length; ++si ) // remainder to test data
    {
      testData[j] = new double[numCols];
      int idx = shuffle[si];
      System.arraycopy(allData[idx], 0, testData[j], 0, numCols);
      ++j;
    }
  } // MakeTrainTest

  static void Normalize(double[][] dataMatrix, int[] cols) {
    // in most cases you want to normalize the x-data
  }

  static void ShowVector(double[] vector, int valsPerRow, int decimals, boolean newLine) {
    for( int i = 0; i < vector.length; ++i ) {
      if( i % valsPerRow == 0 )
        System.out.println("");
      System.out.print(_format.format(vector[i]) + " ");
    }
    if(newLine)
      System.out.println("");
  }

  static void ShowMatrix(double[][] matrix, int numRows, int decimals, boolean newLine) {
    for( int i = 0; i < numRows; ++i ) {
      System.out.print(i + ": ");
      for( int j = 0; j < matrix[i].length; ++j ) {
        if( matrix[i][j] >= 0.0 )
          System.out.print(" ");
        else
          System.out.print("-");
        System.out.print(_format.format(Math.abs(matrix[i][j])) + " ");
      }
      System.out.println("");
    }
    if( newLine == true )
      System.out.println("");
  }

  public static class NeuralNetwork {
    Activation activation = Activation.Tanh;
    int numInput;
    int numHidden;
    int numOutput;

    double[] inputs;

    float[][] ihWeights; // input-hidden
    double[] hBiases;
    double[] hOutputs;

    float[][] hoWeights; // hidden-output
    double[] oBiases;

    double[] outputs;

    // back-prop specific arrays (these could be local to method UpdateWeights)
    double[] oGrads; // output gradients for back-propagation
    double[] hGrads; // hidden gradients for back-propagation

    // back-prop momentum specific arrays (these could be local to method Train)
    float[][] ihPrevWeightsDelta;  // for momentum with back-propagation
    double[] hPrevBiasesDelta;
    float[][] hoPrevWeightsDelta;
    double[] oPrevBiasesDelta;

    public NeuralNetwork(DeepLearningParameters.Activation activationType, int numInput, int numHidden, int numOutput) {
      this.activation = activationType;
      this.numInput = numInput;
      this.numHidden = numHidden;
      this.numOutput = numOutput;

      this.inputs = new double[numInput];

      this.ihWeights = MakeMatrixFloat(numInput, numHidden);
      this.hBiases = new double[numHidden];
      this.hOutputs = new double[numHidden];

      this.hoWeights = MakeMatrixFloat(numHidden, numOutput);
      this.oBiases = new double[numOutput];

      this.outputs = new double[numOutput];

      // back-prop related arrays below
      this.hGrads = new double[numHidden];
      this.oGrads = new double[numOutput];

      this.ihPrevWeightsDelta = MakeMatrixFloat(numInput, numHidden);
      this.hPrevBiasesDelta = new double[numHidden];
      this.hoPrevWeightsDelta = MakeMatrixFloat(numHidden, numOutput);
      this.oPrevBiasesDelta = new double[numOutput];
    } // ctor

    private static double[][] MakeMatrix(int rows, int cols) // helper for ctor
    {
      double[][] result = new double[rows][];
      for( int r = 0; r < result.length; ++r )
        result[r] = new double[cols];
      return result;
    }

    private static float[][] MakeMatrixFloat(int rows, int cols) // helper for ctor
    {
      float[][] result = new float[rows][];
      for( int r = 0; r < result.length; ++r )
        result[r] = new float[cols];
      return result;
    }

    @Override public String toString() // yikes
    {
      String s = "";
      s += "===============================\n";
      s += "numInput = " + numInput + " numHidden = " + numHidden + " numOutput = " + numOutput + "\n\n";

      s += "inputs: \n";
      for( int i = 0; i < inputs.length; ++i )
        s += inputs[i] + " ";
      s += "\n\n";

      s += "ihWeights: \n";
      for( int i = 0; i < ihWeights.length; ++i ) {
        for( int j = 0; j < ihWeights[i].length; ++j ) {
          s += ihWeights[i][j] + " ";
        }
        s += "\n";
      }
      s += "\n";

      s += "hBiases: \n";
      for( int i = 0; i < hBiases.length; ++i )
        s += hBiases[i] + " ";
      s += "\n\n";

      s += "hOutputs: \n";
      for( int i = 0; i < hOutputs.length; ++i )
        s += hOutputs[i] + " ";
      s += "\n\n";

      s += "hoWeights: \n";
      for( int i = 0; i < hoWeights.length; ++i ) {
        for( int j = 0; j < hoWeights[i].length; ++j ) {
          s += hoWeights[i][j] + " ";
        }
        s += "\n";
      }
      s += "\n";

      s += "oBiases: \n";
      for( int i = 0; i < oBiases.length; ++i )
        s += oBiases[i] + " ";
      s += "\n\n";

      s += "hGrads: \n";
      for( int i = 0; i < hGrads.length; ++i )
        s += hGrads[i] + " ";
      s += "\n\n";

      s += "oGrads: \n";
      for( int i = 0; i < oGrads.length; ++i )
        s += oGrads[i] + " ";
      s += "\n\n";

      s += "ihPrevWeightsDelta: \n";
      for( int i = 0; i < ihPrevWeightsDelta.length; ++i ) {
        for( int j = 0; j < ihPrevWeightsDelta[i].length; ++j ) {
          s += ihPrevWeightsDelta[i][j] + " ";
        }
        s += "\n";
      }
      s += "\n";

      s += "hPrevBiasesDelta: \n";
      for( int i = 0; i < hPrevBiasesDelta.length; ++i )
        s += hPrevBiasesDelta[i] + " ";
      s += "\n\n";

      s += "hoPrevWeightsDelta: \n";
      for( int i = 0; i < hoPrevWeightsDelta.length; ++i ) {
        for( int j = 0; j < hoPrevWeightsDelta[i].length; ++j ) {
          s += hoPrevWeightsDelta[i][j] + " ";
        }
        s += "\n";
      }
      s += "\n";

      s += "oPrevBiasesDelta: \n";
      for( int i = 0; i < oPrevBiasesDelta.length; ++i )
        s += oPrevBiasesDelta[i] + " ";
      s += "\n\n";

      s += "outputs: \n";
      for( int i = 0; i < outputs.length; ++i )
        s += outputs[i] + " ";
      s += "\n\n";

      s += "===============================\n";
      return s;
    }

    // ----------------------------------------------------------------------------------------

    public void SetWeights(float[] weights) {
      // copy weights and biases in weights[] array to i-h weights, i-h biases, h-o weights, h-o
// biases
      int numWeights = (numInput * numHidden) + (numHidden * numOutput) + numHidden + numOutput;
      if( weights.length != numWeights )
        throw new RuntimeException("Bad weights array length: ");

      int k = 0; // points into weights param

      for( int i = 0; i < numInput; ++i )
        for( int j = 0; j < numHidden; ++j )
          ihWeights[i][j] = weights[k++];
      for( int i = 0; i < numHidden; ++i )
        hBiases[i] = weights[k++];
      for( int i = 0; i < numHidden; ++i )
        for( int j = 0; j < numOutput; ++j )
          hoWeights[i][j] = weights[k++];
      for( int i = 0; i < numOutput; ++i )
        oBiases[i] = weights[k++];
    }

    public void InitializeWeights() {
      // initialize weights and biases to small random values
      int numWeights = (numInput * numHidden) + (numHidden * numOutput) + numHidden + numOutput;
      float[] initialWeights = new float[numWeights];
      double lo = -0.01f;
      double hi = 0.01f;
      Random rnd = new Random(0);
      for( int i = 0; i < initialWeights.length; ++i )
        initialWeights[i] = (float)((hi - lo) * rnd.nextFloat() + lo);
      this.SetWeights(initialWeights);
    }

    public double[] GetWeights() {
      // returns the current set of wweights, presumably after training
      int numWeights = (numInput * numHidden) + (numHidden * numOutput) + numHidden + numOutput;
      double[] result = new double[numWeights];
      int k = 0;
      for( int i = 0; i < ihWeights.length; ++i )
        for( int j = 0; j < ihWeights[0].length; ++j )
          result[k++] = ihWeights[i][j];
      for( int i = 0; i < hBiases.length; ++i )
        result[k++] = hBiases[i];
      for( int i = 0; i < hoWeights.length; ++i )
        for( int j = 0; j < hoWeights[0].length; ++j )
          result[k++] = hoWeights[i][j];
      for( int i = 0; i < oBiases.length; ++i )
        result[k++] = oBiases[i];
      return result;
    }

    // ----------------------------------------------------------------------------------------

    public double[] ComputeOutputs(double[] xValues) {
      if( xValues.length != numInput )
        throw new RuntimeException("Bad xValues array length");

      double[] hSums = new double[numHidden]; // hidden nodes sums scratch array
      double[] oSums = new double[numOutput]; // output nodes sums

      for( int i = 0; i < xValues.length; ++i )
        // copy x-values to inputs
        this.inputs[i] = xValues[i];

      for( int j = 0; j < numHidden; ++j )
        // compute i-h sum of weights * inputs
        for( int i = 0; i < numInput; ++i )
          hSums[j] += this.inputs[i] * this.ihWeights[i][j]; // note +=

      for( int i = 0; i < numHidden; ++i )
        // add biases to input-to-hidden sums
        hSums[i] += this.hBiases[i];

      for( int i = 0; i < numHidden; ++i )
        // apply activation
        if (activation == Activation.Tanh || activation == Activation.TanhWithDropout) {
          hOutputs[i] = HyperTanFunction(hSums[i]);
        } else if (activation == Activation.Rectifier || activation == Activation.RectifierWithDropout) {
          hOutputs[i] = Rectifier(hSums[i]);
        } else throw new RuntimeException("invalid activation.");

      for( int j = 0; j < numOutput; ++j )
        // compute h-o sum of weights * hOutputs
        for( int i = 0; i < numHidden; ++i )
          oSums[j] += hOutputs[i] * hoWeights[i][j];

      for( int i = 0; i < numOutput; ++i )
        // add biases to input-to-hidden sums
        oSums[i] += oBiases[i];

      double[] softOut = Softmax(oSums); // softmax activation does all outputs at once for
// efficiency
      System.arraycopy(softOut, 0, outputs, 0, softOut.length);

      double[] retResult = new double[numOutput]; // could define a GetOutputs method instead
      System.arraycopy(this.outputs, 0, retResult, 0, retResult.length);
      return retResult;
    } // ComputeOutputs

    private static double HyperTanFunction(double x) {
      return Math.tanh(x);
    }

    private static double Rectifier(double x) {
      return Math.max(x, 0.0f);
    }

    private static double[] Softmax(double[] oSums) {
      // does all output nodes at once so scale doesn't have to be re-computed each time
      // 1. determine max output sum
      double max = oSums[0];
      for( int i = 0; i < oSums.length; ++i )
        if( oSums[i] > max )
          max = oSums[i];

      // 2. determine scaling factor -- sum of exp(each val - max)
      double[] result = new double[oSums.length];
      double scale = 0;
      for( int i = 0; i < result.length; i++ ) {
        result[i] = Math.exp(oSums[i] - max);
        scale += result[i];
      }
      for( int i = 0; i < result.length; i++ )
        result[i] /= scale;
      return result; // now scaled so that xi sum to 1.0
    }

    // ----------------------------------------------------------------------------------------

    private void UpdateWeights(double[] tValues, double learnRate, double momentum, Loss loss) {
      // update the weights and biases using back-propagation, with target values, eta (learning
// rate),
      // alpha (momentum)
      // assumes that SetWeights and ComputeOutputs have been called and so all the internal arrays
// and
      // matrices have values (other than 0.0)
      if( tValues.length != numOutput )
        throw new RuntimeException("target values not same length as output in UpdateWeights");

      // 1. compute output gradients
      for( int i = 0; i < oGrads.length; ++i ) {
        // derivative of softmax = (1 - y) * y (same as log-sigmoid)
        double derivative = (1 - outputs[i]) * outputs[i];
        if (loss == Loss.CrossEntropy) {
          oGrads[i] = tValues[i] - outputs[i];
        } else if (loss == Loss.MeanSquare) {
          // 'mean squared error version'. research suggests cross-entropy is better here . . .
          oGrads[i] = derivative * (tValues[i] - outputs[i]);
        } else throw new RuntimeException("invalid loss function");
      }

      // 2. compute hidden gradients
      for( int i = 0; i < hGrads.length; ++i ) {
        double derivative = 1;
        if (activation == Activation.Tanh || activation == Activation.TanhWithDropout) {
          derivative = (1 - hOutputs[i]) * (1 + hOutputs[i]); // derivative of tanh (y) = (1 - y) * (1 + y)
        } else if (activation == Activation.Rectifier || activation == Activation.RectifierWithDropout) {
          derivative = hOutputs[i] <= 0 ? 0 : 1;
        } else throw new RuntimeException("invalid activation.");

        double sum = 0;
        for( int j = 0; j < numOutput; ++j ) // each hidden delta is the sum of numOutput terms
        {
          double x = oGrads[j] * hoWeights[i][j];
          sum += x;
        }
        hGrads[i] = derivative * sum;
      }

      // 3a. update hidden weights (gradients must be computed right-to-left but weights
      // can be updated in any order)
      for( int i = 0; i < ihWeights.length; ++i ) // 0..2 (3)
      {
        for( int j = 0; j < ihWeights[0].length; ++j ) // 0..3 (4)
        {
          double delta = learnRate * hGrads[j] * inputs[i]; // compute the new delta
          ihWeights[i][j] += delta; // update. note we use '+' instead of '-'. this can be very
// tricky.
          // add momentum using previous delta. on first pass old value will be 0.0 but that's OK.
          ihWeights[i][j] += momentum * ihPrevWeightsDelta[i][j];
          // weight decay would go here
          ihPrevWeightsDelta[i][j] = (float)delta; // don't forget to save the delta for momentum
        }
      }

      // 3b. update hidden biases
      for( int i = 0; i < hBiases.length; ++i ) {
        // the 1.0 below is the constant input for any bias; could leave out
        double delta = learnRate * hGrads[i] * 1;
        hBiases[i] += delta;
        hBiases[i] += momentum * hPrevBiasesDelta[i]; // momentum
        // weight decay here
        hPrevBiasesDelta[i] = delta; // don't forget to save the delta
      }

      // 4. update hidden-output weights
      for( int i = 0; i < hoWeights.length; ++i ) {
        for( int j = 0; j < hoWeights[0].length; ++j ) {
          // see above: hOutputs are inputs to the deeplearning outputs
          double delta = learnRate * oGrads[j] * hOutputs[i];
          hoWeights[i][j] += delta;
          hoWeights[i][j] += momentum * hoPrevWeightsDelta[i][j]; // momentum
          // weight decay here
          hoPrevWeightsDelta[i][j] = (float)delta; // save
        }
      }

      // 4b. update output biases
      for( int i = 0; i < oBiases.length; ++i ) {
        double delta = learnRate * oGrads[i] * 1;
        oBiases[i] += delta;
        oBiases[i] += momentum * oPrevBiasesDelta[i]; // momentum
        // weight decay here
        oPrevBiasesDelta[i] = delta; // save
      }
    } // UpdateWeights

    // ----------------------------------------------------------------------------------------

    public void Train(double[][] trainData, int maxEprochs, double learnRate, double momentum, Loss loss) {
      // train a back-prop style NN classifier using learning rate and momentum
      // no weight decay
      int epoch = 0;
      double[] xValues = new double[numInput]; // inputs
      double[] tValues = new double[numOutput]; // target values

      int[] sequence = new int[trainData.length];
      for( int i = 0; i < sequence.length; ++i )
        sequence[i] = i;

      while( epoch < maxEprochs ) {
        // shuffle(sequence); // visit each training data in random order
        for( int i = 0; i < trainData.length; ++i ) {
          int idx = sequence[i];
          System.arraycopy(trainData[idx], 0, xValues, 0, numInput); // extract x's and y's.
          System.arraycopy(trainData[idx], numInput, tValues, 0, numOutput);
          ComputeOutputs(xValues); // copy xValues in, compute outputs (and store them internally)
          UpdateWeights(tValues, learnRate, momentum, loss); // use back-prop to find better weights
        } // each training tuple
        ++epoch;
      }
    } // Train

    static void shuffle(int[] sequence, Random rand) {
      for( int i = sequence.length - 1; i >= 0; i-- ) {
        int r = rand.nextInt(i + 1);
        int tmp = sequence[r];
        sequence[r] = sequence[i];
        sequence[i] = tmp;
      }
    }

    // ----------------------------------------------------------------------------------------

    public double Accuracy(double[][] testData) {
      // percentage correct using winner-takes all
      int numCorrect = 0;
      int numWrong = 0;
      double[] xValues = new double[numInput]; // inputs
      double[] tValues = new double[numOutput]; // targets
      double[] yValues; // computed Y

      for( int i = 0; i < testData.length; ++i ) {
        System.arraycopy(testData[i], 0, xValues, 0, numInput); // parse test data into x-values and
// t-values
        System.arraycopy(testData[i], numInput, tValues, 0, numOutput);
        yValues = this.ComputeOutputs(xValues);
        //int maxIndex = MaxIndex(yValues); // which cell in yValues has largest value?

        // convert to float and do the same tie-breaking as H2O
        double[] preds = new double[yValues.length+1];
        for (int j=0; j<yValues.length; ++j) preds[j+1] = (float)yValues[j];
        preds[0] = hex.genmodel.GenModel.getPrediction(preds, null, xValues, 0.5);

        if( tValues[(int)preds[0]] == 1.0 ) // ugly. consider AreEqual(double x, double y)
          ++numCorrect;
        else
          ++numWrong;
      }
      return (double)numWrong / (numCorrect + numWrong); // ugly 2 - check for divide by zero
    }

    private static int MaxIndex(double[] vector) // helper for Accuracy()
    {
      // index of largest value
      int bigIndex = 0;
      double biggestVal = vector[0];
      for( int i = 0; i < vector.length; ++i ) {
        if( vector[i] > biggestVal ) {
          biggestVal = vector[i];
          bigIndex = i;
        }
      }
      return bigIndex;
    }
  }
}
