package hex.Infogram;

import hex.Model;
import hex.ModelBuilder;
import hex.SplitFrame;
import hex.deeplearning.DeepLearningModel;
import hex.glm.GLMModel;
import hex.schemas.DRFV3;
import hex.schemas.DeepLearningV3;
import hex.schemas.GBMV3;
import hex.schemas.GLMV3;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBMModel;
import hex.tree.xgboost.XGBoostModel;
import water.DKV;
import water.Key;
import water.Scope;
import water.api.schemas3.ModelParametersSchemaV3;
import water.fvec.Frame;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.util.TwoDimTable;
import static hex.Infogram.InfogramModel.InfogramModelOutput._CMI_RAW_INDEX;
import static hex.Infogram.InfogramModel.InfogramModelOutput._COLUMN_INDEX;
import static hex.Infogram.InfogramModel.InfogramParameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.DoubleStream;

public class InfogramUtils {
  /**
   * This method will take the columns of _parms.train().  It will then remove the response, any columns in 
   * _parms._sensitive_attributes from the columns of _parms.train(), weights_column, offset_column.  Then, the 
   * columns that are left are the columns that are eligible to get their InfoGram.
   */
  public static String[] extractPredictors(InfogramParameters parms, Frame train, String foldColumnName) {
    List<String> colNames = new ArrayList<>(Arrays.asList(train.names()));
    String[] nonPredictors = parms.getNonPredictors();
    for (String nonPred : nonPredictors)
      colNames.remove(nonPred);
    if (parms._protected_columns != null)
      for (String protectPred : parms._protected_columns)
        colNames.remove(protectPred);
    if (foldColumnName != null)
      colNames.remove(foldColumnName);
    return colNames.toArray(new String[colNames.size()]);
  }

  /**
   * Method to run infogram model once in order to get the variable importance of the topK predictors
   */
  public static String[] extractTopKPredictors(InfogramParameters parms, Frame trainFrame,
                                               String[] eligiblePredictors, List<Key<Frame>> generatedFrameKeys) {
    if (parms._top_n_features >= eligiblePredictors.length) return eligiblePredictors;
    Frame topTrain = extractTrainingFrame(parms, eligiblePredictors, 1, trainFrame);
    generatedFrameKeys.add(topTrain._key);
    parms._infogram_algorithm_parameters._train = topTrain._key;
    ModelBuilder builder = ModelBuilder.make(parms._infogram_algorithm_parameters);
    Model builtModel = (Model) builder.trainModel().get();
    Scope.track_generic(builtModel);
    TwoDimTable varImp = extractVarImp(parms._algorithm, builtModel);
    String[] ntopPredictors = new String[parms._top_n_features];
    String[] rowHeaders = varImp.getRowHeaders();
    System.arraycopy(rowHeaders, 0, ntopPredictors, 0, parms._top_n_features);
    return ntopPredictors;
  }

  public static TwoDimTable extractVarImp(InfogramParameters.Algorithm algo, Model model) {
    switch (algo) {
      case gbm : return ((GBMModel) model)._output._variable_importances;
      case glm : return ((GLMModel) model)._output._variable_importances;
      case deeplearning : return ((DeepLearningModel) model)._output._variable_importances;
      case drf : return ((DRFModel) model)._output._variable_importances;
      case xgboost : return ((XGBoostModel) model)._output._variable_importances;
      default : return null;
    }
  }

