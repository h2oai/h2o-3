package hex.gam;

import hex.Model;
import hex.glm.GLMModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.RandomUtils;

import java.util.Random;

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
  @Test
  public void testCVBinomial() {
    try {
      Scope.enter();
      Frame train = parse_test_file("smalldata/glm_test/binomial_20_cols_10KRows.csv");
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
      double threshold = 0.9;
      GAMModel.GAMParameters params = new GAMModel.GAMParameters();
      params._family = GLMModel.GLMParameters.Family.binomial;
      params._response_column = "C21";
      params._max_iterations = 3;
      params._gam_columns = new String[]{"C11"};
      params._train = train._key;
      params._solver = GLMModel.GLMParameters.Solver.IRLSM;
      params._fold_assignment = Model.Parameters.FoldAssignmentScheme.Random;
      params._keep_cross_validation_fold_assignment = true;
      params._keep_cross_validation_models = true;
      params._keep_cross_validation_predictions = true;
      params._nfolds = 3;
      GAMModel gam = new GAM(params).trainModel().get();
      Scope.track_generic(gam);
      GAMModel[] cv_models = new GAMModel[params._nfolds];
      Frame[] cvModelPreds = new Frame[params._nfolds];
      Frame[] predFrames = new Frame[params._nfolds];
      Frame fold_assignment_frame = Scope.track((Frame)DKV.getGet(gam._output._cross_validation_fold_assignment_frame_id));

      // generate prediction from different cv models
      for (int foldRun = 0; foldRun < params._nfolds; foldRun++) {
        cv_models[foldRun] = DKV.getGet(gam._output._cross_validation_models[foldRun]);
        Scope.track_generic(cv_models[foldRun]);
        predFrames[foldRun] = Scope.track(cv_models[foldRun].score(train));
        cvModelPreds[foldRun] = Scope.track((Frame)DKV.getGet(gam._output._cross_validation_predictions[foldRun]));
      }
      // compare cv predictions and fresh predictions from cv models and they better be EQUAL
      for (int rind = 0; rind < train.numRows(); rind++) {
        if (rnd.nextDouble() > threshold) {
          int foldNum = (int) fold_assignment_frame.vec(0).at(rind);
          assert predFrames[foldNum].vec(2).at(rind) == cvModelPreds[foldNum].vec(2).at(rind):"Frame contents differ.";
        }
      }
    } finally {
      Scope.exit();
    }
  }
}
