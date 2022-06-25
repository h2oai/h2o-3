package hex.gam;

import hex.Model;
import hex.SplitFrame;
import hex.glm.GLMModel;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.ast.prims.advmath.AstKFold;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.RandomUtils;

import java.util.Random;

import static hex.gam.GAMModel.GAMParameters;
import static hex.gam.GamTestPiping.massageFrame;
import static hex.glm.GLMModel.GLMParameters.Family.gaussian;
import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class GamCVTest extends TestUtil {
  
// In this test, I will test all the following cross-validation parameters:
// 1. fold_assignment = random
// 2. keep_cross_validation_model
// 3. keep_cross_validation_predictions
// 4. keep_cross_validation_fold_assignment
//
// If we keep the cross-validation models and the fold assignment, then the prediction using the folds and
// the predictions kept from cross-validation should yield the same result!  

  /***
   * CV test for binomial family
   */
  @Test
  public void testCVBinomial() {
    try {
      Scope.enter();
      Frame train = parseTestFile("smalldata/glm_test/binomial_20_cols_10KRows.csv");
      Random rnd = RandomUtils.getRNG(train.byteSize());
      // change training data frame
      int response_index = train.numCols() - 1;
      train.replace((response_index), train.vec(response_index).toCategoricalVec()).remove();
      String[] enumCnames = new String[]{"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C21"};
      int[] eCol = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, response_index};
      int count = 0;
      for (String cname : enumCnames) {
        train.replace((eCol[count]), train.vec(cname).toCategoricalVec()).remove();
        count++;
      }
      Scope.track(train);
      DKV.put(train);
      double threshold = 0.5;
      GAMModel.GAMParameters params = new GAMModel.GAMParameters();
      params._family = GLMModel.GLMParameters.Family.binomial;
      params._response_column = "C21";
      params._max_iterations = 3;
      params._gam_columns = new String[][]{{"C11"}};
      params._train = train._key;
      params._solver = GLMModel.GLMParameters.Solver.IRLSM;
      params._fold_assignment = Model.Parameters.FoldAssignmentScheme.Random;
      params._keep_cross_validation_fold_assignment = true;
      params._keep_cross_validation_models = true;
      params._keep_cross_validation_predictions = true;
      params._nfolds = 3;
      GAMModel gam = new GAM(params).trainModel().get();
      Scope.track_generic(gam);

      assertNotNull(gam._output._cross_validation_models);
      assertNotNull(gam._output._cross_validation_metrics_summary);
      assertEquals(params._nfolds, gam._output._cross_validation_models.length);
      
      GAMModel[] cv_models = new GAMModel[params._nfolds];
      Frame[] cvModelPreds = new Frame[params._nfolds];
      Frame[] predFrames = new Frame[params._nfolds];
      Frame fold_assignment_frame = Scope.track((Frame)DKV.getGet(gam._output._cross_validation_fold_assignment_frame_id));

      // generate prediction from different cv models
      for (int foldRun = 0; foldRun < params._nfolds; foldRun++) {
        cv_models[foldRun] = DKV.getGet(gam._output._cross_validation_models[foldRun]);
        Scope.track_generic(cv_models[foldRun]);
        predFrames[foldRun] = Scope.track(cv_models[foldRun].score(train));
        cvModelPreds[foldRun] = Scope.track((Frame) DKV.getGet(gam._output._cross_validation_predictions[foldRun]));
      }
      // compare cv predictions and fresh predictions from cv models and they better be EQUAL
      for (int rind = 0; rind < train.numRows(); rind++) {
        if (rnd.nextDouble() <= threshold) {
          int foldNum = (int) fold_assignment_frame.vec(0).at(rind);
          assert Math.abs(predFrames[foldNum].vec(2).at(rind) - cvModelPreds[foldNum].vec(2).at(rind)) < 1e-6 
                  : "Frame contents differ.";
        }
      }
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testCVFoldColumnBinomial() {
    try {
      Scope.enter();
      Frame train = parseTestFile("smalldata/glm_test/binomial_20_cols_10KRows.csv");
      int nfolds = 3;
      Vec foldColumn = AstKFold.moduloKfoldColumn(train.anyVec().makeZero(), nfolds);
      DKV.put(foldColumn);
      Scope.track(foldColumn);
      train.prepend("fold", foldColumn);
      Random rnd = RandomUtils.getRNG(train.byteSize());
      // change training data frame
      int response_index = train.numCols() - 1;
      String[] enumCnames = new String[]{"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C21"};
      int[] eCol = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, response_index};
      int count = 0;
      for (String cname : enumCnames) {
        train.replace((eCol[count]), train.vec(cname).toCategoricalVec()).remove();
        count++;
      }
      Scope.track(train);
      DKV.put(train);
      double threshold = 0.5;
      GAMModel.GAMParameters params = new GAMModel.GAMParameters();
      params._family = GLMModel.GLMParameters.Family.binomial;
      params._response_column = "C21";
      params._max_iterations = 3;
      params._gam_columns = new String[][]{{"C11"}};
      params._train = train._key;
      params._solver = GLMModel.GLMParameters.Solver.IRLSM;
      params._fold_column = "fold";
      params._keep_cross_validation_fold_assignment = false;
      params._keep_cross_validation_models = true;
      params._keep_cross_validation_predictions = true;
      GAMModel gam = new GAM(params).trainModel().get();
      Scope.track_generic(gam);
      
      // make sure fold column is still accessible
      assert train.vec(0).at(0)>=0;
      assertNotNull(gam._output._cross_validation_models);
      assertNotNull(gam._output._cross_validation_metrics_summary);
      assertEquals(nfolds, gam._output._cross_validation_models.length);

      GAMModel[] cv_models = new GAMModel[nfolds];
      Frame[] cvModelPreds = new Frame[nfolds];
      Frame[] predFrames = new Frame[nfolds];

      // generate prediction from different cv models
      for (int foldRun = 0; foldRun < nfolds; foldRun++) {
        cv_models[foldRun] = DKV.getGet(gam._output._cross_validation_models[foldRun]);
        Scope.track_generic(cv_models[foldRun]);
        predFrames[foldRun] = Scope.track(cv_models[foldRun].score(train));
        cvModelPreds[foldRun] = Scope.track((Frame) DKV.getGet(gam._output._cross_validation_predictions[foldRun]));
      }
      // compare cv predictions and fresh predictions from cv models and they better be EQUAL
      for (int rind = 0; rind < train.numRows(); rind++) {
        if (rnd.nextDouble() <= threshold) {
          int foldNum = (int) train.vec("fold").at(rind);
          assert Math.abs(predFrames[foldNum].vec(2).at(rind) - cvModelPreds[foldNum].vec(2).at(rind)) < 1e-6
                  : "Frame contents differ.";
        }
      }
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testCVISCSTPBinomial() {
    try {
      Scope.enter();
      Frame train = parseTestFile("smalldata/glm_test/binomial_20_cols_10KRows.csv");
      Random rnd = RandomUtils.getRNG(train.byteSize());
      // change training data frame
      int response_index = train.numCols() - 1;
      train.replace((response_index), train.vec(response_index).toCategoricalVec()).remove();
      String[] enumCnames = new String[]{"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C21"};
      int[] eCol = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, response_index};
      int count = 0;
      for (String cname : enumCnames) {
        train.replace((eCol[count]), train.vec(cname).toCategoricalVec()).remove();
        count++;
      }
      Scope.track(train);
      DKV.put(train);
      double threshold = 0.5;
      String[][] gamColumns = new String[][]{{"C11"}, {"C11", "C12"}, {"C14"}};
      GAMModel.GAMParameters params = new GAMModel.GAMParameters();
      params._family = GLMModel.GLMParameters.Family.binomial;
      params._response_column = "C21";
      params._max_iterations = 3;
      params._gam_columns = gamColumns;
      params._bs = new int[]{2, 1, 0};
      params._spline_orders = new int[]{8, -1, -1};
      params._num_knots = new int[]{5, 12, 6};
      params._train = train._key;
      params._solver = GLMModel.GLMParameters.Solver.IRLSM;
      params._fold_assignment = Model.Parameters.FoldAssignmentScheme.Random;
      params._keep_cross_validation_fold_assignment = true;
      params._keep_cross_validation_models = true;
      params._keep_cross_validation_predictions = true;
      params._nfolds = 3;
      GAMModel gam = new GAM(params).trainModel().get();
      Scope.track_generic(gam);

      assertNotNull(gam._output._cross_validation_models);
      assertNotNull(gam._output._cross_validation_metrics_summary);
      assertEquals(params._nfolds, gam._output._cross_validation_models.length);

      GAMModel[] cv_models = new GAMModel[params._nfolds];
      Frame[] cvModelPreds = new Frame[params._nfolds];
      Frame[] predFrames = new Frame[params._nfolds];
      Frame fold_assignment_frame = Scope.track((Frame)DKV.getGet(gam._output._cross_validation_fold_assignment_frame_id));

      // generate prediction from different cv models
      for (int foldRun = 0; foldRun < params._nfolds; foldRun++) {
        cv_models[foldRun] = DKV.getGet(gam._output._cross_validation_models[foldRun]);
        Scope.track_generic(cv_models[foldRun]);
        predFrames[foldRun] = Scope.track(cv_models[foldRun].score(train));
        cvModelPreds[foldRun] = Scope.track((Frame) DKV.getGet(gam._output._cross_validation_predictions[foldRun]));
      }
      // compare cv predictions and fresh predictions from cv models and they better be EQUAL
      for (int rind = 0; rind < train.numRows(); rind++) {
        if (rnd.nextDouble() <= threshold) {
          int foldNum = (int) fold_assignment_frame.vec(0).at(rind);
          assert Math.abs(predFrames[foldNum].vec(2).at(rind) - cvModelPreds[foldNum].vec(2).at(rind)) < 1e-6
                  : "Frame contents differ.";
        }
      }
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testCVRegressionISCSTP() {
    Scope.enter();
    try {
      String[][] gamColumns = new String[][]{{"C11"}, {"C16"}, {"C17", "C18", "C19"}};
      Frame train = Scope.track(massageFrame(parseTestFile("smalldata/glm_test/gaussian_20cols_10000Rows.csv"), gaussian));
      DKV.put(train);
      GAMModel.GAMParameters params = new GAMModel.GAMParameters();
      params._family = GLMModel.GLMParameters.Family.gaussian;
      params._gam_columns = gamColumns;
      params._bs = new int[]{2, 0, 1};
      params._spline_orders = new int[]{8, -1, -1};
      params._num_knots = new int[]{5, 6, 12};
      params._ignored_columns = new String[]{"C1","C2","C3","C4","C5","C6","C7","C8","C9","C10","C11","C12","C13",
              "C14","C15","C16","C17","C18","C19","C20"};
      params._response_column = "C21";
      params._max_iterations = 3;
      params._train = train._key;
      params._fold_assignment = Model.Parameters.FoldAssignmentScheme.Random;
      params._nfolds = 3;
      params._seed = 12345;
      params._parallelize_cross_validation = false; // for easy debugging for now
      params._keep_cross_validation_fold_assignment = true;
      params._keep_cross_validation_models = true;
      params._keep_cross_validation_predictions = true;
      GAMModel gam = new GAM(params).trainModel().get();
      Scope.track_generic(gam);

      assertNotNull(gam._output._cross_validation_models);
      assertNotNull(gam._output._cross_validation_metrics_summary);
      assertEquals(params._nfolds, gam._output._cross_validation_models.length);

      GAMModel[] cvModels = new GAMModel[params._nfolds];
      Frame[] cvModelPreds = new Frame[params._nfolds];
      Frame[] predFrames = new Frame[params._nfolds];
      Frame foldAssignmentFrame = DKV.getGet(gam._output._cross_validation_fold_assignment_frame_id);
      Scope.track(foldAssignmentFrame);

      // generate prediction from different cv models
      for (int foldRun = 0; foldRun < params._nfolds; foldRun++) {
        cvModels[foldRun] = DKV.getGet(gam._output._cross_validation_models[foldRun]);
        Scope.track_generic(cvModels[foldRun]);
        predFrames[foldRun] = Scope.track(cvModels[foldRun].score(train));
        cvModelPreds[foldRun] = Scope.track((Frame) DKV.getGet(gam._output._cross_validation_predictions[foldRun]));
      }
      for (int row = 0; row < train.numRows(); row++) {
        int foldNum = (int) foldAssignmentFrame.vec(0).at(row);
        double predVal = predFrames[foldNum].vec(0).at(row);
        double cvPredVal = cvModelPreds[foldNum].vec(0).at(row);
        assert Math.abs(predVal - cvPredVal)/Math.max(Math.abs(predVal), Math.abs(cvPredVal)) < 1e-12 : 
                "Frame contents differ at row " + row + " predVal: "+predVal+" cvPredVal: "+cvPredVal;
      }
    } finally {
      Scope.exit();
    }
  }
  
  /***
   * Test cv for Multinomial family.  
   */
  @Test
  public void testCVMultinomial() {
    try {
      Scope.enter();
      Frame train = parseTestFile("smalldata/iris/iris_train.csv");
      DKV.put(train);
      Scope.track(train);

      GAMModel.GAMParameters params = new GAMModel.GAMParameters();
      params._family = GLMModel.GLMParameters.Family.multinomial;
      params._response_column = "species";
      params._max_iterations = 3;
      params._gam_columns = new String[][]{{"petal_wid"}};
      params._train = train._key;
      params._solver = GLMModel.GLMParameters.Solver.IRLSM;
      params._fold_assignment = Model.Parameters.FoldAssignmentScheme.Random;
      params._nfolds = 3;
      params._keep_cross_validation_fold_assignment = true;
      params._keep_cross_validation_models = true;
      params._keep_cross_validation_predictions = true;
      params._keep_gam_cols = true;
      GAMModel gam = new GAM(params).trainModel().get();
      Scope.track_generic(gam);

      assertNotNull(gam._output._cross_validation_models);
      assertNotNull(gam._output._cross_validation_metrics_summary);
      assertEquals(params._nfolds, gam._output._cross_validation_models.length);
      
      GAMModel[] cv_models = new GAMModel[params._nfolds];
      Frame[] cvModelPreds = new Frame[params._nfolds];
      Frame[] predFrames = new Frame[params._nfolds];
      Frame fold_assignment_frame = Scope.track((Frame) DKV.getGet(gam._output._cross_validation_fold_assignment_frame_id));


      // generate prediction from different cv models
      for (int foldRun = 0; foldRun < params._nfolds; foldRun++) {
        cv_models[foldRun] = DKV.getGet(gam._output._cross_validation_models[foldRun]);
        Scope.track_generic(cv_models[foldRun]);
        predFrames[foldRun] = Scope.track(cv_models[foldRun].score(train));
        cvModelPreds[foldRun] = Scope.track((Frame) DKV.getGet(gam._output._cross_validation_predictions[foldRun]));
      }
      
      for (int row = 0; row < train.numRows(); row++) {
        int foldNum = (int) fold_assignment_frame.vec(0).at(row);
        assert Math.abs(predFrames[foldNum].vec(2).at(row) - cvModelPreds[foldNum].vec(2).at(row)) < 1e-6 
                : "Frame contents differ.";
      }
    } finally {
      Scope.exit();
    }
  }

  /***
   * Test cv for Gaussian family.  
   */
  @Test
  public void testCVRegression() {
    try {
      Scope.enter();
      Frame train = parseTestFile("smalldata/iris/iris_train.csv");
      DKV.put(train);
      Scope.track(train);

      GAMModel.GAMParameters params = new GAMModel.GAMParameters();
      params._family = GLMModel.GLMParameters.Family.gaussian;
      params._response_column = "sepal_len";
      params._max_iterations = 3;
      params._gam_columns = new String[][]{{"petal_wid"}};
      params._train = train._key;
      params._solver = GLMModel.GLMParameters.Solver.IRLSM;
      params._fold_assignment = Model.Parameters.FoldAssignmentScheme.Random;
      params._nfolds = 3;
      params._keep_cross_validation_fold_assignment = true;
      params._keep_cross_validation_models = true;
      params._keep_cross_validation_predictions = true;
      GAMModel gam = new GAM(params).trainModel().get();
      Scope.track_generic(gam);
      
      assertNotNull(gam._output._cross_validation_models);
      assertNotNull(gam._output._cross_validation_metrics_summary);
      assertEquals(params._nfolds, gam._output._cross_validation_models.length);
      
      GAMModel[] cvModels = new GAMModel[params._nfolds];
      Frame[] cvModelPreds = new Frame[params._nfolds];
      Frame[] predFrames = new Frame[params._nfolds];
      Frame foldAssignmentFrame = DKV.getGet(gam._output._cross_validation_fold_assignment_frame_id);
     Scope.track(foldAssignmentFrame);

      // generate prediction from different cv models
      for (int foldRun = 0; foldRun < params._nfolds; foldRun++) {
        cvModels[foldRun] = DKV.getGet(gam._output._cross_validation_models[foldRun]);
        Scope.track_generic(cvModels[foldRun]);
        predFrames[foldRun] = Scope.track(cvModels[foldRun].score(train));
        cvModelPreds[foldRun] = Scope.track((Frame) DKV.getGet(gam._output._cross_validation_predictions[foldRun]));
      }
      for (int row = 0; row < train.numRows(); row++) {
        int foldNum = (int) foldAssignmentFrame.vec(0).at(row);
        assert predFrames[foldNum].vec(0).at(row) == cvModelPreds[foldNum].vec(0).at(row) 
                : "Frame contents differ.";
      }
    } finally {
      Scope.exit();
    }
  }
  


  // test CV with validation data for GAM with CS and TP, make sure validation metrics and cv metrics are not null
  @Test
  public void testCVTP() {
    Scope.enter();
    try {
      Frame data = TestUtil.parseTestFile("smalldata/glm_test/binomial_20_cols_10KRows.csv");
      data.replace((20), data.vec(20).toCategoricalVec()).remove();
      DKV.put(data);
      Scope.track(data);
      SplitFrame sf = new SplitFrame(data, new double[] {0.7, 0.3}, null);
      sf.exec().get();
      Key[] splits = sf._destination_frames;
      Frame train = Scope.track((Frame) splits[0].get());
      Frame test = Scope.track((Frame) splits[1].get());
      String[] ignoredCols = new String[]{"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"};
      String[][] gamCols = new String[][]{{"C11"},{"C12", "C13"}, {"C11"}, {"C14", "C15", "C16"}};
      GAMParameters params = new GAMParameters();
      params._bs = new int[]{1, 1, 0, 1};
      params._response_column = "C21";
      params._ignored_columns = ignoredCols;
      params._gam_columns = gamCols;
      params._train = train._key;
      params._valid = test._key;
      params._nfolds = 3;
      params._savePenaltyMat = true;
      params._standardize_tp_gam_cols = true;
      GAMModel gam = new GAM(params).trainModel().get();  // GAM model without standarization of TP gam columns
      Scope.track_generic(gam);
      // check to make sure validation metrics, cross validation metrics are not null
      assertTrue(gam._output._cross_validation_metrics != null); // check cross-validation metrics is not null
      assertTrue(gam._output._validation_metrics != null); // check validation metrics is not null
    } finally {
      Scope.exit();
    }
  }
}
