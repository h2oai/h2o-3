package hex.gam;

import hex.DataInfo;
import hex.SplitFrame;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.glm.GLMTask;
import hex.gram.Gram;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.ArrayUtils;

import static hex.gam.MatrixFrameUtils.GamUtils.equalColNames;
import static hex.genmodel.algos.gam.GamUtilsCubicRegression.locateBin;
import static hex.glm.GLMModel.GLMParameters.Family.*;
import static hex.glm.GLMModel.GLMParameters.GLMType.gam;
import static hex.glm.GLMModel.GLMParameters.GLMType.glm;
import static org.junit.Assert.assertEquals;

/***
 * Here I am going to test the following:
 * - model matrix formation with centering
 */
@RunWith(H2ORunner.class)
@CloudSize(1)
public class GamTestPiping extends TestUtil {
  /**
   * This test is to make sure that we carried out the expansion of ONE gam column to basis functions
   * correctly with and without centering.  I will compare the following with R runs:
   * 1. binvD generation;
   * 2. model matrix that contains the basis function value for each role of the gam column
   * 3. penalty matrix without centering
   * 4. model matrix after centering
   * 5. penalty matrix with centering.
   * <p>
   * I compared my results with the ones generated from R mgcv library.  At this point, I am not concerned with
   * model content, only the data transformation.
   */
  @Test
  public void test1GamTransform() {
    try {
      Scope.enter();
      String[] ignoredCols = new String[]{"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"};
      String[] gamCols = new String[]{"C6"};
      final GAMModel model = getModel(gaussian,
              Scope.track(parse_test_file("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv")),
              "C11", gamCols, ignoredCols, new int[]{5}, new int[]{0}, false,
              true, new double[]{1}, new double[]{0}, new double[]{0}, false, null,
              null, true);
      Scope.track_generic(model);
      final double[][] rBinvD = new double[][]{{1.5605080,
              -3.5620961, 2.5465468, -0.6524143, 0.1074557}, {-0.4210098, 2.5559955, -4.3258597, 2.6228736,
              -0.4319995}, {0.1047194, -0.6357626, 2.6244918, -3.7337994, 1.6403508}};
      final double[][] rS = new double[][]{{0.078482471, -0.17914814, 0.1280732, -0.03281181, 0.005404258}, {-0.179148137,
              0.48996127, -0.4772073, 0.19920397, -0.032809824},
              {0.128073216, -0.47720728, 0.7114729, -0.49778106, 0.135442250},
              {-0.032811808, 0.19920397, -0.4977811, 0.52407922, -0.192690331},
              {0.005404258, -0.03280982, 0.1354423, -0.19269033, 0.084653646}};
      final double[][] rScenter = new double[][]{{0.5448733349, -0.4278253, 0.1799158, -0.0009387815}, {-0.4278253091,
              0.7554630, -0.5087565, 0.1629793339}, {0.1799157665, -0.5087565, 0.4339205, -0.1867776214},
              {-0.0009387815, 0.1629793, -0.1867776, 0.1001323668}};

      TestUtil.checkDoubleArrays(model._output._binvD[0], rBinvD, 1e-6); // compare binvD generation
      TestUtil.checkDoubleArrays(model._output._penaltyMatrices[0], rS, 1e-6);  // compare penalty terms
      TestUtil.checkDoubleArrays(model._output._penaltyMatrices_center[0], rScenter, 1e-6);
      
      Frame rTransformedData = parse_test_file("smalldata/gam_test/multinomial_10_classes_10_cols_10000_Rows_train_C6Gam.csv");
      Scope.track(rTransformedData);

      Frame transformedDataC = ((Frame) DKV.getGet(model._output._gamTransformedTrainCenter));// compare model matrix with centering
      Scope.track(transformedDataC);
      Scope.track(transformedDataC.remove("C11"));
      Frame rTransformedDataC = parse_test_file("smalldata/gam_test/multinomial_10_classes_10_cols_10000_Rows_train_C6Gam_center.csv");
      Scope.track(rTransformedDataC);
      TestUtil.assertIdenticalUpToRelTolerance(transformedDataC, rTransformedDataC, 1e-2);
    } finally {
      Scope.exit();
    }
  }

