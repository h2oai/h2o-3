package hex.modelselection;

import hex.*;
import hex.glm.GLM;
import hex.glm.GLMModel;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.PrettyPrint;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static hex.genmodel.utils.MathUtils.combinatorial;
import static hex.glm.GLMModel.GLMParameters.Family.*;
import static hex.modelselection.ModelSelectionModel.ModelSelectionParameters.Mode.*;
import static hex.modelselection.ModelSelectionUtils.*;

public class ModelSelection extends ModelBuilder<hex.modelselection.ModelSelectionModel, hex.modelselection.ModelSelectionModel.ModelSelectionParameters, hex.modelselection.ModelSelectionModel.ModelSelectionModelOutput> {
    public String[][] _bestModelPredictors; // store for each predictor number, the best model predictors
    public double[] _bestR2Values;  // store the best R2 values of the best models with fix number of predictors
    public String[][] _predictorsAdd;
    public String[][] _predictorsRemoved;
    DataInfo _dinfo;
    String[] _coefNames;
    public int _numPredictors;
    public String[] _predictorNames;
    double[][] _currCPM;
    Frame _currCPMFrame;
    int[] _trackSweep;
    public int _glmNFolds = 0;
    Model.Parameters.FoldAssignmentScheme _foldAssignment = null;
    String _foldColumn = null;

    public ModelSelection(boolean startup_once) {
        super(new hex.modelselection.ModelSelectionModel.ModelSelectionParameters(), startup_once);
    }
    
    public ModelSelection(hex.modelselection.ModelSelectionModel.ModelSelectionParameters parms) {
        super(parms);
        init(false);
    }
    
    public ModelSelection(hex.modelselection.ModelSelectionModel.ModelSelectionParameters parms, Key<hex.modelselection.ModelSelectionModel> key) {
        super(parms, key);
        init(false);
    }
    
    @Override
    protected int nModelsInParallel(int folds) {
        return nModelsInParallel(1,2);  // disallow nfold cross-validation
    }

    @Override
    protected ModelSelectionDriver trainModelImpl() {
        return new ModelSelectionDriver();
    }

    @Override
    public ModelCategory[] can_build() {
        return new ModelCategory[]{ModelCategory.Regression};
    } // because of r2 usage

    @Override
    public boolean isSupervised() {
        return true;
    }

    @Override
    public boolean haveMojo() {
        return false;
    }

    @Override
    public boolean havePojo() {
        return false;
    }
    
    public void init(boolean expensive) {
        if (_parms._nfolds > 0 || _parms._fold_column != null) {    // cv enabled
            if (backward.equals(_parms._mode)) {
                error("nfolds/fold_column", "cross-validation is not supported for backward " +
                        "selection.");
            } else if (maxrsweep.equals(_parms._mode)) {
                error("nfolds/fold_column", "cross-validation is not supported for maxrsweep, " +
                        " maxrsweepsmall, maxrsweep and maxrsweepfull.");
            } else {
                _glmNFolds = _parms._nfolds;
                if (_parms._fold_assignment != null) {
                    _foldAssignment = _parms._fold_assignment;
                    _parms._fold_assignment = null;
                }
                if (_parms._fold_column != null) {
                    _foldColumn = _parms._fold_column;
                    _parms._fold_column = null;
                }
                _parms._nfolds = 0;
            }
        }
        super.init(expensive);
        if (error_count() > 0)
            return;
        if (expensive) {
            initModelSelectionParameters();
            if (error_count() > 0)
                return;
            initModelParameters();
        }
    }
    
    private void initModelParameters() {
        if (!backward.equals(_parms._mode)) {
            _bestR2Values = new double[_parms._max_predictor_number];
            _bestModelPredictors = new String[_parms._max_predictor_number][];
            if (!backward.equals(_parms._mode))
                _predictorsAdd = new String[_parms._max_predictor_number][];
            _predictorsRemoved = new String[_parms._max_predictor_number][];
        }
    }