  /**
   * This method will perform two functions:
   * - if user only wants a fraction of the training dataset to be used for infogram calculation, we will split the
   *   training frame and only use a fraction of it for infogram training purposes;
   * - next, a new training dataset will be generated containing only the predictors in predictors2Use array.
   */
  public static Frame extractTrainingFrame(InfogramParameters parms, String[] sensitivePredictors, double dataFraction,
                                           Frame trainFrame) {
    if (dataFraction < 1) {  // only use a fraction training data for speedup
      SplitFrame sf = new SplitFrame(trainFrame, new double[]{parms._data_fraction, 1-parms._data_fraction},
              new Key[]{Key.make("ig_train_"+trainFrame._key), Key.make("ig_discard"+trainFrame._key)});
      sf.exec().get();
      Key[] ksplits = sf._destination_frames;
      trainFrame = DKV.get(ksplits[0]).get();
      DKV.remove(ksplits[1]); // discard unwanted portion
    }
    final Frame extractedFrame = new Frame(Key.make());
    if (sensitivePredictors != null)
      for (String colName : sensitivePredictors) // add sensitive features to Frame
        extractedFrame.add(colName, trainFrame.vec(colName));
      
      String[] nonPredictors = parms.getNonPredictors();
    List<String> colNames = Arrays.asList(trainFrame.names());
      boolean cvWeightsPresent = parms._weights_column != null && colNames.contains(parms._weights_column)
            && parms._weights_column.equals("__internal_cv_weights__");
      for (String nonPredName : nonPredictors) {
        if ("__internal_cv_weights__".equals(nonPredName) && colNames.contains(parms._weights_column)) {
          String cvWeightName = "infogram_internal_cv_weights_"; // switch weights column to turn off cv in algo used to build infogram
          extractedFrame.add(cvWeightName, trainFrame.vec(nonPredName));
          parms._weights_column = cvWeightName;
        } else if (nonPredName.equals(parms._fold_column) && colNames.contains(parms._fold_column) && !cvWeightsPresent) {
          extractedFrame.add(nonPredName, trainFrame.vec(nonPredName));
        } else if (!nonPredName.equals(parms._fold_column)) {
          extractedFrame.add(nonPredName, trainFrame.vec(nonPredName));
        }
      }
      
      if (!(parms._fold_column != null && colNames.contains(parms._fold_column) && !cvWeightsPresent))
        parms._fold_column = null;

    DKV.put(extractedFrame);
    return extractedFrame;
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

  /***
   * Build model parameters for model specified in infogram_algorithm.  Any model specific parameters can be specified
   * in infogram_algorithm_params.
   */
  public static Model.Parameters[] buildModelParameters(Frame[] trainingFrames, Model.Parameters infoParams,
                                                        int numModels, InfogramParameters.Algorithm algoName) {
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
      modelParams[index]._ignored_columns = null; // training frame contains only needed columns
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
  
  public static Frame generateCMIRelevance(String[] allPredictorNames, double[] admissible, double[] admissibleIndex, 
                                           double[] relevance, double[] cmi, double[] cmiRaw, boolean buildCore) {      
    Vec.VectorGroup vg = Vec.VectorGroup.VG_LEN1;
    Vec vName = Vec.makeVec(allPredictorNames, vg.addVec());
    Vec vAdm = Vec.makeVec(admissible, vg.addVec());
    Vec vAdmIndex = Vec.makeVec(admissibleIndex, vg.addVec());
    Vec vRel = Vec.makeVec(relevance, vg.addVec());
    Vec vCMI = Vec.makeVec(cmi, vg.addVec());
    Vec vCMIRaw = Vec.makeVec(cmiRaw, vg.addVec());
    String[] columnNames = buildCore ? new String[]{"column", "admissible", "admissible_index", "total_information",
            "net_information", "net_information_raw"} : new String[]{"column", "admissible", "admissible_index",
            "relevance_index", "safety_index", "safety_index_raw"};
    Frame cmiRelFrame = new Frame(Key.<Frame>make(), columnNames, new Vec[]{vName, vAdm, vAdmIndex, vRel, vCMI, vCMIRaw});
    DKV.put(cmiRelFrame);
    return cmiRelFrame;
  }
  
  public static void removeFromDKV(List<Key<Frame>> generatedFrameKeys) {
    for (Key<Frame> oneFrameKey : generatedFrameKeys)
        DKV.remove(oneFrameKey);
  }
  
  /***
   * To calculate the CMI, refer to https://h2oai.atlassian.net/browse/PUBDEV-8075 section I step 2 for core infogram,
   * section II step 2 for fair infogram.  Note that the last model is built with all predictors for core infogram or
   * built with protected columns for fair infogram.
   */
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
    double scale = maxCMI == 0 ? 0 : 1.0/maxCMI;
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

  public static void extractInfogramInfo(InfogramModel infoModel, double[][] cmiRaw, 
                                         List<List<String>> columns, int foldIndex) {
    Frame validFrame = DKV.getGet(infoModel._output._relevance_cmi_key_valid);
    // relCMIFrame contains c1:column, c2:admissible, c3:admissible_index, c4:relevance, c5:cmi, c6 cmi_raw
    cmiRaw[foldIndex] = vec2array(validFrame.vec(_CMI_RAW_INDEX));
    String[] oneColumn = strVec2array(validFrame.vec(_COLUMN_INDEX));
    ArrayList<String> oneFrameColumn = new ArrayList(Arrays.asList(oneColumn));
    columns.add(oneFrameColumn);
    validFrame.remove();
  }

  static double[] vec2array(Vec v) {
    assert v.length() < Integer.MAX_VALUE;
    final int len = (int) v.length();
    double[] array = new double[len];
    for (int i = 0; i < len; i++) array[i] = v.at(i);
    return array;
  }

  static String[] strVec2array(Vec v) {
    assert v.length() < Integer.MAX_VALUE;
    final int len = (int) v.length();
    BufferedString bs = new BufferedString();
    String[] array = new String[len];
    for (int i = 0; i < len; i++) {
      BufferedString s = v.atStr(bs, i);
      if (s != null) array[i] = s.toString();
    }
    return array;
  }
}
