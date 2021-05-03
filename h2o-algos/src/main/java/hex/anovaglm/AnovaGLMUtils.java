package hex.anovaglm;

import hex.*;
import hex.glm.GLM;
import hex.glm.GLMModel;
import water.DKV;
import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.util.TwoDimTable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static hex.anovaglm.AnovaGLMModel.AnovaGLMParameters;
import static hex.gam.MatrixFrameUtils.GAMModelUtils.*;
import static hex.gam.MatrixFrameUtils.GamUtils.setParamField;
import static hex.glm.GLMModel.GLMParameters;
import static hex.glm.GLMModel.GLMParameters.Family.*;

public class AnovaGLMUtils {
  /***
   * This method will extract the individual predictor names that will be used to build the GLM models.
   * 
   * @param dinfo: DataInfo generated from dataset with all predictors
   * @param numOfPredictors: number of individual predictors
   * @return: copy of individual predictor names in a String array.
   */
  public static String[] extractPredNames(DataInfo dinfo,  int numOfPredictors) {
    String[] predNames = new String[numOfPredictors];
    String[] frameNames = dinfo._adaptedFrame.names();
    System.arraycopy(frameNames, 0, predNames, 0, numOfPredictors);
    return predNames;
  }

  /**
   * In order to calculate Type III SS, we need the individual predictors and their interactions.  For details, refer
   * to AnovaGLMTutorial section IV
   * 
   * @param predNamesIndividual: string containing individual predictor names
   * @param maxPredInt: maximum number of predictors allowed in interaction term generation
   * @return String array with double indices.  First index refers to the predictor number, second index refers to
   *         the names of predictors involved in generating the interaction terms.  For terms involving only
   *         individual predictors, there is only one predictor name.
   */
  public static String[][] generatePredictorCombos(String[] predNamesIndividual, int maxPredInt) {
    List<String[]> predCombo = new ArrayList<>();
    addIndividualPred(predNamesIndividual, predCombo);  // add individual predictors
    for (int index = 2; index <= maxPredInt; index++) {
      generateOneCombo(predNamesIndividual, index, predCombo);
    }
    return predCombo.toArray(new String[0][0]);
  }
  
  public static void addIndividualPred(String[] predNames, List<String[]> predCombo) {
    int numPred = predNames.length;
    for (int index=0; index < numPred; index++) {
      predCombo.add(new String[]{predNames[index]});
    }
  }
  
  public static void generateOneCombo(String[] predNames, int numInteract, List<String[]> predCombo) {
    int predNum = predNames.length;
    int[] predInd = IntStream.range(0, numInteract).toArray();
    int zeroBound = predNum-numInteract;
    int[] bounds = IntStream.range(zeroBound, predNum).toArray();
    int numCombo = hex.genmodel.utils.MathUtils.combinatorial(predNum, numInteract);
    for (int index = 0; index < numCombo; index++) {
      predCombo.add(predCombo(predNames, predInd));
      if (!updatePredCombo(predInd, bounds))
        break;  // done
    }
  }
  
  public static boolean updatePredCombo(int[] predInd, int[] bounds) {
    int predNum = predInd.length-1;
    for (int index = predNum; index >= 0; index--) {
      if (predInd[index] < bounds[index]) {  // 
        predInd[index]++;
        updateLaterBits(predInd, bounds, index, predNum);
        return true;
      } 
    }
    return false;
  }
  
  public static void updateLaterBits(int[] predInd, int[] bounds, int index, int predNum) {
    if (index < predNum) {
      for (int ind = index+1; ind <= predNum; ind++) {
        predInd[ind] = predInd[ind-1]+1;
      }
    }
  }
  
  public static String[] predCombo(String[] predNames, int[] predInd) {
    int predNum = predInd.length;
    String[] predCombos = new String[predNum];
    for (int index = 0; index < predNum; index++)
      predCombos[index] = predNames[predInd[index]];
    return predCombos;
  }