    private void initModelSelectionParameters() {
        _predictorNames = extractPredictorNames(_parms, _dinfo, _foldColumn);
        _numPredictors = _predictorNames.length;

        if (maxr.equals(_parms._mode) || allsubsets.equals(_parms._mode) || maxrsweep.equals(_parms._mode)) { // check for maxr and allsubsets
            if (_parms._lambda == null && !_parms._lambda_search && _parms._alpha == null && !maxrsweep.equals(_parms._mode))
                _parms._lambda = new double[]{0.0}; // disable regularization if not specified
            if (nclasses() > 1)
                error("response", "'allsubsets', 'maxr', 'maxrsweep', " +
                        "'maxrsweep' only works with regression.");
            
            if (!(AUTO.equals(_parms._family) || gaussian.equals(_parms._family)))
                error("_family", "ModelSelection only supports Gaussian family for 'allsubset' and 'maxr' mode.");
            
            if (AUTO.equals(_parms._family))
                _parms._family = gaussian;

            if (_parms._max_predictor_number < 1 || _parms._max_predictor_number > _numPredictors)
                error("max_predictor_number", "max_predictor_number must exceed 0 and be no greater" +
                        " than the number of predictors of the training frame.");
        } else {    // checks for backward selection only
            _parms._compute_p_values = true;
            if (_parms._valid != null)
                error("validation_frame", " is not supported for ModelSelection mode='backward'");
            if (_parms._lambda_search)
                error("lambda_search", "backward selection does not support lambda_search.");
            if (_parms._lambda != null) {
                if (_parms._lambda.length > 1)
                    error("lambda", "if set must be set to 0 and cannot be an array or more than" +
                            " length one for backward selection.");
                if (_parms._lambda[0] != 0)
                    error("lambda", "must be set to 0 for backward selection");
            } else {
                _parms._lambda = new double[]{0.0};
            }
            if (multinomial.equals(_parms._family) || ordinal.equals(_parms._family))
                error("family", "backward selection does not support multinomial or ordinal");
            if (_parms._min_predictor_number <= 0) 
                error("min_predictor_number", "must be >= 1.");
            if (_parms._min_predictor_number > _numPredictors)
                error("min_predictor_number", "cannot exceed the total number of predictors (" + 
                        _numPredictors + ")in the dataset.");
        }
        
        if (_parms._nparallelism < 0) 
            error("nparallelism", "must be >= 0.");
        
        if (_parms._nparallelism == 0)
            _parms._nparallelism = H2O.NUMCPUS;
        
        if (maxrsweep.equals(_parms._mode))
            warn("validation_frame", " is not used in choosing the best k subset for ModelSelection" +
                    " models with maxrsweep.");
        
        if (maxrsweep.equals(_parms._mode) && !_parms._build_glm_model && _parms._influence != null)
            error("influence", " can only be set if glm models are built.  With maxrsweep model without" +
                    " build_glm_model = true, no GLM models will be built and hence no regression influence diagnostics" +
                    " can be calculated.");
    }

    protected void checkMemoryFootPrint(int p) {
        if (maxrsweep.equals(_parms._mode))
            p = (int) Math.ceil(p*(_parms._max_predictor_number+2)/_dinfo.fullN());
        HeartBeat hb = H2O.SELF._heartbeat;
        long mem_usage = (long) (hb._cpus_allowed * (p * p * p));
        long max_mem = hb.get_free_mem();

        if (mem_usage > max_mem) {
            String msg = "Gram matrices (one per thread) won't fit in the driver node's memory ("
                    + PrettyPrint.bytes(mem_usage) + " > " + PrettyPrint.bytes(max_mem)
                    + ") - try reducing the number of columns and/or the number of categorical factors (or switch to the L-BFGS solver).";
            error("_train", msg);
        }
    }
    
    public class ModelSelectionDriver extends Driver {
        public final void buildModel() {
            hex.modelselection.ModelSelectionModel model = null;
            try {
                int numModelBuilt = 0;
                model = new hex.modelselection.ModelSelectionModel(dest(), _parms, new hex.modelselection.ModelSelectionModel.ModelSelectionModelOutput(ModelSelection.this, _dinfo));
                model.write_lock(_job);
                model._output._mode = _parms._mode;
                if (backward.equals(_parms._mode)) {
                    model._output._best_model_ids = new Key[_numPredictors];
                    model._output._coef_p_values = new double[_numPredictors][];
                    model._output._z_values = new double[_numPredictors][];
                    model._output._best_predictors_subset = new String[_numPredictors][];
                    model._output._coefficient_names = new String[_numPredictors][];
                    model._output._predictors_removed_per_step = new String[_numPredictors][];
                    model._output._predictors_added_per_step = new String[_numPredictors][];
                } else {    // maxr, maxrsweep, allsubset
                    model._output._best_r2_values = new double[_parms._max_predictor_number];
                    model._output._best_predictors_subset = new String[_parms._max_predictor_number][];
                    model._output._coefficient_names = new String[_parms._max_predictor_number][];
                    model._output._predictors_removed_per_step = new String[_parms._max_predictor_number][];
                    model._output._predictors_added_per_step = new String[_parms._max_predictor_number][];
                    if (maxrsweep.equals(_parms._mode) && !_parms._build_glm_model) {
                        model._output._coefficient_values = new double[_parms._max_predictor_number][];
                        model._output._coefficient_values_normalized = new double[_parms._max_predictor_number][];
                        model._output._best_model_ids = null;
                    } else {
                        model._output._best_model_ids = new Key[_parms._max_predictor_number];
                    }
                }

                // build glm model with num_predictors and find one with best R2
                if (allsubsets.equals(_parms._mode))
                    buildAllSubsetsModels(model);
                else if (maxr.equals(_parms._mode))
                    buildMaxRModels(model);
                else if (backward.equals(_parms._mode))
                    numModelBuilt = buildBackwardModels(model);
                else if (maxrsweep.equals(_parms._mode))
                    buildMaxRSweepModels(model);
                
                _job.update(0, "Completed GLM model building.  Extracting results now.");
                model.update(_job);
                // copy best R2 and best predictors to model._output
                if (backward.equals(_parms._mode)) {
                    model._output.shrinkArrays(numModelBuilt);
                    model._output.generateSummary(numModelBuilt);
                } else {
                    model._output.generateSummary();
                }
            } finally {
                model.update(_job);
                model.unlock(_job);
            }
        }
        