  // This test is very similar to test1GamTransform except in this case, I provide the knots in a frame.  
  @Test
  public void testKnotsFromFrame() {
    try {
      Scope.enter();
      Frame knotsFrame = generate_real_only(1, 5, 0);
      Scope.track(knotsFrame);
      final double[][] knots = new double[][]{{-1.9990569949269443}, {-0.9814307533427584}, {0.025991586992542004}, 
              {1.0077098743127828}, {1.999422899675758}};
      new ArrayUtils.CopyArrayToFrame(0,0,knots.length, knots).doAll(knotsFrame);
      DKV.put(knotsFrame);
      Scope.track(knotsFrame);

      String[] ignoredCols = new String[]{"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"};
      String[] gamCols = new String[]{"C6"};
      final GAMModel model = getModel(gaussian,
              Scope.track(parse_test_file("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv")),
              "C11", gamCols, ignoredCols, new int[]{5}, new int[]{0}, false,
              true, new double[]{1}, new double[]{0}, new double[]{0}, false, 
              new String[]{knotsFrame._key.toString()},null, true);
      Scope.track_generic(model);
      final double[][] rBinvD = new double[][]{{1.5605080,
              -3.5620961, 2.5465468, -0.6524143, 0.1074557}, {-0.4210098, 2.5559955, -4.3258597, 2.6228736,
              -0.4319995}, {0.1047194, -0.6357626, 2.6244918, -3.7337994, 1.6403508}};
      final double[][] rS = new double[][]{{0.078482471, -0.17914814, 0.1280732, -0.03281181, 0.005404258}, {-0.179148137,
              0.48996127, -0.4772073, 0.19920397, -0.032809824},
              {0.128073216, -0.47720728, 0.7114729, -0.49778106, 0.135442250},
              {-0.032811808, 0.19920397, -0.4977811, 0.52407922, -0.192690331},
              {0.005404258, -0.03280982, 0.1354423, -0.19269033, 0.084653646}};
      final double[][] rScenter = new double[][]{{0.5448733349, -0.4278253, 0.1799158, -0.0009387815}, {-0.4278253091,
              0.7554630, -0.5087565, 0.1629793339}, {0.1799157665, -0.5087565, 0.4339205, -0.1867776214},
              {-0.0009387815, 0.1629793, -0.1867776, 0.1001323668}};

      TestUtil.checkDoubleArrays(model._output._binvD[0], rBinvD, 1e-6); // compare binvD generation
      TestUtil.checkDoubleArrays(model._output._penaltyMatrices[0], rS, 1e-6);  // compare penalty terms
      TestUtil.checkDoubleArrays(model._output._penaltyMatrices_center[0], rScenter, 1e-6);

      Frame transformedDataC = ((Frame) DKV.getGet(model._output._gamTransformedTrainCenter));  // compare model matrix with centering
      Scope.track(transformedDataC);
      Scope.track(transformedDataC.remove("C11"));
      final Frame rTransformedDataC = parse_test_file("smalldata/gam_test/multinomial_10_classes_10_cols_10000_Rows_train_C6Gam_center.csv");
      Scope.track(rTransformedDataC);
      TestUtil.assertIdenticalUpToRelTolerance(transformedDataC, rTransformedDataC, 1e-2);
    } finally {
      Scope.exit();
    }
  }

  // This test is used to make sure that the scale parameter is used in calculating the penalty matrix for multinomial.
  // First,  one model is built with scale = 10, another model is built with scale = 0.1.  The ratio magnitude of 
  // penalty matrics between the two models should be 10/0.1 ~ 100.0
  @Test
  public void testScaleParameterMultinomial() {
    try {
      Scope.enter();
      final String[] ignoredCols = new String[]{"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"};
      final String[] gamCols = new String[]{"C6", "C7", "C8"};
      final Frame train = massageFrame(
              Scope.track(parse_test_file("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv")), multinomial);
      DKV.put(train);
      Scope.track(train);
      GAMModel.GAMParameters params = new GAMModel.GAMParameters();
      params._scale = new double[]{0.1, 0.1, 0.1};
      params._family = multinomial;
      params._response_column = "C11";
      params._max_iterations = 1;
      params._savePenaltyMat = true;
      params._ignored_columns = ignoredCols;
      params._gam_columns = gamCols;
      params._train = train._key;
      params._solver = GLMModel.GLMParameters.Solver.IRLSM;
      final GAMModel gam = new GAM(params).trainModel().get();
      double[][][] penaltyMat1 = gam._output._penaltyMatrices_center;
      Scope.track_generic(gam);
      params._scale = new double[]{10, 10, 10};
      final GAMModel gam2 = new GAM(params).trainModel().get();
      double[][][] penaltyMat2 = gam2._output._penaltyMatrices_center;
      Scope.track_generic(gam2);
      // ratio of penaltyMat2 and penaltyMat1 should be approximately 10/0.1 = 100
      assert penaltyMat1.length == penaltyMat2.length && penaltyMat1[0].length == penaltyMat2[0].length && 
              penaltyMat1[0][0].length == penaltyMat2[0][0].length: "penalty matrics with different scale parameters " +
              "should be the same but are not!";
      double averageRatios = 0;
      double numPenaltyEles = 0;
      for (int index0 = 0; index0 < penaltyMat1.length; index0++) {
        for (int index1 = 0; index1 < penaltyMat1[index0].length; index1++) {
          for (int index2 = 0; index2 < penaltyMat1[index0][index1].length; index2++) {
            averageRatios += penaltyMat2[index0][index1][index2] / penaltyMat1[index0][index1][index2];
            numPenaltyEles++;
          }
        }
      }
      assert Math.abs(averageRatios/numPenaltyEles-100) < 1e-6 : "Ratio of penaltyMat2 and penaltyMat1 should be " +
              "around 100 but is not.";
    } finally {
      Scope.exit();
    }
  }

