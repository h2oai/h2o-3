package hex.maxrglm;

import hex.Model;
import hex.glm.GLM;
import hex.glm.GLMModel;
import water.DKV;
import water.Key;
import water.Scope;
import water.fvec.Frame;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static hex.genmodel.utils.MathUtils.combinatorial;

public class MaxRGLMUtils {
    /**
     * Given the number of predictors in the training frame and the maximum predictor number, we are going to calculate
     * the number of models that we need to build in order to find:
     * - best model with 1 predictor;
     * - best model with 2 predictors;
     * ...
     * - best model with naxPredictorNumber.
     * 
     * This basically boils down to calculating the following:
     * combination(numPredictors, 1) + combination(numPredictors, 2) + ... + combination(numPredictors, maxPredictorNumber)
     * 
     * @param numPredictors: number of predictors in the training frame
     * @param maxPredictorNumber: maximum number of predictors of interest
     * @return an integer that is the number of models that are going to be built
     */
    public static int calculateModelNumber(int numPredictors, int maxPredictorNumber) {
        int modelNumber = 0;
        for (int index = 1; index <= maxPredictorNumber; index++) {
            modelNumber += combinatorial(numPredictors, index);
        }
        return modelNumber;
    }
    
    public static Frame[] generateTrainingFrames(MaxRGLMModel.MaxRGLMParameters parms, int predNum, String[] predNames, 
                                                 int numModels, String foldColumn) {
        int maxPredNum = predNames.length;
        Frame[] trainFrames = new Frame[numModels];
        int[] predIndices = IntStream.range(0, predNum).toArray();   // contains indices to predictor names
        int zeroBound = maxPredNum-predNum;
        int[] bounds = IntStream.range(zeroBound, maxPredNum).toArray();   // highest combo value
        for (int frameCount = 0; frameCount < numModels; frameCount++) {    // generate one combo
            trainFrames[frameCount] = generateOneFrame(predIndices, parms, predNames, foldColumn);
            DKV.put(trainFrames[frameCount]);
            updatePredIndices(predIndices, bounds);
        }
        return trainFrames;
    }

    /***
     * Given predictor indices stored in currentPredIndices, we need to find the next combination of predictor indices
     * to use to generate the next combination.  For example, if we have 4 predictors and we are looking to take two 
     * predictors, predictor indices can change in the following sequence [0,1]->[0,2]->[0,3]->[1,2]->[1,2]->[2,3]. 
     *
     * @param currentPredIndices
     * @param indicesBounds
     */
    public static void updatePredIndices(int[] currentPredIndices, int[] indicesBounds) {
        int lastPredInd = currentPredIndices.length-1;
        for (int index = lastPredInd; index >= 0; index--) {
            if (currentPredIndices[index] < indicesBounds[index]) { // increase LSB first
                currentPredIndices[index]++;
                updateLaterIndices(currentPredIndices, index, lastPredInd);
                break;
            } 
        }
    }

    /***
     * Give 5 predictors and say we want the combo of 3 predictors, this function will properly reset the prediction
     * combination indices say from [0, 1, 4] -> [0, 2, 3] or [0, 3, 4] -> [1, 2, 3].  Given an index that was just
     * updated, it will update the indices that come later in the list correctly.
     * 
     * @param currentPredIndices
     * @param indexUpdated
     * @param lastPredInd
     */
    public static void updateLaterIndices(int[] currentPredIndices, int indexUpdated, int lastPredInd) {
        for (int index = indexUpdated; index < lastPredInd; index++) {
            currentPredIndices[index+1] = currentPredIndices[index]+1;
        }
    }
    
    /***
     *     Given a predictor indices set, this function will generate a training frame containing the predictors with
     *     indices in predIndices.
     *     
     * @param predIndices
     * @param parms
     * @param predNames
     * @return
     */
    public static Frame generateOneFrame(int[] predIndices, MaxRGLMModel.MaxRGLMParameters parms, String[] predNames, 
                                         String foldColumn) {
        final Frame predVecs = new Frame(Key.make());
        final Frame train = parms.train();
        int numPreds = predIndices.length;
        for (int index = 0; index < numPreds; index++) {
            int predVecNum = predIndices[index];
            predVecs.add(predNames[predVecNum], train.vec(predNames[predVecNum]));
        }
        if (parms._weights_column != null)
            predVecs.add(parms._weights_column, train.vec(parms._weights_column));
        if (parms._offset_column != null)
            predVecs.add(parms._offset_column, train.vec(parms._offset_column));
        if (foldColumn != null)
            predVecs.add(foldColumn, train.vec(foldColumn));
        predVecs.add(parms._response_column, train.vec(parms._response_column));
        return predVecs;
    }
    
