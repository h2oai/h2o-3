package hex.infogram;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import hex.*;
import hex.deeplearning.DeepLearningModel;
import hex.genmodel.utils.DistributionFamily;
import hex.schemas.DRFV3;
import hex.schemas.DeepLearningV3;
import hex.schemas.GBMV3;
import hex.schemas.GLMV3;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBMModel;
import water.*;
import water.api.schemas3.ModelParametersSchemaV3;
import water.fvec.Frame;
import water.fvec.Vec;
import water.udf.CFuncRef;
import water.util.TwoDimTable;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.IntStream;

import static hex.genmodel.utils.DistributionFamily.bernoulli;
import static hex.genmodel.utils.DistributionFamily.multinomial;
import static hex.glm.GLMModel.GLMParameters;
import static hex.glm.GLMModel.GLMParameters.Family.binomial;
import static hex.infogram.InfoGramModel.InfoGramParameter.Algorithm.glm;
import static water.util.ArrayUtils.sort;

public class InfoGramModel extends Model<InfoGramModel, InfoGramModel.InfoGramParameter, InfoGramModel.InfoGramOutput> {
  /**
   * Full constructor
   *
   * @param selfKey
   * @param parms
   * @param output
   */
  public InfoGramModel(Key<InfoGramModel> selfKey, InfoGramParameter parms, InfoGramOutput output) {
    super(selfKey, parms, output);
  }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    assert domain == null;
    switch(_output.getModelCategory()) {
      case Binomial:
        return new ModelMetricsBinomial.MetricBuilderBinomial(domain);
      case Multinomial:
        return new ModelMetricsMultinomial.MetricBuilderMultinomial(_output.nclasses(), domain, _parms._auc_type);
      default:
        throw H2O.unimpl("Invalid ModelCateory "+_output.getModelCategory());
    }
  }
  
  @Override
  protected double[] score0(double[] data, double[] preds) {
    throw new UnsupportedOperationException("InfoGram does not support scoring on data.  It only provides information" +
            " on predictors and choose admissible features for users.  Users can take the admissible features, build" +
            "their own model and score with that model.");
  }

  @Override
  public Frame score(Frame fr, String destinationKey, Job j, boolean computeMetrics, CFuncRef customMetricFunc) {
    throw new UnsupportedOperationException("InfoGram does not support scoring on data.  It only provides information" +
            " on predictors and choose admissible features for users.  Users can take the admissible features, build" +
            "their own model and score with that model.");
  }
  
  public static class InfoGramParameter extends Model.Parameters {
    public Algorithm _infogram_algorithm = Algorithm.gbm;     // default to GBM
    public String _infogram_algorithm_params = new String();   // store user specific parameters for chosen algorithm
    public Algorithm _model_algorithm = Algorithm.gbm; // default to GBM to build final model
    public String _model_algorithm_params = new String();   // store user specific parameters for chosen algorithm
    public String[] _sensitive_attributes = null;     // store sensitive features to be excluded from final model
    public double _conditional_info_threshold = 0.1;  // default set by Deep
    public double _varimp_threshold = 0.1;            // default set by Deep
    public double _data_fraction = 1.0;              // fraction of data to use to calculate infogram
    public Model.Parameters _infogram_algorithm_parameters;   // store parameters of chosen algorithm
    public Model.Parameters _model_algorithm_parameters;   // store parameters of chosen algorithm
    public int _ntop = 50;                           // if 0 consider all predictors, otherwise, consider topk predictors
    public boolean _pval = false;                   // if true, will calculate p-value
    public int _parallelism;                        // how many models to build in parallel
    
    public enum Algorithm {
      deeplearning,
      drf,
      gbm,
      glm
    }
    
    @Override
    public String algoName() {
      return "InfoGram";
    }

    @Override
    public String fullName() {
      return "Information Diagram";
    }

    @Override
    public String javaName() {
      return InfoGramModel.class.getName();
    }

    @Override
    public long progressUnits() {
      return 0;
    }

    /**
     * This method performs the following functions:
     * 1. extract the algorithm specific parameters from _algorithm_params to _algorithm_parameters which will be 
     * one of GBMParameters, DRFParameters, DeepLearningParameters, GLMParameters.
     * 2. Next, it will copy the parameters that are common to all algorithms from InfoGramParameters to 
     * _algorithm_parameters.
     */
    /**
     * This method performs the following functions:
     * 1. when fillInfoGram = true, it will extract the algorithm specific parameters from _info_algorithm_params to
     * infogram_algorithm_parameters which will be one of GBMParameters, DRFParameters, DeepLearningParameters or 
     * GLMParameters.  This will be used to build models and extract the infogram.
     * 2. when fillInfoGram = false, it will extract the algorithm specific parameters from _model_algorithm_params to 
     * _model_algorithm_parameters.  This will be used to build the final model.
     * 3. Next, it will copy the parameters that are common to all algorithms from InfoGramParameters to 
     * _algorithm_parameters.
     */
    public void fillImpl(boolean fillInfoGram) {
      Properties p = new Properties();
      boolean fillParams;
      List<String> excludeList = new ArrayList<>(); // prevent overriding of parameters set by user
      if (fillInfoGram)
        fillParams = _infogram_algorithm_params != null && !_infogram_algorithm_params.isEmpty();
      else
        fillParams = _model_algorithm_params != null && !_model_algorithm_params.isEmpty();
      
      if (fillParams) { // only execute when algorithm specific parameters are filled in by user
        HashMap<String, String[]> map = fillInfoGram ?
                new Gson().fromJson(_infogram_algorithm_params, new TypeToken<HashMap<String, String[]>>() {}.getType()):
                new Gson().fromJson(_model_algorithm_params, new TypeToken<HashMap<String, String[]>>() {
        }.getType());
        for (Map.Entry<String, String[]> param : map.entrySet()) {
          String[] paramVal = param.getValue();
          String paramName = param.getKey();
          excludeList.add("_"+paramName);
          if (paramVal.length == 1) {
            p.setProperty(paramName, paramVal[0]);
          } else {
            p.setProperty(paramName, Arrays.toString(paramVal));
          }
        }
      }
      
      ModelParametersSchemaV3 paramsSchema;
      Model.Parameters params;
      Algorithm algoName = fillInfoGram ? _infogram_algorithm : _model_algorithm;
      switch (algoName) {
        case glm:
          paramsSchema = new GLMV3.GLMParametersV3();
          params = new GLMParameters();
          excludeList.add("_distribution");
  //        ((GLMParameters) params)._family = null;
          break;
        case gbm:
          paramsSchema = new GBMV3.GBMParametersV3();
          params = new GBMModel.GBMParameters();
          if (!excludeList.contains("_stopping_tolerance")) {
            params._stopping_tolerance = 0.01;  // set default to 0.01
            excludeList.add("_stopping_tolerance");
          }
          break;
        case drf:
          paramsSchema = new DRFV3.DRFParametersV3();
          params = new DRFModel.DRFParameters();
          if (!excludeList.contains("_stopping_tolerance")) {
            params._stopping_tolerance = 0.01;  // set default to 0.01
            excludeList.add("_stopping_tolerance");
          }
          break;
        case deeplearning:
          paramsSchema = new DeepLearningV3.DeepLearningParametersV3();
          params = new DeepLearningModel.DeepLearningParameters();
          break;
        default:
          throw new UnsupportedOperationException("Unknown algo: " + algoName);
      }

      paramsSchema.init_meta();
      if (fillInfoGram) {
        _infogram_algorithm_parameters = (Model.Parameters) paramsSchema
                .fillFromImpl(params)
                .fillFromParms(p, true)
                .createAndFillImpl();
      } else {
        _model_algorithm_parameters = (Model.Parameters) paramsSchema
                .fillFromImpl(params)
                .fillFromParms(p, true)
                .createAndFillImpl();
      }
      copyInfoGramParams(fillInfoGram, excludeList); // copy over InfoGramParameters that are applicable to model specific algos
    }

    public void copyInfoGramParams(boolean fillInfoGram, List<String> excludeList) {
      Field[] algoParams = Parameters.class.getDeclaredFields();
      Field algoField;
      for (Field oneField : algoParams) {
        try {
          String fieldName = oneField.getName();
          algoField = this.getClass().getField(fieldName);
          if (excludeList.size() == 0 || !excludeList.contains(fieldName)) {
            if (fillInfoGram)
              algoField.set(_infogram_algorithm_parameters, oneField.get(this));
            else
              algoField.set(_model_algorithm_parameters, oneField.get(this));
          }
        } catch (IllegalAccessException | NoSuchFieldException e) { // suppress error printing.  Only care about fields that are accessible
          ;
        }
      }
    }
  }
  
  public static class InfoGramOutput extends Model.Output {
    public double[] _admissible_cmi;  // conditional info for admissible features in _admissible_features
    public double[] _admissible_relevance;  // varimp values for admissible features in _admissible_features
    public String[] _admissible_features; // predictors chosen that exceeds both conditional_info and varimp thresholds
    public DistributionFamily _distribution;
    public double[] _cmi_raw; // cmi before normalization and for all predictors
    public double[] _cmi_normalize;
    public String[] _all_predictor_names;
    public double[] _relevance; // variable importance for all predictors
    public String _cmi_relevance_key;
    public Key<Frame> _cmiRelKey;
    public String[] _topKFeatures;
    
    @Override
    public ModelCategory getModelCategory() {
      if (bernoulli.equals(_distribution)) {
        return ModelCategory.Binomial;
      } else if (multinomial.equals(_distribution)) {
        return ModelCategory.Multinomial;
      }
      throw new IllegalArgumentException("InfoGram currently only support binomial and multinomial classification");
    }
    
    public InfoGramOutput(InfoGram b) { 
      super(b);
      if (glm.equals(b._parms._infogram_algorithm)) {
        if (binomial.equals(((GLMParameters) b._parms._infogram_algorithm_parameters)._family))
          _distribution = bernoulli;
        else if (multinomial.equals(((GLMParameters) b._parms._infogram_algorithm_parameters)._family))
            _distribution = multinomial;
      } else {
        _distribution = b._parms._infogram_algorithm_parameters._distribution;
      }
    }

    public void extractAdmissibleFeatures(TwoDimTable varImp, String[] topKPredictors, double[] cmi, 
                                          double cmiThreshold, double varImpThreshold) {
      int numRows = varImp.getRowDim();
      List<Double> varimps = new ArrayList<>();
      List<Double> predictorCMI = new ArrayList<>();
      List<String> topKList = new ArrayList<>(Arrays.asList(topKPredictors));
      List<String> admissiblePred = new ArrayList<>();
      String[] varRowHeaders = varImp.getRowHeaders();
      for (int index = 0; index < numRows; index++) { // extract predictor with varimp >= threshold
        double varimp = (double) varImp.get(index, 1);
        if (varimp >= varImpThreshold) {
          int predIndex = topKList.indexOf(varRowHeaders[index]);
          if (cmi[predIndex] > cmiThreshold) {
            varimps.add(varimp);
            predictorCMI.add(cmi[predIndex]);
            admissiblePred.add(topKPredictors[predIndex]);
          }
        }
      }
      _admissible_features = admissiblePred.toArray(new String[admissiblePred.size()]);
      _admissible_cmi = predictorCMI.stream().mapToDouble(i -> i).toArray();
      _admissible_relevance = varimps.stream().mapToDouble(i -> i).toArray();
    }

    public Key<Frame> generateCMIRelFrame() {
      Vec.VectorGroup vg = Vec.VectorGroup.VG_LEN1;
      Vec vName = Vec.makeVec(_all_predictor_names, vg.addVec());
      Vec vCMI = Vec.makeVec(_cmi_normalize, vg.addVec());
      Vec vRel = Vec.makeVec(_relevance, vg.addVec());
      Frame cmiRelFrame = new Frame(Key.<Frame>make(), new String[]{"Features", "CMI", "Relevance"}, 
              new Vec[]{vName, vCMI, vRel});
      DKV.put(cmiRelFrame);
      _cmiRelKey = cmiRelFrame._key;
      _cmi_relevance_key = _cmiRelKey.toString();
      return cmiRelFrame._key;
      
    }
    public void copyCMIRelevance( double[] cmiRaw, double[] cmi, String[] topKPredictors, 
                                        TwoDimTable varImp) {
      _cmi_raw = new double[cmi.length];
      System.arraycopy(cmiRaw, 0, _cmi_raw, 0, _cmi_raw.length);
      double[] distanceFromCorner = new double[cmi.length];
      _cmi_normalize = cmi.clone();
      _topKFeatures = topKPredictors.clone();
      _all_predictor_names = topKPredictors.clone();
      int numRows = varImp.getRowDim();
      String[] varRowHeaders = varImp.getRowHeaders();
      List<String> relNames = new ArrayList<String>(Arrays.asList(varRowHeaders));
      _relevance = new double[numRows];
      for (int index = 0; index < numRows; index++) { // extract predictor with varimp >= threshold
        int newIndex = relNames.indexOf(_all_predictor_names[index]);
        _relevance[index] = (double) varImp.get(newIndex, 1);
        distanceFromCorner[index] = _relevance[index]*_relevance[index]+_cmi_normalize[index]*_cmi_normalize[index];
      }
      int[] indices = IntStream.range(0, cmi.length).toArray();
      sort(indices, distanceFromCorner, -1, -1);
      sortCMIRel(indices);
    }

    /***
     * This method will sort _relvance, _cmi_raw, _cmi_normalize, _all_predictor_names such that features that
     * are closest to upper right corner of infogram comes first with the order specified in the index
     * @param indices
     */
    private void sortCMIRel(int[] indices) {
      int indexLength = indices.length;
      double[] rel = new double[indexLength];
      double[] cmiRaw = new double[indexLength];
      double[] cmiNorm = new double[indexLength];
      String[] predNames = new String[indexLength];
      for (int index = 0; index < indexLength; index++) {
        rel[index] = _relevance[indices[index]];
        cmiRaw[index] = _cmi_raw[indices[index]];
        cmiNorm[index] = _cmi_normalize[indices[index]];
        predNames[index] = _all_predictor_names[indices[index]];
      }
      _relevance = rel;
      _cmi_normalize = cmiNorm;
      _cmi_raw = cmiRaw;
      _all_predictor_names = predNames;
    }
  }
  

  @Override
  protected Futures remove_impl(Futures fs, boolean cascade) {
    super.remove_impl(fs, cascade);
    Keyed.remove(_output._cmiRelKey, fs, true);
    return fs;        
  }
  
  @Override
  protected AutoBuffer writeAll_impl(AutoBuffer ab) {
    if (_output._cmiRelKey != null)
      ab.putKey(_output._cmiRelKey);
    return super.writeAll_impl(ab);
  }
  
  @Override
  protected Keyed readAll_impl(AutoBuffer ab, Futures fs) {
    return super.readAll_impl(ab, fs);
  }
}