  // This test is used to make sure that the scale parameter is used in calculating the penalty matrix for gaussian.
  // First,  one model is built with scale = 10, another model is built with scale = 0.1.  The ratio magnitude of 
  // penalty matrics between the two models should be 10/0.1 ~ 100.0
  @Test
  public void testScaleParameterGaussian() {
    try {
      Scope.enter();
      String[] ignoredCols = new String[]{"C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C11", "C12", "C13", "C14",
              "C15", "C16", "C17", "C18", "C19", "C20"};
      String[] gamCols = new String[]{"C11", "C12", "C13"};
      Frame train = Scope.track(massageFrame(parse_test_file("smalldata/glm_test/gaussian_20cols_10000Rows.csv"), gaussian));
      DKV.put(train);
      Scope.track(train);
      GAMModel.GAMParameters params = new GAMModel.GAMParameters();
      params._scale = new double[]{0.1, 0.1, 0.1};
      params._family = gaussian;
      params._response_column = "C21";
      params._max_iterations = 1;
      params._savePenaltyMat = true;
      params._ignored_columns = ignoredCols;
      params._gam_columns = gamCols;
      params._train = train._key;
      params._solver = GLMModel.GLMParameters.Solver.IRLSM;
      final GAMModel gam = new GAM(params).trainModel().get();
      double[][][] penaltyMat1 = gam._output._penaltyMatrices_center;
      Scope.track_generic(gam);
      params._scale = new double[]{10, 10, 10};
      final GAMModel gam2 = new GAM(params).trainModel().get();
      double[][][] penaltyMat2 = gam2._output._penaltyMatrices_center;
      Scope.track_generic(gam2);
      // ratio of penaltyMat2 and penaltyMat1 should be approximately 10/0.1 = 100
      assert penaltyMat1.length == penaltyMat2.length && penaltyMat1[0].length == penaltyMat2[0].length &&
              penaltyMat1[0][0].length == penaltyMat2[0][0].length: "penalty matrics with different scale parameters " +
              "should be the same but are not!";
      double averageRatios = 0;
      double numPenaltyEles = 0;
      for (int index0 = 0; index0 < penaltyMat1.length; index0++) {
        for (int index1 = 0; index1 < penaltyMat1[index0].length; index1++) {
          for (int index2 = 0; index2 < penaltyMat1[index0][index1].length; index2++) {
            averageRatios += penaltyMat2[index0][index1][index2] / penaltyMat1[index0][index1][index2];
            numPenaltyEles++;
          }
        }
      }
      assert Math.abs(averageRatios/numPenaltyEles-100) < 1e-6 : "Ratio of penaltyMat2 and penaltyMat1 should be " +
              "around 100 but is not.";
    } finally {
      Scope.exit();
    }
  }

  /**
   * This is the same test as in test1GamTransform except in this case we check out three gam columns instead of one.
   * In addition, we compare
   * 1. BinvD;
   * 2. penalty matrices after centering
   * 3. model matrix after centering.
   */
  @Test
  public void test3GamTransform() {
    try {
      Scope.enter();
      final String[] ignoredCols = new String[]{"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"};
      final String[] gamCols = new String[]{"C6", "C7", "C8"};
      final int numGamCols = gamCols.length;
      final GAMModel model = getModel(gaussian,
              Scope.track(parse_test_file("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv"))
              , "C11", gamCols, ignoredCols, new int[]{5, 5, 5}, new int[]{0,0,0},
              false, true, new double[]{1, 1, 1}, new double[]{0, 0, 0}, 
              new double[]{0, 0, 0}, false, null,null, true);
      Scope.track_generic(model);
      final double[][][] rBinvD = new double[][][]{{{1.5605080,
              -3.5620961, 2.5465468, -0.6524143, 0.1074557}, {-0.4210098, 2.5559955, -4.3258597, 2.6228736,
              -0.4319995}, {0.1047194, -0.6357626, 2.6244918, -3.7337994, 1.6403508}}, {{1.6212347, -3.6647127,
              2.5744837, -0.6390045, 0.1079989}, {-0.4304170, 2.5705123, -4.2598869, 2.5509266, -0.4311351}, {0.1082500,
              -0.6464846, 2.5538194, -3.6243725, 1.6087877}}, {{1.5676838, -3.6153068, 2.5754978, -0.6358007,
              0.1079259}, {-0.4150543, 2.5862931, -4.3172873, 2.5848160, -0.4387675}, {0.1045808, -0.6516658,
              2.5880211, -3.6755096, 1.6345735}}};
      final double[][][] rScenter = new double[][][]{{{0.5448733349, -0.4278253, 0.1799158, -0.0009387815}, {-0.4278253091,
              0.7554630, -0.5087565, 0.1629793339}, {0.1799157665, -0.5087565, 0.4339205, -0.1867776214},
              {-0.0009387815, 0.1629793, -0.1867776, 0.1001323668}}, {{0.5697707675, -0.4362176, 0.1755907,
              -0.0001031409}, {-0.4362176085, 0.7592479, -0.4915641, 0.1637712460}, {0.1755907088, -0.4915641, 0.4050463,
              -0.1811556223}, {-0.0001031409, 0.1637712, -0.1811556, 0.1003644978}}, {{0.550472199, -0.4347752, 0.1714131,
              -0.001386494}, {-0.434775190, 0.7641889, -0.4932521, 0.165206427}, {0.171413072, -0.4932521, 0.4094752,
              -0.184101847}, {-0.001386494, 0.1652064, -0.1841018, 0.101590357}}};

      for (int test = 0; test < numGamCols; test++) {
        TestUtil.checkDoubleArrays(model._output._binvD[test], rBinvD[test], 1e-6); // compare binvD generation
        TestUtil.checkDoubleArrays(model._output._penaltyMatrices_center[test], rScenter[test], 1e-6);
      }

      Frame transformedDataC = ((Frame) DKV.getGet(model._output._gamTransformedTrainCenter));  // compare model matrix with centering
      Scope.track(transformedDataC);
      Scope.track(transformedDataC.remove("C11"));
      Frame rTransformedDataC = parse_test_file("smalldata/gam_test/multinomial_10_classes_10_cols_10000_Rows_train_C6Gam_center.csv");
      rTransformedDataC.add(Scope.track(parse_test_file("smalldata/gam_test/multinomial_10_classes_10_cols_10000_Rows_train_C7Gam_center.csv")));
      rTransformedDataC.add(Scope.track(parse_test_file("smalldata/gam_test/multinomial_10_classes_10_cols_10000_Rows_train_C8Gam_center.csv")));
      Scope.track(rTransformedDataC);
      TestUtil.assertIdenticalUpToRelTolerance(transformedDataC, rTransformedDataC, 1e-2);
    } finally {
      Scope.exit();
    }
  }


