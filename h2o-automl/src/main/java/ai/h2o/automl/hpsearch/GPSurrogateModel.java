package ai.h2o.automl.hpsearch;

import ai.h2o.automl.targetencoding.TargetEncoderFrameHelper;
import hex.DataInfo;
import hex.splitframe.ShuffleSplitFrame;
import org.jblas.*;
import water.DKV;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.TwoDimTable;

import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.frameRowAsArray;
import static org.jblas.DoubleMatrix.eye;
import static org.jblas.MatrixFunctions.exp;

/**
 * This surrogate model is based on Gaussian multivariate process regression
 */
public class GPSurrogateModel extends SurrogateModel{

  private DoubleMatrix covariance = null;
  
  private double _currentBestSigma = -1;
  private double _currentBestEll = -1;
  
  DoubleMatrix getCovarianceMtxWithGaussianKernel(double sigma, double ell, DoubleMatrix x1, DoubleMatrix x2) {
    DoubleMatrix d = Geometry.pairwiseSquaredDistances(x1, x2);
    return exp(d.mul(0.5).div(ell).neg()).mul(sigma * sigma);
  }

  public static class MeanVariance {
    private DoubleMatrix mean;
    private DoubleMatrix variance;
    private DoubleMatrix posteriorCovariance; //posterior -> prior kernel

    public MeanVariance(DoubleMatrix mean, DoubleMatrix variance, DoubleMatrix posteriorCovariance) {
      this.mean = mean;
      this.variance = variance;
      this.posteriorCovariance = posteriorCovariance;
    }


  }
  //  K0 + sigma^2*I    K1
  //  K2                K3
  //
  // 
  MeanVariance posteriorMeanAndVariance(double sigma, double ell, DoubleMatrix covariancePrior, DoubleMatrix observedData, DoubleMatrix newObservation, DoubleMatrix priorMeans) {
    int n = observedData.columns;
    assert priorMeans.rows == n;

    DoubleMatrix covarianceBetweenNewAndPrior = getCovarianceMtxWithGaussianKernel(sigma, ell, observedData, newObservation);   // K1
    DoubleMatrix K2 = covarianceBetweenNewAndPrior.transpose();
    DoubleMatrix varianceForNewObservation = getCovarianceMtxWithGaussianKernel(sigma, ell, newObservation, newObservation);

    DoubleMatrix K = combineKMatrices(covariancePrior, covarianceBetweenNewAndPrior, K2, varianceForNewObservation);

    // mt(x) = K(X*, Xt) * [K(Xt , Xt) + σ^2 * I ]^-1 * yt 
    //
    //           ^^                ^^                 ^^
    //           K2                 b                  c (= priorMeans) 
    DoubleMatrix b = Solve.solve(covariancePrior, eye(n)); // solve with Identity B matrix in Ax=B will return us inverse.

    DoubleMatrix ab = K2.mmul(b);
    DoubleMatrix posteriorMeanMatrix = ab.mmul(priorMeans);

    // Sigma = K(X*, X*) − K(X*, Xt) [ K(Xt , Xt) + σ^2 *I]^−1 * K(Xt , X*)
    //
    //           ^^                ^^      ^^
    // varianceForNewObservation   K2      b                  K1(=covarianceBetweenNewAndPrior) 

    DoubleMatrix varianceMatrix = varianceForNewObservation.sub(ab.mmul(covarianceBetweenNewAndPrior)); //TODO Maybe we need to swap K1 and K2???

    return new MeanVariance(posteriorMeanMatrix, varianceMatrix.diag(), K); //TODO not sure that we need to return only diag elements
  }

  private DoubleMatrix combineKMatrices(DoubleMatrix covariancePrior, DoubleMatrix covarianceBetweenNewAndPrior, DoubleMatrix k2, DoubleMatrix varianceForNewObservation) {
    DoubleMatrix K_UPPER = DoubleMatrix.concatHorizontally(covariancePrior, covarianceBetweenNewAndPrior);
    DoubleMatrix K_LOWER = DoubleMatrix.concatHorizontally(k2, varianceForNewObservation);
    return DoubleMatrix.concatVertically(K_UPPER, K_LOWER);
  }
  
  public static class Standardize extends MRTask<Standardize> {
    private DataInfo _dataInfo;

    public Standardize(DataInfo dataInfo) {
      _dataInfo = dataInfo;
    }

    @Override public void map(Chunk[] cs, NewChunk ncs[]) {
      DataInfo.Row r = _dataInfo.newDenseRow();
      for (int i = 0; i < cs[0]._len; ++i) {
        _dataInfo.extractDenseRow(cs, i, r);
        for (int idx = 0; idx < ncs.length; ++idx) {
          ncs[idx].addNum(r.get(idx));
        }
      }
    }
  }
  
