package hex.infogram;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelMetrics;
import hex.SplitFrame;
import hex.deeplearning.DeepLearningModel;
import hex.glm.GLMModel;
import hex.schemas.DRFV3;
import hex.schemas.DeepLearningV3;
import hex.schemas.GBMV3;
import hex.schemas.GLMV3;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBMModel;
import water.DKV;
import water.Key;
import water.Scope;
import water.api.schemas3.ModelParametersSchemaV3;
import water.fvec.Frame;
import water.util.TwoDimTable;

import java.util.*;
import java.util.stream.DoubleStream;

import static hex.infogram.InfoGramModel.InfoGramParameter;
import static hex.infogram.InfoGramModel.InfoGramParameter.Algorithm;
public class InfoGramUtils {
  
  /**
   * This method will take the columns of _parms.train().  It will then remove the response, any columns in 
   * _parms._sensitive_attributes from the columns of _parms.train(), weights_column, offset_column.  Then, the 
   * columns that are left are the columns that are eligible to get their InfoGram.
   * 
   * @param _parms
   * @return
   */
  public static String[] extractPredictors(InfoGramParameter _parms) {
    List<String> colNames = new ArrayList<>(Arrays.asList(_parms.train().names()));
    List<String> excludeCols = new ArrayList<>(Arrays.asList(_parms._response_column));
    if (!(_parms._sensitive_attributes == null))
      excludeCols.addAll(Arrays.asList(_parms._sensitive_attributes));  // remove sensitive attributes
    if (_parms._weights_column != null)
      excludeCols.add(_parms._weights_column);  // remove weight
    if (_parms._offset_column != null)
      excludeCols.add(_parms._offset_column);  // remove offset
    if (_parms._ignored_columns != null)
      excludeCols.addAll(Arrays.asList(_parms._ignored_columns));
    
    colNames.removeAll(excludeCols);  // remove sensitive attributes, response, weight/offset columns
    return colNames.toArray(new String[colNames.size()]);
  }

  /**
   * Method to run infogram model once in order to get the variable importance of the topK predictors
   * @param parms
   * @param trainFrame
   * @param eligiblePredictors
   * @return
   */
  public static String[] extractTopKPredictors(InfoGramParameter parms, Frame trainFrame, String[] eligiblePredictors) {
    if (parms._ntop >= eligiblePredictors.length) return eligiblePredictors;
    Frame topTrain = extractTrainingFrame(parms, eligiblePredictors, 1, trainFrame);
    Scope.track(topTrain);
    parms._infogram_algorithm_parameters._train = topTrain._key;
    ModelBuilder builder = ModelBuilder.make(parms._infogram_algorithm_parameters);
    Model builtModel = (Model) builder.trainModel().get();
    Scope.track_generic(builtModel);
    TwoDimTable varImp = extractVarImp(parms._infogram_algorithm, builtModel);
    String[] ntopPredictors = new String[parms._ntop];
    String[] rowHeaders = varImp.getRowHeaders();
    System.arraycopy(rowHeaders, 0, ntopPredictors, 0, parms._ntop);
    return ntopPredictors;
  }
  
  public static TwoDimTable extractVarImp(Algorithm algo, Model model) {
    switch (algo) {
      case gbm : return ((GBMModel) model)._output._variable_importances;
      case glm : return ((GLMModel) model)._output._variable_importances;
      case deeplearning : return ((DeepLearningModel) model)._output._variable_importances;
      case drf : return ((DRFModel) model)._output._variable_importances;
      default : return null;
    }
  }