  public static Frame massageFrame(Frame train, GLMModel.GLMParameters.Family family) {
    // set cat columns
    int numCols = train.numCols();
    int enumCols = (numCols - 1) / 2;
    for (int cindex = 0; cindex < enumCols; cindex++) {
      train.replace(cindex, train.vec(cindex).toCategoricalVec()).remove();
    }
    int response_index = numCols - 1;
    if (family.equals(binomial) || (family.equals(multinomial))) {
      train.replace((response_index), train.vec(response_index).toCategoricalVec()).remove();
    }
    return train;
  }
  
  public static GAMModel getModel(GLMModel.GLMParameters.Family family, Frame train, String responseColumn,
                           String[] gamCols, String[] ignoredCols, int[] numKnots, int[] bstypes, boolean saveZmat,
                           boolean savePenalty, double[] scale, double[] alpha, double[] lambda, 
                           boolean standardize, String[] knotsKey, Frame valid, boolean computePVal) {
    GAMModel gam = null;
    try {
      Scope.enter();
      train = massageFrame(train, family);
      DKV.put(train);
      Scope.track(train);

      GAMModel.GAMParameters params = new GAMModel.GAMParameters();
      params._standardize = standardize;
      params._knot_ids = knotsKey;
      params._scale = scale;
      params._family = family;
      params._response_column = responseColumn;
      params._max_iterations = 3;
      params._bs = bstypes;
      params._num_knots = numKnots;
      params._ignored_columns = ignoredCols;
      params._alpha = alpha;
      params._lambda = lambda;
      params._compute_p_values = computePVal;
      params._gam_columns = gamCols;
      params._train = train._key;
      params._family = family;
      params._link = GLMModel.GLMParameters.Link.family_default;
      params._saveZMatrix = saveZmat;
      params._keep_gam_cols = true;
      params._savePenaltyMat = savePenalty;
      params._solver = GLMModel.GLMParameters.Solver.IRLSM;
      if (valid!=null)
        params._valid = valid._key;
      gam = new GAM(params).trainModel().get();
      return gam;
    } finally {
      Scope.exit();
    }
  }

  public GAMModel getModel(GLMModel.GLMParameters.Family family, Frame train, String responseColumn,
                           String[] gamCols, String[] ignoredCols, int[] numKnots, int[] bstypes, boolean saveZmat,
                           boolean savePenalty, double[] scale, double[] alpha, double[] lambda,
                           boolean standardize, String[] knotsKey, Frame valid, boolean computePVal,
                           GLMModel.GLMParameters.Family autoRepresentingFamily) {
    
    
    
    computePVal = computePVal && !family.equals(multinomial) && !autoRepresentingFamily.equals(multinomial);
    return getModel(family, train, responseColumn, gamCols, ignoredCols, numKnots, bstypes, saveZmat,
            savePenalty, scale, alpha, lambda, standardize, knotsKey, valid, computePVal);
  }

  /**
   * The following test makes sure that we have added the smooth term to the gradient, hessian and objective
   * calculation to GAM for Gaussian and Multionomial.  All families use the same gram/beta structure except the
   * multinomial and ordinals.
   */
  @Test
  public void testGradientHessianObjTask() {
    try {
      Scope.enter();
      String[] ignoredCols = new String[]{"C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"};
      String[] gamCols = new String[]{"C6", "C7", "C8"};
      GLMModel.GLMParameters.Family[] fams = new GLMModel.GLMParameters.Family[]{multinomial, gaussian};     
      GLMModel.GLMParameters.Link[] links = new GLMModel.GLMParameters.Link[]{GLMModel.GLMParameters.Link.multinomial, 
              GLMModel.GLMParameters.Link.identity};
      for (int runIndex=0; runIndex < links.length; runIndex++) {
        GAMModel model = getModel(fams[runIndex],
                Scope.track(parse_test_file("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv")),
                "C11", gamCols, ignoredCols, new int[]{5, 5, 5}, new int[]{0, 0, 0}, false,
                true, new double[]{1, 1, 1}, new double[]{0}, new double[]{0}, 
                false, null,null, false);
        Scope.track_generic(model);
        Frame train = parse_test_file("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv");
        Scope.track(train);
        Frame dataFrame = ((Frame) DKV.getGet(model._output._gamTransformedTrainCenter)); // actual data used
        Scope.track(dataFrame.remove("C1"));
        Scope.track(dataFrame.remove("C2"));
        Scope.track(dataFrame.remove("C11"));
        dataFrame.prepend("C2", train.vec("C2").toCategoricalVec());
        dataFrame.prepend("C1", train.vec("C1").toCategoricalVec());
        if (fams[runIndex].equals(multinomial)) 
          dataFrame.add("C11", train.vec("C11").toCategoricalVec());
        else 
          dataFrame.add("C11", train.vec("C11"));
        Scope.track(dataFrame);

        GLMModel.GLMParameters glmParms = setGLMParamsFromGamParams(model._parms, dataFrame, fams[runIndex], 
                links[runIndex]);
        glmParms._max_iterations=3;
        DataInfo dinfo = new DataInfo(dataFrame, null, 1,
                glmParms._use_all_factor_levels || glmParms._lambda_search, glmParms._standardize ?
                DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE,
                true, false, false, false, false, false);
        DKV.put(dinfo._key, dinfo);
        Scope.track_generic(dinfo);
        checkHessian(dinfo, glmParms, model, fams[runIndex]);
        checkObjectiveGradients(dinfo, glmParms, model, fams[runIndex]);
      }
    } finally {
      Scope.exit();
    }
  }