        Frame extractCPM(double[][] cpmA, String[] coefNames) {
            String[] coefnames = (coefNames == null || coefNames.length ==0) ? _dinfo.coefNames() : coefNames;
            List<String> predList = Arrays.stream(coefnames).collect(Collectors.toList());
            if (_parms._intercept)
                predList.add("intercept");
            predList.add("XTYnYTY");
            Frame cpm = new water.util.ArrayUtils().frame(Key.make(), predList.stream().toArray(String[]::new), 
                    cpmA);
            Scope.track(cpm);
            return rebalance(cpm, false, Key.make().toString());
        }

        /***
         * The maxrsweep mode is explained here in the pdf doc:  https://github.com/h2oai/h2o-3/issues/6538.
         * Apart from actions specific to the sweeping implementation, the logic in this function is very similar to
         * that of maxr mode.
         */
        void buildMaxRSweepModels(ModelSelectionModel model) {
            _coefNames = _dinfo.coefNames();
            // generate cross-product matrix (CPM) as in section III of doc
            CPMnPredNames cpmPredIndex = genCPMPredNamesIndex(_job._key, _dinfo, _predictorNames, _parms);
            _predictorNames = cpmPredIndex._predNames;
            if (_predictorNames.length < _parms._max_predictor_number)
                error("max_predictor_number", "Your dataset contains duplicated predictors.  " +
                        "After removal, reduce your max_predictor_number to " + _predictorNames.length + " or less.");
            if (error_count() > 0)
                throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(ModelSelection.this);
            int cpmSize = cpmPredIndex._cpm.length;
            if (_parms._multinode_mode) {
                _currCPMFrame = extractCPM(cpmPredIndex._cpm, cpmPredIndex._coefNames);
                Scope.track(_currCPMFrame);
                DKV.put(_currCPMFrame);
                _trackSweep = IntStream.range(0, cpmPredIndex._cpm.length).map(x -> 1).toArray();
                if (_parms._intercept)
                    sweepCPMParallel(_currCPMFrame, new int[]{cpmSize - 2}, _trackSweep);
                cpmPredIndex._cpm = null;   // array no longer needed
            } else {
                _currCPM = cpmPredIndex._cpm;
                if (_parms._intercept)
                    sweepCPM(_currCPM, new int[]{cpmSize - 2}, false);
            }
            int[][] pred2CPMIndice = cpmPredIndex._pred2CPMMapping;
            checkMemoryFootPrint(cpmSize);
            // generate mapping of predictor index to CPM indices due to enum columns add multiple rows/columns to CPM
            double r2Scale = 1.0 / calR2Scale(train(), _parms._response_column);
            CoeffNormalization coefNorm = generateScale(_dinfo, _parms._standardize);

            List<Integer> currSubsetIndices = new ArrayList<>();    // store best k predictor subsets for 1 to k predictors
            List<String> predNames = new ArrayList<>(Arrays.asList(_predictorNames));
            // store predictor indices that are still available to be added to the bigger subset
            List<Integer> validSubset = IntStream.rangeClosed(0, predNames.size() - 1).boxed().collect(Collectors.toList());
            SweepModel bestModel = null;
            List<String> allCoefList = Stream.of(_coefNames).collect(Collectors.toList());
            BitSet predictorIndices = new BitSet(_predictorNames.length);   // pre-allocate memory to use

            for (int predNum = 1; predNum <= _parms._max_predictor_number; predNum++) { // find best predictor subset for each subset size
                Set<BitSet> usedCombos = new HashSet<>();
                bestModel = forwardStep(currSubsetIndices, validSubset, usedCombos, predictorIndices, pred2CPMIndice,
                        bestModel, _parms._intercept);

                validSubset.removeAll(currSubsetIndices);
                _job.update(predNum, "Finished forward step with " + predNum + " predictors.");

                if (predNum <= _numPredictors && predNum > 1) {  // implement the replacement part
                    bestModel = replacement(currSubsetIndices, validSubset, usedCombos, predictorIndices, bestModel, 
                            pred2CPMIndice);
                    // reset validSubset
                    currSubsetIndices = IntStream.of(bestModel._predSubset).boxed().collect(Collectors.toList());
                    validSubset = IntStream.rangeClosed(0, predNames.size() - 1).boxed().collect(Collectors.toList());
                    validSubset.removeAll(currSubsetIndices);
                }
                // build glm model with best subcarrier subsets for size and record the update
                if (_parms._build_glm_model) {
                    GLMModel bestR2Model = buildGLMModel(currSubsetIndices);
                    DKV.put(bestR2Model);
                    model._output.updateBestModels(bestR2Model, predNum - 1);
                } else {
/*                    if (bestModel._CPM == null) {
                        if (predNum < _parms._max_predictor_number) {
                            throw new RuntimeException("sweeping has failed due to zero pivot values at predictor size "
                                    + predNum + ".  To avoid this, perform experiment with max_predictor_number to be: "
                                    + (predNum - 1) + " or less.");
                        } else {
                            warn("maxrsweep process", " has failed when all predictors are chosen.  R2 values is set" +
                                    " to -1 to indicate that.  All predictors are chosen.");
                            bestModel._CPM = cpmPredIndex._cpm;
                            bestModel._predSubset = currSubsetIndices.stream().mapToInt(x->x).toArray();
                            int cpmLastInd = bestModel._CPM.length-1;
                            bestModel._CPM[cpmLastInd][cpmLastInd] = Double.MAX_VALUE;
                        }
                    }*/
                    model._output.updateBestModels(_predictorNames, allCoefList, predNum - 1, _parms._intercept, 
                            bestModel._CPM.length, bestModel._predSubset, bestModel._CPM, r2Scale, coefNorm, 
                            pred2CPMIndice, _dinfo);
                }
            }
        }

