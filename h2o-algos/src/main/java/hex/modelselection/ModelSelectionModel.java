package hex.modelselection;

import hex.*;
import hex.deeplearning.DeepLearningModel;
import hex.glm.GLM;
import hex.glm.GLMModel;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.udf.CFuncRef;
import water.util.TwoDimTable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
        throw new UnsupportedOperationException("ModelSelection does not support scoring on data.  It only provide " +
                "information on predictor relevance");
    }

    @Override
    public Frame result() {
        return _output.generateResultFrame();
    }
    
    public static class ModelSelectionParameters extends Model.Parameters {
        public double[] _alpha;
        public boolean _standardize = true;
        public boolean _intercept = true;
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
        public double _beta_epsilon = 1e-4;
        public double _objective_epsilon = -1;  // -1 to use default setting
        public double _gradient_epsilon = -1;   // -1 to use default setting
        public double _obj_reg = -1.0;
        public double[] _lambda = new double[]{0.0};
        public boolean _use_all_factor_levels = false;
        public boolean _build_glm_model = false;
        public GLMModel.GLMParameters.Influence _influence;  // if set to dfbetas will calculate the difference of betas obtained from including and excluding a data row
        public boolean _multinode_mode = false; // for maxrsweep only, if true will run on multiple nodes in cluster
        
        public enum Mode {
            allsubsets, // use combinatorial, exponential runtime
            maxr, // use sequential replacement but calls GLM to build all models, slow but can use cross-validation and validation dataset to build more robust results
            maxrsweep,  // perform incremental maxrsweep without using sweeping vectors, only on CPM.
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
                    throw new IllegalStateException("Plug values frame needs to be specified when Missing Value " +
                            "Handling = PlugValues.");
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
        String[][] _coefficient_names; // store for each predictor number, the best model predictors
        double[] _best_r2_values;  // store the best R2 values of the best models with fix number of predictors
        String[][] _predictors_added_per_step;
        String[][] _predictors_removed_per_step;
        public Key[] _best_model_ids;
        double[][] _coef_p_values;
        double[][] _coefficient_values;   // store best predictor subset coefficient values
        double[][] _coefficient_values_normalized;   // store best predictor subset coefficient values
        double[][] _z_values;
        public ModelSelectionParameters.Mode _mode;
        String[][] _best_predictors_subset; // predictor names for subset of each size
        
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
            int numRows = _coefficient_names.length;
            String[] modelNames = new String[numRows];
            String[] coefNames = new String[numRows];
            String[] predNames = new String[numRows];
            String[] modelIds = _best_model_ids == null ? null : Stream.of(_best_model_ids).map(Key::toString).toArray(String[]::new);
            String[] zvalues = new String[numRows];
            String[] pvalues = new String[numRows];
            String[] predAddedNames = new String[numRows];
            String[] predRemovedNames = new String[numRows];
            boolean backwardMode = _z_values!=null;
            // generate model names and predictor names
            for (int index=0; index < numRows; index++) {
                int numPred = _best_predictors_subset[index].length;
                modelNames[index] = "best "+numPred+" predictors model";
                coefNames[index] = backwardMode ? String.join(", ", _coefficient_names[index])
                        :String.join(", ", _coefficient_names[index]);
                predAddedNames[index] = backwardMode ? "" : String.join(", ", _predictors_added_per_step[index]);
                predRemovedNames[index] = _predictors_removed_per_step[index] == null ? "" : 
                        String.join(", ", _predictors_removed_per_step[index]);
                predNames[index] = String.join(", ", _best_predictors_subset[index]);
                if (backwardMode) {
                    zvalues[index] = joinDouble(_z_values[index]);
                    pvalues[index] = joinDouble(_coef_p_values[index]);
                }
            }
            // generate vectors before forming frame
            Vec.VectorGroup vg = Vec.VectorGroup.VG_LEN1;
            Vec modNames = Vec.makeVec(modelNames, vg.addVec());
            Vec modelIDV = modelIds == null ? null : Vec.makeVec(modelIds, vg.addVec());
            Vec r2=null;
            Vec zval=null;
            Vec pval=null;
            Vec predAdded=null;
            Vec predRemoved;
            if (backwardMode) {
                zval = Vec.makeVec(zvalues, vg.addVec());
                pval = Vec.makeVec(pvalues, vg.addVec());
            } else {
                r2 = Vec.makeVec(_best_r2_values, vg.addVec());
                predAdded = Vec.makeVec(predAddedNames, vg.addVec());
            }
            predRemoved = Vec.makeVec(predRemovedNames, vg.addVec());
            Vec coefN = Vec.makeVec(coefNames, vg.addVec());
            Vec predN = Vec.makeVec(predNames, vg.addVec());
            
            if (backwardMode) {
                String[] colNames = new String[]{"model_name", "model_id", "z_values", "p_values",
                        "coefficient_names", "predictor_names", "predictors_removed"};
                return new Frame(Key.<Frame>make(), colNames, new Vec[]{modNames, modelIDV, zval, pval, coefN, predN, predRemoved});
            } else {
                if (modelIds == null) {
                    String[] colNames = new String[]{"model_name", "best_r2_value", "coefficient_names", "predictor_names",
                            "predictors_removed", "predictors_added"};
                    return new Frame(Key.<Frame>make(), colNames, new Vec[]{modNames, r2, coefN, predN, predRemoved, predAdded});
                } else {
                    String[] colNames = new String[]{"model_name", "model_id", "best_r2_value", "coefficient_names", "predictor_names",
                            "predictors_removed", "predictors_added"};
                    return new Frame(Key.<Frame>make(), colNames, new Vec[]{modNames, modelIDV, r2, coefN, predN, predRemoved, predAdded});
                }
            }
        }
        
        public void shrinkArrays(int numModelsBuilt) {
            if (_coefficient_names.length > numModelsBuilt) {
                _coefficient_names = shrinkStringArray(_coefficient_names, numModelsBuilt);
                _best_predictors_subset = shrinkStringArray(_best_predictors_subset, numModelsBuilt);
                _coefficient_names = shrinkStringArray(_coefficient_names, numModelsBuilt);
                _z_values = shrinkDoubleArray(_z_values, numModelsBuilt);
                _coef_p_values = shrinkDoubleArray(_coef_p_values, numModelsBuilt);
                _best_model_ids = shrinkKeyArray(_best_model_ids, numModelsBuilt);
                _predictors_removed_per_step = shrinkStringArray(_predictors_removed_per_step, numModelsBuilt);
            }
        }
        
        public void generateSummary() {
            int numModels = _best_r2_values.length;
            String[] names = new String[]{"best_r2_value", "coefficient_names", "predictor_names", 
                    "predictors_removed", "predictors_added"};
            String[] types = new String[]{"double", "String", "String", "String", "String"};
            String[] formats = new String[]{"%d", "%s", "%s", "%s", "%s"};
            String[] rowHeaders = new String[numModels];
            for (int index=1; index<=numModels; index++)
                rowHeaders[index-1] = "with "+_best_predictors_subset[index-1].length+" predictors";
            
            _model_summary = new TwoDimTable("ModelSelection Model Summary", "summary", 
                    rowHeaders, names, types, formats, "");
            for (int rIndex=0; rIndex < numModels; rIndex++) {
                int colInd = 0;
                _model_summary.set(rIndex, colInd++, _best_r2_values[rIndex]);
                _model_summary.set(rIndex, colInd++, String.join(", ", _coefficient_names[rIndex]));
                _model_summary.set(rIndex, colInd++, String.join(", ", _best_predictors_subset[rIndex]));
                if (_predictors_removed_per_step[rIndex] != null)
                    _model_summary.set(rIndex, colInd++, String.join(", ", _predictors_removed_per_step[rIndex]));
                else
                    _model_summary.set(rIndex, colInd++, "");
                _model_summary.set(rIndex, colInd++, String.join(", ", _predictors_added_per_step[rIndex]));
            }
        }
        
        // for backward model only
        public void generateSummary(int numModels) {
            String[] names = new String[]{"coefficient_names", "predictor_names", "z_values", "p_values", "predictors_removed"};
            String[] types = new String[]{"string", "string", "string", "string", "string"};
            String[] formats = new String[]{"%s", "%s", "%s", "%s", "%s"};
            String[] rowHeaders = new String[numModels];
            for (int index=0; index < numModels; index++) {
                rowHeaders[index] = "with "+_best_predictors_subset[index].length+" predictors";
            }
            _model_summary = new TwoDimTable("ModelSelection Model Summary", "summary", 
                    rowHeaders, names, types, formats, "");
            for (int rIndex=0; rIndex < numModels; rIndex++) {
                int colInd = 0;
                String coeffNames = String.join(", ", _coefficient_names[rIndex]);
                String predNames = String.join(", ", _best_predictors_subset[rIndex]);
                String pValue = joinDouble(_coef_p_values[rIndex]);
                String zValue = joinDouble(_z_values[rIndex]);
                _model_summary.set(rIndex, colInd++, coeffNames);
                _model_summary.set(rIndex, colInd++, predNames);
                _model_summary.set(rIndex, colInd++, zValue);
                _model_summary.set(rIndex, colInd++, pValue);
                _model_summary.set(rIndex, colInd, _predictors_removed_per_step[rIndex][0]);
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
            updateAddedRemovedPredictors(index);
        }

        void extractCoeffs(GLMModel model, int index) {
            _coefficient_names[index] = model._output.coefficientNames().clone(); // all coefficients
            ArrayList<String> coeffNames = new ArrayList<>(Arrays.asList(model._output.coefficientNames()));
            _coefficient_names[index] = coeffNames.toArray(new String[0]); // without intercept
            List<String> predNames = Stream.of(model.names()).collect(Collectors.toList());
            predNames.remove(model._parms._response_column);
            _best_predictors_subset[index] = predNames.stream().toArray(String[]::new);
        }

        void updateBestModels(String[] predictorNames, List<String> allCoefNames, int index, boolean hasIntercept, 
                              int actualCPMSize, int[] predsubset, double[][] lastCPM, double r2Scale, 
                              CoeffNormalization coeffN, int[][] pred2CPMIndex, DataInfo dinfo) {
            int lastCPMIndex = actualCPMSize-1;
            if (lastCPM[lastCPMIndex][lastCPMIndex] == Double.MAX_VALUE)
                _best_r2_values[index] = -1;
            else
                _best_r2_values[index] = 1-r2Scale * lastCPM[lastCPMIndex][lastCPMIndex];
            extractCoeffs(predictorNames, allCoefNames, lastCPM, index, hasIntercept, actualCPMSize, predsubset, coeffN,
                    pred2CPMIndex, dinfo);
            updateAddedRemovedPredictors(index);
        }

        void extractCoeffs(String[] predNames, List<String> allCoefNames, double[][] cpm, int index, boolean hasIntercept,
                           int actualCPMSize, int[] predSubset, CoeffNormalization coeffN, int[][] predsubset2CPMIndices,
                           DataInfo dinfo) {
            _best_predictors_subset[index] = extractPredsFromPredIndices(predNames, predSubset);
            _coefficient_names[index] = extractCoefsFromPred(allCoefNames, hasIntercept, dinfo, predSubset);
            extractCoefsValues(cpm, _coefficient_names[index].length, hasIntercept, actualCPMSize, coeffN, index, 
                    predSubset, predsubset2CPMIndices);
        }
        
        public void extractCoefsValues(double[][] cpm, int coefValLen, boolean hasIntercept, int actualCPMSize, 
                                           CoeffNormalization coeffN, int predIndex, int[] predSubset, int[][] pred2CPMIndices) {
            _coefficient_values[predIndex] = new double[coefValLen];
            _coefficient_values_normalized[predIndex] = new double[coefValLen];
            int lastCPMIndex = actualCPMSize-1;
            int cpmIndexOffset = hasIntercept?1:0;
            boolean standardize = coeffN._standardize;
            double[] sigmaOrOneOSigma = coeffN._sigmaOrOneOSigma;
            double[] meanOverSigma = coeffN._meanOverSigma;
            double sumBetaMeanOverSigma = 0;
            int numIndexStart = _dinfo._cats;
            int offset =0;
            int predSubsetLen = predSubset.length;
            int cpmInd, coefIndex;
            for (int pIndex = 0; pIndex < predSubsetLen; pIndex++) {
                int predictor = predSubset[pIndex];
                if (predictor >= numIndexStart) {   // numerical columns
                    coefIndex = pIndex+offset;
                    cpmInd = cpmIndexOffset+pIndex;
                    if (standardize) {
                        _coefficient_values[predIndex][coefIndex] = cpm[cpmInd][lastCPMIndex]*sigmaOrOneOSigma[predictor-numIndexStart];
                        _coefficient_values_normalized[predIndex][coefIndex] = cpm[cpmInd][lastCPMIndex];
                    } else {
                        _coefficient_values[predIndex][coefIndex] = cpm[cpmInd][lastCPMIndex];
                        _coefficient_values_normalized[predIndex][coefIndex] = cpm[cpmInd][lastCPMIndex]*sigmaOrOneOSigma[predictor-numIndexStart];
                    }
                    sumBetaMeanOverSigma += _coefficient_values_normalized[predIndex][coefIndex]*meanOverSigma[predictor-numIndexStart];
                } else {    // categorical columns
                    int cpmLen = pred2CPMIndices[predictor].length; // indices of cpm to grab for coefficients info
                    for (int cpmIndex = 0; cpmIndex < cpmLen; cpmIndex++) {
                        coefIndex = offset + cpmIndex + pIndex;
                        cpmInd = cpmIndexOffset + cpmIndex + pIndex;
                        _coefficient_values[predIndex][coefIndex] = cpm[cpmInd][lastCPMIndex];
                        _coefficient_values_normalized[predIndex][coefIndex] = cpm[cpmInd][lastCPMIndex];
                    }
                    offset += cpmLen-1;
                    cpmIndexOffset += cpmLen-1;
                }
            }

            if (hasIntercept) { // extract intercept value
                int lastCoefInd = _coefficient_values[predIndex].length-1;
                if (coeffN._standardize) {
                    _coefficient_values_normalized[predIndex][lastCoefInd] = cpm[0][lastCPMIndex];
                    _coefficient_values[predIndex][lastCoefInd] = cpm[0][lastCPMIndex]-sumBetaMeanOverSigma;
                } else {
                    _coefficient_values_normalized[predIndex][lastCoefInd] = cpm[0][lastCPMIndex]+sumBetaMeanOverSigma;
                    _coefficient_values[predIndex][lastCoefInd] = cpm[0][lastCPMIndex];
                }
            }
        }
        
        public static String[] extractCoefsFromPred(List<String> allCoefList, boolean hasIntercept,
                                                    DataInfo dinfo, int[] predSubset) {
            List<String> coefNames = new ArrayList<>();
            int numPred = predSubset.length;
            int predIndex;
            int numCats = dinfo._cats;
            int catOffsets = dinfo._catOffsets[dinfo._catOffsets.length-1];
            int numCatLevel;
            for (int index=0; index<numPred; index++) {
                predIndex = predSubset[index];
                if (predIndex < numCats) {  // categorical columns
                    numCatLevel = dinfo._catOffsets[predIndex+1]-dinfo._catOffsets[predIndex];
                    final int predictorInd=predIndex;
                    List<String> coeffs = IntStream.range(0, numCatLevel).mapToObj(x -> allCoefList.get(x+dinfo._catOffsets[predictorInd])).collect(Collectors.toList());
                    coefNames.addAll(coeffs);
                } else {    // numerical columns
                    coefNames.add(allCoefList.get(predIndex+catOffsets-numCats));
                }
            }
            if (hasIntercept)
                coefNames.add("Intercept");
            return coefNames.toArray(new String[0]);
        }
        public static String[] extractPredsFromPredIndices(String[] allPreds, int[] predSubset) {
            int numPreds = predSubset.length;
            String[] predSubsetNames = new String[numPreds];
            for (int index=0; index<numPreds; index++)
                predSubsetNames[index] = allPreds[predSubset[index]];
            return predSubsetNames;
        }
        
        void updateAddedRemovedPredictors(int index) {
            final List<String> newSet = Stream.of(_coefficient_names[index]).collect(Collectors.toList());
            if (index > 0) {
                final List<String> oldSet = Stream.of(_coefficient_names[index - 1]).collect(Collectors.toList());
                List<String> predDeleted = oldSet.stream().filter(x -> (!newSet.contains(x) && 
                        !"Intercept".equals(x))).collect(Collectors.toList());
                _predictors_removed_per_step[index] = predDeleted == null || predDeleted.size()==0 ? new String[]{""} :
                        predDeleted.toArray(new String[predDeleted.size()]);
                if (!ModelSelectionParameters.Mode.backward.equals(_mode)) {
                    List<String> predAdded = newSet.stream().filter(x -> (!oldSet.contains(x) &&
                            !"Intercept".equals(x))).collect(Collectors.toList());
                    _predictors_added_per_step[index] = predAdded.toArray(new String[0]);
                }
                return;
            } else if (!ModelSelectionParameters.Mode.backward.equals(_mode)) {
                _predictors_added_per_step[index] = new String[]{_coefficient_names[index][0]};
                _predictors_removed_per_step[index] = new String[]{""};
                return;
            }
            _predictors_removed_per_step[index] = new String[]{""};
            _predictors_added_per_step[index] = new String[]{""};
        }

        /**
         * Method to remove redundant predictors at the beginning of backward method.
         */
        void resetCoeffs(GLMModel model, List<String> predNames, List<String> numPredNames, List<String> catPredNames) {
            final String[] coeffName = model._output.coefficientNames();
            int[] idxs = model._output.bestSubmodel().idxs;
            if (idxs == null) // no redundant predictors
                return;
            List<String> coeffNames = Arrays.stream(idxs).mapToObj(x -> coeffName[x]).collect(Collectors.toList());
            resetAllPreds(predNames, catPredNames, numPredNames, model, coeffNames); // remove redundant preds
        }
        
        void resetAllPreds(List<String> predNames, List<String> catPredNames, List<String> numPredNames, 
                           GLMModel model, List<String> coeffNames) {
            if (model._output.bestSubmodel().idxs.length == model.coefficients().size())  // no redundant predictors
                return;
            resetNumPredNames(numPredNames, coeffNames);
            resetCatPredNames(model.dinfo(), model._output.bestSubmodel().idxs, catPredNames);
            if (predNames.size() > (numPredNames.size() + catPredNames.size())) {
                predNames.clear();
                predNames.addAll(catPredNames);
                predNames.addAll(numPredNames);
            }
        }
        
        public void resetNumPredNames(List<String> numPredNames, List<String> coeffNames) {
            List<String> newNumPredNames = numPredNames.stream().filter(x -> coeffNames.contains(x)).collect(Collectors.toList());
            numPredNames.clear();
            numPredNames.addAll(newNumPredNames);
        }
        
        public void resetCatPredNames(DataInfo dinfo, int[] idxs, List<String> catPredNames) {
            List<String> newCatPredNames = new ArrayList<>();
            List<Integer> idxsList = Arrays.stream(idxs).boxed().collect(Collectors.toList());
            int[] catOffset = dinfo._catOffsets;
            int catIndex = catOffset.length;
            int maxCatOffset = catOffset[catIndex-1];
            for (int index=1; index<catIndex; index++) {
                int offsetedIndex = index-1;
                List<Integer> currCatList = IntStream.range(catOffset[offsetedIndex], catOffset[index]).boxed().collect(Collectors.toList());
                if (currCatList.stream().filter(x -> idxsList.contains(x)).count() > 0 && currCatList.get(currCatList.size()-1) < maxCatOffset) {
                    newCatPredNames.add(catPredNames.get(offsetedIndex));
                }
            }
            if (newCatPredNames.size() < catPredNames.size()) {
                catPredNames.clear();
                catPredNames.addAll(newCatPredNames);
            }
        }

        /***
         * Eliminate predictors with lowest z-value (z-score) magnitude as described in III of 
         * ModelSelectionTutorial.pdf in https://github.com/h2oai/h2o-3/issues/7232
         */
        void extractPredictors4NextModel(GLMModel model, int index, List<String> predNames, List<String> numPredNames, 
                                         List<String> catPredNames) {
            boolean firstRun = (index+1) == predNames.size();
            List<String> oldPredNames = firstRun ? new ArrayList<>(predNames) : null;
            extractCoeffs(model, index);
            int predIndex2Remove = findMinZValue(model, numPredNames, catPredNames, predNames);
            String pred2Remove = predNames.get(predIndex2Remove);
            if (firstRun) // remove redundant predictors if present
                resetCoeffs(model, predNames, numPredNames, catPredNames);
            List<String> redundantPred = firstRun ? 
                    oldPredNames.stream().filter(x -> !predNames.contains(x)).collect(Collectors.toList()) : null;
            _best_model_ids[index] = model.getKey();

            if (redundantPred != null && redundantPred.size() > 0) {
                redundantPred = redundantPred.stream().map(x -> x+"(redundant_predictor)").collect(Collectors.toList());
                redundantPred.add(pred2Remove);
                _predictors_removed_per_step[index] = redundantPred.stream().toArray(String[]::new);
            } else {
                _predictors_removed_per_step[index] = new String[]{pred2Remove};
            }

            _z_values[index] = model._output.zValues().clone();
            _coef_p_values[index] = model._output.pValues().clone();
            predNames.remove(pred2Remove);
            if (catPredNames.contains(pred2Remove))
                catPredNames.remove(pred2Remove);
            else
                numPredNames.remove(pred2Remove);      
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