  /***
   * Given the number of individual predictors, the highest order of interaction terms allowed, this method will
   * calculate the total number of predictors that will be used to build the full model.
   * 
   * @param numPred: number of individual predictors
   * @param highestInteractionTerms: highest number of predictors allowed in generating interactions
   * @return
   */
  public static int calculatePredComboNumber(int numPred, int highestInteractionTerms) {
    int numCombo = numPred;
    for (int index = 2; index <= highestInteractionTerms; index++) 
      numCombo += hex.genmodel.utils.MathUtils.combinatorial(numPred, index);
    return numCombo;
  }

  /***
   * This method will take the frame that contains transformed columns of predictor A, predictor B, interaction
   * of predictor A and B and generate new training frames that contains the following columns:
   * - transformed columns of predictor B, interaction of predictor A and B, response
   * - transformed columns of predictor A, interaction of predictor A and B, response
   * - transformed columns of predictor A, predictor B, response
   * - transformed columns of predictor A, predictor B, interaction of predictor A and B, response
   * 
   * The same logic applies if there are more than two individual predictors.  You basically generate all the 
   * predictor combos.  In building the model, you leave one predictor combo out.
   * 
   * @param transformedCols: contains frame key of frame containing transformed columns of predictor A, predictor B,
   *                      interaction of predictor A and B
   * @param numberOfModels: number of models to build.  For 2 factors, this should be 4.
   * @return Array of training frames to build all the GLM models.
   */
  public static Frame[] buildTrainingFrames(Key<Frame> transformedCols, int numberOfModels, 
                                            String[][] transformedColNames, AnovaGLMParameters parms) {
    Frame[] trainingFrames = new Frame[numberOfModels];
    int numFrames2Build = numberOfModels-1;
    Frame allCols = DKV.getGet(transformedCols);  // contains all the transformed columns except response, weight/offset
    trainingFrames[numFrames2Build] = allCols;

    int[][] predNums = new int[numFrames2Build][];
    for (int index = 0; index < numFrames2Build; index++) {
      predNums[index] = oneIndexOut(index, numFrames2Build);
    }

    for (int index = 0; index < numFrames2Build; index++) {
      trainingFrames[index] = buildSpecificFrame(predNums[index], allCols, transformedColNames, parms);
      DKV.put(trainingFrames[index]);
    }
    return trainingFrames;
  }
  
  public static int[] oneIndexOut(int currIndex, int indexRange) {
    int[] indexArray = new int[indexRange-1];
    int count = 0;
    for (int index = 0; index < indexRange; index++) {
      if (index != currIndex) {
        indexArray[count++] = index;
      }
    }
    return indexArray;
  }

  /***
   * This method will copy the GLM cofficients from all GLM models and stuff them into a TwoDimTable array for
   * the AnovaGLMModel._output.
   * 
   * @param model: AnovaGLMModel onto which the GLM coefficients will be copied to
   * @param glmModels: all GLM models built
   * @param modelNames: string describing each GLM model built in terms of which predictor combo is left out.
   */
  public static void copyGLMCoeffs(AnovaGLMModel model, GLMModel[] glmModels, String[] modelNames) {
    int numModels = glmModels.length;
    model._output._coefficients_table = new TwoDimTable[numModels];
    model._output._coefficient_names = new String[numModels][];
    for (int index = 0; index < numModels; index++) {
      model._output._coefficients_table[index] = genCoefficientTable(new String[]{"coefficients",
                      "standardized coefficients"}, glmModels[index]._output.beta(),
              glmModels[index]._output.getNormBeta(), glmModels[index]._output._coefficient_names,
              "Coefficients for " + modelNames[index]);
      model._output._coefficient_names[index] = glmModels[index]._output._coefficient_names.clone();
    }
  }

