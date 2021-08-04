package hex.maxrglm;

import hex.*;
import hex.deeplearning.DeepLearningModel;
import hex.glm.GLM;
import hex.glm.GLMModel;
import water.*;
import water.fvec.Frame;
import water.udf.CFuncRef;
import water.util.TwoDimTable;

import java.io.Serializable;

public class MaxRGLMModel extends Model<MaxRGLMModel, MaxRGLMModel.MaxRGLMParameters, 
        MaxRGLMModel.MaxRGLMModelOutput> {
    
    public MaxRGLMModel(Key<MaxRGLMModel> selfKey, MaxRGLMParameters parms, MaxRGLMModelOutput output) {
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
        throw new UnsupportedOperationException("MaxRGLM does not support scoring on data.  It only provide " +
                "information on predictor relevance");
    }

    @Override
    public Frame score(Frame fr, String destination_key, Job j, boolean computeMetrics, CFuncRef customMetricFunc) {
        throw new UnsupportedOperationException("AnovaGLM does not support scoring on data.  It only provide " +
                "information on predictor relevance");
    }
    
    public static class MaxRGLMParameters extends Model.Parameters {
        public double[] _alpha;
        public double[] _lambda;
        public boolean _standardize = true;
        GLMModel.GLMParameters.Family _family = GLMModel.GLMParameters.Family.gaussian;
        public boolean lambda_search;
        public GLMModel.GLMParameters.Link _link = GLMModel.GLMParameters.Link.identity;
        public GLMModel.GLMParameters.Solver _solver = GLMModel.GLMParameters.Solver.IRLSM;
        public String[] _interactions=null;
        public Serializable _missing_values_handling = GLMModel.GLMParameters.MissingValuesHandling.MeanImputation;
        public boolean _compute_p_values = false;
        public boolean _remove_collinear_columns = false;
        public int _nfolds = 0; // disable cross-validation
        public Key<Frame> _plug_values = null;
        public int _max_predictor_number = 1;
        public int _nparallelism = 0;

        @Override
        public String algoName() {
            return "MaxRGLM";
        }

        @Override
        public String fullName() {
            return "Maximum R Square Improvement (MAXR) to GLM";
        }

        @Override
        public String javaName() {
            return MaxRGLMModel.class.getName();
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
    
    public static class MaxRGLMModelOutput extends Model.Output {
        GLMModel.GLMParameters.Family _family;
        DataInfo _dinfo;
        String[][] _best_model_predictors; // store for each predictor number, the best model predictors
        double[] _best_r2_values;  // store the best R2 values of the best models with fix number of predictors
        
        public MaxRGLMModelOutput(MaxRGLM b, DataInfo dinfo) {
            super(b, dinfo._adaptedFrame);
            _dinfo = dinfo;
        }
        
        @Override
        public ModelCategory getModelCategory() {
            return ModelCategory.Regression;
        }

        public void summarizeRunResult(String[][] bestModelPredictors, double[] bestR2Values) {
            int numModels = bestR2Values.length;
            _best_r2_values = bestR2Values.clone();
            _best_model_predictors = new String[numModels][];
            for (int index = 0; index < numModels; index++)
                _best_model_predictors[index] = bestModelPredictors[index].clone();

            generateSummary(bestModelPredictors, bestR2Values);
        }
        
        public void generateSummary(String[][] bestModelPredictors, double[] bestR2Values) {
            int numModels = bestR2Values.length;
            String[] names = new String[]{"best R2 value", "predictor names"};
            String[] types = new String[]{"double", "String"};
            String[] formats = new String[]{"%d", "%s"};
            String[] rowHeaders = new String[numModels];
            for (int index=1; index<=numModels; index++)
                rowHeaders[index-1] = "with "+index+" predictors";
            
            _model_summary = new TwoDimTable("MaxRGLM Model Summary", "summary", 
                    rowHeaders, names, types, formats, "");
            for (int rIndex=0; rIndex < numModels; rIndex++) {
                int colInd = 0;
                _model_summary.set(rIndex, colInd++, bestR2Values[rIndex]);
                _model_summary.set(rIndex, colInd++, String.join(", ", bestModelPredictors[rIndex]));
            }
        }
    }

    @Override
    protected Futures remove_impl(Futures fs, boolean cascade) {
        super.remove_impl(fs, cascade);
        return fs;
    }

    @Override
    protected AutoBuffer writeAll_impl(AutoBuffer ab) {
        return super.writeAll_impl(ab);
    }

    @Override
    protected Keyed readAll_impl(AutoBuffer ab, Futures fs) {
        return super.readAll_impl(ab, fs);
    }

}
