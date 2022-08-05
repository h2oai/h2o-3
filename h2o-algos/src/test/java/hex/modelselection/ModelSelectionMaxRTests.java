package hex.modelselection;

import hex.SplitFrame;
import hex.glm.GLMModel;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static hex.gam.GamTestPiping.massageFrame;
import static hex.glm.GLMModel.GLMParameters.Family.gaussian;
import static hex.modelselection.ModelSelection.forwardStep;
import static hex.modelselection.ModelSelection.replacement;
import static hex.modelselection.ModelSelectionModel.ModelSelectionParameters.Mode.*;
import static hex.modelselection.ModelSelectionUtils.generateMaxRTrainingFrames;
import static hex.modelselection.ModelSelectionUtils.removeTrainingFrames;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class ModelSelectionMaxRTests extends TestUtil {
    
    @Test
    public void testReplacement() {
        Scope.enter();
        try {
            Frame origF = Scope.track(parseTestFile("smalldata/glm_test/gaussian_20cols_10000Rows.csv"));
            Frame trainF = new Frame(Key.make());
            String[] predNames = origF.names();
            int[] coefInd = new int[]{0,2,4,6,8,10,12,14,16,18};
            for (int index : coefInd) 
                trainF.add(predNames[index], origF.vec(index));
            trainF.add("C21", origF.vec("C21"));
            DKV.put(trainF);
            Scope.track(trainF);
            ModelSelectionModel.ModelSelectionParameters parms = new ModelSelectionModel.ModelSelectionParameters();
            parms._response_column = "C21";
            parms._family = gaussian;
            parms._max_predictor_number = 5;
            parms._train = trainF._key;
            parms._mode = allsubsets;
            ModelSelectionModel modelAllSubsets = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Scope.track_generic(modelAllSubsets);
            String[][] bestR2Coeffs = modelAllSubsets._output.coefficientNames();
            List<String> coefNames = new ArrayList<>(Arrays.asList(trainF.names()));
            coefNames.remove(parms._response_column);
            // test for subset size 2
            String[] expectedCoeff = sortStringArray(bestR2Coeffs[1]);
            assertCorrectReplacement(new ArrayList<>(Arrays.asList(7, 9)), coefNames, 
                    modelAllSubsets._output._best_r2_values[1], expectedCoeff, true, parms);
            assertCorrectReplacement(new ArrayList<>(Arrays.asList(9, 7)), coefNames,
                    modelAllSubsets._output._best_r2_values[1], expectedCoeff, true, parms);
            assertCorrectReplacement(new ArrayList<>(Arrays.asList(7, 0)), coefNames, 0,
                    expectedCoeff, false, parms);
            assertCorrectReplacement(new ArrayList<>(Arrays.asList(1, 9)), coefNames, 0,
                    expectedCoeff, false, parms);
            assertCorrectReplacement(new ArrayList<>(Arrays.asList(8, 2)), coefNames, 0,
                    expectedCoeff, false, parms);
            // test for subset size 5
            expectedCoeff = sortStringArray(bestR2Coeffs[4]);
            assertCorrectReplacement(new ArrayList<>(Arrays.asList(2, 5, 7, 8, 9)), coefNames, 
                    modelAllSubsets._output._best_r2_values[4], expectedCoeff, true, parms);
            assertCorrectReplacement(new ArrayList<>(Arrays.asList(9, 5, 7, 8, 2)), coefNames,
                    modelAllSubsets._output._best_r2_values[4], expectedCoeff, true, parms);
            assertCorrectReplacement(new ArrayList<>(Arrays.asList(2, 5, 7, 6, 9)), coefNames, 0,
                    expectedCoeff, false, parms);
            assertCorrectReplacement(new ArrayList<>(Arrays.asList(2, 0, 7, 8, 1)), coefNames, 0,
                    expectedCoeff, false, parms);
            assertCorrectReplacement(new ArrayList<>(Arrays.asList(1, 0, 3, 4, 9)), coefNames, 0,
                    expectedCoeff, false, parms);
            assertCorrectReplacement(new ArrayList<>(Arrays.asList(6, 4, 3, 1, 0)), coefNames, 0,
                    expectedCoeff, false, parms);
        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void testForwardStep() {
        Scope.enter();
        try {
            Frame origF = Scope.track(parseTestFile("smalldata/glm_test/gaussian_20cols_10000Rows.csv"));
            Frame trainF = new Frame(Key.make());
            String[] predNames = origF.names();
            int[] coefInd = new int[]{0,2,4,6,8,10,12,14,16,18};
            for (int index : coefInd)
                trainF.add(predNames[index], origF.vec(index));
            trainF.add("C21", origF.vec("C21"));
            DKV.put(trainF);
            Scope.track(trainF);
            ModelSelectionModel.ModelSelectionParameters parms = new ModelSelectionModel.ModelSelectionParameters();
            parms._response_column = "C21";
            parms._family = gaussian;
            parms._max_predictor_number = 5;
            parms._train = trainF._key;
            parms._mode = allsubsets;
            ModelSelectionModel modelAllSubsets = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Scope.track_generic(modelAllSubsets);
            String[][] bestR2Coeffs = modelAllSubsets._output.coefficientNames();
            List<String> coefNames = new ArrayList<>(Arrays.asList(trainF.names()));
            coefNames.remove(parms._response_column);
            List<ArrayList<Integer>> bestR2CoeffsInd = bestR2CoeffsInd(modelAllSubsets, coefNames);
            // test for best forward model for predictor size of 2, changing out 0, 1st predictor indices
            assertCorrectForwardStep(bestR2Coeffs, bestR2CoeffsInd.get(1), 0, coefNames, parms);
            assertCorrectForwardStep( bestR2Coeffs, bestR2CoeffsInd.get(1), 1, coefNames, parms);
            // test for best forward model for predictor of size 5, changing out 0, 1, 2, 3 and 4th predictor indices
            assertCorrectForwardStep(bestR2Coeffs, bestR2CoeffsInd.get(4),0, coefNames, parms);
            assertCorrectForwardStep(bestR2Coeffs, bestR2CoeffsInd.get(4), 1, coefNames, parms);
            assertCorrectForwardStep(bestR2Coeffs, bestR2CoeffsInd.get(4),2, coefNames, parms);
            assertCorrectForwardStep(bestR2Coeffs, bestR2CoeffsInd.get(4), 3, coefNames, parms);
            assertCorrectForwardStep(bestR2Coeffs, bestR2CoeffsInd.get(4), 4, coefNames, parms);
        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void testGenerateMaxRTrainingFrames() {
        Scope.enter();
        try {
            Frame trainF = parseTestFile("smalldata/logreg/prostate.csv");
            Scope.track(trainF);
            ModelSelectionModel.ModelSelectionParameters parms = new ModelSelectionModel.ModelSelectionParameters();
            parms._ignored_columns = new String[]{"ID"};
            parms._response_column = "AGE";
            parms._train = trainF._key;
            List<Integer> currSubsetIndices = new ArrayList<>();
            List<Integer> validSubset = IntStream.rangeClosed(0, 6).boxed().collect(Collectors.toList());
            String[] predictorNames = new String[]{"CAPSULE","RACE","DPROS","DCAPS","PSA","VOL","GLEASON"};
            Frame[] trainingFrames1 = generateMaxRTrainingFrames(parms, predictorNames, null,
                    currSubsetIndices, 0,validSubset, new HashSet<BitSet>()); // trainingFrames with 1 predictors only
            removeTrainingFrames(trainingFrames1);
            String[][] correctTrainCols1 = new String[][]{{"CAPSULE", "AGE"}, {"RACE", "AGE"},{"DPROS", "AGE"},
                    {"DCAPS", "AGE"},{"PSA", "AGE"},{"VOL", "AGE"},{"GLEASON", "AGE"}};
            assertCorrectTrainingFrames(trainingFrames1, correctTrainCols1);
            currSubsetIndices.add(0);
            validSubset.removeAll(currSubsetIndices);
            Frame[] trainingFrames2 = generateMaxRTrainingFrames(parms, predictorNames, null,
                    currSubsetIndices, 1,validSubset, new HashSet<BitSet>()); // trainingFrames with 2 predictors only
            removeTrainingFrames(trainingFrames2);
            String[][] correctTrainCols2 = new String[][]{{"CAPSULE", "RACE", "AGE"}, {"CAPSULE", "DPROS", "AGE"}, 
                    {"CAPSULE", "DCAPS", "AGE"}, {"CAPSULE", "PSA", "AGE"}, {"CAPSULE", "VOL", "AGE"}, 
                    {"CAPSULE", "GLEASON", "AGE"}};
            assertCorrectTrainingFrames(trainingFrames2, correctTrainCols2);
            currSubsetIndices.add(4);
            validSubset.removeAll(currSubsetIndices);
            String[][] correctTrainCols3 = new String[][]{{"CAPSULE", "PSA", "RACE", "AGE"}, 
                    {"CAPSULE", "PSA", "DPROS", "AGE"}, {"CAPSULE", "PSA", "DCAPS", "AGE"}, 
                    {"CAPSULE", "PSA", "VOL", "AGE"}, {"CAPSULE", "PSA", "GLEASON", "AGE"}};
            Frame[] trainingFrames3 = generateMaxRTrainingFrames(parms, predictorNames, null,
                    currSubsetIndices, 2,validSubset, new HashSet<BitSet>()); // trainingFrames with 3 predictors only
            removeTrainingFrames(trainingFrames3);
            assertCorrectTrainingFrames(trainingFrames3, correctTrainCols3);
            currSubsetIndices.add(6);
            validSubset.removeAll(currSubsetIndices);
            Frame[] trainingFrames4 = generateMaxRTrainingFrames(parms, predictorNames, null,
                    currSubsetIndices, 2, validSubset, new HashSet<BitSet>()); // trainingFrames with 3 predictors only
            removeTrainingFrames(trainingFrames4);
            String[][] correctTrainCols4 = new String[][]{{"CAPSULE", "PSA", "GLEASON", "RACE", "AGE"}, 
                    {"CAPSULE", "PSA", "GLEASON", "DPROS", "AGE"}, {"CAPSULE", "PSA", "GLEASON", "DCAPS", "AGE"}, 
                    {"CAPSULE", "PSA", "GLEASON", "VOL", "AGE"}};
            assertCorrectTrainingFrames(trainingFrames4, correctTrainCols4);
        } finally {
            Scope.exit();
        }
    }

    /**
     * test to make sure maxR generate the correct result Frame and coefficients by comparing them to the ones from
     * mode=allsubsets
     */
    @Test
    public void testProstateResultFrameCoeff() {
        Scope.enter();
        try {
            double tol = 1e-6;
            Frame trainF = parseTestFile("smalldata/logreg/prostate.csv");
            Scope.track(trainF);
            ModelSelectionModel.ModelSelectionParameters parms = new ModelSelectionModel.ModelSelectionParameters();
            parms._response_column = "AGE";
            parms._mode = maxr;
            parms._family = gaussian;
            parms._ignored_columns = new String[]{"ID"};
            parms._train = trainF._key;
            parms._max_predictor_number=3;
            ModelSelectionModel modelMaxR = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Scope.track_generic(modelMaxR); // best one, two and three predictors models
            parms._mode = allsubsets;
            ModelSelectionModel modelAllsubsets = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Scope.track_generic(modelAllsubsets); // best one, two and three predictors models
            // compare result frame
            Frame resultFrameMaxR = modelMaxR.result();
            Scope.track(resultFrameMaxR);
            Frame resultFrameAllSubset = modelAllsubsets.result();
            Scope.track(resultFrameAllSubset);
            TestUtil.assertStringVecEquals(resultFrameMaxR.vec(0), resultFrameAllSubset.vec(0));
            TestUtil.assertVecEquals("", resultFrameMaxR.vec(2), resultFrameAllSubset.vec(2), 1e-6);
            // compare coefficients
            HashMap<String, Double>[] coeffsMaxR = modelMaxR.coefficients();
            HashMap<String, Double>[] coeffsNormMaxR = modelMaxR.coefficients(true);
            HashMap<String, Double>[] coeffsAllsubsets = modelAllsubsets.coefficients();
            HashMap<String, Double>[] coeffsNormAllsubsets = modelAllsubsets.coefficients(true);
            // coefficients obtained from both ways should be equal
            int numModel = coeffsMaxR.length;
            for (int index=0; index < numModel; index++) {
                HashMap<String, Double> coefOneModelMaxR = modelMaxR.coefficients(index+1);
                HashMap<String, Double> coefOneModelNormMaxR = modelMaxR.coefficients(index+1, true);
                HashMap<String, Double> coefOneModelAllsubsets = modelAllsubsets.coefficients(index+1);
                HashMap<String, Double> coefOneModelNormAllsubsets = modelAllsubsets.coefficients(index+1,
                        true);
                
                for (String coefKey : coefOneModelMaxR.keySet()) {
                    assertTrue(Math.abs(coefOneModelMaxR.get(coefKey)-coefOneModelAllsubsets.get(coefKey)) < tol);
                    assertTrue(Math.abs(coefOneModelNormMaxR.get(coefKey)-coefOneModelNormAllsubsets.get(coefKey)) < tol);
                    assertTrue(Math.abs(coeffsMaxR[index].get(coefKey)-coeffsAllsubsets[index].get(coefKey)) < tol);
                    assertTrue(Math.abs(coeffsNormMaxR[index].get(coefKey)-coeffsNormAllsubsets[index].get(coefKey)) < tol);
                }
            }
        } finally {
            Scope.exit();
        }
    }

    /**
     * check validation runs correctly with maxr by comparing R2 with those from allsubsets
     */
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
            parms._valid = testFrame._key;
            ModelSelectionModel modelVAllsubsets = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Scope.track_generic(modelVAllsubsets); //  model with validation dataset
            parms._mode = maxr;
            ModelSelectionModel modelVMaxr = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Scope.track_generic(modelVMaxr); //  model with validation dataset
            
            double[] modelvR2Allsubsets = modelVAllsubsets._output._best_r2_values;
            int r2Len = modelvR2Allsubsets.length;
            double[] modelvR2Maxr = modelVMaxr._output._best_r2_values;
            for (int index=0; index < r2Len; index++)
                assertTrue(Math.abs(modelvR2Allsubsets[index]-modelvR2Maxr[index]) < 1e-6);
        } finally {
            Scope.exit();
        }
    }

    /**
     * Test and make sure the added and removed predictors are captured in both the result frame and the model summary.
     * In particular, I want to make sure that they agree.  The correctness of the added/removed predictors are tested
     * in Python unit test and won't be repeated here.
     */
    @Test
    public void testAddedRemovedCols() {
        Scope.enter();
        try {
            Frame train = Scope.track(massageFrame(parseTestFile("smalldata/glm_test/gaussian_20cols_10000Rows.csv"),
                    gaussian));
            DKV.put(train);
            ModelSelectionModel.ModelSelectionParameters parms = new ModelSelectionModel.ModelSelectionParameters();
            parms._response_column = "C21";
            parms._family = gaussian;
            parms._max_predictor_number = 3;
            parms._seed=12345;
            parms._train = train._key;
            parms._mode = maxr;
            ModelSelectionModel modelMaxr = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Scope.track_generic(modelMaxr); //  model with validation dataset
            compareResultFModelSummary(modelMaxr);
        } finally {
            Scope.exit();
        }
    }

    /***
     * make sure model summary and result frame contains the same contents for removed or added predictors
     * @param model
     */
    public static void compareResultFModelSummary(ModelSelectionModel model) {
        Frame resultF = model.result();
        Scope.track(resultF);
        int numRows = (int) resultF.numRows();
        String[] frameNames = new String[]{"predictor(s)_removed", "predictor(s)_added"};
        IcedWrapper[][] cellValues = model._output._model_summary.getCellValues();
        List<String> colHeaders = Stream.of(model._output._model_summary.getColHeaders()).collect(Collectors.toList());
        int removedInd = colHeaders.indexOf("predictor(s)_removed");
        int addedInd = !backward.equals(model._parms._mode) ? colHeaders.indexOf("predictor(s)_added") : 0;
        for (int rInd = 0; rInd < numRows; rInd++) {
            // removed predictor
            Assert.assertTrue(cellValues[rInd][removedInd].toString().equals(resultF.vec(frameNames[0]).stringAt(rInd)));
            // added predictor
            if (!backward.equals(model._parms._mode))
                Assert.assertTrue(cellValues[rInd][addedInd].toString().equals(resultF.vec(frameNames[1]).stringAt(rInd)));
        }
    }
    

    /**
     * check cv runs correctly with maxr by comparing R2 with those from allsubsets
     */
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
            parms._seed=12345;
            parms._train = train._key;
            parms._mode = maxr;
            ModelSelectionModel modelMaxr = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Scope.track_generic(modelMaxr); //  model with validation dataset
            double[] modelMaxrR2 = modelMaxr._output._best_r2_values;
            parms._nfolds = 3;
            ModelSelectionModel modelCVMaxr = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Scope.track_generic(modelCVMaxr); //  model with validation dataset
            double[] modelCVMaxrR2 = modelCVMaxr._output._best_r2_values;
            int r2Len = modelCVMaxrR2.length;
            for (int index=0; index < r2Len; index++)
                assertTrue(Math.abs(modelMaxrR2[index]-modelCVMaxrR2[index]) > 1e-6);
        } finally {
            Scope.exit();
        }
    }
    
    public void assertCorrectReplacement(List<Integer> currSubset, List<String> coefNames, double bestR2,
                                         String[] bestR2Subset, boolean okToBeNull,
                                         ModelSelectionModel.ModelSelectionParameters parms) {
        List<Integer> validSubset = IntStream.rangeClosed(0, coefNames.size()-1).boxed().collect(Collectors.toList()); 
        validSubset.removeAll(currSubset);
        GLMModel bestR2Model = replacement(currSubset, coefNames, bestR2, parms, 0,
                null, validSubset,null, new HashSet<BitSet>());
        if (bestR2Model == null && okToBeNull) {
            return;
        }
        String[] modelCoeff = sortStringArray(bestR2Model._output._coefficient_names);
        assertArrayEquals(bestR2Subset, modelCoeff);
    }

    public String[] sortStringArray(String[] arr) {
        List<String> coeffList = Arrays.stream(arr).collect(Collectors.toList());
        coeffList = coeffList.stream().sorted().collect(Collectors.toList());
        return coeffList.stream().toArray(String[]::new);
    }

    List<ArrayList<Integer>> bestR2CoeffsInd(ModelSelectionModel model, List<String> coefNames) {
        List<ArrayList<Integer>> bestCoefList = new ArrayList<>();
        Key[] modelIds = model._output._best_model_ids;
        int numModels = modelIds.length;
        for (int modelInd = 0; modelInd < numModels; modelInd++) {
            ArrayList<Integer> oneModelList = new ArrayList<>();
            GLMModel oneModel = DKV.getGet(modelIds[modelInd]);
            Scope.track_generic(oneModel);
            String[] oneModelCoef = oneModel._output._names;
            int coefSize = oneModelCoef.length-1;   // exclude response column
            for (int predInd = 0; predInd < coefSize; predInd++) {
                int coefIndex = coefNames.indexOf(oneModelCoef[predInd]);
                if (coefIndex >= 0)
                    oneModelList.add(coefIndex);
            }
            bestCoefList.add(oneModelList);
        }
        return bestCoefList;
    }

    public void assertCorrectForwardStep(String[][] bestR2Coeffs, List<Integer> currSubsetIndices, int newPredInd,
                                         List<String> coefNames, ModelSelectionModel.ModelSelectionParameters parms) {
        String[] bestR2Coeff = bestR2Coeffs[currSubsetIndices.size()-1];
        List<Integer> changedSubset = new ArrayList<>(currSubsetIndices);
        changedSubset.remove(newPredInd);
        List<Integer> validSubset = IntStream.rangeClosed(0, coefNames.size()-1).boxed().collect(Collectors.toList());
        validSubset.removeAll(changedSubset);
        GLMModel bestR2Model = forwardStep(changedSubset, coefNames, newPredInd, validSubset, parms,
                null, 0, null);
        Scope.track_generic(bestR2Model);
        String[] modelCoefNames = bestR2Model._output.coefficientNames();
        assertArrayEquals(bestR2Coeff, modelCoefNames);
    }
    
    public void assertCorrectTrainingFrames(Frame[] trainingFrames, String[][] correctTrainCols) {
        int numFrame = trainingFrames.length;
        for (int frameInd = 0; frameInd < numFrame; frameInd++) {
            String[] coefNames = sortStringArray(trainingFrames[frameInd].names());
            String[] expectedNames = sortStringArray(correctTrainCols[frameInd]);
            assertArrayEquals(expectedNames, coefNames);
        }
    }
}