  /**
   * I copied this method from Zuzana Olajcova to add model metrics of the full GLM model as the AnovaModel model
   * metrics
   * @param aModel
   * @param glmModel
   * @param trainingFrame
   */
  public static void fillModelMetrics(AnovaGLMModel aModel, GLMModel glmModel, Frame trainingFrame) {
    aModel._output._training_metrics = glmModel._output._training_metrics;
    for (Key<ModelMetrics> modelMetricsKey : glmModel._output.getModelMetrics()) {
      aModel.addModelMetrics(modelMetricsKey.get().deepCloneWithDifferentModelAndFrame(glmModel, trainingFrame));
    }
    aModel._output._scoring_history = copyTwoDimTable(glmModel._output._scoring_history, "glm scoring history");
  }

  /***
   * Simple method to extract GLM Models from GLM ModelBuilders.
   * @param glmResults: array of GLM ModelBuilders
   * @return: array of GLMModels
   */
  public static GLMModel[] extractGLMModels(GLM[] glmResults) {
    int numberModel = glmResults.length;
    GLMModel[] models = new GLMModel[numberModel];
    for (int index = 0; index < numberModel; index++) {
      models[index] = glmResults[index].get();
      Scope.track_generic(models[index]);
    }
    return models;
  }
  
  public static void removeFromDKV(Frame[] trainingFrames, int numFrame2Delete) {
    for (int index=0; index < numFrame2Delete; index++)
      DKV.remove(trainingFrames[index]._key);
  }

  /***
   * This method is used to attach the weight/offset columns if they exist and the response columns, specific 
   * transformed columns to a training frames.
   * 
   * @param predNums: number of all predictor combos
   * @param allCols: Frame containing all transformed columns
   * @param transformedColNames: transformed predictor combo arrays containing only predictor combos for a specific
   *                           training dataset.  Recall that models are built with one predictor combo left out.  This
   *                           is to generate that training frame with a specific predictor combo left out.
   * @param parms: AnovaGLMParameters
   * @return training frame excluding a specific set of predictor combos.
   */
  public static Frame buildSpecificFrame(int[] predNums, Frame allCols, String[][] transformedColNames, 
                                         AnovaGLMParameters parms) {
    final Frame predVecs = new Frame(Key.make());
    int numVecs = predNums.length;
    for (int index = 0; index < numVecs; index++) {
      int predVecNum = predNums[index];
      predVecs.add(allCols.subframe(transformedColNames[predVecNum]));
    }
    if (parms._weights_column != null)
      predVecs.add(parms._weights_column, allCols.vec(parms._weights_column));
    if (parms._offset_column != null)
      predVecs.add(parms._offset_column, allCols.vec(parms._offset_column));
    predVecs.add(parms._response_column, allCols.vec(parms._response_column));
    return predVecs;
  }
  
  public static GLMParameters[] buildGLMParameters(Frame[] trainingFrames, AnovaGLMParameters parms) {
    int numberOfModels = trainingFrames.length;
    GLMParameters[] glmParams = new GLMParameters[numberOfModels];
    List<String> anovaGLMOnlyList = Arrays.asList("save_transformed_framekeys", "type");
    for (int index = 0; index < numberOfModels; index++) {
      glmParams[index] = new GLMParameters();
      Field[] field1 = AnovaGLMParameters.class.getDeclaredFields();
      setParamField(parms, glmParams[index], false, field1, anovaGLMOnlyList);
      Field[] field2 = Model.Parameters.class.getDeclaredFields();
      setParamField(parms, glmParams[index], true, field2, Arrays.asList());
      glmParams[index]._train = trainingFrames[index]._key;
      glmParams[index]._family = parms._family;
    }
    return glmParams;
  }