  /**
   * This method will perform two functions:
   * - if user only wants a fraction of the training dataset to be used for infogram calculation, we will split the
   *   training frame and only use a fraction of it for infogram training purposes;
   * - next, a new training dataset will be generated containing only the predictors in predictors2Use array. 
   *
   * @param parms
   * @param sensitivePredictors
   * @param dataFraction
   * @param 
   * @return
   */
  public static Frame extractTrainingFrame(InfoGramParameter parms, String[] sensitivePredictors, double dataFraction, 
                                           Frame trainFrame) {
    if (dataFraction < 1) {  // only use a fraction training data for speedup
      SplitFrame sf = new SplitFrame(trainFrame, new double[]{parms._data_fraction, 1-parms._data_fraction}, 
              new Key[]{Key.make("train.hex"), Key.make("discard.hex")});
      sf.exec().get();
      Key[] ksplits = sf._destination_frames;
      trainFrame = DKV.get(ksplits[0]).get();
      DKV.remove(ksplits[1]); // discard unwanted portion
    }
    final Frame extractedFrame = new Frame(Key.make());
    if (sensitivePredictors != null)
      for (String colName : sensitivePredictors) // add sensitive features to Frame
        extractedFrame.add(colName, trainFrame.vec(colName));

    List<String> colNames = Arrays.asList(trainFrame.names());
    if (parms._weights_column != null && colNames.contains(parms._weights_column))
      extractedFrame.add(parms._weights_column, trainFrame.vec(parms._weights_column));
    if (parms._offset_column != null && colNames.contains(parms._offset_column))
      extractedFrame.add(parms._offset_column, trainFrame.vec(parms._offset_column));
    if (parms._response_column != null && colNames.contains(parms._response_column))
      extractedFrame.add(parms._response_column, trainFrame.vec(parms._response_column));

    DKV.put(extractedFrame);
    return extractedFrame;
  }
  
  public static void buildAlgorithmParameters(InfoGramParameter parms) {
    
  }
  
  public static void copyAlgoParams(ModelBuilder builder, InfoGramParameter parms) {
    String inputAlgoName = parms._infogram_algorithm.name();
    String algoName = ModelBuilder.algoName(inputAlgoName);
    String schemaDir = ModelBuilder.schemaDirectory(inputAlgoName);
    int algoVersion = 3;
    if (algoName.equals("SVD") || algoName.equals("Aggregator") || algoName.equals("StackedEnsemble")) algoVersion=99;
    String paramSchemaName = schemaDir+algoName+"V"+algoVersion+"$"+ModelBuilder.paramName(inputAlgoName)+"V"+algoVersion;
  }
  
  public static String[] generateModelDescription(String[] topKPredictors, String[] sensitive_attributes) {
    int numModel = topKPredictors.length+1;
    String[] modelNames = new String[numModel];
    int numPredInd = topKPredictors.length-1;
    if (sensitive_attributes == null) { // contains only predictors
      for (int index = 0; index < numPredInd; index++)
        modelNames[index] = "Model built missing predictor "+topKPredictors[index];
      modelNames[numPredInd] = "Full model built with all predictors";
    } else {  // contains one predictor and all sensitive_attributes
      for (int index = 0; index < numPredInd; index++)
        modelNames[index] = "Model built with sensitive_features and predictor "+topKPredictors[index];
      modelNames[numPredInd] = "Model built with sensitive_features only";
    }
    return modelNames;
  }
  
  public static Frame[] buildTrainingFrames(String[] topKPredictors, Frame trainingFrame, Frame baseFrame,
                                            int startInd, int numFrames, boolean buildCore, int lastModelInd) {
    Frame[] trainingFrames = new Frame[numFrames];
    int finalFrameInd = startInd + numFrames;
    int frameCount = 0;
    for (int frameInd = startInd; frameInd < finalFrameInd; frameInd++) {
      trainingFrames[frameCount] = new Frame(baseFrame);
      if (buildCore) { // add predictor frameInd to sensitiveFrame
        for (int vecInd = 0; vecInd < topKPredictors.length; vecInd++) {
          if ((frameInd < lastModelInd) && (vecInd != frameInd)) // skip ith vector except last model
            trainingFrames[frameCount].add(topKPredictors[vecInd], trainingFrame.vec(topKPredictors[vecInd]));
          else if (frameInd == lastModelInd)// add all predictors
            trainingFrames[frameCount].add(topKPredictors[vecInd], trainingFrame.vec(topKPredictors[vecInd]));
        }
      } else {
        if (frameInd < lastModelInd) // use baseFrame for last model
          trainingFrames[frameCount].prepend(topKPredictors[frameInd], trainingFrame.vec(topKPredictors[frameInd]));
      }
      Scope.track(trainingFrames[frameCount]);
      DKV.put(trainingFrames[frameCount++]);
    }
    return trainingFrames;
  }

