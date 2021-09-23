package hex.anovaglm;

import hex.*;
import hex.deeplearning.DeepLearningModel;
import hex.glm.GLM;
import hex.glm.GLMModel;
import org.apache.commons.math3.distribution.FDistribution;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.udf.CFuncRef;
import water.util.TwoDimTable;

import java.io.Serializable;
import java.util.Arrays;

import static hex.anovaglm.ANOVAGLMUtils.generateGLMSS;
import static hex.glm.GLMModel.GLMParameters.*;
import static hex.glm.GLMModel.GLMParameters.Family.AUTO;


public class ANOVAGLMModel extends Model<ANOVAGLMModel, ANOVAGLMModel.ANOVAGLMParameters, ANOVAGLMModel.ANOVAGLMModelOutput>{
  public ANOVAGLMModel(Key<ANOVAGLMModel> selfKey, ANOVAGLMParameters parms, ANOVAGLMModelOutput output) {
    super(selfKey, parms, output);
  }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    assert domain == null;
    switch (_output.getModelCategory()) {
      case Binomial:
        return new ModelMetricsBinomial.MetricBuilderBinomial(domain);
      case Multinomial:
        return new ModelMetricsMultinomial.MetricBuilderMultinomial(_output.nclasses(), domain, _parms._auc_type);
      case Regression:
        return new ModelMetricsRegression.MetricBuilderRegression();
      default:
        throw H2O.unimpl("Invalid ModelCategory " + _output.getModelCategory());
    }
  }

  @Override
  protected double[] score0(double[] data, double[] preds) {
    throw new UnsupportedOperationException("ANOVAGLM does not support scoring on data.  It only provide information" +
            " on predictor relevance");
  }

  @Override
  public Frame score(Frame fr, String destination_key, Job j, boolean computeMetrics, CFuncRef customMetricFunc) {
    throw new UnsupportedOperationException("ANOVAGLM does not support scoring on data.  It only provide information" +
            " on predictor relevance");
  }

  /***
   * Return the ANOVA table as an H2OFrame per seb suggestion
   * @return H2O Frame containing the ANOVA table as in the model summary
   */
  public Frame resultFrame() {
    assert _output._resultFramekey != null : "ANOVA Table Key is null";
    return DKV.getGet(_output._resultFramekey);
  }
  
  public static class ANOVAGLMParameters extends Model.Parameters {
    public int _highest_interaction_term;
    public double[] _alpha;
    public double[] _lambda = new double[]{0};
    public boolean _standardize = true;
    public Family _family = AUTO;
    public boolean lambda_search;
    public Link _link = Link.family_default;
    public Solver _solver = Solver.IRLSM;
    public String[] _interactions=null;
    public double _tweedie_variance_power;
    public double _tweedie_link_power=1.0;
    public double _theta;
    public double _invTheta;
    public Serializable _missing_values_handling = MissingValuesHandling.MeanImputation;
    public boolean _compute_p_values = true;
    public boolean _remove_collinear_columns = true;
    public int _nfolds = 0; // disable cross-validation
    public Key<Frame> _plug_values = null;
    public boolean _save_transformed_framekeys = false; // for debugging, save the transformed predictors/interaction
    public int _nparallelism = 4;

    @Override
    public String algoName() {
      return "ANOVAGLM";
    }

    @Override
    public String fullName() {
      return "ANOVA for Generalized Linear Model";
    }

    @Override
    public String javaName() { return ANOVAGLMModel.class.getName(); }

    @Override
    public long progressUnits() {
      return 1;
    }

    public MissingValuesHandling missingValuesHandling() {
      if (_missing_values_handling instanceof MissingValuesHandling)
        return (MissingValuesHandling) _missing_values_handling;
      assert _missing_values_handling instanceof DeepLearningModel.DeepLearningParameters.MissingValuesHandling;
      switch ((DeepLearningModel.DeepLearningParameters.MissingValuesHandling) _missing_values_handling) {
        case MeanImputation:
          return MissingValuesHandling.MeanImputation;
        case Skip:
          return MissingValuesHandling.Skip;
        default:
          throw new IllegalStateException("Unsupported missing values handling value: " + _missing_values_handling);
      }
    }

    public boolean imputeMissing() {
      return missingValuesHandling() == MissingValuesHandling.MeanImputation ||
              missingValuesHandling() == MissingValuesHandling.PlugValues;
    }

    public DataInfo.Imputer makeImputer() {
      if (missingValuesHandling() == MissingValuesHandling.PlugValues) {
        if (_plug_values == null || _plug_values.get() == null) {
          throw new IllegalStateException("Plug values frame needs to be specified when Missing Value Handling = PlugValues.");
        }
        return new GLM.PlugValuesImputer(_plug_values.get());
      } else { // mean/mode imputation and skip (even skip needs an imputer right now! PUBDEV-6809)
        return new DataInfo.MeanImputer();
      }
    }
  }

  public static class ANOVAGLMModelOutput extends Model.Output {
    DataInfo _dinfo;
    public long _training_time_ms;
    public String[][] _coefficient_names; // coefficient names of all models
    Family _family;
    public String _transformed_columns_key;
    public Key<Frame> _transformedColumnKey;
    public Key<Frame> _resultFramekey;
    public String _result_frame_key;
    public TwoDimTable[] _coefficients_table;

    @Override
    public ModelCategory getModelCategory() {
      switch (_family) {
        case quasibinomial:
        case fractionalbinomial:
        case binomial: return ModelCategory.Binomial;
        case multinomial: return ModelCategory.Multinomial;
        case ordinal: return ModelCategory.Ordinal;
        default: return ModelCategory.Regression;
      }
    }

    public String[][] coefficientNames() { return _coefficient_names; }

    public ANOVAGLMModelOutput(ANOVAGLM b, DataInfo dinfo) {
      super(b, dinfo._adaptedFrame);
      _dinfo = dinfo;
      _domains = dinfo._adaptedFrame.domains();
      _family = b._parms._family;
    }
  }

  /**
   * The Type III SS calculation, degree of freedom, F-statistics and p-values will be included in the model
   * summary.  For details on how those are calculated, refer to ANOVAGLMTutorial  
   * https://h2oai.atlassian.net/browse/PUBDEV-8088 section V.
   * 
   * @param modelNames
   * @param glmModels
   * @param degreeOfFreedom
   * @return
   */
  public TwoDimTable generateSummary(String[] modelNames, GLMModel[] glmModels, int[] degreeOfFreedom){
    String[] names = new String[]{"model", "family", "link", "ss", "df", "ms", "f", "p_value"};
    String[] types = new String[]{"string", "string", "string", "double", "double", "double", "double", "double"};
    String[] formats = new String[]{"%s", "%s", "%s", "%d", "%d",  "%d", "%d", "%d"};
    int numModel = glmModels.length;
    int lastModelIndex = numModel-1;
    String[] familyNames = new String[lastModelIndex];
    String[] linkNames = new String[lastModelIndex];
    String[] rowHeaders = new String[lastModelIndex];
    double[] pValues = new double[lastModelIndex];
    double[] msA = new double[lastModelIndex];
    double[] fA = new double[lastModelIndex];
    System.arraycopy(modelNames, 0, rowHeaders, 0, lastModelIndex);
    double[] ss = generateGLMSS(glmModels, _parms._family);
    long dofFullModel = glmModels[lastModelIndex]._output._training_metrics.residual_degrees_of_freedom();
    double mse = ss[lastModelIndex]/dofFullModel;
    double oneOverMse = 1.0/mse;
    _output._model_summary = new TwoDimTable("GLM ANOVA Type III SS", "summary", 
            rowHeaders, names, types, formats, "");
    
    for (int rIndex = 0; rIndex < lastModelIndex; rIndex++) {
      int colInd = 0;
      _output._model_summary.set(rIndex, colInd++, modelNames[rIndex]);
      _output._model_summary.set(rIndex, colInd++, _parms._family.toString());
      _output._model_summary.set(rIndex, colInd++, _parms._link.toString());
      familyNames[rIndex] = _parms._family.toString();
      linkNames[rIndex] = _parms._link.toString();
      _output._model_summary.set(rIndex, colInd++, ss[rIndex]);
      _output._model_summary.set(rIndex, colInd++, degreeOfFreedom[rIndex]);
      double ms = ss[rIndex]/degreeOfFreedom[rIndex];
      msA[rIndex] = ms;
      _output._model_summary.set(rIndex, colInd++, ms);
      FDistribution fdist = new FDistribution(degreeOfFreedom[rIndex], dofFullModel);
      double f = oneOverMse*ss[rIndex]/degreeOfFreedom[rIndex];
      fA[rIndex] = f;
      _output._model_summary.set(rIndex, colInd++, f);
      double p_value = 1.0 - fdist.cumulativeProbability(f);
      _output._model_summary.set(rIndex, colInd, p_value);
      pValues[rIndex] = p_value;
    }
    _output._resultFramekey = generateANOVATableFrame(names, modelNames, familyNames, linkNames, ss,
            Arrays.stream(degreeOfFreedom).asDoubleStream().toArray(), msA, fA, pValues);
    _output._result_frame_key = _output._resultFramekey.toString();
    return _output._model_summary;
  }

  public Key<Frame> generateANOVATableFrame(String[] colNames, String[] rowNames, String[] familyNames,
                                            String[] linkNames, double[] ss, double[] dof, double[] msA, double[] fA,
                                            double[] pValues) {
    Vec.VectorGroup vg = Vec.VectorGroup.VG_LEN1;
    Vec rNames = Vec.makeVec(rowNames, vg.addVec());
    Vec fNames = Vec.makeVec(familyNames, vg.addVec());
    Vec lNames = Vec.makeVec(linkNames, vg.addVec());
    Vec sumSquares = Vec.makeVec(ss, vg.addVec());
    Vec degOfFreedom = Vec.makeVec(dof, vg.addVec());
    Vec msV = Vec.makeVec(msA, vg.addVec());
    Vec fV = Vec.makeVec(fA, vg.addVec());
    Vec pValuesV = Vec.makeVec(pValues, vg.addVec());
    Frame anovaFrame = new Frame(Key.<Frame>make(), colNames, new Vec[]{rNames, fNames, lNames, sumSquares, 
            degOfFreedom, msV, fV, pValuesV});
    DKV.put(anovaFrame);
    return anovaFrame._key;
  }
  
  @Override
  protected Futures remove_impl(Futures fs, boolean cascade) {
    super.remove_impl(fs, cascade);
    Keyed.remove(_output._transformedColumnKey, fs, true);
    return fs;
  }

  @Override
  protected AutoBuffer writeAll_impl(AutoBuffer ab) {
    if (_output._transformedColumnKey != null)
      ab.putKey(_output._transformedColumnKey);
    return super.writeAll_impl(ab);
  }

  @Override
  protected Keyed readAll_impl(AutoBuffer ab, Futures fs) {
    return super.readAll_impl(ab, fs);
  }
}
