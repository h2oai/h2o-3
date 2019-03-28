//package ai.h2o.automl.hpsearch;
//
//import ai.h2o.automl.targetencoding.TargetEncoderFrameHelper;
//import hex.splitframe.ShuffleSplitFrame;
//import org.jblas.DoubleMatrix;
//import org.jblas.Geometry;
//import org.jblas.Solve;
//import water.Key;
//import water.fvec.Frame;
//import water.fvec.Vec;
//import water.util.TwoDimTable;
//
//import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.frameRowAsArray;
//import static org.jblas.DoubleMatrix.eye;
//import static org.jblas.MatrixFunctions.exp;
//
///**
// * This surrogate model is based on Gaussian multivariate process regression
// */
//public class GPSurrogateModel_V2 extends SurrogateModel{
//
//  private DoubleMatrix covariance = null;
//  DoubleMatrix gaussianKernel(double w, DoubleMatrix x1, DoubleMatrix x2) {
//    DoubleMatrix d = Geometry.pairwiseSquaredDistances(x1, x2);
//    return exp(d.div(w).neg());
//  }
//
//  public static class MeanVariance {
//    private DoubleMatrix mean;
//    private DoubleMatrix variance;
//    private DoubleMatrix posteriorCovariance; //posterior -> prior kernel
//
//    public MeanVariance(DoubleMatrix mean, DoubleMatrix variance, DoubleMatrix posteriorCovariance) {
//      this.mean = mean;
//      this.variance = variance;
//      this.posteriorCovariance = posteriorCovariance;
//    }
//
//
//  }
//  //  K0 + sigma^2*I    K1
//  //  K2                K3
//  //
//  // 
//  MeanVariance posteriorMeanAndVariance(double w, DoubleMatrix covariancePrior, DoubleMatrix observedData, DoubleMatrix newObservation, DoubleMatrix priorMeans) {
//    double sigma = 2.7;
//    int n = observedData.columns;
//    assert priorMeans.rows == n;
//
//    DoubleMatrix priorWithNoise = covariancePrior.addi(eye(n).muli(sigma * sigma)); // K0 + sigma^2*I
////    DoubleMatrix priorWithNoise = /*gaussianKernel(w, kernel, kernel)*/kernel.addi(eye(n).muli(sigma)); // K0 + sigma^2*I
//
//    DoubleMatrix covarianceBetweenNewAndPrior = gaussianKernel(w, observedData, newObservation);   // K1
//    DoubleMatrix K2 = covarianceBetweenNewAndPrior.transpose();
//    DoubleMatrix varianceForNewObservation = gaussianKernel(w, newObservation, newObservation);
//
//    DoubleMatrix K = combineKMatrices(covariancePrior, covarianceBetweenNewAndPrior, K2, varianceForNewObservation);
//
//    // mt(x) = K(X*, Xt) * [K(Xt , Xt) + σ^2 * I ]^-1 * yt 
//    //
//    //           ^^                ^^                 ^^
//    //           K2                 b                  c (= priorMeans) 
//    DoubleMatrix b = Solve.solve(priorWithNoise, eye(n)); // solve with Identity B matrix in Ax=B will return us inverse.
//
//    DoubleMatrix ab = K2.mmul(b);
//    DoubleMatrix posteriorMeanMatrix = ab.mmul(priorMeans);
//
//    // Sigma = K(X*, X*) − K(X*, Xt) [ K(Xt , Xt) + σ^2 *I]^−1 * K(Xt , X*)
//    //
//    //           ^^                ^^      ^^
//    // varianceForNewObservation   K2      b                  K1(=covarianceBetweenNewAndPrior) 
//
//    DoubleMatrix varianceMatrix = varianceForNewObservation.sub(ab.mmul(covarianceBetweenNewAndPrior));
//
//    return new MeanVariance(posteriorMeanMatrix, varianceMatrix.diag(), K); //TODO not sure that we need to return only diag elements
//  }
//
//  private DoubleMatrix combineKMatrices(DoubleMatrix covariancePrior, DoubleMatrix covarianceBetweenNewAndPrior, DoubleMatrix k2, DoubleMatrix varianceForNewObservation) {
//    DoubleMatrix K_UPPER = DoubleMatrix.concatHorizontally(covariancePrior, covarianceBetweenNewAndPrior);
//    DoubleMatrix K_LOWER = DoubleMatrix.concatHorizontally(k2, varianceForNewObservation);
//    return DoubleMatrix.concatVertically(K_UPPER, K_LOWER);
//  }
//
//  @Override
//  public Frame evaluate(Frame hyperparameters, Frame train) {
//    Frame withoutLast = null;
//    if(covariance != null) {
//      long[] indexesToSlice = new long[(int) train.numRows() - 1];
//      for (int i = 0; i < (int) train.numRows() - 1; i++) {
//        indexesToSlice[0] = i;
//      }
//      withoutLast = train.deepSlice(indexesToSlice, null);
//    } else {
//      withoutLast = train;
//    }
//
//    double[][] observedData = TargetEncoderFrameHelper.frameAsArray(train, new String[]{"score", "id"});
//    DoubleMatrix observedDataDM = new DoubleMatrix(observedData);
//
//    double[][] observedMeans = TargetEncoderFrameHelper.frameAsArray(new Frame(train.vec("score")), new String[]{});
//    DoubleMatrix observedMeansDM = new DoubleMatrix(observedMeans).transpose();
//
//    double[][] unObservedData = TargetEncoderFrameHelper.frameAsArray(hyperparameters, new String[]{"id"});
//    DoubleMatrix unObservedDataDM = new DoubleMatrix(unObservedData);
//
//    int w = 1;
//    
////    if(covariance == null)
//    if(true)
//      covariance = gaussianKernel(w, observedDataDM, observedDataDM);
//    else {
//      double[][] observedDataLast = TargetEncoderFrameHelper.frameAsArray(withoutLast, new String[]{"score", "id"});
//      DoubleMatrix observedDataLastDM = new DoubleMatrix(observedDataLast);
//      double[] data = frameRowAsArray(train, (int)train.numRows() - 1, new String[]{"id", "score"});
//      DoubleMatrix lastEvaluatedOnOFObservation = new DoubleMatrix(data);
//      DoubleMatrix covarianceBetweenNewAndPrior = gaussianKernel(w, observedDataLastDM, lastEvaluatedOnOFObservation);   // K1
//      DoubleMatrix K2 = covarianceBetweenNewAndPrior.transpose();
//      DoubleMatrix varianceForNewObservation = gaussianKernel(w, lastEvaluatedOnOFObservation, lastEvaluatedOnOFObservation);
//      covariance = combineKMatrices(covariance, covarianceBetweenNewAndPrior, K2, varianceForNewObservation);
//    }
//    MeanVariance meanVariance = posteriorMeanAndVariance(w, covariance, observedDataDM, unObservedDataDM, observedMeansDM);
//    
//    // TODO make sure that predictions(means) are aligned with variance values
//    hyperparameters.add("prediction", Vec.makeVec(meanVariance.mean.data, Vec.newKey()));
//    hyperparameters.add("variance", Vec.makeVec(meanVariance.variance.data, Vec.newKey()));
//    
//    return hyperparameters;
//  }
//
//  private Frame[] splitByRatio(Frame frame, double first, double second, long seed) {
//    double[] ratios = new double[]{first, second};
//    Key<Frame>[] keys = new Key[] {Key.make(), Key.make()};
//    Frame[] splits = null;
//    splits = ShuffleSplitFrame.shuffleSplitFrame(frame, keys, ratios, seed);
//    return splits;
//  }
//
//  public static void printOutFrameAsTable(Frame fr) {
//    printOutFrameAsTable(fr, false, fr.numRows());
//  }
//
//  public static void printOutFrameAsTable(Frame fr, boolean rollups, long limit) {
//    assert limit <= Integer.MAX_VALUE;
//    TwoDimTable twoDimTable = fr.toTwoDimTable(0, (int) limit, rollups);
//    System.out.println(twoDimTable.toString(2, true));
//  }
//
//  void multilinePrint(DoubleMatrix matrix) {
//    for (int i = 0; i < matrix.rows; i++) {
//      matrix.getRow(i).print();
//    }
//  }
//}