  public static Model.Parameters[] buildModelParameters(Frame[] trainingFrames, Model.Parameters infoParams, 
                                                        int numModels, Algorithm algoName) {
    ModelParametersSchemaV3 paramsSchema;
    switch (algoName) {
      case glm:
        paramsSchema = new GLMV3.GLMParametersV3();
        break;
      case gbm:
        paramsSchema = new GBMV3.GBMParametersV3();
        break;
      case drf:
        paramsSchema = new DRFV3.DRFParametersV3();
        break;
      case deeplearning:
        paramsSchema = new DeepLearningV3.DeepLearningParametersV3();
        break;
      default:
        throw new UnsupportedOperationException("Unknown algo: " + algoName);
    }
    Model.Parameters[] modelParams = new Model.Parameters[numModels];
    for (int index = 0; index < numModels; index++) {
      modelParams[index] = (Model.Parameters) paramsSchema.fillFromImpl(infoParams).createAndFillImpl();
      modelParams[index]._train = trainingFrames[index]._key;
    }
    return modelParams;
  }
  
  public static ModelBuilder[] buildModelBuilders(Model.Parameters[] modelParams) {
    int numModel = modelParams.length;
    ModelBuilder[] modelBuilders = new ModelBuilder[numModel];
    for (int index = 0; index < numModel; index++)
      modelBuilders[index] = ModelBuilder.make(modelParams[index]);
    return modelBuilders;
  }

  public static void generateInfoGrams(ModelBuilder[] builders, Frame[] trainingFrames, double[] cmi, int startIndex,
                                       int numModels, String response) {
    for (int index = 0; index < numModels; index++) {
      Model oneModel = builders[index].get();  // extract model
      Frame prediction = oneModel.score(trainingFrames[index]); // generate prediction
      prediction.add(response, trainingFrames[index].vec(response));
      Scope.track_generic(oneModel);
      Scope.track(prediction);
      cmi[index+startIndex] = new EstimateCMI(prediction).doAll(prediction)._meanCMI;  // calculate intermediate CMI
    }
  }

  public static double[] calculateFinalCMI(double[] cmiRaw, boolean buildCore) {
    int lastInd = cmiRaw.length-1; // index of full model or model with sensitive features only
    double maxCMI = 0;
    for (int index = 0; index < lastInd; index++) {
      if (buildCore)
        cmiRaw[index] = Math.max(0, cmiRaw[lastInd] - cmiRaw[index]);
      else
        cmiRaw[index] = Math.max(0, cmiRaw[index] - cmiRaw[lastInd]);
      
      if (cmiRaw[index] > maxCMI)
        maxCMI = cmiRaw[index];
    }
    double scale = 1.0/maxCMI;
    double[] cmi = new double[lastInd];
    double[] cmiLong = DoubleStream.of(cmiRaw).map(d->d*scale).toArray();
    System.arraycopy(cmiLong, 0, cmi, 0, lastInd);
    return cmi;   
  }
  
  public static Frame subtractAdd2Frame(Frame base, Frame featureFrame, String[] removeFeatures, String[] addFeatures) {
    Frame newFrame = new Frame(base);
    if (removeFeatures != null) {
      for (String removeEle : removeFeatures)
        newFrame.remove(removeEle);
    }
    for (String addEle : addFeatures)
      newFrame.add(addEle, featureFrame.vec(addEle));
    DKV.put(newFrame);
    return newFrame;
  }
  
  public static void fillModelMetrics(Model model, Model finalModel, Frame trainingFrame, Algorithm  algo_name) {
    model._output._training_metrics = finalModel._output._training_metrics;
    for (Key<ModelMetrics> modelMetricsKey : finalModel._output.getModelMetrics()) 
      model.addModelMetrics(modelMetricsKey.get().deepCloneWithDifferentModelAndFrame(finalModel, trainingFrame));
    model._output._scoring_history = copyTwoDimTable(finalModel._output._scoring_history, "Scoring history of "+
            algo_name +" final model");
    model._output._model_summary = copyTwoDimTable(finalModel._output._model_summary, "Model summary of "+
            algo_name +" final model");
  }

  public static TwoDimTable copyTwoDimTable(TwoDimTable table, String tableHeader) {
    String[] rowHeaders = table.getRowHeaders();
    String[] colTypes = table.getColTypes();
    int tableSize = rowHeaders.length;
    int colSize = colTypes.length;
    TwoDimTable tableCopy = new TwoDimTable(tableHeader, "",
            rowHeaders, table.getColHeaders(), colTypes, table.getColFormats(),
            "names");
    for (int rowIndex = 0; rowIndex < tableSize; rowIndex++)  {
      for (int colIndex = 0; colIndex < colSize; colIndex++) {
        tableCopy.set(rowIndex, colIndex,table.get(rowIndex, colIndex));
      }
    }
    return tableCopy;
  }
}