  /***
   * This method will check the Hessian calculation is correct.  We copied the hessian implementation for GAM and then
   * perform our own manual calculation to make sure the two agrees.
   * 
   * @param dinfo
   * @param params
   * @param model
   * @param fam
   */
  public void checkHessian(DataInfo dinfo, GLMModel.GLMParameters params, GAMModel model,
                           GLMModel.GLMParameters.Family fam) {
    double[] betaG = model._output._model_beta;
    double[][] beta_multinomial = model._output._model_beta_multinomial;
    int nclass = fam.equals(gaussian)?1:beta_multinomial.length;
    if (fam.equals(multinomial)) {
      double[] nb = TestUtil.changeDouble2SingleArray(model._output._model_beta_multinomial);
      double maxRow = ArrayUtils.maxValue(nb);
      double sumExp = 0;
      int P = dinfo.fullN();
      int N = dinfo.fullN() + 1;
      for (int i = 1; i < nclass; ++i)
        sumExp += Math.exp(nb[i * N + P] - maxRow);
      Vec[] vecs = dinfo._adaptedFrame.anyVec().makeDoubles(2, new double[]{sumExp, maxRow});
      dinfo.addResponse(new String[]{"__glm_sumExp", "__glm_maxRow"}, vecs);
      DKV.put(dinfo);
    }
    int[][] gamCoeffIndices = new int[][]{{10, 11, 12, 13}, {14, 15, 16, 17}, {18, 19, 20, 21}};
    for (int classInd = 0; classInd < nclass; classInd++) {
      double[] beta = fam.equals(gaussian)?betaG:beta_multinomial[classInd];
      GLMTask.GLMIterationTask gt = new GLMTask.GLMIterationTask(null, dinfo, new GLMModel.GLMWeightsFun(params),
              beta, classInd).doAll(dinfo._adaptedFrame);
      GLMTask.GLMIterationTask gt2 = new GLMTask.GLMIterationTask(null, dinfo,
              new GLMModel.GLMWeightsFun(params), beta, classInd).doAll(dinfo._adaptedFrame);
      Integer[] activeCols = null;
      int[] activeColumns = dinfo.activeCols();
      if (activeColumns.length < dinfo.fullN()) { // columns are deleted
        activeCols = ArrayUtils.toIntegers(activeColumns, 0, activeColumns.length);
      }
      Gram gramGAM = gt2.getGram(); // add penalty contribution to gram
      gramGAM.addGAMPenalty(activeCols, model._output._penaltyMatrices_center, gamCoeffIndices);
      // manually add contribution from penalty terms
      double[][] gramGLM = gt.getGram().getXX();
      double[][][] penalty_mat = model._output._penaltyMatrices_center;
      int numGamCols = penalty_mat.length;
      for (int gamColInd = 0; gamColInd < numGamCols; gamColInd++) {
        int numKnots = penalty_mat[gamColInd].length;
        for (int beta1 = 0; beta1 < numKnots; beta1++) {
          Integer betaIndex = gamCoeffIndices[gamColInd][beta1]; // for multinomial
          if (activeCols != null)
            betaIndex = ArrayUtils.indexOf(activeCols, betaIndex);
          if (betaIndex < 0)
            continue;
          for (int beta2 = 0; beta2 < numKnots; beta2++) {
            Integer betaIndex2 = gamCoeffIndices[gamColInd][beta2]; // for multinomial
            if (activeCols != null)
              betaIndex2 = ArrayUtils.indexOf(activeCols, betaIndex2);
            if (betaIndex2 < 0)
              continue;
            gramGLM[betaIndex][betaIndex2] += penalty_mat[gamColInd][beta1][beta2] + penalty_mat[gamColInd][beta2][beta1];
          }
        }
      }
      TestUtil.checkDoubleArrays(gramGAM.getXX(), gramGLM, 1e-6);
    }
  }