  private Frame standardizeFrame(Frame fr) {
    Frame copyFr = fr.deepCopy(Key.make().toString());
    DKV.put(copyFr);
    copyFr.remove("id").remove();
    copyFr.remove("score").remove();
    
    final DataInfo dinfo = new DataInfo(
            copyFr,
            null,
            0, // we removed score before so there is no responses here
            false,
            DataInfo.TransformType.STANDARDIZE, //do not standardize
            DataInfo.TransformType.NONE, //do not standardize response
            false, //whether to skip missing
            false, // do not replace NAs in numeric cols with mean
            false,  // always add a bucket for missing values
            false, // observation weights
            false,
            false,
            false
    );

    String[] names = copyFr.names();
    Frame res = new Standardize(dinfo).doAll(copyFr.numCols(), Vec.T_NUM, dinfo._adaptedFrame).outputFrame(Key.<Frame>make(), names,null);
    DKV.put(res);
    
    printOutFrameAsTable(res);
    return dinfo._adaptedFrame;
  }
  
  public long totalTimeStandardisation = 0;

  @Override
  public Frame evaluate(Frame hyperparameters, Frame train) {
    
    printOutFrameAsTable(train);
    long start1 = System.currentTimeMillis();
    Frame trainStandardisedWithoutScores = standardizeFrame(train);
    long timeWithSMBO = System.currentTimeMillis() - start1;
    totalTimeStandardisation += timeWithSMBO;
    
    trainStandardisedWithoutScores.add("score", train.vec("score"));
    trainStandardisedWithoutScores.add("id", train.vec("id"));
    
    printOutFrameAsTable(trainStandardisedWithoutScores);

    double[][] observedData = TargetEncoderFrameHelper.frameAsArray(trainStandardisedWithoutScores, new String[]{"score", "id"});
    DoubleMatrix observedDataDM = new DoubleMatrix(observedData);

    double[][] observedMeans = TargetEncoderFrameHelper.frameAsArray(new Frame(trainStandardisedWithoutScores.vec("score")), new String[]{});
    DoubleMatrix observedMeansDM = new DoubleMatrix(observedMeans).transpose();

    double[][] unObservedData = TargetEncoderFrameHelper.frameAsArray(hyperparameters, new String[]{"id"});
    DoubleMatrix unObservedDataDM = new DoubleMatrix(unObservedData);

    //TODO too slow to do it on every step. Iterative method is needed (gradient descent)
    long numberOfTrainingRows = train.numRows();
    if( _currentBestSigma < 0 || numberOfTrainingRows % ( numberOfTrainingRows < 30 ? 5 : 10) == 0) {
      double[] sigmaEll = gridSearchOverGPsHyperparameters(observedDataDM, observedMeansDM);
      _currentBestSigma = sigmaEll[0];
      _currentBestEll = sigmaEll[1]; //TODO we can use  average of values to prevent outliers to impact 10 runs
    }

    covariance = getCovarianceMtxWithGaussianKernel(_currentBestSigma, _currentBestEll, observedDataDM, observedDataDM);
    
    MeanVariance meanVariance = posteriorMeanAndVariance(_currentBestSigma, _currentBestEll, covariance, observedDataDM, unObservedDataDM, observedMeansDM);
    
    hyperparameters.add("prediction", Vec.makeVec(meanVariance.mean.data, Vec.newKey()));
    hyperparameters.add("variance", Vec.makeVec(meanVariance.variance.data, Vec.newKey()));
    
    return hyperparameters;
  }
  
  double[] gridSearchOverGPsHyperparameters(DoubleMatrix observedDataDM, DoubleMatrix observedMeansDM) {
    double argMaxSigma = 0.1;
    double argMaxEll = 0.1;
    double mll = Double.NEGATIVE_INFINITY;
    
    for (double sigma = 0.4; sigma < 0.9; sigma+=0.1) {
      for (double ell = 1.5; ell < 3.2; ell+=0.1) {
        
        int n = observedDataDM.length; 

        DoubleMatrix K = getCovarianceMtxWithGaussianKernel(sigma, ell, observedDataDM, observedDataDM);
        
        // r = -1/2*t(y)%*%solve(K)%*%y - 1/2*log(det(K)) - n/2*log(pi*2)
        Decompose.QRDecomposition<DoubleMatrix> qr = Decompose.qr(K);
        double determinant = 1;
        for (double diagElem : qr.r.diag().data) {
          determinant *= diagElem;
        }
        double det_K = Math.abs(determinant);
        DoubleMatrix firstTerm = observedMeansDM.transpose().mul(-0.5).mmul(Solve.solve(K, eye(K.columns))).mmul(observedMeansDM);
        double r = firstTerm.get(0) - 0.5 * Math.log(det_K) - (n/2) * Math.log(Math.PI*2);
        if(r > mll) {
          mll = r;
          argMaxSigma = sigma;
          argMaxEll = ell;
        }
      }
    }
    
    return new double[] {argMaxSigma, argMaxEll};
  }

  public static void printOutFrameAsTable(Frame fr) {
    printOutFrameAsTable(fr, false, fr.numRows());
  }

  public static void printOutFrameAsTable(Frame fr, boolean rollups, long limit) {
    assert limit <= Integer.MAX_VALUE;
    TwoDimTable twoDimTable = fr.toTwoDimTable(0, (int) limit, rollups);
    System.out.println(twoDimTable.toString(2, true));
  }

  void multilinePrint(DoubleMatrix matrix) {
    for (int i = 0; i < matrix.rows; i++) {
      matrix.getRow(i).print();
    }
  }
}
