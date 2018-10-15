package hex.glm;

import hex.CreateFrame;
import hex.DataInfo;
import hex.SplitFrame;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.Frame;
import water.util.Log;
import water.util.RandomUtils;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertEquals;

// Want to test the following:
// 1. make sure gradient calculation is correct
// 2. for Binomial, compare ordinal result with the one for binomial
// 3. make sure h2o predict, mojo and pojo predict all agrees
public class GLMBasicTestOrdinal extends TestUtil {
  private static final double _tol = 1e-10;   // threshold for comparison

  @BeforeClass public static void stall() { stall_till_cloudsize(1); }

  private void convert2Enum(Frame f, int[] cols) {
    for (int col : cols) {
      f.replace(col, f.vec(col).toCategoricalVec()).remove();
    }
    DKV.put(f);
  }

  // Ordinal regression with class = 2 defaults to binomial.  Hence, they should have the same gradients at the
  // beginning of a run.
  @Test
  public void testCheckGradientBinomial() {
    try {
      Scope.enter();
      Frame trainBinomialEnum = parse_test_file("smalldata/glm_ordinal_logit/ordinal_binomial_training_set_enum_small.csv");
      convert2Enum(trainBinomialEnum, new int[]{0, 1, 2, 3, 4, 5, 6, 34}); // convert enum columns
      Frame trainBinomial = parse_test_file("smalldata/glm_ordinal_logit/ordinal_binomial_training_set_small.csv");
      convert2Enum(trainBinomial, new int[]{34});
      Scope.track(trainBinomialEnum);
      Scope.track(trainBinomial);
      checkGradientWithBinomial(trainBinomial, 34, "C35"); // only numerical columns
      checkGradientWithBinomial(trainBinomialEnum, 34, "C35"); // with enum and numerical columns
    } finally {
      Scope.exit();
    }
  }

  // test and make sure the h2opredict, pojo and mojo predict agrees with multinomial dataset that includes
  // both enum and numerical datasets
  @Test
  public void testOrdinalPredMojoPojo() {
    testOrdinalMojoPojo(GLMModel.GLMParameters.Solver.AUTO);
    testOrdinalMojoPojo(GLMModel.GLMParameters.Solver.GRADIENT_DESCENT_SQERR);
  }