        public GLMModel buildGLMModel(List<Integer> bestSubsetIndices) {
            // generate training frame
            int[] subsetIndices = bestSubsetIndices.stream().mapToInt(Integer::intValue).toArray();
            Frame trainFrame = generateOneFrame(subsetIndices, _parms, _predictorNames, null);
            DKV.put(trainFrame);
            // generate training parameters
            final Field[] field1 = ModelSelectionModel.ModelSelectionParameters.class.getDeclaredFields();
            final Field[] field2 = Model.Parameters.class.getDeclaredFields();
            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            setParamField(_parms, params, false, field1, Collections.emptyList());
            setParamField(_parms, params, true, field2, Collections.emptyList());
            params._train = trainFrame._key;
            if (_parms._valid != null)
                params._valid = _parms._valid;
                
            // build and return model
            GLMModel model = new GLM(params).trainModel().get();
            DKV.remove(trainFrame._key);
            return model;
        }

        /**
         * Perform variable selection using MaxR implementing the sequential replacement method:
         * 1. forward step: find the initial subset of predictors with highest R2 values.  When subset size = 1, this
         *    means basically choosing the one predictor that generates a model with highest R2.  When subset size > 1,
         *    this means we choose one predictor to add to the subset which generates a model with hightest R2.
         * 2. Replacement step: consider the predictors in subset as pred0, pred1, pred2 (using subset size 3 as example):
         *    a. Keep pred1, pred2 and replace pred0 with remaining predictors, find replacement with highest R2
         *    b. keep pred0, pred2 and replace pred1 with remaining predictors, find replacement with highest R2
         *    c. keeep pred0, pred1 and replace pred2 with remaining predictors, find replacement with highest R2
         *    d. from step 2a, 2b, 2c, choose the predictor subset with highest R2, say the subset is pred0, pred4, pred2
         *    e. Take subset pred0, pred4, pred2 and go through steps a,b,c,d again and only stop when the best R2 does
         *       not improve anymore.
         *       
         * see doc at https://github.com/h2oai/h2o-3/issues/7217 for details.
         * 
         * @param model
         */
        void buildMaxRModels(ModelSelectionModel model) {
            List<Integer> currSubsetIndices = new ArrayList<>();
            List<String> coefNames = new ArrayList<>(Arrays.asList(_predictorNames));
            List<Integer> validSubset = IntStream.rangeClosed(0, coefNames.size() - 1).boxed().collect(Collectors.toList());
            for (int predNum = 1; predNum <= _parms._max_predictor_number; predNum++) { // perform for each subset size
                Set<BitSet> usedCombos = new HashSet<>();
                GLMModel bestR2Model = forwardStep(currSubsetIndices, coefNames, predNum - 1, validSubset,
                        _parms, _foldColumn, _glmNFolds, _foldAssignment, usedCombos); // forward step
                validSubset.removeAll(currSubsetIndices);
                _job.update(predNum, "Finished building all models with "+predNum+" predictors.");
                if (predNum < _numPredictors && predNum > 1) {
                    double bestR2ofModel = 0;
                    if (bestR2Model != null)
                        bestR2ofModel = bestR2Model.r2();
                    GLMModel currBestR2Model = replacement(currSubsetIndices, coefNames, bestR2ofModel, _parms,
                            _glmNFolds, _foldColumn, validSubset, _foldAssignment, usedCombos);
                    if (currBestR2Model != null) {
                        bestR2Model.delete();
                        bestR2Model = currBestR2Model;
                    }
                    validSubset.removeAll(currSubsetIndices);
                }
                DKV.put(bestR2Model);
                model._output.updateBestModels(bestR2Model, predNum - 1);
            }
        }
        