  /***
   * This method is used to generate Model SS for all models built except the full model.  Refer to AnovaGLMTutorial
   * section V.
   * 
   * @param glmModels
   * @param family
   * @return
   */
  public static double[] generateGLMSS(GLMModel[] glmModels, GLMParameters.Family family) {
    int numModels = glmModels.length;
    int lastModelIndex = numModels-1;
    double[] modelSS = new double[numModels];
    double[] rss = new double[numModels];
    for (int index = 0; index < numModels; index++) {
      if (binomial.equals(family) || quasibinomial.equals(family) || fractionalbinomial.equals(family))
        rss[index] = ((ModelMetricsBinomialGLM) glmModels[index]._output._training_metrics).residual_deviance();
      else  // for numerical response column
        rss[index] = ((ModelMetricsRegressionGLM) glmModels[index]._output._training_metrics).residual_deviance();
    }
    // calculate model ss as rss - rss with full model
   for (int index = 0; index < lastModelIndex; index++)
     modelSS[index] = rss[index]-rss[lastModelIndex];
   modelSS[lastModelIndex] = rss[lastModelIndex];
    return modelSS;
  }
  
  public static GLM[] buildGLMBuilders(GLMParameters[] glmParams) {
    int numModel = glmParams.length;  // copied from Zuzana
    GLM[] builder = new GLM[numModel];
    for (int index = 0; index < numModel; index++)
      builder[index] = new GLM(glmParams[index]);
    return builder;
  }

  /***
   * This method aims to generate the column names of the final transformed frames.  This means that for single
   * enum predictor "ABC" with domains "0" and "1", "2", the new column names will be ABC_0, ABC_1.  For single 
   * numerical column, the same column name will be used.
   * 
   * To generate the names of interaction columns, let's assume there are three predictors, R (2 levels), C (3 levels),
   * S (3 levels).  If the highest interaction terms allowed is 3, we will generate the following transformed names
   * for the interaction columns: R0:C0, R0:C1, C0:S0, C0:S1, C1:S0, C1:S1, R0:C0:S0, R0:C0:S1, R0:C1:S0, R0:C1:S1
   * @param predComboNames: string array containing all predictor combos and for each combo, all the predictor names
   *                      involved in generating the interactions.
   * @param predictorNames: string array containing all predictor names and for each combo, all the predictor names
   *    *                      involved in generating the interactions.
   * @param predColumnStart: column of each predictor combo after the frame transformation.
   * @param degreeOfFreedom: degree of freedom for each predictor combo
   * @param dinfo
   */
  public static void generatePredictorNames(String[][] predComboNames, String[][] predictorNames, int[] predColumnStart, 
                                            int[] degreeOfFreedom, DataInfo dinfo) {
    int predNums = predComboNames.length;
    int colStart = 0;
    for (int predInd = 0; predInd < predNums; predInd++) {
      if (predComboNames[predInd].length == 1) {
        if (dinfo._adaptedFrame.vec(predComboNames[predInd][0]).domain() == null) // one numeric column
          predictorNames[predInd] = new String[]{predComboNames[predInd][0]};
        else 
          predictorNames[predInd] = transformOneCol(dinfo._adaptedFrame, predComboNames[predInd][0]);
      } else {  // working with interaction columns
          predictorNames[predInd] = transformMultipleCols(dinfo._adaptedFrame, predComboNames, predInd, predictorNames);
      }
      colStart = updateDOFColInfo(predInd, predictorNames[predInd], degreeOfFreedom, predColumnStart, colStart);
    }
  }
  
  public static int updateDOFColInfo(int predInd, String[] predComboNames, int[] dof, int[] predCS, int offset) {
    dof[predInd] = predComboNames.length;;
    predCS[predInd] = offset;
    return dof[predInd]+offset;
  }
  
  public static int findComboMatch(String[][] predComboNames, int currIndex) {
    String[] currCombo = predComboNames[currIndex];
    int startPos = 1;
    for (int comboSize = currCombo.length-1; comboSize >= 0; comboSize--) {
      String[] smallerCurrCombo = Arrays.copyOfRange(currCombo, startPos++, currCombo.length);
      for (int sInd = currIndex - 1; sInd >= 0; sInd--) {
        if (Arrays.equals(smallerCurrCombo, predComboNames[sInd]))
          return sInd;
      }
    }
    return -1;
  }

