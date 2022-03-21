package hex.modelselection;

import hex.*;
import hex.deeplearning.DeepLearningModel;
import hex.glm.GLM;
import hex.glm.GLMModel;
import org.apache.commons.lang.ArrayUtils;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.udf.CFuncRef;
import water.util.TwoDimTable;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import static hex.glm.GLMModel.GLMParameters.Family.AUTO;
import static hex.modelselection.ModelSelectionUtils.*;

public class ModelSelectionModel extends Model<ModelSelectionModel, ModelSelectionModel.ModelSelectionParameters,
        ModelSelectionModel.ModelSelectionModelOutput> {
    
    public ModelSelectionModel(Key<ModelSelectionModel> selfKey, ModelSelectionParameters parms, ModelSelectionModelOutput output) {
        super(selfKey, parms, output);
    }

    @Override
    public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
        assert domain == null;
        switch (_output.getModelCategory()) {
            case Regression:
                return new ModelMetricsRegression.MetricBuilderRegression();
            default:
                throw H2O.unimpl("Invalid ModelCategory " + _output.getModelCategory());
        }
    }

    @Override
    protected double[] score0(double[] data, double[] preds) {
        throw new UnsupportedOperationException("ModelSelection does not support scoring on data.  It only provide " +
                "information on predictor relevance");
    }

    @Override
    public Frame score(Frame fr, String destination_key, Job j, boolean computeMetrics, CFuncRef customMetricFunc) {
        throw new UnsupportedOperationException("AnovaGLM does not support scoring on data.  It only provide " +
                "information on predictor relevance");
    }

    @Override
    public Frame result() {
        return _output.generateResultFrame();
    }
    
    public static class ModelSelectionParameters extends Model.Parameters {
        public double[] _alpha;
        public double[] _lambda;
        public boolean _standardize = true;
        GLMModel.GLMParameters.Family _family = AUTO;
        public boolean _lambda_search;
        public GLMModel.GLMParameters.Link _link = GLMModel.GLMParameters.Link.family_default;
        public GLMModel.GLMParameters.Solver _solver = GLMModel.GLMParameters.Solver.IRLSM;
        public String[] _interactions=null;
        public Serializable _missing_values_handling = GLMModel.GLMParameters.MissingValuesHandling.MeanImputation;
        public boolean _compute_p_values = false;
        public boolean _remove_collinear_columns = false;
        public int _nfolds = 0; // disable cross-validation
        public Key<Frame> _plug_values = null;
        public int _max_predictor_number = 1;
        public int _min_predictor_number = 1;
        public int _nparallelism = 0;
        public double _p_values_threshold = 0;
        public double _tweedie_variance_power;
        public double _tweedie_link_power;
        public Mode _mode = Mode.maxr;  // mode chosen to perform model selection

        public enum Mode {
            allsubsets, // use combinatorial, exponential runtime
            maxr, // use sequential replacement
            backward // use backward selection
        }
        @Override
        public String algoName() {
            return "ModelSelection";
        }

        @Override
        public String fullName() {
            return "Model Selection";
        }

        @Override
        public String javaName() {
            return ModelSelectionModel.class.getName();
        }

        @Override
        public long progressUnits() {
            return 1;
        }

        public GLMModel.GLMParameters.MissingValuesHandling missingValuesHandling() {
            if (_missing_values_handling instanceof GLMModel.GLMParameters.MissingValuesHandling)
                return (GLMModel.GLMParameters.MissingValuesHandling) _missing_values_handling;
            assert _missing_values_handling instanceof DeepLearningModel.DeepLearningParameters.MissingValuesHandling;
            switch ((DeepLearningModel.DeepLearningParameters.MissingValuesHandling) _missing_values_handling) {
                case MeanImputation:
                    return GLMModel.GLMParameters.MissingValuesHandling.MeanImputation;
                case Skip:
                    return GLMModel.GLMParameters.MissingValuesHandling.Skip;
                default:
                    throw new IllegalStateException("Unsupported missing values handling value: " + _missing_values_handling);
            }
        }

        public boolean imputeMissing() {
            return missingValuesHandling() == GLMModel.GLMParameters.MissingValuesHandling.MeanImputation ||
                    missingValuesHandling() == GLMModel.GLMParameters.MissingValuesHandling.PlugValues;
        }

        public DataInfo.Imputer makeImputer() {
            if (missingValuesHandling() == GLMModel.GLMParameters.MissingValuesHandling.PlugValues) {
                if (_plug_values == null || _plug_values.get() == null) {
                    throw new IllegalStateException("Plug values frame needs to be specified when Missing Value Handling = PlugValues.");
                }
                return new GLM.PlugValuesImputer(_plug_values.get());
            } else { // mean/mode imputation and skip (even skip needs an imputer right now! PUBDEV-6809)
                return new DataInfo.MeanImputer();
            }
        }
        
    }
    
    public static class ModelSelectionModelOutput extends Model.Output {
        GLMModel.GLMParameters.Family _family;
        DataInfo _dinfo;
        String[][] _best_model_predictors; // store for each predictor number, the best model predictors
        double[] _best_r2_values;  // store the best R2 values of the best models with fix number of predictors
        public Key[] _best_model_ids;
        String[][] _coefficient_names;
        double[][] _coef_p_values;
        double[][] _z_values;
        
        public ModelSelectionModelOutput(hex.modelselection.ModelSelection b, DataInfo dinfo) {
            super(b, dinfo._adaptedFrame);
            _dinfo = dinfo;
        }

        public String[][] coefficientNames() {
            return _coefficient_names;
        }
        
        public double[][] beta() {
            int numModel = _best_model_ids.length;
            double[][] coeffs = new double[numModel][];
            for (int index=0; index < numModel; index++) {
                GLMModel oneModel = DKV.getGet(_best_model_ids[index]);
                coeffs[index] = oneModel._output.beta().clone();
            }
            return coeffs;
        }
        
        public double[][] getNormBeta() {
            int numModel = _best_model_ids.length;
            double[][] coeffs = new double[numModel][];
            for (int index=0; index < numModel; index++) {
                GLMModel oneModel = DKV.getGet(_best_model_ids[index]);
                coeffs[index] = oneModel._output.getNormBeta().clone();
            }
            return coeffs;
        }
        
        @Override
        public ModelCategory getModelCategory() {
            return ModelCategory.Regression;
        }
        
        private Frame generateResultFrame() {
            int numRows = _best_model_predictors.length;
            String[] modelNames = new String[numRows];
            String[] predNames = new String[numRows];
            String[] modelIds = Stream.of(_best_model_ids).map(Key::toString).toArray(String[]::new);
            String[] zvalues = new String[numRows];
            String[] pvalues = new String[numRows];
            boolean backwardMode = _z_values!=null;
            // generate model names and predictor names
            for (int index=0; index < numRows; index++) {
                int numPred = _best_model_predictors[index].length;
                modelNames[index] = "best "+numPred+" predictor(s) model";
                predNames[index] = backwardMode ? String.join(", ", _coefficient_names[index])
                        :String.join(", ", _best_model_predictors[index]);
                if (backwardMode) {
                    zvalues[index] = joinDouble(_z_values[index]);
                    pvalues[index] = joinDouble(_coef_p_values[index]);
                }
            }
            // generate vectors before forming frame
            Vec.VectorGroup vg = Vec.VectorGroup.VG_LEN1;
            Vec modNames = Vec.makeVec(modelNames, vg.addVec());
            Vec modelIDV = Vec.makeVec(modelIds, vg.addVec());
            Vec r2=null;
            Vec zval=null;
            Vec pval=null;
            if (backwardMode) {
                zval = Vec.makeVec(zvalues, vg.addVec());
                pval = Vec.makeVec(pvalues, vg.addVec());
            } else {
                r2 = Vec.makeVec(_best_r2_values, vg.addVec());
            }
            Vec predN = Vec.makeVec(predNames, vg.addVec());
            
            if (backwardMode) {
                String[] colNames = new String[]{"model_name", "model_id", "z_values", "p_values", "coefficient_names"};
                return new Frame(Key.<Frame>make(), colNames, new Vec[]{modNames, modelIDV, zval, pval, predN});
            } else {
                String[] colNames = new String[]{"model_name", "model_id", "best_r2_value", "predictor_names"};
                return new Frame(Key.<Frame>make(), colNames, new Vec[]{modNames, modelIDV, r2, predN});

            }
        }
        
        public void shrinkArrays(int numModelsBuilt) {
            if (_best_model_predictors.length > numModelsBuilt) {
                _best_model_predictors = shrinkStringArray(_best_model_predictors, numModelsBuilt);
                _coefficient_names = shrinkStringArray(_coefficient_names, numModelsBuilt);
                _z_values = shrinkDoubleArray(_z_values, numModelsBuilt);
                _coef_p_values = shrinkDoubleArray(_coef_p_values, numModelsBuilt);
                _best_model_ids = shrinkKeyArray(_best_model_ids, numModelsBuilt);
            }
        }
        
        public void generateSummary() {
            int numModels = _best_r2_values.length;
            String[] names = new String[]{"best r2 value", "predictor names"};
            String[] types = new String[]{"double", "String"};
            String[] formats = new String[]{"%d", "%s"};
            String[] rowHeaders = new String[numModels];
            for (int index=1; index<=numModels; index++)
                rowHeaders[index-1] = "with "+index+" predictors";
            
            _model_summary = new TwoDimTable("ModelSelection Model Summary", "summary", 
                    rowHeaders, names, types, formats, "");
            for (int rIndex=0; rIndex < numModels; rIndex++) {
                int colInd = 0;
                _model_summary.set(rIndex, colInd++, _best_r2_values[rIndex]);
                _model_summary.set(rIndex, colInd++, String.join(", ", _best_model_predictors[rIndex]));
            }
        }
        
        public void generateSummary(int numModels) {
            String[] names = new String[]{"coefficient names", "z values", "p values"};
            String[] types = new String[]{"string", "string", "string"};
            String[] formats = new String[]{"%s", "%s", "%s"};
            String[] rowHeaders = new String[numModels];
            for (int index=0; index < numModels; index++) {
                rowHeaders[index] = "with "+_best_model_predictors[index].length+" predictors";
            }
            _model_summary = new TwoDimTable("ModelSlection Model Summary", "summary", 
                    rowHeaders, names, types, formats, "");
            for (int rIndex=0; rIndex < numModels; rIndex++) {
                int colInd = 0;
                String pValue = joinDouble(_coef_p_values[rIndex]);
                String zValue = joinDouble(_z_values[rIndex]);
                String coeffNames = String.join(", ", _coefficient_names[rIndex]);
                _model_summary.set(rIndex, colInd++, coeffNames);
                _model_summary.set(rIndex, colInd++, zValue);
                _model_summary.set(rIndex, colInd++, pValue);
            }
        }
        
        void updateBestModels(GLMModel bestModel, int index) {
            _best_model_ids[index] = bestModel.getKey();
            if (bestModel._parms._nfolds > 0) {
                int r2Index = Arrays.asList(bestModel._output._cross_validation_metrics_summary.getRowHeaders()).indexOf("r2");
                Float tempR2 = (Float) bestModel._output._cross_validation_metrics_summary.get(r2Index, 0);
                _best_r2_values[index] = tempR2.doubleValue();
            } else {
                _best_r2_values[index] = bestModel.r2();
            }
            extractCoeffs(bestModel, index);
        }
        
        void extractCoeffs(GLMModel model, int index) {
            _coefficient_names[index] = model._output.coefficientNames().clone(); // all coefficients
            ArrayList<String> coeffNames = new ArrayList<>(Arrays.asList(model._output.coefficientNames()));
            coeffNames.remove(coeffNames.size()-1); // remove intercept as it is not a predictor
            _best_model_predictors[index] = coeffNames.toArray(new String[0]); // without intercept
        }

        /***
         * Eliminate predictors with lowest z-value (z-score) magnitude as described in III of 
         * ModelSelectionTutorial.pdf in https://h2oai.atlassian.net/browse/PUBDEV-8428
         */
        void extractPredictors4NextModel(GLMModel model, int index, List<String> predNames, List<Integer> predIndices) {
            extractCoeffs(model, index);
            _best_model_ids[index] = model.getKey();
            List<Double> zValList = Arrays.stream(model._output.zValues()).boxed().map(Math::abs).collect(Collectors.toList());
            zValList.remove(zValList.size()-1);    // remove intercept terms
            int minZInd = zValList.indexOf(zValList.stream().min(Double::compare).get());   // find min magnitude
            String minZvalPred = _best_model_predictors[index][minZInd];
            int predIndex = predNames.indexOf(minZvalPred);
            predIndices.remove(predIndices.indexOf(predIndex));
            _z_values[index] = model._output.zValues().clone();
            _coef_p_values[index] = model._output.pValues().clone();
        }
    }

    @Override
    protected Futures remove_impl(Futures fs, boolean cascade) {
        super.remove_impl(fs, cascade);
        if (cascade && _output._best_model_ids != null && _output._best_model_ids.length > 0) {
            for (Key oneModelID : _output._best_model_ids)
                if (null != oneModelID)
                    Keyed.remove(oneModelID, fs, cascade);   // remove model key
        }
        return fs;
    }

    @Override
    protected AutoBuffer writeAll_impl(AutoBuffer ab) {
        if (_output._best_model_ids != null && _output._best_model_ids.length > 0) {
            for (Key oneModelID : _output._best_model_ids)
                if (null != oneModelID)
                    ab.putKey(oneModelID);  // add GLM model key
        }
        return super.writeAll_impl(ab);
    }

    @Override
    protected Keyed readAll_impl(AutoBuffer ab, Futures fs) {
        if (_output._best_model_ids != null && _output._best_model_ids.length > 0) {
            for (Key oneModelID : _output._best_model_ids) {
                if (null != oneModelID)
                    ab.getKey(oneModelID, fs);  // add GLM model key
            }
        }
        return super.readAll_impl(ab, fs);
    }

    public HashMap<String, Double>[] coefficients() {
        return coefficients(false);
    }
    
    public HashMap<String, Double>[] coefficients(boolean standardize) {
        int numModel = _output._best_model_ids.length;
        HashMap<String, Double>[] coeffs = new HashMap[numModel];
        for (int index=0; index < numModel; index++) {
            coeffs[index] = coefficients(index+1, standardize);
        }
        return coeffs;
    }

    public HashMap<String, Double> coefficients(int predictorSize) {
        return coefficients(predictorSize, false);
    }
    
    public HashMap<String, Double> coefficients(int predictorSize, boolean standardize) {
        int numModel = _output._best_model_ids.length;
        if (predictorSize <= 0 || predictorSize > numModel)
            throw new IllegalArgumentException("predictorSize must be between 1 and maximum size of predictor subset" +
                    " size.");
        GLMModel oneModel = DKV.getGet(_output._best_model_ids[predictorSize-1]);
        return oneModel.coefficients(standardize);
    }
}