        /**
         * Implements the backward selection mode.  Refer to III of ModelSelectionTutorial.pdf in 
         * https://github.com/h2oai/h2o-3/issues/7232
         */
        private int buildBackwardModels(ModelSelectionModel model) {
            List<String> predNames = new ArrayList<>(Arrays.asList(_predictorNames));
            Frame train = DKV.getGet(_parms._train);
            List<String> numPredNames = predNames.stream().filter(x -> train.vec(x).isNumeric()).collect(Collectors.toList());
            List<String> catPredNames = predNames.stream().filter(x -> !numPredNames.contains(x)).collect(Collectors.toList());
            int numModelsBuilt = 0;
            String[] coefName = predNames.toArray(new String[0]);
            for (int predNum = _numPredictors; predNum >= _parms._min_predictor_number; predNum--) {
                int modelIndex = predNum-1;
                Frame trainingFrame = generateOneFrame(null, _parms, coefName, _foldColumn);
                DKV.put(trainingFrame);
                GLMModel.GLMParameters[] glmParam = generateGLMParameters(new Frame[]{trainingFrame}, _parms, 
                        _glmNFolds, _foldColumn, _foldAssignment);
                GLMModel glmModel = new GLM(glmParam[0]).trainModel().get();
                DKV.put(glmModel);  // track model

                // evaluate which variable to drop for next round of testing and store corresponding values
                // if p_values_threshold is specified, model building may stop
                model._output.extractPredictors4NextModel(glmModel, modelIndex, predNames, numPredNames, 
                        catPredNames);
                numModelsBuilt++;
                DKV.remove(trainingFrame._key);
                coefName = predNames.toArray(new String[0]);
                _job.update(predNum, "Finished building all models with "+predNum+" predictors.");
                if (_parms._p_values_threshold > 0) {   // check if p-values are used to stop model building
                    if (DoubleStream.of(model._output._coef_p_values[modelIndex])
                            .limit(model._output._coef_p_values[modelIndex].length-1)
                            .allMatch(x -> x <= _parms._p_values_threshold))
                        break;
                }
                if (predNames.size() == 0)    // no more predictors available to build models with
                    break;
            }
            return numModelsBuilt;
        }
        
        /***
         * Find the subset of predictors of sizes 1, 2, ..., _parm._max_predictor_number that generate the
         * highest R2 value using brute-force.  Basically for each subset size, all combinations of predictors
         * are considered.  This method is guaranteed to find the best predictor subset at the cost of computation
         * complexity.
         * 
         * @param model
         */
        void buildAllSubsetsModels(ModelSelectionModel model) {
            for (int predNum=1; predNum <= _parms._max_predictor_number; predNum++) {
                int numModels = combinatorial(_numPredictors, predNum);
                // generate the training frames with num_predictor predictors in the frame
                Frame[] trainingFrames = generateTrainingFrames(_parms, predNum, _predictorNames, numModels,
                        _foldColumn);
                // find best GLM Model with highest R2 value
                GLMModel bestModel = buildExtractBestR2Model(trainingFrames,_parms, _glmNFolds, _foldColumn, _foldAssignment);
                DKV.put(bestModel);
                // extract R2 and collect the best R2 and the predictors set
                int index = predNum-1;
                model._output.updateBestModels(bestModel, index);
                // remove training frames from DKV
                removeTrainingFrames(trainingFrames);
                _job.update(predNum, "Finished building all models with "+predNum+" predictors.");
            }
        }
        
        @Override
        public void computeImpl() {
            if (_parms._lambda_search || !_parms._intercept || _parms._lambda == null || _parms._lambda[0] > 0)
                _parms._use_all_factor_levels = true;
            _dinfo = new DataInfo(_train.clone(), _valid, 1, _parms._use_all_factor_levels,
                    _parms._standardize ? DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE,
                    _parms.missingValuesHandling() == GLMModel.GLMParameters.MissingValuesHandling.Skip,
                    _parms.imputeMissing(), _parms.makeImputer(), false, hasWeightCol(), hasOffsetCol(),
                    hasFoldCol(), null);
            init(true);
            if (error_count() > 0)
                throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(ModelSelection.this);
            _job.update(0, "finished init and ready to build models");
            buildModel();
        }
    }