  /**
   * compare GAM gradient generated with manually calculated gradient generated by:
   * 1.  Get gradient from GLM without GAM penalty matrix;
   * 2. manually calculate gradient contribution from GAM penalty matrix;
   * 3. add gradient from 1 and 2.
   * 
   * Compare with GAM gradient and pray that they are the same.
   * 
   * @param dinfo
   * @param glmParms
   * @param model
   */
  public void checkObjectiveGradients(DataInfo dinfo, GLMModel.GLMParameters glmParms, GAMModel model,
                                      GLMModel.GLMParameters.Family fam) {
    int[][] gamCoeffIndices = new int[][]{{10, 11, 12, 13}, {14, 15, 16, 17}, {18, 19, 20, 21}};
    double[][][] penalty_mat = model._output._penaltyMatrices_center;
    glmParms._glmType = gam;
    double[] beta = fam.equals(gaussian)?model._output._model_beta 
            :TestUtil.changeDouble2SingleArray(model._output._model_beta_multinomial);
    GLM.GLMGradientInfo ginfo = new GLM.GLMGradientSolver(null, glmParms, dinfo, 0, null,
            penalty_mat, gamCoeffIndices).getGradient(beta);
    double[] gamGradient = ginfo._gradient;
    double objGam = ginfo._objVal;
    
    glmParms._glmType = glm;  // object glm gradient/objective and manually add gam penalty contributions
    GLM.GLMGradientInfo ginfoGLM = new GLM.GLMGradientSolver(null, glmParms, dinfo, 0, null,
            null, null).getGradient(beta);
    double[] glmGradient = ginfoGLM._gradient; // add penalty part to this gradient manually
    double objGlm = ginfoGLM._objVal; // need to manually add gam penalty term
    int numGamCol = penalty_mat.length; // total number of gam cols to deal with   
    int nclass = fam.equals(gaussian)?1:model._output._model_beta_multinomial.length;
    int coeffNPerClass = beta.length/nclass;
    int classOffset = 0;
    double tempObj = 0.0;
    int[] gamCoeffInd = new int[penalty_mat[0].length];
    for (int classInd=0; classInd < nclass; classInd++) {
      for (int gamColInd = 0; gamColInd < numGamCol; gamColInd++) {
        System.arraycopy(gamCoeffIndices[gamColInd],0,gamCoeffInd,0,penalty_mat[0].length);
        int penaltySize = penalty_mat[gamColInd].length;
        gamCoeffInd = ArrayUtils.add(gamCoeffInd, classOffset);
        double[] sb = new double[penaltySize];
        for (int gamColi = 0; gamColi < penaltySize; gamColi++) { // index into beta
          int trueglmGradient = gamCoeffInd[gamColi];
          glmGradient[trueglmGradient] += ArrayUtils.innerProductPartial(beta, gamCoeffInd,
                  penalty_mat[gamColInd][gamColi])+penalty_mat[gamColInd][gamColi][gamColi]*beta[trueglmGradient]; // assume penalty-matrix is symmetric
          sb[gamColi] = ArrayUtils.innerProductPartial(beta, gamCoeffInd, penalty_mat[gamColInd][gamColi]);
        }
        tempObj += ArrayUtils.innerProductPartial(beta, gamCoeffInd,sb);
      }
      classOffset += coeffNPerClass;
    }
    TestUtil.checkArrays(glmGradient, gamGradient, 1e-6); // compare gradients
    assertEquals(objGam, (tempObj+objGlm), 1e-6); // compare objectives
  }
  
  public GLMModel.GLMParameters setGLMParamsFromGamParams(GAMModel.GAMParameters gamP, Frame trainFrame, 
                                                          GLMModel.GLMParameters.Family fam, 
                                                          GLMModel.GLMParameters.Link link) {
    GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
    parms._family = fam;
    parms._response_column = gamP._response_column;
    parms._train = trainFrame._key;
    parms._ignored_columns = gamP._ignored_columns;
    parms._link = link;
    return parms;
  }

  /**
   * Here, I compare the modelmetrics from glm model and the ones calculated from GAM model.
   * 
   * @param gamM
   */
  public void verifyModelMetrics(GAMModel gamM, double tot) {
    assert Math.abs(gamM._output._training_metrics.mse() - gamM._output._glm_training_metrics.mse())<tot:"MSE " +
            "comparison failed.";
    assert Math.abs(gamM._output._training_metrics.residual_degrees_of_freedom() - 
            gamM._output._glm_training_metrics.residual_degrees_of_freedom())<tot:"Residual degrees of freedom " +
            "comparison failed.";
  }
  
  @Test
  public void testEqualColNames() {
    try {
      Scope.enter();
      Frame train = Scope.track(parse_test_file("smalldata/glm_test/binomial_20_cols_10KRows.csv"));
      String[] fnames = train.names();
      String[] types = train.typesStr();
      assert !(equalColNames(types, fnames, fnames[fnames.length-1])):"Equal length but different strings!";
      assert equalColNames(fnames, fnames, fnames[fnames.length-1]); // should be the same
      String[] fnames2 = new String[fnames.length-1];
      System.arraycopy(fnames, 0, fnames2, 0, fnames2.length); // copy everything except response
      assert equalColNames(fnames, fnames2, fnames[fnames.length-1]);
      String[] fnames3 = new String[fnames2.length-1];
      System.arraycopy(fnames, 0, fnames3, 0, fnames3.length); // copy everything except response
      assert !equalColNames(fnames, fnames3, fnames[fnames.length-1]);  // one element less.  Should not be equal
      String temp = fnames2[0];
      fnames2[0] = fnames2[fnames2.length-1];
      fnames2[fnames2.length-1] = temp;
      assert equalColNames(fnames2, fnames, fnames[fnames.length-1]); // should equal, switch an element order
    } finally {
      Scope.exit();
    }
  }