  public void testOrdinalMojoPojo(GLMModel.GLMParameters.Solver sol) {
    try {
      Scope.enter();
      CreateFrame cf = new CreateFrame();
      Random generator = new Random();
      int numRows = generator.nextInt(10000)+15000+200;
      int numCols = generator.nextInt(17)+3;
      int response_factors = generator.nextInt(7)+3;
      cf.rows= numRows;
      cf.cols = numCols;
      cf.factors=10;
      cf.has_response=true;
      cf.response_factors = response_factors;
      cf.positive_response=true;
      cf.missing_fraction = 0;
      cf.seed = System.currentTimeMillis();
      System.out.println("Createframe parameters: rows: "+numRows+" cols:"+numCols+" response number:"
              +response_factors+" seed: "+cf.seed);

      Frame trainMultinomial = Scope.track(cf.execImpl().get());
      SplitFrame sf = new SplitFrame(trainMultinomial, new double[]{0.8,0.2}, new Key[] {Key.make("train.hex"), Key.make("test.hex")});
      sf.exec().get();
      Key[] ksplits = sf._destination_frames;
      Frame tr = DKV.get(ksplits[0]).get();
      Frame te = DKV.get(ksplits[1]).get();
      Scope.track(tr);
      Scope.track(te);

      GLMModel.GLMParameters paramsO = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.ordinal,
              GLMModel.GLMParameters.Family.ordinal.defaultLink, new double[]{0}, new double[]{0}, 0, 0);
      paramsO._train = tr._key;
      paramsO._lambda_search = false;
      paramsO._response_column = "response";
      paramsO._lambda = new double[]{0};
      paramsO._alpha = new double[]{0.001};  // l1pen
      paramsO._objective_epsilon = 1e-6;
      paramsO._beta_epsilon = 1e-4;
      paramsO._standardize = false;
      paramsO._solver = sol;
      GLMModel model = new GLM(paramsO).trainModel().get();
      Scope.track_generic(model);
      Frame pred = model.score(te);
      Scope.track(pred);
      Assert.assertTrue(model.testJavaScoring(te, pred, _tol));
    } finally {
      Scope.exit();
    }
  }

  // test ordinal regression with few iterations to make sure our gradient calculation and update is correct
  // for ordinals with multinomial data.  Ordinal regression coefficients are compared with ones calcluated using
  // alternate calculation without the distributed framework.  The datasets contains only numerical columns.
  @Test
  public void testOrdinalMultinomial() {
    try {
      Scope.enter();
      Frame trainMultinomial = Scope.track(parse_test_file("smalldata/glm_ordinal_logit/ordinal_multinomial_training_set_small.csv"));
      convert2Enum(trainMultinomial, new int[]{25});

      final int iterNum = new Random().nextInt(10) + 2;   // number of iterations to test
      Log.info("testOrdinalMultinomial will use iterNum = " + iterNum);

      GLMModel.GLMParameters paramsO = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.ordinal,
              GLMModel.GLMParameters.Family.ordinal.defaultLink, new double[]{0}, new double[]{0}, 0, 0);
      paramsO._train = trainMultinomial._key;
      paramsO._lambda_search = false;
      paramsO._response_column = "C26";
      paramsO._lambda = new double[]{1e-6};  // no regularization here
      paramsO._alpha = new double[]{1e-5};  // l1pen
      paramsO._objective_epsilon = 1e-6;
      paramsO._beta_epsilon = 1e-4;
      paramsO._max_iterations = iterNum;
      paramsO._standardize = false;
      paramsO._seed = 987654321;
      paramsO._obj_reg = 1e-7;

      GLMModel model = new GLM(paramsO).trainModel().get();
      Scope.track_generic(model);

      double[] interceptPDF = model._ymu;
      double[][] coeffs = model._output._global_beta_multinomial; // class by npred
      double[] beta = new double[coeffs[0].length - 1]; // coefficients not including the intercepts
      double[] icpt = new double[coeffs.length - 1]; // all the intercepts
      updateOrdinalCoeff(trainMultinomial, 25, paramsO, interceptPDF, coeffs[0].length,
              Integer.parseInt(model._output._model_summary.getCellValues()[0][5].toString()), beta, icpt);
      compareMultCoeffs(coeffs, beta, icpt);  // compare and check coefficients agree
    } finally {
      Scope.exit();
    }
  }

  public void compareMultCoeffs(double[][] coeffs, double[] beta, double[] icpt) {
    for (int predInd = 0; predInd < beta.length; predInd++)  // compare non-intercept parameters
      assertEquals(coeffs[0][predInd], beta[predInd], _tol);

    for (int icptInd = 0; icptInd < icpt.length; icptInd++)
      assertEquals(coeffs[icptInd][beta.length], icpt[icptInd], _tol);
  }

  // original version
  public void updateOrdinalCoeff(Frame fr, int respCol, GLMModel.GLMParameters params, double[] icptPDF,
                                 int npred, int numRuns, double[] beta, double[] intercpts) {
    int nclass = icptPDF.length;  // number of class of response variable
    int lastClass = nclass-1;
    int lastPred = npred-1;

    double[] betaGrad = new double[lastPred]; // store gradient calculation of non-intercepts
    double[] icptGrad = new double[lastClass];  // store gradient calculation of intercepts
    double multiplier = 0.0;
    double[] multiplierI = new double[2];
    double l2pen = params._lambda[0]*(1-params._alpha[0]);
    double l1pen = params._lambda[0]*params._alpha[0];
    double reg = params._obj_reg;
    Random rng = RandomUtils.getRNG(params._seed);
    double[] tempIcpt = new double[lastClass];
    for (int i = 0; i < lastClass; i++) {  // only contains nclass-2 thresholds here
      tempIcpt[i] = (-1+2*rng.nextDouble()) * nclass;
    }
    Arrays.sort(tempIcpt);

    for (int index = 0; index < lastClass; index++) { // initialize intercept of beta values
      intercpts[index] = tempIcpt[index];
    }

    int rowNum = (int) fr.numRows();
    for (int iter=0; iter < numRuns; iter++) {  // for each iteration
      for (int row = 0; row < rowNum; row++) {  // go through each row and update the gradient information
        int yresp = (int)fr.vec(respCol).at(row); // get response class
        Arrays.fill(multiplierI, 0.0);
        if (yresp == 0) { // yresponse is class 0
          multiplier = getCDF(fr, row, beta, intercpts, yresp)-1;
          multiplierI[0] = multiplier;
        } else if (yresp == lastClass) {  // yresponse is last class
          multiplier = getCDF(fr, row, beta, intercpts, yresp-1);
          multiplierI[0] = multiplier;
        } else {  // response is between 1 and class-2
          int pC = yresp-1;
          double cdfC = getCDF(fr, row, beta, intercpts, yresp);
          double cdfPC = getCDF(fr, row, beta, intercpts, pC);
          multiplier = cdfC+cdfPC-1;
          double delta = cdfC-cdfPC;
          double oneODelta = 1.0/(delta==0?1e-10:delta);
          multiplierI[0] = -getCDFDeriv(cdfC)*oneODelta;
          multiplierI[1] = getCDFDeriv(cdfPC)*oneODelta;
        }
        if (multiplier != 0.0) {
          for (int predInd = 0; predInd < lastPred; predInd++) { // calculate gradient of non-intercept coefficients
            betaGrad[predInd] += multiplier * fr.vec(predInd).at(row);
          }
        }

        if (yresp < lastClass) {
          icptGrad[yresp] += multiplierI[0];  // calculate gradient of intercept terms
          if (yresp > 0) {
            icptGrad[yresp - 1] += multiplierI[1];
          }
        } else {  // yresp = C-1
          icptGrad[yresp-1] += multiplierI[0];
        }
      }
      addGradChange(beta, betaGrad, intercpts, icptGrad, l2pen, l1pen, reg);
      Arrays.fill(betaGrad, 0.0);
      Arrays.fill(icptGrad, 0.0);
    }
  }

  public void addGradChange(double[] beta, double[] betaGrad, double[] icpt, double[] icptGrad,
                            double l2pen, double l1pen, double reg) {

    int npred = beta.length;
    int nicpt = icpt.length;

    for (int npredInd = 0; npredInd < npred; npredInd++) {
      betaGrad[npredInd] *= reg;
      betaGrad[npredInd] += l2pen*beta[npredInd]; // add L2pen
      betaGrad[npredInd] += l1pen==0.0?0:(beta[npredInd] > 0?l1pen:-l1pen);
      beta[npredInd] -= betaGrad[npredInd];
    }

    for (int icptInd = 0; icptInd < nicpt; icptInd++) {
      icpt[icptInd] -= icptGrad[icptInd]*reg;
    }
  }

  double getCDFDeriv(double x) {
    return x*(1-x);
  }

  // Calculate the CDF of class specified in respClass
  double getCDF(Frame fr, int row, double[] beta, double[] icpt, int respClass) {
    int colNum = fr.numCols()-1;  // exclude the response column
    double eta = 0;
    for (int colInd = 0; colInd < colNum; colInd++) {
      eta += beta[colInd]*fr.vec(colInd).at(row);
    }
    if (respClass < icpt.length) {
      eta += icpt[respClass];
      double expEta = Math.exp(eta);
      return expEta/(1+expEta);
    } else {
      eta += icpt[icpt.length - 1];
      double expEta = Math.exp(eta);
      return (1-expEta/(1+expEta));
    }
  }

  public void checkGradientWithBinomial(Frame fr, int respCol, String resp) {
    DataInfo dinfo=null;
    DataInfo odinfo = null;
    try {
      int nclasses = fr.vec(respCol).domain().length;
      GLMModel.GLMParameters params = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.binomial,
              GLMModel.GLMParameters.Family.binomial.defaultLink, new double[]{0}, new double[]{0}, 0, 0);
      // params._response = fr.find(params._response_column);
      params._train = fr._key;
      params._lambda = new double[]{0.0001};
      params._alpha = new double[]{0.5};
      params._lambda_search = false;
      params._response_column = resp;
      params._obj_reg = 1e-5;
      double lAmbda = 0.0001;
      dinfo = new DataInfo(fr, null, 1,
              params._use_all_factor_levels || params._lambda_search,
              params._standardize ? DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE,
              DataInfo.TransformType.NONE, true, false, false,
              false, false, false);
      DKV.put(dinfo._key, dinfo);
      GLMModel.GLMParameters paramsO = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.ordinal,
              GLMModel.GLMParameters.Family.ordinal.defaultLink, new double[]{0}, new double[]{0}, 0, 0);
      paramsO._train = fr._key;
      paramsO._lambda = new double[]{0.0001};
      paramsO._lambda_search = false;
      paramsO._response_column = resp;
      paramsO._alpha = new double[]{0.5};
      paramsO._obj_reg = params._obj_reg;
      odinfo = new DataInfo(fr, null, 1,
              paramsO._use_all_factor_levels || paramsO._lambda_search,
              paramsO._standardize ? DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE,
              DataInfo.TransformType.NONE, true, false, false,
              false, false, false);
      DKV.put(odinfo._key, odinfo);
      double[][] _betaMultinomial = new double[nclasses][];
      for (int i = 0; i < nclasses; ++i)
        _betaMultinomial[i] = MemoryManager.malloc8d(odinfo.fullN() + 1);
      double[] beta = new double[_betaMultinomial[0].length];

      GLMTask.GLMGradientTask grBinomial = new GLMTask.GLMBinomialGradientTask(null, dinfo, params,
              lAmbda, beta).doAll(dinfo._adaptedFrame);
      GLMTask.GLMMultinomialGradientBaseTask grOrdinal = new GLMTask.GLMMultinomialGradientTask(null, odinfo, lAmbda,
              _betaMultinomial, paramsO).doAll(odinfo._adaptedFrame);
      compareBinomalOrdinalGradients(grBinomial, grOrdinal);  // compare and make sure the two gradients agree

    } finally {
      dinfo.remove();
      odinfo.remove();
    }
  }

  public void compareBinomalOrdinalGradients(GLMTask.GLMGradientTask bGr, GLMTask.GLMMultinomialGradientBaseTask oGr) {
    // compare likelihood
    assertEquals(bGr._likelihood, oGr._likelihood, _tol);

    // compare gradients
    double[] binomialG = bGr._gradient;
    double[] ordinalG = oGr.gradient();

    for (int index = 0; index < binomialG.length; index++) {
      assertEquals(binomialG[index], -ordinalG[index], _tol);
    }
  }
}
