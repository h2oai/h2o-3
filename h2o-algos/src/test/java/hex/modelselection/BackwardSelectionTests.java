package hex.modelselection;

import hex.glm.GLM;
import hex.glm.GLMModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.*;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.TwoDimTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import static hex.glm.GLMModel.GLMParameters.Family.*;
import static hex.modelselection.ModelSelectionModel.ModelSelectionParameters.Mode.backward;
import static org.junit.Assert.assertTrue;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class BackwardSelectionTests extends TestUtil {
    public static final double TOLERANCE = 1e-6;
    /**
     * test backward selection for poisson family.  In particular, want to compare the model summary results and
     * result frame containing the same results
     */
    @Test
    public void testPoisson() {
        Scope.enter();
        try {
            final Frame trainF = parseTestFile("smalldata/prostate/prostate_complete.csv.zip");
            Scope.track(trainF);
            final ModelSelectionModel model = runModel(trainF, "GLEASON", poisson, 1,
                    null);
            Scope.track_generic(model);
            // compare model summary and result frame
            final Frame resultF = model.result();
            Scope.track(resultF);
            final TwoDimTable summary = model._output._model_summary;
            final IcedWrapper[][] modelSummary = summary.getCellValues();
            final int numRow = modelSummary.length;
            final int numCol= modelSummary[0].length;
            final String[] frameNames = new String[]{"coefficient_names", "z_values", "p_values"};
            for (int rInd = 0; rInd < numRow; rInd++)
                for (int cInd = 0; cInd < numCol; cInd++) {
                    String cellValue = String.valueOf(modelSummary[rInd][cInd]);
                    String frameValue = resultF.vec(frameNames[cInd]).stringAt(rInd);
                    assertTrue(cellValue.equals(frameValue));
                }
        } finally {
            Scope.exit();
        }
    }
    
    /** 
      * test with using p-values to stop model building
      * compare with manually built frames, check coefficients, z-values, p-values
      */
    @Test
    public void testTweediePValue() {
        Scope.enter();
        try {
            final Frame trainF = parseTestFile("smalldata/glm_test/auto.csv");
            Scope.track(trainF);
            ModelSelectionModel.ModelSelectionParameters parms = new ModelSelectionModel.ModelSelectionParameters();
            parms._response_column = "y";
            parms._family = tweedie;
            parms._train = trainF._key;
            parms._mode = backward;
            parms._seed = 12345;
            parms._tweedie_power = 1.5;
            parms._tweedie_link_power = 0.5;
            parms._tweedie_variance_power = 0.5;
            final ModelSelectionModel fullModel = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Scope.track_generic(fullModel);
            double[] pValueThresholds = new double[]{0.85, 0.5, 0.1, 0.001};
            Frame resultF = fullModel.result();
            Scope.track(resultF);
            for (double pThreshold : pValueThresholds) {// backward selection model built stopped by p-value threshold
                parms._p_values_threshold = pThreshold;
                ModelSelectionModel oneModel = new hex.modelselection.ModelSelection(parms).trainModel().get();
                Scope.track_generic(oneModel);
                Frame oneModelResult = oneModel.result();
                Scope.track(oneModelResult);
                // check models are stopped correctly using p-value thresholds. Check p-values, z-values match between
                // fullmodel and p-value stopped models
                assertCorrectStopP(resultF, oneModelResult, pThreshold);
            }
        } finally {
            Scope.exit();
        }
    }
    
    public void assertCorrectStopP(Frame resultF, Frame resultFP, double pvalueThreshold) {
        int modelBuilt = (int) resultFP.numRows()-1;
        int offset = (int) resultF.numRows()-(int) resultFP.numRows();
        for (int modelIndex=modelBuilt; modelIndex >= 1; modelIndex--) {
            boolean lowerP = allLowerP(resultFP, pvalueThreshold, modelIndex);
            assertTrue("not all p-values should be lower than or equal to "+pvalueThreshold, !lowerP);
            assertValsMatch(resultFP.vec("p_values").stringAt(modelIndex), 
                    resultF.vec("p_values").stringAt(modelIndex+offset)); // full model, p-value stopped model pvalue match
            assertValsMatch(resultFP.vec("z_values").stringAt(modelIndex),
                    resultF.vec("z_values").stringAt(modelIndex+offset)); // full model, z-value stopped model pvalue match
        }
        // the last one should have p-value all lower than pvalueThreshold
        assertTrue("last model p-values should be lower than "+pvalueThreshold, 
                allLowerP(resultFP, pvalueThreshold, 0));
    }
    
    public boolean allLowerP(Frame result, double pvalueThreshold, int index) {
        boolean lowerP = true;
        String[] modelP = result.vec("p_values").stringAt(index).split(",");
        int numEle = modelP.length-1;
        for (int eleInd=0; eleInd<numEle; eleInd++) {
            lowerP = lowerP && Double.valueOf(modelP[eleInd]) <= pvalueThreshold;
            if (!lowerP)
                break;
        }
        return lowerP;
    }

    /**
     * Test backward selection with gamma family.  In particular, we want to check and make sure that the model 
     * building process is correct by comparing the coefficients built at each step (manually).  Backward selection
     * algorithm will throw away the predictor with the minimum absolute value of the z-score.  In addition, I want
     * to check that the z-values, p-values collected at each step are correct when compared to the manually built 
     * models as well.
     */
    @Test
    public void testGamma() {
        Scope.enter();
        try {
            final Frame trainF = parseTestFile("smalldata/prostate/prostate_complete.csv.zip");
            Scope.track(trainF);
            final String response = "DPROS";
            final ModelSelectionModel model = runModel(trainF, response, gamma, 1, 
                    new String[]{"C1"});
            Scope.track_generic(model);
            Map<String, Double>[] backwardCoeffs = model.coefficients();
            // compare model summary and result frame
            final Frame resultF = model.result();
            Scope.track(resultF);
            final int numModelsBuilt = (int) resultF.numRows();
            List<String> ignoredCols = new ArrayList<>(Arrays.asList("C1"));
            for (int modelInd = numModelsBuilt-1; modelInd >= 0; modelInd--) {  // manually built GLM,  compare result
                GLMModel.GLMParameters glmParams = new GLMModel.GLMParameters();
                glmParams._family = gamma;
                glmParams._lambda = new double[]{0.0};
                glmParams._compute_p_values = true;
                glmParams._ignored_columns = ignoredCols.toArray(new String[0]);
                glmParams._response_column = response;
                glmParams._train = trainF._key;
                glmParams._seed = 12345;
                GLMModel oneModel = new GLM(glmParams).trainModel().get();
                Scope.track_generic(oneModel);
                Map<String, Double> glmCoeffs = oneModel.coefficients();
                assertCoeffsMatch(glmCoeffs, backwardCoeffs[modelInd]); // check coefficients
                // check zvalues
                assertValsMatch(oneModel._output.getZValues(), resultF.vec("z_values").stringAt(modelInd)); 
                // check pvalues
                assertValsMatch(oneModel._output.pValues(), resultF.vec("p_values").stringAt(modelInd));
                // remove predictor with smallest z-score magnitude and move on to next iteration
                removePredictor(ignoredCols, oneModel); // add removed predictor to ignoredCols
            }
        } finally {
            Scope.exit();
        }
    }
    
    public void removePredictor(List<String> ignoredCols, GLMModel model) {
        String[] coefNames = model._output.coefficientNames();
        List<Double> zscore = Arrays.stream(model._output.getZValues()).boxed().collect(Collectors.toList());
        zscore.remove(zscore.size()-1); // remove zscore correspond to intercept
        double minMag = Double.MAX_VALUE;
        int minIndex = -1;
        int arrLen = zscore.size();
        for (int index = 0; index < arrLen; index++) {
            if (Math.abs(zscore.get(index)) <= minMag) {
                minMag = Math.abs(zscore.get(index));
                minIndex = index;
            }
        }
        ignoredCols.add(coefNames[minIndex]);
    }
    
    public void assertValsMatch(double[] values, String valuesStr) {
        String[] allValues = valuesStr.split(",");
        int valuesLen = values.length;
        assertTrue("values compared should have same length.", valuesLen == allValues.length);
        for (int index=0; index < valuesLen; index++) {
            assertTrue("z or p-value should equal.", 
                    Math.abs(values[index]-Double.valueOf(allValues[index])) < TOLERANCE);
        }
    }

    public void assertValsMatch(String values, String valuesStr) {
        String[] allValues = valuesStr.split(",");
        String[] valStr = values.split(",");
        int valuesLen = valStr.length;
        assertTrue("values compared should have same length.", valuesLen == allValues.length);
        for (int index=0; index < valuesLen; index++) {
            assertTrue("z or p-value should equal.",
                    Math.abs(Double.valueOf(valStr[index])-Double.valueOf(allValues[index])) < TOLERANCE);
        }
    }
    
    public void assertCoeffsMatch(Map<String, Double> coeff1, Map<String, Double> coeff2) {
        assertTrue("Coefficient sizes are different", coeff1.size() == coeff2.size());
        for (String key : coeff1.keySet()) {
            assertTrue("Coefficient values are different", 
                    Math.abs(coeff1.get(key)-coeff2.get(key)) < TOLERANCE);
        }
    }

    
    public ModelSelectionModel runModel(Frame trainingFrame, String responseColumn, 
                                        GLMModel.GLMParameters.Family family, int min_predictor_number,
                                        String[] ignoredCols) {
        ModelSelectionModel.ModelSelectionParameters parms = new ModelSelectionModel.ModelSelectionParameters();
        parms._response_column = responseColumn;
        parms._family = family;
        parms._train = trainingFrame._key;
        parms._mode = backward;
        parms._seed = 12345;
        parms._min_predictor_number = min_predictor_number;
        if (ignoredCols != null)
            parms._ignored_columns = ignoredCols;
        return new hex.modelselection.ModelSelection(parms).trainModel().get();
    }
}
