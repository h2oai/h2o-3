package hex.modelselection;

import hex.glm.GLM;
import hex.glm.GLMModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.IcedWrapper;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.TwoDimTable;

import java.util.*;
import java.util.stream.Collectors;

import static hex.gam.GamTestPiping.massageFrame;
import static hex.glm.GLMModel.GLMParameters.Family.*;
import static hex.modelselection.ModelSelectionMaxRTests.compareResultFModelSummary;
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
            final String[] frameNames = new String[]{"coefficient_names", "predictor_names", "z_values", "p_values", "predictors_removed"};
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
     * added support to include categorical columns in modelselection backward mode.
     */
    @Test
    public void testWithCatPredictors() {
        Scope.enter();
        try {
            final Frame trainF = Scope.track(parseTestFile("smalldata/demos/bank-additional-full.csv"));
            ModelSelectionModel.ModelSelectionParameters parms = new ModelSelectionModel.ModelSelectionParameters();
            parms._response_column = "y";
            parms._lambda = new double[]{0.0};
            parms._remove_collinear_columns = true;
            parms._train = trainF._key;
            parms._mode = backward;
            parms._seed = 12345;
            parms._ignored_columns = new String[]{"previous", "poutcome", "pdays"};
            parms._min_predictor_number = 5;
            final ModelSelectionModel fullModel = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Scope.track_generic(fullModel);
            assertCorrectPredElimination(fullModel);
        } finally {
            Scope.exit();
        }
    }

    /***
     * Check and make sure at each model building step, the predictor with the smallest z-value magnitude is removed.
     */
    public static void assertCorrectPredElimination(ModelSelectionModel fullModel) {
        List<String> catPreds = Arrays.asList("job", "marital", "education", "default", "housing", "loan", "contact",
                "month", "day_of_week", "poutcome");
        List<String> catCoeffs = Arrays.asList("job.blue-collar", "job.entrepreneur", "job.housemaid", "job.management", 
                "job.retired", "job.self-employed", "job.services", "job.student", "job.technician", "job.unemployed", 
                "job.unknown", "education.basic.6y", "education.basic.9y", "education.high.school", 
                "education.illiterate",  "education.professional.course", "education.university.degree", 
                "education.unknown", "day_of_week.mon", "day_of_week.thu", "day_of_week.tue", "day_of_week.wed", 
                "month.jul", "month.jun", "month.may", "marital.married", "marital.single", "marital.unknown", 
                "housing.unknown", "housing.yes", "loan.unknown", "loan.yes", "contact.telephone");
        String[][] allModelCoefs = fullModel._output._coefficient_names;
        double[][] allModelZValues = fullModel._output._z_values;
        int maxModelIndex = allModelCoefs.length-1;
        
        for (int modInd=maxModelIndex; modInd >= 1; modInd--) {
            List<String> modelCoefsHigh = Arrays.asList(allModelCoefs[modInd]);
            List<String> modelCoefsLow = Arrays.asList(allModelCoefs[modInd-1]);
            double[] modelZValues = allModelZValues[modInd];
            List<String> removedPredName = extractRemovedPredictors(modelCoefsHigh, modelCoefsLow);
            List<String> redundantPredictors = new ArrayList<>();
            if (modInd==maxModelIndex) {
                List<String> redundantPreds = Arrays.stream(fullModel._output._predictors_removed_per_step[maxModelIndex]).
                        filter(x->x.contains("(redundant_predictor)")).map(x -> x.substring(0, x.indexOf('('))).collect(Collectors.toList());
                redundantPredictors = removedPredName.stream().filter(x -> !redundantPreds.contains((x+"(redundant_predictor)"))).collect(Collectors.toList());
            }
            assertCorrectMinZRemoved(modelCoefsHigh, modelZValues, removedPredName, catPreds, catCoeffs, redundantPredictors);
        }
    }
    
    public static List<String> extractRemovedPredictors(List<String> modelCoefsHigh, List<String> modelCoefsLow) {
        List<String> removedCoeffNames = new ArrayList<>();
        for (String oneCoeff : modelCoefsHigh) {
            if (modelCoefsHigh.contains(oneCoeff) && !modelCoefsLow.contains(oneCoeff))
                removedCoeffNames.add(oneCoeff);
        }
        return removedCoeffNames;
    }

    public static void assertCorrectMinZRemoved(List<String> modelCoefs, double[] modelZValues,
                                                List<String> removedPreds, List<String> catPreds, List<String> catCoeffs, 
                                                List<String> redundantPreds) {
        double[] removedZValues = new double[removedPreds.size()];
        int counter = 0;
        for (String removedPred : removedPreds) {
            if (!redundantPreds.contains(removedPred)) {
                double zVal = modelZValues[modelCoefs.indexOf(removedPred)];
                if (Double.isNaN(zVal))
                    removedZValues[counter++] = 0.0;
                else
                    removedZValues[counter++] = Math.abs(zVal);
            }
        }
        double minZMag = ArrayUtils.maxValue(removedZValues);
        Log.info(" predictor size " + modelCoefs.size());
        for (String name : modelCoefs) {
            Log.info("coefficient ", name);
            if (!name.equals("Intercept")) {    // exclude Intercept term
                double modelZVal = extractModelZVal(name, modelCoefs, modelZValues, catPreds, catCoeffs);
                if (Double.isNaN(modelZVal))
                    modelZVal = 0.0;
                assertTrue("Wrong predictor " + name + " is eliminated with higher z-value.",
                        Math.abs(modelZVal) >= minZMag);
            }
        }
    }
    
    public static double extractModelZVal(String predName, List<String> modelCoeffs, double[] modelZValues, 
                                          List<String> catPreds, List<String> catCoeffs) {
        boolean catCol = catCoeffs.contains(predName);
        if (catCol) {  // categorical predictors here
            String predPrefix = null;
            for (String catPred : catPreds) {
                if (predName.contains(catPred)) {
                    predPrefix = catPred;
                    break;
                }
            }
            List<Double> catZValues = new ArrayList<>();
            for (String onePred : modelCoeffs) {
                if (onePred.contains(predPrefix)) {
                    double zVal = modelZValues[modelCoeffs.indexOf(onePred)];
                    if (Double.isNaN(zVal))
                        catZValues.add(0.0);
                    else
                        catZValues.add(Math.abs(zVal));
                }
            }
            return Collections.max(catZValues);
        } else {
            return modelZValues[modelCoeffs.indexOf(predName)];
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
            parms._mode = backward;
            ModelSelectionModel modelBackward = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Scope.track_generic(modelBackward); //  model with validation dataset
            compareResultFModelSummary(modelBackward);
        } finally {
            Scope.exit();
        }
    }
}
