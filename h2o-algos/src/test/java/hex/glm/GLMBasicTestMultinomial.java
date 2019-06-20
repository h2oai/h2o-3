package hex.glm;

import hex.CreateFrame;
import hex.DataInfo;
import hex.FrameSplitter;
import hex.ModelMetricsBinomialGLM.ModelMetricsMultinomialGLM;
import hex.SplitFrame;
import hex.deeplearning.DeepLearningModel;
import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.glm.GLMModel.GLMParameters.Solver;
import hex.optimization.ADMM;
import org.junit.*;
import org.junit.rules.ExpectedException;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by tomasnykodym on 10/28/15.
 */
public class GLMBasicTestMultinomial extends TestUtil {
  static Frame _covtype;
  static Frame _train;
  static Frame _test;
  double _tol = 1e-10;

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
    _covtype = parse_test_file("smalldata/covtype/covtype.20k.data");
    _covtype.replace(_covtype.numCols()-1,_covtype.lastVec().toCategoricalVec()).remove();
    Key[] keys = new Key[]{Key.make("train"),Key.make("test")};
    H2O.submitTask(new FrameSplitter(_covtype, new double[]{.8},keys,null)).join();
    _train = DKV.getGet(keys[0]);
    _test = DKV.getGet(keys[1]);
  }

  @AfterClass
  public static void cleanUp() {
    if(_covtype != null)  _covtype.delete();
    if(_train != null) _train.delete();
    if(_test != null) _test.delete();
  }

  @Test
  public void testMultinomialPredMojoPojo() {
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

      GLMModel.GLMParameters paramsO = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.multinomial,
              Family.multinomial.defaultLink, new double[]{0}, new double[]{0}, 0, 0);
      paramsO._train = tr._key;
      paramsO._lambda_search = false;
      paramsO._response_column = "response";
      paramsO._lambda = new double[]{0};
      paramsO._alpha = new double[]{0.001};  // l1pen
      paramsO._objective_epsilon = 1e-6;
      paramsO._beta_epsilon = 1e-4;
      paramsO._standardize = false;

      GLMModel model = new GLM(paramsO).trainModel().get();
      Scope.track_generic(model);

      Frame pred = model.score(te);
      Scope.track(pred);
      Assert.assertTrue(model.testJavaScoring(te, pred, _tol));
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testCovtypeNoIntercept(){
    GLMParameters params = new GLMParameters(Family.multinomial);
    GLMModel model = null;
    Frame preds = null;
    Vec weights = _covtype.anyVec().makeCon(1);
    Key k = Key.<Frame>make("cov_with_weights");
    Frame f = new Frame(k,_covtype.names(),_covtype.vecs());
    f.add("weights",weights);
    DKV.put(f);
    try {
      params._response_column = "C55";
      params._train = k;
      params._valid = _covtype._key;
      params._objective_epsilon = 1e-6;
      params._beta_epsilon = 1e-4;
      params._weights_column = "weights";
      params._missing_values_handling = DeepLearningModel.DeepLearningParameters.MissingValuesHandling.Skip;
      params._intercept = false;
      double[] alpha = new double[]{0,.5,.1};
      Solver s = Solver.L_BFGS;
      System.out.println("solver = " + s);
      params._solver = s;
      params._max_iterations = 5000;
      for (int i = 0; i < alpha.length; ++i) {
        params._alpha = new double[]{alpha[i]};
//        params._lambda[0] = lambda[i];
        model = new GLM(params).trainModel().get();
        System.out.println(model.coefficients());
//        Assert.assertEquals(0,model.coefficients().get("Intercept"),0);
        double [][] bs = model._output.getNormBetaMultinomial();
        for(double [] b:bs)
          Assert.assertEquals(0,b[b.length-1],0);
        System.out.println(model._output._model_summary);
        System.out.println(model._output._training_metrics);
        System.out.println(model._output._validation_metrics);
        preds = model.score(_covtype);
        ModelMetricsMultinomialGLM mmTrain = (ModelMetricsMultinomialGLM) hex.ModelMetricsMultinomial.getFromDKV(model, _covtype);
        assertTrue(model._output._training_metrics.equals(mmTrain));
        model.delete();
        model = null;
        preds.delete();
        preds = null;
      }
    } finally{
      weights.remove();
      DKV.remove(k);
      if(model != null)model.delete();
      if(preds != null)preds.delete();
    }
  }


  @Test
  public void testCovtypeBasic(){
    GLMParameters params = new GLMParameters(Family.multinomial);
    GLMModel model = null;
    Frame preds = null;
    Vec weights = _covtype.anyVec().makeCon(1);
    Key k = Key.<Frame>make("cov_with_weights");
    Frame f = new Frame(k,_covtype.names(),_covtype.vecs());
    f.add("weights",weights);
    DKV.put(f);
    try {
      params._response_column = "C55";
      params._train = k;
      params._valid = _covtype._key;
      params._lambda = new double[]{4.881e-05};
      params._alpha = new double[]{1};
      params._objective_epsilon = 1e-6;
      params._beta_epsilon = 1e-4;
      params._weights_column = "weights";
      params._missing_values_handling = DeepLearningModel.DeepLearningParameters.MissingValuesHandling.Skip;
      double[] alpha = new double[]{1};
      double[] expected_deviance = new double[]{25499.76};
      double[] lambda = new double[]{2.544750e-05};
      for (Solver s : new Solver[]{Solver.IRLSM, Solver.COORDINATE_DESCENT, Solver.L_BFGS}) {
        System.out.println("solver = " + s);
        params._solver = s;
        params._max_iterations = params._solver == Solver.L_BFGS?300:10;
        for (int i = 0; i < alpha.length; ++i) {
          params._alpha[0] = alpha[i];
          params._lambda[0] = lambda[i];
          model = new GLM(params).trainModel().get();
          System.out.println(model._output._model_summary);
          System.out.println(model._output._training_metrics);
          System.out.println(model._output._validation_metrics);
          assertTrue(model._output._training_metrics.equals(model._output._validation_metrics));
          assertTrue(((ModelMetricsMultinomialGLM) model._output._training_metrics)._resDev <= expected_deviance[i] * 1.1);
          preds = model.score(_covtype);
          ModelMetricsMultinomialGLM mmTrain = (ModelMetricsMultinomialGLM) hex.ModelMetricsMultinomial.getFromDKV(model, _covtype);
          assertTrue(model._output._training_metrics.equals(mmTrain));
          model.delete();
          model = null;
          preds.delete();
          preds = null;
        }
      }
    } finally{
      weights.remove();
      DKV.remove(k);
      if(model != null)model.delete();
      if(preds != null)preds.delete();
    }
  }

  /*
  I have manually derived the coefficient updates for COD and they are more accurate than what is currently
  implemented because I update all the probabilities after a coefficient has been changed.  In reality, this will
  be very slow and an approximation may be more appropriate.  The coefficients generated here is the golden standard.
   */
  @Test
  public void testCODGradients(){
    Scope.enter();
    Frame train;
    GLMParameters params = new GLMParameters(Family.multinomial);
    GLMModel model = null;
    double[] oldGLMCoeffs = new double[] {0.059094274726151426, 0.013361781886804975, -0.00798977427248744,
            0.007467359562151555, 0.06737827548293934, -1.002393430927568, -0.04066511294457045, -0.018960901996125427,
            0.07330281133353159, -0.02285669809606731, 0.002805290931441751, -1.1394632268347782, 0.021976767313534512,
            0.01013967640490087, -0.03999288928633559, 0.012385348397898913, -0.0017922461738315199,
            -1.159667420372168};
    try {
      train = parse_test_file("smalldata/glm_test/multinomial_3_class.csv");
      Scope.track(train);
      params._response_column = "response";
      params._train = train._key;
      params._lambda = new double[]{4.881e-05};
      params._alpha = new double[]{0.5};
      params._objective_epsilon = 1e-6;
      params._beta_epsilon = 1e-4;
      params._max_iterations = 1; // one iteration
      params._seed = 12345; // don't think this one matters but set it anyway
      Solver s = Solver.COORDINATE_DESCENT;
      System.out.println("solver = " + s);
      params._solver = s;
      model = new GLM(params).trainModel().get();
      Scope.track_generic(model);
      DataInfo tinfo = new DataInfo(train.clone(), null, 0, true, DataInfo.TransformType.STANDARDIZE,
              DataInfo.TransformType.NONE, false, false, false,
              /* weights */ false, /* offset */ false, /* fold */ false);
      double[] manualCoeff = getCODCoeff(train, params._alpha[0], params._lambda[0], model._ymu, tinfo);
      Scope.track_generic(tinfo);

      compareGLMCoeffs(manualCoeff, model._output._submodels[0].beta, 2e-2);  // compare two sets of coeffs
      compareGLMCoeffs(model._output._submodels[0].beta, oldGLMCoeffs, 1e-10);  // compare to original GLM

    } finally{
      Scope.exit();
    }
  }

  public void compareGLMCoeffs(double[] coeff1, double[] coeff2, double tol) {

    assertTrue(coeff1.length==coeff2.length); // assert coefficients having the same length first
    for (int index=0; index < coeff1.length; index++) {
      assert Math.abs(coeff1[index]-coeff2[index]) < tol :
              "coefficient difference "+Math.abs(coeff1[index]-coeff2[index])+" exceeded tolerance of "+tol;
    }
  }

  public double[] getCODCoeff(Frame train, double alpha, double lambda, double[] ymu, DataInfo tinfo) {
    int numClass = train.vec("response").domain().length;
    int numPred = train.numCols() - 1;
    int numRow = (int) train.numRows();
    double[] beta = new double[numClass * (numPred + 1)];
    double reg = 1.0/train.numRows();

    // initialize beta
    for (int index = 0; index < numClass; index++) {
      beta[(index + 1) * (numPred + 1) - 1] = Math.log(ymu[index]);
    }

    for (int iter = 0; iter < 3; iter++) {
      // update beta
      for (int cindex = 0; cindex < numClass; cindex++) {
        for (int pindex = 0; pindex < numPred; pindex++) {
          double grad = 0;
          double hess = 0;

          for (int rindex = 0; rindex < numRow; rindex++) {
            int resp = (int) train.vec("response").at(rindex);
            double predProb = calProb(train, rindex, beta, numClass, numPred, cindex, tinfo);
            double entry = (train.vec(pindex).at(rindex) - tinfo._numMeans[pindex]) * tinfo._normMul[pindex];
            grad -= entry * ((resp == cindex ? 1 : 0) - predProb);
            hess += entry * entry * (predProb - predProb * predProb); // hess calculation is correct
          }
          grad = grad * reg + lambda * (1 - alpha) * beta[cindex * (numPred + 1) + pindex]; // add l2 penalty
          hess = hess * reg + lambda * (1 - alpha);
          beta[cindex * (numPred + 1) + pindex] -= ADMM.shrinkage(grad, lambda * alpha) / hess;
        }

        double grad = 0;
        double hess = 0;
        // change the intercept term here
        for (int rindex = 0; rindex < numRow; rindex++) {
          int resp = (int) train.vec("response").at(rindex);


          double predProb = calProb(train, rindex, beta, numClass, numPred, cindex, tinfo);
          grad -= ((resp == cindex ? 1 : 0) - predProb);
          hess += (predProb - predProb * predProb);

        }
        grad *= reg;
        hess *= reg;
        beta[(cindex + 1) * (numPred + 1) - 1] -= grad / hess;
      }
    }
    return beta;
  }

  public double calProb(Frame train, int rowIndex, double[] beta, int numClass, int numPred, int classNo, DataInfo tinfo) {
    double prob = 0.0;
    double sum = 0.0;
    for (int cindex = 0; cindex < numClass; cindex++) {
      double temp = 0;
      for (int pindex = 0; pindex < numPred; pindex++) {
        double entry = (train.vec(pindex).at(rowIndex)-tinfo._numMeans[pindex])*tinfo._normMul[pindex];
        temp += entry*beta[cindex*(numPred+1)+pindex];
      }
      temp+= beta[(cindex+1)*(numPred+1)-1];
      if (classNo == cindex) {
        prob = Math.exp(temp);
      }
      sum+= Math.exp(temp);
    }
    return (prob/sum);
  }


  @Test
  public void testCovtypeMinActivePredictors(){
    GLMParameters params = new GLMParameters(Family.multinomial);
    GLMModel model = null;
    Frame preds = null;
    try {
      params._response_column = "C55";
      params._train = _covtype._key;
      params._valid = _covtype._key;
      params._lambda = new double[]{4.881e-05};
      params._alpha = new double[]{1};
      params._objective_epsilon = 1e-6;
      params._beta_epsilon = 1e-4;
      params._max_active_predictors = 50;
      params._max_iterations = 10;
      double[] alpha = new double[]{.99};
      double expected_deviance = 33000;
      double[] lambda = new double[]{2.544750e-05};
      Solver s = Solver.COORDINATE_DESCENT;
      System.out.println("solver = " + s);
      params._solver = s;
      model = new GLM(params).trainModel().get();
      System.out.println(model._output._model_summary);
      System.out.println(model._output._training_metrics);
      System.out.println(model._output._validation_metrics);
      System.out.println("rank = " + model._output.rank() + ", max active preds = " + (params._max_active_predictors + model._output.nclasses()));
      assertTrue(model._output.rank() <= params._max_active_predictors + model._output.nclasses());
      assertTrue(model._output._training_metrics.equals(model._output._validation_metrics));
      assertTrue(((ModelMetricsMultinomialGLM) model._output._training_metrics)._resDev <= expected_deviance * 1.1);
      preds = model.score(_covtype);
      ModelMetricsMultinomialGLM mmTrain = (ModelMetricsMultinomialGLM) hex.ModelMetricsMultinomial.getFromDKV(model, _covtype);
      assertTrue(model._output._training_metrics.equals(mmTrain));
      model.delete();
      model = null;
      preds.delete();
      preds = null;
    } finally{
      if(model != null)model.delete();
      if(preds != null)preds.delete();
    }
  }


  @Test
  public void testCovtypeLS(){
    GLMParameters params = new GLMParameters(Family.multinomial);
    GLMModel model = null;
    Frame preds = null;
    try {
      double expected_deviance = 33000;
      params._nlambdas = 3;
      params._response_column = "C55";
      params._train = _covtype._key;
      params._valid = _covtype._key;
      params._alpha = new double[]{.99};
      params._objective_epsilon = 1e-6;
      params._beta_epsilon = 1e-4;
      params._max_active_predictors = 50;
      params._max_iterations = 500;
      params._solver = Solver.AUTO;
      params._lambda_search = true;
      model = new GLM(params).trainModel().get();
      System.out.println(model._output._training_metrics);
      System.out.println(model._output._validation_metrics);
      assertTrue(model._output._training_metrics.equals(model._output._validation_metrics));
      preds = model.score(_covtype);
      ModelMetricsMultinomialGLM mmTrain = (ModelMetricsMultinomialGLM) hex.ModelMetricsMultinomial.getFromDKV(model, _covtype);
      assertTrue(model._output._training_metrics.equals(mmTrain));
      assertTrue(((ModelMetricsMultinomialGLM) model._output._training_metrics)._resDev <= expected_deviance);
      System.out.println(model._output._model_summary);
      model.delete();
      model = null;
      preds.delete();
      preds = null;
    } finally{
      if(model != null)model.delete();
      if(preds != null)preds.delete();
    }
  }

  @Test
  public void testCovtypeNAs(){
    GLMParameters params = new GLMParameters(Family.multinomial);
    GLMModel model = null;
    Frame preds = null;
    Frame covtype_subset = null, covtype_copy = null;
    try {
      double expected_deviance = 26000;
      covtype_copy = _covtype.deepCopy("covtype_copy");
      DKV.put(covtype_copy);
      Vec.Writer w = covtype_copy.vec(54).open();
      w.setNA(10);
      w.setNA(20);
      w.setNA(30);
      w.close();
      covtype_subset = new Frame(Key.<Frame>make("covtype_subset"),new String[]{"C51","C52","C53","C54","C55"},covtype_copy.vecs(new int[]{50,51,52,53,54}));
      DKV.put(covtype_subset);
//      params._nlambdas = 3;
      params._response_column = "C55";
      params._train = covtype_copy._key;
      params._valid = covtype_copy._key;
      params._alpha = new double[]{.99};
      params._objective_epsilon = 1e-6;
      params._beta_epsilon = 1e-4;
      params._max_active_predictors = 50;
      params._max_iterations = 500;
      params._solver = Solver.L_BFGS;
      params._missing_values_handling = DeepLearningModel.DeepLearningParameters.MissingValuesHandling.Skip;
//      params._lambda_search = true;
      model = new GLM(params).trainModel().get();
      assertEquals(covtype_copy.numRows()-3-1,model._nullDOF);
      System.out.println(model._output._training_metrics);
      System.out.println(model._output._validation_metrics);
      assertTrue(model._output._training_metrics.equals(model._output._validation_metrics));
      preds = model.score(covtype_copy);
      ModelMetricsMultinomialGLM mmTrain = (ModelMetricsMultinomialGLM) hex.ModelMetricsMultinomial.getFromDKV(model, covtype_copy);
      assertTrue(model._output._training_metrics.equals(mmTrain));
      assertTrue(((ModelMetricsMultinomialGLM) model._output._training_metrics)._resDev <= expected_deviance);
      System.out.println(model._output._model_summary);
      model.delete();
      model = null;
      preds.delete();
      preds = null;
      // now run the same on the subset
      params._train = covtype_subset._key;
      model = new GLM(params).trainModel().get();
      assertEquals(covtype_copy.numRows()-3-1,model._nullDOF);
      System.out.println(model._output._training_metrics);
      System.out.println(model._output._validation_metrics);
      assertTrue(model._output._training_metrics.equals(model._output._validation_metrics));
      preds = model.score(_covtype);
      System.out.println(model._output._model_summary);
      assertTrue(((ModelMetricsMultinomialGLM) model._output._training_metrics)._resDev <= 66000);
      model.delete();
      model = null;
      preds.delete();
      preds = null;

    } finally{
      if(covtype_subset != null) covtype_subset.delete();
      if(covtype_copy != null)covtype_copy.delete();
      if(model != null)model.delete();
      if(preds != null)preds.delete();
    }
  }
  
  @Test
  public void testMultinomialOctave() {
    Scope.enter();
    try {
      Random generator = new Random();
      Frame fr = parse_test_file("/Users/wendycwong/temp/debug_multinomial_glm/multinomial_3_cols_num_3_class_20Rows.csv");
      Vec v = fr.remove("C4");
      fr.add("C4", v.toCategoricalVec());
      v.remove();
      Scope.track(fr);
      GLMParameters params = new GLMParameters(Family.multinomial);
      params._response_column = fr._names[fr.numCols()-1];
      params._ignored_columns = new String[]{};
      params._train = fr._key;
      params._lambda = new double[]{0.5};
      params._alpha = new double[]{0.5};
      params._solver = Solver.IRLSM_SPEEDUP;
      params._standardize = false;
      int nclass = 3;

      GLMModel.GLMWeightsFun glmw = new GLMModel.GLMWeightsFun(params);
      DataInfo dinfo = new DataInfo(fr, null, 1, true, DataInfo.TransformType.NONE,
              DataInfo.TransformType.NONE, true, false, false, false,
              false, false);
      int ncoeffPClass = dinfo.fullN()+1;
      double sumExp = 0;
      double[] beta = new double[]{0.387797, 0.269003, 0.444987, 0.403535, 0.129472, 0.826823, 0.221008, 0.402504, 
              0.378747, 0.137892, 0.129652, 0.024352};
/*      for (int ind = 0; ind < beta.length; ind++) {
        beta[ind] = generator.nextDouble();
      }*/
      int P = dinfo.fullN();       // number of predictors
      int N = dinfo.fullN() + 1;   // number of GLM coefficients per class
      for (int i = 1; i < nclass; ++i)
        sumExp += Math.exp(beta[i * N + P]);

      Vec [] vecs = dinfo._adaptedFrame.anyVec().makeDoubles(2, new double[]{sumExp,0});  // store sum exp and maxRow
      dinfo.addResponse(new String[]{"__glm_sumExp", "__glm_logSumExp"}, vecs);
      Scope.track(vecs[0]);
      Scope.track(vecs[1]);
      // calculate Hessian, xy and likelihood manually
      double[][] hessian = new double[beta.length][beta.length];
      double[] xy = new double[beta.length];
      double manualLLH = manualHessianXYLLH(beta, hessian, xy, dinfo, nclass, ncoeffPClass, fr.numCols()-1);
      GLMTask.GLMIterationTask gmt = new GLMTask.GLMIterationTask(null,dinfo,glmw,beta,
              nclass, true, null, ncoeffPClass).doAll(dinfo._adaptedFrame);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testNaiveCoordinateDescent() {
    expectedException.expect(H2OIllegalArgumentException.class);
    expectedException.expectMessage("Naive coordinate descent is not supported for multinomial.");
    GLMParameters params = new GLMParameters(Family.multinomial);
    params._solver = Solver.COORDINATE_DESCENT_NAIVE;

    // Should throw exception with information about unsupported message
    new GLM(params);
  }

  @Test
  public void testNaiveCoordinateDescent_families() {
    GLMParameters params = new GLMParameters(Family.binomial);
    params._solver = Solver.COORDINATE_DESCENT_NAIVE;
    final Family[] families = {Family.binomial, Family.gaussian, Family.gamma, Family.tweedie, Family.poisson, Family.ordinal,
    Family.quasibinomial};
    GLMParameters.Link[] linkingfuncs = {GLMParameters.Link.logit, GLMParameters.Link.identity, GLMParameters.Link.log,
            GLMParameters.Link.tweedie, GLMParameters.Link.log, GLMParameters.Link.ologit, GLMParameters.Link.logit};

    for (int i = 0; i < families.length; i++) {
      params._family = families[i];
      params._link = linkingfuncs[i];
      new GLM(params);
    }
  }

  /***
   * The next sets of tests are written to make sure GLM Multinomial is implemented correctly.  Tests are
   * written to check the following:
   * 1.  Gradient/Hessian calculations are correct.  This is done by comparing the gradient/hessian from the 
   * code and from the manually coded gradient/hessian calculation.
   * 2. ADMM implementation using all columns.  This is done by comparing the final prediction accuracies
   * from ADMM and without ADMM.  They should be equal within tolerance.  Note that ADMM will generate a result with
   * moderate accuracies.  Hence, we expect the results from no ADMM to be more accurate.
   * 3. ADMM implementation considering only active columns.  This is done by providing a dataset where all 
   * predictors are included in the active columns.  Then, we compare the coefficients generated by ADMM using all
   * columns and ADMM considering active columns only. When the activeCols include all predictors, I expect the
   * coefficients to be equal.
   * 4. Quick check with performance from IRLSM_SPEEDUP with original IRLSM results to make sure they obtain
   * similar results.
   * 
   * My hope is that tests should be enough to check and make sure my code is correct.
   */

  /* 1. Verify gradient calculation for GLM multinomial */
  @Test
  public void testMultinomialGradientSpeedUp(){
    Scope.enter();
    Frame fr, f1, f2, f3;
    // get new coefficients, 7 classes and 53 predictor+intercept
    Random rand = new Random();
    rand.setSeed(12345);
    int nclass = 4;
    double threshold = 1e-10;
    DataInfo dinfo=null;
    int numRows = 1000;

    try {
      f1 = TestUtil.generate_enum_only(2, numRows, nclass, 0);
      Scope.track(f1);
      f2 = TestUtil.generate_real_only(4, numRows, 0);
      Scope.track(f2);
      f3 = TestUtil.generate_enum_only(1, numRows, nclass, 0);
      Scope.track(f3);
      fr = f1.add(f2).add(f3);  // complete frame generation
      Scope.track(fr);
      GLMParameters params = new GLMParameters(Family.multinomial);
      params._response_column = f1._names[4];
      params._ignored_columns = new String[]{};
      params._train = fr._key;
      params._lambda = new double[]{0.5};
      params._alpha = new double[]{0.5};

      dinfo = new DataInfo(fr, null, 1, true, DataInfo.TransformType.STANDARDIZE, DataInfo.TransformType.NONE, true, false, false, false, false, false);
      int ncoeffPClass = dinfo.fullN()+1;
      double[] beta = new double[nclass*ncoeffPClass];
      for (int ind = 0; ind < beta.length; ind++) {
        beta[ind] = rand.nextDouble();
      }
      double l2pen = (1.0-params._lambda[0])*params._alpha[0];
      GLMTask.GLMMultinomialGradientSpeedUpTask gmt = new GLMTask.GLMMultinomialGradientSpeedUpTask(null,dinfo,l2pen,beta,1.0/fr.numRows()).doAll(dinfo._adaptedFrame);
      // calculate gradient and likelihood manually
      double[] manualGrad = new double[beta.length];
      double manualLLH = manualLikelihoodGradient(beta, manualGrad, 1.0/fr.numRows(), l2pen, dinfo, nclass,
              ncoeffPClass);
      // check likelihood calculation;
      assertEquals(manualLLH, gmt._likelihood, threshold);
      // check gradient
      TestUtil.checkArrays(gmt.gradient(), manualGrad, threshold);
    } finally {
      if (dinfo!=null)
        dinfo.remove();
      Scope.exit();
    }
  }

  public double manualLikelihoodGradient(double[] initialBeta, double[] gradient, double reg, double l2pen,
                                         DataInfo dinfo, int nclass, int ncoeffPClass) {
    double likelihood = 0;
    int numRows = (int) dinfo._adaptedFrame.numRows();
    int respInd = dinfo._adaptedFrame.numCols()-1;
    double[] etas = new double[nclass];
    double[] probs = new double[nclass+1];
    double[][] multinomialBetas = new double[nclass][ncoeffPClass];
    for (int classInd = 0; classInd < nclass; classInd++) {
      System.arraycopy(initialBeta,classInd*ncoeffPClass, multinomialBetas[classInd], 0, ncoeffPClass);
    }

    // calculate the etas for each class
    for (int rowInd=0; rowInd < numRows; rowInd++) {
      for (int classInd = 0; classInd < nclass; classInd++) { // calculate beta*coeff+beta0
        etas[classInd] = getInnerProduct(rowInd, multinomialBetas[classInd], dinfo);
      }
      int yresp = (int) dinfo._adaptedFrame.vec(respInd).at(rowInd);
      double logSumExp = computeMultinomialEtasSpeedUp(etas, probs);
      likelihood += logSumExp-etas[yresp];
      for (int classInd = 0; classInd < nclass; classInd++) { // calculate the multiplier here
        etas[classInd] = classInd==yresp?(probs[classInd]-1):probs[classInd];
      }
      // apply the multiplier and update the gradient accordingly
      updateGradient(gradient, nclass, ncoeffPClass, dinfo, rowInd, etas);
    }

    // apply learning rate and regularization constant
    ArrayUtils.mult(gradient,reg);
    if (l2pen > 0) {
      for (int classInd=0; classInd < nclass; classInd++) {
        for (int predInd = 0; predInd < dinfo.fullN(); predInd++) {  // loop through all coefficients for predictors only
          gradient[classInd*ncoeffPClass+predInd] += l2pen*initialBeta[classInd*ncoeffPClass+predInd];
        }
      }
    }
    return likelihood;
  }

  public void updateGradient(double[] gradient, int nclass, int ncoeffPclass, DataInfo dinfo, int rowInd,
                             double[] multiplier) {
    for (int classInd = 0; classInd < nclass; classInd++) {
      for (int cid = 0; cid < dinfo._cats; cid++) {
        int id = dinfo.getCategoricalId(cid, dinfo._adaptedFrame.vec(cid).at(rowInd));
        gradient[id + classInd * ncoeffPclass] += multiplier[classInd];
      }
      int numOff = dinfo.numStart();
      int cidOff = dinfo._cats;
      for (int cid = 0; cid < dinfo._nums; cid++) {
        double scale = dinfo._normMul != null ? dinfo._normMul[cid] : 1;
        double off = dinfo._normSub != null ? dinfo._normSub[cid] : 0;
        gradient[numOff + cid + classInd * ncoeffPclass] += multiplier[classInd] *
                (dinfo._adaptedFrame.vec(cid + cidOff).at(rowInd)-off)*scale;
      }
      // fix the intercept term
      gradient[(classInd + 1) * ncoeffPclass - 1] += multiplier[classInd];
    }
  }

  /**
   *  1. Verify Hessian calculation for GLM Multinomial.  This includes 
   *  the generation of gram matrix and the XY
   */
  @Test
  public void testMultinomialHessianXYSpeedUp(){
    Scope.enter();
    Frame fr, f1, f2, f3, f4;
    Random rand = new Random();
    long seed = 12345;
    rand.setSeed(seed);
    int nclass = 4;
    double threshold = 1e-10;
    int numRows = 1000;;

    try {
      f1 = TestUtil.generate_enum_only(4, numRows, nclass, 0);
      Scope.track(f1);
      checkHessianXY(f1, nclass, rand, threshold);  // check with only enum columns
      f2 = TestUtil.generate_real_only(6, numRows, 0);
      Scope.track(f2);
      f3 = TestUtil.generate_enum_only(1, numRows, nclass, 0);
      Scope.track(f3);
      fr = f1.add(f2).add(f3);  // complete frame generation
      f4 = f2.add(f3);  // only numeric columns
      Scope.track(f4);
      checkHessianXY(f4, nclass, rand, threshold);  // check with only enum columns
      Scope.track(fr);
      checkHessianXY(fr, nclass, rand, threshold);  // check with only mixed columns
    } finally {
      Scope.exit();
    }
  }


  public void checkHessianXY(Frame fr, int nclass, Random rand, double threshold) {
    Scope.enter();
    DataInfo dinfo=null;
    try {
      GLMParameters params = new GLMParameters(Family.multinomial);
      params._response_column = fr._names[fr.numCols()-1];
      params._ignored_columns = new String[]{};
      params._train = fr._key;
      params._lambda = new double[]{0.5};
      params._alpha = new double[]{0.5};
      params._solver = Solver.IRLSM_SPEEDUP;

      GLMModel.GLMWeightsFun glmw = new GLMModel.GLMWeightsFun(params);
      dinfo = new DataInfo(fr, null, 1, true, DataInfo.TransformType.STANDARDIZE,
              DataInfo.TransformType.NONE, true, false, false, false,
              false, false);
      int ncoeffPClass = dinfo.fullN()+1;
      double sumExp = 0;
      double[] beta = new double[nclass*ncoeffPClass];
      for (int ind = 0; ind < beta.length; ind++) {
        beta[ind] = rand.nextDouble();
      }
      int P = dinfo.fullN();       // number of predictors
      int N = dinfo.fullN() + 1;   // number of GLM coefficients per class
      for (int i = 1; i < nclass; ++i)
        sumExp += Math.exp(beta[i * N + P]);

      Vec [] vecs = dinfo._adaptedFrame.anyVec().makeDoubles(2, new double[]{sumExp,0});  // store sum exp and maxRow
      dinfo.addResponse(new String[]{"__glm_sumExp", "__glm_logSumExp"}, vecs);
      Scope.track(vecs[0]);
      Scope.track(vecs[1]);
      // calculate Hessian, xy and likelihood manually
      double[][] hessian = new double[beta.length][beta.length];
      double[] xy = new double[beta.length];
      double manualLLH = manualHessianXYLLH(beta, hessian, xy, dinfo, nclass, ncoeffPClass, fr.numCols()-1);
      GLMTask.GLMIterationTask gmt = new GLMTask.GLMIterationTask(null,dinfo,glmw,beta,
              nclass, true, null, ncoeffPClass).doAll(dinfo._adaptedFrame);

      // check likelihood calculation;
      assertEquals(manualLLH, gmt._likelihood, threshold);
      // check xy
      TestUtil.checkArrays(xy, gmt._xy, threshold);
      // check hessian
      double[][] glmHessian = gmt.getGram().getXX();
      checkDoubleArrays(glmHessian, hessian, threshold);
    } finally {
      if (dinfo!=null)
        dinfo.remove();
      Scope.exit();
    }
  }

  public double manualHessianXYLLH(double[] initialBeta, double[][] hessian, double[] xy, DataInfo dinfo, int nclass,
                                   int ncoeffPClass, int respInd) {
    double likelihood = 0;
    int numRows = (int) dinfo._adaptedFrame.numRows();
    double[] etas = new double[nclass];
    double[] probs = new double[nclass + 1];
    double[][] multinomialBetas = new double[nclass][ncoeffPClass];
    double[][] w = new double[nclass][nclass]; // reuse for each row
    double[] wz = new double[nclass];           // reuse for each row
    double[][] xtx = new double[ncoeffPClass][ncoeffPClass];
    double[] grads = new double[initialBeta.length];
    double[] multipliers = new double[nclass];
    for (int classInd = 0; classInd < nclass; classInd++) {
      System.arraycopy(initialBeta,classInd*ncoeffPClass, multinomialBetas[classInd], 0, ncoeffPClass);
    }
    // calculate the etas for each class
    for (int rowInd = 0; rowInd < numRows; rowInd++) { // work through each row
      for (int classInd = 0; classInd < nclass; classInd++) { // calculate beta*coeff+beta0
        etas[classInd] = getInnerProduct(rowInd, multinomialBetas[classInd], dinfo);
      }
      int yresp = (int) dinfo._adaptedFrame.vec(respInd).at(rowInd);
      double logSumExp = computeMultinomialEtasSpeedUp(etas, probs); // calculate the prob of each class
      dinfo._adaptedFrame.vec("__glm_sumExp").set(rowInd, probs[nclass]);
      dinfo._adaptedFrame.vec("__glm_logSumExp").set(rowInd, logSumExp);
      likelihood += logSumExp - etas[yresp];  // checked out manually
      // calculate w hessian without the predictors and complete w, not just lower triangle
      calculateW(w, probs);  // checked out okay
      // Add predictors to hessian to generate transpose(X)*W*X
      addX2W(xtx, hessian, w, dinfo, rowInd, nclass, ncoeffPClass); // checked out okay
      // calculate wz W*Etas+Grad again, without the predictors
      calculateWZ(w, wz, yresp, probs, etas); // checked out okay
      Arrays.fill(grads, 0.0);
      for (int classInd = 0; classInd < nclass; classInd++) { // calculate the multiplier here
        multipliers[classInd] = classInd==yresp?(probs[classInd]-1):probs[classInd];
      }
      updateGradient(grads, nclass, ncoeffPClass, dinfo, rowInd, multipliers);
      // add predictors to wz to form XY
      addX2Wz(xy, wz, dinfo, rowInd, nclass, ncoeffPClass, grads); // checked out okay
    }
    return likelihood;
  }

  public double getInnerProduct(int rowInd, double[] coeffs, DataInfo dinfo) {
    double innerP = coeffs[coeffs.length-1];  // add the intercept term;

    for (int predInd = 0; predInd < dinfo._cats; predInd++) { // categorical columns
      int id = dinfo.getCategoricalId(predInd, (int) dinfo._adaptedFrame.vec(predInd).at(rowInd));
      innerP += coeffs[id];
    }

    int numOff = dinfo.numStart();
    int cidOff = dinfo._cats;
    for (int cid=0; cid < dinfo._nums; cid++) {
      double scale = dinfo._normMul!=null?dinfo._normMul[cid]:1;
      double off = dinfo._normSub != null?dinfo._normSub[cid]:0;
      innerP += coeffs[cid+numOff]*(dinfo._adaptedFrame.vec(cid+cidOff).at(rowInd)-off)*scale;
    }

    return innerP;
  }

  public void addX2W(double[][] xtx, double[][] hessian, double[][] w, DataInfo dinfo, int rowInd, int nclass, int coeffPClass) {
    int numOff = dinfo._cats; // start of numerical columns
    int interceptInd = coeffPClass-1;
    // generate XTX first
    ArrayUtils.mult(xtx,0.0);
    for (int predInd=0; predInd < dinfo._cats; predInd++) {
      int rid = dinfo.getCategoricalId(predInd, (int) dinfo._adaptedFrame.vec(predInd).at(rowInd));
      for (int predInd2=0; predInd2 <= predInd; predInd2++) { // cat x cat
        int cid = dinfo.getCategoricalId(predInd2, (int) dinfo._adaptedFrame.vec(predInd2).at(rowInd));
        xtx[rid][cid] = 1;
      }

      // intercept x cat
      xtx[interceptInd][rid] = 1;
    }
    for (int predInd = 0; predInd < dinfo._nums; predInd++) {
      int rid = predInd+numOff;
      double scale = dinfo._normMul!=null?dinfo._normMul[predInd]:1;
      double off = dinfo._normSub != null?dinfo._normSub[predInd]:0;
      double d = (dinfo._adaptedFrame.vec(rid).at(rowInd)-off)*scale;
      for (int predInd2 = 0; predInd2 < dinfo._cats; predInd2++) {   // num x cat
        int cid = dinfo.getCategoricalId(predInd2, (int) dinfo._adaptedFrame.vec(predInd2).at(rowInd));
        xtx[dinfo._numOffsets[predInd]][cid] = d;
      }
    }
    for (int predInd=0; predInd < dinfo._nums; predInd++) { // num x num
      int rid = predInd+numOff;
      double scale = dinfo._normMul!=null?dinfo._normMul[predInd]:1;
      double off = dinfo._normSub != null?dinfo._normSub[predInd]:0;
      double d = (dinfo._adaptedFrame.vec(rid).at(rowInd)-off)*scale;
      // intercept x num
      xtx[interceptInd][dinfo._numOffsets[predInd]] = d;
      for (int predInd2=0; predInd2 <= predInd; predInd2++) {
        scale = dinfo._normMul!=null?dinfo._normMul[predInd2]:1;
        off = dinfo._normSub != null?dinfo._normSub[predInd2]:0;
        int cid = predInd2+numOff;
        xtx[dinfo._numOffsets[predInd]][dinfo._numOffsets[predInd2]] = d*(dinfo._adaptedFrame.vec(cid).at(rowInd)-off)*scale;
      }
    }
    xtx[interceptInd][interceptInd] = 1;
    // copy the lower triangle to the uppder triangle of xtx
    for (int rInd = 0; rInd < coeffPClass; rInd++) {
      for (int cInd=rInd+1; cInd < coeffPClass; cInd++) {
        xtx[rInd][cInd] = xtx[cInd][rInd];
      }
    }
    // xtx generation checkout out with my manual calculation
    for (int classInd=0; classInd < nclass; classInd++) {
      for (int classInd2 = 0; classInd2 < nclass; classInd2++) {
        for (int rpredInd = 0; rpredInd < coeffPClass; rpredInd++) {
          for (int cpredInd = 0; cpredInd < coeffPClass; cpredInd++) {
            hessian[classInd*coeffPClass+rpredInd][classInd2*coeffPClass+cpredInd]+=w[classInd][classInd2]*xtx[rpredInd][cpredInd];
          }
        }
      }
    }
  }

  public void addX2Wz(double[] xy, double[] wz, DataInfo dinfo, int rowInd, int nclass, int coeffPClass, double[] grads) {
    for (int predInd = 0; predInd < dinfo._cats; predInd++) { // cat
      int cid = dinfo.getCategoricalId(predInd, (int) dinfo._adaptedFrame.vec(predInd).at(rowInd));
      for (int classInd = 0; classInd < nclass; classInd++) {
        xy[classInd*coeffPClass+cid] += wz[classInd];
      }
    }

    for (int predInd = 0; predInd < dinfo._nums; predInd++) { // num
      double scale = dinfo._normMul!=null?dinfo._normMul[predInd]:1;
      double off = dinfo._normSub != null?dinfo._normSub[predInd]:0;
      int cid = predInd+dinfo._cats;
      double d = (dinfo._adaptedFrame.vec(cid).at(rowInd)-off)*scale;
      for (int classInd = 0; classInd < nclass; classInd++) {
        xy[classInd*coeffPClass+dinfo._numOffsets[predInd]] += wz[classInd]*d;
      }
    }

    for (int classInd=0; classInd < nclass; classInd++) { // intercept terms
      xy[(classInd+1)*coeffPClass-1] += wz[classInd];
    }
    
    for (int pind = 0; pind < xy.length; pind++)
      xy[pind] -= grads[pind]; // add gradient part
  }

  public void calculateW(double[][] w, double[] probs) {
    int nclass = w.length;
    for (int rclassInd=0; rclassInd < nclass; rclassInd++) {
      for (int cclassInd=0; cclassInd < nclass; cclassInd++) {
        w[rclassInd][cclassInd] = (rclassInd==cclassInd)?(probs[rclassInd]-probs[rclassInd]*probs[rclassInd]):-probs[rclassInd]*probs[cclassInd];
      }
    }
  }

  public void calculateWZ(double[][] w, double[] wz, int y, double[] probs, double[] etas) {
    int nclass = wz.length;

    for (int rclassInd=0; rclassInd < nclass; rclassInd++) {
      wz[rclassInd] = 0; // gradient part
      for (int cclassInd=0; cclassInd < nclass; cclassInd++) {
        wz[rclassInd] += w[rclassInd][cclassInd]*etas[cclassInd];   // due to transpose(W)*beta
      }
    }
  }

  /* 2. Verify ADMM implementation considering all predictors.  I am comparing results using ADMM and without 
   * using ADMM.
   */
  @Test
  public void testMultinomialADMMandNoADMM() {
    Scope.enter();
    Frame fr, test;
    Random rand = new Random();
    long seed = 12345;
    rand.setSeed(seed);
    double threshold = 1e-3;
    double lambda = 1.104374697155361E-4;

    try {
      fr = parse_test_file("smalldata/glm_test/multinomial_3Class_10KRow.csv");
      test = parse_test_file("smalldata/glm_test/multinomial_3Class_test_set_5kRows.csv");
      for (String s : new String[]{"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C11", "C12", "C13",
              "C14", "C15", "C16", "C17", "C18", "C19", "C20", "C79"}) {
        Vec v = fr.remove(s);
        fr.add(s, v.toCategoricalVec());
        v.remove();
        Vec vt = test.remove(s);
        test.add(s, vt.toCategoricalVec());
        vt.remove();
      }
      Scope.track(fr);
      Scope.track(test);

      // build models with ADMM considering all columns.
      GLMModel admmnoreg = checkADMM(fr, test, 0, 0.5, true, Solver.IRLSM_SPEEDUP);
      Scope.track_generic(admmnoreg);
      GLMModel admml2pen = checkADMM(fr, test, lambda, 0, true, Solver.IRLSM_SPEEDUP);
      Scope.track_generic(admml2pen);
      GLMModel admml1pen = checkADMM(fr, test, lambda, 1, true, Solver.IRLSM_SPEEDUP);
      Scope.track_generic(admml1pen);
      GLMModel admmbothreg = checkADMM(fr, test, lambda, 0.5, true, Solver.IRLSM_SPEEDUP);
      Scope.track_generic(admmbothreg);
      
      // build models without ADMM. These would be compared to models with ADMM.  They may not be equal but close.
      GLMModel noadmml2pen = checkADMM(fr, test, lambda, 0, true, Solver.IRLSM_SPEEDUP_NO_ADMM);  // check with only enum columns with both
      GLMModel noadmmbothreg = checkADMM(fr, test, lambda, 0.5, true, Solver.IRLSM_SPEEDUP_NO_ADMM);  // check with only enum columns with both
      Scope.track_generic(noadmml2pen);
      Scope.track_generic(noadmmbothreg);

      // compare admm and no admm with same kind of regularization in terms of accuracy and logloss
      checkLoglossMeanError(admml2pen, noadmml2pen, threshold);
      checkLoglossMeanError(admmbothreg, noadmmbothreg, threshold);

      // compare admml1pen performance with admmbothreg in terms of accuracy and logloss
      checkLoglossMeanError(admml1pen, admmbothreg, threshold);

      // compare admml1pen performance with admmbothreg in terms of accuracy and logloss
      checkLoglossMeanError(admml1pen, admmbothreg, threshold);

      // compare admmnoreg performance with admmbothreg in terms of accuracy and logloss
      checkLoglossMeanError(admmnoreg, admmbothreg, threshold);

    } finally {
      Scope.exit();
    }
  }

  /* 3. Verify ADMM implementation for active cols.  The verification is performed using ADMM built with 
   * all predictors.
   */
  @Test
  public void testMultinomialADMMActiveCols() {
    Scope.enter();
    Frame fr, test;
    Random rand = new Random();
    long seed = 12345;
    rand.setSeed(seed);
    double threshold = 1e-3;
    double lambda = 1.104374697155361E-4;

    try {
      fr = parse_test_file("smalldata/glm_test/multinomial_3Class_10KRow.csv");
      test = parse_test_file("smalldata/glm_test/multinomial_3Class_test_set_5kRows.csv");
      for (String s : new String[]{"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C11", "C12", "C13",
              "C14", "C15", "C16", "C17", "C18", "C19", "C20", "C79"}) {
        Vec v = fr.remove(s);
        fr.add(s, v.toCategoricalVec());
        v.remove();
        Vec vt = test.remove(s);
        test.add(s, vt.toCategoricalVec());
        vt.remove();
      }
      Scope.track(fr);
      Scope.track(test);

      // build admm model with only activeCols
      GLMModel admmnoreg2 = checkADMM(fr, test, 0,0.5, true, Solver.IRLSM_SPEEDUP2);
      Scope.track_generic(admmnoreg2);
      GLMModel admmbothreg2 = checkADMM(fr, test, lambda,0.5, true, Solver.IRLSM_SPEEDUP2);
      Scope.track_generic(admmbothreg2);

      GLMModel admml2pen2 = checkADMM(fr, test, lambda,0, true, Solver.IRLSM_SPEEDUP2);
      Scope.track_generic(admml2pen2);  // includes all predictors
      GLMModel admml1pen2 = checkADMM(fr, test, lambda,1, true, Solver.IRLSM_SPEEDUP2);
      Scope.track_generic(admml1pen2);
      
      // build models with ADMM considering all columns.
      GLMModel admml2pen = checkADMM(fr, test, lambda, 0, true, Solver.IRLSM_SPEEDUP);
      Scope.track_generic(admml2pen);
      GLMModel admml1pen = checkADMM(fr, test, lambda, 1, true, Solver.IRLSM_SPEEDUP);
      Scope.track_generic(admml1pen);
      GLMModel admmbothreg = checkADMM(fr, test, lambda, 0.5, true, Solver.IRLSM_SPEEDUP);
      Scope.track_generic(admmbothreg);
      GLMModel admmnoreg = checkADMM(fr, test, 0, 0.5, true, Solver.IRLSM_SPEEDUP);
      Scope.track_generic(admmnoreg);

      // compare multinomial coefficients for l2pen, active columns in this case include all predictors
      TestUtil.checkArrays(admml2pen2.beta(), admml2pen.beta(), threshold);
      // compare multinomial coefficients for noreg, active columns in this case include all predictors
      TestUtil.checkArrays(admmnoreg2.beta(), admmnoreg.beta(), threshold);
      
      // compare admm with active cols only and admm with all predictors in terms of accuracy and logloss
      checkLoglossMeanError(admml2pen2, admml2pen, threshold);
      checkLoglossMeanError(admmbothreg2, admmbothreg, threshold);
      checkLoglossMeanError(admml1pen2, admml1pen, threshold);
      checkLoglossMeanError(admmnoreg2, admmnoreg2, threshold);
    } finally {
      Scope.exit();
    }
  }


  /* 2. Verify ADMM implementation considering all predictors  */
  /* 3. Verify ADMM implementaton using only Active Columns */
  @Test
  public void testMultinomialADMMSpeedUp(){
    Scope.enter();
    Frame fr, test, pred1, pred2, pred3, pred4;
    Random rand = new Random();
    long seed = 12345;
    rand.setSeed(seed);
    double threshold = 1e-3;
    double lambda = 1.104374697155361E-4;

    try {
      fr = parse_test_file("smalldata/glm_test/multinomial_3Class_10KRow.csv");
      test = parse_test_file("smalldata/glm_test/multinomial_3Class_test_set_5kRows.csv");
      for (String s : new String[]{"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C11", "C12", "C13",
              "C14", "C15", "C16", "C17", "C18", "C19", "C20", "C79"}) {
        Vec v = fr.remove(s);
        fr.add(s, v.toCategoricalVec());
        v.remove();
        Vec vt = test.remove(s);
        test.add(s, vt.toCategoricalVec());
        vt.remove();
      }
      Scope.track(fr);
      Scope.track(test);

      // build admm model with only activeCols
      GLMModel admmbothreg2 = checkADMM(fr, test, lambda,0.5, true, Solver.IRLSM_SPEEDUP2);
      Scope.track_generic(admmbothreg2);
      GLMModel admml2pen2 = checkADMM(fr, test, lambda,0, true, Solver.IRLSM_SPEEDUP2);
      Scope.track_generic(admml2pen2);  // includes all predictors
      GLMModel admml1pen2 = checkADMM(fr, test, lambda,1, true, Solver.IRLSM_SPEEDUP2);
      Scope.track_generic(admml1pen2);
      GLMModel admmnoreg2 = checkADMM(fr, test, 0,0.5, true, Solver.IRLSM_SPEEDUP2);
      Scope.track_generic(admmnoreg2);
      
      // build models with ADMM considering all columns.
      GLMModel admml2pen = checkADMM(fr, test, lambda,0, true, Solver.IRLSM_SPEEDUP);
      Scope.track_generic(admml2pen);
      GLMModel admml1pen = checkADMM(fr, test, lambda, 1, true, Solver.IRLSM_SPEEDUP);
      Scope.track_generic(admml1pen);
      GLMModel admmbothreg = checkADMM(fr, test, lambda,0.5, true, Solver.IRLSM_SPEEDUP);
      Scope.track_generic(admmbothreg);
      GLMModel admmnoreg = checkADMM(fr, test, lambda,0.5, true, Solver.IRLSM_SPEEDUP);
      Scope.track_generic(admmnoreg);
      

      
      // build models with original IRLSM.  We will compare prediction results with this models and our new ones
      GLMModel admml2penIRLSMO = checkADMM(fr, test, lambda,0, true, Solver.IRLSM);
      Scope.track_generic(admml2penIRLSMO);
      GLMModel admmbothreg2IRLSMO = checkADMM(fr, test, lambda,0.5, true, Solver.IRLSM);
      Scope.track_generic(admmbothreg2IRLSMO);
      GLMModel admml1penIRLSMO = checkADMM(fr, test, lambda,1, true, Solver.IRLSM);
      Scope.track_generic(admml1penIRLSMO);
      GLMModel admmnoreg2IRLSMO = checkADMM(fr, test, 0,0.5, true, Solver.IRLSM);
      Scope.track_generic(admmnoreg2IRLSMO);
      
      // build models without ADMM. These would be compared to models with ADMM.  They may not be equal but close.
      GLMModel noadmml2pen = checkADMM(fr, test, lambda,0, true, Solver.IRLSM_SPEEDUP_NO_ADMM);  // check with only enum columns with both
      GLMModel noadmmbothreg = checkADMM(fr, test, lambda,0.5, true, Solver.IRLSM_SPEEDUP_NO_ADMM);  // check with only enum columns with both
      Scope.track_generic(noadmml2pen);
      Scope.track_generic(noadmmbothreg);
      
      // compare admm and no admm with same kind of regularization in terms of accuracy and logloss
      checkLoglossMeanError(admml2pen, noadmml2pen, threshold);
      checkLoglossMeanError(admmbothreg, noadmmbothreg, threshold);
      
      // compare admml1pen performance with admmbothreg in terms of accuracy and logloss
      checkLoglossMeanError(admml1pen, admmbothreg, threshold);
      


      // compare admm with all predictors and admm only with activeCols in terms of accuracy and logloss
      checkLoglossMeanError(admml1pen2, admml1pen, threshold);
      checkLoglossMeanError(admml2pen2, admml2pen, threshold);
      checkLoglossMeanError(admmbothreg2, admmbothreg, threshold);
      
      // compare coefficients of admm with all predictors and admm only with activeCols since they both use all predictors
      TestUtil.checkArrays(admml2pen2.beta(), admml2pen.beta(), threshold);
      

      
      // compare original IRLSM and admm with active cols only 
      checkLoglossMeanError(admml1pen2, admml1penIRLSMO, threshold);
      checkLoglossMeanError(admml2pen2, admml2penIRLSMO, threshold);
      checkLoglossMeanError(admmbothreg2, admmbothreg2IRLSMO, threshold);
      checkLoglossMeanError(admmnoreg2, admmnoreg2IRLSMO, threshold);
    } finally {
      Scope.exit();
    }
  }
  
  public void checkLoglossMeanError(GLMModel model1, GLMModel model2, double threshold) {
    assertTrue((Math.abs(model1.logloss()-model2.logloss()) < threshold) || (model1.logloss() < model2.logloss()));
    assertTrue((Math.abs(model1.mean_per_class_error()-model2.mean_per_class_error()) < threshold) ||
            (model1.mean_per_class_error() < model2.mean_per_class_error()));
  }

  public GLMModel checkADMM(Frame fr, Frame valid, double lambda, double alpha, 
                        boolean hasIntercept, Solver solver) {
    Scope.enter();
    try {
      GLMParameters params = new GLMParameters(Family.multinomial);
      params._response_column = fr._names[fr.numCols()-1];
      params._ignored_columns = new String[]{};
      params._train = fr._key;
      params._valid = valid._key;
      params._lambda = new double[]{lambda};
      params._alpha = new double[]{alpha};
      params._solver = solver;
   //   params._max_iterations = 1;
      params._seed = 12345;
      if (!hasIntercept)
        params._intercept = false;
      
      GLMModel model = new GLM(params).trainModel().get();
      return model;
    } finally {
      Scope.exit();
    }
  }
  

  
  // This method needs to calculate Pr(yi=c) for each class c and to 1/(sum of all exps
  public double  computeMultinomialEtasSpeedUp(double [] etas, double [] exps) {
    double sumExp = 0;
    int K = etas.length;
    for(int c = 0; c < K; ++c) { // calculate pr(yi=c) for each class
      double x = Math.exp(etas[c]);
      sumExp += x;
      exps[c] = x;
    }
    double reg = 1.0/(sumExp);
    exps[K] = reg;  // store 1/(sum of exp)
    for(int c = 0; c < K; ++c)  // calculate pr(yi=c) for each class
      exps[c] *= reg;
    return Math.log(sumExp);
  }
}