    public static GLMModel.GLMParameters[] generateGLMParameters(Frame[] trainingFrames,
                                                                 MaxRGLMModel.MaxRGLMParameters parms, int nfolds,
                                                                 String foldColumn, 
                                                                 Model.Parameters.FoldAssignmentScheme foldAssignment) {
        final int numModels = trainingFrames.length;
        GLMModel.GLMParameters[] params = new GLMModel.GLMParameters[numModels];
        final Field[] field1 = MaxRGLMModel.MaxRGLMParameters.class.getDeclaredFields();
        final Field[] field2 = Model.Parameters.class.getDeclaredFields();
        for (int index = 0; index < numModels; index++) {
            params[index] = new GLMModel.GLMParameters();
            setParamField(parms, params[index], false, field1, Collections.emptyList());
            setParamField(parms, params[index], true, field2, Collections.emptyList());
            params[index]._train = trainingFrames[index]._key;
            params[index]._family = parms._family;
            params[index]._nfolds = nfolds;
            params[index]._fold_column = foldColumn;
            params[index]._fold_assignment = foldAssignment;
        }
        return params;
    }
    
    public static void setParamField(Model.Parameters params, GLMModel.GLMParameters glmParam, boolean superClassParams,
                                     Field[] paramFields, List<String> excludeList) {
        // assign relevant GAMParameter fields to GLMParameter fields
        Field glmField;
        boolean emptyExcludeList = excludeList.size() == 0;
        for (Field oneField : paramFields) {
            try {
                if (emptyExcludeList || !excludeList.contains(oneField.getName())) {
                    if (superClassParams)
                        glmField = glmParam.getClass().getSuperclass().getDeclaredField(oneField.getName());
                    else
                        glmField = glmParam.getClass().getDeclaredField(oneField.getName());
                    glmField.set(glmParam, oneField.get(params));
                }
            } catch (IllegalAccessException|NoSuchFieldException e) { // suppress error printing, only cares about fields that are accessible
                ;
            }
        }    
    }

    /***
     * Given parms.train(), generate an String array that contains names of elligible predictors.
     * 
     * @param parms
     * @return
     */
    public static String[] generatePredictorNames(MaxRGLMModel.MaxRGLMParameters parms) {
        List<String> excludedNames = new ArrayList<String>(Arrays.asList(parms._response_column));
        if (parms._ignored_columns != null)
            excludedNames.addAll(Arrays.asList(parms._ignored_columns));
        if (parms._weights_column != null)
            excludedNames.add(parms._weights_column);
        if (parms._offset_column != null)
            excludedNames.add(parms._offset_column);
        
        List<String> predNames = new ArrayList<>(Arrays.asList(parms.train().names()));
        predNames.removeAll(excludedNames);
        return predNames.toArray(new String[0]);
    }
    
    public static GLM[] buildGLMBuilders(GLMModel.GLMParameters[] trainingParams) {
        int numModels = trainingParams.length;
        GLM[] builders = new GLM[numModels];
        for (int index=0; index<numModels; index++)
            builders[index] = new GLM(trainingParams[index]);
        return builders;
    }
    
    public static void removeTrainingFrames(Frame[] trainingFrames) {
        for (Frame oneFrame : trainingFrames) 
            DKV.remove(oneFrame._key);
    }

    /**
     * Given GLM run results of a fixed number of predictors, the model with the best R2 value will be extracted into
     * arrays bestModelPredictors and bestR2VValues
     * @param bestModelPredictors
     * @param bestR2Values
     * @param glmResults
     * @param numPredMinus1
     */
    public static void extractBestModels(String[][] bestModelPredictors, double[] bestR2Values, GLM[] glmResults, 
                                         int numPredMinus1) {
        double bestR2Val = 0;
        String[] bestPreds = null;
        int numModels = glmResults.length;
        for (int index = 0; index < numModels; index++) {
            GLMModel oneModel = glmResults[index].get();
            Scope.track_generic(oneModel);
            double currR2 = oneModel.r2();
            if (oneModel._parms._nfolds > 0) {
                int r2Index = Arrays.asList(oneModel._output._cross_validation_metrics_summary.getRowHeaders()).indexOf("r2");
                Float tempR2 = (Float) oneModel._output._cross_validation_metrics_summary.get(r2Index, 0);
                currR2 = tempR2.doubleValue();
            }
            if (currR2 > bestR2Val) {
                bestR2Val = currR2;
                bestPreds = oneModel._output.coefficientNames().clone();
            }
        }
        bestR2Values[numPredMinus1] = bestR2Val;
        int predNum = bestPreds.length-1;   // copy over coefficient names excluding the intercept
        bestModelPredictors[numPredMinus1] = new String[predNum];
        System.arraycopy(bestPreds, 0, bestModelPredictors[numPredMinus1], 0, predNum);
    }
}
