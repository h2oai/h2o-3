package hex.glm;

import hex.CreateFrame;
import hex.DataInfo;
import hex.FrameSplitter;
import hex.ModelMetricsBinomialGLM.ModelMetricsMultinomialGLM;
import hex.SplitFrame;
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
  static double _tol = 1e-10;

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
      params._missing_values_handling = GLMModel.GLMParameters.MissingValuesHandling.Skip;
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
      params._missing_values_handling = GLMModel.GLMParameters.MissingValuesHandling.Skip;
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
      params._missing_values_handling = GLMModel.GLMParameters.MissingValuesHandling.Skip;
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
  
  /**
   * Here I am comparing the hessian and xy generation from Octave and from Java. The first test will use datasets 
   * containing only numeric columns.  The second test will use datasets containing two enums and one numeric
   * column.
   */
  
  @Test
  public void testMultinomialOctaveCheck() {
    // preparation for first test with enum and numeric columns
    String fileEnum = "smalldata/glm_test/multinomial_2enum_1num_3_class_training_set_20Rows.csv";
    double[] betaInitEnum = new double[]{6.032518056475340e-01, 2.640933210565435e-01, 9.776610628574375e-01,
            9.053994887993339e-01, 8.568094395996303e-01, 8.160944841628063e-01, 8.535849765221630e-01,
            9.089574716333825e-01, 9.307192993747759e-01, 9.026543767123323e-02, 4.433115852756973e-01,
            3.949082561729321e-01, 5.116906460935773e-01, 3.923280162531700e-01, 3.817220806386150e-02,
            7.253019966760611e-01, 2.718688319609313e-02, 8.800342103367991e-01, 9.375494687132276e-01,
            2.055074814305576e-01, 5.510301561096235e-01, 2.040023326788270e-02, 3.002179521713415e-02,
            3.359326736300998e-01, 9.851973786322425e-02, 3.778812121333486e-01, 5.341025070853849e-01,
            9.687561732874905e-01, 8.975540155624436e-01, 9.473183322524110e-01, 5.087137287713507e-01,
            6.719602905787800e-01, 9.956092031838000e-01, 1.517852940297946e-01, 6.513775311597036e-01,
            2.969932659071039e-01, 7.898241942465704e-01, 2.295012581837947e-01, 2.875073629554708e-01};
    double[][] hessOctaveEnum = new double[][] {{7.474173064845179e-01, 0, 0, 0, 0, 0, 4.979060100704669e-01, 0, 0, 
            2.495112964140510e-01, 0, 7.136986673156284e-01, 7.474173064845179e-01, -2.339932000167036e-01, 0, 0, 0, 
            0, 0, -1.422755401286521e-01, 0, 0, -9.171765988805154e-02, 0, -2.360939144282587e-01, 
            -2.339932000167036e-01, -5.134241064678142e-01, 0, 0, 0, 0, 0, -3.556304699418148e-01, 0, 0, 
            -1.577936365259994e-01, 0, -4.776047528873696e-01, -5.134241064678142e-01},
            {0, 6.644135009518463e-01, 0, 0, 0, 0, 0, 4.953127606448653e-01, 0, 1.691007403069810e-01, 0, 
                    -1.709231854801061e-01, 6.644135009518463e-01, 0, -2.398193436796391e-01, 0, 0, 0, 0, 0,
                    -1.909034289337227e-01, 0, -4.891591474591645e-02, 0, 4.204810872368511e-02, -2.398193436796391e-01,
                    0, -4.245941572722073e-01, 0, 0, 0, 0, 0, -3.044093317111427e-01, 0, -1.201848255610645e-01,
                    0, 1.288750767564209e-01,
                    -4.245941572722073e-01},
            {0, 0, 7.273764345583342e-01, 0, 0, 0, 4.999053267844445e-01, 2.274711077738897e-01, 0, 0, 0, 
                    8.138586836523973e-01, 7.273764345583342e-01, 0, 0, -2.550329724297243e-01, 0, 0, 0, 
                    -1.365019182449233e-01, -1.185310541848010e-01, 0, 0, 0, -3.224878048688143e-01, 
                    -2.550329724297243e-01, 0, 0, -4.723434621286099e-01, 0, 0, 0, -3.634034085395212e-01, 
                    -1.089400535890887e-01, 0, 0, 0, -4.913708787835829e-01, -4.723434621286099e-01},
            {0, 0, 0, 9.591577426605575e-01, 0, 0, 0, 4.912375468802413e-01, 0, 2.352068305112997e-01, 
                    2.327133652690165e-01, -8.113305185345081e-01, 9.591577426605575e-01, 0, 0, 0, 
                    -3.258822293568328e-01, 0, 0, 0, -2.074994417364917e-01, 0, -6.436568247665543e-02, 
                    -5.401710514368574e-02, 2.848902130211543e-01, -3.258822293568328e-01, 0, 0, 0, 
                    -6.332755133037247e-01, 0, 0, 0, -2.837381051437496e-01, 0, -1.708411480346443e-01, 
                    -1.786962601253307e-01, 5.264403055133537e-01, -6.332755133037247e-01},
            {0, 0, 0, 0, 1.454804071429532e+00, 0, 2.448456310265110e-01, 0, 2.329488495123498e-01, 
                    7.293013880421275e-01, 2.477082028485434e-01, 1.357575323976950e+00, 1.454804071429532e+00, 0, 0, 0,
                    0, -6.900615257773394e-01, 0, -8.837590838502636e-02, 0, -9.669287651917700e-02, 
                    -3.905471578231773e-01, -1.144455830499587e-01, -5.882108811362481e-01, -6.900615257773394e-01,
                    0, 0, 0, 0, -7.647425456521924e-01, 0, -1.564697226414847e-01, 0, -1.362559729931728e-01,
                    -3.387542302189502e-01, -1.332626197985847e-01, -7.693644428407020e-01, -7.647425456521924e-01},
            {0, 0, 0, 0, 0, 2.493791989551229e-01, 2.493791989551229e-01, 0, 0, 0, 0, 2.008971105550796e-01, 
                    2.493791989551229e-01, 0, 0, 0, 0, 0, -9.183828417907063e-02, -9.183828417907063e-02, 0, 0, 0, 0, 
                    -7.398390085145692e-02, -9.183828417907063e-02, 0, 0, 0, 0, 0, -1.575409147760524e-01,
                    -1.575409147760524e-01, 0, 0, 0, 0, -1.269132097036227e-01, -1.575409147760524e-01},
            {4.979060100704669e-01, 0, 4.999053267844445e-01, 0, 2.448456310265110e-01, 2.493791989551229e-01, 
                    1.492036166836545e+00, 0, 0, 0, 0, 1.350714944576590e+00, 1.492036166836545e+00, 
                    -1.422755401286521e-01, 0, -1.365019182449233e-01, 0, -8.837590838502636e-02, 
                    -9.183828417907063e-02, -4.589916509376724e-01, 0, 0, 0, 0, -4.285896332235410e-01, 
                    -4.589916509376724e-01, -3.556304699418148e-01, 0, -3.634034085395212e-01, 0, 
                    -1.564697226414847e-01, -1.575409147760524e-01, -1.033044515898873e+00, 0, 0, 0, 0, 
                    -9.221253113530495e-01, -1.033044515898873e+00},
            {0, 4.953127606448653e-01, 2.274711077738897e-01, 4.912375468802413e-01, 0, 0, 0, 1.214021415298996e+00, 0, 
                    0, 0, 6.279082381888845e-02, 1.214021415298996e+00, 0, -1.909034289337227e-01, 
                    -1.185310541848010e-01, -2.074994417364917e-01, 0, 0, 0, -5.169339248550153e-01, 0, 0, 0,
                    -5.691727925730792e-02, -5.169339248550153e-01, 0, -3.044093317111427e-01, -1.089400535890887e-01,
                    -2.837381051437496e-01, 0, 0, 0, -6.970874904439810e-01, 0, 0, 0, -5.873544561580638e-03,
                    -6.970874904439810e-01},
            {0, 0, 0, 0, 2.329488495123498e-01, 0, 0, 0, 2.329488495123498e-01, 0, 0, 3.089454391462518e-01, 
                    2.329488495123498e-01, 0, 0, 0, 0, -9.669287651917700e-02, 0, 0, 0, -9.669287651917700e-02,
                    0, 0, -1.282376936441909e-01, -9.669287651917700e-02, 0, 0, 0, 0, -1.362559729931728e-01, 0, 0, 0,
                    -1.362559729931728e-01, 0, 0, -1.807077455020609e-01,
                    -1.362559729931728e-01},
            {2.495112964140510e-01, 1.691007403069810e-01, 0, 2.352068305112997e-01, 7.293013880421275e-01, 0, 0, 0, 0, 
                    1.383120255274459e+00, 0, 2.337283853756555e-01, 1.383120255274459e+00, -9.171765988805154e-02,
                    -4.891591474591645e-02, 0, -6.436568247665543e-02, -3.905471578231773e-01, 0, 0, 0, 0, 
                    -5.955464149338007e-01, 0, -1.762582605938490e-01, -5.955464149338007e-01, -1.577936365259994e-01,
                    -1.201848255610645e-01, 0, -1.708411480346443e-01, -3.387542302189502e-01, 0, 0, 0, 0, 
                    -7.875738403406585e-01, 0, -5.747012478180653e-02, -7.875738403406585e-01},
            {0, 0, 0, 2.327133652690165e-01, 2.477082028485434e-01, 0, 0, 0, 0, 0, 4.804215681175598e-01, 
                    1.475964885680550e-01, 4.804215681175598e-01, 0, 0, 0, -5.401710514368574e-02, 
                    -1.144455830499587e-01, 0, 0, 0, 0, 0, -1.684626881936444e-01, -1.038353128210499e-01, 
                    -1.684626881936444e-01, 0, 0, 0, -1.786962601253307e-01, -1.332626197985847e-01, 0, 0, 0, 0, 0,
                    -3.119588799239154e-01, -4.376117574700500e-02, -3.119588799239154e-01},
            {7.136986673156284e-01, -1.709231854801061e-01, 8.138586836523973e-01, -8.113305185345081e-01, 
                    1.357575323976950e+00, 2.008971105550796e-01, 1.350714944576590e+00, 6.279082381888845e-02,
                    3.089454391462518e-01, 2.337283853756555e-01, 1.475964885680550e-01, 5.292078936478144e+00,
                    2.103776081485441e+00, -2.360939144282587e-01, 4.204810872368511e-02, -3.224878048688143e-01,
                    2.848902130211543e-01, -5.882108811362481e-01, -7.398390085145692e-02, -4.285896332235410e-01,
                    -5.691727925730792e-02, -1.282376936441909e-01, -1.762582605938490e-01, -1.038353128210499e-01,
                    -2.036930786703289e+00, -8.938381795399387e-01, -4.776047528873696e-01, 1.288750767564209e-01,
                    -4.913708787835829e-01, 5.264403055133537e-01, -7.693644428407020e-01, -1.269132097036227e-01,
                    -9.221253113530495e-01, -5.873544561580638e-03, -1.807077455020609e-01, -5.747012478180653e-02,
                    -4.376117574700500e-02, -3.255148149774855e+00, -1.209937901945503e+00},
            {7.474173064845179e-01, 6.644135009518463e-01, 7.273764345583342e-01, 9.591577426605575e-01, 
                    1.454804071429532e+00, 2.493791989551229e-01, 1.492036166836545e+00, 1.214021415298996e+00, 
                    2.329488495123498e-01, 1.383120255274459e+00, 4.804215681175598e-01, 2.103776081485441e+00,
                    4.802548255039911e+00, -2.339932000167036e-01, -2.398193436796391e-01, -2.550329724297243e-01,
                    -3.258822293568328e-01, -6.900615257773394e-01, -9.183828417907063e-02, -4.589916509376724e-01,
                    -5.169339248550153e-01, -9.669287651917700e-02, -5.955464149338007e-01, -1.684626881936444e-01,
                    -8.938381795399387e-01, -1.836627555439310e+00, -5.134241064678142e-01, -4.245941572722073e-01,
                    -4.723434621286099e-01, -6.332755133037247e-01, -7.647425456521924e-01, -1.575409147760524e-01,
                    -1.033044515898873e+00, -6.970874904439810e-01, -1.362559729931728e-01, -7.875738403406585e-01,
                    -3.119588799239154e-01, -1.209937901945503e+00, -2.965920699600602e+00},
            { -2.339932000167036e-01, 0, 0, 0, 0, 0, -1.422755401286521e-01, 0, 0, -9.171765988805154e-02, 0, 
                    -2.360939144282587e-01, -2.339932000167036e-01, 4.092351912984802e-01, 0, 0, 0, 0, 0,
                    2.541480051991947e-01, 0, 0, 1.550871860992856e-01, 0, 4.028952244820714e-01, 4.092351912984802e-01,
                    -1.752419912817766e-01, 0, 0, 0, 0, 0, -1.118724650705426e-01, 0, 0, -6.336952621123403e-02, 0,
                    -1.668013100538127e-01, -1.752419912817766e-01},
            {0, -2.398193436796391e-01,0, 0, 0, 0, 0, -1.909034289337227e-01, 0, -4.891591474591645e-02, 0, 
                    4.204810872368511e-02, -2.398193436796391e-01, 0, 5.069262139865550e-01, 0, 0, 0, 0, 0,
                    3.315031934283351e-01, 0, 1.754230205582199e-01, 0, -2.199266645266825e-01, 5.069262139865550e-01,
                    0, -2.671068703069159e-01, 0, 0, 0, 0, 0, -1.405997644946125e-01, 0, -1.265071058123035e-01, 0,
                    1.778785558029974e-01, -2.671068703069159e-01},
            {0, 0, -2.550329724297243e-01, 0, 0, 0, -1.365019182449233e-01, -1.185310541848010e-01, 0, 0, 0,
                    -3.224878048688143e-01, -2.550329724297243e-01, 0, 0, 3.869680883489559e-01, 0, 0, 0,
                    2.378832944683423e-01, 1.490847938806135e-01, 0, 0, 0, 4.596297914680660e-01, 3.869680883489559e-01,
                    0, 0, -1.319351159192316e-01, 0, 0, 0, -1.013813762234190e-01, -3.055373969581256e-02, 0, 0, 0,
                    -1.371419865992518e-01, -1.319351159192316e-01},
            {0, 0, 0, -3.258822293568328e-01, 0, 0, 0, -2.074994417364917e-01, 0, -6.436568247665543e-02,
                    -5.401710514368574e-02, 2.848902130211543e-01, -3.258822293568328e-01, 0, 0, 0,
                    5.684213704605494e-01, 0, 0, 0, 3.021547389444349e-01, 0, 1.411737754974775e-01,
                    1.250928560186369e-01, -4.933095747437474e-01, 5.684213704605494e-01, 0, 0, 0, 
                    -2.425391411037165e-01, 0, 0, 0, -9.465529720794330e-02, 0, -7.680809302082209e-02,
                    -7.107575087495113e-02, 2.084193617225931e-01, -2.425391411037165e-01},
            {0, 0, 0, 0, -6.900615257773394e-01, 0, -8.837590838502636e-02, 0, -9.669287651917700e-02, 
                    -3.905471578231773e-01, -1.144455830499587e-01, -5.882108811362481e-01, -6.900615257773394e-01, 0, 
                    0, 0, 0, 1.093722668554482e+00, 0, 1.306705272688737e-01, 0, 1.298265486878574e-01, 
                    6.441719137543418e-01, 1.890536788434094e-01, 9.030632265902933e-01, 1.093722668554482e+00, 0, 0, 0,
                    0, -4.036611427771429e-01, 0, -4.229461888384730e-02, 0, -3.313367216868039e-02, 
                    -2.536247559311645e-01, -7.460809579345069e-02, -3.148523454540451e-01, -4.036611427771429e-01},
            {0, 0, 0, 0, 0, -9.183828417907063e-02, -9.183828417907063e-02, 0, 0, 0, 0, -7.398390085145692e-02,
                    -9.183828417907063e-02, 0, 0, 0, 0, 0, 1.559409505887975e-01, 1.559409505887975e-01, 0, 0, 0, 0,
                    1.256242963397260e-01, 1.559409505887975e-01, 0, 0, 0, 0, 0, -6.410266640972688e-02, 
                    -6.410266640972688e-02, 0, 0, 0, 0, -5.164039548826907e-02, -6.410266640972688e-02},
            {-1.422755401286521e-01, 0, -1.365019182449233e-01, 0, -8.837590838502636e-02, -9.183828417907063e-02, 
                    -4.589916509376724e-01, 0, 0, 0, 0, -4.285896332235410e-01, -4.589916509376724e-01,
                    2.541480051991947e-01, 0, 2.378832944683423e-01, 0, 1.306705272688737e-01, 1.559409505887975e-01,
                    7.786427775252081e-01, 0, 0, 0, 0, 6.997742308572420e-01, 7.786427775252081e-01, 
                    -1.118724650705426e-01, 0, -1.013813762234190e-01, 0, -4.229461888384730e-02, 
                    -6.410266640972688e-02, -3.196511265875358e-01, 0, 0, 0, 0, -2.711845976337011e-01, 
                    -3.196511265875358e-01},
            {0, -1.909034289337227e-01, -1.185310541848010e-01, -2.074994417364917e-01, 0, 0, 0,
                    -5.169339248550153e-01, 0, 0, 0, -5.691727925730792e-02, -5.169339248550153e-01, 0, 
                    3.315031934283351e-01, 1.490847938806135e-01, 3.021547389444349e-01, 0, 0, 0, 7.827427262533837e-01,
                    0, 0, 0, 4.416577052640303e-02, 7.827427262533837e-01, 0, -1.405997644946125e-01,
                    -3.055373969581256e-02, -9.465529720794330e-02, 0, 0, 0, -2.658088013983683e-01, 0, 0, 0,
                    1.275150873090487e-02, -2.658088013983683e-01},
            {0, 0, 0, 0, -9.669287651917700e-02, 0, 0, 0, -9.669287651917700e-02, 0, 0, -1.282376936441909e-01,
                    -9.669287651917700e-02, 0, 0, 0, 0, 1.298265486878574e-01, 0, 0, 0, 1.298265486878574e-01, 0, 0,
                    1.721808035591348e-01, 1.298265486878574e-01, 0, 0, 0, 0, -3.313367216868039e-02, 0, 0, 0,
                    -3.313367216868039e-02, 0, 0, -4.394310991494386e-02, -3.313367216868039e-02},
            {-9.171765988805154e-02, -4.891591474591645e-02, 0, -6.436568247665543e-02, -3.905471578231773e-01, 0, 0, 0,
                    0, -5.955464149338007e-01, 0, -1.762582605938490e-01, -5.955464149338007e-01, 1.550871860992856e-01,
                    1.754230205582199e-01, 0, 1.411737754974775e-01, 6.441719137543418e-01, 0, 0, 0, 0, 
                    1.115855895909325e+00, 0, 1.142206428815691e-01, 1.115855895909325e+00, -6.336952621123403e-02,
                    -1.265071058123035e-01, 0, -7.680809302082209e-02, -2.536247559311645e-01, 0, 0, 0, 0,
                    -5.203094809755241e-01, 0, 6.203761771227988e-02, -5.203094809755241e-01},
            {0, 0, 0, -5.401710514368574e-02, -1.144455830499587e-01, 0, 0, 0, 0, 0, -1.684626881936444e-01,
                    -1.038353128210499e-01, -1.684626881936444e-01, 0, 0, 0, 1.250928560186369e-01, 
                    1.890536788434094e-01, 0, 0, 0, 0, 0, 3.141465348620462e-01, 1.476348517853778e-01, 
                    3.141465348620462e-01, 0, 0, 0, -7.107575087495113e-02, -7.460809579345069e-02, 0, 0, 0, 0, 0,
                    -1.456838466684018e-01, -4.379953896432789e-02, -1.456838466684018e-01},
            { -2.360939144282587e-01, 4.204810872368511e-02, -3.224878048688143e-01, 2.848902130211543e-01,
                    -5.882108811362481e-01, -7.398390085145692e-02, -4.285896332235410e-01, -5.691727925730792e-02,
                    -1.282376936441909e-01, -1.762582605938490e-01, -1.038353128210499e-01, -2.036930786703289e+00,
                    -8.938381795399387e-01, 4.028952244820714e-01, -2.199266645266825e-01, 4.596297914680660e-01,
                    -4.933095747437474e-01, 9.030632265902933e-01, 1.256242963397260e-01, 6.997742308572420e-01,
                    4.416577052640303e-02, 1.721808035591348e-01, 1.142206428815691e-01, 1.476348517853778e-01,
                    3.387363087474965e+00, 1.177976299609727e+00, -1.668013100538127e-01, 1.778785558029974e-01,
                    -1.371419865992518e-01, 2.084193617225931e-01, -3.148523454540451e-01, -5.164039548826907e-02,
                    -2.711845976337011e-01, 1.275150873090487e-02, -4.394310991494386e-02, 6.203761771227988e-02,
                    -4.379953896432789e-02, -1.350432300771676e+00, -2.841381200697882e-01},
            {-2.339932000167036e-01, -2.398193436796391e-01, -2.550329724297243e-01, -3.258822293568328e-01, 
                    -6.900615257773394e-01, -9.183828417907063e-02, -4.589916509376724e-01, -5.169339248550153e-01, 
                    -9.669287651917700e-02, -5.955464149338007e-01, -1.684626881936444e-01,-8.938381795399387e-01,
                    -1.836627555439310e+00, 4.092351912984802e-01, 5.069262139865550e-01, 3.869680883489559e-01,
                    5.684213704605494e-01, 1.093722668554482e+00, 1.559409505887975e-01, 7.786427775252081e-01,
                    7.827427262533837e-01, 1.298265486878574e-01, 1.115855895909325e+00, 3.141465348620462e-01, 
                    1.177976299609727e+00, 3.121214483237820e+00, -1.752419912817766e-01, -2.671068703069159e-01,
                    -1.319351159192316e-01, -2.425391411037165e-01, -4.036611427771429e-01, -6.410266640972688e-02,
                    -3.196511265875358e-01, -2.658088013983683e-01, -3.313367216868039e-02, -5.203094809755241e-01,
                    -1.456838466684018e-01, -2.841381200697882e-01, -1.284586927798510e+00},
            {-5.134241064678142e-01, 0, 0, 0, 0, 0, -3.556304699418148e-01, 0, 0, -1.577936365259994e-01, 0,
                    -4.776047528873696e-01, -5.134241064678142e-01, -1.752419912817766e-01, 0, 0, 0, 0, 0,
                    -1.118724650705426e-01, 0, 0, -6.336952621123403e-02, 0, -1.668013100538127e-01, 
                    -1.752419912817766e-01, 6.886660977495908e-01, 0, 0, 0, 0, 0, 4.675029350123574e-01, 0, 0,
                    2.211631627372334e-01, 0, 6.444060629411822e-01, 6.886660977495908e-01},
            {0, -4.245941572722073e-01, 0, 0, 0, 0, 0, -3.044093317111427e-01, 0, -1.201848255610645e-01, 0,
                    1.288750767564209e-01, -4.245941572722073e-01, 0, -2.671068703069159e-01, 0, 0, 0, 0, 0,
                    -1.405997644946125e-01, 0, -1.265071058123035e-01, 0, 1.778785558029974e-01, -2.671068703069159e-01,
                    0, 6.917010275791231e-01, 0, 0, 0, 0, 0, 4.450090962057551e-01, 0, 2.466919313733680e-01, 0,
                    -3.067536325594183e-01, 6.917010275791231e-01},
            {0, 0, -4.723434621286099e-01, 0, 0, 0, -3.634034085395212e-01, -1.089400535890887e-01, 0, 0, 0, 
                    -4.913708787835829e-01, -4.723434621286099e-01, 0, 0, -1.319351159192316e-01, 0, 0, 0,
                    -1.013813762234190e-01, -3.055373969581256e-02, 0, 0, 0, -1.371419865992518e-01, 
                    -1.319351159192316e-01, 0, 0, 6.042785780478415e-01, 0, 0, 0, 4.647847847629403e-01,
                    1.394937932849012e-01, 0, 0, 0, 6.285128653828347e-01, 6.042785780478415e-01},
            {0, 0, 0, -6.332755133037247e-01, 0, 0, 0, -2.837381051437496e-01, 0, -1.708411480346443e-01,
                    -1.786962601253307e-01, 5.264403055133537e-01, -6.332755133037247e-01, 0, 0, 0, 
                    -2.425391411037165e-01, 0, 0, 0, -9.465529720794330e-02, 0, -7.680809302082209e-02,
                    -7.107575087495113e-02, 2.084193617225931e-01, -2.425391411037165e-01, 0, 0, 0, 
                    8.758146544074412e-01, 0, 0, 0, 3.783934023516929e-01, 0, 2.476492410554664e-01, 
                    2.497720110002819e-01, -7.348596672359469e-01, 8.758146544074412e-01},
            {0, 0, 0, 0, -7.647425456521924e-01, 0, -1.564697226414847e-01, 0, -1.362559729931728e-01, 
                    -3.387542302189502e-01,
                    -1.332626197985847e-01, -7.693644428407020e-01, -7.647425456521924e-01, 0, 0, 0, 0, 
                    -4.036611427771429e-01, 0,
                    -4.229461888384730e-02, 0, -3.313367216868039e-02, -2.536247559311645e-01, -7.460809579345069e-02,
                    -3.148523454540451e-01, -4.036611427771429e-01, 0, 0, 0, 0, 1.168403688429335e+00, 0,
                    1.987643415253320e-01, 0, 1.693896451618532e-01, 5.923789861501148e-01, 2.078707155920354e-01, 
                    1.084216788294747e+00, 1.168403688429335e+00},
            {0, 0, 0, 0, 0, -1.575409147760524e-01, -1.575409147760524e-01, 0, 0, 0, 0, -1.269132097036227e-01,
                    -1.575409147760524e-01, 0, 0, 0, 0, 0, -6.410266640972688e-02, -6.410266640972688e-02, 0, 0, 0, 0,
                    -5.164039548826907e-02, -6.410266640972688e-02, 0, 0, 0, 0, 0, 2.216435811857792e-01,
                    2.216435811857792e-01, 0, 0, 0, 0, 1.785536051918917e-01, 2.216435811857792e-01},
            {-3.556304699418148e-01, 0, -3.634034085395212e-01, 0, -1.564697226414847e-01, -1.575409147760524e-01,
                    -1.033044515898873e+00, 0, 0, 0, 0, -9.221253113530495e-01, -1.033044515898873e+00,
                    -1.118724650705426e-01, 0, -1.013813762234190e-01, 0, -4.229461888384730e-02, 
                    -6.410266640972688e-02, -3.196511265875358e-01, 0, 0, 0, 0, -2.711845976337011e-01,
                    -3.196511265875358e-01, 4.675029350123574e-01, 0, 4.647847847629403e-01, 0, 1.987643415253320e-01,
                    2.216435811857792e-01, 1.352695642486409e+00, 0, 0, 0, 0, 1.193309908986751e+00, 1.352695642486409e+00},
            {0, -3.044093317111427e-01, -1.089400535890887e-01, -2.837381051437496e-01, 0, 0, 0, -6.970874904439810e-01,
                    0, 0, 0, -5.873544561580638e-03, -6.970874904439810e-01, 0, -1.405997644946125e-01,
                    -3.055373969581256e-02, -9.465529720794330e-02, 0, 0, 0, -2.658088013983683e-01, 0, 0, 0, 
                    1.275150873090487e-02, -2.658088013983683e-01, 0, 4.450090962057551e-01, 1.394937932849012e-01,
                    3.783934023516929e-01, 0, 0, 0, 9.628962918423493e-01, 0, 0, 0, -6.877964169324305e-03,
                    9.628962918423493e-01},
            {0, 0, 0, 0, -1.362559729931728e-01, 0, 0, 0, -1.362559729931728e-01, 0, 0, -1.807077455020609e-01,
                    -1.362559729931728e-01, 0, 0, 0, 0, -3.313367216868039e-02, 0, 0, 0, -3.313367216868039e-02,
                    0, 0, -4.394310991494386e-02, -3.313367216868039e-02, 0, 0, 0, 0, 1.693896451618532e-01, 0, 0, 0,
                    1.693896451618532e-01, 0, 0, 2.246508554170048e-01, 1.693896451618532e-01},
            {-1.577936365259994e-01, -1.201848255610645e-01, 0, -1.708411480346443e-01, -3.387542302189502e-01, 0, 0, 0, 
                    0, -7.875738403406585e-01, 0, -5.747012478180653e-02, -7.875738403406585e-01, 
                    -6.336952621123403e-02, -1.265071058123035e-01, 0, -7.680809302082209e-02, -2.536247559311645e-01, 
                    0, 0, 0, 0, -5.203094809755241e-01, 0, 6.203761771227988e-02, -5.203094809755241e-01, 
                    2.211631627372334e-01, 2.466919313733680e-01, 0, 2.476492410554664e-01, 5.923789861501148e-01, 0, 0,
                    0, 0, 1.307883321316182e+00, 0, -4.567492930473274e-03, 1.307883321316182e+00},
            {0, 0, 0, -1.786962601253307e-01, -1.332626197985847e-01, 0, 0, 0, 0, 0, -3.119588799239154e-01, 
                    -4.376117574700500e-02, -3.119588799239154e-01, 0, 0, 0, -7.107575087495113e-02, 
                    -7.460809579345069e-02, 0, 0, 0, 0, 0, -1.456838466684018e-01, -4.379953896432789e-02,
                    -1.456838466684018e-01, 0, 0, 0, 2.497720110002819e-01, 2.078707155920354e-01, 0, 0, 0, 0, 0, 
                    4.576427265923173e-01, 8.756071471133292e-02, 4.576427265923173e-01},
            {-4.776047528873696e-01, 1.288750767564209e-01, -4.913708787835829e-01, 5.264403055133537e-01,
                    -7.693644428407020e-01, -1.269132097036227e-01, -9.221253113530495e-01, -5.873544561580638e-03, 
                    -1.807077455020609e-01, -5.747012478180653e-02, -4.376117574700500e-02, -3.255148149774855e+00, 
                    -1.209937901945503e+00, -1.668013100538127e-01, 1.778785558029974e-01, -1.371419865992518e-01,
                    2.084193617225931e-01, -3.148523454540451e-01, -5.164039548826907e-02, -2.711845976337011e-01,
                    1.275150873090487e-02, -4.394310991494386e-02, 6.203761771227988e-02, -4.379953896432789e-02,
                    -1.350432300771676e+00, -2.841381200697882e-01, 6.444060629411822e-01, -3.067536325594183e-01,
                    6.285128653828347e-01, -7.348596672359469e-01, 1.084216788294747e+00, 1.785536051918917e-01,
                    1.193309908986751e+00, -6.877964169324305e-03, 2.246508554170048e-01, -4.567492930473274e-03,
                    8.756071471133292e-02, 4.605580450546531e+00, 1.494076022015291e+00},
            {-5.134241064678142e-01, -4.245941572722073e-01, -4.723434621286099e-01, -6.332755133037247e-01, 
                    -7.647425456521924e-01, -1.575409147760524e-01, -1.033044515898873e+00, -6.970874904439810e-01,
                    -1.362559729931728e-01, -7.875738403406585e-01, -3.119588799239154e-01, -1.209937901945503e+00, 
                    -2.965920699600602e+00, -1.752419912817766e-01, -2.671068703069159e-01, -1.319351159192316e-01,
                    -2.425391411037165e-01, -4.036611427771429e-01, -6.410266640972688e-02, -3.196511265875358e-01,
                    -2.658088013983683e-01, -3.313367216868039e-02, -5.203094809755241e-01, -1.456838466684018e-01,
                    -2.841381200697882e-01, -1.284586927798510e+00, 6.886660977495908e-01, 6.917010275791231e-01,
                    6.042785780478415e-01, 8.758146544074412e-01, 1.168403688429335e+00, 2.216435811857792e-01,
                    1.352695642486409e+00, 9.628962918423493e-01, 1.693896451618532e-01, 1.307883321316182e+00,
                    4.576427265923173e-01, 1.494076022015291e+00, 4.250507627399111e+00}};
    double[] xyOctaveEnum = new double[] {9.620924743292461e-01, -1.001473609414206e+00, 1.941359866164636e+00, 
            -1.392462149382027e+00, 9.907653127763741e-01, 6.641395733685506e-01, 2.914315618791304e+00,
            -6.885014410958616e-01, 6.520709855752358e-01, -1.016976513955447e+00, 3.035128185273430e-01,
            8.580914866748653e+00, 2.164421467842577e+00, 1.242752500606185e-01, 1.029109560248360e+00,
            -8.799294656923946e-01, -1.206963767577102e+00, -1.940174306923739e+00, -3.104838225804721e-01,
            -7.200539445688148e-01, 4.077870956751905e-01, -3.014265734143996e-01, -1.958189800957026e+00,
            -6.122833291996792e-01, -1.939669339978112e+00, -3.184166552464729e+00, -1.086367724389864e+00,
            -2.763595083415488e-02, -1.061430400472241e+00, 2.599425916959129e+00,
            9.494089941473658e-01, -3.536557507880789e-01, -2.194261674222489e+00, 2.807143454206714e-01,
            -3.506444121608359e-01, 2.975166314912472e+00, 3.087705106723369e-01, -6.641245526770548e+00,
            1.019745084622156e+00};
    compareHessXYOctave(fileEnum, betaInitEnum, hessOctaveEnum, xyOctaveEnum);

    // preparation for first test with all numeric columns
    String fileNum = "smalldata/glm_test/multinomial_3_cols_num_3_class_20Rows.csv";
    double[] betaInit = new double[]{0.387797, 0.269003, 0.444987, 0.403535, 0.129472, 0.826823, 0.221008, 0.402504,
            0.378747, 0.137892, 0.129652, 0.024352};
    double[][] hessOctave = new double[][] {{3.077923688343536e+01,-8.685803439702172e-01,-1.697824483926594e+00,
            -1.330913565153130e-02,-1.677233669317430e+01,-1.873981054238402e-01,1.802337255199468e+00,
            1.870571937361168e+00,-1.400690019026108e+01,1.055978449394056e+00,-1.045127712728748e-01,
            -1.857262801709639e+00}, {-0.8685803440,26.5888012004,2.9993618574,-2.8050586367,-0.1873981054,
            -14.4601337537,-1.1594200164,-2.5266824574,1.0559784494,-12.1286674467,-1.8399418410,5.3317410941},
            {-1.6978244839,2.9993618574,29.9811292986,2.4235167078,1.8023372552,-1.1594200164,-17.4504288380,
                    -2.5728170363,-0.1045127713,-1.8399418410,-12.5307004605,0.1493003285},
            {-0.0133091357,-2.8050586367,2.4235167078,23.6758820685,1.8705719374,-2.5266824574,-2.5728170363,
                    -13.3960348550,-1.8572628017,5.3317410941,0.1493003285,-10.2798472135}, {-16.7723366932,
            -0.1873981054,1.8023372552,1.8705719374,27.9399464075,0.8369202727,-2.0341028546,-3.0680161449,
            -11.1676097144,-0.6495221673,0.2317655994,1.1974442075},
            {-0.1873981054,-14.4601337537,-1.1594200164,-2.5266824574,0.8369202727,24.7780082243,1.6806632958,
                    2.7738188490,-0.6495221673,-10.3178744706,-0.5212432794,-0.2471363916},
            {1.8023372552,-1.1594200164,-17.4504288380,-2.5728170363,-2.0341028546,1.6806632958,29.5494884092,
                    0.5605243917,0.2317655994,-0.5212432794,-12.0990595712,2.0122926446},
            {1.8705719374,-2.5266824574,-2.5728170363,-13.3960348550,-3.0680161449,2.7738188490,0.5605243917,
                    22.4852713692,1.1974442075,-0.2471363916,2.0122926446,-9.0892365142},{-14.0069001903,
            1.0559784494,-0.1045127713,-1.8572628017,-11.1676097144,-0.6495221673,0.2317655994,1.1974442075,
            25.1745099046,-0.4064562821,-0.1272528281,0.6598185942},{1.0559784494,-12.1286674467,-1.8399418410,
            5.3317410941,-0.6495221673,-10.3178744706,-0.5212432794,-0.2471363916,-0.4064562821,22.4465419173,
            2.3611851204,-5.0846047024},{-0.1045127713,-1.8399418410,-12.5307004605,0.1493003285,0.2317655994,
            -0.5212432794,-12.0990595712,2.0122926446,-0.1272528281,2.3611851204,24.6297600317,-2.1615929731},
            {-1.8572628017,5.3317410941,0.1493003285,-10.2798472135,1.1974442075,-0.2471363916,2.0122926446,
                    -9.0892365142,0.6598185942,-5.0846047024,-2.1615929731,19.3690837277}};

    double[] xyOctave = new double[]{ 3.723724862851886e+01, 1.909982057504991e+01, 4.830201387562915e+00,
            -3.881247433218718e+00, 1.101789544111538e+00, 4.130113056508980e+00, 2.516354284946583e+00,
            1.284557939124202e+00, -3.833903817263046e+01, -2.322993363155884e+01, -7.346555672509493e+00,
            2.596689494094514e+00};
    compareHessXYOctave(fileNum, betaInit, hessOctave, xyOctave);    
    
  }

  public void compareHessXYOctave(String filename, double[] beta, double[][] hessOctave, double[] xyOctave) {
    Scope.enter();
    double threshold = 1e-6;
    try {
      Frame fr = parse_test_file(filename);
      if (fr.vec("C1").isInt()) { // massage frame for enum columns
        Vec v = fr.remove("C1");
        fr.add("C1", v.toCategoricalVec());
        v.remove();
        if (fr.vec("C2").isInt()) {
          Vec v1 = fr.remove("C2");
          fr.add("C2", v1.toCategoricalVec());
          v1.remove();
        }
      }
      fr.add("C3", fr.remove("C3"));
      Vec v = fr.remove("C4");
      fr.add("C4", v.toCategoricalVec());
      v.remove();
      DKV.put(fr); // always remember to put it to DKV after change!
      Scope.track(fr);
      GLMParameters params = new GLMParameters(Family.multinomial);
      params._response_column = fr._names[fr.numCols()-1];
      params._ignored_columns = new String[]{};
      params._train = fr._key;
      params._lambda = new double[]{0.5};
      params._alpha = new double[]{0.5};
      params._solver = Solver.IRLSM_SPEEDUP_TEST;
      params._standardize = false;
      int nclass = 3;

      GLMModel.GLMWeightsFun glmw = new GLMModel.GLMWeightsFun(params);
      DataInfo dinfo = new DataInfo(fr, null, 1, true, DataInfo.TransformType.NONE,
              DataInfo.TransformType.NONE, true, false, false, false,
              false, false);
      int ncoeffPClass = dinfo.fullN()+1;
      double sumExp = 0;
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
      // check xy
      TestUtil.checkArrays(xyOctave, xy, threshold);
      TestUtil.checkArrays(xyOctave, gmt._xy, threshold);
      // check hessian
      double[][] glmHessian = gmt.getGram().getXX();
      checkDoubleArrays(hessOctave, glmHessian, threshold);
      checkDoubleArrays(hessOctave, hessian, threshold);
      assertEquals(manualLLH, gmt._likelihood, threshold);
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
   * 2. ADMM implementation using all columns.  This is done by comparing coefficient updates using all columns with
   * weight generated using IRLSM_SPEEDUP.
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

  public double[][] convert1DbetaTo2D(double[] beta, int nclass, int ncoeffPClass) {
    double[][] beta2D = new double[nclass][ncoeffPClass];
    for (int classInd = 0; classInd < nclass; classInd++) {
      System.arraycopy(beta,classInd*ncoeffPClass, beta2D[classInd], 0, ncoeffPClass);
    }
    return beta2D;
  }
  public double manualLikelihoodGradient(double[] initialBeta, double[] gradient, double reg, double l2pen,
                                         DataInfo dinfo, int nclass, int ncoeffPClass) {
    double likelihood = 0;
    int numRows = (int) dinfo._adaptedFrame.numRows();
    int respInd = dinfo._adaptedFrame.numCols()-1;
    double[] etas = new double[nclass];
    double[] probs = new double[nclass+1];
/*    double[][] multinomialBetas = new double[nclass][ncoeffPClass];
    for (int classInd = 0; classInd < nclass; classInd++) {
      System.arraycopy(initialBeta,classInd*ncoeffPClass, multinomialBetas[classInd], 0, ncoeffPClass);
    }*/
    double[][] multinomialBetas = convert1DbetaTo2D(initialBeta, nclass, ncoeffPClass);

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
      params._solver = Solver.IRLSM_SPEEDUP_TEST;

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
  
  /* 3. Verify ADMM implementation for active cols.  Using datasets with all columns used for response generation, all
   * columns are active.  The results from IRLSM_SPEEDUP and IRLSM_SPEEDUP2 should equal in this case with no
   * regularization, l1 only, l2 only and both.
   */
  @Test
  public void testMultinomialActiveCols() {
    String filename = "smalldata/glm_test/multinomial_3_cols_num_3_class_20Rows.csv"; // all numeric columns
    compareWeightUpdates2Solvers(filename, 100, Solver.IRLSM_SPEEDUP_TEST, Solver.IRLSM_NATIVE,
            0.000001, 0.5);  // compare l1+l2 penalty, alpha = 0.5
    compareWeightUpdates2Solvers(filename, 100, Solver.IRLSM_SPEEDUP_TEST, Solver.IRLSM_NATIVE,
            0.000001, 1.0);  // compare l1 penalty only, alpha = 1
    compareWeightUpdates2Solvers(filename, 100, Solver.IRLSM_SPEEDUP_TEST, Solver.IRLSM_NATIVE,
            0.0001, 0.0);  // compare l2 penalty only, alpha = 0
    compareWeightUpdates2Solvers(filename, 100, Solver.IRLSM_SPEEDUP_TEST, Solver.IRLSM_NATIVE, 
            0.0, 0.0);  // compare without regularization
    
  }

  @Test
  public void testMultinomialActiveColsEnums() {
    String filename = "smalldata/glm_test/multinomial_3_classes_2enum_1num_col_200Rows.csv";; // all numeric columns
    compareWeightUpdates2Solvers(filename, 100, Solver.IRLSM_SPEEDUP_TEST, Solver.IRLSM_NATIVE,
            0.000001, 0.5);  // compare l1+l2 penalty, alpha = 0.5
    compareWeightUpdates2Solvers(filename, 100, Solver.IRLSM_SPEEDUP_TEST, Solver.IRLSM_NATIVE,
            0.000001, 1.0);  // compare l1 penalty only, alpha = 1
    compareWeightUpdates2Solvers(filename, 100, Solver.IRLSM_SPEEDUP_TEST, Solver.IRLSM_NATIVE,
            0.0001, 0.0);  // compare l2 penalty only, alpha = 0
    compareWeightUpdates2Solvers(filename, 100, Solver.IRLSM_SPEEDUP_TEST, Solver.IRLSM_NATIVE,
            0.0, 0.0);  // compare without regularization

  }

  public void compareWeightUpdates2Solvers(String filename, int numIter, Solver solver1, Solver solver2, double lambda, double alpha) {
    Scope.enter();
    double threshold = 1e-6;
    try {
      Frame fr = parse_test_file(filename);
      if (fr.vec("C1").isInt()) { // massage frame for enum columns
        Vec v = fr.remove("C1");
        fr.add("C1", v.toCategoricalVec());
        v.remove();
        if (fr.vec("C2").isInt()) {
          Vec v1 = fr.remove("C2");
          fr.add("C2", v1.toCategoricalVec());
          v1.remove();
        }
      }
      fr.add("C3", fr.remove("C3"));
      Vec v = fr.remove("C4");
      fr.add("C4", v.toCategoricalVec());
      v.remove();
      DKV.put(fr); // always remember to put it to DKV after change!
      Scope.track(fr);

      GLMParameters params = new GLMParameters(Family.multinomial);
      params._response_column = fr._names[fr.numCols()-1];
      params._ignored_columns = new String[]{};
      params._train = fr._key;
      params._lambda = new double[]{lambda};
      params._alpha = new double[]{alpha};
      params._solver = Solver.IRLSM_SPEEDUP_TEST;
      params._standardize = false;
      params._max_iterations = numIter;
      params._use_all_factor_levels=true;
      params._solver = solver1;
      GLMModel model1 = new GLM(params).trainModel().get();
      Scope.track_generic(model1);
      params._solver = solver2;
      GLMModel model2 = new GLM(params).trainModel().get();
      Scope.track_generic(model2);
      TestUtil.checkArrays(model1.beta(), model2.beta(), threshold);
    } finally {
      Scope.exit();
    }
  }
  
  /* 3. Verify ADMM implementation using only Active Columns */
  @Test
  public void testMultinomialADMMSpeedUp(){
    Scope.enter();
    Frame fr, test;
    Random rand = new Random();
    long seed = 12345;
    rand.setSeed(seed);
    double tol = 1e-3;
    double lambda = 1.104374697155361E-4;
    double lambdaNative = lambda/3;

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
      
      // build admm model with only activeCols for both regs
      GLMModel admmbothreg2 = checkADMM(fr, test, lambdaNative,0.5, true, Solver.IRLSM_NATIVE);
      Scope.track_generic(admmbothreg2);
      GLMModel admmbothreg2IRLSMO = checkADMM(fr, test, lambda,0.5, true, Solver.IRLSM);
      Scope.track_generic(admmbothreg2IRLSMO);
      checkLoglossMeanError(admmbothreg2, admmbothreg2IRLSMO, tol);     
      
      
      GLMModel admml2pen2 = checkADMM(fr, test, lambdaNative,0, true, Solver.IRLSM_NATIVE);
      Scope.track_generic(admml2pen2);  // includes all predictors
      GLMModel admml2penIRLSMO = checkADMM(fr, test, lambda,0, true, Solver.IRLSM);
      Scope.track_generic(admml2penIRLSMO);
      checkLoglossMeanError(admml2pen2, admml2penIRLSMO, tol);
      
      GLMModel admml1pen2 = checkADMM(fr, test, lambdaNative,1, true, Solver.IRLSM_NATIVE);
      Scope.track_generic(admml1pen2);
      GLMModel admml1penIRLSMO = checkADMM(fr, test, lambda,1, true, Solver.IRLSM);
      Scope.track_generic(admml1penIRLSMO);
      checkLoglossMeanError(admml1pen2, admml1penIRLSMO, tol);
    } finally {
      Scope.exit();
    }
  }
  
  // make sure IRLSM_SPEEDUP performs better or within tolerance of original IRLSM performance
  public void checkLoglossMeanError(GLMModel model1, GLMModel model2, double tol) {
    double lessPerClassError = model1.mean_per_class_error() - model2.mean_per_class_error();
    if (lessPerClassError < 0) 
      System.out.println("IRLSM_SPEEDUP mean_per_class_error is lower for validation set than original IRLSM.");
    else
      System.out.println("IRLSM_SPEEDUP mean_per_class_error: "+model1.mean_per_class_error()+". IRLSM mean_per_class_error: "+model2.mean_per_class_error());
    assertTrue ((lessPerClassError < 0) || (Math.abs(lessPerClassError) < tol));
    double lessLogLoss = model1.logloss()-model2.logloss(); // from validation set
    if (lessLogLoss < 0)
      System.out.println("IRLSM_SPEEDUP logloss is lower for validation set than original IRLSM.");
    else
      System.out.println("IRLSM_SPEEDUP logloss: "+model1.logloss()+". IRLSM mean_per_class_error: "+model2.logloss());
    assertTrue ((lessLogLoss < 0) || (Math.abs(lessLogLoss) < tol));
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