    /***
     * Given current predictor subset in currSubsetIndices, this method will add one more predictor to the subset and
     * choose the one that will increase the R2 by the most.
     */
    public SweepModel forwardStep(List<Integer> currSubsetIndices, List<Integer> validSubsets, Set<BitSet> usedCombo,
                                  BitSet predIndices, int[][] predInd2CPMInd, SweepModel bestModel, 
                                  boolean hasIntercept) {
        // generate all models
        double[] subsetErrVar = bestModel == null ? generateAllErrVar(_currCPM, _currCPMFrame, -1, 
                currSubsetIndices, validSubsets, usedCombo, predIndices, predInd2CPMInd, hasIntercept) :
                generateAllErrVar(_currCPM, _currCPMFrame, bestModel._CPM.length - 1, currSubsetIndices, 
                        validSubsets, usedCombo, predIndices, predInd2CPMInd, hasIntercept);

        // find the best subset and the corresponding cpm by checking for lowest error variance
        int bestInd = -1;
        double errorVarianceMin = Double.MAX_VALUE;
        int numModel = subsetErrVar.length;
        for (int index=0; index<numModel; index++) {
            if (subsetErrVar[index] < errorVarianceMin) {
                errorVarianceMin = subsetErrVar[index];
                bestInd = index;
            }
        }
        if (bestInd == -1) { // Predictor sets are duplicates.  Return SweepModel for findBestMSEModel stream operations
            return new SweepModel(null, null, errorVarianceMin);
        } else {    // set new predictor subset to curSubsetIndices, 
            int newPredictor = validSubsets.get(bestInd);
            List<Integer> newIndices = extractCPMIndexFromPredOnly(predInd2CPMInd, new int[]{newPredictor});
            if (_parms._multinode_mode)
                sweepCPMParallel(_currCPMFrame, newIndices.stream().mapToInt(x -> x).toArray(), _trackSweep);
            else
                sweepCPM(_currCPM, newIndices.stream().mapToInt(x -> x).toArray(), false);
            currSubsetIndices.add(newPredictor);
            int[] subsetPred = currSubsetIndices.stream().mapToInt(x->x).toArray();
            double[][] subsetCPM = _parms._multinode_mode ? 
                    extractPredSubsetsCPMFrame(_currCPMFrame, subsetPred, predInd2CPMInd, hasIntercept) : 
                    extractPredSubsetsCPM(_currCPM, subsetPred, predInd2CPMInd, hasIntercept);
            return new SweepModel(subsetPred, subsetCPM, errorVarianceMin);
        }
    }

    /**
     * consider the predictors in subset as pred0, pred1, pred2 (using subset size 3 as example):
     *    a. Keep pred1, pred2 and replace pred0 with remaining predictors, find replacement with highest R2
     *    b. keep pred0, pred2 and replace pred1 with remaining predictors, find replacement with highest R2
     *    c. keep pred0, pred1 and replace pred2 with remaining predictors, find replacement with highest R2
     *    d. from step 2a, 2b, 2c, choose the predictor subset with highest R2, say the subset is pred0, pred4, pred2
     *    e. Take subset pred0, pred4, pred2 and go through steps a,b,c,d again and only stop when the best R2 does
     *       not improve anymore.
     *
     * see doc at https://github.com/h2oai/h2o-3/issues/7217 for details.
     *
     * The most important thing here is to make sure validSubset contains the true eligible predictors to choose
     * from.  Inside the for loop, I will remove and add predictors that have been chosen in oneLessSubset and add
     * the removed predictor back to validSubset after it is no longer selected in the predictor subset.
     *
     * I also will reset the validSubset from time to time to just start to include all predictors as 
     * valid, then I will remove all predictors that have been chosen in currSubsetIndices.
     *
     */
    public SweepModel replacement(List<Integer> currSubsetIndices, List<Integer> validSubset, 
                                         Set<BitSet> usedCombos, BitSet predIndices, SweepModel bestModel, 
                                         int[][] predictorIndex2CPMIndices) {
        double errorVarianceMin = bestModel._errorVariance;
        int currSubsetSize = currSubsetIndices.size();  // predictor subset size
        int lastBestErrVarPosIndex = -1;
        SweepModel currModel = new SweepModel(bestModel._predSubset, bestModel._CPM, bestModel._errorVariance);
        SweepModel bestErrVarModel = new SweepModel(bestModel._predSubset, bestModel._CPM, bestModel._errorVariance);
        SweepModel tempModel;
        while (true) {  // loop to find better predictor subset via sequential replacement
            for (int index = 0; index < currSubsetSize; index++) {  // go through each predictor position
                ArrayList<Integer> oneLessSubset = new ArrayList<>(currSubsetIndices);
                int removedPred = oneLessSubset.remove(index);
                validSubset.removeAll(oneLessSubset);
                currModel._predSubset = oneLessSubset.stream().mapToInt(x -> x).toArray();
                tempModel = forwardStepR(currSubsetIndices, validSubset, usedCombos, predIndices, 
                        predictorIndex2CPMIndices, currModel, errorVarianceMin, index);
                if (tempModel._CPM != null && errorVarianceMin > tempModel._errorVariance) {
                    currModel = tempModel;
                    errorVarianceMin = currModel._errorVariance;
                    lastBestErrVarPosIndex = index;
                    bestErrVarModel = new SweepModel(currModel._predSubset, currModel._CPM, currModel._errorVariance);
                    validSubset.add(removedPred);
                }
            }
            if (lastBestErrVarPosIndex >= 0) // improvement was found, continue
                lastBestErrVarPosIndex = -1;
            else
                break;
        }
        return bestErrVarModel;
    }


