package hex.gam;

import hex.CreateFrame;
import hex.glm.GLMModel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.ArrayList;

import static hex.gam.GamTestPiping.getModel;
import static hex.gam.GamTestPiping.massageFrame;
import static hex.glm.GLMModel.GLMParameters.Family.*;
import static org.junit.Assert.assertTrue;
import static water.TestUtil.parseTestFile;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class GamMojoModelTest {
  public static final double _tol = 1e-6;

  // test and make sure that quasibinomial mojo works
  @Test
  public void testQuasibinomial() {
    Scope.enter();
    try {
      final Frame fr = Scope.track(parseTestFile("smalldata/glm_test/prostate_cat_replaced.csv"));
      DKV.put(fr);
      final GAMModel.GAMParameters params = new GAMModel.GAMParameters();
      params._response_column = "CAPSULE";
      params._family = quasibinomial;
      params._ignored_columns = new String[]{"ID"};
      params._gam_columns = new String[]{"PSA"};
      params._num_knots = new int[]{5};
      params._train = fr._key;
      params._link = GLMModel.GLMParameters.Link.logit;
      final GAMModel model = new GAM(params).trainModel().get();      
      Frame predictFrame = Scope.track(model.score(fr));
      Scope.track_generic(model);
      assertTrue(model.testJavaScoring(fr, predictFrame, _tol));
    } finally {
      Scope.exit();
    }
  }
  
  // test and make sure the h2opredict, mojo predict agrees with binomial dataset that includes
  // both enum and numerical datasets for the binomial family
  @Test
  public void testBinomialPredMojo() {
    Scope.enter();
    try {
      // test for binomial
      String[] ignoredCols = new String[]{"C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C11", "C12", "C13", "C14",
              "C15", "C16", "C17", "C18", "C19", "C20"};
      String[] gamCols = new String[]{"C11", "C12", "C13"};
      Frame trainBinomial = Scope.track(massageFrame(parseTestFile("smalldata/glm_test/binomial_20_cols_10KRows.csv"),
              binomial));
      DKV.put(trainBinomial);
      GAMModel binomialModel = getModel(binomial,
              parseTestFile("smalldata/glm_test/binomial_20_cols_10KRows.csv"), "C21",
              gamCols, ignoredCols, new int[]{5, 5, 5}, new int[]{0, 0, 0}, false, true,
              new double[]{1, 1, 1}, new double[]{0, 0, 0}, new double[]{0, 0, 0}, true, null,
              null, false);
      Scope.track_generic(binomialModel);
      binomialModel._output._training_metrics = null; // force prediction threshold of 0.5
      Frame predictBinomial = Scope.track(binomialModel.score(trainBinomial));
      assertTrue(binomialModel.testJavaScoring(trainBinomial, predictBinomial, _tol));
    } finally {
      Scope.exit();
    }
  }

  // test and make sure the h2opredict, mojo predict agrees with gaussian dataset that includes
  // both enum and numerical datasets for the gaussian family
  @Test
  public void testGaussianPredMojo() {
    Scope.enter();
    try {
      String[] ignoredCols = new String[]{"C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C11", "C12", "C13", "C14",
              "C15", "C16", "C17", "C18", "C19", "C20"};
      String[] gamCols = new String[]{"C11", "C12", "C13"};
      Frame trainGaussian = Scope.track(massageFrame(parseTestFile("smalldata/glm_test/gaussian_20cols_10000Rows.csv"), gaussian));
      DKV.put(trainGaussian);
      GAMModel gaussianmodel = getModel(gaussian,
              parseTestFile("smalldata/glm_test/gaussian_20cols_10000Rows.csv"), "C21",
              gamCols, ignoredCols, new int[]{5, 5, 5}, new int[]{0, 0, 0}, false, true,
              new double[]{1, 1, 1}, new double[]{0, 0, 0}, new double[]{0, 0, 0}, true, null,null, true);
      Scope.track_generic(gaussianmodel);
      Frame predictGaussian = Scope.track(gaussianmodel.score(trainGaussian));
      Frame predictG = predictGaussian.subframe(new String[]{"predict"});
      Scope.track(predictG);

      assertTrue(gaussianmodel.testJavaScoring(trainGaussian, predictG, _tol)); // compare scoring result with mojo
    } finally {
      Scope.exit();
    }
  }

  // test and make sure the h2opredict, mojo predict agrees with multinomial dataset that includes
  // both enum and numerical datasets for the multinomial family
  @Test
  public void testMultinomialModelMojo() {
    Scope.enter();
    try {
      // multinomial
      String[] ignoredCols = new String[]{"C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"};
      String[] gamCols = new String[]{"C6", "C7", "C8"};
      Frame trainMultinomial = Scope.track(massageFrame(parseTestFile("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv"), multinomial));
      DKV.put(trainMultinomial);
      GAMModel multinomialModel = getModel(multinomial,
              parseTestFile("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv"),
              "C11", gamCols, ignoredCols, new int[]{5, 5, 5}, new int[]{0, 0, 0}, false,
              true, new double[]{1, 1, 1}, new double[]{0, 0, 0}, new double[]{0, 0, 0},
              true, null,null, false);
      Scope.track_generic(multinomialModel);
      Frame predictMult = Scope.track(multinomialModel.score(trainMultinomial));
      assertTrue(multinomialModel.testJavaScoring(trainMultinomial, predictMult, _tol)); // compare scoring result with mojo
    } finally {
      Scope.exit();
    }
  }

  // test and make sure that tweedie mojo works.
  @Test
  public void testTweedie() {
    Scope.enter();
    try {
      final Frame fr = Scope.track(parseTestFile("smalldata/glm_test/auto.csv"));
      final GAMModel.GAMParameters params = new GAMModel.GAMParameters();
      params._response_column = "y";
      params._family = tweedie;
      params._link = GLMModel.GLMParameters.Link.tweedie;
      params._tweedie_variance_power = 1.5;
      params._tweedie_link_power = 0.5;
      params._ignored_columns = new String[]{"ID"};
      params._gam_columns = new String[]{"x.TRAVTIME"};
      params._num_knots = new int[]{5};
      params._train = fr._key;
      final GAMModel model = new GAM(params).trainModel().get();
      Scope.track_generic(model);
      Frame predictFrame = Scope.track(model.score(fr));
      System.out.println("Starting gam tweedie mojo test...");
      assertTrue(model.testJavaScoring(fr, predictFrame, _tol));
      System.out.println("successfully completed gam tweedie mojo test...");
    } finally {
      Scope.exit();
    }
  }
  
  public GAMModel.GAMParameters buildGamParams(Frame train, GLMModel.GLMParameters.Family fam) {
    GAMModel.GAMParameters paramsO = new GAMModel.GAMParameters();
    paramsO._train = train._key;
    paramsO._lambda_search = false;
    paramsO._response_column = "response";
    paramsO._lambda = new double[]{0};
    paramsO._alpha = new double[]{0.001};  // l1pen
    paramsO._objective_epsilon = 1e-6;
    paramsO._beta_epsilon = 1e-4;
    paramsO._standardize = false;
    paramsO._family = fam;
    paramsO._gam_columns =  chooseGamColumns(train, 3);
    return paramsO;
  }

  public String[] chooseGamColumns(Frame trainF, int maxGamCols) {
    int gamCount=0;
    ArrayList<String> numericCols = new ArrayList<>();
    String[] colNames = trainF.names();
    for (String cnames : colNames) {
      if (trainF.vec(cnames).isNumeric() && !trainF.vec(cnames).isInt()) {
        numericCols.add(cnames);
        gamCount++;
      }
      if (gamCount >= maxGamCols)
        break;
    }
    String[] gam_columns = new String[numericCols.size()];
    return numericCols.toArray(gam_columns);
  }

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Test
  public void testNaN() {
    Scope.enter();
    try {
      final Frame fr = Scope.track(createTrainFrameWithNaNs());
      final GAMModel.GAMParameters params = new GAMModel.GAMParameters();
      int cidx = 0;
      String[] gam_columns = new String[3];
      for (int i = 0; i < fr.numCols(); i++) {
        if (!fr.name(i).equals("response")&& fr.vec(i).get_type() == Vec.T_NUM) {
          gam_columns[cidx++] = fr.name(i);
          if (cidx == gam_columns.length) break;
        }
      }
      params._response_column = "response";
      params._missing_values_handling = GLMModel.GLMParameters.MissingValuesHandling.MeanImputation;
      params._family = gaussian;
      params._gam_columns = gam_columns;
      params._scale = new double[] {0.001, 0.001, 0.001};
      params._train = fr._key;
      final GAMModel model = new GAM(params).trainModel().get();
      Scope.track_generic(model);
      Frame predictFrame = Scope.track(model.score(fr));
      assertTrue(model.testJavaScoring(fr, predictFrame, _tol));
    } finally {
      Scope.exit();
    }
  }
  
  private Frame createTrainFrameWithNaNs() {
    CreateFrame cf = new CreateFrame();
    int numRows = 20000;
    int numCols = 11;
    cf.rows= numRows;
    cf.cols = numCols;
    cf.factors=10;
    cf.has_response = true;
    cf.positive_response = true;
    cf.missing_fraction = 0.5;
    cf.response_factors = 1;
    cf.seed = 2;
    System.out.println("Createframe parameters: rows: "+numRows+" cols:"+numCols+" seed: "+cf.seed);
    return cf.execImpl().get();
  }
}
