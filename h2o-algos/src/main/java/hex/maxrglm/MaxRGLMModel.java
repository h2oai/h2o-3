package hex.maxrglm;

import hex.*;
import hex.deeplearning.DeepLearningModel;
import hex.glm.GLM;
import hex.glm.GLMModel;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.udf.CFuncRef;
import water.util.TwoDimTable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Stream;

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

    @Override
    public Frame result() {
        return _output.generateResultFrame();
    }
    
    public static class MaxRGLMParameters extends Model.Parameters {
        public double[] _alpha;
        public double[] _lambda;
        public boolean _standardize = true;
        GLMModel.GLMParameters.Family _family = GLMModel.GLMParameters.Family.gaussian;
        public boolean _lambda_search;
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
        public Key[] _best_model_ids;
        
        public MaxRGLMModelOutput(MaxRGLM b, DataInfo dinfo) {
            super(b, dinfo._adaptedFrame);
            _dinfo = dinfo;
        }
        
        @Override
        public ModelCategory getModelCategory() {
            return ModelCategory.Regression;
        }
        
        private Frame generateResultFrame() {
            int numRows = _best_r2_values.length;
            String[] modelNames = new String[numRows];
            String[] predNames = new String[numRows];
            String[] modelIds = Stream.of(_best_model_ids).map(Key::toString).toArray(String[]::new);
            // generate model names and predictor names
            for (int index=0; index < numRows; index++) {
                int numPred = index+1;
                modelNames[index] = "best "+numPred+" predictor(s) model";
                predNames[index] = String.join(", ", _best_model_predictors[index]);
            }
            // generate vectors before forming frame
            Vec.VectorGroup vg = Vec.VectorGroup.VG_LEN1;
            Vec modNames = Vec.makeVec(modelNames, vg.addVec());
            Vec modelIDV = Vec.makeVec(modelIds, vg.addVec());
            Vec r2 = Vec.makeVec(_best_r2_values, vg.addVec());
            Vec predN = Vec.makeVec(predNames, vg.addVec());
            String[] colNames = new String[]{"model_name", "model_id", "best_r2_value", "predictor_names"};
            return new Frame(Key.<Frame>make(), colNames, new Vec[]{modNames, modelIDV, r2, predN});
        }
        
        public void generateSummary() {
            int numModels = _best_r2_values.length;
            String[] names = new String[]{"best r2 value", "predictor names"};
            String[] types = new String[]{"double", "String"};
            String[] formats = new String[]{"%d", "%s"};
            String[] rowHeaders = new String[numModels];
            for (int index=1; index<=numModels; index++)
                rowHeaders[index-1] = "with "+index+" predictors";
            
            _model_summary = new TwoDimTable("MaxRGLM Model Summary", "summary", 
                    rowHeaders, names, types, formats, "");
            for (int rIndex=0; rIndex < numModels; rIndex++) {
                int colInd = 0;
                _model_summary.set(rIndex, colInd++, _best_r2_values[rIndex]);
                _model_summary.set(rIndex, colInd++, String.join(", ", _best_model_predictors[rIndex]));
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
            ArrayList<String> coeffNames = new ArrayList<>(Arrays.asList(bestModel._output.coefficientNames()));
            coeffNames.remove(coeffNames.size()-1); // remove intercept as it is not a predictor
            _best_model_predictors[index] = coeffNames.toArray(new String[0]);
        }
    }

    @Override
    protected Futures remove_impl(Futures fs, boolean cascade) {
        super.remove_impl(fs, cascade);
        if (cascade && _output._best_model_ids != null && _output._best_model_ids.length > 0) {
            for (Key oneModelID : _output._best_model_ids)
                Keyed.remove(oneModelID, fs, cascade);   // remove model key
        }
        return fs;
    }

    @Override
    protected AutoBuffer writeAll_impl(AutoBuffer ab) {
        if (_output._best_model_ids != null && _output._best_model_ids.length > 0) {
            for (Key oneModelID : _output._best_model_ids)
                ab.putKey(oneModelID);  // add GLM model key
        }
        return super.writeAll_impl(ab);
    }

    @Override
    protected Keyed readAll_impl(AutoBuffer ab, Futures fs) {
        if (_output._best_model_ids != null && _output._best_model_ids.length > 0) {
            for (Key oneModelID : _output._best_model_ids) {
                ab.getKey(oneModelID, fs);  // add GLM model key
            }
        }
        return super.readAll_impl(ab, fs);
    }

}