    /***
     * Given a currSubsetIndices and a predPos, this function will try to look for new predictor that will decrease the
     * error variance compared to bestErrVar.  This is slightly different than the forwardStep.  This is what happens,
     * given the bestModel with the predictor subset stored in currSubsetIndices, it will do the following:
     * 1. extract a subsetCPM which contains the cpm rows/columns corresponding to predictors in currSubsetIndices;
     * 2. perform a sweeping for the removed predictors to undo the effect of the removed predictors.  This subset is
     *    stored in subsetCPMO.
     * 3. Next, in generateAllErrVarR, a new predictor is added to currentSubsetIndices to decide which one will provide
     *    the lowest error variance.
     * 4. If the lowest error variance is lower than bestErrVar, the currentSubsetIndices will replace the predictor
     *    at predPos with the new predictor.
     * 5. If the lowest error variance found from generateAllErrVarR is higher than bestErrVar, nothing is done.
     */
    public SweepModel forwardStepR(List<Integer> currSubsetIndices, List<Integer> validSubsets, 
                                          Set<BitSet> usedCombo, BitSet predIndices, int[][] predInd2CPMInd, 
                                          SweepModel bestModel, double bestErrVar, int predPos) {
        // generate all models
        double[][] subsetCPMO = ArrayUtils.deepClone(bestModel._CPM); // grab cpm that swept by all in currSubsetIndices
        int predRemoved = currSubsetIndices.get(predPos);
        int[] removedPredSweepInd = extractSweepIndices(currSubsetIndices, predPos, predRemoved, predInd2CPMInd, 
                _parms._intercept);
        SweepVector[][] removedPredSV = sweepCPM(subsetCPMO, removedPredSweepInd, true); // undo sweep by removed pred
        double[] subsetErrVar = generateAllErrVarR(_currCPM, _currCPMFrame, subsetCPMO, predPos, currSubsetIndices, 
                validSubsets, usedCombo, predIndices, predInd2CPMInd, _parms._intercept, removedPredSweepInd, removedPredSV);
        // find the best subset and the corresponding cpm by checking for lowest error variance
        int bestInd = -1;
        double errorVarianceMin = Double.MAX_VALUE;
        int numModel = subsetErrVar.length;
        for (int index=0; index<numModel; index++) {
            if (subsetErrVar[index] < errorVarianceMin) {
                errorVarianceMin = subsetErrVar[index];
                bestInd = index;
            }
        }
        if (bestInd == -1 || errorVarianceMin > bestErrVar) { // Predictor sets are duplicates.  Return SweepModel for findBestMSEModel stream operations
            return new SweepModel(null, null, errorVarianceMin);
        } else {    // new predictor in replacement performs better than before 
            int newPredictor = validSubsets.get(bestInd);
            currSubsetIndices.remove(predPos);  // removed the predictor at position predPos
            currSubsetIndices.add(predPos, newPredictor);  // add new replaced predictor into predictor subset
            int[] subsetPred = currSubsetIndices.stream().mapToInt(Integer::intValue).toArray();
            bestModel._predSubset = subsetPred;
            double[][] subsetCPM;
            if (_parms._multinode_mode) {
                sweepCPMParallel(_currCPMFrame, predInd2CPMInd[predRemoved], _trackSweep);
                sweepCPMParallel(_currCPMFrame, predInd2CPMInd[newPredictor], _trackSweep);
                subsetCPM = extractPredSubsetsCPMFrame(_currCPMFrame, subsetPred, predInd2CPMInd, _parms._intercept);
            } else {
                sweepCPM(_currCPM, predInd2CPMInd[predRemoved], false); // undo sweeping by replaced predictor on currCPM
                sweepCPM(_currCPM, predInd2CPMInd[newPredictor], false);// perform sweeping by newly chosen predictor on currCPM
                subsetCPM = extractPredSubsetsCPM(_currCPM, subsetPred, predInd2CPMInd, _parms._intercept);
            }
            bestModel._CPM = subsetCPM;
            bestModel._errorVariance = errorVarianceMin;
            return bestModel;
        }
    }


    /**
     * Given the training Frame array, build models for each training frame and return the GLMModel with the best
     * R2 values.
     *
     * @param trainingFrames
     * @return
     */
    public static GLMModel buildExtractBestR2Model(Frame[] trainingFrames,
                                                   ModelSelectionModel.ModelSelectionParameters parms, int glmNFolds,
                                                   String foldColumn, Model.Parameters.FoldAssignmentScheme foldAssignment) {
        GLMModel.GLMParameters[] trainingParams = generateGLMParameters(trainingFrames, parms, glmNFolds,
                foldColumn, foldAssignment);
        // generate the builder;
        GLM[] glmBuilder = buildGLMBuilders(trainingParams);
        // call parallel build
        GLM[] glmResults = ModelBuilderHelper.trainModelsParallel(glmBuilder, parms._nparallelism);
        // find best GLM Model with highest R2 value
        return findBestModel(glmResults);
    }

