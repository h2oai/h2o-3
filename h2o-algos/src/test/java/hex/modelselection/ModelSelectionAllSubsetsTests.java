package hex.modelselection;

import hex.SplitFrame;
import hex.glm.GLMModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.IntStream;

import static hex.gam.GamTestPiping.massageFrame;
import static hex.genmodel.utils.MathUtils.combinatorial;
import static hex.glm.GLMModel.GLMParameters.Family.gaussian;
import static hex.modelselection.ModelSelectionModel.ModelSelectionParameters.Mode.allsubsets;
import static hex.modelselection.ModelSelectionUtils.updatePredIndices;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static water.TestUtil.parseTestFile;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class ModelSelectionAllSubsetsTests extends TestUtil {
    /***
     * test the combination name generation is correct for 4 predictors, 
     *  choosing 1 predictor only, 
     *           2 predictors only,
     *           3 predictors only
     *           4 predictors
     */
    @Test
    public void testColNamesCombo() {
        assertCorrectCombos(new Integer[][]{{0}, {1}, {2}, {3}}, 4, 1);
        assertCorrectCombos(new Integer[][]{{0,1}, {0,2}, {0,3}, {1,2}, {1,3}, {2,3}}, 4, 2);
        assertCorrectCombos(new Integer[][]{{0,1,2}, {0,1,3}, {0,2,3}, {1,2,3}}, 4, 3);
        assertCorrectCombos(new Integer[][]{{0,1,2,3}}, 4, 4);
    }
    
    public void assertCorrectCombos(Integer[][] answers, int maxPredNum, int predNum) {
        int[] predIndices = IntStream.range(0, predNum).toArray();
        int zeroBound = maxPredNum-predNum;
        int[] bounds = IntStream.range(zeroBound, maxPredNum).toArray();   // highest combo value
        int numModels = combinatorial(maxPredNum, predNum);
        for (int index = 0; index < numModels; index++) {    // generate one combo
            assertArrayEquals("Array must be equal.", 
                    Arrays.stream(predIndices).boxed().toArray(Integer[]::new), answers[index]);
            updatePredIndices(predIndices, bounds);
        }
    }
    
    @Test
    public void testValidationSet() {
        Scope.enter();
        try {
            Frame train = Scope.track(massageFrame(parseTestFile("smalldata/glm_test/gaussian_20cols_10000Rows.csv"),
                    gaussian));
            DKV.put(train);
            SplitFrame sf = new SplitFrame(train, new double[]{0.7, 0.3}, null);
            sf.exec().get();
            Key[] splits = sf._destination_frames;
            Frame trainFrame = Scope.track((Frame) splits[0].get());
            Frame testFrame = Scope.track((Frame) splits[1].get());

            ModelSelectionModel.ModelSelectionParameters parms = new ModelSelectionModel.ModelSelectionParameters();
            parms._response_column = "C21";
            parms._mode = allsubsets;
            parms._family = gaussian;
            parms._max_predictor_number = 3;
            parms._train = trainFrame._key;
            ModelSelectionModel model = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Scope.track_generic(model); // model without validation dataset
            parms._valid = testFrame._key;
            ModelSelectionModel modelV = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Scope.track_generic(modelV); //  model with validation dataset
            double[] modelR2 = model._output._best_r2_values;
            double[] modelvR2 = modelV._output._best_r2_values;
            int r2Len = modelR2.length;
            for (int index=0; index < r2Len; index++)
                assertTrue(Math.abs(modelR2[index]-modelvR2[index]) > 1e-6);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testCrossValidation() {
        Scope.enter();
        try {
            Frame train = Scope.track(massageFrame(parseTestFile("smalldata/glm_test/gaussian_20cols_10000Rows.csv"),
                    gaussian));
            DKV.put(train);
            ModelSelectionModel.ModelSelectionParameters parms = new ModelSelectionModel.ModelSelectionParameters();
            parms._response_column = "C21";
            parms._family = gaussian;
            parms._max_predictor_number = 3;
            parms._mode = allsubsets;
            parms._train = train._key;
            ModelSelectionModel model = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Scope.track_generic(model); // model without validation dataset
            parms._nfolds = 3;
            ModelSelectionModel modelCV = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Scope.track_generic(modelCV); //  model with validation dataset
            double[] modelR2 = model._output._best_r2_values;
            double[] modelCVR2 = modelCV._output._best_r2_values;
            int r2Len = modelR2.length;
            for (int index=0; index < r2Len; index++)
                assertTrue(Math.abs(modelR2[index]-modelCVR2[index]) > 1e-6);
        } finally {
            Scope.exit();
        }
    }
    
    // test the returned r2 are from the best predictors
    @Test
    public void testBestR2Prostate() {
        Scope.enter();
        try {
            double tol = 1e-6;
            Frame trainF = parseTestFile("smalldata/logreg/prostate.csv");
            Scope.track(trainF);
            ModelSelectionModel.ModelSelectionParameters parms = new ModelSelectionModel.ModelSelectionParameters();
            parms._response_column = "AGE";
            parms._mode = allsubsets;
            parms._family = gaussian;
            parms._ignored_columns = new String[]{"ID"};
            parms._max_predictor_number=1;
            parms._train = trainF._key;
            ModelSelectionModel model1 = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Scope.track_generic(model1); // best one predictor model
            
            parms._max_predictor_number=2;
            ModelSelectionModel model2 = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Scope.track_generic(model2); // best one and two predictors models
            assertTrue(Math.abs(model1._output._best_r2_values[0]-model2._output._best_r2_values[0]) < tol);
            
            parms._max_predictor_number=3;
            ModelSelectionModel model3 = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Scope.track_generic(model3); // best one, two and three predictors models
            assertTrue(Math.abs(model2._output._best_r2_values[1]-model3._output._best_r2_values[1]) < tol);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testProstateResultFrame() {
        Scope.enter();
        try {
            double tol = 1e-6;
            Frame trainF = parseTestFile("smalldata/logreg/prostate.csv");
            Scope.track(trainF);
            ModelSelectionModel.ModelSelectionParameters parms = new ModelSelectionModel.ModelSelectionParameters();
            parms._response_column = "AGE";
            parms._family = gaussian;
            parms._mode = allsubsets;
            parms._ignored_columns = new String[]{"ID"};
            parms._max_predictor_number=trainF.numCols()-3;
            parms._train = trainF._key;
            ModelSelectionModel model = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Scope.track_generic(model); // best one predictor model
            Frame resultFrame = model.result();
            Scope.track(resultFrame);

            double[] bestR2 = model._output._best_r2_values;
            String[][] bestPredictorSubsets = model._output._best_model_predictors;
            int numModels = bestR2.length;
            for (int index = 0; index < numModels; index++) {
                // check with model summary r2 values
                assertTrue(Math.abs(bestR2[index] - resultFrame.vec(2).at(index)) < tol);
                // grab the best model, check model can score and model coefficients agree with what is in the result frame
                GLMModel oneModel = DKV.getGet(resultFrame.vec(1).stringAt(index));
                Scope.track_generic(oneModel);
                Frame scoreFrame = oneModel.score(trainF);  // check it can score
                assertTrue(scoreFrame.numRows() == trainF.numRows());
                Scope.track(scoreFrame);
                String[] coeff = oneModel._output._coefficient_names;   // contains the name intercept as well
                String[] coeffWOIntercept = new String[coeff.length - 1];
                System.arraycopy(coeff, 0, coeffWOIntercept, 0, coeffWOIntercept.length);
                assertArrayEquals("best predictor subset containing different predictors", coeffWOIntercept,
                        bestPredictorSubsets[index]);
            }
        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void testCoeffs() {
        Scope.enter();
        try {
            double tol = 1e-6;
            Frame trainF = parseTestFile("smalldata/logreg/prostate.csv");
            Scope.track(trainF);
            ModelSelectionModel.ModelSelectionParameters parms = new ModelSelectionModel.ModelSelectionParameters();
            parms._response_column = "AGE";
            parms._family = gaussian;
            parms._mode = allsubsets;
            parms._ignored_columns = new String[]{"ID"};
            parms._max_predictor_number=trainF.numCols()-3;
            parms._train = trainF._key;
            ModelSelectionModel model = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Scope.track_generic(model); // best one predictor model
            String[][] coeffNames = model._output.coefficientNames();
            double[][] beta = model._output.beta();
            double[][] betaNorm = model._output.getNormBeta();
            HashMap<String, Double>[] coeffs = model.coefficients();
            HashMap<String, Double>[] coeffsNorm = model.coefficients(true);
            // coefficients obtained from both ways should be equal
            int numModel = beta.length;
            for (int index=0; index < numModel; index++) {
                HashMap<String, Double> coefOneModel = model.coefficients(index+1);
                HashMap<String, Double> coefOneModelNorm = model.coefficients(index+1, true);
                int coefLen = beta[index].length;
                for (int index2=0; index2 < coefLen; index2++) {
                    assertTrue(Math.abs(beta[index][index2]-coeffs[index].get(coeffNames[index][index2])) < tol);
                    assertTrue(Math.abs(betaNorm[index][index2]-coeffsNorm[index].get(coeffNames[index][index2])) < tol);
                    assertTrue(Math.abs(beta[index][index2]-coefOneModel.get(coeffNames[index][index2])) < tol);
                    assertTrue(Math.abs(betaNorm[index][index2]-coefOneModelNorm.get(coeffNames[index][index2])) < tol);
                }
            }
        } finally {
            Scope.exit();
        }
    }
}