  /***
   * Given the same coefficients, GAM and GLM scoring should produce the same results for Gaussian, Binomial and
   * multinomial.  This test will compare the predict results from GLM and GAM scoring.
   */
  @Test
  public void testModelScoring() {
    Scope.enter();
    try {
      // test for binomial
      String[] ignoredCols = new String[]{"C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C11", "C12", "C13", "C14",
              "C15", "C16", "C17", "C18", "C19", "C20"};
      String[] gamCols = new String[]{"C11", "C12", "C13"};
      Frame trainBinomial = Scope.track(massageFrame(parse_test_file("smalldata/glm_test/binomial_20_cols_10KRows.csv"),
              binomial));
      DKV.put(trainBinomial);
      GAMModel binomialModel = getModel(binomial,
              parse_test_file("smalldata/glm_test/binomial_20_cols_10KRows.csv"), "C21",
              gamCols, ignoredCols, new int[]{5, 5, 5}, new int[]{0, 0, 0}, false, true,
              new double[]{1, 1, 1}, new double[]{0}, new double[]{0}, true, null,
              null, false);
      Scope.track_generic(binomialModel);
      binomialModel._output._training_metrics = null; // force prediction threshold of 0.5
      Frame predictBinomial = Scope.track(binomialModel.score(trainBinomial));
      Frame predictGLMBinomial = Scope.track(parse_test_file("smalldata/gam_test/predictBinomialGAM3.csv"));
      TestUtil.assertIdenticalUpToRelTolerance(predictBinomial, predictGLMBinomial, 1e-6);
      
      // test for Gaussian
      Frame trainGaussian = Scope.track(massageFrame(parse_test_file("smalldata/glm_test/gaussian_20cols_10000Rows.csv"), gaussian));
      DKV.put(trainGaussian);
      GAMModel gaussianModel = getModel(gaussian,
              parse_test_file("smalldata/glm_test/gaussian_20cols_10000Rows.csv"), "C21",
              gamCols, ignoredCols, new int[]{5, 5, 5}, new int[]{0, 0, 0}, false, true,
              new double[]{1, 1, 1}, new double[]{0}, new double[]{0}, true, null,
              null, false);
      Scope.track_generic(gaussianModel);
      Frame predictGaussian = Scope.track(gaussianModel.score(trainGaussian));
      Frame predictGLMGaussian = Scope.track(parse_test_file("smalldata/gam_test/predictGaussianGAM3.csv"));
      TestUtil.assertIdenticalUpToRelTolerance(predictGaussian, predictGLMGaussian, 1e-6);
      
      // multinomial
      ignoredCols = new String[]{"C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"};
      gamCols = new String[]{"C6", "C7", "C8"};
      Frame trainMultinomial = Scope.track(massageFrame(parse_test_file("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv"), multinomial));
      DKV.put(trainMultinomial);
      GAMModel multinomialModel = getModel(multinomial,
              parse_test_file("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv"),
              "C11", gamCols, ignoredCols, new int[]{5, 5, 5}, new int[]{0, 0, 0}, false,
              true, new double[]{1, 1, 1}, new double[]{0}, new double[]{0},
              true, null, null, false);
      Scope.track_generic(multinomialModel);
      Frame predictMult = Scope.track(multinomialModel.score(trainMultinomial));
      Frame predictGLMMulti = Scope.track(parse_test_file("smalldata/gam_test/predictMultinomialGAM3.csv"));
      TestUtil.assertIdenticalUpToRelTolerance(predictMult, predictGLMMulti, 1e-6);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testAutoFamilyMultinomial() {
    Scope.enter();
    try {
      String[] ignoredCols = new String[]{"C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"};
      String[] gamCols = new String[]{"C6", "C7", "C8"};
      Frame trainMultinomial = Scope.track(massageFrame(parse_test_file("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv"), multinomial));
      DKV.put(trainMultinomial);
      Frame trainMultiWithC11Categorical = parse_test_file("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv");
      String target = "C11";
      int target_idx = trainMultiWithC11Categorical.find(target);
      trainMultiWithC11Categorical.replace(target_idx, trainMultinomial.vec(target_idx).toCategoricalVec()).remove();
      
      GAMModel multinomialModel = getModel(AUTO,
              trainMultiWithC11Categorical,
              "C11", gamCols, ignoredCols, new int[]{5, 5, 5}, new int[]{0, 0, 0}, false,
              true, new double[]{1, 1, 1}, new double[]{0}, new double[]{0},
              true, null,null,false, multinomial);
      Scope.track_generic(multinomialModel);
      
      Frame predictMultinomial = Scope.track(multinomialModel.score(trainMultinomial));
      Frame predictGAMMultinomial = Scope.track(parse_test_file("smalldata/gam_test/predictMultinomialGAM3.csv"));
      TestUtil.assertIdenticalUpToRelTolerance(predictMultinomial, predictGAMMultinomial, 1e-6);
    } finally {
      Scope.exit();
    }
  }


  @Test
  public void testAutoFamilyGaussian() {
    Scope.enter();
    try {
      String[] ignoredCols = new String[]{"C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C11", "C12", "C13", "C14",
              "C15", "C16", "C17", "C18", "C19", "C20"};
      String[] gamCols = new String[]{"C11", "C12", "C13"};
      Frame trainGaussian = Scope.track(massageFrame(parse_test_file("smalldata/glm_test/gaussian_20cols_10000Rows.csv"), gaussian));
      DKV.put(trainGaussian);
      
      GAMModel autoGaussianModel = getModel(AUTO,
              parse_test_file("smalldata/glm_test/gaussian_20cols_10000Rows.csv"), "C21",
              gamCols, ignoredCols, new int[]{5, 5, 5}, new int[]{0, 0, 0}, false, true,
              new double[]{1, 1, 1}, new double[]{0}, new double[]{0}, true, null,null, false, gaussian);
      Scope.track_generic(autoGaussianModel);
      
      Frame predictAutoGaussian = Scope.track(autoGaussianModel.score(trainGaussian));
      Frame predictGAMGaussian = Scope.track(parse_test_file("smalldata/gam_test/predictGaussianGAM3.csv"));
      TestUtil.assertIdenticalUpToRelTolerance(predictAutoGaussian, predictGAMGaussian, 1e-6);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testAutoFamilyBinomial() {
    Scope.enter();
    try {
      String[] ignoredCols = new String[]{"C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C11", "C12", "C13", "C14",
              "C15", "C16", "C17", "C18", "C19", "C20"};
      String[] gamCols = new String[]{"C11", "C12", "C13"};
      Frame trainBinomial = Scope.track(massageFrame(parse_test_file("smalldata/glm_test/binomial_20_cols_10KRows.csv"), binomial));
      DKV.put(trainBinomial);
      Frame trainBinomialWithC21Categorical = parse_test_file("smalldata/glm_test/binomial_20_cols_10KRows.csv");
      String target = "C21";
      int target_idx = trainBinomialWithC21Categorical.find(target);
      trainBinomialWithC21Categorical.replace(target_idx, trainBinomialWithC21Categorical.vec(target_idx).toCategoricalVec()).remove();
      DKV.put(trainBinomialWithC21Categorical);
      
      GAMModel autoGaussianModel = getModel(AUTO,
              trainBinomialWithC21Categorical, "C21",
              gamCols, ignoredCols, new int[]{5, 5, 5}, new int[]{0, 0, 0}, false, true,
              new double[]{1, 1, 1}, new double[]{0}, new double[]{0}, true, null,null, false,binomial);
      Scope.track_generic(autoGaussianModel);
      autoGaussianModel._output._training_metrics = null; // force prediction threshold of 0.5
      
      Frame predictAutoGaussian = Scope.track(autoGaussianModel.score(trainBinomial));
      Frame predictGAMBinomial = Scope.track(parse_test_file("smalldata/gam_test/predictBinomialGAM3.csv"));
      TestUtil.assertIdenticalUpToRelTolerance(predictAutoGaussian, predictGAMBinomial, 1e-6);
    } finally {
      Scope.exit();
    }
  }
  
  @Test
  public void testLocateBin() {
    double[] binLimit = new double[]{0,1,2,3,4,5,6,7,8}; // represent the bin boundaries
    int nbin = binLimit.length-1; // total number of bins present
    assert locateBin(-1, binLimit)==0;  // values lower than leftmost bin classify as in 
    assert locateBin(9, binLimit) == (nbin-1);  // value in last bin

    for (int index=0; index < nbin; index++) { // check and make sure all bins are assigned correctly
      double xval = binLimit[index]+0.5;
      assert locateBin(xval, binLimit)==index;
    }
  }
  
  // simple test to make sure there is no leaked keys.
  @Test 
  public void testValidationData() {
    Scope.enter();
    try {
      String[] ignoredCols = new String[]{"C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C11", "C12", "C13", "C14",
              "C15", "C16", "C17", "C18", "C19", "C20"};
      String[] gamCols = new String[]{"C11", "C12", "C13"};
      Frame trainBinomial = Scope.track(massageFrame(parse_test_file("smalldata/glm_test/binomial_20_cols_10KRows.csv"),
              binomial));
      DKV.put(trainBinomial);
      SplitFrame sf = new SplitFrame(trainBinomial, new double[]{0.7, 0.3}, null);
      sf.exec().get();
      Key[] splits = sf._destination_frames;
      Frame trainFrame = Scope.track((Frame) splits[0].get());
      Frame testFrame = Scope.track((Frame) splits[1].get());
      
      GAMModel binomialModel = getModel(binomial,
              parse_test_file("smalldata/glm_test/binomial_20_cols_10KRows.csv"), "C21",
              gamCols, ignoredCols, new int[]{5, 5, 5}, new int[]{0, 0, 0}, false, true,
              new double[]{1, 1, 1}, new double[]{0}, new double[]{0}, true, null, 
              testFrame, true);
      Scope.track_generic(binomialModel);
      Frame testPred = binomialModel.score(trainFrame);
      Scope.track(testPred);
      assert testPred.numRows() > testFrame.numRows();
    } finally {
      Scope.exit();
    }
  }
  
  @Test
  public void testModelMetricsGeneration() {
    Scope.enter();
    try {
      // test for gaussian
      String[] ignoredCols = new String[]{"C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C11", "C12", "C13", "C14",
              "C15", "C16", "C17", "C18", "C19", "C20"};
      String[] gamCols = new String[]{"C11", "C12", "C13"};
      GAMModel binomialModel = getModel(binomial,  Scope.track(parse_test_file("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
              , "C21", gamCols, ignoredCols, new int[]{5, 5, 5}, new int[]{0, 0, 0},
              false, true, new double[]{1, 1, 1}, new double[]{0},
              new double[]{0}, true, null,null, true);
      Scope.track_generic(binomialModel);
      verifyModelMetrics(binomialModel,1e-6);

      GAMModel gaussianModel = getModel(gaussian,
              Scope.track(parse_test_file("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
              , "C21", gamCols, ignoredCols, new int[]{5, 5, 5}, new int[]{0, 0, 0},
              false, true, new double[]{1, 1, 1}, new double[]{0},
              new double[]{0}, true, null,null, true);
      Scope.track_generic(gaussianModel);
      verifyModelMetrics(gaussianModel,1e-6);
      
      // multinomial
      ignoredCols = new String[]{"C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"};
      gamCols = new String[]{"C6", "C7", "C8"};

      GAMModel multinomialModel = getModel(multinomial,
              Scope.track(parse_test_file("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv"))
              , "C11", gamCols, ignoredCols, new int[]{5, 5, 5}, new int[]{0, 0, 0},
              false, true, new double[]{1, 1, 1}, new double[]{0},
              new double[]{0}, true, null,null, false);
      Scope.track_generic(multinomialModel);
      verifyModelMetrics(multinomialModel,1e-6);
    } finally {
      Scope.exit();
    }
  }
}