  public static String[] combineAndFlat(String[][] predictComboNames) {
    int numCombos = predictComboNames.length;
    String[] finalPredNames = new String[numCombos];
    
    for (int index = 0; index < numCombos; index++) {
      String start = predictComboNames[index][0];
      if (predictComboNames[index].length > 1)
      for (int subIndex = 1; subIndex < predictComboNames[index].length; subIndex++)
        start = start +":"+predictComboNames[index][subIndex];
      finalPredNames[index] = start;
    }
    return finalPredNames;
  }

  public static String[] transformMultipleCols(Frame vec2Transform, String[][] predComboNames, int currIndex,
                                               String[][] predNames) {
    String[] currPredCombo = predComboNames[currIndex];
    int matchPreviousCombo = findComboMatch(predComboNames, currIndex);
    String[] matchPredCombo = predComboNames[matchPreviousCombo];
    String[] matchPredNames = predNames[matchPreviousCombo];
    String[] searchPair = new String[]{currPredCombo[0], currPredCombo[1]};
    matchPredNames = transformTwoCols(vec2Transform, searchPair, matchPredNames);

    return matchPredNames;
  }

  /**
   * Generate frame transformation on two interacting columns.  Refer to AnovaGLMTutorial sectinos III.II. and IV.
   * 
   * @param vec2Transform: frame containing the two predictors to transform
   * @param vecNames: name of the predictors
   * @param lastComboNames: predictor combo names of the second vector if applicable.  This is used to transform
   *                      more than two predictors
   * @return String containing the transformed column names.
   */
  public static String[] transformTwoCols(Frame vec2Transform, String[] vecNames, String[] lastComboNames) {
    String[] domains1 = vec2Transform.vec(vecNames[0]).domain();
    String[] domains2 = lastComboNames == null ? vec2Transform.vec(vecNames[1]).domain() : lastComboNames;
    String colName1 = vecNames[0];
    String colName2 = vecNames[1];
    int degOfFreedomC1 = domains1 == null ? 1 : (domains1.length-1);
    int degOfFreedomC2 = lastComboNames == null ? (domains2.length-1) : domains2.length;
    String[] newColNames = new String[degOfFreedomC1*degOfFreedomC2];
    int colIndex = 0;
    for (int col1 = 0; col1 < degOfFreedomC1; col1++) {
      String part1 = colName1;
      if (domains1 != null)
        part1 = colName1 + "_" + domains1[col1];
      for (int col2 = 0; col2 < degOfFreedomC2; col2++) {
        if (lastComboNames == null) {
          if (domains2 == null)
            newColNames[colIndex++] = part1 + ":" + colName2;
          else
            newColNames[colIndex++] = part1 + ":" + colName2 + "_" + domains2[col2];
        } else {
          newColNames[colIndex++] = part1 + ":"+domains2[col2];
        }
      }
    }
    return newColNames;
  }

  /**
   * perform data transformation described in AnovaGLMTutorial section III.II on one predictor.
   * 
   * @param vec2Transform: frame containing that one predictor to transform.
   * @param vecName: name of predictor
   * @return: string array containing the transformed predictor column names.
   */
  public static String[] transformOneCol(Frame vec2Transform, String vecName) {
    String[] domains = vec2Transform.vec(vecName).domain();
    int degOfFreedom = domains.length-1;
    String[] newColNames = new String[degOfFreedom];
    for (int domainInd = 0; domainInd < degOfFreedom; domainInd++)
      newColNames[domainInd] = vecName+"_"+domains[domainInd];
    return newColNames;
  }
  
  public static String[] generateModelNames(String[][] predictComboNames) {
    int numPredCombo = predictComboNames.length;
    String[] modelNames = new String[numPredCombo+1];
    for (int index=0; index < numPredCombo; index++) {
      if (predictComboNames[index].length == 1)
        modelNames[index] = "GLM model built without predictor " + predictComboNames[index][0];
      else
        modelNames[index] = "GLM model built without predictors interactions " +
                Stream.of(predictComboNames[index]).collect(Collectors.joining(":"));
    }
    modelNames[numPredCombo] = "GLM model built with all predictors";
    return modelNames;
  }
}