    /**
     * Given a predictor subset with indices stored in currSubsetIndices, one more predictor from the coefNames
     * that was not found in currSubsetIndices was added to the subset to form a new Training frame. An array of 
     * training frame are built training frames from all elligible predictors from coefNames.  GLMParameters are
     * built for all TrainingFrames, GLM models are built from those parameters.  The GLM model with the highest 
     * R2 value is returned.  The added predictor which resulted in the highest R2 value will be added to 
     * currSubsetIndices.
     * 
     * see doc at https://github.com/h2oai/h2o-3/issues/7217 for details.
     *
     * @param currSubsetIndices: stored predictors that are chosen in the subset
     * @param coefNames: predictor names of full training frame
     * @param predPos: index/location of predictor to be added into currSubsetIndices
     * @return GLMModel with highest R2 value
     */
    public static GLMModel forwardStep(List<Integer> currSubsetIndices, List<String> coefNames, int predPos, 
                                       List<Integer> validSubsets, ModelSelectionModel.ModelSelectionParameters parms,
                                       String foldColumn, int glmNFolds, 
                                       Model.Parameters.FoldAssignmentScheme foldAssignment, Set<BitSet> usedCombo) {
        String[] predictorNames = coefNames.stream().toArray(String[]::new);
        // generate training frames
        Frame[] trainingFrames = generateMaxRTrainingFrames(parms, predictorNames, foldColumn,
                currSubsetIndices, predPos, validSubsets, usedCombo);
        if (trainingFrames.length > 0) {
            // find GLM model with best R2 value and return it
            GLMModel bestModel = buildExtractBestR2Model(trainingFrames, parms, glmNFolds, foldColumn, foldAssignment);
            List<String> coefUsed = extraModelColumnNames(coefNames, bestModel);
            for (int predIndex = coefUsed.size() - 1; predIndex >= 0; predIndex--) {
                int index = coefNames.indexOf(coefUsed.get(predIndex));
                if (!currSubsetIndices.contains(index)) {
                    currSubsetIndices.add(predPos, index);
                    break;
                }
            }
            removeTrainingFrames(trainingFrames);
            return bestModel;
        } else {
            return null;
        }
    }

    /**
     * Contains information of a predictor subsets like predictor indices of the subset (with the newest predictor as
     * the last element of the array), CPM associated with predictor subset minus the latest element and the error
     * variance of the CPM.
     */
    public static class SweepModel {
        int[] _predSubset;
        double[][] _CPM;
        double _errorVariance;
        
        public SweepModel(int[] predSubset, double[][] cpm,  double mse) {
            _predSubset =  predSubset;
            _CPM = cpm;
            _errorVariance = mse;
        }
    }
    
    public static GLMModel forwardStep(List<Integer> currSubsetIndices, List<String> coefNames, int predPos,
                                       List<Integer> validSubsets, ModelSelectionModel.ModelSelectionParameters parms,
                                       String foldColumn, int glmNFolds,
                                       Model.Parameters.FoldAssignmentScheme foldAssignment) {
        return forwardStep(currSubsetIndices, coefNames, predPos, validSubsets, parms, foldColumn, glmNFolds, 
                foldAssignment, null);
    }

    /**
     * consider the predictors in subset as pred0, pred1, pred2 (using subset size 3 as example):
     *    a. Keep pred1, pred2 and replace pred0 with remaining predictors, find replacement with highest R2
     *    b. keep pred0, pred2 and replace pred1 with remaining predictors, find replacement with highest R2
     *    c. keeep pred0, pred1 and replace pred2 with remaining predictors, find replacement with highest R2
     *    d. from step 2a, 2b, 2c, choose the predictor subset with highest R2, say the subset is pred0, pred4, pred2
     *    e. Take subset pred0, pred4, pred2 and go through steps a,b,c,d again and only stop when the best R2 does
     *       not improve anymore.
     *       
     * see doc at https://github.com/h2oai/h2o-3/issues/7217 for details.
     *
     */
    public static GLMModel replacement(List<Integer> currSubsetIndices, List<String> coefNames,
                                       double bestR2, ModelSelectionModel.ModelSelectionParameters parms,
                                       int glmNFolds, String foldColumn, List<Integer> validSubset,
                                       Model.Parameters.FoldAssignmentScheme foldAssignment, Set<BitSet> usedCombos) {
        int currSubsetSize = currSubsetIndices.size();
        int lastInd = currSubsetSize-1;
        int lastBestR2PosIndex = -1;
        GLMModel bestR2Model = null;
        GLMModel oneModel;
        while (true) {
            for (int index = 0; index < currSubsetSize; index++) {  // go through all predictor position in subset
                ArrayList<Integer> oneLessSubset = new ArrayList<>(currSubsetIndices);
                int predIndexRemoved = oneLessSubset.remove(index);
                oneModel = forwardStep(oneLessSubset, coefNames, index, validSubset, parms,
                        foldColumn, glmNFolds, foldAssignment, usedCombos);
                if (oneModel != null) {
                    if (oneModel.r2() > bestR2) {
                        lastBestR2PosIndex = index;
                        validSubset.remove(oneLessSubset.get(lastInd));
                        if (bestR2Model != null)
                            bestR2Model.delete();
                        bestR2Model = oneModel;
                        bestR2 = bestR2Model.r2();
                        currSubsetIndices.clear();
                        currSubsetIndices.addAll(oneLessSubset);
                        validSubset.add(predIndexRemoved);
                    } else {
                        oneModel.delete();
                    }
                }
            }
            if (lastBestR2PosIndex >= 0) 
                lastBestR2PosIndex = -1;
            else 
                break;
        }
        return bestR2Model;
    }
}
